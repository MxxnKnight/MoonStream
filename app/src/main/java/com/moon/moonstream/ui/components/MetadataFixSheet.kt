package com.moon.moonstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.model.fakeSearchResults
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii

enum class FixMode { FIX_TITLE, FIX_FILE, LINK }

/**
 * Shared metadata search sheet.
 * - FIX_TITLE: re-match a wrongly indexed title
 * - FIX_FILE: re-match a single wrongly indexed file
 * - LINK: link an unmatched file to an existing entry / new episode / new entry
 *
 * TODO(Phase 3): wire search to TMDB/TVDB/Kitsu and Apply to Library.replaceMetadata().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataFixSheet(
    mode: FixMode,
    currentTitle: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }

    val heading = when (mode) {
        FixMode.FIX_TITLE -> "Fix Metadata — This Title"
        FixMode.FIX_FILE -> "Fix Metadata — This File"
        FixMode.LINK -> "Link to Title"
    }
    val results = fakeSearchResults.filter { it.contains(query, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = heading) {
            Text(
                text = when (mode) {
                    FixMode.FIX_TITLE -> "Currently: $currentTitle. Search and pick the correct title — the entry will be merged and its files moved."
                    FixMode.FIX_FILE -> "Currently filed under: $currentTitle. Search and pick the correct title — this file will be moved there."
                    FixMode.LINK -> "Search an existing title to attach this file to it, or create a new entry from a result."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            MsTextField(
                value = query,
                onValueChange = { query = it; selected = null },
                hint = "Search title",
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(modifier = Modifier.height(232.dp)) {
                items(results) { r ->
                    val isSel = selected == r
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MsRadii.Chip))
                            .clickable { selected = r }
                            .background(
                                if (isSel) MsColors.Accent.copy(alpha = 0.14f)
                                else MsColors.SurfaceAlt,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = r,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MsColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSel) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MsColors.Accent,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (mode == FixMode.LINK) {
                Text(
                    "Or attach as:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MsOutlinePill(
                        text = "New episode",
                        onClick = { onApply("new-episode:" + (selected ?: query)) },
                        enabled = selected != null || query.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                    MsOutlinePill(
                        text = "New entry",
                        onClick = { onApply("new-entry:" + (selected ?: query)) },
                        enabled = selected != null || query.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            LimePillButton(
                text = if (mode == FixMode.LINK) "Link to selected title" else "Apply — merge & move files",
                onClick = { selected?.let(onApply) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
