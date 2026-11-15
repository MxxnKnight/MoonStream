package com.moon.moonstream.index.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    /** Update IMDb ID for an existing entry (moon's 2026-11-05: manual link should save IDs). */
    @Query("UPDATE media_items SET imdbId = :imdbId WHERE id = :mediaId")
    suspend fun updateImdbId(mediaId: Long, imdbId: String)

    /** Save a fetched landscape backdrop (detail header). */
    @Query("UPDATE media_items SET backdropUrl = :url WHERE id = :mediaId")
    suspend fun updateBackdropUrl(mediaId: Long, url: String)

    /** Move one entry between Movie / Series / Anime (moon's 2026-11-07:
     *  anime in a non-anime channel lands in Series — let him move it
     *  to the Anime tab himself). Just the type — title, poster and
     *  files stay exactly as they are. No schema change, so no migration. */
    @Query("UPDATE media_items SET type = :type WHERE id = :mediaId")
    suspend fun updateType(mediaId: Long, type: String)

    /** Delete entries with no streams AND no subtitles (moon's request 2026-11-04:
     *  "really we don't need a entry without any streams right!").
     *  Returns the number of deleted entries. */
    @Query(
        """DELETE FROM media_items
           WHERE id NOT IN (SELECT DISTINCT mediaId FROM stream_files WHERE mediaId IS NOT NULL)
           AND id NOT IN (SELECT DISTINCT mediaId FROM subtitle_files WHERE mediaId IS NOT NULL)"""
    )
    suspend fun deleteEmpty(): Int

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM media_items WHERE type = :type")
    suspend fun countByType(type: String): Int

    /** Titles with a genre recorded (stats coverage caption). A genres
     *  string counts when it holds at least one non-blank segment —
     *  TRIM strips the separators, so "," alone doesn't count. */
    @Query("SELECT COUNT(*) FROM media_items WHERE genres IS NOT NULL AND TRIM(genres, ' ,') != ''")
    suspend fun countWithGenres(): Int

    /** Titles with an original language recorded (stats coverage). */
    @Query("SELECT COUNT(*) FROM media_items WHERE originalLanguage IS NOT NULL AND TRIM(originalLanguage) != ''")
    suspend fun countWithLanguage(): Int

    /** Titles with a country recorded (stats coverage). */
    @Query("SELECT COUNT(*) FROM media_items WHERE country IS NOT NULL AND TRIM(country) != ''")
    suspend fun countWithCountry(): Int

    /** Rows still missing their Stremio ID — the startup backfill used
     *  to load the whole library to find these (moon's 2026-11-15).
     *  After the first backfill this returns almost nothing. */
    @Query("SELECT * FROM media_items WHERE stremioId IS NULL OR stremioId = ''")
    suspend fun getMissingStremioId(): List<MediaItemEntity>

    @Query("SELECT COALESCE(SUM(episodeCount), 0) FROM media_items WHERE type IN ('series', 'anime')")
    suspend fun episodeTotal(): Int

    @Query("SELECT * FROM media_items WHERE channelId = :channelId ORDER BY title COLLATE NOCASE")
    fun observeByChannel(channelId: Long): Flow<List<MediaItemEntity>>

    /** Cross-channel match (moon's 2026-11-06: one entry per title + year
     *  + type across ALL channels — the same movie posted in two channels,
     *  or twice in one channel months apart, must land in one entry). */
    @Query(
        """SELECT * FROM media_items
           WHERE normalizedTitle = :norm
           AND type = :type AND (year = :year OR (year IS NULL AND :year IS NULL))
           LIMIT 1""",
    )
    suspend fun findMatchAnyChannel(
        norm: String,
        year: String?,
        type: String,
    ): MediaItemEntity?

    /** Exact TMDB match (moon's 2026-11-09 duplicate fix): a file whose
     *  metadata resolved to the same TMDB entry belongs to that entry no
     *  matter how its filename spelled the title. */
    @Query("SELECT * FROM media_items WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun findByTmdb(tmdbId: Int): MediaItemEntity?

    /**
     * Year-less duplicate guard (moon's 2026-11-09: one movie landed as
     * FOUR entries). Same canonical title + tab is the same show/movie even
     * when one file carried no year and another carried a wrong one. Keeps
     * remakes apart only when BOTH years are known and different, which the
     * strict query above already handles.
     */
    @Query(
        """SELECT * FROM media_items
           WHERE normalizedTitle = :norm AND type = :type
           ORDER BY fileCount DESC, id ASC LIMIT 1""",
    )
    suspend fun findMatchLoose(norm: String, type: String): MediaItemEntity?

    /**
     * Spacing-blind duplicate guard: "iam game" and "i am game" are the same
     * title. normalizedTitle already has every non-alphanumeric turned into a
     * space, so stripping spaces makes the two equal. Rows created before the
     * canonical-key change are matched here too (moon's 2026-11-09).
     */
    @Query(
        """SELECT * FROM media_items
           WHERE type = :type AND REPLACE(normalizedTitle, ' ', '') = :squashed
           ORDER BY fileCount DESC, id ASC LIMIT 1""",
    )
    suspend fun findMatchSquashed(squashed: String, type: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE channelId = :channelId AND normalizedTitle = :norm LIMIT 1")
    suspend fun findByTitle(channelId: Long, norm: String): MediaItemEntity?

    /** Any-channel lookup for the "In library" badge (moon's request 2026-11-01). */
    @Query("SELECT * FROM media_items WHERE normalizedTitle = :norm LIMIT 1")
    suspend fun findByNormAnyChannel(norm: String): MediaItemEntity?

    /** Substring title search for the LINK sheet (moon's 2026-11-14): finds
     *  local entries — including custom ones no provider knows — by normalized
     *  title. Capped at 10 so the sheet never loads the whole library. */
    @Query("SELECT * FROM media_items WHERE normalizedTitle LIKE '%' || :norm || '%' ORDER BY updatedAt DESC LIMIT 10")
    suspend fun searchByNorm(norm: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE hidden = 0 ORDER BY updatedAt DESC")
    suspend fun listVisible(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE imdbId = :imdbId LIMIT 1")
    suspend fun findByImdb(imdbId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE stremioId = :stremioId LIMIT 1")
    suspend fun findByStremioId(stremioId: String): MediaItemEntity?

    @Query("SELECT title FROM media_items ORDER BY updatedAt DESC LIMIT 5")
    suspend fun latestTitles(): List<String>

    @Query("UPDATE media_items SET stremioId = :stremioId WHERE id = :id")
    suspend fun setStremioId(id: Long, stremioId: String)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Drops an entry only if nothing points at it any more — streams AND
     * subtitles. (The audits used to call deleteById(), which happily left
     * subtitle rows pointing at an entry that no longer existed.)
     */
    @Query(
        "DELETE FROM media_items WHERE id = :id " +
            "AND NOT EXISTS(SELECT 1 FROM stream_files WHERE mediaId = :id) " +
            "AND NOT EXISTS(SELECT 1 FROM subtitle_files WHERE mediaId = :id)",
    )
    suspend fun deleteIfEmpty(id: Long)

    @Query("UPDATE media_items SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query(
        """UPDATE media_items SET
               title = :title, normalizedTitle = :norm, year = :year,
               imdbId = :imdbId, tmdbId = :tmdbId, posterUrl = :posterUrl,
               backdropUrl = :backdropUrl,
               overview = :overview, stremioId = :stremioId, type = :type,
               genres = :genres, originalLanguage = :originalLanguage,
               country = :country, updatedAt = :now
           WHERE id = :id""",
    )
    suspend fun updateMetadata(
        id: Long,
        title: String,
        norm: String,
        year: String?,
        imdbId: String?,
        tmdbId: Int?,
        posterUrl: String?,
        backdropUrl: String?,
        overview: String?,
        stremioId: String?,
        type: String,
        genres: String?,
        originalLanguage: String?,
        country: String?,
        now: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity): Long

    /** Recomputes size / file / episode aggregates from the attached files. */
    @Query(
        """UPDATE media_items SET
               totalSize = (SELECT COALESCE(SUM(fileSize), 0) FROM stream_files WHERE mediaId = :mediaId),
               fileCount = (SELECT COUNT(*) FROM stream_files WHERE mediaId = :mediaId),
               episodeCount = (SELECT COUNT(DISTINCT season || 'x' || episode) FROM stream_files
                               WHERE mediaId = :mediaId AND episode IS NOT NULL),
               updatedAt = :now
           WHERE id = :mediaId""",
    )
    suspend fun refreshAggregates(mediaId: Long, now: Long)

    @Query("DELETE FROM media_items WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)

    @Query("SELECT COUNT(*) FROM stream_files WHERE channelId = :channelId")
    suspend fun fileCountByChannel(channelId: Long): Int

    @Query(
        """SELECT
               (SELECT COUNT(*) FROM media_items WHERE channelId = :channelId AND type = 'movie') AS movies,
               (SELECT COUNT(*) FROM media_items WHERE channelId = :channelId AND type IN ('series', 'anime')) AS shows,
               (SELECT COALESCE(SUM(episodeCount), 0) FROM media_items WHERE channelId = :channelId AND type IN ('series', 'anime')) AS episodes""",
    )
    suspend fun breakdownByChannel(channelId: Long): ChannelBreakdown
}

@Dao
interface StreamDao {
    @Query("SELECT EXISTS(SELECT 1 FROM stream_files WHERE channelId = :channelId AND messageId = :messageId)")
    suspend fun exists(channelId: Long, messageId: Long): Boolean

    /**
     * Edit stamp this message was indexed with, wherever it landed (stream,
     * subtitle or unmatched). Null = the message isn't indexed at all.
     * One query instead of three per message in the edit look-back.
     */
    @Query(
        "SELECT editDate FROM stream_files WHERE channelId = :channelId AND messageId = :messageId " +
            "UNION ALL SELECT editDate FROM subtitle_files WHERE channelId = :channelId AND messageId = :messageId " +
            "UNION ALL SELECT editDate FROM unmatched_files WHERE channelId = :channelId AND messageId = :messageId",
    )
    suspend fun storedEditDate(channelId: Long, messageId: Long): Long?

    @Query("UPDATE stream_files SET editDate = :editDate WHERE channelId = :channelId AND messageId = :messageId")
    suspend fun setEditDate(channelId: Long, messageId: Long, editDate: Long)

    @Query("DELETE FROM stream_files WHERE channelId = :channelId AND messageId = :messageId")
    suspend fun deleteByMessage(channelId: Long, messageId: Long)

    @Query("SELECT * FROM stream_files WHERE channelId = :channelId AND messageId = :messageId LIMIT 1")
    suspend fun findByMessage(channelId: Long, messageId: Long): StreamFileEntity?

    @Query("SELECT * FROM stream_files")
    suspend fun getAll(): List<StreamFileEntity>

    @Query("SELECT COUNT(*) FROM stream_files")
    suspend fun countAll(): Int

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM stream_files")
    suspend fun totalSize(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(file: StreamFileEntity): Long

    @Query("SELECT * FROM stream_files WHERE mediaId = :mediaId ORDER BY season, episode, id")
    fun observeByMedia(mediaId: Long): Flow<List<StreamFileEntity>>

    @Query("SELECT * FROM stream_files WHERE mediaId = :mediaId ORDER BY season, episode, id")
    suspend fun listByMedia(mediaId: Long): List<StreamFileEntity>

    @Query("SELECT * FROM stream_files WHERE id = :id")
    suspend fun getById(id: Long): StreamFileEntity?

    @Query("DELETE FROM stream_files WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)

    @Query("DELETE FROM stream_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE stream_files SET mediaId = :newMediaId WHERE mediaId = :oldMediaId")
    suspend fun moveAllToMedia(oldMediaId: Long, newMediaId: Long)

    @Query("UPDATE stream_files SET mediaId = :newMediaId WHERE id = :fileId")
    suspend fun moveToMedia(fileId: Long, newMediaId: Long)

    @Query("UPDATE stream_files SET season = :season, episode = :episode WHERE id = :fileId")
    suspend fun updateSeasonEpisode(fileId: Long, season: Int?, episode: Int?)

    /** Entries holding a season pack / episode range (moon's 2026-11-09). */
    @Query(
        """SELECT DISTINCT mediaId FROM stream_files
           WHERE episodeEnd IS NOT NULL OR (season IS NOT NULL AND episode IS NULL)""",
    )
    suspend fun mediaIdsWithPacks(): List<Long>

    /**
     * Moves already-indexed season packs out of their fake episode slot and
     * into the "Other" bucket (both numbers NULL). Returns rows changed;
     * idempotent, so a second run touches nothing.
     */
    @Query(
        """UPDATE stream_files SET season = NULL, episode = NULL, episodeEnd = NULL
           WHERE episodeEnd IS NOT NULL OR (season IS NOT NULL AND episode IS NULL)""",
    )
    suspend fun parkPacks(): Int

    /** Stream files of series/anime entries still missing season/episode
     *  (moon's 2026-11-05 Reacher case: a row can sit in the DB with NULL S/E
     *  while its filename clearly has S04E01, and re-scans skip existing rows
     *  so it never gets repaired). */
    @Query(
        """SELECT s.* FROM stream_files s JOIN media_items m ON m.id = s.mediaId
           WHERE s.channelId = :channelId AND s.season IS NULL AND s.episode IS NULL
           AND m.type IN ('series', 'anime')""",
    )
    suspend fun listMissingSeasonEpisode(channelId: Long): List<StreamFileEntity>

    /** Media id of the entry in this channel whose title matches AND that
     *  already has a file for the given season/episode — the strongest
     *  subtitle-mapping signal. */
    @Query(
        """SELECT m.id FROM media_items m JOIN stream_files s ON s.mediaId = m.id
           WHERE m.channelId = :channelId AND m.normalizedTitle = :norm
           AND s.season = :season AND s.episode = :episode LIMIT 1""",
    )
    suspend fun findMediaWithEpisode(
        channelId: Long,
        norm: String,
        season: Int,
        episode: Int,
    ): Long?

    /** Any-channel version (moon's 2026-11-06 grouping: entries are shared
     *  across channels, so a subtitle in channel B must find the entry
     *  created from channel A). */
    @Query(
        """SELECT m.id FROM media_items m JOIN stream_files s ON s.mediaId = m.id
           WHERE m.normalizedTitle = :norm
           AND s.season = :season AND s.episode = :episode LIMIT 1""",
    )
    suspend fun findMediaWithEpisodeAnyChannel(
        norm: String,
        season: Int,
        episode: Int,
    ): Long?

    /** Media ids having at least one file whose quality label contains [q]. */
    @Query("SELECT DISTINCT mediaId FROM stream_files WHERE quality LIKE '%' || :q || '%'")
    suspend fun mediaIdsWithQuality(q: String): List<Long>
}

@Dao
interface SubtitleDao {
    @Query("SELECT EXISTS(SELECT 1 FROM subtitle_files WHERE channelId = :channelId AND messageId = :messageId)")
    suspend fun exists(channelId: Long, messageId: Long): Boolean

    @Query("UPDATE subtitle_files SET editDate = :editDate WHERE channelId = :channelId AND messageId = :messageId")
    suspend fun setEditDate(channelId: Long, messageId: Long, editDate: Long)

    @Query("DELETE FROM subtitle_files WHERE channelId = :channelId AND messageId = :messageId")
    suspend fun deleteByMessage(channelId: Long, messageId: Long)

    @Query("SELECT * FROM subtitle_files")
    suspend fun getAll(): List<SubtitleFileEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(s: SubtitleFileEntity): Long

    @Query("SELECT * FROM subtitle_files WHERE mediaId = :mediaId ORDER BY season, episode, id")
    fun observeByMedia(mediaId: Long): Flow<List<SubtitleFileEntity>>

    @Query("SELECT * FROM subtitle_files WHERE mediaId = :mediaId ORDER BY season, episode, id")
    suspend fun listByMedia(mediaId: Long): List<SubtitleFileEntity>

    @Query("SELECT * FROM subtitle_files WHERE id = :id")
    suspend fun getById(id: Long): SubtitleFileEntity?

    @Query("SELECT * FROM subtitle_files WHERE channelId = :channelId AND mediaId IS NULL ORDER BY id DESC")
    fun observeUnlinkedByChannel(channelId: Long): Flow<List<SubtitleFileEntity>>

    @Query("SELECT * FROM subtitle_files WHERE channelId = :channelId AND mediaId IS NULL")
    suspend fun unlinkedByChannel(channelId: Long): List<SubtitleFileEntity>

    @Query("UPDATE subtitle_files SET mediaId = :mediaId, season = :season, episode = :episode WHERE id = :id")
    suspend fun attach(id: Long, mediaId: Long, season: Int?, episode: Int?)

    @Query("UPDATE subtitle_files SET mediaId = :newMediaId WHERE mediaId = :oldMediaId")
    suspend fun moveAllToMedia(oldMediaId: Long, newMediaId: Long)

    @Query("DELETE FROM subtitle_files WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)

    @Query("DELETE FROM subtitle_files WHERE id = :id")
    suspend fun delete(id: Long)

    /** Media ids having at least one linked subtitle. */
    @Query("SELECT DISTINCT mediaId FROM subtitle_files WHERE mediaId IS NOT NULL")
    suspend fun mediaIdsWithSubtitles(): List<Long>

    /** Media ids having at least one linked subtitle in the given language ("Mal", "Eng", ...). */
    @Query("SELECT DISTINCT mediaId FROM subtitle_files WHERE mediaId IS NOT NULL AND language = :lang")
    suspend fun mediaIdsWithSubtitleLang(lang: String): List<Long>
}

@Dao
interface ScanCursorDao {
    @Query("SELECT * FROM scan_cursors WHERE channelId = :channelId")
    suspend fun get(channelId: Long): ScanCursorEntity?

    @Query("SELECT * FROM scan_cursors")
    suspend fun all(): List<ScanCursorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(cursor: ScanCursorEntity)

    @Query("DELETE FROM scan_cursors WHERE channelId = :channelId")
    suspend fun clear(channelId: Long)
}

@Dao
interface UnmatchedDao {
    @Query("SELECT * FROM unmatched_files ORDER BY id DESC")
    fun observeAll(): Flow<List<UnmatchedFileEntity>>

    @Query("SELECT * FROM unmatched_files")
    suspend fun getAll(): List<UnmatchedFileEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM unmatched_files WHERE channelId = :channelId AND messageId = :messageId)")
    suspend fun exists(channelId: Long, messageId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(u: UnmatchedFileEntity)

    @Query("DELETE FROM unmatched_files WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM unmatched_files WHERE channelId = :channelId AND messageId = :messageId")
    suspend fun deleteByMessage(channelId: Long, messageId: Long)

    @Query("UPDATE unmatched_files SET editDate = :editDate WHERE channelId = :channelId AND messageId = :messageId")
    suspend fun setEditDate(channelId: Long, messageId: Long, editDate: Long)

    @Query("DELETE FROM unmatched_files WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)
}

@Dao
interface ChannelConfigDao {
    @Query("SELECT * FROM channel_config")
    suspend fun all(): List<ChannelConfigEntity>

    @Query("SELECT * FROM channel_config WHERE channelId = :channelId")
    suspend fun get(channelId: Long): ChannelConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ChannelConfigEntity)

    @Query("UPDATE channel_config SET lastSyncedAt = :at WHERE channelId = :channelId")
    suspend fun setLastSynced(channelId: Long, at: Long)
}

/** One row of a stats aggregate: a local date ("yyyy-MM-dd") + ms watched. */
data class StatsDayRow(val day: String, val ms: Long)

/** Weekday bucket: 0 = Sunday .. 6 = Saturday (SQLite strftime '%w'). */
data class StatsWeekdayRow(val weekday: Int, val ms: Long)

/** Month bucket: "yyyy-MM" + ms watched. */
data class StatsMonthRow(val month: String, val ms: Long)

/** Hour-of-day bucket: 0-23 (local time) + ms watched. */
data class StatsHourRow(val hour: Int, val ms: Long)

/** Hours watched per video quality, from stream_files.quality. */
data class StatsQualityRow(val quality: String, val ms: Long)

/** One watched title's stats metadata + its hours in range — the genre,
 *  language, country and decade breakdowns all read this one list. */
data class StatsTitleMetaRow(
    val genres: String?,
    val originalLanguage: String?,
    val country: String?,
    val year: String?,
    val hoursMs: Long,
)

/** Top-watched title: media info joined from media_items. */
data class StatsTopRow(
    val mediaId: Long,
    val title: String,
    val posterUrl: String?,
    val hoursMs: Long,
    val episodes: Int,
    val type: String?,
)

/** Recently finished title: latest finish stamp + media info. */
data class StatsFinishedRow(
    val mediaId: Long,
    val title: String,
    val posterUrl: String?,
    val finishedAtMs: Long,
)

/** By-type slice: display type ("movie"/"series"/"anime"/"drama") + count. */
data class StatsTypeRow(val type: String, val count: Int)

@Dao
interface WatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchProgressEntity)

    /** All resume rows — for the library backup (moon's 2026-11-06). */
    @Query("SELECT * FROM watch_progress")
    suspend fun getAll(): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 15): List<WatchProgressEntity>

    /**
     * Live data for the Home "Continue watching" strip (fix 2026-11-05).
     *
     * A Flow, not a one-shot read: the old version reloaded only on the
     * *Activity's* ON_RESUME, which never fires here because screens are
     * swapped inside a single Activity — the strip could stay stale or empty
     * after watching. Room now pushes every position save to the UI.
     *
     * Joined to media_items so a re-index can't leave a blank poster/title.
     */
    @Query(
        """SELECT wp.mediaId AS mediaId, wp.streamFileId AS streamFileId,
                  COALESCE(m.title, wp.title) AS title,
                  wp.episodeLabel AS episodeLabel,
                  COALESCE(m.posterUrl, wp.posterUrl) AS posterUrl,
                  m.imdbId AS imdbId,
                  wp.playedAt AS playedAt, wp.positionMs AS positionMs,
                  wp.durationMs AS durationMs
           FROM watch_progress wp
           LEFT JOIN media_items m ON m.id = wp.mediaId
           ORDER BY wp.playedAt DESC"""
    )
    fun observeContinueWatching(): Flow<List<ContinueWatchingItem>>

    @Query("SELECT * FROM watch_progress WHERE mediaId = :mediaId")
    fun observeByMedia(mediaId: Long): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE mediaId = :mediaId AND streamFileId = :streamFileId ORDER BY playedAt DESC LIMIT 1")
    suspend fun get(mediaId: Long, streamFileId: Long): WatchProgressEntity?

    /** Most recently watched file of an entry — for the header Play button
     *  (continue watching, moon's pick 2026-11-02). */
    @Query("SELECT * FROM watch_progress WHERE mediaId = :mediaId ORDER BY playedAt DESC LIMIT 1")
    suspend fun latestForMedia(mediaId: Long): WatchProgressEntity?

    @Query("UPDATE watch_progress SET positionMs = :positionMs, durationMs = :durationMs, playedAt = :playedAt WHERE streamFileId = :streamFileId")
    suspend fun updatePosition(streamFileId: Long, positionMs: Long, durationMs: Long, playedAt: Long): Int

    /** Mark a file watched to the end (player close at >=90%). Separate
     *  from updatePosition, which clears the resume position to 0 on a
     *  finish — this is the record the stats read. */
    @Query("UPDATE watch_progress SET finishedAt = :finishedAt WHERE streamFileId = :streamFileId")
    suspend fun markFinished(streamFileId: Long, finishedAt: Long): Int

    /**
     * "This file is being opened" — refresh the row's metadata but KEEP the
     * saved position (resume fix 2026-11-09).
     *
     * Detail used to `deleteByStreamFile()` + `upsert()` a fresh row here, which
     * zeroed positionMs. That ran at the same moment the player read the saved
     * spot, so every play from Detail started at 0:00 and the Resume button
     * never appeared. mediaId is also re-pointed so a merged entry heals itself.
     */
    @Query(
        "UPDATE watch_progress SET mediaId = :mediaId, title = :title, " +
            "episodeLabel = :episodeLabel, posterUrl = :posterUrl, playedAt = :playedAt " +
            "WHERE streamFileId = :streamFileId"
    )
    suspend fun touchOpened(
        streamFileId: Long, mediaId: Long, title: String,
        episodeLabel: String?, posterUrl: String?, playedAt: Long,
    ): Int

    @Query("DELETE FROM watch_progress WHERE streamFileId = :streamFileId")
    suspend fun deleteByStreamFile(streamFileId: Long)

    @Query("DELETE FROM watch_progress WHERE mediaId = :mediaId")
    suspend fun deleteByMedia(mediaId: Long)

    /**
     * Merge helper (moon's 2026-11-06): when duplicate entries merge, the
     * resume spots move to the kept entry with the video files — otherwise
     * the progress is orphaned and tapping it opens a "deleted" entry.
     * No unique index on this table, so the move can never clash.
     */
    @Query("UPDATE watch_progress SET mediaId = :newMediaId WHERE mediaId = :oldMediaId")
    suspend fun moveAllToMedia(oldMediaId: Long, newMediaId: Long): Int

    /**
     * Heal resume spots orphaned by a merge that ran before the move
     * existed (moon's Reacher, 2026-11-06): re-point any row whose entry
     * is gone to the entry that now owns its video file. Rows whose file
     * is gone too are left alone.
     */
    @Query(
        """UPDATE watch_progress SET mediaId = (
            SELECT mediaId FROM stream_files WHERE stream_files.id = watch_progress.streamFileId
        ) WHERE mediaId NOT IN (SELECT id FROM media_items)
          AND streamFileId IN (SELECT id FROM stream_files)"""
    )
    suspend fun repairOrphanedProgress(): Int

    @Query("DELETE FROM watch_progress")
    suspend fun clearAll()

    // ---- Stats aggregates (moon's 2026-11-15) ----
    // The stats screen used to load every watch row, every media row and
    // every stream file, then count and add them up in Kotlin — a
    // full-table read x3 on a ~20k-title library, every time Stats
    // opened (twice for the server card). These queries return only the
    // finished numbers (or one small row per watched title), so the big
    // tables are never loaded.
    //
    // Day math uses SQLite's 'localtime' so a "day" is the phone's
    // midnight-to-midnight, matching the old dayStart() grouping.

    /** Hours watched (sum of positionMs) at/after :since; null = all time. */
    @Query("SELECT COALESCE(SUM(positionMs), 0) FROM watch_progress WHERE :since IS NULL OR playedAt >= :since")
    suspend fun sumPositionSince(since: Long?): Long

    /** Hours watched between two cutoffs (the hero's previous window). */
    @Query("SELECT COALESCE(SUM(positionMs), 0) FROM watch_progress WHERE playedAt >= :from AND playedAt < :to")
    suspend fun sumPositionBetween(from: Long, to: Long): Long

    /** Files watched past 10s in range (episodes/files watched). */
    @Query("SELECT COUNT(*) FROM watch_progress WHERE positionMs > 10000 AND (:since IS NULL OR playedAt >= :since)")
    suspend fun countEpisodesSince(since: Long?): Int

    /** Distinct titles played in range (started titles / coverage). */
    @Query("SELECT COUNT(DISTINCT mediaId) FROM watch_progress WHERE :since IS NULL OR playedAt >= :since")
    suspend fun countDistinctMediaSince(since: Long?): Int

    /** Longest single file watched in range (position reached). */
    @Query("SELECT COALESCE(MAX(positionMs), 0) FROM watch_progress WHERE :since IS NULL OR playedAt >= :since")
    suspend fun maxPositionSince(since: Long?): Long

    /** Finished titles: distinct mediaIds with a finish in range
     *  (null since = all time). A title counts when ANY of its rows
     *  finished in the range. */
    @Query("SELECT COUNT(DISTINCT mediaId) FROM watch_progress WHERE finishedAt IS NOT NULL AND (:since IS NULL OR finishedAt >= :since)")
    suspend fun countFinishedSince(since: Long?): Int

    /** Distinct movie titles played in range. */
    @Query("SELECT COUNT(DISTINCT w.mediaId) FROM watch_progress w JOIN media_items m ON m.id = w.mediaId WHERE m.type = 'movie' AND (:since IS NULL OR w.playedAt >= :since)")
    suspend fun countMoviesWatchedSince(since: Long?): Int

    /** Hours per local day in range ("yyyy-MM-dd" keys). */
    @Query("SELECT date(playedAt / 1000, 'unixepoch', 'localtime') AS day, SUM(positionMs) AS ms FROM watch_progress WHERE :since IS NULL OR playedAt >= :since GROUP BY day")
    suspend fun sumByDaySince(since: Long?): List<StatsDayRow>

    /** Distinct local play days, all time — for streaks. */
    @Query("SELECT DISTINCT date(playedAt / 1000, 'unixepoch', 'localtime') AS day FROM watch_progress")
    suspend fun distinctDays(): List<String>

    /** Hours per weekday in range (0 = Sunday .. 6 = Saturday). */
    @Query("SELECT CAST(strftime('%w', playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday, SUM(positionMs) AS ms FROM watch_progress WHERE :since IS NULL OR playedAt >= :since GROUP BY weekday")
    suspend fun sumByWeekdaySince(since: Long?): List<StatsWeekdayRow>

    /** Hours per month in range ("yyyy-MM" keys). */
    @Query("SELECT strftime('%Y-%m', playedAt / 1000, 'unixepoch', 'localtime') AS month, SUM(positionMs) AS ms FROM watch_progress WHERE :since IS NULL OR playedAt >= :since GROUP BY month")
    suspend fun sumByMonthSince(since: Long?): List<StatsMonthRow>

    /** Hours per hour of day in range (0-23, local time). */
    @Query("SELECT CAST(strftime('%H', playedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, SUM(positionMs) AS ms FROM watch_progress WHERE :since IS NULL OR playedAt >= :since GROUP BY hour")
    suspend fun sumByHourSince(since: Long?): List<StatsHourRow>

    /** Top 5 titles by hours in range, media info joined. The title
     *  falls back to the watch row's own title when the media row is
     *  gone (orphan watch row). */
    @Query(
        """SELECT w.mediaId AS mediaId, COALESCE(m.title, MAX(w.title)) AS title,
                  m.posterUrl AS posterUrl, SUM(w.positionMs) AS hoursMs,
                  COUNT(*) AS episodes, m.type AS type
           FROM watch_progress w LEFT JOIN media_items m ON m.id = w.mediaId
           WHERE :since IS NULL OR w.playedAt >= :since
           GROUP BY w.mediaId ORDER BY hoursMs DESC LIMIT 5"""
    )
    suspend fun topWatchedSince(since: Long?): List<StatsTopRow>

    /** By-type slices: distinct titles played in range, grouped by
     *  display type — series in a drama language count as dramas.
     *  Orphan watch rows (no media row) are dropped, as before. */
    @Query(
        """SELECT CASE WHEN m.type = 'movie' THEN 'movie'
                      WHEN m.type = 'anime' THEN 'anime'
                      WHEN m.type = 'series' AND m.originalLanguage IN ('ko', 'zh', 'ja', 'th') THEN 'drama'
                      ELSE 'series' END AS type, COUNT(*) AS count
           FROM (SELECT DISTINCT mediaId FROM watch_progress
                 WHERE :since IS NULL OR playedAt >= :since) w
           JOIN media_items m ON m.id = w.mediaId
           GROUP BY type ORDER BY count DESC"""
    )
    suspend fun countByTypeSince(since: Long?): List<StatsTypeRow>

    /** Recently finished titles: latest finish stamp per title, newest
     *  first, top 5. */
    @Query(
        """SELECT w.mediaId AS mediaId, COALESCE(m.title, MAX(w.title)) AS title,
                  m.posterUrl AS posterUrl, MAX(w.finishedAt) AS finishedAtMs
           FROM watch_progress w LEFT JOIN media_items m ON m.id = w.mediaId
           WHERE w.finishedAt IS NOT NULL
           GROUP BY w.mediaId ORDER BY finishedAtMs DESC LIMIT 5"""
    )
    suspend fun recentlyFinished(): List<StatsFinishedRow>

    /** Hours per video quality of the files actually played in range.
     *  Files whose stream row is gone land in "Unknown", as before. */
    @Query(
        """SELECT COALESCE(s.quality, 'Unknown') AS quality, SUM(w.positionMs) AS ms
           FROM watch_progress w LEFT JOIN stream_files s ON s.id = w.streamFileId
           WHERE :since IS NULL OR w.playedAt >= :since
           GROUP BY quality HAVING ms > 0"""
    )
    suspend fun sumByQualitySince(since: Long?): List<StatsQualityRow>

    /** Bytes actually streamed in range: each played file contributes
     *  its size x the fraction watched (a finished file counts whole).
     *  Per-row truncation matches the old Kotlin sum. */
    @Query(
        """SELECT COALESCE(SUM(CAST(s.fileSize * CASE
               WHEN w.finishedAt IS NOT NULL THEN 1.0
               WHEN w.durationMs > 0 THEN MIN(1.0, MAX(0.0, w.positionMs * 1.0 / w.durationMs))
               ELSE 0.0 END AS INTEGER)), 0)
           FROM watch_progress w JOIN stream_files s ON s.id = w.streamFileId
           WHERE :since IS NULL OR w.playedAt >= :since"""
    )
    suspend fun streamedBytesSince(since: Long?): Long

    /** One row per watched title in range: its stats metadata + hours.
     *  The genre / language / country / decade breakdowns all read
     *  this list instead of every media row. */
    @Query(
        """SELECT m.genres AS genres, m.originalLanguage AS originalLanguage,
                  m.country AS country, m.year AS year, SUM(w.positionMs) AS hoursMs
           FROM watch_progress w JOIN media_items m ON m.id = w.mediaId
           WHERE :since IS NULL OR w.playedAt >= :since
           GROUP BY w.mediaId HAVING hoursMs > 0"""
    )
    suspend fun watchedTitleMetaSince(since: Long?): List<StatsTitleMetaRow>
}
