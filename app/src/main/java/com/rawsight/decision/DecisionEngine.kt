package com.rawsight.decision

import com.rawsight.ai.AIAnalyzer
import com.rawsight.ai.AIAnalysisResult
import com.rawsight.ai.ExposureAdvice
import com.rawsight.ai.SceneType
import com.rawsight.imu.IMUData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Core decision engine for RawSight.
 *
 * Fuses AI analysis results, IMU sensor data, and focus state into a single
 * [ShotReadinessState] emitted via [report] as a [DecisionReport].
 *
 * Decision logic (priority order, highest wins):
 * 1. IMU shakeLevel > 0.7 → [ShotReadinessState.HIGH_MOTION_RISK]
 * 2. IMU shakeLevel > 0.4 AND compositionScore < 0.4 → [ShotReadinessState.CRITICAL_RISK]
 * 3. [ExposureAdvice.UNDEREXPOSED] or [ExposureAdvice.OVEREXPOSED] → [ShotReadinessState.LIGHTING_WARNING]
 * 4. compositionScore < 0.4 → [ShotReadinessState.SUBOPTIMAL_COMPOSITION]
 * 5. |tiltDegrees| > 5.0 → [ShotReadinessState.SUBOPTIMAL_COMPOSITION]
 * 6. Otherwise → [ShotReadinessState.OPTIMAL_SHOT_READY]
 */
class DecisionEngine(
    private val aiAnalyzer: AIAnalyzer? = null
) {
    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    private var latestAiResult: AIAnalysisResult? = null
    private var latestImuData: IMUData? = null
    private var focusStable: Boolean = true

    private val _report = MutableStateFlow(DecisionReport())
    val report: StateFlow<DecisionReport> = _report.asStateFlow()

    // -----------------------------------------------------------------------
    // Public update methods
    // -----------------------------------------------------------------------

    /**
     * Feed a fresh AI analysis result into the engine.
     * Triggers a re-evaluation and state emission.
     */
    fun updateFromAI(result: AIAnalysisResult) {
        latestAiResult = result
        _report.value = evaluate()
    }

    /**
     * Feed a fresh IMU measurement into the engine.
     * Triggers a re-evaluation and state emission.
     */
    fun updateFromIMU(data: IMUData) {
        latestImuData = data
        _report.value = evaluate()
    }

    /**
     * Update the focus-stable flag (typically from camera AF callback).
     * Triggers a re-evaluation and state emission.
     */
    fun updateFocusStable(stable: Boolean) {
        focusStable = stable
        _report.value = evaluate()
    }

    /**
     * Reset the engine to its initial state.
     * Clears all cached data and emits a fresh default report.
     */
    fun reset() {
        latestAiResult = null
        latestImuData = null
        focusStable = true
        _report.value = DecisionReport()
    }

    // -----------------------------------------------------------------------
    // Decision logic
    // -----------------------------------------------------------------------

    private fun evaluate(): DecisionReport {
        val aiResult = latestAiResult
        val imuData = latestImuData

        // --- Extract / derive values ---------------------------------------

        val compositionScore = aiResult?.compositionSuggestion?.framingQualityScore ?: 0.5f
        val motionLevel = imuData?.shakeLevel ?: 0f
        val tiltDegrees = imuData?.tiltDegrees ?: 0f
        val sceneTypeName = aiResult?.sceneType?.displayName ?: "Unknown"
        val subjectDetected = aiResult?.subjectBox != null
        val currentFocusStable = focusStable

        val exposureDeviation = computeExposureDeviation(
            advice = aiResult?.exposureSuggestion,
            sceneType = aiResult?.sceneType
        )

        // --- Priority-ordered state decision -------------------------------

        val state: ShotReadinessState = when {
            // 1. Heavy shake — immediate motion risk
            motionLevel > 0.7f -> ShotReadinessState.HIGH_MOTION_RISK

            // 2. Moderate shake + poor composition — critical
            motionLevel > 0.4f && compositionScore < 0.4f ->
                ShotReadinessState.CRITICAL_RISK

            // 3. Exposure under/over — lighting warning
            aiResult?.exposureSuggestion == ExposureAdvice.UNDEREXPOSED ||
                aiResult?.exposureSuggestion == ExposureAdvice.OVEREXPOSED ->
                ShotReadinessState.LIGHTING_WARNING

            // 4. Weak composition score
            compositionScore < 0.4f -> ShotReadinessState.SUBOPTIMAL_COMPOSITION

            // 5. Excessive tilt
            abs(tiltDegrees) > 5.0f -> ShotReadinessState.SUBOPTIMAL_COMPOSITION

            // 6. All clear
            else -> ShotReadinessState.OPTIMAL_SHOT_READY
        }

        return DecisionReport(
            state = state,
            exposureDeviation = exposureDeviation,
            compositionScore = compositionScore,
            motionLevel = motionLevel,
            focusStable = currentFocusStable,
            sceneType = sceneTypeName,
            subjectDetected = subjectDetected
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Computes an exposure deviation value (in stops) based on the
     * [ExposureAdvice] and the [SceneType].
     *
     * - UNDEREXPOSED → -1.0 stops (more sensitive scenes push to -1.5)
     * - OVEREXPOSED  → +1.0 stops (more sensitive scenes push to +1.5)
     * - ADEQUATE     →  0.0 stops
     */
    private fun computeExposureDeviation(
        advice: ExposureAdvice?,
        sceneType: SceneType?
    ): Float {
        val base = when (advice) {
            ExposureAdvice.UNDEREXPOSED -> -1.0f
            ExposureAdvice.OVEREXPOSED -> 1.0f
            ExposureAdvice.ADEQUATE -> 0.0f
            null -> 0.0f
        }
        // Night scenes are more sensitive to exposure deviations.
        val multiplier = when (sceneType) {
            SceneType.NIGHT -> 1.5f
            SceneType.MACRO -> 1.2f
            else -> 1.0f
        }
        return base * multiplier
    }
}
