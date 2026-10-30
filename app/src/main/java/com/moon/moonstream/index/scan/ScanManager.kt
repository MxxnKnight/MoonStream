package com.moon.moonstream.index.scan

import android.content.Context
import android.util.Log
import com.moon.moonstream.backend.MoonStreamBackend
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.ScanCursorEntity
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.index.match.EnrichedMeta
import com.moon.moonstream.index.match.MetadataMatcher
import com.moon.moonstream.index.parse.FilenameParser
import com.moon.moonstream.index.parse.ParsedMedia
import com.moon.moonstream.index.parse.normalizeTitle
import kotlinx.coroutines.delay

/**
 * Phase 3 scan engine — written fresh for MoonStream.
 *
 * Channels are scanned SEQUENTIALLY, one by one (rate-limit safety, the way
 * NoxMedia does it): polite pauses between message pages and between
 * channels keep Telegram happy.
 *
 * Two modes (moon's request, like TeleStremio):
 * - INCREMENTAL ("Quick scan"): pages newest-first and stops at the stored
 *   cursor — picks up only what the app missed since the last scan.
 * - FROM_SCRATCH ("Full scan"): wipes the channel's rows and walks its
 *   entire history from the newest message down.
 */
enum class ScanMode { INCREMENTAL, FROM_SCRATCH }

/** One channel queued for a scan run. */
data class ScanTarget(
    val channelId: Long,
    val title: String,
    val isAnime: Boolean,
)

object ScanManager {
    private const val TAG = "ScanManager"
    private const val PAGE_SIZE = 100
    private const val PAGE_DELAY_MS = 350L
    private const val CHANNEL_DELAY_MS = 1_500L

    /**
     * Scans every target in order. Safe to call from a foreground service;
     * reports live progress through [ScanUiState].
     */
    suspend fun scanAll(
        context: Context,
        backend: MoonStreamBackend,
        targets: List<ScanTarget>,
        mode: ScanMode,
    ) {
        if (targets.isEmpty()) return
        val db = MsDatabase.get(context)
        val tmdbKey = ScanPrefs.getTmdbKey(context)
        // Metadata lookups are cached per run: one title = one set of API calls.
        val metaCache = mutableMapOf<String, EnrichedMeta?>()

        ScanUiState.beginRun(targets.map { it.channelId to it.title })
        try {
            targets.forEachIndexed { index, target ->
                try {
                    scanOneChannel(db, backend, target, mode, tmdbKey, metaCache)
                } catch (e: Exception) {
                    Log.e(TAG, "channel ${target.channelId} failed: ${e.message}")
                }
                ScanUiState.markDone(target.channelId)
                if (index < targets.lastIndex) delay(CHANNEL_DELAY_MS)
            }
        } finally {
            ScanUiState.finishRun()
        }
    }

