package com.moon.moonstream.ui.components

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleConfiguration
import androidx.compose.ui.graphics.Color

/**
 * No tap highlight anywhere in the app — pressed states render nothing.
 * (Mockup requirement; moon asked to remove the click-preview overlay entirely.)
 *
 * Compose 1.7 removed RippleTheme (error-level deprecation); the same
 * transparent, zero-alpha setup now lives in a RippleConfiguration,
 * provided app-wide from MainActivity.
 */
val NeutralRippleConfiguration = RippleConfiguration(
    color = Color.Transparent,
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0f,
        focusedAlpha = 0f,
        hoveredAlpha = 0f,
        pressedAlpha = 0f,
    ),
)
