package com.moon.moonstream.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.ui.components.BareIconButton
import com.moon.moonstream.ui.components.MsSegmented
import com.moon.moonstream.ui.stats.FinishedItem
import com.moon.moonstream.ui.stats.MostWatchedItem
import com.moon.moonstream.ui.stats.StatsRange
import com.moon.moonstream.ui.stats.WatchStats
import com.moon.moonstream.ui.stats.WatchStatsLoader
import com.moon.moonstream.ui.theme.Inter
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.doto
import com.moon.moonstream.ui.theme.motionDurationMs
import com.moon.moonstream.ui.theme.rememberReducedMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Watch Stats screen (moon's pick 2026-11-10; rebuilt 2026-11-11).
 *
 * What changed in the rebuild, all of it aimed at "more glow, more good
 * stats, iOS-style motion, still smooth on the G96":
 * - a HERO card in the Library card's language (gradient border + radial
 *   glow): the period's hours huge in Doto, next to it the change against
 *   the previous period, today's time, the best day and the streak, with a
 *   small 7-bar sparkline along the bottom.
 * - new numbers: active days, longest session, average per active day, a
 *   By quality breakdown (stream_files.quality) and a library snapshot
 *   (titles / files / size on Telegram).
 * - motion: numbers count up, bars grow from zero, sections fade + lift,
 *   all driven by ONE animation value (a single Animatable read inside
 *   graphicsLayer), so nothing recomposes per frame and a low-RAM phone
 *   just sees a shorter version of the same thing (see rememberReducedMotion).
 * - the whole screen is a LazyColumn: a stats screen is long, and composing
 *   only the visible cards is the single biggest low-RAM win here.
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { MsDatabase.get(context) }
    val reduced = rememberReducedMotion()
    var range by remember { mutableStateOf(StatsRange.WEEK) }
    var stats by remember { mutableStateOf<WatchStats?>(null) }
    var selectedBar by remember { mutableStateOf<Int?>(null) }

    // The one animation that drives the whole reveal. Read inside
    // graphicsLayer lambdas and bar scaling, never in composition, so the
    // per-frame work stays on the render thread.
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(range) {
        selectedBar = null
        reveal.snapTo(0f)
        stats = withContext(Dispatchers.IO) { WatchStatsLoader.load(db, range) }
        val ms = motionDurationMs(520, reduced)
        if (ms <= 0) reveal.snapTo(1f) else reveal.animateTo(1f, tween(ms, easing = FastOutSlowInEasing))
    }

    val s = stats
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { StatsHeader(onBack = onBack, stats = s) }
        item(key = "range") {
            MsSegmented(
                options = StatsRange.entries.map { it.label },
                selected = range.ordinal,
                onSelect = { range = StatsRange.entries[it] },
                modifier = Modifier.statsReveal(reveal, 1),
            )
        }

        if (s != null && isEmpty(s)) {
            item(key = "empty") {
                EmptyStatsCard(modifier = Modifier.fillMaxWidth().statsReveal(reveal, 2))
            }
        }

        if (s != null && !isEmpty(s)) {
            item(key = "hero") {
                HeroCard(
                    s = s,
                    reveal = reveal,
                    reduced = reduced,
                    modifier = Modifier.fillMaxWidth().statsReveal(reveal, 2),
                )
            }
            item(key = "grid") {
                QuickGrid(s = s, reveal = reveal, reduced = reduced,
                    modifier = Modifier.fillMaxWidth().statsReveal(reveal, 3))
            }
            item(key = "chart") {
                SectionTitle("Watch time", s.chartCaption())
                Spacer(Modifier.height(8.dp))
                ChartCard(
                    s = s,
                    selectedBar = selectedBar,
                    onSelectBar = { selectedBar = it },
                    reveal = reveal,
                    index = 4,
                    modifier = Modifier.fillMaxWidth().statsReveal(reveal, 4),
                )
            }
            if (s.mostWatched.isNotEmpty()) {
                item(key = "most") {
                    SectionTitle("Most watched")
                    Spacer(Modifier.height(8.dp))
                    MostWatchedCard(s.mostWatched, reveal,
                        Modifier.fillMaxWidth().statsReveal(reveal, 5))
                }
            }
            if (s.byType.isNotEmpty()) {
                item(key = "type") {
                    SectionTitle("By type")
                    Spacer(Modifier.height(8.dp))
                    TypeCard(s, reveal, Modifier.fillMaxWidth().statsReveal(reveal, 6))
                }
            }
            item(key = "genres") {
                SectionTitle("Top genres")
                Spacer(Modifier.height(8.dp))
                BarListCard(
                    items = s.topGenres.map { it.name to it.hoursMs },
                    emptyText = "No genres recorded yet — they fill in as titles are indexed.",
                    coverageText = "Genres on ${s.genreCoverage.first} of ${s.genreCoverage.second} titles.",
                    reveal = reveal,
                    index = 7,
                    modifier = Modifier.fillMaxWidth().statsReveal(reveal, 7),
                )
            }
            item(key = "lang") {
                SectionTitle("By language")
                Spacer(Modifier.height(8.dp))
                BarListCard(
                    items = s.topLanguages.map { it.name to it.hoursMs },
                    emptyText = "No languages recorded yet — they fill in as titles are indexed.",
                    coverageText = "Languages on ${s.languageCoverage.first} of ${s.languageCoverage.second} titles.",
                    reveal = reveal,
                    index = 8,
                    modifier = Modifier.fillMaxWidth().statsReveal(reveal, 8),
                )
            }
            item(key = "country") {
                SectionTitle("By country")
                Spacer(Modifier.height(8.dp))
                BarListCard(
                    items = s.topCountries.map { it.name to it.hoursMs },
                    emptyText = "No countries recorded yet — they fill in as titles are indexed.",
                    coverageText = "Countries on ${s.countryCoverage.first} of ${s.countryCoverage.second} titles.",
                    reveal = reveal,
                    index = 9,
                    modifier = Modifier.fillMaxWidth().statsReveal(reveal, 9),
                )
            }
            item(key = "decade") {
                SectionTitle("By decade")
                Spacer(Modifier.height(8.dp))
                BarListCard(
                    items = s.topDecades.map { it.label to it.hoursMs },
                    emptyText = "No years recorded yet — they fill in as titles are indexed.",
                    reveal = reveal,
                    index = 10,
                    modifier = Modifier.fillMaxWidth().statsReveal(reveal, 10),
                )
            }
            if (s.byQuality.isNotEmpty()) {
                item(key = "quality") {
                    SectionTitle("By quality", "The files you actually played.")
                    Spacer(Modifier.height(8.dp))
                    BarListCard(
                        items = s.byQuality.map { it.label to it.hoursMs },
                        emptyText = "",
                        reveal = reveal,
                        index = 11,
                        modifier = Modifier.fillMaxWidth().statsReveal(reveal, 11),
                    )
                }
            }
            item(key = "habits") {
                SectionTitle("Habits")
                Spacer(Modifier.height(8.dp))
                HabitsCard(s, reveal, reduced,
                    Modifier.fillMaxWidth().statsReveal(reveal, 12))
            }
            item(key = "library") {
                SectionTitle("Your library")
                Spacer(Modifier.height(8.dp))
                LibraryCard(s, reveal, reduced,
                    Modifier.fillMaxWidth().statsReveal(reveal, 13))
            }
            item(key = "recent") {
                SectionTitle("Recently finished")
                Spacer(Modifier.height(8.dp))
                RecentlyFinishedCard(s.recentlyFinished, reveal,
                    Modifier.fillMaxWidth().statsReveal(reveal, 14))
            }
        } else if (s == null) {
            item(key = "loading") {
                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Loading your stats…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

private fun isEmpty(s: WatchStats): Boolean =
    s.hoursWatchedMs == 0L && s.episodesWatched == 0 &&
        s.mostWatched.isEmpty() && s.recentlyFinished.isEmpty()

private fun WatchStats.chartCaption(): String = when (range) {
    StatsRange.WEEK -> "Last 7 days"
    StatsRange.MONTH -> "This month by weekday"
    StatsRange.ALL -> "Last 7 months"
}

// ---------------------------------------------------------------------------
// Reveal: one shared progress value, read inside graphicsLayer so the slide
// costs no recomposition. Sections start a hair after each other; the whole
// thing is over in ~0.5s.
// ---------------------------------------------------------------------------
private fun Modifier.statsReveal(reveal: Animatable<Float, AnimationVector1D>, index: Int): Modifier =
    this.graphicsLayer {
        val start = (index * 0.035f).coerceAtMost(0.6f)
        val f = ((reveal.value - start) / (1f - start)).coerceIn(0f, 1f)
        alpha = f
        translationY = (1f - f) * 16.dp.toPx()
    }

/**
 * A number that counts up from 0 when it first appears (and again when the
 * range changes). Reads the animation inside the Text call, so only this
 * line recomposes while it runs — a handful of numbers, ~0.7s, then nothing.
 */
@Composable
private fun CountUp(
    value: Long,
    format: (Long) -> String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    reduced: Boolean,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value, reduced) {
        if (reduced) {
            anim.snapTo(1f)
        } else {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    Text(
        text = format((value * anim.value).toLong()),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------
@Composable
private fun StatsHeader(onBack: () -> Unit, stats: WatchStats?) {
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
        Spacer(Modifier.weight(1f))
        if (stats != null && stats.currentStreakDays > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MsColors.Accent.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "${stats.currentStreakDays}d streak",
                    style = MaterialTheme.typography.labelMedium.doto(),
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.Accent,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero — the Library card's glow language, big number, sparkline.
// ---------------------------------------------------------------------------
@Composable
private fun HeroCard(
    s: WatchStats,
    reveal: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val delta = when {
        s.range == StatsRange.ALL -> null
        s.prevHoursMs <= 0L -> null
        else -> ((s.hoursWatchedMs - s.prevHoursMs) * 100f / s.prevHoursMs)
    }
    val periodWord = when (s.range) {
        StatsRange.WEEK -> "this week"
        StatsRange.MONTH -> "this month"
        StatsRange.ALL -> "all time"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MsColors.GlowLight,
                        MsColors.GlowDeep,
                        MsColors.GlowDeep.copy(alpha = 0.7f),
                        MsColors.Bg,
                    ),
                ),
            )
            .padding(1.5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.5.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MsColors.GlowDeep.copy(alpha = 0.75f),
                            MsColors.GlowDeep.copy(alpha = 0.32f),
                            MsColors.Bg,
                            MsColors.Bg,
                        ),
                        center = Offset(0.12f, 0.05f),
                        radius = 800f,
                    ),
                )
                .padding(18.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (s.range) {
                            StatsRange.WEEK -> "YOUR WEEK"
                            StatsRange.MONTH -> "YOUR MONTH"
                            StatsRange.ALL -> "ALL TIME"
                        },
                        style = MaterialTheme.typography.labelMedium.doto(),
                        fontWeight = FontWeight.SemiBold,
                        color = MsColors.Accent,
                    )
                    Spacer(Modifier.weight(1f))
                    s.topGenres.firstOrNull()?.let {
                        Text(
                            it.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MsColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(
                        value = s.hoursWatchedMs,
                        format = ::formatHours,
                        style = MaterialTheme.typography.displayMedium.doto(),
                        color = Color.White,
                        reduced = reduced,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        periodWord,
                        style = MaterialTheme.typography.bodyMedium.doto(),
                        color = MsColors.TextSecondary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (delta != null) {
                        val up = delta >= 0f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (up) Color(0xFF7DD956).copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.08f),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                (if (up) "↑ " else "↓ ") +
                                    "${kotlin.math.abs(delta).toInt()}% vs previous",
                                style = MaterialTheme.typography.labelSmall.doto(),
                                fontWeight = FontWeight.SemiBold,
                                color = if (up) Color(0xFF7DD956) else MsColors.TextSecondary,
                            )
                        }
                    } else {
                        Text(
                            "Today ${formatHours(s.todayMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MsColors.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroFact("Today", formatHours(s.todayMs), Modifier.weight(1f))
                    HeroFact(
                        "Best day",
                        if (s.bestDayMs > 0) formatHours(s.bestDayMs) else "—",
                        Modifier.weight(1f),
                        sub = s.bestDayLabel.takeIf { it.isNotBlank() },
                    )
                    HeroFact("Streak", "${s.currentStreakDays}d", Modifier.weight(1f))
                }
                if (s.chartBars.any { it.second > 0f }) {
                    Spacer(Modifier.height(14.dp))
                    Sparkline(s.chartBars, reveal)
                }
            }
        }
    }
}

@Composable
private fun HeroFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.doto(),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (sub != null) "$label · $sub" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MsColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 7 tiny bars under the hero number; grow from the bottom with the reveal. */
@Composable
private fun Sparkline(bars: List<Pair<String, Float>>, reveal: Animatable<Float, AnimationVector1D>) {
    val max = (bars.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(0.001f)
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { i, (_, hours) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // Same stagger trick as the big chart: scale only.
                        val start = 0.25f + i * 0.02f
                        val f = ((reveal.value - start) / (1f - start)).coerceIn(0f, 1f)
                        val h = (hours / max).coerceIn(0.06f, 1f)
                        scaleY = h * f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MsColors.Accent, MsColors.Accent.copy(alpha = 0.35f)),
                        ),
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Quick grid
// ---------------------------------------------------------------------------
@Composable
private fun QuickGrid(
    s: WatchStats,
    reveal: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Episodes", s.episodesWatched.toLong(), reveal, 3, reduced, Modifier.weight(1f))
            StatTile("Movies", s.moviesWatched.toLong(), reveal, 3, reduced, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Finished", s.titlesFinished.toLong(), reveal, 4, reduced, Modifier.weight(1f))
            StatTile("Active days", s.activeDays.toLong(), reveal, 4, reduced, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Long,
    reveal: Animatable<Float, AnimationVector1D>,
    index: Int,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            CountUp(
                value = value,
                format = { "%,d".format(it) },
                style = MaterialTheme.typography.headlineSmall.doto(),
                color = MsColors.TextPrimary,
                reduced = reduced,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MsColors.TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Chart
// ---------------------------------------------------------------------------
@Composable
private fun ChartCard(
    s: WatchStats,
    selectedBar: Int?,
    onSelectBar: (Int) -> Unit,
    reveal: Animatable<Float, AnimationVector1D>,
    index: Int,
    modifier: Modifier = Modifier,
) {
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            val max = (s.chartBars.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(0.001f)
            Row(
                modifier = Modifier.fillMaxWidth().height(128.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                s.chartBars.forEachIndexed { idx, (label, hours) ->
                    val selected = selectedBar == idx
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // No Android ripple: the bar lighting up is the
                            // feedback (moon's 2026-11-11).
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onSelectBar(idx) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    (hours / max * 96f)
                                        .coerceAtLeast(if (hours > 0f) 5f else 3f).dp,
                                )
                                .graphicsLayer {
                                    val start = (index * 0.035f) + idx * 0.02f
                                    val f = ((reveal.value - start) / 0.45f).coerceIn(0f, 1f)
                                    scaleY = f
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selected) {
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.95f), MsColors.Accent),
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                MsColors.Accent.copy(alpha = 0.85f),
                                                MsColors.Accent.copy(alpha = 0.35f),
                                            ),
                                        )
                                    },
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MsColors.TextPrimary else MsColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val idx = selectedBar
            if (idx != null && idx in s.chartBars.indices) {
                val (label, hours) = s.chartBars[idx]
                Text(
                    "$label — ${formatHours((hours * 3_600_000f).toLong())}",
                    style = MaterialTheme.typography.bodyMedium.doto(),
                    color = MsColors.TextPrimary,
                )
            } else {
                val best = s.chartBars.maxByOrNull { it.second }
                Text(
                    if (best != null && best.second > 0f) {
                        "Peak: ${best.first} · ${formatHours((best.second * 3_600_000f).toLong())}. Tap a bar for details."
                    } else {
                        "Nothing watched in this period yet."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MsColors.TextSecondary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Most watched
// ---------------------------------------------------------------------------
@Composable
private fun MostWatchedCard(
    items: List<MostWatchedItem>,
    reveal: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            val max = (items.maxOfOrNull { it.hoursMs } ?: 1L).coerceAtLeast(1L)
            items.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${idx + 1}",
                        style = MaterialTheme.typography.titleMedium.doto(),
                        fontWeight = FontWeight.Bold,
                        color = MsColors.Accent,
                        modifier = Modifier.width(22.dp),
                    )
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp, 52.dp)
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MsColors.TextSecondary,
                        )
                        Spacer(Modifier.height(5.dp))
                        // Share of the top title, grown with the reveal.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.10f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((item.hoursMs.toFloat() / max).coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        val start = 0.4f + idx * 0.03f
                                        val f = ((reveal.value - start) / (1f - start)).coerceIn(0f, 1f)
                                        scaleX = f
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .background(MsColors.Accent),
                            )
                        }
                    }
                }
                if (idx < items.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// By type
// ---------------------------------------------------------------------------
@Composable
private fun TypeCard(
    s: WatchStats,
    reveal: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val palette = listOf(
        MsColors.Accent,
        Color(0xFF7DD956),
        Color(0xFFB388FF),
        Color(0xFFFF8A3D),
    )
    val total = s.byType.sumOf { it.count }.coerceAtLeast(1)
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .graphicsLayer {
                        val f = ((reveal.value - 0.2f) / 0.5f).coerceIn(0f, 1f)
                        scaleX = f
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            ) {
                s.byType.forEachIndexed { idx, tc ->
                    Box(
                        modifier = Modifier
                            .weight(tc.count.toFloat() / total)
                            .fillMaxHeight()
                            .background(palette[idx % palette.size]),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            s.byType.forEachIndexed { idx, tc ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette[idx % palette.size]),
                    )
                    Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared bar list (genres / languages / countries / decades / quality)
// ---------------------------------------------------------------------------
@Composable
private fun BarListCard(
    items: List<Pair<String, Long>>,
    emptyText: String,
    reveal: Animatable<Float, AnimationVector1D>,
    index: Int,
    modifier: Modifier = Modifier,
    coverageText: String? = null,
) {
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (items.isEmpty()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            } else {
                val max = (items.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                items.forEachIndexed { row, (name, hours) ->
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
                            modifier = Modifier.width(92.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.10f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((hours.toFloat() / max).coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        val start = (index * 0.035f) + row * 0.03f
                                        val f = ((reveal.value - start) / 0.5f).coerceIn(0f, 1f)
                                        scaleX = f
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(MsColors.Accent, MsColors.Accent.copy(alpha = 0.45f)),
                                        ),
                                    ),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatHours(hours),
                            style = MaterialTheme.typography.labelMedium.doto(),
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

// ---------------------------------------------------------------------------
// Habits
// ---------------------------------------------------------------------------
@Composable
private fun HabitsCard(
    s: WatchStats,
    reveal: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val perActiveDay = if (s.activeDays > 0) s.hoursWatchedMs / s.activeDays else 0L
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Per active day", perActiveDay, reveal, 12, reduced, Modifier.weight(1f))
                StatTile("Longest session", s.longestSessionMs, reveal, 12, reduced, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Current streak", s.currentStreakDays.toLong(), reveal, 13, reduced, Modifier.weight(1f))
                StatTile("Longest streak", s.longestStreakDays.toLong(), reveal, 13, reduced, Modifier.weight(1f))
            }
            if (s.bestDayLabel.isNotBlank() && s.bestDayMs > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Best day: ${s.bestDayLabel} · ${formatHours(s.bestDayMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MsColors.TextSecondary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Library snapshot
// ---------------------------------------------------------------------------
@Composable
private fun LibraryCard(
    s: WatchStats,
    reveal: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    GlowCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibFact("Titles", s.libraryTitles.toLong(), reduced, Modifier.weight(1f))
            LibFact("Files", s.libraryFiles.toLong(), reduced, Modifier.weight(1f))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatBytes(s.libraryBytes),
                    style = MaterialTheme.typography.titleMedium.doto(),
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "On Telegram",
                    style = MaterialTheme.typography.labelSmall,
                    color = MsColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun LibFact(
    label: String,
    value: Long,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CountUp(
            value = value,
            format = { "%,d".format(it) },
            style = MaterialTheme.typography.titleMedium.doto(),
            color = MsColors.TextPrimary,
            reduced = reduced,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MsColors.TextSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Recently finished
// ---------------------------------------------------------------------------
@Composable
private fun RecentlyFinishedCard(
    items: List<FinishedItem>,
    reveal: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (items.isEmpty()) {
                Text(
                    "Nothing finished yet — finish a title and it'll show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MsColors.TextSecondary,
                )
            } else {
                items.forEachIndexed { idx, item ->
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
                                style = MaterialTheme.typography.labelSmall,
                                color = MsColors.TextSecondary,
                            )
                        }
                    }
                    if (idx < items.lastIndex) {
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
    }
}

@Composable
private fun EmptyStatsCard(modifier: Modifier = Modifier) {
    GlowCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Nothing watched yet",
                style = MaterialTheme.typography.titleMedium.doto(),
                fontWeight = FontWeight.SemiBold,
                color = MsColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Play something from your library and this screen fills itself in — hours, streaks, what you watch most.",
                style = MaterialTheme.typography.bodyMedium,
                color = MsColors.TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------
@Composable
private fun SectionTitle(text: String, caption: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MsColors.Accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.doto(),
            fontWeight = FontWeight.SemiBold,
            color = MsColors.TextPrimary,
        )
        if (caption != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The standard stats card: a soft radial accent glow in the top-left corner
 * over the app background, plus a hairline outline. Cheap (two gradients on
 * one layer, no blur, no shadow) so a screen of them stays light.
 */
@Composable
private fun GlowCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MsColors.GlowDeep.copy(alpha = 0.30f),
                        MsColors.GlowDeep.copy(alpha = 0.10f),
                        Color(0xFF1C1C1E),
                        Color(0xFF1C1C1E),
                    ),
                    center = Offset(0.1f, 0.0f),
                    radius = 560f,
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp)),
    ) {
        content()
    }
}

/**
 * "18h 24m" / "1,842h" / "45m" — hours with minutes when under a day.
 * Under a minute it shows seconds ("32s") instead of "0m", which read as
 * missing data (moon's video 2026-11-11: three "0m · 1 ep" rows).
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val tb = bytes / 1_099_511_627_776.0
    if (tb >= 1) return String.format(Locale.US, "%.1f TB", tb)
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1) return String.format(Locale.US, "%.0f MB", mb)
    return String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
