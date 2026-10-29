package com.moon.moonstream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.LimeTextButton
import com.moon.moonstream.ui.components.MsSheetContent
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.model.FakeTitle
import com.moon.moonstream.ui.model.fakeTitles
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii

/**
 * Phase 1: library with fake data.
 * TODO(Phase 3): back with Room observeMedia() Flow; filters map to real queries.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpenDetail: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf("Any") }
    var language by remember { mutableStateOf("Any") }
    var source by remember { mutableStateOf("Any") }
    var sort by remember { mutableStateOf("Recently added") }

    val tabs = listOf("All", "Movies", "Series", "Anime", "Hidden")
    val hiddenIds = AppUiState.hiddenIds
    val filtered = fakeTitles.filter { t ->
        val isHidden = hiddenIds.contains(t.id)
        val matchesTab = when (tab) {
            0 -> !isHidden
            1 -> t.type == "movie" && !isHidden
            2 -> t.type == "series" && !isHidden
            3 -> t.type == "anime" && !isHidden
            4 -> isHidden
            else -> true
        }
        val matchesQuery = query.isBlank() || t.title.contains(query, ignoreCase = true)
        matchesTab && matchesQuery
    }
    val inHiddenTab = tab == 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 8.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MsTextField(
                value = query,
                onValueChange = { query = it },
                hint = "Search library",
                modifier = Modifier.weight(1f),
            )
            BareIconButton(
                icon = Icons.Filled.FilterList,
                contentDescription = "Filters",
                tint = MsColors.TextSecondary,
                onClick = { showFilters = true },
            )
        }

        // Custom tabs: lime underline on the active tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            tabs.forEachIndexed { i, name ->
                val selected = tab == i
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { tab = i }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MsColors.Accent else MsColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (selected) MsColors.Accent else Color.Transparent,
                            ),
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.id }) { title ->
                PosterCell(
                    title = title,
                    inHiddenTab = inHiddenTab,
                    onClick = { onOpenDetail(title.id) },
                    onToggleHide = {
                        if (hiddenIds.contains(title.id)) hiddenIds.remove(title.id)
                        else hiddenIds.add(title.id)
                    },
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCell(
    title: FakeTitle,
    inHiddenTab: Boolean,
    onClick: () -> Unit,
    onToggleHide: () -> Unit,
) {
    val context = LocalContext.current
    var showHideMenu by remember { mutableStateOf(false) }
    val isHidden = AppUiState.hiddenIds.contains(title.id)

    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { showHideMenu = true },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(MsRadii.Poster))
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

            // Long-press menu: Hide / Unhide entry (anchored to the poster).
            DropdownMenu(
                expanded = showHideMenu,
                onDismissRequest = { showHideMenu = false },
                modifier = Modifier.background(MsColors.SurfaceAlt),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isHidden) "Unhide entry" else "Hide entry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                        )
                    },
                    onClick = {
                        onToggleHide()
                        Toast.makeText(
                            context,
                            if (isHidden) "Unhidden (fake)" else "Hidden (fake)",
                            Toast.LENGTH_SHORT,
                        ).show()
                        showHideMenu = false
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${title.year}",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
