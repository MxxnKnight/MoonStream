package com.moon.moonstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.model.FakeTitle
import com.moon.moonstream.ui.model.fakeTitles

/**
 * Phase 1: library with fake data.
 * TODO(Phase 3): back with Room observeMedia() Flow; filters map to real queries.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val filtered = fakeTitles.filter { t ->
        val matchesTab = when (tab) {
            0 -> !t.hidden
            1 -> t.type == "movie" && !t.hidden
            2 -> t.type == "series"
            3 -> t.type == "anime"
            4 -> t.hidden
            else -> true
        }
        val matchesQuery = query.isBlank() ||
            t.title.contains(query, ignoreCase = true)
        matchesTab && matchesQuery
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search library") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showFilters = true }) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filters")
            }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, name ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(name) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered) { title ->
                PosterCell(title = title, onClick = { onOpenDetail(title.id) })
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

@Composable
fun PosterCell(title: FakeTitle, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(title.colorA, title.colorB))),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = title.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${title.year}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            FilterRow("Quality", listOf("Any", "4K", "1080p", "720p", "480p"), quality, onQuality)
            FilterRow("Language", listOf("Any", "English", "Malayalam", "Hindi", "Dual"), language, onLanguage)
            FilterRow("Source", listOf("Any", "BluRay", "WEB-DL", "HDTV", "CAM"), source, onSource)
            FilterRow("Sort", listOf("Recently added", "Title A–Z", "Year ↓", "Size ↓"), sort, onSort)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Apply filters")
            }
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
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.take(5).forEach { opt ->
                val isSel = opt == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        opt,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
