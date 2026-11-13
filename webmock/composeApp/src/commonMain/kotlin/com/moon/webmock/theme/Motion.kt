package com.moon.webmock.theme

import androidx.compose.runtime.Composable

// Web stand-in for the app's Motion.kt (which reads Android's
// ActivityManager + animation scale — not available on web). The mockup
// always plays the full animation.
@Composable
fun rememberReducedMotion(): Boolean = false

fun motionDurationMs(base: Int, reduced: Boolean): Int {
    if (!reduced) return base
    return (base * 0.6f).toInt().coerceAtLeast(60)
}
