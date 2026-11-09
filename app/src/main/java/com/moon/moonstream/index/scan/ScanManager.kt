package com.moon.moonstream.index.scan

import android.content.Context
import android.util.Log
import com.moon.moonstream.backend.MoonStreamBackend
import com.moon.moonstream.backend.MsMessage
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.ScanCursorEntity
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.SubtitleFileEntity
import com.moon.moonstream.index.db.UnmatchedFileEntity
import com.moon.moonstream.index.StremioIds
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
    /**
     * Manual channel (moon's 2026-11-09): index its files without asking any
     * metadata provider about them. Every file lands in Unmatched, so an
     * educational clip called "Photosynthesis" can't become a film of that
     * name. Subtitles are stored unlinked for manual attach.
     */
    val manualOnly: Boolean = false,
)

object ScanManager {
    private const val TAG = "ScanManager"
    private const val PAGE_SIZE = 100
    private const val PAGE_DELAY_MS = 350L
    private const val CHANNEL_DELAY_MS = 1_500L

    /**
     * How many pages (× [PAGE_SIZE]) past the cursor a quick scan keeps
     * looking for edited captions. Three pages ≈ the newest 300 messages:
     * that is where uploaders actually fix a typo, and it costs 3 extra
     * requests per channel instead of a full rescan.
     */
    private const val EDIT_LOOKBACK_PAGES = 3

    /** Subtitle extensions the scanner indexes instead of treating as video. */
    private val SUBTITLE_EXTS = setOf("srt", "ass", "ssa", "vtt", "sub", "smi")

