package com.rawsight.decision

/**
 * Shot readiness states output by the DecisionEngine.
 */
enum class ShotReadinessState(
    val displayText: String,
    val color: ReadinessColor
) {
    OPTIMAL_SHOT_READY("OPTIMAL SHOT READY", ReadinessColor.GREEN),
    SUBOPTIMAL_COMPOSITION("SUBOPTIMAL COMPOSITION", ReadinessColor.YELLOW),
    LIGHTING_WARNING("LOW LIGHT WARNING", ReadinessColor.YELLOW),
    HIGH_MOTION_RISK("HIGH MOTION RISK", ReadinessColor.RED),
    CRITICAL_RISK("CRITICAL RISK", ReadinessColor.RED)
}

enum class ReadinessColor {
    GREEN,
    YELLOW,
    RED
}

/**
 * Structured decision report for UI consumption.
 */
data class DecisionReport(
    val state: ShotReadinessState = ShotReadinessState.OPTIMAL_SHOT_READY,
    val exposureDeviation: Float = 0f,        // stops deviation from optimal
    val compositionScore: Float = 0.5f,       // 0–1
    val motionLevel: Float = 0f,              // 0–1, higher = more motion
    val focusStable: Boolean = true,
    val sceneType: String = "Unknown",
    val subjectDetected: Boolean = false
)
