package com.rawsight.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.util.Rational
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2

/**
 * Core camera service managing Camera2 lifecycle, preview, and parameter control.
 *
 * Drives a single rear-camera preview session at 30 FPS with independent
 * AUTO / MANUAL switching per parameter (ISO, shutter, WB, focus, EV).
 */
class CameraService(private val context: Context) {

    // ──────────────────────────────────────────────────
    // Camera2 infrastructure
    // ──────────────────────────────────────────────────
    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var currentPreviewSurface: Surface? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var activeCameraId: String? = null

    // ──────────────────────────────────────────────────
    // Threading – all camera ops run off main thread
    // ──────────────────────────────────────────────────
    private val backgroundThread: HandlerThread =
        HandlerThread("RawSightCamera").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    // ──────────────────────────────────────────────────
    // Observable state
    // ──────────────────────────────────────────────────
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _isPreviewActive = MutableStateFlow(false)
    val isPreviewActive: StateFlow<Boolean> = _isPreviewActive.asStateFlow()

    private val _focusStable = MutableStateFlow(true)
    val focusStable: StateFlow<Boolean> = _focusStable.asStateFlow()

    // ──────────────────────────────────────────────────
    // Callbacks
    // ──────────────────────────────────────────────────
    var onCameraOpened: (() -> Unit)? = null
    var onCameraDisconnected: (() -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null
    var onAutoValueUpdate: ((CameraState) -> Unit)? = null

    // ──────────────────────────────────────────────────
    // Public accessors (for CaptureController etc.)
    // ──────────────────────────────────────────────────
    fun getCameraDevice(): CameraDevice? = cameraDevice
    fun getCameraCharacteristics(): CameraCharacteristics? = cameraCharacteristics
    fun getBackgroundHandler(): Handler = backgroundHandler
    fun getPreviewSurface(): Surface? = currentPreviewSurface

    // ──────────────────────────────────────────────────
    // Camera lifecycle
    // ──────────────────────────────────────────────────

    /**
     * Open the camera identified by [cameraId].
     * Must be called before [startPreview].
     */
    fun openCamera(cameraId: String) {
        if (cameraDevice != null) {
            close()
        }
        activeCameraId = cameraId
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        try {
            cameraManager.openCamera(cameraId, deviceStateCallback, backgroundHandler)
        } catch (e: SecurityException) {
            onCameraError?.invoke("Camera permission denied")
        } catch (e: IllegalArgumentException) {
            onCameraError?.invoke("Camera ID not found: $cameraId")
        } catch (e: CameraAccessException) {
            onCameraError?.invoke("Cannot access camera: ${e.message}")
        }
    }

    /**
     * Start the preview stream onto [surface] (from TextureView).
     */
    fun startPreview(surface: Surface) {
        currentPreviewSurface = surface
        val device = cameraDevice ?: run {
            onCameraError?.invoke("Camera not opened")
            return
        }
        try {
            // Select optimal preview size – match surface resolution or fallback
            val previewSize = choosePreviewSize(surface)

            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                applyCameraState(_cameraState.value)
            }

            device.createCaptureSession(
                listOf(surface),
                sessionStateCallback,
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            onCameraError?.invoke("Failed to start preview: ${e.message}")
        }
    }

    /**
     * Stop the active preview repeating request and close the session.
     */
    fun stopPreview() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (_: Exception) { }
        captureSession = null
        previewRequestBuilder = null
        _isPreviewActive.value = false
    }

    /**
     * Close the camera device and tear down all resources.
     */
    fun close() {
        stopPreview()
        try {
            cameraDevice?.close()
        } catch (_: Exception) { }
        cameraDevice = null
        cameraCharacteristics = null
        currentPreviewSurface = null
        activeCameraId = null
    }

    /**
     * Release background thread. Call when service is no longer needed.
     */
    fun release() {
        close()
        backgroundThread.quitSafely()
    }

    // ──────────────────────────────────────────────────
    // Parameter control
    // ──────────────────────────────────────────────────

