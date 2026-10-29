package com.moon.moonstream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MoonStream dark theme. A bundled font will be wired in before release builds
// (the target device's handwriting system font restyles unlabeled UIs).
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD956),
    onPrimary = Color(0xFF0B1407),
    secondary = Color(0xFF6A9BFF),
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
)

@Composable
fun MoonStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
