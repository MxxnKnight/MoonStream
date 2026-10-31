package com.moon.moonstream.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.R
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val SplashGold = Color(0xFFFFC46A)
private val SplashGoldDeep = Color(0xFF8A5A18)

/**
 * Ambient splash screen, NoxMedia-style:
 * - two gold ambient glows cross-fading so the light seems to drift,
 * - a faint diagonal gold sheen sweeping across,
 * - the (now transparent) logo breathing gently,
 * - a gold dots-ring spinner + CONNECTING label,
 * - cinematic zoom-and-fade outro.
 *
 * All Canvas-free (plain gradient Boxes), and it only lives ~2 seconds,
 * so the handful of looping animations cost nothing in practice.
 * TODO(Phase 2): replace the fixed delay with the real TDLib session
 * restore — show this while the saved session reconnects, then go Home.
 */
@Composable
fun SplashScreen(onDone: () -> Unit, splashDurationMillis: Long = 2000L) {
    val infinite = rememberInfiniteTransition(label = "splash")

    // Ambient drift phase (0..2pi) — drives the cross-fading glows.
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    // Diagonal sheen sweep position (0..1 across the screen).
    val sheen by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sheen",
    )
    // Gentle logo breathing.
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    // Spinner progress.
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    // Entrance + outro states.
    val outroAlpha = remember { Animatable(1f) }
    val outroScale = remember { Animatable(1f) }
    val entranceAlpha = remember { Animatable(0f) }
    val entranceScale = remember { Animatable(0.86f) }

    LaunchedEffect(Unit) {
        launch {
            entranceAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            )
        }
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 550,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f),
                ),
            )
        }

        delay(splashDurationMillis)

        // Cinematic outro: scale up slightly + dissolve.
        launch {
            outroScale.animateTo(
                targetValue = 1.08f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
                ),
            )
        }
        outroAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        )
        onDone()
    }

    // Two glows cross-fade out of phase — the light appears to drift.
    val glowA = 0.05f + 0.05f * (0.5f + 0.5f * sin(phase))
    val glowB = 0.05f + 0.05f * (0.5f + 0.5f * sin(phase + Math.PI.toFloat()))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(outroAlpha.value)
            .scale(outroScale.value),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val glowCenterA = with(density) {
            Offset(maxWidth.toPx() * 0.72f, maxHeight.toPx() * 0.30f)
        }
        val glowCenterB = with(density) {
            Offset(maxWidth.toPx() * 0.28f, maxHeight.toPx() * 0.62f)
        }
        val glowRadius = with(density) { maxWidth.toPx() * 0.95f }
        val sheenStart = with(density) { Offset(maxWidth.toPx(), 0f) }
        val sheenEnd = with(density) { Offset(0f, maxHeight.toPx()) }

        // Layer A: drifting gold ambient glows.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to SplashGold.copy(alpha = glowA),
                        0.6f to SplashGoldDeep.copy(alpha = glowA * 0.5f),
                        1.0f to Color.Transparent,
                        center = glowCenterA,
                        radius = glowRadius,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to SplashGold.copy(alpha = glowB),
                        0.6f to SplashGoldDeep.copy(alpha = glowB * 0.5f),
                        1.0f to Color.Transparent,
                        center = glowCenterB,
                        radius = glowRadius,
                    ),
                ),
        )

        // Layer B: diagonal gold sheen sweep. The bright band slides 0 -> 1
        // along the diagonal; clamped stops keep the gradient valid at the
        // edges so it glides in from one corner and out the other.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        (sheen - 0.12f).coerceIn(0f, 1f) to Color.Transparent,
                        sheen.coerceIn(0.001f, 0.999f) to SplashGold.copy(alpha = 0.07f),
                        (sheen + 0.12f).coerceIn(0f, 1f) to Color.Transparent,
                        start = sheenStart,
                        end = sheenEnd,
                    ),
                ),
        )

        // Center: transparent logo + branding.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(entranceAlpha.value)
                .scale(entranceScale.value * breathe),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_trans),
                contentDescription = "MoonStream",
                modifier = Modifier.size(132.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "MoonStream",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // Bottom: dots-ring spinner + status.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(entranceAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DotsSpinner(progress = spin)
            Text(
                "CONNECTING TO TELEGRAM",
                color = MsColors.TextSecondary,
                fontSize = 10.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(54.dp))
        }
    }
}

/**
 * Ring of 8 dots with a brightness wave chasing around — one animated
 * float drives all dots, Canvas-free.
 */
@Composable
private fun DotsSpinner(progress: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(modifier = modifier.size(44.dp), contentAlignment = Alignment.Center) {
        repeat(8) { i ->
            val frac = (progress + i / 8f) % 1f
            val dotAlpha = 0.15f + 0.85f * frac
            val angleRad = Math.toRadians((i * 45 - 90).toDouble())
            Box(
                Modifier
                    .offset {
                        with(density) {
                            val r = 15.dp.toPx()
                            IntOffset(
                                (cos(angleRad) * r).toInt(),
                                (sin(angleRad) * r).toInt(),
                            )
                        }
                    }
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(SplashGold.copy(alpha = dotAlpha)),
            )
        }
    }
}
