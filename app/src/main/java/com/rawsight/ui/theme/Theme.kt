package com.rawsight.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// RawSight color palette – dark, professional camera aesthetic
val RawsightGreen = Color(0xFF4CAF50)
val RawsightYellow = Color(0xFFFFC107)
val RawsightRed = Color(0xFFFF5252)
val RawsightBlueCold = Color(0x8064B5F6)
val RawsightWhite = Color(0xFFFFFFFF)
val RawsightGridOverlay = Color(0x18FFFFFF)
val RawsightBboxOverlay = Color(0xCCFFFFFF)
val RawsightHudBg = Color(0x80000000)
val RawsightDarkSurface = Color(0xFF1A1A1A)
val RawsightDarkBg = Color(0xFF0D0D0D)
val RawsightDarkCard = Color(0xFF252525)

private val DarkColorScheme = darkColorScheme(
    primary = RawsightGreen,
    secondary = RawsightYellow,
    tertiary = RawsightRed,
    background = RawsightDarkBg,
    surface = RawsightDarkSurface,
    surfaceVariant = RawsightDarkCard,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = RawsightWhite,
    onSurface = RawsightWhite,
    onSurfaceVariant = Color(0xFFBBBBBB)
)

@Composable
fun RawSightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
