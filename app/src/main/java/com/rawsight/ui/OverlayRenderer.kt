package com.rawsight.ui

import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rawsight.ai.AIAnalysisResult
import com.rawsight.ai.MoveDirection
import com.rawsight.decision.DecisionReport
import kotlin.math.abs

/**
 * Canvas overlay composable rendering:
 * 1. Rule-of-thirds grid
 * 2. Subject detection bounding box
 * 3. Composition guidance arrows
 * 4. IMU level indicator
 */
@Composable
fun OverlayRenderer(
    modifier: Modifier = Modifier,
    decisionReport: DecisionReport,
    aiResult: AIAnalysisResult?,
    tiltDegrees: Float,
    showGrid: Boolean = true
) {
    // Arrow pulse animation
    val arrowAlpha by rememberInfiniteTransition(label = "arrowPulse").animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowAlpha"
    )

    // Level rotation
    val levelAngle by animateFloatAsState(
        targetValue = tiltDegrees, animationSpec = tween(200), label = "tilt"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Rule-of-thirds grid
        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.10f)
            val sw = 1.dp.toPx()

            drawLine(gridColor, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = sw)
            drawLine(gridColor, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), strokeWidth = sw)
            drawLine(gridColor, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = sw)
            drawLine(gridColor, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), strokeWidth = sw)
        }

        // 2. Subject bounding box
        aiResult?.subjectBox?.let { box ->
            // Scale from image coords to canvas coords
            val left = box.left * w
            val top = box.top * h
            val right = box.right * w
            val bottom = box.bottom * h

            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. Composition arrow
        aiResult?.compositionSuggestion?.let { comp ->
            if (comp.moveDirection != MoveDirection.NONE) {
                val arrowColor = Color(0xFF64B5F6).copy(alpha = arrowAlpha)
                val cx = w / 2
                val cy = h / 2
                val arrowLen = 40.dp.toPx()
                val arrowHead = 12.dp.toPx()

                val (dx, dy) = when (comp.moveDirection) {
                    MoveDirection.LEFT -> Pair(-arrowLen, 0f)
                    MoveDirection.RIGHT -> Pair(arrowLen, 0f)
                    MoveDirection.UP -> Pair(0f, -arrowLen)
                    MoveDirection.DOWN -> Pair(0f, arrowLen)
                    MoveDirection.NONE -> Pair(0f, 0f)
                }

                // Shaft
                drawLine(arrowColor, Offset(cx, cy), Offset(cx + dx, cy + dy), strokeWidth = 3.dp.toPx())

                // Arrowhead
                val perpX = abs(dy) * 0.5f
                val perpY = abs(dx) * 0.5f
                drawLine(arrowColor,
                    Offset(cx + dx, cy + dy),
                    Offset(cx + dx * 0.6f - perpX, cy + dy * 0.6f - perpY),
                    strokeWidth = 2.dp.toPx())
                drawLine(arrowColor,
                    Offset(cx + dx, cy + dy),
                    Offset(cx + dx * 0.6f + perpX, cy + dy * 0.6f + perpY),
                    strokeWidth = 2.dp.toPx())
            }
        }

        // 4. Level indicator
        withTransform({
            rotate(levelAngle, pivot = Offset(w / 2, h / 2))
        }) {
            val lineY = h / 2
            val lineW = w * 0.3f
            drawLine(
                Color.White.copy(alpha = 0.4f),
                Offset(w / 2 - lineW, lineY),
                Offset(w / 2 + lineW, lineY),
                strokeWidth = 2.dp.toPx()
            )
            // Center dot when near level
            if (abs(levelAngle) < 0.5f) {
                drawCircle(
                    Color(0xFF4CAF50).copy(alpha = 0.6f),
                    radius = 4.dp.toPx(),
                    center = Offset(w / 2, lineY)
                )
            }
        }
    }
}
