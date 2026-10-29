package com.moon.moonstream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.R

// ---------------------------------------------------------------------------
// Design tokens — taken from the approved mockup. Use these, never raw
// Material3 defaults.
// ---------------------------------------------------------------------------
object MsColors {
    val Bg = Color(0xFF0E1116)          // app background (near-black)
    val Surface = Color(0xFF161B24)     // cards (flat, no elevation shadows)
    val SurfaceAlt = Color(0xFF1E2532)  // slightly lifted surfaces (fields, sheets)
    val Lime = Color(0xFF7DD956)        // primary accent
    val OnLime = Color(0xFF0E1116)      // dark text on lime buttons
    val Blue = Color(0xFF6A9BFF)        // sparing use (e.g. ANIME badge)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9AA0A6)
    val TrackOff = Color(0xFF3A4356)
    val Divider = Color(0xFF232A38)
}

object MsRadii {
    val Card = 20.dp
    val Field = 16.dp
    val Poster = 16.dp
    val Chip = 8.dp
}

// ---------------------------------------------------------------------------
// Bundled font. The target device's handwriting system font restyles any UI
// that falls back to the system default — so Inter is forced through EVERY
// MaterialTheme typography style below, and every custom component uses these
// styles (or an explicit Inter fontFamily) instead of raw Text defaults.
// ---------------------------------------------------------------------------
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun Typography.withInter(): Typography {
    fun TextStyle.i() = copy(fontFamily = Inter)
    return copy(
        displayLarge = displayLarge.i(),
        displayMedium = displayMedium.i(),
        displaySmall = displaySmall.i(),
        headlineLarge = headlineLarge.i(),
        headlineMedium = headlineMedium.i(),
        headlineSmall = headlineSmall.i(),
        titleLarge = titleLarge.i(),
        titleMedium = titleMedium.i(),
        titleSmall = titleSmall.i(),
        bodyLarge = bodyLarge.i(),
        bodyMedium = bodyMedium.i(),
        bodySmall = bodySmall.i(),
        labelLarge = labelLarge.i(),
        labelMedium = labelMedium.i(),
        labelSmall = labelSmall.i(),
    )
}

private val DarkColors = darkColorScheme(
    primary = MsColors.Lime,
    onPrimary = MsColors.OnLime,
    secondary = MsColors.Blue,
    background = MsColors.Bg,
    surface = MsColors.Surface,
    surfaceVariant = MsColors.SurfaceAlt,
    onBackground = MsColors.TextPrimary,
    onSurface = MsColors.TextPrimary,
    onSurfaceVariant = MsColors.TextSecondary,
    outline = MsColors.Divider,
)

@Composable
fun MoonStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography().withInter(),
        content = content,
    )
}
