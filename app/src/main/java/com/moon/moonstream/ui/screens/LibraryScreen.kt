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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MsOutlinePill
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.model.FakeTitle
import com.moon.moonstream.ui.model.fakeTitles
import com.moon.moonstream.ui.model.formatSizeMb
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii

/**
 * Library, NoxMedia-style (v0.4.0):
 * big header + indexed subtitle, search, pill segmented control
 * (ALL/MOVIES/SERIES/ANIME/HIDDEN), "N titles" + Select + Filters row,
 * 3-column poster grid with EPS badges + meta lines.
 * Multi-select activates ONLY via the Select button (never long-press);
 * bottom action bar offers Hide/Unhide for the selection.
 * TODO(Phase 3): back with Room observeMedia() Flow; filters map to real queries.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var segment by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("Any") }
    var language by remember { mutableStateOf("Any") }
    var source by remember { mutableStateOf("Any") }
    var sort by remember { mutableStateOf("Recently added") }

    // Multi-select state — strictly gated behind the Select button (item 24).
    var selectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    val hiddenIds = AppUiState.hiddenIds
    val filtered = fakeTitles.filter { t ->
        val isHidden = hiddenIds.contains(t.id)
        val matchesSegment = when (segment) {
            0 -> !isHidden
            1 -> t.type == "movie" && !isHidden
            2 -> t.type == "series" && !isHidden
            3 -> t.type == "anime" && !isHidden
            4 -> isHidden
            else -> true
        }
        val matchesQuery = query.isBlank() || t.title.contains(query, ignoreCase = true)
        matchesSegment && matchesQuery
    }
    val inHiddenTab = segment == 4
    val totalStreams = fakeTitles.sumOf { it.allStreams().size }

    fun exitSelectMode() {
        selectMode = false
        selectedIds.clear()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- NoxMedia-style header ----
            Text(
                "Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
            )
            Text(
                "${fakeTitles.size} titles · $totalStreams streams indexed",
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

            // ---- Pill segmented control (item 20 + 25) ----
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

            // ---- "N titles" + Select + Filters row ----
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

            // ---- Poster grid ----
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it.id }) { title ->
                    val isSelected = selectedIds.contains(title.id)
                    PosterCell(
                        title = title,
                        selectMode = selectMode,
                        isSelected = isSelected,
                        inHiddenTab = inHiddenTab,
                        onClick = {
                            if (selectMode) {
                                if (isSelected) selectedIds.remove(title.id)
                                else selectedIds.add(title.id)
                            } else {
                                onOpenDetail(title.id)
                            }
                        },
                        onLongPress = {
                            // Long-press enters selection mode with this poster
                            // pre-selected (item 24 refined).
                            if (!selectMode) {
                                selectMode = true
                                selectedIds.add(title.id)
                            } else if (!isSelected) {
                                selectedIds.add(title.id)
                            }
                        },
                        onToggleHide = {
                            if (hiddenIds.contains(title.id)) hiddenIds.remove(title.id)
                            else hiddenIds.add(title.id)
                        },
                    )
                }
            }
        }

        // ---- Bottom multi-select action bar (NoxMedia-style) ----
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
                            if (inHiddenTab) hiddenIds.removeAll(selectedIds)
                            else hiddenIds.addAll(selectedIds.filter { !hiddenIds.contains(it) })
                            val n = selectedIds.size
                            Toast.makeText(
                                context,
                                if (inHiddenTab) "$n unhidden (fake)" else "$n hidden (fake)",
                                Toast.LENGTH_SHORT,
                            ).show()
                            exitSelectMode()
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
            language = language, onLanguage = { language = it },
            source = source, onSource = { source = it },
            sort = sort, onSort = { sort = it },
            onDismiss = { showFilters = false },
        )
    }
}

/** Pill segmented control with a sliding accent thumb (NoxMedia pattern, fresh code). */
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
        // Sliding thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = tabWidth, height = 38.dp)
                .clip(CircleShape)
                .background(MsColors.Accent),
        )
        // Labels
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
    title: FakeTitle,
    selectMode: Boolean,
    isSelected: Boolean,
    inHiddenTab: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleHide: () -> Unit,
) {
    val context = LocalContext.current
    val epCount = title.episodeCount()
    Column(
        // Long-press enters multi-select with this poster pre-selected
        // (item 24 refined). It never shows a popup menu — no gesture conflict.
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
                    .background(Brush.verticalGradient(listOf(title.colorA, title.colorB))),
                contentAlignment = Alignment.BottomStart,
            ) {
                // Bottom scrim so the title stays readable on bright posters
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB3000000)),
                            ),
                        ),
                )
                Text(
                    text = title.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }

            // "N EPS" badge for series/anime (NoxMedia pattern).
            if ((title.type == "series" || title.type == "anime") && epCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF6B21A8).copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "$epCount EPS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 9.sp,
                    )
                }
            }

            // Selection checkmark circle (select mode only, NoxMedia pattern).
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
            text = title.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MsColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${title.year} · ${title.type.uppercase()} · ${formatSizeMb(title.totalSizeMb())}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MsColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Hidden tab: explicit per-item Unhide action.
        if (inHiddenTab) {
            LimeTextButton(
                text = "Unhide",
                fontSize = 13,
                onClick = {
                    onToggleHide()
                    Toast.makeText(context, "Unhidden (fake)", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    quality: String, onQuality: (String) -> Unit,
    language: String, onLanguage: (String) -> Unit,
    source: String, onSource: (String) -> Unit,
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
            FilterRow("Language", listOf("Any", "English", "Malayalam", "Hindi", "Dual"), language, onLanguage)
            FilterRow("Source", listOf("Any", "BluRay", "WEB-DL", "HDTV", "CAM"), source, onSource)
            FilterRow("Sort", listOf("Recently added", "Title A–Z", "Year", "Size"), sort, onSort)
            LimePillButton(
                text = "Apply filters",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Filter tag row — tags WRAP below (FlowRow) so nothing ever clips
 * (item 19: the old horizontal Row cut tags off).
 */
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
