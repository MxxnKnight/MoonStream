package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.state.AppUiState
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "MoonStream",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
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

        // Manifest link row
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Manifest URL", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        AppUiState.manifestLink,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(AppUiState.manifestLink))
                    Toast.makeText(context, "Manifest link copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy manifest link")
                }
            }
        }

        // Library stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatBox("Titles", "7,142", Modifier.weight(1f))
            StatBox("Channels", "4", Modifier.weight(1f))
            StatBox("Cache", "312 MB", Modifier.weight(1f))
        }

        Text(
            "Paste the manifest URL into Stremio or Nuvio to browse your library.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServerCard(on: Boolean, uptimeSecs: Int, onToggle: () -> Unit) {
    // Animated shimmer bar across the top of the card
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "shimmerX",
    )
    // Pulsing status dot
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.9f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    // Springy power switch
    val knobOffset by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "knob",
    )

    val dotColor = if (on) Color(0xFF4CAF50) else Color(0xFFF44336)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1B2A1B), Color(0xFF101A12)),
                    ),
                )
                .padding(20.dp),
        ) {
            // shimmer strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                Color.Transparent,
                            ),
                            startX = shimmerX * 1000f - 400f,
                            endX = shimmerX * 1000f,
                        ),
                    )
                    .align(Alignment.TopCenter),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .scale(if (on) pulseScale else 1f)
                            .alpha(if (on) pulseAlpha else 1f)
                            .background(dotColor, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (on) "Server ON" else "Server OFF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (on) "Running · ${formatUptime(uptimeSecs)} · 2 devices"
                    else "Tap the switch to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0BEC5),
                )
                Spacer(Modifier.height(20.dp))

                // Big power switch with ring
                val trackWidth = 120.dp
                val trackHeight = 64.dp
                Box(
                    modifier = Modifier
                        .size(trackWidth, trackHeight)
                        .clip(CircleShape)
                        .background(if (on) Color(0xFF2E7D32) else Color(0xFF37474F))
                        .border(
                            3.dp,
                            if (on) MaterialTheme.colorScheme.primary else Color(0xFF607D8B),
                            CircleShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggle() },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp + (64.dp * knobOffset), end = 6.dp)
                            .size(52.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⏻", color = Color(0xFF212121), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Port ${AppUiState.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5),
                )
            }
        }
    }
}

private fun formatUptime(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