    private suspend fun scanOneChannel(
        db: MsDatabase,
        backend: MoonStreamBackend,
        target: ScanTarget,
        mode: ScanMode,
        tmdbKey: String,
        metaCache: MutableMap<String, EnrichedMeta?>,
    ) {
        val channelId = target.channelId

        if (mode == ScanMode.FROM_SCRATCH) {
            db.mediaDao().deleteByChannel(channelId)
            db.streamDao().deleteByChannel(channelId)
            db.unmatchedDao().deleteByChannel(channelId)
            db.scanCursorDao().clear(channelId)
        }

        val cursor = if (mode == ScanMode.INCREMENTAL) {
            db.scanCursorDao().get(channelId)?.lastMessageId
        } else null

        var fromId = Long.MAX_VALUE
        var newestSeen = cursor ?: 0L
        var pages = 0
        var hitCursor = false

        var indexed = 0
        var existing = 0
        var unmatched = 0
        var checked = 0

        while (true) {
            val page = try {
                backend.fetchMessages(channelId, fromId, PAGE_SIZE)
            } catch (e: Exception) {
                Log.e(TAG, "fetchMessages failed ch=$channelId: ${e.message}")
                break
            }
            if (page.isEmpty()) break
            pages++

            for (msg in page) {
                if (msg.id > newestSeen) newestSeen = msg.id
                // Incremental: everything at/below the cursor was scanned before.
                if (cursor != null && msg.id <= cursor) {
                    hitCursor = true
                    break
                }
                checked++
                if (db.streamDao().exists(channelId, msg.id) ||
                    db.unmatchedDao().exists(channelId, msg.id)
                ) {
                    existing++
                    continue
                }
                val parsed = FilenameParser.parse(msg.fileName, target.isAnime)
                if (parsed.title.isBlank()) {
                    db.unmatchedDao().insert(
                        UnmatchedFileEntity(
                            channelId = channelId,
                            messageId = msg.id,
                            fileName = msg.fileName,
                            fileSize = msg.fileSize,
                        ),
                    )
                    unmatched++
                    continue
                }
                indexFile(db, target, msg.id, msg.fileName, msg.fileSize, parsed, tmdbKey, metaCache)
                indexed++
            }

            if (hitCursor) break
            fromId = page.last().id
            // Rough progress: we can't know the total up front, so we pulse
            // forward a little per page and finish at markDone().
            ScanUiState.updateProgress(channelId, (pages * 0.08f).coerceAtMost(0.95f))
            delay(PAGE_DELAY_MS)
        }

        if (newestSeen > (cursor ?: 0L)) {
            db.scanCursorDao().set(ScanCursorEntity(channelId, newestSeen))
        }
        // Stamp the relative "Synced …" time shown under the channel.
        db.channelConfigDao().setLastSynced(channelId, System.currentTimeMillis())
        ScanUiState.addTotals(indexed, existing, unmatched, checked)
        Log.i(TAG, "ch=$channelId done: indexed=$indexed existing=$existing unmatched=$unmatched checked=$checked")
    }

    private suspend fun indexFile(
        db: MsDatabase,
        target: ScanTarget,
        messageId: Long,
        fileName: String,
        fileSize: Long,
        parsed: ParsedMedia,
        tmdbKey: String,
        metaCache: MutableMap<String, EnrichedMeta?>,
    ) {
        val type = if (target.isAnime) "anime" else parsed.type
        val norm = normalizeTitle(parsed.title)
        val cacheKey = "$norm|$type|${parsed.year ?: ""}"
        val meta = metaCache.getOrPut(cacheKey) {
            val m = MetadataMatcher.match(parsed.title, parsed.year, type, target.isAnime, tmdbKey)
            if (m?.imdbId != null) MetadataMatcher.enrichFromCinemeta(m, type) else m
        }

        val now = System.currentTimeMillis()
        val media = db.mediaDao().findMatch(target.channelId, norm, meta?.year ?: parsed.year, type)
        val mediaId = if (media == null) {
            db.mediaDao().upsert(
                MediaItemEntity(
                    channelId = target.channelId,
                    title = meta?.title ?: parsed.title,
                    normalizedTitle = norm,
                    year = meta?.year ?: parsed.year,
                    type = type,
                    imdbId = meta?.imdbId,
                    tmdbId = meta?.tmdbId,
                    posterUrl = meta?.posterUrl,
                    overview = meta?.overview,
                    totalSize = 0L,
                    episodeCount = 0,
                    fileCount = 0,
                    updatedAt = now,
                ),
            )
        } else {
            media.id
        }

        db.streamDao().insert(
            StreamFileEntity(
                mediaId = mediaId,
                channelId = target.channelId,
                messageId = messageId,
                fileName = fileName,
                fileSize = fileSize,
                season = parsed.season,
                episode = parsed.episode,
                quality = parsed.qualityBadge,
            ),
        )
        db.mediaDao().refreshAggregates(mediaId, now)
    }
}
