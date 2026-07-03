package com.rawsight.ai

import android.graphics.RectF

/**
 * AI analysis result for a single preview frame.
 */
data class AIAnalysisResult(
    val sceneType: SceneType = SceneType.UNKNOWN,
    val subjectBox: RectF? = null,
    val exposureSuggestion: ExposureAdvice = ExposureAdvice.ADEQUATE,
    val compositionSuggestion: CompositionAdvice = CompositionAdvice(
        moveDirection = MoveDirection.NONE,
        tiltCorrectionHint = 0f,
        framingQualityScore = 0.5f
    )
)

enum class SceneType(val displayName: String) {
    UNKNOWN("Unknown"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    NIGHT("Night"),
    MACRO("Macro")
}

enum class ExposureAdvice(val displayName: String) {
    UNDEREXPOSED("Underexposed"),
    ADEQUATE("Adequate"),
    OVEREXPOSED("Overexposed")
}

enum class MoveDirection(val displayName: String) {
    NONE("None"),
    LEFT("Move Left"),
    RIGHT("Move Right"),
    UP("Move Up"),
    DOWN("Move Down")
}

data class CompositionAdvice(
    val moveDirection: MoveDirection = MoveDirection.NONE,
    val tiltCorrectionHint: Float = 0f, // degrees, positive = clockwise
    val framingQualityScore: Float = 0.5f // 0.0 to 1.0
)
