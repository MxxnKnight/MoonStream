package com.moon.moonstream.ui.components

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * No tap highlight anywhere in the app — pressed states render nothing.
 * (Mockup requirement; moon asked to remove the click-preview overlay entirely.)
 *
 * Compose 1.7 deprecated RippleTheme at error level, but its replacement
 * (RippleConfiguration) only exists from Compose 1.8 — so on this 1.7.0
 * experiment branch the old theme stays, with the deprecation suppressed.
 * It still works exactly as before. A real upgrade to 1.8+ must migrate
 * this to RippleConfiguration.
 */
@Suppress("DEPRECATION_ERROR")
object NeutralRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor(): Color = Color.Transparent

    @Composable
    override fun rippleAlpha(): RippleAlpha = RippleAlpha(
        draggedAlpha = 0f,
        focusedAlpha = 0f,
        hoveredAlpha = 0f,
        pressedAlpha = 0f,
    )
}
