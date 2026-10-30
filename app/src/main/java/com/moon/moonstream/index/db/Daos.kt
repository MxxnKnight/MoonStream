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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(file: StreamFileEntity): Long

    @Query("DELETE FROM stream_files WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)
}

@Dao
interface ScanCursorDao {
    @Query("SELECT * FROM scan_cursors WHERE channelId = :channelId")
    suspend fun get(channelId: Long): ScanCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(cursor: ScanCursorEntity)

    @Query("DELETE FROM scan_cursors WHERE channelId = :channelId")
    suspend fun clear(channelId: Long)
}

@Dao
interface UnmatchedDao {
    @Query("SELECT * FROM unmatched_files ORDER BY id DESC")
    fun observeAll(): Flow<List<UnmatchedFileEntity>>

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
