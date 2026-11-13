package com.moon.webmock.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.webmock.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

// ---------------------------------------------------------------------------
// MoonStream design tokens, ported 1:1 from the app's Theme.kt / AppState.kt.
// The mockup reads MockUiState the way the app reads AppUiState, so the
// tokens behave identically (accent picker + AMOLED toggle included — a
// mockup can flip them to preview other themes).
// ---------------------------------------------------------------------------

enum class MsAccent(
    val label: String,
    val color: Color,
    val onColor: Color,
    val glowLight: Color,
    val glowDeep: Color,
) {
    LIME(
        "Moon Lime", Color(0xFF7DD956), Color(0xFF0E1116),
        glowLight = Color(0xFFB8F59B), glowDeep = Color(0xFF2E7A1E),
    ),
    EMBER(
        "Moon Ember", Color(0xFFFF8A3D), Color(0xFF0E1116),
        glowLight = Color(0xFFFFB37D), glowDeep = Color(0xFF9B4A1A),
    ),
    VIOLET(
        "Moon Violet", Color(0xFFB388FF), Color(0xFF0E1116),
        glowLight = Color(0xFFD3B8FF), glowDeep = Color(0xFF5E35B1),
    ),
    MOON_BLUE(
        "Moon Blue", Color(0xFF3B82F6), Color(0xFF0E1116),
        glowLight = Color(0xFF7DB4FF), glowDeep = Color(0xFF0B3FA0),
    ),
    MOON_GOLD(
        "Moon Gold", Color(0xFFFFAA33), Color(0xFF0E1116),
        glowLight = Color(0xFFFFD27D), glowDeep = Color(0xFF8A5A18),
    ),
    MOON_SILVER(
        "Moon Silver", Color(0xFFC9D4E3), Color(0xFF0E1116),
        glowLight = Color(0xFFFFFFFF), glowDeep = Color(0xFF6B7686),
    ),
}

/** Stand-in for the app's AppUiState (moon’s setup: Moon Blue + AMOLED on). */
object MockUiState {
    var accent by mutableStateOf(MsAccent.MOON_BLUE)
    var amoled by mutableStateOf(true)
    var amoledCardBorders by mutableStateOf(true)
}

object MsColors {
    val Accent: Color
        get() = MockUiState.accent.color
    val OnAccent: Color
        get() = MockUiState.accent.onColor
    val GlowLight: Color
        get() = MockUiState.accent.glowLight
    val GlowDeep: Color
        get() = MockUiState.accent.glowDeep
    val GlowDark: Color
        get() = MockUiState.accent.glowDeep.copy(alpha = 0.55f)

    val Bg: Color
        get() = if (MockUiState.amoled) Color(0xFF000000) else Color(0xFF0E1116)
    val Surface: Color
        get() = if (MockUiState.amoled) Color(0xFF0A0A0A) else Color(0xFF161B24)
    val SurfaceAlt: Color
        get() = if (MockUiState.amoled) Color(0xFF141414) else Color(0xFF1E2532)
    val Blue = Color(0xFF6A9BFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9AA0A6)
    val TrackOff: Color
        get() = if (MockUiState.amoled) Color(0xFF2A2A2A) else Color(0xFF3A4356)
    val Divider: Color
        get() = if (MockUiState.amoled) Color(0xFF1C1C1C) else Color(0xFF232A38)
    val CardBorder: Color
        get() = if (MockUiState.amoled && MockUiState.amoledCardBorders)
            Color.White.copy(alpha = 0.08f)
        else Color.Transparent
}

object MsRadii {
    val Card = 20.dp
    val Field = 16.dp
    val Poster = 16.dp
    val Chip = 8.dp
}

// Bundled fonts — the same .ttf files as the app's res/font.
@OptIn(ExperimentalResourceApi::class)
val Inter = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@OptIn(ExperimentalResourceApi::class)
val Doto = FontFamily(
    Font(Res.font.doto_regular, FontWeight.Normal),
    Font(Res.font.doto_bold, FontWeight.Bold),
)

@OptIn(ExperimentalResourceApi::class)
val Redaction35 = FontFamily(
    Font(Res.font.redaction35_regular, FontWeight.Normal),
    Font(Res.font.redaction35_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.redaction35_bold, FontWeight.Bold),
)

fun TextStyle.doto() = copy(fontFamily = Doto)

private fun Typography.withBrandFonts(): Typography {
    fun TextStyle.inter() = copy(fontFamily = Inter)
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
