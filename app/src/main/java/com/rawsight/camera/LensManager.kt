package com.rawsight.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Manages camera lens discovery and selection.
 * Filters out logical cameras and deduplicates by focal length.
 */
class LensManager(private val context: Context) {

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    data class LensInfo(
        val cameraId: String,
        val facing: Int,
        val focalLengths: FloatArray,
        val isRawSupported: Boolean,
        val maxIso: Int,
        val minIso: Int,
        val maxExposureNanos: Long,
        val minExposureNanos: Long
    ) {
        val primaryFocalLength: Float get() = focalLengths.firstOrNull() ?: 1f
        val isRear: Boolean get() = facing == CameraCharacteristics.LENS_FACING_BACK
        val isFront: Boolean get() = facing == CameraCharacteristics.LENS_FACING_FRONT
    }

    /**
     * Get all non-logical cameras (rear + front), deduplicated by focal length within each facing group.
     */
    fun enumerateAll(): List<LensInfo> {
        val all = cameraManager.cameraIdList.mapNotNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
                ?: return@mapNotNull null
            // Skip external and logical cameras
            if (facing != CameraCharacteristics.LENS_FACING_BACK &&
                facing != CameraCharacteristics.LENS_FACING_FRONT)
                return@mapNotNull null

            // Skip logical multi-camera devices
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA))
                return@mapNotNull null

            LensInfo(
                cameraId = id,
                facing = facing,
                focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?: floatArrayOf(0f),
                isRawSupported = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW),
                maxIso = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.upper ?: 6400,
                minIso = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.lower ?: 100,
                maxExposureNanos = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                    ?.upper ?: 30_000_000_000L,
                minExposureNanos = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                    ?.lower ?: 125_000L
            )
        }

        // Deduplicate: keep only one lens per focal length per facing
        val seen = mutableSetOf<Pair<Int, Float>>()
        return all.filter { lens ->
            val fl = lens.primaryFocalLength
            // Round to 1 decimal to group close focal lengths
            val key = lens.facing to ((fl * 10).toInt() / 10f)
            seen.add(key)
        }.sortedBy { it.facing * 100f + it.primaryFocalLength }
    }

    fun enumerateRearCameras(): List<LensInfo> =
        enumerateAll().filter { it.isRear }

    fun enumerateFrontCameras(): List<LensInfo> =
        enumerateAll().filter { it.isFront }

    fun getMainRearCamera(): LensInfo? =
        enumerateRearCameras().firstOrNull()

    fun getMainFrontCamera(): LensInfo? =
        enumerateFrontCameras().firstOrNull()

    fun getCharacteristics(cameraId: String): CameraCharacteristics =
        cameraManager.getCameraCharacteristics(cameraId)
}
