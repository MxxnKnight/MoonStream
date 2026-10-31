package com.moon.moonstream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.R
import com.moon.moonstream.ui.state.AppUiState

// ---------------------------------------------------------------------------
// Design tokens — taken from the approved mockup. Use these, never raw
// Material3 defaults.
//
// v0.3.1: Accent + backgrounds are DYNAMIC — they read AppUiState (accent
// picker + pure AMOLED toggle, persisted). Because the backing fields are
// Compose mutableState, every composable that reads MsColors recomposes
// instantly when the user changes them in Settings → Appearance.
// ---------------------------------------------------------------------------
object MsColors {
    /** Primary accent — follows the Settings → Appearance accent picker. */
    val Accent: Color
        get() = AppUiState.accent.color

    /** Text/icons drawn ON the accent (dark, readable on all accents). */
    val OnAccent: Color
        get() = AppUiState.accent.onColor

    /**
     * Signature glow family — follows the accent picker. Drives the server
     * card border/inner glow and the splash ambient animation, so the app's
     * "aura" matches the accent. Works with AMOLED on or off.
     */
    val GlowLight: Color
        get() = AppUiState.accent.glowLight
    val GlowDeep: Color
        get() = AppUiState.accent.glowDeep
    /** Darkest glow tone, for gradient edge stops. */
    val GlowDark: Color
        get() = AppUiState.accent.glowDeep.copy(alpha = 0.55f)

    val Bg: Color
        get() = if (AppUiState.amoled) Color(0xFF000000) else Color(0xFF0E1116)
    val Surface: Color
        get() = if (AppUiState.amoled) Color(0xFF0A0A0A) else Color(0xFF161B24)
    val SurfaceAlt: Color
        get() = if (AppUiState.amoled) Color(0xFF141414) else Color(0xFF1E2532)
    val Blue = Color(0xFF6A9BFF)        // sparing use (e.g. ANIME badge)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9AA0A6)
    val TrackOff: Color
        get() = if (AppUiState.amoled) Color(0xFF2A2A2A) else Color(0xFF3A4356)
    val Divider: Color
        get() = if (AppUiState.amoled) Color(0xFF1C1C1C) else Color(0xFF232A38)
}

object MsRadii {
    val Card = 20.dp
    val Field = 16.dp
    val Poster = 16.dp
    val Chip = 8.dp
}

// ---------------------------------------------------------------------------
// Bundled fonts. The target device's handwriting system font restyles any UI
// that falls back to the system default — so every family below is bundled
// and forced through the typography styles; no style may fall back to the
// system default.
//   - Inter: body/UI text (readability)
//   - Doto: display + headline styles (main screen titles, NoxMedia-style)
//   - Redaction35: accents/metas (serif counterpoint, applied per-screen)
// ---------------------------------------------------------------------------
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val Doto = FontFamily(
    Font(R.font.doto_regular, FontWeight.Normal),
    Font(R.font.doto_bold, FontWeight.Bold),
)

val Redaction35 = FontFamily(
    Font(R.font.redaction35_regular, FontWeight.Normal),
    Font(R.font.redaction35_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.redaction35_bold, FontWeight.Bold),
)

private fun Typography.withBrandFonts(): Typography {
    fun TextStyle.inter() = copy(fontFamily = Inter)
    fun TextStyle.doto() = copy(fontFamily = Doto)
    return copy(
        displayLarge = displayLarge.doto(),
        displayMedium = displayMedium.doto(),
        displaySmall = displaySmall.doto(),
        headlineLarge = headlineLarge.doto(),
        headlineMedium = headlineMedium.doto(),
        headlineSmall = headlineSmall.doto(),
        titleLarge = titleLarge.inter(),
        titleMedium = titleMedium.inter(),
        titleSmall = titleSmall.inter(),
        bodyLarge = bodyLarge.inter(),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(),
        labelMedium = labelMedium.inter(),
        labelSmall = labelSmall.inter(),
    )
}

@Composable
fun MoonStreamTheme(content: @Composable () -> Unit) {
    // Built inside the composable (not a static val) so the scheme follows
    // the live accent / AMOLED state on every recomposition.
    val scheme = darkColorScheme(
        primary = MsColors.Accent,
        onPrimary = MsColors.OnAccent,
        secondary = MsColors.Blue,
        background = MsColors.Bg,
        surface = MsColors.Surface,
        surfaceVariant = MsColors.SurfaceAlt,
        onBackground = MsColors.TextPrimary,
        onSurface = MsColors.TextPrimary,
        onSurfaceVariant = MsColors.TextSecondary,
        outline = MsColors.Divider,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography().withBrandFonts(),
        content = content,
    )
}
