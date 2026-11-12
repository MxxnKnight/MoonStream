package com.moon.moonstream.ui.screens

import android.widget.Toast
import com.moon.moonstream.ui.components.MsToast
import com.moon.moonstream.ui.components.IosBottomSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.components.unfocusOnTapOutside
import com.moon.moonstream.ui.model.formatBytes
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.Redaction35
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * Library on REAL data (v0.7.0): observes the Room library built by the
 * scanner. Segments ALL/MOVIES/SERIES/ANIME/HIDDEN, search, real filters,
 * long-press/Select multi-select with Hide/Unhide persisted to the database.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpenDetail: (Long) -> Unit) {
    val context = LocalContext.current
    val db = remember { MsDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val allItems by com.moon.moonstream.index.db.LibraryCache.items(context).collectAsState()

    var query by remember { mutableStateOf("") }
    var segment by remember { mutableStateOf(AppUiState.restoreScroll("library_tab")) }
    // Save tab + scroll so Back returns to where you were (moon's bug 2026-11-01).
    DisposableEffect(Unit) {
        onDispose { AppUiState.saveScroll("library_tab", segment) }
    }
    var showFilters by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("Any") }
    var subFilter by remember { mutableStateOf("Any") }
    var subLangFilter by remember { mutableStateOf("Any") }
    var sort by remember { mutableStateOf("Recently added") }

    // Multi-select state — Select button or long-press; bottom action bar.
    var selectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    /** Two-phase merge (moon's design 2026-11-04): phase 1 = pick sources,
     *  tap "Merge to" → sources locked in (red outline = will be deleted),
     *  phase 2 = pick the target. Empty = not in merge mode. */
    val mergeSources = remember { mutableStateListOf<Long>() }

    // Real filter id-sets, loaded when the filter changes.
    var qualityIds by remember { mutableStateOf<Set<Long>?>(null) }
    var subIds by remember { mutableStateOf<Set<Long>?>(null) }
    var subLangIds by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(quality) {
        qualityIds = if (quality == "Any") null
        else db.streamDao().mediaIdsWithQuality(quality).toSet()
    }
    LaunchedEffect(subFilter) {
        subIds = if (subFilter == "Any") null
        else db.subtitleDao().mediaIdsWithSubtitles().toSet()
    }
    // Subtitle language codes used by the indexer.
    val subLangCodes = mapOf(
        "Malayalam" to "Mal", "English" to "Eng", "Hindi" to "Hin",
        "Tamil" to "Tam", "Telugu" to "Tel",
    )
    LaunchedEffect(subLangFilter) {
        subLangIds = if (subLangFilter == "Any") null
        else subLangCodes[subLangFilter]?.let { db.subtitleDao().mediaIdsWithSubtitleLang(it).toSet() }
    }

    // remember(...): filtering/sorting the whole library used to re-run on EVERY
    // recomposition (scrolling, animations). Now only when an input changes.
    val filtered = remember(
        allItems, segment, query, qualityIds, subFilter, subIds, subLangIds, sort,
    ) { allItems.filter { t ->
        val matchesSegment = when (segment) {
            0 -> !t.hidden
            1 -> t.type == "movie" && !t.hidden
            2 -> t.type == "series" && !t.hidden
            3 -> t.type == "anime" && !t.hidden
            4 -> t.hidden
            else -> true
        }
        val matchesQuery = query.isBlank() || t.title.contains(query, ignoreCase = true)
        val matchesQuality = qualityIds?.contains(t.id) ?: true
        val matchesSubs = when (subFilter) {
            "Has subtitles" -> subIds?.contains(t.id) == true
            "No subtitles" -> subIds?.contains(t.id) != true
            else -> true
        }
        val matchesSubLang = subLangIds?.contains(t.id) ?: true
        matchesSegment && matchesQuery && matchesQuality && matchesSubs && matchesSubLang
    }.let { list ->
        when (sort) {
            "Title A–Z" -> list.sortedBy { it.title.lowercase() }
            "Year" -> list.sortedByDescending { it.year ?: "" }
            "Size" -> list.sortedByDescending { it.totalSize }
            else -> list // "Recently added": observeAll is updatedAt DESC
        }
    } }
    val inHiddenTab = segment == 4
    // Low-end pass (moon's 2026-11-09): this sum walked every entry on every
    // recomposition (it sits in a header that also moves while scrolling).
    val totalStreams = remember(allItems) { allItems.sumOf { it.fileCount } }
    // Count-up animation (moon's 2026-11-09: he missed the files-count
    // animation). The titles + streams numbers animate from the old value
    // to the new one instead of snapping — so when the library loads they
    // count up from 0, and after a scan they roll up to the new total.
    // A plain int animation: no layout, no layer, nothing heavy.
    val animatedTitles by animateIntAsState(
        targetValue = allItems.size,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "titlesCount",
    )
    val animatedStreams by animateIntAsState(
        targetValue = totalStreams,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "streamsCount",
    )

    fun exitSelectMode() {
        selectMode = false
        selectedIds.clear()
        mergeSources.clear()
    }

    fun setHidden(ids: List<Long>, hidden: Boolean) {
        scope.launch {
            ids.forEach { db.mediaDao().setHidden(it, hidden) }
            MsToast.show(if (hidden) "${ids.size} hidden" else "${ids.size} unhidden")
            exitSelectMode()
        }
    }

    /** Phase 1 → 2: lock selected as merge sources (red outline = will be deleted),
     *  then pick the target. (moon's two-phase design 2026-11-04) */
    fun startMergeTo() {
        if (selectedIds.isEmpty()) return
        mergeSources.clear()
        mergeSources.addAll(selectedIds)
        selectedIds.clear()
        // selectMode stays on — user now picks the target (can switch tabs).
    }

    /** Phase 2: merge the locked sources into the selected target. */
    fun confirmMerge() {
        val targetId = selectedIds.firstOrNull() ?: return
        val sources = mergeSources.toList().filter { it != targetId }
        if (sources.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                for (id in sources) {
                    db.streamDao().moveAllToMedia(id, targetId)
                    db.subtitleDao().moveAllToMedia(id, targetId)
                    db.mediaDao().deleteById(id)
                }
                db.mediaDao().refreshAggregates(targetId, System.currentTimeMillis())
                // Safety net: delete any entries left empty (moon's 2026-11-04).
                db.mediaDao().deleteEmpty()
            }
            MsToast.show("Merged ${sources.size} ${if (sources.size == 1) "entry" else "entries"}")
            exitSelectMode()
        }
    }

    /** Merge selected entries: move all streams/subs to the first one,
     *  delete the rest. (moon's idea 2026-11-01) */
    // (old mergeSelected removed — replaced by two-phase startMergeTo/confirmMerge above)

    /** Delete selected entries and their streams/subs. */
    fun deleteSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                for (id in ids) {
                    db.streamDao().listByMedia(id).forEach { db.streamDao().deleteById(it.id) }
                    db.subtitleDao().listByMedia(id).forEach { db.subtitleDao().delete(it.id) }
                    db.mediaDao().deleteById(id)
                }
            }
            MsToast.show("Deleted ${ids.size} ${if (ids.size == 1) "entry" else "entries"}")
            exitSelectMode()
        }
    }

    // Header (title, search, filters, counts) PINNED at the top — it stays
    // put while the poster grid scrolls (moon's correction 2026-11-06), so
    // tabs and search are always reachable, even scrolled far down.
    @Composable
    fun LibraryHeader() {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
            )
            Text(
                "$animatedTitles titles · $animatedStreams streams indexed",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Redaction35),
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 12.dp),
            )

            // Continue Watching row REMOVED (moon's correction 2026-11-02):
            // instead each entry shows "watched X ago" on its watched files,
            // and the player offers Resume.

            MsTextField(
                value = query,
                onValueChange = { query = it },
                hint = "Search library",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))

            // Filter tabs: the ORIGINAL iOS segmented bar, restored
            // exactly (moon's reference screenshot, 2026-11-07). The pill
            // is a rounded rectangle that fills the whole selected slot
            // (36dp tall, 8dp corners) — NOT a round capsule hugging the
            // text, and NOT a slot-wide flat slide. The thumb has ONE fixed
            // size and only its X animates (bouncy spring), so the click
            // animation is the clean iOS slide, not a stretching wobble.
            // The bar is fixed — all five tabs visible, no scroll, no drag.
            MsSegmentedControl(
                segments = listOf("ALL", "MOVIES", "SERIES", "ANIME", "HIDDEN"),
                selected = segment,
                onSelect = {
                    segment = it
                    // Don't exit select mode on tab switch (moon's bug 2026-11-04:
                    // selection must survive to allow cross-tab merge).
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${filtered.size} titles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                MsOutlinePill(
                    text = if (selectMode) "Done" else "Select",
                    onClick = {
                        if (selectMode) exitSelectMode() else selectMode = true
                    },
                    fontSize = 13,
                    modifier = Modifier.padding(end = 8.dp),
                )
                MsOutlinePill(
                    text = "Filters",
                    onClick = { showFilters = true },
                    fontSize = 13,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = AppUiState.restoreScroll("library_grid"),
    )
    DisposableEffect(Unit) {
        onDispose {
            AppUiState.saveScroll("library_grid", gridState.firstVisibleItemIndex)
        }
    }

    // Tap outside the search box to unfocus it (moon's 2026-11-06):
    // tapping empty grid space clears focus + hides the keyboard.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .unfocusOnTapOutside(),
    ) {
        Column(Modifier.fillMaxSize()) {
            LibraryHeader()
            // Directional slide when switching segments (moon's
            // 2026-11-09, pick 2): the grid slides in from the side
            // you're moving toward (right tab from the right, left tab
            // from the left), matching the screen slide. Only the
            // incoming grid animates (the old one is dropped at once),
            // so it stays light on a low-end phone.
            AnimatedContent(
                targetState = segment,
                transitionSpec = {
                    val dir = when {
                        targetState > initialState -> 1
                        targetState < initialState -> -1
                        else -> 0
                    }
                    if (dir == 0) {
                        fadeIn(tween(90)) togetherWith ExitTransition.None
                    } else {
                        // 165ms slide, no fade (2026-11-09 speed pass,
                        // matches the screen switch): a whole-grid alpha
                        // fade forces an offscreen layer and per-pixel
                        // blending over the moving slide, which is the part
                        // that stutters on a low-end GPU. Pure offset only.
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> dir * fullWidth },
                            animationSpec = tween(165, easing = FastOutSlowInEasing),
                        ) togetherWith ExitTransition.None
                    }
                },
                label = "librarySegment",
                modifier = Modifier.weight(1f),
            ) { seg ->
                if (filtered.isEmpty()) {
                    // Plain-words empty states — never a fake grid.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                    Text(
                        text = when {
                            allItems.isEmpty() -> "No titles yet — scan a channel to build your library."
                            seg == 4 -> "Nothing hidden."
                            seg == 1 -> "No movies yet."
                            seg == 2 -> "No series yet."
                            seg == 3 -> "No anime yet."
                            else -> "Nothing matches your search or filters."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        PosterCell(
                            item = item,
                            selectMode = selectMode,
                            isSelected = isSelected,
                            isMergeSource = mergeSources.contains(item.id),
                            inHiddenTab = inHiddenTab,
                            onClick = {
                                if (selectMode) {
                                    // In phase 2 (mergeSources locked), can't pick a source as target.
                                    if (mergeSources.isNotEmpty() && mergeSources.contains(item.id)) {
                                        MsToast.show("This entry is marked for merge")
                                    } else if (isSelected) selectedIds.remove(item.id)
                                    else {
                                        // Phase 2: single target only.
                                        if (mergeSources.isNotEmpty()) selectedIds.clear()
                                        selectedIds.add(item.id)
                                    }
                                } else {
                                    onOpenDetail(item.id)
                                }
                            },
                            onLongPress = {
                                if (!selectMode) {
                                    selectMode = true
                                    selectedIds.add(item.id)
                                } else if (!isSelected) {
                                    selectedIds.add(item.id)
                                }
                            },
                            onToggleHide = {
                                scope.launch {
                                    db.mediaDao().setHidden(item.id, !item.hidden)
                                }
                            },
                        )
                    }
                }
            }
            }
        }

        // ---- Bottom multi-select action bar ----
        // bottom 96dp (moon's 2026-11-13): was 122dp, set for the old big
        // bottom bar — with the small pill (~77dp footprint) it floated too
        // high. 96dp matches the Channels bulk bar, just above the pill.
        AnimatedVisibility(
            visible = selectMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BareIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Cancel selection",
                    tint = Color(0xFF98989F),
                    onClick = { exitSelectMode() },
                )
                Text(
                    "${selectedIds.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // iOS blue text button (moon's request 2026-11-04).
                TextButton(onClick = {
                    selectedIds.clear()
                    if (selectedIds.size != filtered.size) {
                        selectedIds.addAll(filtered.map { it.id })
                    }
                }) {
                    Text(
                        text = if (selectedIds.size == filtered.size && filtered.isNotEmpty()) "Deselect" else "Select All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.Accent,
                    )
                }
                // iOS red pill for Hide/Unhide.
                val hideColor = if (inHiddenTab) Color(0xFF30D158) else Color(0xFFFF453A)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedIds.isNotEmpty()) hideColor else Color(0xFF39393B))
                        .clickable(enabled = selectedIds.isNotEmpty()) {
                            setHidden(selectedIds.toList(), !inHiddenTab)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(
                            (if (inHiddenTab) "Unhide" else "Hide") + " (${selectedIds.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
                // Two-phase merge (moon's design 2026-11-04):
                // Phase 1: "Merge to" (1+ selected) → locks sources, pick target next.
                // Phase 2: "Merge" (target picked) → completes.
                // iOS blue pill for Merge (moon's request 2026-11-04).
                Spacer(Modifier.width(8.dp))
                if (mergeSources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedIds.isNotEmpty()) MsColors.Accent else Color(0xFF39393B))
                            .clickable(enabled = selectedIds.isNotEmpty()) { startMergeTo() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Merge to (${selectedIds.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedIds.isNotEmpty()) MsColors.Accent else Color(0xFF39393B))
                            .clickable(enabled = selectedIds.isNotEmpty()) { confirmMerge() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Merge (${mergeSources.size} → 1)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
                // Delete: needs 1+ selected.
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selectedIds.isNotEmpty()) Color(0xFF991B1B) else MsColors.TrackOff)
                        .clickable(enabled = selectedIds.isNotEmpty()) { deleteSelected() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Delete (${selectedIds.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            quality = quality, onQuality = { quality = it },
            subFilter = subFilter, onSubFilter = { subFilter = it },
            subLangFilter = subLangFilter, onSubLangFilter = { subLangFilter = it },
            sort = sort, onSort = { sort = it },
            onDismiss = { showFilters = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCell(
    item: MediaItemEntity,
    selectMode: Boolean,
    isSelected: Boolean,
    /** Red outline = marked as merge source, will be deleted (moon's 2026-11-04). */
    isMergeSource: Boolean = false,
    inHiddenTab: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleHide: () -> Unit,
) {
    val context = LocalContext.current
    // Low-end pass (moon's 2026-11-09): the poster style/template live in
    // SharedPreferences, and reading them here ran two prefs lookups per
    // visible cell on every scroll frame. Read once per cell, and build the
    // display URL once per poster instead of on every recomposition.
    val posterStyle = remember(context) {
        com.moon.moonstream.index.scan.ScanPrefs.getPosterStyle(context)
    }
    val posterTemplate = remember(context) {
        com.moon.moonstream.index.scan.ScanPrefs.getPosterTemplate(context)
    }
    val posterUrl = remember(item.posterUrl, item.imdbId, posterStyle, posterTemplate) {
        item.posterUrl?.let {
            com.moon.moonstream.util.PosterStyle.displayUrl(
                it, item.imdbId, posterStyle, posterTemplate,
            )
        }
    }
    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress,
        ),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected || isMergeSource) 2.dp else 0.dp,
                        color = when {
                            isMergeSource -> Color(0xFFF44336)
                            isSelected -> MsColors.Accent
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .background(MsColors.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Title-only entries: clean initial-letter tile.
                    Text(
                        text = item.title.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        color = MsColors.Accent.copy(alpha = 0.75f),
                    )
                }
            }

            if ((item.type == "series" || item.type == "anime") && item.episodeCount > 0) {
                // Top-left: the bottom placement covered the title on artwork.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF6B21A8).copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${item.episodeCount} EPS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 9.sp,
                    )
                }
            }

            if (selectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MsColors.Accent else Color(0x99000000),
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) Color.White else Color(0x99FFFFFF),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MsColors.OnAccent,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MsColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                item.year,
                item.type.uppercase(),
                formatBytes(item.totalSize).takeIf { item.totalSize > 0 },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MsColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (inHiddenTab) {
            LimeTextButton(
                text = "Unhide",
                fontSize = 13,
                onClick = onToggleHide,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    quality: String, onQuality: (String) -> Unit,
    subFilter: String, onSubFilter: (String) -> Unit,
    subLangFilter: String, onSubLangFilter: (String) -> Unit,
    sort: String, onSort: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    IosBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        MsSheetContent(title = "Filters", scrollable = true) {
            FilterRow("Quality", listOf("Any", "4K", "1080p", "720p", "480p"), quality, onQuality)
            FilterRow("Subtitles", listOf("Any", "Has subtitles", "No subtitles"), subFilter, onSubFilter)
            FilterRow(
                "Subtitle language",
                listOf("Any", "Malayalam", "English", "Hindi", "Tamil", "Telugu"),
                subLangFilter, onSubLangFilter,
            )
            FilterRow("Sort", listOf("Recently added", "Title A–Z", "Year", "Size"), sort, onSort)
            LimePillButton(
                text = "Apply filters",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Filter tag row — tags wrap below (FlowRow) so nothing ever clips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MsColors.TextSecondary,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { opt ->
                val isSel = opt == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSel) MsColors.Accent else MsColors.SurfaceAlt,
                        )
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        opt,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSel) MsColors.OnAccent else MsColors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Pill segmented control with a sliding accent thumb. */
@Composable
private fun MsSegmentedControl(
    segments: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1C1C1E))
            .padding(2.dp),
    ) {
        val tabWidth = maxWidth / segments.size
        val tabWidthPx = with(density) { tabWidth.toPx() }
        // Drag threshold (moon's 2026-11-07): a tap must never trip the
        // drag. The old 24px (8dp) was so small that the tiny finger
        // movement of pressing a segment engaged the drag and the thumb
        // SNAPPED to the finger (teleport) instead of sliding via the
        // spring. 16dp gives a tap real headroom.
        val dragThresholdPx = with(density) { 16.dp.toPx() }
        // The thumb's X is a pixel Animatable (not animateDpAsState) so a
        // drag can move it with the finger and it springs back on release.
        // Same bouncy spring as the original, same fixed thumb size, so
        // a tap animates exactly like the original control. Pixels (not
        // Dp) because the drag deltas arrive in pixels — and Dp's
        // VectorConverter won't resolve in this module's Compose version.
        // Glide spec (moon's 2026-11-09, mpvRx video): the same smooth,
        // visible glide as the bottom pill — the old bouncy Medium
        // spring was too fast to see. A softer spring glides the thumb
        // so you can watch it travel, like mpvRx.
        val slideSpec = spring<Float>(
            dampingRatio = 0.9f,
            stiffness = 260f,
        )
        val thumbX = remember { Animatable(0f) }
        // Thumb X while the finger holds it (2026-11-09, moon: "same teleport
        // bug on the library tabs"). Null = the Animatable drives it. A plain
        // state so the drag costs one write per event instead of a coroutine +
        // Animatable call, and so the thumb can glide on first engage instead
        // of jumping under the finger.
        var dragThumbX by remember { mutableStateOf<Float?>(null) }
        var thumbReady by remember { mutableStateOf(false) }
        LaunchedEffect(selected, tabWidthPx) {
            val target = tabWidthPx * selected
            if (!thumbReady) {
                thumbX.snapTo(target)
                thumbReady = true
            } else {
                thumbX.animateTo(target, slideSpec)
            }
        }
        // Horizontal drag (moon's 2026-11-07): press and drag the thumb —
        // it follows the finger, release snaps to the nearest tab. Taps
        // still work (the segment clickables fire when no drag). The drag
        // lives on the labels Row (it covers the whole bar — the thumb sits
        // behind it, so a drag on the thumb itself would never receive
        // touches), but it only ENGAGES when the finger starts ON the thumb
        // (see the grab-only check in the pointerInput below).
        // iOS-style: selected thumb is lighter gray (moon's pick 2026-11-04).
        Box(
            modifier = Modifier
                .offset { IntOffset((dragThumbX ?: thumbX.value).roundToInt(), 0) }
                .size(width = tabWidth, height = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF636366)),
        )
        // No touch animation on the segments (moon's 2026-11-07): only the
        // capsule and its slide animate — a tap ripple would draw a second
        // rounded rect around the pill, the same double-selection the
        // bottom pill had.
        val segInteractionSource = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selected, tabWidthPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            var dragging = false
                            var accum = 0f
                            var fingerX = 0f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    // Gesture cancelled: park the thumb back on
                                    // the selected segment.
                                    val held = dragThumbX
                                    if (dragging && held != null) {
                                        scope.launch {
                                            thumbX.snapTo(held)
                                            dragThumbX = null
                                            thumbX.animateTo(tabWidthPx * selected, slideSpec)
                                        }
                                    } else {
                                        dragThumbX = null
                                    }
                                    break
                                }
                                if (!change.pressed) {
                                    // Finger lifted: settle on the nearest
                                    // segment, decided by the FINGER (the
                                    // thumb glides behind it, so on a fast
                                    // flick the thumb's own X can still be on
                                    // the segment we came from).
                                    val releaseX = dragThumbX ?: thumbX.value
                                    if (dragging) {
                                        val nearest = ((fingerX / tabWidthPx).roundToInt())
                                            .coerceIn(0, segments.size - 1)
                                        // Hand the Animatable the drag position
                                        // BEFORE dropping the override, so the
                                        // thumb never flashes back to the old
                                        // segment for a frame (that backwards
                                        // hop was the visible half of the
                                        // teleport).
                                        scope.launch {
                                            thumbX.snapTo(releaseX)
                                            dragThumbX = null
                                            if (nearest != selected) {
                                                onSelect(nearest)
                                            } else {
                                                thumbX.animateTo(
                                                    tabWidthPx * selected, slideSpec
                                                )
                                            }
                                        }
                                    } else {
                                        dragThumbX = null
                                    }
                                    break
                                }
                                val dx = change.position.x - change.previousPosition.x
                                accum += dx
                                // Wait for a clear horizontal move before
                                // claiming the gesture, so taps still work.
                                if (!dragging && kotlin.math.abs(accum) < dragThresholdPx) continue
                                val firstEngage = !dragging
                                dragging = true
                                change.consume()
                                // Thumb follows the finger (2026-11-09): its
                                // centre sits under the finger, but it GLIDES
                                // there on the first engaged event (0.45 of
                                // the way) instead of snapping under the
                                // finger, which is what looked like a
                                // teleport. After that it tracks 1:1 (0.85),
                                // so sparse touch events don't stutter it.
                                // thumbX is the thumb's LEFT edge, so the
                                // centre is thumbX + tabWidthPx/2.
                                val maxX = tabWidthPx * (segments.size - 1)
                                fingerX = change.position.x
                                val cur = dragThumbX ?: thumbX.value
                                val targetX =
                                    (fingerX - tabWidthPx / 2).coerceIn(0f, maxX)
                                val follow = if (firstEngage) 0.45f else 0.85f
                                val next = cur + (targetX - cur) * follow
                                if (kotlin.math.abs(next - cur) > 0.05f) {
                                    dragThumbX = next
                                }
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { i, label ->
                val isSel = i == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = segInteractionSource,
                            indication = null,
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
