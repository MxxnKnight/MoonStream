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
import androidx.compose.material3.Text
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
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.parse.normalizeTitle
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /** "movie" or "series" — the tab the result was picked from. Null = keep current. */
    val type: String? = null,
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
    /** Pre-fill the search field (moon's request 2026-11-01: parsed name auto-filled). */
    initialQuery: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<EnrichedMeta>>(emptyList()) }
    // IMDb IDs + normalized titles already in the library — for the "In library" badge
    // (moon's request 2026-11-01: know when an entry already exists).
    var libraryKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EnrichedMeta?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var noKeyHint by remember { mutableStateOf(false) }
    // Movie/Series toggle for the search (anime always uses Kitsu).
    var searchType by remember { mutableStateOf(if (mediaType == "movie") "movie" else "series") }

    val heading = when (mode) {
        FixMode.FIX_TITLE -> "Fix Metadata — This Title"
        FixMode.FIX_FILE -> "Fix Metadata — This File"
        FixMode.LINK -> "Link to Title"
    }

    // Debounced live search.
    LaunchedEffect(query, searchType) {
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
            // Cinemeta needs no key — only hint when TMDB is the sole source.
            noKeyHint = false
            // Network must run on IO dispatcher, not Main (moon's bug
            // 2026-11-01: search returned empty due to NetworkOnMainThread).
            results = withContext(Dispatchers.IO) {
                MetadataMatcher.searchTitles(
                    query = query,
                    // Anime goes through Kitsu; otherwise use the toggle —
                    // movies were wrongly searching TV shows.
                    type = if (isAnime) "series" else searchType,
                    isAnime = isAnime,
                    tmdbKey = key,
                )
            }
            searching = false
        }
    }

    IosBottomSheet(
        onDismissRequest = onDismiss,
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
                hint = "Search title or IMDb ID (e.g. tt0320661)",
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    // Paste button (moon's report 2026-11-02): long-press
                    // paste is unreliable inside the bottom sheet, so offer
                    // an explicit paste action for IMDb IDs.
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    androidx.compose.material3.TextButton(
                        onClick = {
                            clipboard.getText()?.text?.let { query = it.trim() }
                        }
                    ) {
                        Text("Paste", color = MsColors.Accent)
                    }
                },
            )
            // Movie / Series search toggle (anime always searches Kitsu).
            if (!isAnime) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("movie" to "Movies", "series" to "Series").forEach { (v, label) ->
                        val sel = searchType == v
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (sel) MsColors.Accent else MsColors.SurfaceAlt)
                                .clickable { searchType = v }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) MsColors.OnAccent else MsColors.TextSecondary,
                            )
                        }
                    }
                }
            }
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
                    // Check which results already have a library entry WITH streams.
                    // (moon's request: show "already indexed" icon in all modes, not just LINK)
                    LaunchedEffect(results) {
                        if (results.isNotEmpty()) {
                            libraryKeys = withContext(Dispatchers.IO) {
                                val db = MsDatabase.get(context)
                                val keys = mutableSetOf<String>()
                                for (r in results) {
                                    val media = if (!r.imdbId.isNullOrBlank()) {
                                        db.mediaDao().findByImdb(r.imdbId)
                                    } else {
                                        db.mediaDao().findByNormAnyChannel(
                                            normalizeTitle(r.title),
                                        )
                                    }
                                    // Only mark if the entry has actual streams
                                    // (moon's 2026-11-04: "already indexed with streams").
                                    if (media != null &&
                                        db.streamDao().listByMedia(media.id).isNotEmpty()
                                    ) {
                                        r.imdbId?.let { keys.add(it) }
                                        keys.add(normalizeTitle(r.title))
                                    }
                                }
                                keys
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.height(264.dp)) {
                        items(results) { r ->
                            val inLibrary = !r.imdbId.isNullOrBlank() && libraryKeys.contains(r.imdbId) ||
                                libraryKeys.contains(normalizeTitle(r.title))
                            MetaResultRow(
                                result = r,
                                selected = selected == r,
                                inLibrary = inLibrary,
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
                                    type = searchType,
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
                                    type = searchType,
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
                                type = searchType,
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
    inLibrary: Boolean = false,
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
                    model = com.moon.moonstream.util.PosterStyle.displayUrl(
                        result.posterUrl, result.imdbId,
                        com.moon.moonstream.index.scan.ScanPrefs.getPosterStyle(context),
                        com.moon.moonstream.index.scan.ScanPrefs.getPosterTemplate(context),
                    ),
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
            if (inLibrary) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MsColors.Accent.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "In library",
                        tint = MsColors.Accent,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "In library",
                        style = MaterialTheme.typography.labelSmall,
                        color = MsColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!result.overview.isNullOrBlank()) {
                Text(
                    result.overview!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
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