    /**
     * Update a single camera parameter (mode or value) and rebuild the preview request.
     */
    fun updateParameter(
        param: CameraParam,
        mode: ControlMode? = null,
        isoValue: Int? = null,
        shutterValue: ShutterSpeed? = null,
        wbValue: Int? = null,
        focusMode: FocusMode? = null,
        focusDistance: Float? = null,
        evValue: Float? = null
    ) {
        _cameraState.value = _cameraState.value.run {
            when (param) {
                CameraParam.ISO -> copy(
                    iso = isoValue ?: iso,
                    isoMode = mode ?: isoMode
                )
                CameraParam.SHUTTER_SPEED -> copy(
                    shutterSpeed = shutterValue ?: shutterSpeed,
                    shutterMode = mode ?: shutterMode
                )
                CameraParam.WHITE_BALANCE -> copy(
                    whiteBalance = wbValue ?: whiteBalance,
                    wbMode = mode ?: wbMode
                )
                CameraParam.FOCUS -> copy(
                    focusMode = focusMode ?: this.focusMode,
                    focusDistance = focusDistance ?: this.focusDistance,
                    focusControlMode = mode ?: focusControlMode
                )
                CameraParam.EV_COMPENSATION -> copy(
                    evCompensation = evValue ?: evCompensation,
                    evMode = mode ?: evMode
                )
            }
        }
        rebuildPreviewRequest()
    }

