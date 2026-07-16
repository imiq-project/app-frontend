package com.example.imiq.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mobility design palette — keeps every Material3-themed screen (onboarding
// questionnaire, passport detail) consistent with the teal/dark MobilityDesign.
// The app is dark-only, so there is no light scheme.
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF04130F),
    primaryContainer = Color(0xFF103A33),
    onPrimaryContainer = Color(0xFF5EEAD4),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF04130F),
    tertiary = Color(0xFFA78BFA),
    background = Color(0xFF07090D),
    onBackground = Color(0xFFEEF2F6),
    surface = Color(0xFF151A22),
    onSurface = Color(0xFFEEF2F6),
    surfaceVariant = Color(0xFF1C232E),
    onSurfaceVariant = Color(0xFF9AA6B4),
    outline = Color(0xFF2A323D),
    error = Color(0xFFFB7185),
    onError = Color(0xFF1A0A0E)
)

@Composable
fun IMIQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
