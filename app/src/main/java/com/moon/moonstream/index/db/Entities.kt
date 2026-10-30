package com.moon.moonstream.index.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 3: the on-phone library database.
 *
 * One [MediaItemEntity] groups every file of the same title inside one
 * channel (a movie, a series, or an anime). Individual Telegram files live
 * in [StreamFileEntity]. Files the parser couldn't identify land in
 * [UnmatchedFileEntity] for the Link-to-Title flow.
 */

/** A movie / series / anime entry in the library. */
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val title: String,
    /** Lower-cased, punctuation-stripped title used for grouping + matching. */
    val normalizedTitle: String,
    val year: String?,
    /** "movie" | "series" | "anime" */
    val type: String,
    val imdbId: String?,
    val tmdbId: Int?,
    val posterUrl: String?,
    val overview: String?,
    val totalSize: Long,
    val episodeCount: Int,
    val fileCount: Int,
    val updatedAt: Long,
)

/** One Telegram file attached to a media item. */
@Entity(
    tableName = "stream_files",
    indices = [
        Index(value = ["channelId", "messageId"], unique = true),
        Index(value = ["mediaId"]),
    ],
)
data class StreamFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val channelId: Long,
    val messageId: Long,
    val fileName: String,
    val fileSize: Long,
    val season: Int?,
    val episode: Int?,
    val quality: String,
)

/** Resume cursor per channel: newest message id already scanned. */
@Entity(tableName = "scan_cursors")
data class ScanCursorEntity(
    @PrimaryKey val channelId: Long,
    val lastMessageId: Long,
)

/** Files the parser couldn't identify — shown in Channels → Unmatched. */
@Entity(
    tableName = "unmatched_files",
    indices = [Index(value = ["channelId", "messageId"], unique = true)],
)
data class UnmatchedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val messageId: Long,
    val fileName: String,
    val fileSize: Long,
)

/** Per-channel user settings: anime flag + soft-remove + sync timestamp. */
@Entity(tableName = "channel_config")
data class ChannelConfigEntity(
    @PrimaryKey val channelId: Long,
    val title: String,
    val enabled: Boolean = true,
    val isAnime: Boolean = false,
    /** Set by "Remove channel" — hides it from the list even though Telegram still lists it. */
    val removed: Boolean = false,
    /** Epoch millis of the last completed scan of this channel. 0 = never. */
    val lastSyncedAt: Long = 0L,
)

/** "X Movies · Y Shows · Z Episodes" breakdown for one channel. */
data class ChannelBreakdown(
    val movies: Int,
    val shows: Int,
    val episodes: Int,
)
