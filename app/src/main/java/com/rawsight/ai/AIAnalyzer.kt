package com.rawsight.ai

/**
 * Interface for AI analysis engine.
 * Implementations perform scene detection, subject localization,
 * exposure evaluation, and composition advice on downsampled preview frames.
 *
 * Constraints:
 * - Must run asynchronously (never block camera thread)
 * - Analysis rate ≤ 2 FPS
 * - Stub implementation returns simulated results
 */
interface AIAnalyzer {
    /**
     * Analyze a preview frame asynchronously.
     * @param frameData Downsampled RGB preview frame (low resolution)
     * @return Analysis result, or null if analysis skipped (rate limiting)
     */
    suspend fun analyze(frameData: PreviewFrame): AIAnalysisResult?

    /**
     * Start the analyzer. Called when camera preview starts.
     */
    fun start()

    /**
     * Stop the analyzer. Called when camera preview stops.
     */
    fun stop()

    /**
     * Whether the analyzer is ready to accept frames.
     */
    val isReady: Boolean
}

/**
 * Low-resolution frame input for AI analysis.
 */
data class PreviewFrame(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,  // NV21 or RGB format
    val timestamp: Long = System.currentTimeMillis()
)
