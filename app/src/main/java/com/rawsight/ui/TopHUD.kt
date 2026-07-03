package com.rawsight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawsight.decision.DecisionReport
import com.rawsight.decision.ReadinessColor

/**
 * Top-of-screen HUD showing shot readiness status and scene type.
 */
@Composable
fun TopHUD(
    modifier: Modifier = Modifier,
    decisionReport: DecisionReport
) {
    val stateColor = when (decisionReport.state.color) {
        ReadinessColor.GREEN -> Color(0xFF4CAF50)
        ReadinessColor.YELLOW -> Color(0xFFFFC107)
        ReadinessColor.RED -> Color(0xFFFF5252)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Status text
        Text(
            text = decisionReport.state.displayText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = stateColor,
            modifier = Modifier.weight(1f)
        )

        // Scene type label
        Text(
            text = decisionReport.sceneType,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
