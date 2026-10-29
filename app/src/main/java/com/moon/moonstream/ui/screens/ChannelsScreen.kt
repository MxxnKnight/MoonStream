package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.model.FakeChannel
import com.moon.moonstream.ui.model.UnmatchedFile
import com.moon.moonstream.ui.model.fakeChannels
import com.moon.moonstream.ui.model.fakeUnmatched
import com.moon.moonstream.ui.state.AppUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase 1: channels with fake data.
 * TODO(Phase 3): real channel list, scan jobs, unmatched index from Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val channels = remember { mutableStateListOf<FakeChannel>().apply { addAll(fakeChannels) } }
    val unmatched = remember { mutableStateListOf<UnmatchedFile>().apply { addAll(fakeUnmatched) } }

    var scanningAll by remember { mutableStateOf(false) }
    var scanningId by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var linkFile by remember { mutableStateOf<UnmatchedFile?>(null) }

    fun fakeScan(id: String?, label: String) {
        if (id != null) scanningId = id else scanningAll = true
        scope.launch {
            delay(2000)
            if (id != null) scanningId = null else scanningAll = false
            Toast.makeText(context, "$label scan finished (fake)", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Channels", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(
                onClick = { fakeScan(null, "All channels") },
                enabled = !scanningAll && scanningId == null,
            ) {
                if (scanningAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Scanning…")
                } else {
                    Text("Scan All Now")
                }
            }
        }

        channels.forEach { ch ->
            ChannelRow(
                channel = ch,
                scanning = scanningId == ch.id,
                onToggle = { ch.enabled = !ch.enabled },
                onScan = { fakeScan(ch.id, ch.name) },
            )
        }

        Button(onClick = { showAdd }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add Channel")
        }

        Text("Unmatched content (${unmatched.size})",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        unmatched.forEach { f ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(f.fileName, style = MaterialTheme.typography.bodyMedium)
                    Text(f.size, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = {
                            Toast.makeText(context, "Create entry (fake)", Toast.LENGTH_SHORT).show()
                        }) { Text("Create entry") }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString("${AppUiState.manifestLink}/stream/${f.id}"))
                            Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy Link") }
                        TextButton(onClick = { linkFile = f }) { Text("Link to Title") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddChannelSheet(
            onDismiss = { showAdd = false },
            onAdd = { name, anime ->
                channels.add(FakeChannel("c${channels.size + 1}", name, 0, anime, true))
                Toast.makeText(context, "Channel added (fake)", Toast.LENGTH_SHORT).show()
                showAdd = false
            },
        )
    }

    linkFile?.let { f ->
        MetadataFixSheet(
            mode = FixMode.LINK,
            currentTitle = f.fileName,
            onDismiss = { linkFile = null },
            onApply = { picked ->
                unmatched.remove(f)
                Toast.makeText(context, "Linked to $picked (fake)", Toast.LENGTH_LONG).show()
                linkFile = null
            },
        )
    }
}

@Composable
private fun ChannelRow(
    channel: FakeChannel,
    scanning: Boolean,
    onToggle: () -> Unit,
    onScan: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.name, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold)
                    if (channel.isAnime) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ANIME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text("${channel.files} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onScan, enabled = !scanning) {
                if (scanning) CircularProgressIndicator(strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = "Scan Now")
            }
            Switch(checked = channel.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChannelSheet(onDismiss: () -> Unit, onAdd: (String, Boolean) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var anime by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add Channel", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Username, link or channel ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = anime, onCheckedChange = { anime = it })
                Spacer(Modifier.width(8.dp))
                Text("This is an anime channel")
            }
            Button(
                onClick = { onAdd(name.ifBlank { "New Channel" }, anime) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add")
            }
        }
    }
}
