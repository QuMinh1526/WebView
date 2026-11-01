package com.example.webview.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BrowserColors = darkColorScheme(
    primary = ActionBlue,
    onPrimary = TextPrimary,
    primaryContainer = SurfaceRaised,
    onPrimaryContainer = TextPrimary,
    secondary = AccentBlue,
    onSecondary = TextPrimary,
    secondaryContainer = SurfacePressed,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentLight,
    onTertiary = TextPrimary,
    background = Canvas,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = EdgeTint,
    outlineVariant = EdgeTint,
    error = AccentPressed,
    onError = TextPrimary,
)

@Composable
fun BrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrowserColors,
        shapes = BrowserShapes,
        content = content,
    )
}
