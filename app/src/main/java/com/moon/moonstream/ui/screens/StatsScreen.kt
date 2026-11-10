package com.moon.moonstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.MsCard
import com.moon.moonstream.ui.components.MsSegmented
import com.moon.moonstream.ui.stats.MostWatchedItem
import com.moon.moonstream.ui.stats.StatsRange
import com.moon.moonstream.ui.stats.WatchStats
import com.moon.moonstream.ui.stats.WatchStatsLoader
import com.moon.moonstream.ui.theme.Inter
import com.moon.moonstream.ui.theme.MsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Watch Stats screen (moon's pick 2026-11-10): the whole picture of
 * what he watched — hours, finishes, most-watched, type split, genres,
 * streak, recently finished. Opened from the stats card on the home
 * screen (under the Library card). All numbers come from watch_progress
 * + media_items; "hours watched" is the sum of positionMs (time into
 * titles), the best the DB holds.
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { MsDatabase.get(context) }
    var range by remember { mutableStateOf(StatsRange.WEEK) }
    var stats by remember { mutableStateOf<WatchStats?>(null) }
    var selectedBar by remember { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(range) {
        selectedBar = null
        stats = withContext(Dispatchers.IO) { WatchStatsLoader.load(db, range) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 32.dp),
    ) {
        // Header: back + title.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Text(
                "Your Stats",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Inter),
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        // Range: This week / This month / All time.
        MsSegmented(
            options = StatsRange.entries.map { it.label },
            selected = range.ordinal,
            onSelect = { range = StatsRange.entries[it] },
        )
        Spacer(Modifier.height(16.dp))

        val s = stats
        if (s == null) {
            // Loading state.
            Text(
                "Loading your stats…",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return@Column
        }

        // Overview 2x2.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                value = formatHours(s.hoursWatchedMs),
                label = "Hours watched",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = s.titlesFinished.toString(),
                label = "Titles finished",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                value = s.episodesWatched.toString(),
                label = "Episodes watched",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = s.moviesWatched.toString(),
                label = "Movies watched",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))

        // Watch time chart.
        StatsSectionTitle("Watch time")
        Spacer(Modifier.height(8.dp))
        MsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val max = (s.chartBars.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)
                    s.chartBars.forEachIndexed { idx, (label, hours) ->
                        Column(
                            modifier = Modifier.weight(1f).clickable { selectedBar = idx },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((hours / max * 88f).coerceAtLeast(if (hours > 0f) 4f else 2f).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (selectedBar == idx) MsColors.Accent
                                        else MsColors.Accent.copy(alpha = 0.55f),
                                    ),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MsColors.TextSecondary,
                            )
                        }
                    }
                }
                selectedBar?.let { idx ->
                    val (label, hours) = s.chartBars[idx]
                    Text(
                        "$label — ${formatHours((hours * 3_600_000f).toLong())}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Inter),
                        color = MsColors.TextPrimary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // Most watched.
        if (s.mostWatched.isNotEmpty()) {
            StatsSectionTitle("Most watched")
            Spacer(Modifier.height(8.dp))
            MsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    s.mostWatched.forEachIndexed { idx, item ->
                        MostWatchedRow(item, idx + 1)
                        if (idx < s.mostWatched.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // By type.
        if (s.byType.isNotEmpty()) {
            StatsSectionTitle("By type")
            Spacer(Modifier.height(8.dp))
            MsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    val total = s.byType.sumOf { it.count }.coerceAtLeast(1)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    ) {
                        val colors = listOf(
                            MsColors.Accent,
                            Color(0xFF7DD956),
                            Color(0xFFB388FF),
                        )
                        s.byType.forEachIndexed { idx, tc ->
                            Box(
                                modifier = Modifier
                                    .weight(tc.count.toFloat() / total)
                                    .height(10.dp)
                                    .background(colors[idx % colors.size]),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    s.byType.forEach { tc ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tc.type.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MsColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${tc.count} · ${(tc.count * 100 / total)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MsColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Top genres.
        StatsSectionTitle("Top genres")
        Spacer(Modifier.height(8.dp))
        MsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                if (s.topGenres.isEmpty()) {
                    Text(
                        "No genres recorded yet — they fill in as titles are indexed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                    )
                } else {
                    val max = (s.topGenres.maxOfOrNull { it.hoursMs } ?: 1L).coerceAtLeast(1L)
                    s.topGenres.forEach { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                g.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MsColors.TextPrimary,
                                modifier = Modifier.width(96.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((g.hoursMs.toFloat() / max).coerceIn(0f, 1f))
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MsColors.Accent),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatHours(g.hoursMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MsColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "Genres on ${s.genreCoverage.first} of ${s.genreCoverage.second} titles.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MsColors.TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // Watch streak.
        StatsSectionTitle("Watch streak")
        Spacer(Modifier.height(8.dp))
        MsCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    value = "${s.currentStreakDays}d",
                    label = "Current streak",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = "${s.longestStreakDays}d",
                    label = "Longest streak",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Recently finished.
        if (s.recentlyFinished.isNotEmpty()) {
            StatsSectionTitle("Recently finished")
            Spacer(Modifier.height(8.dp))
            MsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    s.recentlyFinished.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp, 56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Inter),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MsColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                                        .format(Date(item.finishedAtMs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MsColors.TextSecondary,
                                )
                            }
                        }
                        if (idx < s.recentlyFinished.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = Inter),
        fontWeight = FontWeight.SemiBold,
        color = MsColors.TextPrimary,
    )
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    MsCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Inter),
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun MostWatchedRow(item: MostWatchedItem, rank: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Inter),
            fontWeight = FontWeight.Bold,
            color = MsColors.Accent,
            modifier = Modifier.width(24.dp),
        )
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp, 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Inter),
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatHours(item.hoursMs)} · ${item.episodes} ep",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
    }
}

/** "18h 24m" / "1,842h" / "45m" — hours with minutes when under a day. */
private fun formatHours(ms: Long): String {
    val totalMin = (ms / 60_000L).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h >= 100) {
        "%,d".format(h) + "h"
    } else if (h > 0) {
        "${h}h ${m}m"
    } else {
        "${m}m"
    }
}
