package com.rawsight.imu

/**
 * IMU data snapshot for shake and tilt analysis.
 */
data class IMUData(
    val tiltDegrees: Float = 0f,       // roll angle, positive = clockwise tilt
    val shakeLevel: Float = 0f,        // 0.0 to 1.0, aggregated motion intensity
    val isStable: Boolean = true,      // shakeLevel < threshold
    val timestamp: Long = 0L
)
