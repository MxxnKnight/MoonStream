package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.CopyMorphButton
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.PowerSwitch
import com.moon.moonstream.ui.components.PulsingDot
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Server home screen with fake data.
 * - OFF: fully static card (no shimmer, no pulsing, flat dark).
 * - ON: colored + animated (green gradient, shimmer, pulsing dot), and the
 *   manifest card + stats dashboard appear below.
 * TODO(Phase 4): wire the switch to the real foreground service + socket
 * servers; replace fake stats with real telemetry.
 */
@Composable
fun ServerScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val on = AppUiState.serverOn
    var uptimeSecs by remember { mutableStateOf(0) }

    LaunchedEffect(on) {
        if (on) {
            while (true) {
                delay(1000)
                uptimeSecs++
            }
        } else {
            uptimeSecs = 0
        }
    }

    val onToggle = {
        AppUiState.serverOn = !AppUiState.serverOn
        Toast.makeText(
            context,
            if (AppUiState.serverOn) "Server starting… (fake)" else "Server stopped (fake)",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MoonStream",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
            )
            MsSectionEyebrow(if (on) "RUNNING" else "IDLE")
        }

        if (on) {
            ServerCardOn(uptimeSecs = uptimeSecs, onToggle = onToggle)
        } else {
            ServerCardOff(onToggle = onToggle)
        }

        // Everything below only exists while the server is ON.
        AnimatedVisibility(
            visible = on,
            enter = fadeIn(tween(300)) + expandVertically(tween(350)),
            exit = fadeOut(tween(250)) + shrinkVertically(tween(300)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MsSectionEyebrow("ADDON LINK")
                // THE single addon link UI (v0.3.1: no duplicates anywhere else).
                MsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Manifest URL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MsColors.TextSecondary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                AppUiState.manifestLink,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MsColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            CopyMorphButton(
                                showLabel = false,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(AppUiState.manifestLink))
                                },
                            )
                        }
                    }
                }

                ServerStatsCard(uptimeSecs = uptimeSecs)

                MsSectionEyebrow("LIBRARY")
                LibraryDashboardCard()

                Text(
                    "Paste the manifest URL into Stremio or Nuvio to browse your library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                // Room for the floating pill: last content scrolls fully into view.
                Spacer(Modifier.height(106.dp))
            }
        }
    }
}

/** Small caps section eyebrow for the home screen hierarchy. */
@Composable
private fun MsSectionEyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MsColors.TextSecondary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** OFF state: completely static — flat dark card, static red dot, no shimmer. */
@Composable
private fun ServerCardOff(onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MsRadii.Card))
            .background(MsColors.Surface)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Static dot — deliberately NOT PulsingDot.
                Box(
                    Modifier
                        .size(14.dp)
                        .background(Color(0xFFF44336), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Server OFF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap the switch to start",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(22.dp))

            PowerSwitch(on = false, onToggle = onToggle)

            Spacer(Modifier.height(10.dp))
            Text(
                "Port ${AppUiState.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
    }
}

/** ON state: accent-tinted gradient, shimmer strip, pulsing dot. */
@Composable
private fun ServerCardOn(uptimeSecs: Int, onToggle: () -> Unit) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "shimmerX",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MsRadii.Card))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MsColors.Accent.copy(alpha = 0.14f),
                        MsColors.Surface,
                    ),
                ),
            )
            .padding(22.dp),
    ) {
        // Shimmer strip pinned to the top edge (ON only).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            MsColors.Accent.copy(alpha = 0.75f),
                            Color.Transparent,
                        ),
                        startX = shimmerX * 1200f - 480f,
                        endX = shimmerX * 1200f,
                    ),
                )
                .align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = MsColors.Accent, pulse = true)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Server ON",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Running · ${formatUptime(uptimeSecs)} · 2 devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(22.dp))

            PowerSwitch(on = true, onToggle = onToggle)

            Spacer(Modifier.height(10.dp))
            Text(
                "Port ${AppUiState.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
    }
}

/**
 * Server telemetry card (ON only), NoxMedia-style information architecture:
 * two large metrics (uptime, speed), a session/all-time shelf, and the URL row.
 * All values fake for now.
 * TODO(Phase 4): feed from the real server telemetry.
 */
@Composable
private fun ServerStatsCard(uptimeSecs: Int) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LargeMetric(value = formatUptime(uptimeSecs), label = "uptime", modifier = Modifier.weight(1f))
                LargeMetric(value = "0 B/s", label = "speed", modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MsColors.Bg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallMetric(label = "session", value = "0.0 MB")
                Box(
                    Modifier
                        .size(3.dp)
                        .background(MsColors.Divider, CircleShape),
                )
                SmallMetric(label = "all-time", value = "1.33 GB")
            }
            // v0.3.1: the URL row lived here too — removed. The manifest card
            // above is the ONE addon link UI; no duplicates.
        }
    }
}

@Composable
private fun LargeMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
        )
    }
}

@Composable
private fun SmallMetric(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MsColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MsColors.TextPrimary,
        )
    }
}

/**
 * Purple-gradient library dashboard card with count-up animations,
 * NoxMedia-style. Fake numbers for now.
 * TODO(Phase 3): feed from the real Room library stats.
 */
@Composable
private fun LibraryDashboardCard() {
    val files = remember { Animatable(0f) }
    val movies = remember { Animatable(0f) }
    val shows = remember { Animatable(0f) }
    val eps = remember { Animatable(0f) }
    val sizeTb = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        files.animateTo(7142f, tween(1200))
        movies.animateTo(5380f, tween(900))
        shows.animateTo(175f, tween(900))
        eps.animateTo(2236f, tween(900))
        sizeTb.animateTo(10f, tween(1100))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MsRadii.Card))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF7C3AED), Color(0xFF4C1D95)),
                ),
            )
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF3E8FF),
                    )
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Light,
                        color = Color(0xCCE9D5FF),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                // v0.5.0: total size moved to the top-right corner (moon's ask)
                // so the breakdown below owns the full width and can never
                // truncate or hide behind the size again.
                Text(
                    text = String.format(Locale.US, "%.1f TB", sizeTb.value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatCount(files.value.toInt()),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE9D5FF),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                // Animated equalizer bars (Canvas-free: ui-graphics in this
                // sandbox can't do Canvas strokes, so bars are plain Boxes).
                EqualizerBars(modifier = Modifier.size(width = 110.dp, height = 40.dp))
            }

            // Breakdown owns the FULL width now — nothing competes with it.
            Text(
                text = "${formatCount(movies.value.toInt())} Movies  ·  " +
                    "${formatCount(shows.value.toInt())} Shows  ·  " +
                    "${formatCount(eps.value.toInt())} Ep",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFDDD6FE),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Fake-data animated equalizer: 9 bars bouncing out of phase. */
@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(9) { i ->
            val h by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700 + i * 60),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eqBar$i",
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight(h)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

private fun formatCount(n: Int): String =
    String.format(Locale.US, "%,d", n)

private fun formatUptime(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
