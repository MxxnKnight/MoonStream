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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.model.FakeStream
import com.moon.moonstream.ui.model.fakeTitles
import com.moon.moonstream.ui.state.AppUiState

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
            .verticalScroll(rememberScrollState()),
    ) {
        // Big poster header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(title.colorA, title.colorB))),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(8.dp),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
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

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${title.year} · ${title.type.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title.desc, style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { fixMode = FixMode.FIX_TITLE }) {
                    Text("Fix Metadata")
                }
                OutlinedButton(onClick = { showAddStream = true }) {
                    Text("Add Custom Stream")
                }
                OutlinedButton(onClick = { showAddSubtitle = true }) {
                    Text("Add Subtitle")
                }
            }

            Text("Streams (${streams.size})", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(stream.quality, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stream.fileName, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1)
                    Text(stream.size, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Link")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
            TextButton(onClick = onFixFile) {
                Text("wrong title?", style = MaterialTheme.typography.bodySmall)
            }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            fields.forEach { f ->
                OutlinedTextField(
                    value = values[f]!!.value,
                    onValueChange = { values[f]!!.value = it },
                    label = { Text(f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
