package com.moon.moonstream.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * One place that decides how much animation the phone can afford
 * (moon's 2026-11-11 — "optimise it to use low end phones with low ram and
 * gpu").
 *
 * Two signals, both free to read:
 * - ActivityManager.isLowRamDevice: the phone itself says it is short on RAM
 *   (the G96 is a "low RAM" device), so every slide is shortened and the
 *   expensive extras are dropped.
 * - ANIMATOR_DURATION_SCALE == 0: the user turned animations off in Developer
 *   options / Accessibility. Respecting that is correctness, not just speed.
 *
 * No new setting in the app: the OS already has this switch, and a second one
 * would only be another thing to explain.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { computeReducedMotion(context) }
}

fun computeReducedMotion(context: Context): Boolean = isLowRam(context) || animationsOff(context)

fun isLowRam(context: Context): Boolean = try {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.isLowRamDevice == true
} catch (e: Exception) {
    false
}

fun animationsOff(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (e: Exception) {
    false
}

/**
 * Scales a duration for the current device: full length normally, about 60%
 * on a low-RAM phone (still visible, but cheaper), and 0 when the system says
 * "no animations".
 */
fun motionDurationMs(base: Int, reduced: Boolean): Int {
    if (!reduced) return base
    return (base * 0.6f).toInt().coerceAtLeast(60)
}
