package com.moon.moonstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moon.moonstream.index.match.EnrichedMeta
import com.moon.moonstream.index.match.MetadataMatcher
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FixMode { FIX_TITLE, FIX_FILE, LINK }

/**
 * What the user picked in the sheet. `action` is "pick" for a normal
 * selection, or "new-episode" / "new-entry" for the LINK-mode shortcuts.
 */
data class MetaPick(
    val title: String,
    val year: String?,
    val imdbId: String?,
    val tmdbId: Int?,
    val posterUrl: String?,
    val overview: String?,
    val action: String = "pick",
)

/**
 * Shared metadata search sheet, now on REAL data.
 * - FIX_TITLE: re-match a wrongly indexed title (Apply updates the row).
 * - FIX_FILE: re-match a single wrongly indexed file (Apply moves the file).
 * - LINK: link an unmatched file to an existing entry / new episode / new entry.
 *
 * Search hits TMDB (needs the user's API key from Settings) or Kitsu for
 * anime (keyless). Results show real poster art.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataFixSheet(
    mode: FixMode,
    currentTitle: String,
    isAnime: Boolean,
    onDismiss: () -> Unit,
    onApply: (MetaPick) -> Unit,
    /** FIX_FILE mode: "this isn't a real show — make it a custom entry". */
    onCustomContent: () -> Unit = {},
    /** "movie" | "series" | "anime" — drives the metadata search type. */
    mediaType: String = "series",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EnrichedMeta>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EnrichedMeta?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var noKeyHint by remember { mutableStateOf(false) }

    val heading = when (mode) {
        FixMode.FIX_TITLE -> "Fix Metadata — This Title"
        FixMode.FIX_FILE -> "Fix Metadata — This File"
        FixMode.LINK -> "Link to Title"
    }

    // Debounced live search.
    LaunchedEffect(query) {
        searchJob?.cancel()
        selected = null
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            noKeyHint = false
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(450)
            searching = true
            val key = ScanPrefs.getTmdbKey(context)
            noKeyHint = !isAnime && key.isBlank()
            results = MetadataMatcher.searchTitles(
                query = query,
                // Anime goes through Kitsu; otherwise match the entry's real
                // type — movies were wrongly searching TV shows.
                type = if (isAnime) "series" else mediaType,
                isAnime = isAnime,
                tmdbKey = key,
            )
            searching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = heading) {
            Text(
                text = when (mode) {
                    FixMode.FIX_TITLE -> "Currently: $currentTitle. Search and pick the correct title — the entry's metadata will be updated."
                    FixMode.FIX_FILE -> "Currently filed under: $currentTitle. Search and pick the correct title — this file will be moved there."
                    FixMode.LINK -> "Search an existing title to attach this file to it, or create a new entry from a result."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            MsTextField(
                value = query,
                onValueChange = { query = it },
                hint = "Search title",
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                searching -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MsColors.Accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                noKeyHint -> {
                    Text(
                        "Add your TMDB API key in Settings → Metadata to enable search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                results.isEmpty() && query.isNotBlank() -> {
                    Text(
                        "No results.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                results.isNotEmpty() -> {
                    LazyColumn(modifier = Modifier.height(264.dp)) {
                        items(results) { r ->
                            MetaResultRow(
                                result = r,
                                selected = selected == r,
                                onPick = { selected = r },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
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
                        onClick = {
                            onApply(
                                MetaPick(
                                    title = selected?.title ?: query,
                                    year = selected?.year,
                                    imdbId = selected?.imdbId,
                                    tmdbId = selected?.tmdbId,
                                    posterUrl = selected?.posterUrl,
                                    overview = selected?.overview,
                                    action = "new-episode",
                                ),
                            )
                        },
                        enabled = selected != null || query.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                    MsOutlinePill(
                        text = "New entry",
                        onClick = {
                            onApply(
                                MetaPick(
                                    title = selected?.title ?: query,
                                    year = selected?.year,
                                    imdbId = selected?.imdbId,
                                    tmdbId = selected?.tmdbId,
                                    posterUrl = selected?.posterUrl,
                                    overview = selected?.overview,
                                    action = "new-entry",
                                ),
                            )
                        },
                        enabled = selected != null || query.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            LimePillButton(
                text = if (mode == FixMode.LINK) "Link to selected title" else "Apply — update metadata",
                onClick = {
                    selected?.let {
                        onApply(
                            MetaPick(
                                title = it.title,
                                year = it.year,
                                imdbId = it.imdbId,
                                tmdbId = it.tmdbId,
                                posterUrl = it.posterUrl,
                                overview = it.overview,
                            ),
                        )
                    }
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode == FixMode.FIX_FILE) {
                MsOutlinePill(
                    text = "Add to custom content",
                    onClick = onCustomContent,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Not a real show? Turn this file into a custom entry " +
                        "(class videos, personal rips…).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
            }
        }
    }
}

/** One search result row: real poster thumbnail + title + year + IMDb id. */
@Composable
private fun MetaResultRow(
    result: EnrichedMeta,
    selected: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPick() }
            .background(
                if (selected) MsColors.Accent.copy(alpha = 0.14f)
                else MsColors.SurfaceAlt,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MsColors.Bg),
            contentAlignment = Alignment.Center,
        ) {
            if (result.posterUrl != null) {
                AsyncImage(
                    model = result.posterUrl,
                    contentDescription = result.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "No poster",
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(result.year, result.imdbId).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MsColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Picked",
                    tint = MsColors.OnAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
