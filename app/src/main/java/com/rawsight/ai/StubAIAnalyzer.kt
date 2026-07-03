package com.rawsight.ai

import android.graphics.RectF
import kotlinx.coroutines.delay
import java.util.Random

/**
 * Stub implementation of [AIAnalyzer] for Demo / architecture validation.
 *
 * Produces simulated scene detection, subject bounding boxes,
 * exposure advice, and composition suggestions with random variation.
 * Rate-limited to ≤ 2 FPS.
 */
class StubAIAnalyzer : AIAnalyzer {

    override var isReady: Boolean = false
        private set

    private var lastAnalysisTime = 0L
    private val minIntervalMs = 500L // 2 FPS cap
    private val random = Random(System.currentTimeMillis())

    override suspend fun analyze(frameData: PreviewFrame): AIAnalysisResult? {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < minIntervalMs) return null
        lastAnalysisTime = now

        // Simulate inference latency
        delay((100L + random.nextInt(200)).toLong())

        val sceneType = pickSceneType()
        val subjectBox = if (random.nextFloat() > 0.2f) generateSubjectBox(frameData) else null
        val exposureAdvice = pickExposure()
        val compositionAdvice = CompositionAdvice(
            moveDirection = pickMoveDirection(),
            tiltCorrectionHint = (random.nextFloat() * 6f - 3f), // -3 to +3 degrees
            framingQualityScore = 0.3f + random.nextFloat() * 0.6f // 0.3 – 0.9
        )

        return AIAnalysisResult(
            sceneType = sceneType,
            subjectBox = subjectBox,
            exposureSuggestion = exposureAdvice,
            compositionSuggestion = compositionAdvice
        )
    }

    override fun start() {
        isReady = true
    }

    override fun stop() {
        isReady = false
    }

    // ── Random helpers ─────────────────────────────────

    private fun pickSceneType(): SceneType {
        val r = random.nextFloat()
        return when {
            r < 0.35f -> SceneType.PORTRAIT
            r < 0.55f -> SceneType.LANDSCAPE
            r < 0.70f -> SceneType.NIGHT
            r < 0.82f -> SceneType.MACRO
            else -> SceneType.UNKNOWN
        }
    }

    private fun pickExposure(): ExposureAdvice {
        val r = random.nextFloat()
        return when {
            r < 0.15f -> ExposureAdvice.UNDEREXPOSED
            r < 0.30f -> ExposureAdvice.OVEREXPOSED
            else -> ExposureAdvice.ADEQUATE
        }
    }

    private fun pickMoveDirection(): MoveDirection {
        val r = random.nextFloat()
        return when {
            r < 0.40f -> MoveDirection.NONE
            r < 0.55f -> MoveDirection.LEFT
            r < 0.70f -> MoveDirection.RIGHT
            r < 0.85f -> MoveDirection.UP
            else -> MoveDirection.DOWN
        }
    }

    private fun generateSubjectBox(frame: PreviewFrame): RectF {
        val w = frame.width.toFloat()
        val h = frame.height.toFloat()
        val boxW = w * (0.15f + random.nextFloat() * 0.25f)
        val boxH = h * (0.20f + random.nextFloat() * 0.30f)
        val left = (w - boxW) / 2f + (random.nextFloat() - 0.5f) * w * 0.2f
        val top = (h - boxH) / 2f + (random.nextFloat() - 0.5f) * h * 0.2f
        return RectF(left, top, left + boxW, top + boxH)
    }
}
