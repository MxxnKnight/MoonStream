package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsStatBox
import com.moon.moonstream.ui.components.PowerSwitch
import com.moon.moonstream.ui.components.PulsingDot
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii
import kotlinx.coroutines.delay

/**
 * Phase 1: server home screen with fake data.
 * TODO(Phase 4): wire the switch to the real foreground service + socket servers.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "MoonStream",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )

        ServerCard(
            on = on,
            uptimeSecs = uptimeSecs,
            onToggle = {
                AppUiState.serverOn = !AppUiState.serverOn
                Toast.makeText(
                    context,
                    if (AppUiState.serverOn) "Server starting… (fake)" else "Server stopped (fake)",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

        // Manifest URL card
        MsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Manifest URL",
                        style = MaterialTheme.typography.labelLarge,
                        color = MsColors.TextSecondary,
                    )
                    Text(
                        AppUiState.manifestLink,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BareIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = "Copy manifest link",
                    tint = MsColors.TextSecondary,
                    onClick = {
                        clipboard.setText(AnnotatedString(AppUiState.manifestLink))
                        Toast.makeText(context, "Manifest link copied", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MsStatBox("Titles", "7,142", Modifier.weight(1f))
            MsStatBox("Channels", "4", Modifier.weight(1f))
            MsStatBox("Cache", "312 MB", Modifier.weight(1f))
        }

        Text(
            "Paste the manifest URL into Stremio or Nuvio to browse your library.",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
        )
    }
}

@Composable
private fun ServerCard(on: Boolean, uptimeSecs: Int, onToggle: () -> Unit) {
    // Animated shimmer line across the top of the card
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "shimmerX",
    )

    val dotColor = if (on) MsColors.Lime else Color(0xFFF44336)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MsRadii.Card))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A2B1D), Color(0xFF0F170F)),
                ),
            )
            .padding(20.dp),
    ) {
        // Shimmer strip pinned to the top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            MsColors.Lime.copy(alpha = 0.75f),
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
                PulsingDot(color = dotColor, pulse = true)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (on) "Server ON" else "Server OFF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (on) "Running · ${formatUptime(uptimeSecs)} · 2 devices"
                else "Tap the switch to start",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(22.dp))

            PowerSwitch(on = on, onToggle = onToggle)

            Spacer(Modifier.height(10.dp))
            Text(
                "Port ${AppUiState.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
    }
}

private fun formatUptime(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
