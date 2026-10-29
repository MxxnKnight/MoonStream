package com.moon.moonstream.ui.components

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Neutral ripple theme — no blue tap highlight anywhere in the app.
 * (Mockup requirement; also maps directly to a custom RippleTheme in the real app.)
 */
object NeutralRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor(): Color = Color(0xFF9AA0A6)

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(
        draggedAlpha = 0.10f,
        focusedAlpha = 0.10f,
        hoveredAlpha = 0.06f,
        pressedAlpha = 0.10f,
    )
}
