package com.moon.moonstream.ui.screens

import android.widget.Toast
import com.moon.moonstream.ui.components.MsToast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors

/**
 * Diagnostics log viewer. Real lines from AppLog; auto-scrolls to the newest.
 */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lines = com.moon.moonstream.util.AppLog.lines
    val listState = rememberLazyListState()

    // Auto-scroll to the newest line whenever the log grows — but only if
    // the user is already near the bottom. Uses instant scrollToItem (not
    // animated) so rapid log bursts during audits don't stack animations
    // and kill the app (moon's crash).
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // If user is within 5 items of the bottom, follow the tail.
            if (lastVisible >= lines.size - 6) {
                listState.scrollToItem(lines.size - 1)
            }
        }
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
                    MsToast.show("Logs cleared")
                },
            )
        }

        Spacer(Modifier.height(4.dp))

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
            // Long-press any line to select and copy text. The container sits
            // outside the LazyColumn so selection works across rows.
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(lines, key = { it.time + it.tag + it.message }) { line ->
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
        }
    }
}
