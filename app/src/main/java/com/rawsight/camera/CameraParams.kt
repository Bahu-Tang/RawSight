package com.rawsight.camera

/**
 * Camera2 manual control parameters.
 */
enum class CameraParam {
    ISO,
    SHUTTER_SPEED,
    WHITE_BALANCE,
    FOCUS,
    EV_COMPENSATION
}

/**
 * Control mode for each parameter.
 */
enum class ControlMode {
    AUTO,
    MANUAL
}

/**
 * Exposure ISO sensitivity values (standard stops).
 */
object IsoValues {
    val values = listOf(64, 100, 200, 400, 800, 1600, 3200, 6400)
    const val DEFAULT_AUTO = 100
}

/**
 * Shutter speed values in fractional seconds (display) and nanoseconds (Camera2).
 */
data class ShutterSpeed(
    val display: String,   // "1/250", "1/30", etc.
    val nanos: Long         // Camera2 exposure time in nanoseconds
)

object ShutterValues {
    val values = listOf(
        ShutterSpeed("1/8000", 125_000L),
        ShutterSpeed("1/4000", 250_000L),
        ShutterSpeed("1/2000", 500_000L),
        ShutterSpeed("1/1000", 1_000_000L),
        ShutterSpeed("1/500",  2_000_000L),
        ShutterSpeed("1/250",  4_000_000L),
        ShutterSpeed("1/125",  8_000_000L),
        ShutterSpeed("1/60",  16_666_667L),
        ShutterSpeed("1/30",  33_333_333L),
        ShutterSpeed("1/15",  66_666_667L),
        ShutterSpeed("1/8",  125_000_000L),
        ShutterSpeed("1/4",  250_000_000L),
        ShutterSpeed("1/2",  500_000_000L),
        ShutterSpeed("1\"", 1_000_000_000L),
        ShutterSpeed("2\"", 2_000_000_000L),
        ShutterSpeed("4\"", 4_000_000_000L),
        ShutterSpeed("8\"", 8_000_000_000L),
        ShutterSpeed("15\"",15_000_000_000L),
        ShutterSpeed("30\"",30_000_000_000L)
    )
    val DEFAULT_AUTO = ShutterSpeed("1/125", 8_000_000L)
}

/**
 * White balance in Kelvin.
 */
object WbValues {
    val values = listOf(2500, 3000, 3500, 4000, 4500, 5000, 5500, 6500, 7500)
    const val DEFAULT_AUTO = 5000
}

/**
 * Focus mode.
 */
enum class FocusMode {
    AF,   // Auto focus
    MF    // Manual focus (DIY slider simulated)
}

/**
 * EV compensation in stops.
 */
object EvValues {
    val values = listOf(-2.0f, -1.5f, -1.0f, -0.5f, 0.0f, +0.5f, +1.0f, +1.5f, +2.0f)
    const val DEFAULT_AUTO = 0.0f
}

/**
 * Snapshot of all current camera parameters and their control modes.
 */
data class CameraState(
    val iso: Int = IsoValues.DEFAULT_AUTO,
    val isoMode: ControlMode = ControlMode.AUTO,

    val shutterSpeed: ShutterSpeed = ShutterValues.DEFAULT_AUTO,
    val shutterMode: ControlMode = ControlMode.AUTO,

    val whiteBalance: Int = WbValues.DEFAULT_AUTO,
    val wbMode: ControlMode = ControlMode.AUTO,
    val wbTint: Float = 0f, // -50 .. +50 (green ← 0 → magenta)

    val focusMode: FocusMode = FocusMode.AF,
    val focusDistance: Float = 0f, // 0.0 (near) .. 1.0 (infinity)
    val focusControlMode: ControlMode = ControlMode.AUTO,

    val evCompensation: Float = EvValues.DEFAULT_AUTO,
    val evMode: ControlMode = ControlMode.AUTO,

    val zoomLevel: Float = 1.0f // 1.0x .. maxDigitalZoom
)
