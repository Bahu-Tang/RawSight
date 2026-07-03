package com.rawsight.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Single-session JPEG + RAW capture via Camera2.
 */
class CaptureController(
    private val context: Context,
    private val cameraDevice: CameraDevice,
    private val cameraCharacteristics: CameraCharacteristics,
    private val backgroundHandler: Handler
) {
    val isRawSupported: Boolean =
        cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val baseDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RawSight"
    )
    private val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

    private val isoRange = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    private val exposureRange = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    private val configMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

    /**
     * Capture JPEG + RAW (if supported) in one CameraCaptureSession.
     * Returns JPEG file path, or empty string on failure.
     */
    suspend fun captureBoth(cameraState: CameraState, previewSurface: Surface): String =
        withContext(Dispatchers.IO) {
            ensureDir()
            val timestamp = dateFormat.format(Date())
            val jpegFile = File(baseDir, "RAW_$timestamp.jpg")
            val dngFile = if (isRawSupported) File(baseDir, "RAW_$timestamp.dng") else null

            try {
                // --- Choose sizes ---
                val jpegSize = configMap?.getOutputSizes(ImageFormat.JPEG)
                    ?.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)
                val rawSize = if (isRawSupported) {
                    configMap?.getOutputSizes(ImageFormat.RAW_SENSOR)?.firstOrNull()
                } else null

                // --- Create ImageReaders ---
                val jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1)
                val rawReader = if (rawSize != null) ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 1) else null

                // Build surfaces list
                val surfaces = mutableListOf<Surface>()
                surfaces.add(previewSurface)
                surfaces.add(jpegReader.surface!!)
                rawReader?.surface?.let { surfaces.add(it) }

                // --- Build capture request ---
                val requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                requestBuilder.addTarget(jpegReader.surface!!)
                rawReader?.surface?.let { requestBuilder.addTarget(it) }
                requestBuilder.applyCaptureParams(cameraState)

                // --- Create session ---
                val session = suspendCancellableCoroutine<CameraCaptureSession> { cont ->
                    cameraDevice.createCaptureSession(surfaces,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) { cont.resume(s, null) }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                cont.resumeWith(Result.failure(RuntimeException("Session config failed")))
                            }
                        }, backgroundHandler)
                }

                // --- Wait for JPEG image ---
                val jpegDeferred = CompletableDeferred<ByteArray>()
                jpegReader.setOnImageAvailableListener({ reader ->
                    reader.acquireLatestImage()?.let { img ->
                        val buf = img.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        img.close()
                        jpegDeferred.complete(bytes)
                    }
                }, backgroundHandler)

                // --- Wait for RAW image ---
                val rawDeferred = rawReader?.let { reader ->
                    CompletableDeferred<Pair<Image, TotalCaptureResult>>().also { d ->
                        reader.setOnImageAvailableListener({ _ -> }, backgroundHandler)
                    }
                }

                // --- Issue capture ---
                val resultDeferred = CompletableDeferred<TotalCaptureResult>()
                session.capture(requestBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            s: CameraCaptureSession, req: CaptureRequest, result: TotalCaptureResult
                        ) { resultDeferred.complete(result) }
                    }, backgroundHandler)

                val captureResult = resultDeferred.await()
                val jpegBytes = jpegDeferred.await()

                // --- Save JPEG ---
                FileOutputStream(jpegFile).use { it.write(jpegBytes) }
                registerMediaStore(jpegFile, "image/jpeg")

                // --- Save DNG ---
                if (rawReader != null && dngFile != null && rawSize != null) {
                    val rawImg = rawReader.acquireLatestImage()
                    if (rawImg != null) {
                        val dng = DngCreator(cameraCharacteristics, captureResult)
                        FileOutputStream(dngFile).use { out ->
                            val buf = rawImg.planes[0].buffer
                            val bytes = ByteArray(buf.remaining())
                            buf.get(bytes)
                            java.io.ByteArrayInputStream(bytes).use { input ->
                                dng.writeInputStream(out, rawSize, input, 0L)
                            }
                        }
                        dng.close()
                        rawImg.close()
                        registerMediaStore(dngFile, "image/x-adobe-dng")
                    }
                }

                // --- Cleanup ---
                session.close()
                jpegReader.close()
                rawReader?.close()

                jpegFile.absolutePath
            } catch (e: Exception) {
                Log.e("RawSight", "CaptureBoth error", e)
                ""
            }
        }

    private fun CaptureRequest.Builder.applyCaptureParams(state: CameraState) {
        val isoAuto = state.isoMode == ControlMode.AUTO
        val shutterAuto = state.shutterMode == ControlMode.AUTO
        if (isoAuto && shutterAuto) {
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        } else {
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            set(CaptureRequest.SENSOR_SENSITIVITY, state.iso.coerceIn(isoRange?.lower ?: 64, isoRange?.upper ?: 6400))
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, state.shutterSpeed.nanos.coerceIn(exposureRange?.lower ?: 125_000L, exposureRange?.upper ?: 30_000_000_000L))
        }
        if (state.wbMode == ControlMode.AUTO) {
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        } else {
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            val r = state.whiteBalance / 5000f
            set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector((1f / r).coerceIn(0.5f, 2f), 1f, 1f, r.coerceIn(0.5f, 2f)))
        }
        set(CaptureRequest.CONTROL_AF_MODE, if (state.focusMode == FocusMode.AF) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
        if (state.focusMode == FocusMode.MF) set(CaptureRequest.LENS_FOCUS_DISTANCE, state.focusDistance.coerceIn(0f, 1f))

        val evRange = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: Rational(1, 3)
        val ev = ((state.evCompensation / (evStep.numerator.toFloat() / evStep.denominator)).toInt())
            .coerceIn(evRange?.lower ?: -4, evRange?.upper ?: 4)
        set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev)
        set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
    }

    private fun ensureDir() { if (!baseDir.exists()) baseDir.mkdirs() }

    private fun registerMediaStore(file: File, mime: String) {
        try {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RawSight")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            v.clear(); v.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, v, null, null)
        } catch (e: Exception) { Log.e("RawSight", "MediaStore", e) }
    }
}
