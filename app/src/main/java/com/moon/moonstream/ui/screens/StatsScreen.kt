package com.moon.moonstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.moon.moonstream.ui.components.MsSegmented
import com.moon.moonstream.ui.stats.MostWatchedItem
import com.moon.moonstream.ui.stats.StatsRange
import com.moon.moonstream.ui.stats.WatchStats
import com.moon.moonstream.ui.stats.WatchStatsLoader
import com.moon.moonstream.ui.theme.Inter
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.doto
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
 *
 * Look (moon's fix 2026-11-11 — the first version was flat grey cards
 * and plain Inter next to the Library card's glow): Doto for every
 * number and title, and every card is a StatsCard — a subtle radial
 * Accent glow + a hairline outline, the Library card's language turned
 * down so a screen full of cards doesn't become a wall of blue.
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
                style = MaterialTheme.typography.headlineSmall.doto(),
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
        StatsCard {
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
                                    .clip(RoundedCornerShape(6.dp))
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
                        style = MaterialTheme.typography.bodyMedium.doto(),
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
            StatsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    s.mostWatched.forEachIndexed { idx, item ->
                        MostWatchedRow(item, idx + 1)
                        if (idx < s.mostWatched.lastIndex) {
                            // iOS list hairline between rows (moon's fix 2026-11-11).
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MsColors.Divider),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // By type.
        if (s.byType.isNotEmpty()) {
            StatsSectionTitle("By type")
            Spacer(Modifier.height(8.dp))
            StatsCard {
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
                            Color(0xFFFF8A3D),
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
                                style = MaterialTheme.typography.bodyMedium.doto(),
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
        StatsBarList(
            items = s.topGenres.map { it.name to it.hoursMs },
            emptyText = "No genres recorded yet — they fill in as titles are indexed.",
            coverageText = "Genres on ${s.genreCoverage.first} of ${s.genreCoverage.second} titles.",
        )
        Spacer(Modifier.height(20.dp))

        // By language (moon's 2026-11-11).
        StatsSectionTitle("By language")
        Spacer(Modifier.height(8.dp))
        StatsBarList(
            items = s.topLanguages.map { it.name to it.hoursMs },
            emptyText = "No languages recorded yet — they fill in as titles are indexed.",
            coverageText = "Languages on ${s.languageCoverage.first} of ${s.languageCoverage.second} titles.",
        )
        Spacer(Modifier.height(20.dp))

        // By country (moon's 2026-11-11).
        StatsSectionTitle("By country")
        Spacer(Modifier.height(8.dp))
        StatsBarList(
            items = s.topCountries.map { it.name to it.hoursMs },
            emptyText = "No countries recorded yet — they fill in as titles are indexed.",
            coverageText = "Countries on ${s.countryCoverage.first} of ${s.countryCoverage.second} titles.",
        )
        Spacer(Modifier.height(20.dp))

        // By decade (moon's 2026-11-11).
        StatsSectionTitle("By decade")
        Spacer(Modifier.height(8.dp))
        StatsBarList(
            items = s.topDecades.map { it.label to it.hoursMs },
            emptyText = "No years recorded yet — they fill in as titles are indexed.",
        )
        Spacer(Modifier.height(20.dp))

        // Watch streak.
        StatsSectionTitle("Watch streak")
        Spacer(Modifier.height(8.dp))
        StatsCard {
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
        StatsSectionTitle("Recently finished")
        Spacer(Modifier.height(8.dp))
        if (s.recentlyFinished.isNotEmpty()) {
            StatsCard {
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
                        if (idx < s.recentlyFinished.lastIndex) {
                            // iOS list hairline between rows.
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MsColors.Divider),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        } else {
            StatsCard {
                Text(
                    "Nothing finished yet — finish a title and it'll show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

/**
 * The stats screen's card (moon's fix 2026-11-11): the Library card's
 * language turned down — a subtle radial Accent glow in the top-left
 * corner plus a hairline outline — so a screen full of cards feels
 * rich instead of flat grey, without becoming a wall of blue.
 */
@Composable
private fun StatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MsColors.GlowDeep.copy(alpha = 0.35f),
                        MsColors.GlowDeep.copy(alpha = 0.12f),
                        Color(0xFF1C1C1E),
                        Color(0xFF1C1C1E),
                    ),
                    center = Offset(0.15f, 0.1f),
                    radius = 600f,
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
    ) {
        content()
    }
}

@Composable
private fun StatsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.doto(),
        fontWeight = FontWeight.SemiBold,
        color = MsColors.TextPrimary,
    )
}

/**
 * A bar list for the breakdown sections (genres, languages, countries,
 * decades): a named row with an Accent bar and the hours in Doto, plus
 * an empty state and an optional coverage caption. One shared shape so
 * the four sections read as a family.
 */
@Composable
private fun StatsBarList(
    items: List<Pair<String, Long>>,
    emptyText: String,
    coverageText: String? = null,
) {
    StatsCard {
        Column(modifier = Modifier.padding(14.dp)) {
            if (items.isEmpty()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            } else {
                val max = (items.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                items.forEach { (name, hours) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                                    .fillMaxWidth((hours.toFloat() / max).coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MsColors.Accent),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatHours(hours),
                            style = MaterialTheme.typography.bodyMedium.doto(),
                            color = MsColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                coverageText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MsColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    StatsCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.doto(),
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
            style = MaterialTheme.typography.titleMedium.doto(),
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

/**
 * "18h 24m" / "1,842h" / "45m" — hours with minutes when under a day.
 * Under a minute it shows seconds ("32s") instead of "0m", which read
 * as missing data (moon's video 2026-11-11: three "0m · 1 ep" rows).
 */
private fun formatHours(ms: Long): String {
    val totalSec = (ms / 1000L).toInt()
    if (totalSec < 60) return "${totalSec}s"
    val totalMin = totalSec / 60
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
