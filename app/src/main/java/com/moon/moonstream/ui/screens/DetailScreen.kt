package com.moon.moonstream.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.QualityChip
import com.moon.moonstream.ui.model.FakeStream
import com.moon.moonstream.ui.model.fakeTitles
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors

/**
 * Phase 1: title detail with fake data.
 * TODO(Phase 3): load title + streams from Room; wire Play/Copy/Delete,
 * Add Custom Stream, Add Subtitle, Fix Metadata to the real library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(titleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val title = fakeTitles.find { it.id == titleId } ?: fakeTitles.first()
    val streams = remember { mutableStateListOf<FakeStream>().apply { addAll(title.streams) } }

    var fixMode by remember { mutableStateOf<FixMode?>(null) }
    var fixFileName by remember { mutableStateOf("") }
    var showAddStream by remember { mutableStateOf(false) }
    var showAddSubtitle by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // Gradient poster header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Brush.verticalGradient(listOf(title.colorA, title.colorB))),
        ) {
            // Darken the bottom so the title reads well
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    ),
            )
            BareIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                onClick = onBack,
                modifier = Modifier.padding(8.dp),
            )
            Text(
                text = title.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${title.year} · ${title.type.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                Text(
                    title.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextPrimary,
                )
            }

            // Action row: one lime pill + two outlined pills, equal widths.
            // (v0.1.0 bug: "Add Subtitle" wrapped into a circle here.)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LimePillButton(
                    text = "Fix Metadata",
                    onClick = { fixMode = FixMode.FIX_TITLE },
                    modifier = Modifier.weight(1f),
                    fontSize = 13,
                )
                MsOutlinePill(
                    text = "Add Custom Stream",
                    onClick = { showAddStream = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 12,
                )
                MsOutlinePill(
                    text = "Add Subtitle",
                    onClick = { showAddSubtitle = true },
                    modifier = Modifier.weight(1f),
                    fontSize = 12,
                )
            }

            Text(
                "Streams (${streams.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
            )

            streams.forEach { s ->
                StreamRow(
                    stream = s,
                    onPlay = {
                        Toast.makeText(context, "Playing (fake)…", Toast.LENGTH_SHORT).show()
                    },
                    onCopy = {
                        val url = "${AppUiState.manifestLink}/stream/${s.id}"
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        streams.remove(s)
                        Toast.makeText(context, "Stream deleted (fake)", Toast.LENGTH_SHORT).show()
                    },
                    onFixFile = {
                        fixFileName = s.fileName
                        fixMode = FixMode.FIX_FILE
                    },
                )
            }
        }
    }

    fixMode?.let { mode ->
        MetadataFixSheet(
            mode = mode,
            currentTitle = if (mode == FixMode.FIX_FILE) fixFileName else title.title,
            onDismiss = { fixMode = null },
            onApply = { picked ->
                Toast.makeText(context, "Merged into $picked (fake)", Toast.LENGTH_LONG).show()
                fixMode = null
            },
        )
    }

    if (showAddStream) {
        SimpleFormSheet(
            title = "Add Custom Stream",
            fields = listOf("Telegram file link (t.me/…)", "Quality label (e.g. 1080p)"),
            onDismiss = { showAddStream = false },
            onSave = {
                Toast.makeText(context, "Custom stream added (fake)", Toast.LENGTH_SHORT).show()
                showAddStream = false
            },
        )
    }

    if (showAddSubtitle) {
        SimpleFormSheet(
            title = "Add Subtitle",
            fields = listOf("Subtitle file link", "Language"),
            onDismiss = { showAddSubtitle = false },
            onSave = {
                Toast.makeText(context, "Subtitle added (fake)", Toast.LENGTH_SHORT).show()
                showAddSubtitle = false
            },
        )
    }
}

@Composable
private fun StreamRow(
    stream: FakeStream,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onFixFile: () -> Unit,
) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityChip(stream.quality)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stream.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stream.size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                BareIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    onClick = onPlay,
                )
                BareIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = "Copy Link",
                    onClick = onCopy,
                )
                BareIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    onClick = onDelete,
                )
            }
            LimeTextButton(
                text = "wrong title?",
                onClick = onFixFile,
                fontSize = 13,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleFormSheet(
    title: String,
    fields: List<String>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val values = remember { fields.associateWith { mutableStateOf("") } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = title) {
            fields.forEach { f ->
                MsTextField(
                    value = values[f]!!.value,
                    onValueChange = { values[f]!!.value = it },
                    hint = f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LimePillButton(
                text = "Save",
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
