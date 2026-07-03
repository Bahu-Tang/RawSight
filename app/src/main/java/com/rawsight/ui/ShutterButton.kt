package com.rawsight.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rawsight.decision.ReadinessColor

/**
 * Central shutter button with a colored outer ring that responds to shot readiness.
 */
@Composable
fun ShutterButton(
    modifier: Modifier = Modifier,
    readinessColor: ReadinessColor,
    enabled: Boolean = true,
    onCapture: () -> Unit
) {
    val ringColor = when (readinessColor) {
        ReadinessColor.GREEN -> Color(0xFF4CAF50)
        ReadinessColor.YELLOW -> Color(0xFFFFC107)
        ReadinessColor.RED -> Color(0xFFFF5252)
    }

    val pulseAlpha by rememberInfiniteTransition(
        label = if (readinessColor != ReadinessColor.GREEN) "pulse" else "steady"
    ).animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (readinessColor == ReadinessColor.RED) 600 else 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier.size(84.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .then(
                    if (readinessColor == ReadinessColor.GREEN) {
                        Modifier
                            .border(3.dp, ringColor, CircleShape)
                            .shadow(12.dp, CircleShape)
                    } else {
                        Modifier.border(3.dp, ringColor.copy(alpha = pulseAlpha), CircleShape)
                    }
                )
        )

        // Inner white circle
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
                .then(if (enabled) Modifier.clickable { onCapture() } else Modifier)
        )
    }
}
