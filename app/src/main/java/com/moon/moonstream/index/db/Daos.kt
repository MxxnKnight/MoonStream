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

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE channelId = :channelId ORDER BY title COLLATE NOCASE")
    fun observeByChannel(channelId: Long): Flow<List<MediaItemEntity>>

    @Query(
        """SELECT * FROM media_items
           WHERE channelId = :channelId AND normalizedTitle = :norm
           AND type = :type AND (year = :year OR (year IS NULL AND :year IS NULL))
           LIMIT 1""",
    )
    suspend fun findMatch(
        channelId: Long,
        norm: String,
        year: String?,
        type: String,
    ): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE channelId = :channelId AND normalizedTitle = :norm LIMIT 1")
    suspend fun findByTitle(channelId: Long, norm: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE hidden = 0 ORDER BY updatedAt DESC")
    suspend fun listVisible(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE imdbId = :imdbId LIMIT 1")
    suspend fun findByImdb(imdbId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE channelId = :channelId")
    suspend fun getAllByChannel(channelId: Long): List<MediaItemEntity>

    @Query("UPDATE media_items SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query(
        """UPDATE media_items SET
               title = :title, normalizedTitle = :norm, year = :year,
               imdbId = :imdbId, tmdbId = :tmdbId, posterUrl = :posterUrl,
               overview = :overview, updatedAt = :now
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
        overview: String?,
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

    @Query("UPDATE stream_files SET mediaId = :newMediaId WHERE id = :fileId")
    suspend fun moveToMedia(fileId: Long, newMediaId: Long)

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

    @Query("DELETE FROM subtitle_files WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)

    @Query("DELETE FROM subtitle_files WHERE id = :id")
    suspend fun delete(id: Long)

    /** Media ids having at least one linked subtitle. */
    @Query("SELECT DISTINCT mediaId FROM subtitle_files WHERE mediaId IS NOT NULL")
    suspend fun mediaIdsWithSubtitles(): List<Long>
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
