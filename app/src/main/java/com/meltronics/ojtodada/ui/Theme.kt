package com.meltronics.ojtodada.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OjRed = Color(0xFFC8102E)
val OjRedDeep = Color(0xFF7A0019)
val OjGold = Color(0xFFFFCD00)
val OjInk = Color(0xFF1D1B1A)
val OjGreen = Color(0xFF1B8A3A)

@Composable
fun OjTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFFF5A6E), onPrimary = Color.White,
        secondary = OjGold, onSecondary = OjInk,
        background = Color(0xFF171412), onBackground = Color(0xFFF4EFE9),
        surface = Color(0xFF221E1B), onSurface = Color(0xFFF4EFE9),
        surfaceVariant = Color(0xFF2E2824), onSurfaceVariant = Color(0xFFB9AFA6),
        outline = Color(0xFF4A413A),
    ) else lightColorScheme(
        primary = OjRed, onPrimary = Color.White,
        secondary = OjGold, onSecondary = OjInk,
        background = Color(0xFFFFF8F0), onBackground = OjInk,
        surface = Color.White, onSurface = OjInk,
        surfaceVariant = Color(0xFFF6ECE2), onSurfaceVariant = Color(0xFF6B6560),
        outline = Color(0xFFE2D5C8),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
