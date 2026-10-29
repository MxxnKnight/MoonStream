package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.AnimeBadge
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.FixMode
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MetadataFixSheet
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.MsToggle
import com.moon.moonstream.ui.model.FakeChannel
import com.moon.moonstream.ui.model.UnmatchedFile
import com.moon.moonstream.ui.model.fakeChannels
import com.moon.moonstream.ui.model.fakeUnmatched
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Channels with fake data. The whole screen is ONE LazyColumn with stable keys
 * so the unmatched list stays smooth even with 400+ items.
 * TODO(Phase 3): real channel list, scan jobs, unmatched index from Room.
 */
private enum class ScanState { IDLE, SCANNING, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(onCreateEntry: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val channels = remember { mutableStateListOf<FakeChannel>().apply { addAll(fakeChannels) } }
    val unmatched = remember { mutableStateListOf<UnmatchedFile>().apply { addAll(fakeUnmatched) } }

    var scanAllState by remember { mutableStateOf(ScanState.IDLE) }
    var scanStates by remember { mutableStateOf<Map<String, ScanState>>(emptyMap()) }
    var showAdd by remember { mutableStateOf(false) }
    var linkFile by remember { mutableStateOf<UnmatchedFile?>(null) }

    fun fakeScan(id: String?, label: String) {
        if (id != null) {
            scanStates = scanStates + (id to ScanState.SCANNING)
        } else {
            scanAllState = ScanState.SCANNING
            scanStates = channels.associate { it.id to ScanState.SCANNING }
        }
        scope.launch {
            delay(2000)
            if (id != null) {
                scanStates = scanStates + (id to ScanState.DONE)
                delay(1500)
                scanStates = scanStates - id
            } else {
                scanAllState = ScanState.DONE
                scanStates = channels.associate { it.id to ScanState.DONE }
                delay(1500)
                scanAllState = ScanState.IDLE
                scanStates = emptyMap()
            }
            Toast.makeText(context, "$label scan finished (fake)", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Channels",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MsColors.TextPrimary,
                )
                ScanAllButton(
                    state = scanAllState,
                    busy = scanStates.values.any { it == ScanState.SCANNING },
                    onClick = { fakeScan(null, "All channels") },
                )
            }
        }

        items(channels, key = { it.id }) { ch ->
            ChannelRow(
                channel = ch,
                state = scanStates[ch.id] ?: ScanState.IDLE,
                onToggle = { ch.enabled = !ch.enabled },
                onScan = { fakeScan(ch.id, ch.name) },
            )
        }

        item(key = "addChannel") {
            LimePillButton(
                text = "+ Add Channel",
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "unmatchedHeader") {
            Text(
                "Unmatched content (${unmatched.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Dedicated entry point for custom content, separate from per-file actions.
        item(key = "addCustomContent") {
            LimePillButton(
                text = "＋ Add Custom Content",
                onClick = onCreateEntry,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        items(unmatched, key = { it.id }) { f ->
            UnmatchedFileRow(
                file = f,
                onCreateEntry = onCreateEntry,
                onCopyLink = {
                    clipboard.setText(AnnotatedString("${AppUiState.manifestLink}/stream/${f.id}"))
                    Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                },
                onLinkToTitle = { linkFile = f },
            )
        }

        item(key = "bottomPad") {
            Spacer(Modifier.height(8.dp))
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

/** Scan All Now button with spinner → checkmark states. */
@Composable
private fun ScanAllButton(state: ScanState, busy: Boolean, onClick: () -> Unit) {
    when (state) {
        ScanState.IDLE -> LimePillButton(
            text = "Scan All Now",
            onClick = onClick,
            enabled = !busy,
            fontSize = 14,
        )
        ScanState.SCANNING -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MsColors.Lime,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Scanning…",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
        }
        ScanState.DONE -> DonePill(text = "Done")
    }
}

@Composable
private fun ChannelRow(
    channel: FakeChannel,
    state: ScanState,
    onToggle: () -> Unit,
    onScan: () -> Unit,
) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (channel.isAnime) {
                        Spacer(Modifier.width(8.dp))
                        AnimeBadge()
                    }
                }
                Text(
                    "${channel.files} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
            }
            when (state) {
                ScanState.IDLE -> BareIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Scan Now",
                    tint = MsColors.TextSecondary,
                    onClick = onScan,
                )
                ScanState.SCANNING -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MsColors.Lime,
                )
                ScanState.DONE -> DoneCheck()
            }
            Spacer(Modifier.width(4.dp))
            MsToggle(checked = channel.enabled, onCheckedChange = { onToggle() })
        }
    }
}

/** Lime check badge that pops in with a spring when a scan finishes. */
@Composable
private fun DoneCheck(modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "donePop",
    )
    Box(
        modifier = modifier
            .size(28.dp)
            .scale(scale)
            .background(MsColors.Lime, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Scan done",
            tint = MsColors.OnLime,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DonePill(text: String) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "donePillPop",
    )
    Row(
        modifier = Modifier
            .scale(scale)
            .background(MsColors.Lime, CircleShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MsColors.OnLime,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MsColors.OnLime,
        )
    }
}

/** One unmatched file row — kept light for 400+ item lists. */
@Composable
private fun UnmatchedFileRow(
    file: UnmatchedFile,
    onCreateEntry: () -> Unit,
    onCopyLink: () -> Unit,
    onLinkToTitle: () -> Unit,
) {
    MsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                file.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                file.size,
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LimeTextButton(text = "Create entry", onClick = onCreateEntry)
                LimeTextButton(text = "Copy Link", onClick = onCopyLink)
                LimeTextButton(text = "Link to Title", onClick = onLinkToTitle)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChannelSheet(onDismiss: () -> Unit, onAdd: (String, Boolean) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var anime by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = "Add Channel") {
            MsTextField(
                value = name,
                onValueChange = { name = it },
                hint = "Username, link or channel ID",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MsToggle(checked = anime, onCheckedChange = { anime = it })
                Spacer(Modifier.width(10.dp))
                Text(
                    "This is an anime channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextPrimary,
                )
            }
            LimePillButton(
                text = "Add",
                onClick = { onAdd(name.ifBlank { "New Channel" }, anime) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