    fun isSubtitleFile(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in SUBTITLE_EXTS

    /**
     * Free playability gate (moon's 2026-11-06): true when Telegram's own
     * MIME type says this file is clearly NOT a video. Unknown (null) and
     * generic application/octet-stream pass — many legit videos arrive as
     * those — as do video/ types and the legit application/mp4 + MKV claims.
     * Everything else present (archives, text, images, audio, docs...) is
     * rejected. Note: a file that CLAIMS to be video but isn't (the Philips
     * case, if its mime said video) still passes — catching that needs the
     * header check, which moon declined for now.
     */
    fun isNonVideoMime(mimeType: String?): Boolean {
        val m = mimeType?.trim()?.lowercase() ?: return false
        if (m.startsWith("video/")) return false
        if (m == "application/octet-stream") return false
        if (m == "application/mp4") return false
        if (m == "application/x-matroska") return false
        return true
    }

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
        val omdbKey = ScanPrefs.getOmdbKey(context)
        // Anime numbering mode (moon's 2026-11-06 setting): Absolute counts
        // One Piece 1..1012, Seasonal uses S/E. Read once per run.
        val absoluteAnime = ScanPrefs.isAnimeAbsolute(context)
        // Poster style (moon's 2026-11-06): read once per run. Fanart
        // lookups are cached per run too, keyed by TMDB id.
        val posterStyle = ScanPrefs.getPosterStyle(context)
        val fanartKey = ScanPrefs.getFanartKey(context)
        val fanartCache = mutableMapOf<Int, String?>()
        // Metadata lookups are cached per run: one title = one set of API calls.
        val metaCache = mutableMapOf<String, EnrichedMeta?>()
        // Anime auto-detect (moon's 2026-11-07): one Kitsu check per
        // normalized title per run, so a title with many files is only
        // checked once.
        val animeCache = mutableMapOf<String, Boolean>()

        com.moon.moonstream.util.AppLog.i("SCAN", "Starting scan of ${targets.size} channel(s)")
        ScanUiState.beginRun(targets.map { it.channelId to it.title })
        try {
            targets.forEachIndexed { index, target ->
                try {
                    scanOneChannel(db, backend, target, mode, tmdbKey, omdbKey, metaCache, animeCache, absoluteAnime, posterStyle, fanartKey, fanartCache)
                } catch (e: Exception) {
                    Log.e(TAG, "channel ${target.channelId} failed: ${e.message}")
                    com.moon.moonstream.util.AppLog.e("SCAN", "Channel ${target.title} failed: ${e.message}")
                }
                ScanUiState.markDone(target.channelId)
                if (index < targets.lastIndex) delay(CHANNEL_DELAY_MS)
            }
        } finally {
            // Merge entries the old channel-scoped dedup split (moon's
            // 2026-11-06: one entry per title + year + type, all channels).
            // Idempotent — after the first run there is nothing to merge.
            try {
                val merged = mergeDuplicateTitles(db)
                if (merged > 0) {
                    com.moon.moonstream.util.AppLog.i(
                        "SCAN", "Merged $merged duplicate entries into their titles",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "merge duplicates failed: ${e.message}")
            }
            // Existing rows that were filed as an episode before packs were
            // recognised ("Complete S02" sitting in Season 2 with no episode
            // number) move to the Other bucket once. Idempotent: afterwards
            // no row matches the query.
            try {
                val packMedia = db.streamDao().mediaIdsWithPacks()
                val parked = db.streamDao().parkPacks()
                if (parked > 0) {
                    val now = System.currentTimeMillis()
                    for (mediaId in packMedia) db.mediaDao().refreshAggregates(mediaId, now)
                    com.moon.moonstream.util.AppLog.i(
                        "SCAN", "Moved $parked season pack(s) to the Other bucket",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "park packs failed: ${e.message}")
            }
            ScanUiState.finishRun()
            com.moon.moonstream.util.AppLog.i("SCAN", "Scan complete")
        }
    }

    private suspend fun scanOneChannel(
        db: MsDatabase,
        backend: MoonStreamBackend,
        target: ScanTarget,
        mode: ScanMode,
        tmdbKey: String,
        omdbKey: String,
        metaCache: MutableMap<String, EnrichedMeta?>,
        animeCache: MutableMap<String, Boolean>,
        absoluteAnime: Boolean,
        posterStyle: Int,
        fanartKey: String,
        fanartCache: MutableMap<Int, String?>,
    ) {
        val channelId = target.channelId

        if (mode == ScanMode.FROM_SCRATCH) {
            // Files first, then only entries left empty (moon's 2026-11-06
            // grouping: an entry can hold files from several channels, so
            // wiping one channel must not delete the shared entry).
            db.streamDao().deleteByChannel(channelId)
            db.subtitleDao().deleteByChannel(channelId)
            db.unmatchedDao().deleteByChannel(channelId)
            db.mediaDao().deleteEmpty()
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
        // Last values already pushed to the UI — we report deltas per page
        // so the scan popup shows live counts instead of zeros until done.
        var repIndexed = 0
        var repExisting = 0
        var repUnmatched = 0
        var repChecked = 0
        // Caption edits (moon's 2026-11-09): an edit keeps the message id and
        // its date, so newest-first paging that stops at the cursor would
        // never see it again. Instead of stopping dead we walk a bounded
        // look-back window past the cursor and re-index any message whose
        // Telegram edit stamp moved. The window is only a few pages, so a
        // quick scan stays a quick scan.
        var pagesPastCursor = 0
        var reindexed = 0
        // Entries a re-index may have emptied out (a fixed caption can move a
        // file to a different show), refreshed once the channel is done.
        val affectedMedia = mutableSetOf<Long>()

        while (true) {
            val tgStart = System.currentTimeMillis()
            val page = try {
                backend.fetchMessages(channelId, fromId, PAGE_SIZE)
            } catch (e: Exception) {
                Log.e(TAG, "fetchMessages failed ch=$channelId: ${e.message}")
                com.moon.moonstream.util.AppLog.e("TG", "fetchMessages ch=$channelId failed: ${e.message}")
                break
            }
            // TG log (moon's pick #2): what Telegram gave us.
            com.moon.moonstream.util.AppLog.d(
                "TG",
                "fetchMessages ch=$channelId from=$fromId -> ${page.size} msgs " +
                    "(${System.currentTimeMillis() - tgStart}ms)",
            )
            if (page.isEmpty()) break
            pages++

            for (msg in page) {
                if (msg.id > newestSeen) newestSeen = msg.id
                // Incremental: everything at/below the cursor was scanned
                // before — except that its caption may have been edited
                // since. Check the edit stamp inside the look-back window.
                if (cursor != null && msg.id <= cursor) {
                    hitCursor = true
                    if (pagesPastCursor < EDIT_LOOKBACK_PAGES) {
                        val stored = db.streamDao().storedEditDate(channelId, msg.id)
                        if (stored != null && stored != msg.editDate) {
                            db.streamDao().findByMessage(channelId, msg.id)
                                ?.let { affectedMedia += it.mediaId }
                            db.streamDao().deleteByMessage(channelId, msg.id)
                            db.subtitleDao().deleteByMessage(channelId, msg.id)
                            db.unmatchedDao().deleteByMessage(channelId, msg.id)
                            if (handleMessage(db, target, msg, absoluteAnime, tmdbKey, omdbKey, metaCache, animeCache, posterStyle, fanartKey, fanartCache) == Outcome.INDEXED) {
                                indexed++
                            } else {
                                unmatched++
                            }
                            reindexed++
                            com.moon.moonstream.util.AppLog.i(
                                "SCAN",
                                "ch=$channelId msg=${msg.id} caption edited → re-indexed",
                            )
                        }
                    }
                    continue
                }
                checked++
                if (db.streamDao().exists(channelId, msg.id) ||
                    db.subtitleDao().exists(channelId, msg.id) ||
                    db.unmatchedDao().exists(channelId, msg.id)
                ) {
                    existing++
                    continue
                }
                if (handleMessage(db, target, msg, absoluteAnime, tmdbKey, omdbKey, metaCache, animeCache, posterStyle, fanartKey, fanartCache) == Outcome.INDEXED) {
                    indexed++
                } else {
                    unmatched++
                }
            }

            if (hitCursor) {
                pagesPastCursor++
                if (pagesPastCursor >= EDIT_LOOKBACK_PAGES) break
            }
            fromId = page.last().id
            // Rough progress: we can't know the total up front, so we pulse
            // forward a little per page and finish at markDone().
            ScanUiState.updateProgress(channelId, (pages * 0.08f).coerceAtMost(0.95f))
            // Live counters: push this page's deltas so the popup isn't stuck at 0.
            ScanUiState.addTotals(
                indexed - repIndexed,
                existing - repExisting,
                unmatched - repUnmatched,
                checked - repChecked,
            )
            repIndexed = indexed
            repExisting = existing
            repUnmatched = unmatched
            repChecked = checked
            delay(PAGE_DELAY_MS)
        }

        if (newestSeen > (cursor ?: 0L)) {
            db.scanCursorDao().set(ScanCursorEntity(channelId, newestSeen))
        }
        // A re-indexed file may have left its old entry empty (caption fixed
        // to a different show) or changed its season/episode counts.
        if (affectedMedia.isNotEmpty()) {
            val now = System.currentTimeMillis()
            for (mediaId in affectedMedia) {
                db.mediaDao().refreshAggregates(mediaId, now)
                db.mediaDao().deleteIfEmpty(mediaId)
            }
        }
        if (reindexed > 0) {
            com.moon.moonstream.util.AppLog.i(
                "SCAN", "ch=$channelId: $reindexed edited caption(s) re-indexed",
            )
        }
        // Subtitles can arrive before their video files (newest-first paging),
        // so take a second pass over anything still unlinked. Manual channels
        // take the strict version (exact title only) — subtitle-only channels
        // still have to reach their show (moon's 2026-11-09).
        relinkSubtitles(
            db, channelId, target.isAnime, absoluteAnime, strict = target.manualOnly,
        )
        // Backfill (moon's 2026-11-05 Reacher case): stream rows with NULL
        // season/episode stay NULL forever because re-scans skip existing
        // rows — and Skip Intro needs S/E to query IntroDB. Re-parse those
        // filenames and fill what the parser finds; rows that already have
        // S/E are never touched.
        backfillMissingSeasonEpisode(db, channelId, target.isAnime, absoluteAnime)
        // Stamp the relative "Synced …" time shown under the channel.
        db.channelConfigDao().setLastSynced(channelId, System.currentTimeMillis())
        // Report any remainder not yet pushed per-page (avoids double-counting).
        ScanUiState.addTotals(
            indexed - repIndexed,
            existing - repExisting,
            unmatched - repUnmatched,
            checked - repChecked,
        )
        Log.i(TAG, "ch=$channelId done: indexed=$indexed existing=$existing unmatched=$unmatched checked=$checked")
    }

    /** What a message did: went into the library, or aside as unmatched. */
    private enum class Outcome { INDEXED, UNMATCHED }

    /**
     * The whole per-message pipeline: subtitle → mime gate → parse → match,
     * with the Telegram edit stamp written onto whichever row it produces.
     * Shared by new messages and by re-indexing a message whose caption the
     * uploader fixed (moon's 2026-11-09).
     */
    private suspend fun handleMessage(
        db: MsDatabase,
        target: ScanTarget,
        msg: MsMessage,
        absoluteAnime: Boolean,
        tmdbKey: String,
        omdbKey: String,
        metaCache: MutableMap<String, EnrichedMeta?>,
        animeCache: MutableMap<String, Boolean>,
        posterStyle: Int,
        fanartKey: String,
        fanartCache: MutableMap<Int, String?>,
    ): Outcome {
        val channelId = target.channelId
        // Subtitle files take the subtitle path, not the video path.
        if (isSubtitleFile(msg.fileName)) {
            indexSubtitle(db, target, msg.id, msg.fileName, msg.fileSize, absoluteAnime)
            db.subtitleDao().setEditDate(channelId, msg.id, msg.editDate)
            return Outcome.INDEXED
        }
        // Free playability gate (moon's 2026-11-06): Telegram's own
        // MIME type says this is not a video — it can never play, so
        // it goes to unmatched instead of the library. No downloads,
        // no slower scans: the mime already arrives with the message.
        if (isNonVideoMime(msg.mimeType)) {
            com.moon.moonstream.util.AppLog.w(
                "MATCH",
                "'${msg.fileName}' mime=${msg.mimeType} is not a video → unmatched",
            )
            db.unmatchedDao().insert(
                UnmatchedFileEntity(
                    channelId = channelId,
                    messageId = msg.id,
                    fileName = msg.fileName,
                    fileSize = msg.fileSize,
                    editDate = msg.editDate,
                    caption = msg.caption,
                ),
            )
            return Outcome.UNMATCHED
        }
        val parsed = FilenameParser.parse(msg.fileName, target.isAnime, msg.caption, absoluteAnime)
        if (parsed.title.isBlank()) {
            db.unmatchedDao().insert(
                UnmatchedFileEntity(
                    channelId = channelId,
                    messageId = msg.id,
                    fileName = msg.fileName,
                    fileSize = msg.fileSize,
                    editDate = msg.editDate,
                    caption = msg.caption,
                ),
            )
            return Outcome.UNMATCHED
        }
        val matched = indexFile(db, target, msg.id, msg.fileName, msg.fileSize, msg.caption, parsed, tmdbKey, omdbKey, metaCache, animeCache, posterStyle, fanartKey, fanartCache)
        if (matched) {
            db.streamDao().setEditDate(channelId, msg.id, msg.editDate)
            return Outcome.INDEXED
        }
        db.unmatchedDao().setEditDate(channelId, msg.id, msg.editDate)
        return Outcome.UNMATCHED
    }

    /**
     * Returns true if the file was confidently matched and indexed;
     * false if it was routed to unmatched (low-confidence match).
     */
    private suspend fun indexFile(
        db: MsDatabase,
        target: ScanTarget,
        messageId: Long,
        fileName: String,
        fileSize: Long,
        caption: String,
        parsed: ParsedMedia,
        tmdbKey: String,
        omdbKey: String,
        metaCache: MutableMap<String, EnrichedMeta?>,
        animeCache: MutableMap<String, Boolean>,
        posterStyle: Int,
        fanartKey: String,
        fanartCache: MutableMap<Int, String?>,
    ): Boolean {
        val norm = normalizeTitle(parsed.title)
        // Manual channel (moon's 2026-11-09): no online lookup at all — not
        // even the Kitsu anime probe. The file is listed as unmatched so it
        // stays visible (playable, linkable by hand) without being filed
        // under whatever film or show its name resembles.
        if (target.manualOnly) {
            db.unmatchedDao().insert(
                UnmatchedFileEntity(
                    channelId = target.channelId,
                    messageId = messageId,
                    fileName = fileName,
                    fileSize = fileSize,
                    caption = caption,
                ),
            )
            return false
        }
        var type = if (target.isAnime) "anime" else parsed.type
        if (!target.isAnime && type != "anime" && norm.isNotBlank()) {
            // Auto-detect anime (moon's 2026-11-07): a title in a
            // non-anime channel that Kitsu knows goes to the Anime tab
            // on its own — series AND anime movies (Kitsu lists both).
            // Cached per normalized title, so it's one Kitsu call per
            // new title, not per file. Only applies to NEW files: the
            // scanner skips already-indexed rows, so anime already in
            // Series stays there until moved with the Detail-screen
            // button (or a from-scratch rescan).
            val detected = animeCache.getOrPut(norm) {
                MetadataMatcher.isAnimeTitle(parsed.title, parsed.episode != null)
            }
            if (detected) {
                com.moon.moonstream.util.AppLog.i(
                    "MATCH", "'${parsed.title}' auto-detected as anime → Anime tab",
                )
                type = "anime"
            }
        }
        val cacheKey = "$norm|$type|${parsed.year ?: ""}|${parsed.imdbId ?: ""}"
        val meta = metaCache.getOrPut(cacheKey) {
            val m = MetadataMatcher.match(
                parsed.title, parsed.year, type, target.isAnime, tmdbKey,
                hasEpisode = parsed.episode != null,
                imdbId = parsed.imdbId,
                omdbKey = omdbKey,
            )
            if (m?.imdbId != null) MetadataMatcher.enrichFromCinemeta(m, type) else m
        }

        // No match OR low-confidence: don't create a wrong entry — send to
        // unmatched so the user can manually link it.
        // (moon 2026-11-01: "if no tmdb results, why index as entry?")
        if (meta == null || meta.confidence < 0.75) {
            com.moon.moonstream.util.AppLog.w(
                "MATCH",
                if (meta == null) "'${parsed.title}' no match → unmatched"
                else "'${parsed.title}' low confidence (${"%.2f".format(meta.confidence)}) → unmatched",
            )
            db.unmatchedDao().insert(
                UnmatchedFileEntity(
                    channelId = target.channelId,
                    messageId = messageId,
                    fileName = fileName,
                    fileSize = fileSize,
                    caption = caption,
                ),
            )
            return false
        }

        val now = System.currentTimeMillis()
        val year = meta?.year ?: parsed.year
        // Canonical grouping key (moon's 2026-11-09 duplicate fix): when the
        // metadata match knows the real title, group on THAT, not on how the
        // uploader spelled the filename. "I Am Game", "IAM.Game.2024" and
        // "[Group] I Am Game 1080p" then all resolve to "i am game" instead
        // of three different normalized keys.
        val canonicalNorm = meta?.title?.let { normalizeTitle(it) }?.takeIf { it.isNotBlank() }
            ?: norm
        // Identity, strongest first: IMDb id, TMDB id, exact title+year+tab,
        // then title+tab ignoring the year. The last one is what stops a
        // re-upload with no year (or a wrong year) from becoming a second
        // entry for a movie already in the library.
        val imdb = meta?.imdbId
        val tmdb = meta?.tmdbId
        val media = (if (!imdb.isNullOrBlank()) db.mediaDao().findByImdb(imdb) else null)
            ?: (if (tmdb != null) db.mediaDao().findByTmdb(tmdb) else null)
            ?: db.mediaDao().findMatchAnyChannel(canonicalNorm, year, type)
            ?: db.mediaDao().findMatchLoose(canonicalNorm, type)
            // Rows indexed before this change kept the parsed spelling
            // ("iam game"), so compare with spaces removed as well, and
            // finally with the parsed key in case the metadata title differs.
            ?: db.mediaDao().findMatchSquashed(canonicalNorm.replace(" ", ""), type)
            ?: db.mediaDao().findMatchSquashed(norm.replace(" ", ""), type)
        val mediaId = if (media == null) {
            // Poster style (moon's 2026-11-06): Fanart.tv is resolved here
            // at match time (needs the TMDB id + key, throttled + cached);
            // BetterPosters is a pure URL rewrite at display time, so the
            // provider poster is stored. Existing entries keep their poster.
            val posterUrl = com.moon.moonstream.util.PosterStyle.resolveForStore(
                meta?.posterUrl, meta?.imdbId, meta?.tmdbId, type,
                posterStyle, fanartKey, fanartCache,
            )
            db.mediaDao().upsert(
                MediaItemEntity(
                    channelId = target.channelId,
                    title = meta?.title ?: parsed.title,
                    normalizedTitle = canonicalNorm,
                    year = year,
                    type = type,
                    imdbId = meta?.imdbId,
                    tmdbId = meta?.tmdbId,
                    kitsuId = meta?.kitsuId,
                    anilistId = meta?.anilistId,
                    // Real tt ID when matched, else a stable synthetic tg ID —
                    // this is what Stremio clients (e.g. Nuvio) key on.
                    stremioId = StremioIds.forNewItem(target.channelId, norm, meta?.imdbId),
                    posterUrl = posterUrl,
                    backdropUrl = meta?.backdropUrl,
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

        // Packs are not episodes (moon's 2026-11-09): "Complete S01",
        // "Season 1", "S01 EP 1-5" describe a batch, so they must not be
        // filed as episode 1 of that season. Dropping BOTH numbers puts the
        // file in the entry's "Other" bucket, where it is still playable and
        // the filename still says what it is. A season number without an
        // episode, or an episode range, is the signal; single episodes keep
        // their slot. This only runs for files whose metadata matched with
        // confidence ≥ 0.75 (the gate above) — low-confidence files never
        // reach an entry at all.
        val isPack = parsed.isPack || parsed.episodeEnd != null ||
            (parsed.season != null && parsed.episode == null)
        if (isPack) {
            com.moon.moonstream.util.AppLog.i(
                "SCAN",
                "pack '$fileName' (S${parsed.season ?: "?"}E${parsed.episode ?: "?"}" +
                    "-${parsed.episodeEnd ?: "?"}) → Other bucket",
            )
        }
        db.streamDao().insert(
            StreamFileEntity(
                mediaId = mediaId,
                channelId = target.channelId,
                messageId = messageId,
                fileName = fileName,
                fileSize = fileSize,
                season = if (isPack) null else parsed.season,
                episode = if (isPack) null else parsed.episode,
                episodeEnd = if (isPack) null else parsed.episodeEnd,
                quality = parsed.qualityBadge,
            ),
        )
        db.mediaDao().refreshAggregates(mediaId, now)
        return true
    }

    /**
     * Indexes one subtitle file. Language comes from the filename tags the
     * parser already extracts ("Mal", "Eng", …); "unknown" when there's no
     * hint. Mapping prefers an exact season+episode file match, then a
     * title match, then a similarity fallback — anything left over stays
     * unlinked (mediaId = null) for manual Attach in Detail.
     */
    private suspend fun indexSubtitle(
        db: MsDatabase,
        target: ScanTarget,
        messageId: Long,
        fileName: String,
        fileSize: Long,
        absoluteAnime: Boolean,
    ) {
        val parsed = FilenameParser.parse(fileName, target.isAnime, "", absoluteAnime)
        val norm = normalizeTitle(parsed.title)
        // Subtitles are the exception in a manual channel (moon's 2026-11-09):
        // subtitle-only channels carry no video, so a well-named SRT should
        // still reach its show from wherever that show was indexed. They are
        // matched in STRICT mode — exact title (and the exact episode when the
        // name says SxxExx), never the title-similarity guess — so a lecture's
        // "Energy.srt" can't be glued to a show called "Energy".
        val mediaId = if (norm.isBlank()) {
            null
        } else {
            resolveSubtitleMedia(
                db, target.channelId, norm, parsed.season, parsed.episode,
                strict = target.manualOnly, year = parsed.year,
            )
        }
        db.subtitleDao().insert(
            SubtitleFileEntity(
                channelId = target.channelId,
                messageId = messageId,
                fileName = fileName,
                fileSize = fileSize,
                mediaId = mediaId,
                season = parsed.season,
                episode = parsed.episode,
                language = parsed.languages.firstOrNull() ?: "unknown",
            ),
        )
    }

    /**
     * Best-effort subtitle → media mapping. Returns the media id or null.
     *
     * [strict] is used for manual channels: exact title only (plus the exact
     * episode when the name carries SxxExx), and when the subtitle's name has
     * a year, an entry with a different known year is rejected. That is the
     * "only correctly named subtitles get attached" rule moon asked for on
     * 2026-11-09; the loose similarity guess stays for normal channels.
     */
    suspend fun resolveSubtitleMedia(
        db: MsDatabase,
        channelId: Long,
        normTitle: String,
        season: Int?,
        episode: Int?,
        strict: Boolean = false,
        year: String? = null,
        /**
         * Pre-loaded library for the similarity fallback. The relink pass
         * calls this in a loop and used to re-query every entry for every
         * unlinked subtitle (quadratic on a big library, slow on low-end
         * phones) — it now loads the list once and passes it in.
         */
        candidates: List<MediaItemEntity>? = null,
    ): Long? {
        // 1. Same title AND the episode already exists in the library.
        if (season != null && episode != null) {
            db.streamDao().findMediaWithEpisodeAnyChannel(normTitle, season, episode)
                ?.let { return it }
        }
        // 2. Exact title match (movies, or series whose episode isn't indexed yet).
        // Cross-channel (moon's 2026-11-06 grouping): the entry may have
        // been created from a different channel than this subtitle.
        db.mediaDao().findByNormAnyChannel(normTitle)?.let { entry ->
            val yearClash = strict && year != null && entry.year != null && entry.year != year
            if (!yearClash) return entry.id
        }
        // Strict mode stops here: no fuzzy guess, the file waits for a manual
        // attach. A near-miss title must never decide where a subtitle goes.
        if (strict) return null
        // 3. Similarity fallback across the whole library (one score per
        // entry — the old version scored the winner twice).
        val all = candidates ?: db.mediaDao().listVisible()
        var bestId: Long? = null
        var bestScore = 0.0
        for (e in all) {
            val score = MetadataMatcher.similarity(normTitle, e.normalizedTitle)
            if (score > bestScore) {
                bestScore = score
                bestId = e.id
            }
        }
        return if (bestScore >= 0.6) bestId else null
    }

    /**
     * Retries mapping for every still-unlinked subtitle in the channel.
     * [strict] = manual channel: exact title/episode only, no fuzzy guess.
     */
    private suspend fun relinkSubtitles(
        db: MsDatabase,
        channelId: Long,
        isAnime: Boolean,
        absoluteAnime: Boolean,
        strict: Boolean = false,
    ) {
        val pending = db.subtitleDao().unlinkedByChannel(channelId)
        if (pending.isEmpty()) return
        // Loaded once for the whole pass (strict mode never needs it).
        val candidates = if (strict) emptyList() else db.mediaDao().listVisible()
        for (s in pending) {
            val parsed = FilenameParser.parse(s.fileName, isAnime, "", absoluteAnime)
            val norm = normalizeTitle(parsed.title)
            if (norm.isBlank()) continue
            val mediaId = resolveSubtitleMedia(
                db, channelId, norm, parsed.season, parsed.episode,
                strict = strict, year = parsed.year, candidates = candidates,
            )
            if (mediaId != null) {
                db.subtitleDao().attach(s.id, mediaId, parsed.season, parsed.episode)
            }
        }
    }

    /**
     * Merges entries the old channel-scoped dedup split: same
     * (normalizedTitle, year, type) across channels, or twice in one
     * channel — plus the looser IMDb-id / spacing / year-less cases above.
     * Keeps the richest entry (IMDb, then poster, then most files, then
     * oldest), re-points the dupes' files + subtitles onto it and deletes
     * the dupes. Returns how many duplicate entries were merged. Idempotent.
     */
    suspend fun mergeDuplicateTitles(db: MsDatabase): Int {
        val all = db.mediaDao().getAll()
        if (all.size < 2) return 0
        // Groups of entries that are really the same title.
        //
        // The old key was (normalizedTitle, year, type) exactly — so one
        // slightly different filename ("IAM.Game.2024" vs "I Am Game") or a
        // file with no year at all created another entry, and a movie could
        // pile up as four (moon's 2026-11-09: "I Am Game"). Now:
        //  1. same IMDb id → same entry, whatever the title/tab/year says;
        //  2. same title with spaces/punctuation ignored + same tab → same
        //     entry, and a missing year folds into the year its siblings
        //     agree on. Genuine remakes (two DIFFERENT known years) stay
        //     separate, because only the year-less rows are pulled in.
        val groups = mutableListOf<List<MediaItemEntity>>()
        val taken = mutableSetOf<Long>()
        fun addGroup(candidates: List<MediaItemEntity>) {
            val fresh = candidates.filter { it.id !in taken }
            if (fresh.size > 1) {
                groups += fresh
                fresh.forEach { taken += it.id }
            }
        }
        all.filter { !it.imdbId.isNullOrBlank() }
            .groupBy { it.imdbId!!.lowercase() }
            .filter { it.value.size > 1 }
            .forEach { (_, g) -> addGroup(g) }

        val squashed = { s: String -> s.lowercase().filter { it.isLetterOrDigit() } }
        all.filterNot { it.id in taken }
            .groupBy { squashed(it.normalizedTitle) + "|" + it.type }
            .filter { it.value.size > 1 }
            .forEach { (_, g) ->
                val byYear = g.groupBy { it.year }
                val yearless = byYear[null].orEmpty()
                val years = byYear.filterKeys { it != null }
                when (years.size) {
                    0 -> addGroup(g)
                    1 -> addGroup(g)
                    else -> {
                        years.values.filter { it.size > 1 }.forEach { addGroup(it) }
                        val biggest = years.maxByOrNull { it.value.size }!!.value
                        addGroup(biggest + yearless)
                    }
                }
            }

        var merged = 0
        val now = System.currentTimeMillis()
        for (items in groups) {
            val kept = items.sortedWith(
                compareByDescending<MediaItemEntity> { !it.imdbId.isNullOrBlank() }
                    .thenByDescending { !it.posterUrl.isNullOrBlank() }
                    .thenByDescending { it.fileCount }
                    .thenBy { it.id },
            ).first()
            for (dupe in items.filter { it.id != kept.id }) {
                db.streamDao().moveAllToMedia(dupe.id, kept.id)
                db.subtitleDao().moveAllToMedia(dupe.id, kept.id)
                // Resume spots move too (moon's 2026-11-06): without this
                // the kept entry has no resume spot and the old one opens
                // a "deleted" entry from Continue Watching.
                db.watchProgressDao().moveAllToMedia(dupe.id, kept.id)
                db.mediaDao().deleteById(dupe.id)
                merged++
                Log.i(TAG, "merged '${dupe.title}' (${dupe.year}) into entry ${kept.id}")
            }
            db.mediaDao().refreshAggregates(kept.id, now)
        }
        // Heal resume spots orphaned by merges that ran before the move
        // existed (moon's Reacher S4E1, 2026-11-06): re-point them to the
        // entry that now owns their video file.
        val repaired = db.watchProgressDao().repairOrphanedProgress()
        if (repaired > 0) {
            Log.i(TAG, "re-pointed $repaired orphaned resume spot(s) to their surviving entries")
        }
        return merged
    }

    /**
     * One pass over stream rows that still lack season/episode: re-parse
     * the filename and fill what the parser finds. Idempotent — after the
     * first run only unparseable names remain NULL, so later scans are a
     * single cheap query. Episode counts are refreshed for touched entries.
     */
    private suspend fun backfillMissingSeasonEpisode(
        db: MsDatabase,
        channelId: Long,
        isAnime: Boolean,
        absoluteAnime: Boolean,
    ) {
        val missing = db.streamDao().listMissingSeasonEpisode(channelId)
        if (missing.isEmpty()) return
        val touched = mutableSetOf<Long>()
        var filled = 0
        for (f in missing) {
            val parsed = FilenameParser.parse(f.fileName, isAnime, "", absoluteAnime)
            if (parsed.season == null && parsed.episode == null) continue
            // Season packs / episode ranges stay in the Other bucket — do
            // not give them an episode slot (moon's 2026-11-09).
            if (parsed.isPack || parsed.episodeEnd != null ||
                (parsed.season != null && parsed.episode == null)
            ) {
                continue
            }
            db.streamDao().updateSeasonEpisode(f.id, parsed.season, parsed.episode)
            touched.add(f.mediaId)
            filled++
        }
        if (filled > 0) {
            val now = System.currentTimeMillis()
            for (mediaId in touched) db.mediaDao().refreshAggregates(mediaId, now)
            Log.i(TAG, "ch=$channelId backfilled S/E for $filled files")
        }
    }
}
