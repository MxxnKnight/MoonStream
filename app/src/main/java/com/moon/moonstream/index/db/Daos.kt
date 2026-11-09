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

    @Query("SELECT * FROM media_items WHERE channelId = :channelId AND normalizedTitle = :norm LIMIT 1")
    suspend fun findByTitle(channelId: Long, norm: String): MediaItemEntity?

    /** Any-channel lookup for the "In library" badge (moon's request 2026-11-01). */
    @Query("SELECT * FROM media_items WHERE normalizedTitle = :norm LIMIT 1")
    suspend fun findByNormAnyChannel(norm: String): MediaItemEntity?

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

    @Query("SELECT * FROM media_items WHERE channelId = :channelId")
    suspend fun getAllByChannel(channelId: Long): List<MediaItemEntity>

    @Query("UPDATE media_items SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query(
        """UPDATE media_items SET
               title = :title, normalizedTitle = :norm, year = :year,
               imdbId = :imdbId, tmdbId = :tmdbId, posterUrl = :posterUrl,
               backdropUrl = :backdropUrl,
               overview = :overview, stremioId = :stremioId, type = :type,
               updatedAt = :now
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
}