    // ──────────────────────────────────────────────────
    // Device state callback
    // ──────────────────────────────────────────────────

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            onCameraOpened?.invoke()
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            cameraDevice = null
            onCameraDisconnected?.invoke()
        }

        override fun onError(device: CameraDevice, error: Int) {
            val msg = when (error) {
                ERROR_CAMERA_DEVICE -> "Fatal camera device error"
                ERROR_CAMERA_DISABLED -> "Camera disabled by policy"
                ERROR_CAMERA_IN_USE -> "Camera already in use"
                ERROR_CAMERA_SERVICE -> "Camera service error"
                ERROR_MAX_CAMERAS_IN_USE -> "Too many cameras open"
                else -> "Unknown camera error ($error)"
            }
            device.close()
            cameraDevice = null
            onCameraError?.invoke(msg)
        }

        override fun onClosed(device: CameraDevice) {
            cameraDevice = null
        }
    }

    // ──────────────────────────────────────────────────
    // Session state callback
    // ──────────────────────────────────────────────────

    private val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
                val request = previewRequestBuilder?.build() ?: return
                session.setRepeatingRequest(request, captureCallback, backgroundHandler)
                _isPreviewActive.value = true
            } catch (e: CameraAccessException) {
                onCameraError?.invoke("Failed to start repeating request: ${e.message}")
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            onCameraError?.invoke("Capture session configuration failed")
        }

        override fun onClosed(session: CameraCaptureSession) {
            captureSession = null
            _isPreviewActive.value = false
        }
    }

    // ──────────────────────────────────────────────────
    // Capture result callback – tracks auto values & AF
    // ──────────────────────────────────────────────────

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val state = _cameraState.value
            val updatedState = state.copy(
                // Track auto-exposed ISO / exposure time
                iso = if (state.isoMode == ControlMode.AUTO) {
                    result.get(CaptureResult.SENSOR_SENSITIVITY) ?: state.iso
                } else state.iso,
                shutterSpeed = if (state.shutterMode == ControlMode.AUTO) {
                    val nanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: state.shutterSpeed.nanos
                    // Find closest display value
                    ShutterValues.values.minByOrNull {
                        kotlin.math.abs(it.nanos - nanos)
                    } ?: state.shutterSpeed
                } else state.shutterSpeed,
                // Track auto-WB
                whiteBalance = if (state.wbMode == ControlMode.AUTO) state.whiteBalance else state.whiteBalance
            )

            if (updatedState != state) {
                _cameraState.value = updatedState
                onAutoValueUpdate?.invoke(updatedState)
            }

            // Track AF stability
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            val stable = when (afState) {
                CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
                CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
                CaptureResult.CONTROL_AF_STATE_INACTIVE -> true
                else -> false
            }
            _focusStable.value = stable
        }
    }

    // ──────────────────────────────────────────────────
    // Preview request building
    // ──────────────────────────────────────────────────

    private fun rebuildPreviewRequest() {
        val builder = previewRequestBuilder ?: return
        try {
            builder.applyCameraState(_cameraState.value)
            captureSession?.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            onCameraError?.invoke("Failed to update preview: ${e.message}")
        }
    }

    /**
     * Apply the current [CameraState] to a [CaptureRequest.Builder].
     */
    private fun CaptureRequest.Builder.applyCameraState(state: CameraState) {
        val chars = cameraCharacteristics ?: return

        val isoAuto = state.isoMode == ControlMode.AUTO
        val shutterAuto = state.shutterMode == ControlMode.AUTO

        if (isoAuto && shutterAuto) {
            // Full 3A auto
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        } else {
            // Manual exposure (ISO or shutter override)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            set(
                CaptureRequest.SENSOR_SENSITIVITY,
                state.iso.coerceIn(
                    (chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.lower ?: 64),
                    (chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.upper ?: 6400)
                )
            )
            set(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                state.shutterSpeed.nanos.coerceIn(
                    (chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.lower ?: 125_000L),
                    (chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: 30_000_000_000L)
                )
            )
        }

        // White balance
        if (state.wbMode == ControlMode.AUTO) {
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        } else {
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            set(CaptureRequest.COLOR_CORRECTION_GAINS, approximateGains(state.whiteBalance))
        }

        // Focus
        set(
            CaptureRequest.CONTROL_AF_MODE,
            if (state.focusMode == FocusMode.AF) CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            else CaptureRequest.CONTROL_AF_MODE_OFF
        )
        if (state.focusMode == FocusMode.MF) {
            set(CaptureRequest.LENS_FOCUS_DISTANCE, state.focusDistance.coerceIn(0f, 1f))
        }

        // EV compensation (works even in CONTROL_MODE_AUTO)
        val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
            ?: Rational(1, 3)
        val lower = evRange?.lower ?: -4
        val upper = evRange?.upper ?: 4
        val evStepNum = evStep.numerator.toFloat()
        val evStepDen = evStep.denominator.toFloat()
        val evSteps = ((state.evCompensation / (evStepNum / evStepDen)).toInt()).coerceIn(lower, upper)
        set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evSteps)
    }

    // ──────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────

    private fun choosePreviewSize(surface: Surface): Size {
        val chars = cameraCharacteristics ?: return Size(1920, 1080)
        val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = configMap?.getOutputSizes(SurfaceTexture::class.java) ?: return Size(1920, 1080)
        // Pick the largest size ≤ 1920 wide
        return outputSizes
            .filter { it.width <= 1920 && it.height <= 1920 }
            .maxByOrNull { it.width * it.height }
            ?: outputSizes.maxByOrNull { it.width * it.height }
            ?: Size(1920, 1080)
    }

    /**
     * Simple white-balance gain approximation from Kelvin temperature.
     * Produces an [RggbChannelVector] with red and blue gains relative to green.
     */
    private fun approximateGains(kelvin: Int): RggbChannelVector {
        val ratio = kelvin / 5000f
        val redGain = (1f / ratio).coerceIn(0.5f, 2.0f)
        val blueGain = ratio.coerceIn(0.5f, 2.0f)
        return RggbChannelVector(redGain, 1f, 1f, blueGain)
    }

    /**
     * Single-frame capture helper used by CaptureController.
     * Stops preview → creates temp session with preview+reader → captures → restarts preview.
     */
    internal fun prepareForCapture(imageReaderSurface: Surface): CameraCaptureSession? {
        val device = cameraDevice ?: return null
        val preview = currentPreviewSurface ?: return null

        // Stop current repeating request
        try { captureSession?.stopRepeating() } catch (_: Exception) {}

        var session: CameraCaptureSession? = null
        val lock = Object()

        try {
            device.createCaptureSession(
                listOf(preview, imageReaderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        synchronized(lock) { lock.notifyAll() }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        synchronized(lock) { lock.notifyAll() }
                    }
                },
                backgroundHandler
            )

            // Wait up to 2 s for session creation
            synchronized(lock) { lock.wait(2000) }
        } catch (e: Exception) {
            // fall through
        }
        return session
    }

    /**
     * Resume the preview after a still capture.
     * Suspends until the new session is configured or fails.
     */
    internal suspend fun resumePreviewAfterCapture() {
        val surface = currentPreviewSurface ?: return
        val device = cameraDevice ?: return

        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            try {
                previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                    applyCameraState(_cameraState.value)
                }
                device.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                val request = previewRequestBuilder?.build() ?: return
                                session.setRepeatingRequest(request, captureCallback, backgroundHandler)
                                _isPreviewActive.value = true
                                cont.resume(Unit, null)
                            } catch (e: CameraAccessException) {
                                cont.resumeWith(Result.failure(e))
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            cont.resumeWith(Result.failure(RuntimeException("Preview session failed")))
                        }
                    },
                    backgroundHandler
                )
            } catch (e: CameraAccessException) {
                cont.resumeWith(Result.failure(e))
            }
        }
    }
}
