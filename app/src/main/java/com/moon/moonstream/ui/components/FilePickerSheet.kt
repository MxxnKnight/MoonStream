package com.moon.moonstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.ui.model.PrefillFile
import com.moon.moonstream.ui.theme.MsColors
import java.util.Locale

/**
 * Bottom sheet to pick Telegram files for a custom entry (or to attach to
 * an existing title). Lists unmatched files — the files the scanner
 * couldn't identify, which is exactly what custom content is for.
 *
 * @param subtitleMode when true, the sheet is picking subtitle files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    files: List<UnmatchedFileEntity>,
    subtitleMode: Boolean,
    onDismiss: () -> Unit,
    onPick: (List<PrefillFile>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(setOf<Long>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(
            title = if (subtitleMode) "Pick subtitle files" else "Pick files",
        ) {
            if (files.isEmpty()) {
                Text(
                    "No unmatched files. Scan a channel first — files the " +
                        "scanner can't identify land here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(files, key = { it.id }) { f ->
                        val checked = f.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MsColors.SurfaceAlt)
                                .clickable {
                                    selected = if (checked) selected - f.id
                                    else selected + f.id
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MsColors.Accent,
                                    uncheckedColor = MsColors.TextSecondary,
                                ),
                            )
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    f.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MsColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    formatBytes(f.fileSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MsColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LimePillButton(
                    text = if (selected.isEmpty()) "Pick files"
                    else "Add ${selected.size} file${if (selected.size == 1) "" else "s"}",
                    onClick = {
                        val picked = files
                            .filter { it.id in selected }
                            .map {
                                PrefillFile(
                                    channelId = it.channelId,
                                    messageId = it.messageId,
                                    fileName = it.fileName,
                                    fileSize = it.fileSize,
                                    isSubtitle = subtitleMode,
                                )
                            }
                        onPick(picked)
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) {
        v /= 1024
        u++
    }
    return if (u == 0) "$bytes B"
    else String.format(Locale.US, "%.1f %s", v, units[u])
}
