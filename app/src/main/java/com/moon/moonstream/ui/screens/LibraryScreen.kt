package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.moon.moonstream.ui.model.formatBytes
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.launch

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

    val allItems by db.mediaDao().observeAll().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var segment by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("Any") }
    var subFilter by remember { mutableStateOf("Any") }
    var sort by remember { mutableStateOf("Recently added") }

    // Multi-select state — Select button or long-press; bottom action bar.
    var selectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    // Real filter id-sets, loaded when the filter changes.
    var qualityIds by remember { mutableStateOf<Set<Long>?>(null) }
    var subIds by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(quality) {
        qualityIds = if (quality == "Any") null
        else db.streamDao().mediaIdsWithQuality(quality).toSet()
    }
    LaunchedEffect(subFilter) {
        subIds = if (subFilter == "Any") null
        else db.subtitleDao().mediaIdsWithSubtitles().toSet()
    }

    val filtered = allItems.filter { t ->
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
        matchesSegment && matchesQuery && matchesQuality && matchesSubs
    }.let { list ->
        when (sort) {
            "Title A–Z" -> list.sortedBy { it.title.lowercase() }
            "Year" -> list.sortedByDescending { it.year ?: "" }
            "Size" -> list.sortedByDescending { it.totalSize }
            else -> list // "Recently added": observeAll is updatedAt DESC
        }
    }
    val inHiddenTab = segment == 4
    val totalStreams = allItems.sumOf { it.fileCount }

    fun exitSelectMode() {
        selectMode = false
        selectedIds.clear()
    }

    fun setHidden(ids: List<Long>, hidden: Boolean) {
        scope.launch {
            ids.forEach { db.mediaDao().setHidden(it, hidden) }
            Toast.makeText(
                context,
                if (hidden) "${ids.size} hidden" else "${ids.size} unhidden",
                Toast.LENGTH_SHORT,
            ).show()
            exitSelectMode()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
            )
            Text(
                "${allItems.size} titles · $totalStreams streams indexed",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 12.dp),
            )

            MsTextField(
                value = query,
                onValueChange = { query = it },
                hint = "Search library",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

            MsSegmentedControl(
                segments = listOf("ALL", "MOVIES", "SERIES", "ANIME", "HIDDEN"),
                selected = segment,
                onSelect = {
                    segment = it
                    exitSelectMode()
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

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
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                // Plain-words empty states — never a fake grid.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            allItems.isEmpty() -> "No titles yet — scan a channel to build your library."
                            segment == 4 -> "Nothing hidden."
                            segment == 1 -> "No movies yet."
                            segment == 2 -> "No series yet."
                            segment == 3 -> "No anime yet."
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
                            inHiddenTab = inHiddenTab,
                            onClick = {
                                if (selectMode) {
                                    if (isSelected) selectedIds.remove(item.id)
                                    else selectedIds.add(item.id)
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

        // ---- Bottom multi-select action bar ----
        AnimatedVisibility(
            visible = selectMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 122.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MsColors.SurfaceAlt)
                    .border(1.dp, MsColors.Divider, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BareIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Cancel selection",
                    tint = MsColors.TextSecondary,
                    onClick = { exitSelectMode() },
                )
                Text(
                    "${selectedIds.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                LimeTextButton(
                    text = if (selectedIds.size == filtered.size && filtered.isNotEmpty()) "Deselect" else "Select All",
                    fontSize = 13,
                    onClick = {
                        selectedIds.clear()
                        if (selectedIds.size != filtered.size) {
                            selectedIds.addAll(filtered.map { it.id })
                        }
                    },
                )
                val hideColor = if (inHiddenTab) Color(0xFF059669) else Color(0xFFDC2626)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selectedIds.isNotEmpty()) hideColor else MsColors.TrackOff)
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
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            quality = quality, onQuality = { quality = it },
            subFilter = subFilter, onSubFilter = { subFilter = it },
            sort = sort, onSort = { sort = it },
            onDismiss = { showFilters = false },
        )
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(MsColors.Surface)
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / segments.size
        val thumbOffset by animateDpAsState(
            targetValue = tabWidth * selected,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "segmentThumb",
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = tabWidth, height = 38.dp)
                .clip(CircleShape)
                .background(MsColors.Accent),
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            segments.forEachIndexed { i, label ->
                val isSel = i == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSel) MsColors.OnAccent else MsColors.TextSecondary,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCell(
    item: MediaItemEntity,
    selectMode: Boolean,
    isSelected: Boolean,
    inHiddenTab: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleHide: () -> Unit,
) {
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
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MsColors.Accent else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .background(MsColors.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (item.posterUrl != null) {
                    AsyncImage(
                        model = item.posterUrl,
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
    sort: String, onSort: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MsColors.Surface,
        contentColor = MsColors.TextPrimary,
    ) {
        MsSheetContent(title = "Filters") {
            FilterRow("Quality", listOf("Any", "4K", "1080p", "720p", "480p"), quality, onQuality)
            FilterRow("Subtitles", listOf("Any", "Has subtitles", "No subtitles"), subFilter, onSubFilter)
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
