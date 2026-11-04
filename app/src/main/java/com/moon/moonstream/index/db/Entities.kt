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
    /** Kitsu anime ID for AniSkip intro lookup. Set at index time for anime. */
    val kitsuId: String? = null,
    /** AniList anime ID for AniSkip API. Set at index time for anime. */
    val anilistId: String? = null,
    /**
     * Stremio-facing ID: real `tt...` when matched, else synthetic `tg...`
     * (see StremioIds). Set at index time; backfilled for older rows.
     */
    val stremioId: String? = null,
    val posterUrl: String?,
    val overview: String?,
    val totalSize: Long,
    val episodeCount: Int,
    val fileCount: Int,
    val updatedAt: Long,
    /** User-hidden via Library multi-select / Detail. Shown in the HIDDEN tab. */
    val hidden: Boolean = false,
    /** User-created via the Create screen (never auto-matched / re-matched). */
    val isCustom: Boolean = false,
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
    /** Last episode for multi-episode files (e.g. "E7-8" → episode=7, episodeEnd=8). Null = single. */
    val episodeEnd: Int? = null,
    val quality: String,
)

/**
 * Continue-watching entry: what the user last played.
 * (moon's pick 2026-11-01 — v1 tracks "played X ago", not exact position,
 * since external players don't report position back.)
 */
@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val streamFileId: Long,
    val title: String,
    val episodeLabel: String?,
    val posterUrl: String?,
    val playedAt: Long,
    /** Last playback position in ms for in-player Resume (0 = none). */
    val positionMs: Long = 0,
    /** Total duration in ms for progress bar (0 = unknown). */
    val durationMs: Long = 0,
)

/**
 * Display row for the Home "Continue watching" strip (fix 2026-11-05).
 *
 * A projection of watch_progress LEFT JOIN media_items, so the poster and
 * title stay correct after a re-index (the old version copied them at insert
 * time and showed a blank letter tile when the copy was stale).
 */
data class ContinueWatchingItem(
    val mediaId: Long,
    val streamFileId: Long,
    val title: String,
    val episodeLabel: String?,
    val posterUrl: String?,
    val playedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
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

/** A subtitle file found during scanning. Linked to a media item when the
 *  scanner (or the user, via Attach) can map it; otherwise mediaId is null
 *  and it waits in the unlinked list. */
@Entity(
    tableName = "subtitle_files",
    indices = [
        Index(value = ["channelId", "messageId"], unique = true),
        Index(value = ["mediaId"]),
    ],
)
data class SubtitleFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val messageId: Long,
    val fileName: String,
    val fileSize: Long,
    /** Null until the subtitle is mapped to a library entry. */
    val mediaId: Long?,
    val season: Int?,
    val episode: Int?,
    /** e.g. "Mal", "Eng", "Hin" — "unknown" when the filename gives no hint. */
    val language: String,
)

/** "X Movies · Y Shows · Z Episodes" breakdown for one channel. */
data class ChannelBreakdown(
    val movies: Int,
    val shows: Int,
    val episodes: Int,
)
