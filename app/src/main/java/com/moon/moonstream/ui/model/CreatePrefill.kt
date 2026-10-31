package com.moon.moonstream.ui.model

/**
 * A Telegram file the user picked for a custom entry — either from the
 * unmatched list, from a channel, or from the "wrong title?" flows.
 */
data class PrefillFile(
    val channelId: Long,
    val messageId: Long,
    val fileName: String,
    val fileSize: Long,
    /** True when this file is a subtitle (not a video). */
    val isSubtitle: Boolean = false,
    /** For series: the season/episode this file represents (user-editable). */
    val season: Int? = null,
    val episode: Int? = null,
    /**
     * When set, this file already exists as a stream row (the per-file
     * "wrong title?" → "Add to custom content" flow): Create moves the row
     * instead of inserting a duplicate.
     */
    val existingStreamId: Long? = null,
)

/**
 * Pre-fill for the Create screen. When the user taps "Create entry" /
 * "Add to custom content" on a file, the Create screen opens with that
 * file already attached.
 */
data class CreatePrefill(
    val files: List<PrefillFile> = emptyList(),
)
