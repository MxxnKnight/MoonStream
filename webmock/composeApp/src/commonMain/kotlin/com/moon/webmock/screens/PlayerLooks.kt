package com.moon.webmock.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Player looks (moon's 2026-11-19): three faces for the SAME player —
// same buttons, same behaviour, look only. Current is the approved
// mpvEx-style design, kept as the baseline; Air is lighter, Bar is bolder.
// The look is a plain parameter to PlayerScreen, so switching looks never
// resets playback state (it lives in PlayerLandscapeMock's remember).
// Sheets, drawers, trimmer, NextUp and the unlock slider are identical
// in every look — only the main control layer changes.
// ---------------------------------------------------------------------------

/**
 * Every visual value the mock player draws from. Colours that depend on
 * the theme are NOT stored here: [playFillAccent] means "MsColors.Accent,
 * read live at composition", so the accent picker still recolours Bar.
 */
data class PlayerLook(
    val id: String,
    val label: String,
    val blurb: String,
    // Icon buttons (top bar + bottom row).
    val buttonSize: Dp,
    val buttonIconSize: Dp,
    val buttonFillAlpha: Float,
    val buttonBorder: Boolean,
    // Top bar.
    val topScrimAlpha: Float,
    val titlePillAlpha: Float,
    val topSpacing: Dp,
    // Center transport.
    val centerSpacing: Dp,
    val seek10Size: Dp,
    val seek10IconSize: Dp,
    val playSize: Dp,
    val playIconSize: Dp,
    val playFillAccent: Boolean,
    val playFillAlpha: Float,
    val playBorderAlpha: Float,
    val playBorderWidth: Dp,
    // Bottom cluster.
    val bottomScrimAlpha: Float,
    val bottomPanelAlpha: Float,
    val bottomSpacing: Dp,
    val timeFontSize: TextUnit,
    val subBottomPadding: Dp,
    // Seekbar.
    val seekRestHeight: Dp,
    val seekScrubHeight: Dp,
    // Skip / Resume pills.
    val pillFillAlpha: Float,
)

object PlayerLooks {
    /** The approved design, exactly as it is today — the baseline. */
    val current = PlayerLook(
        id = "current",
        label = "Current",
        blurb = "The design you know — mpvEx-style, as it is in the app.",
        buttonSize = 45.dp,
        buttonIconSize = 20.dp,
        buttonFillAlpha = 0.12f,
        buttonBorder = true,
        topScrimAlpha = 0.65f,
        titlePillAlpha = 0.45f,
        topSpacing = 8.dp,
        centerSpacing = 64.dp,
        seek10Size = 52.dp,
        seek10IconSize = 32.dp,
        playSize = 76.dp,
        playIconSize = 38.dp,
        playFillAccent = false,
        playFillAlpha = 0.14f,
        playBorderAlpha = 0f,
        playBorderWidth = 0.dp,
        bottomScrimAlpha = 0.7f,
        bottomPanelAlpha = 0f,
        bottomSpacing = 10.dp,
        timeFontSize = 12.sp,
        subBottomPadding = 132.dp,
        seekRestHeight = 4.dp,
        seekScrubHeight = 10.dp,
        pillFillAlpha = 0.14f,
    )

    /** Lighter: near-ghost buttons, slim bar, plain title, airy spacing. */
    val air = PlayerLook(
        id = "air",
        label = "Air",
        blurb = "Lighter — ghost buttons, slim bar, plain title, more air.",
        buttonSize = 45.dp,
        buttonIconSize = 20.dp,
        buttonFillAlpha = 0.06f,
        buttonBorder = false,
        topScrimAlpha = 0.35f,
        titlePillAlpha = 0f,
        topSpacing = 12.dp,
        centerSpacing = 80.dp,
        seek10Size = 52.dp,
        seek10IconSize = 32.dp,
        playSize = 64.dp,
        playIconSize = 32.dp,
        playFillAccent = false,
        playFillAlpha = 0f,
        playBorderAlpha = 0.5f,
        playBorderWidth = 1.5.dp,
        bottomScrimAlpha = 0.45f,
        bottomPanelAlpha = 0f,
        bottomSpacing = 14.dp,
        timeFontSize = 11.sp,
        subBottomPadding = 124.dp,
        seekRestHeight = 2.dp,
        seekScrubHeight = 6.dp,
        pillFillAlpha = 0.08f,
    )

    /** Bolder: big buttons, solid bottom panel, thick bar, Accent play. */
    val bar = PlayerLook(
        id = "bar",
        label = "Bar",
        blurb = "Bolder — big buttons, solid bottom panel, thick bar.",
        buttonSize = 56.dp,
        buttonIconSize = 24.dp,
        buttonFillAlpha = 0.16f,
        buttonBorder = true,
        topScrimAlpha = 0.65f,
        titlePillAlpha = 0.60f,
        topSpacing = 8.dp,
        centerSpacing = 56.dp,
        seek10Size = 60.dp,
        seek10IconSize = 36.dp,
        playSize = 88.dp,
        playIconSize = 44.dp,
        playFillAccent = true,
        playFillAlpha = 0f,
        playBorderAlpha = 0f,
        playBorderWidth = 0.dp,
        bottomScrimAlpha = 0f,
        bottomPanelAlpha = 0.88f,
        bottomSpacing = 8.dp,
        timeFontSize = 13.sp,
        subBottomPadding = 148.dp,
        seekRestHeight = 8.dp,
        seekScrubHeight = 12.dp,
        pillFillAlpha = 0.20f,
    )

    val all = listOf(current, air, bar)
}
