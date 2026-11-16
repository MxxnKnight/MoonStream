package com.moon.moonstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moon.moonstream.index.match.EnrichedMeta
import com.moon.moonstream.index.match.MetadataMatcher
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.parse.FilenameParser
import com.moon.moonstream.index.parse.normalizeTitle
import com.moon.moonstream.index.scan.ScanPrefs
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.Job
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
    /** "movie" | "series" | "anime" — the tab the result was picked from.
     * Null = keep current. "custom" is never stored here (it is a search
     * filter, not a media type) — a custom pick is identified by mediaId. */
    val type: String? = null,
    /** Library row id when the pick came from a local result row. The
     * apply step attaches to this exact entry instead of re-guessing
     * by title. Null for provider picks. */
    val mediaId: Long? = null,
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
    /** Telegram caption of the file (LINK mode only) — shown under the filename. */
    caption: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // Prefill with the CLEAN title (moon's 2026-11-06): the raw filename
    // carries the @promo handle, quality tags and "- 10.mkv", which
    // pollutes the search (and is too long to read in the box — the full
    // filename is shown just above the field in LINK mode). Parse it to
    // the bare title, e.g. "Meesaya Murukku 2".
    val initialSearch = remember(initialQuery, isAnime) {
        FilenameParser.parse(initialQuery, isAnime, "").title.trim()
            .ifBlank { initialQuery.trim() }
    }
    var query by remember { mutableStateOf(initialSearch) }
    // True once a search has actually run for the current query — gates
    // the "No results." line so it never shows while just typing.
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<EnrichedMeta>>(emptyList()) }
    // IMDb IDs + normalized titles already in the library — for the "In library" badge
    // (moon's request 2026-11-01: know when an entry already exists).
    var libraryKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EnrichedMeta?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var noKeyHint by remember { mutableStateOf(false) }
    // Search-type toggle (moon's 2026-11-16): "movie" | "series" |
    // "anime" | "custom". Anime files start on Anime (Kitsu); the toggle
    // itself — not the channel flag — now drives the search lane, so an
    // anime file from a non-anime channel can still search Kitsu.
    var searchType by remember {
        mutableStateOf(
            if (isAnime) "anime"
            else if (mediaType == "movie") "movie"
            else "series",
        )
    }
    /** Type carried into a pick: "custom" is a search filter, not a media
     * type, so it maps to null (keep current / file's own type). */
    val pickType = if (searchType == "custom") null else searchType

    val heading = when (mode) {
        FixMode.FIX_TITLE -> "Fix Metadata — This Title"
        FixMode.FIX_FILE -> "Fix Metadata — This File"
        FixMode.LINK -> "Link to Title"
    }

    // Manual search (moon's 2026-11-06): the old debounced live search
    // fired an API call on every keystroke — even clearing a letter —
    // which burns the free TMDB/Cinemeta rate limits. Searches now run
    // only when the user taps Search (or the keyboard's search key, or
    // flips Movies/Series), plus once on open for the prefilled title.
    fun runSearch() {
        searchJob?.cancel()
        selected = null
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            noKeyHint = false
            searched = false
            return
        }
        searching = true
        searched = true
        searchJob = scope.launch {
            val key = ScanPrefs.getTmdbKey(context)
            // Cinemeta needs no key — only hint when TMDB is the sole source.
            noKeyHint = false
            // Network must run on IO dispatcher, not Main (moon's bug
            // 2026-11-01: search returned empty due to NetworkOnMainThread).
            results = withContext(Dispatchers.IO) {
                // Custom searches the local library only — no provider
                // knows custom content, so no API call at all.
                val isCustomSearch = searchType == "custom"
                val isAnimeSearch = searchType == "anime"
                val provider = if (isCustomSearch) emptyList()
                else MetadataMatcher.searchTitles(
                    query = query,
                    // Anime goes through Kitsu; otherwise use the toggle —
                    // movies were wrongly searching TV shows.
                    type = if (isAnimeSearch) "series" else searchType,
                    isAnime = isAnimeSearch,
                    tmdbKey = key,
                )
                if (mode != FixMode.LINK && mode != FixMode.FIX_FILE) {
                    provider
                } else {
                    // Local first (moon's 2026-11-14): entries the providers
                    // can't know about — custom content — show up when the
                    // typed name matches. Same type toggle as the provider
                    // search (anime counts as series); a provider row wins
                    // over a local row with the same title, so linking via
                    // it still fills the entry's IMDb id. Custom skips that
                    // suppression (no provider results) — that is the point:
                    // a custom entry is pickable even when a real show
                    // shares its name (moon's 2026-11-16).
                    val norm = normalizeTitle(query)
                    val local = if (norm.isBlank()) emptyList()
                    else MsDatabase.get(context).mediaDao().searchByNorm(norm)
                    val providerNorms =
                        provider.map { normalizeTitle(it.title) }.toSet()
                    val localOnly = local.filter {
                        val typeOk = when {
                            isCustomSearch -> it.isCustom
                            isAnimeSearch -> it.type == "anime"
                            searchType == "series" -> it.type == "series" || it.type == "anime"
                            else -> it.type == "movie"
                        }
                        typeOk && normalizeTitle(it.title) !in providerNorms
                    }
                    localOnly.map {
                        EnrichedMeta(
                            title = it.title,
                            year = it.year,
                            overview = it.overview,
                            posterUrl = it.posterUrl,
                            backdropUrl = it.backdropUrl,
                            imdbId = it.imdbId,
                            tmdbId = it.tmdbId,
                            kitsuId = it.kitsuId,
                            anilistId = it.anilistId,
                            mediaId = it.id,
                        )
                    } + provider
                }
            }
            searching = false
        }
    }
    // Once on open (covers the prefill), and again when the Movies/Series
    // toggle flips.
    LaunchedEffect(searchType) { runSearch() }

    IosBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(),
    ) {
        // Full-tall sheet (moon's 2026-11-16): the results area takes the
        // free space (weight) so the action buttons stay pinned at the
        // bottom, and imePadding lifts them above the keyboard while
        // typing instead of letting the keyboard cover them. Same title /
        // padding / tap-outside-unfocus as MsSheetContent, laid out here
        // because the wrapper can't stretch.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .unfocusOnTapOutside(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
            )
            // Original filename + caption (moon's 2026-11-15): in LINK
            // mode the header showed only a generic help sentence, so he
            // couldn't see the file's year or details while picking a
            // title. The full filename (and caption, when the uploader
            // left one) now sits here instead.
            if (mode == FixMode.LINK) {
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MsColors.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = when (mode) {
                        FixMode.FIX_TITLE -> "Currently: $currentTitle. Search and pick the correct title — the entry's metadata will be updated."
                        FixMode.FIX_FILE -> "Currently filed under: $currentTitle. Search and pick the correct title — this file will be moved there."
                        FixMode.LINK -> "" // unreachable — LINK has its own header above
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MsColors.TextSecondary,
                )
            }
            MsTextField(
                value = query,
                onValueChange = {
                    query = it
                    // Don't show stale results for a query that's no
                    // longer in the box — they clear until Search is
                    // tapped again.
                    results = emptyList()
                    selected = null
                    searched = false
                },
                hint = if (mode == FixMode.LINK || mode == FixMode.FIX_FILE) "Search your library or online"
                else "Search title or IMDb ID (e.g. tt0320661)",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        runSearch()
                    },
                ),
                trailingIcon = {
                    // Search button (moon's 2026-11-06): searches run only
                    // on tap, so the free APIs aren't burned on every
                    // keystroke. The old Paste button is gone — long-press
                    // paste works in the field itself (moon's video).
                    androidx.compose.material3.TextButton(
                        onClick = {
                            focusManager.clearFocus() // hide keyboard, show results
                            runSearch()
                        }
                    ) {
                        Text("Search", color = MsColors.Accent)
                    }
                },
            )
            // Search-type toggle (moon's 2026-11-16): Movies / Series /
            // Anime / Custom. Anime searches Kitsu; Custom shows only
            // local custom entries. Custom is not offered in FIX_TITLE —
            // fixing metadata means picking a real title.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val options = when (mode) {
                    FixMode.FIX_TITLE -> listOf(
                        "movie" to "Movies",
                        "series" to "Series",
                        "anime" to "Anime",
                    )
                    else -> listOf(
                        "movie" to "Movies",
                        "series" to "Series",
                        "anime" to "Anime",
                        "custom" to "Custom",
                    )
                }
                options.forEach { (v, label) ->
                    val sel = searchType == v
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) MsColors.Accent else MsColors.SurfaceAlt)
                            .clickable { searchType = v }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            // Results area takes the free space (weight) — the buttons
            // below stay pinned at the bottom of the full-tall sheet.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
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
                results.isEmpty() && searched -> {
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
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            } // Box: results area
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
                                    type = pickType,
                                    mediaId = selected?.mediaId,
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
                                    type = pickType,
                                    mediaId = selected?.mediaId,
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
                                type = pickType,
                                mediaId = it.mediaId,
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
    val context = LocalContext.current
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
