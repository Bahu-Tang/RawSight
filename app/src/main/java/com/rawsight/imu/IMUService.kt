package com.rawsight.imu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * IMU sensor service providing tilt and shake data for the DecisionEngine.
 *
 * Uses TYPE_GRAVITY for absolute tilt and TYPE_GYROSCOPE for motion/shake detection.
 * Falls back to TYPE_ACCELEROMETER + low-pass filter when gravity sensor is absent.
 * Emits [IMUData] at ~10 Hz via [imuFlow].
 */
class IMUService(context: Context) {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gravitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscopeSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // ── Sliding window for shake estimation ───────────
    private val gyroWindow = ArrayDeque<Float>(50) // ~500ms at 100Hz
    private var latestTilt = 0f
    private var useAccelFallback = gravitySensor?.type != Sensor.TYPE_GRAVITY

    // ── Low-pass filter coefficients for accelerometer fallback ──
    private var filteredAccel = FloatArray(3)
    private val alpha = 0.1f

    val imuFlow: Flow<IMUData> = callbackFlow {
        var lastEmit = 0L

        fun computeTilt(x: Float, y: Float, z: Float) {
            latestTilt = Math.toDegrees(
                atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))
            ).toFloat()
        }

        val gravityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val values = event.values // x, y, z

                if (useAccelFallback) {
                    // Low-pass filter accelerometer to approximate gravity
                    filteredAccel[0] = alpha * values[0] + (1 - alpha) * filteredAccel[0]
                    filteredAccel[1] = alpha * values[1] + (1 - alpha) * filteredAccel[1]
                    filteredAccel[2] = alpha * values[2] + (1 - alpha) * filteredAccel[2]
                    computeTilt(filteredAccel[0], filteredAccel[1], filteredAccel[2])
                } else {
                    computeTilt(values[0], values[1], values[2])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val magnitude = sqrt(
                    event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
                )
                gyroWindow.addLast(magnitude)
                if (gyroWindow.size > 50) gyroWindow.removeFirst()

                // Emit at ~10 Hz
                val now = System.currentTimeMillis()
                if (now - lastEmit >= 100) {
                    lastEmit = now
                    val shake = if (gyroWindow.isEmpty()) 0f
                    else (gyroWindow.average().toFloat() / 3.0f).coerceIn(0f, 1f)
                    trySend(
                        IMUData(
                            tiltDegrees = latestTilt,
                            shakeLevel = shake,
                            isStable = shake < 0.2f,
                            timestamp = now
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        gravitySensor?.let {
            sensorManager.registerListener(
                gravityListener, it, SensorManager.SENSOR_DELAY_GAME
            )
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(
                gyroListener, it, SensorManager.SENSOR_DELAY_GAME
            )
        }

        awaitClose {
            sensorManager.unregisterListener(gravityListener)
            sensorManager.unregisterListener(gyroListener)
        }
    }

    /**
     * Convenience method – flow starts collecting when called,
     * stops when the coroutine collecting it is cancelled.
     */
    fun start() { /* no-op: flow is cold, starts on collect */ }

    fun stop() { /* no-op: flow stops when collecting coroutine is cancelled */ }
}
