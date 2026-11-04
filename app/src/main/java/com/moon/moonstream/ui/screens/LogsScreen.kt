package com.moon.moonstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.launch

/**
 * Diagnostics log viewer. Real lines from AppLog; auto-scrolls to the newest.
 */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    // SNAPSHOT (moon's bug 2026-11-05): AppLog.lines is a live ring buffer.
    // Observing it directly caused black screen on swipe-up (evicted items
    // invalidated LazyColumn scroll state). We snapshot on open; Refresh button updates.
    var snapshot by remember { mutableStateOf(com.moon.moonstream.util.AppLog.lines.toList()) }
    val lines = snapshot
    val listState = rememberLazyListState()
    var initialScrollDone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Scroll to bottom ONCE on first load (moon's bug 2026-11-04: black screen on open).
    // No auto-follow — the scroll-down button handles jumping to latest. The old
    // follow-tail logic fought manual scrolls and blanked the list (moon's bug 2026-11-04).
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && !initialScrollDone) {
            listState.scrollToItem(lines.size - 1)
            initialScrollDone = true
        }
    }

    fun refresh() {
        snapshot = com.moon.moonstream.util.AppLog.lines.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Text(
                "Logs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            MsOutlinePill(
                text = "Copy",
                fontSize = 13,
                onClick = {
                    val all = lines.joinToString("\n") { "${it.time} [${it.tag}] ${it.message}" }
                    clipboard.setText(AnnotatedString(all))
                    MsToast.show("Logs copied")
                },
            )
            Spacer(Modifier.width(8.dp))
            MsOutlinePill(
                text = "Clear",
                fontSize = 13,
                onClick = {
                    com.moon.moonstream.util.AppLog.clear()
                    refresh()
                    MsToast.show("Logs cleared")
                },
            )
            Spacer(Modifier.width(8.dp))
            MsOutlinePill(
                text = "Refresh",
                fontSize = 13,
                onClick = { refresh() },
            )
        }

        Spacer(Modifier.height(4.dp))

        // List + scroll-to-bottom button share ONE Box (moon's bug 2026-11-05:
        // black screen while scrolling up). The button used to be a separate
        // full-size Box in the Column right after the weighted list Box, so as
        // soon as it appeared it took the whole remaining height and squeezed
        // the LazyColumn to 0dp — all lines vanished, only the button stayed.
        // Overlaying it here makes the button cost zero layout space.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (lines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No log lines yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(lines, key = { it.id }) { line ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = line.time,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MsColors.TextSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "[${line.tag}]",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MsColors.Accent,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = line.message,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MsColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Scroll-to-bottom button (moon's request 2026-11-03): appears when
            // scrolled up, jumps to the latest logs. iOS-style small circle, not
            // an M3 FAB. Sits above the floating bottom pill.
            val showScrollDown by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lines.isNotEmpty() && lastVisible < lines.size - 6
                }
            }
            if (showScrollDown) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 110.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MsColors.Accent)
                        .clickable {
                            scope.launch {
                                if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "Scroll to bottom",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
