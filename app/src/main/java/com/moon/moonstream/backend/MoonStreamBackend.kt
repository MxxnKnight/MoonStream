package com.moon.moonstream.backend

import kotlinx.coroutines.flow.StateFlow

/**
 * The socket between MoonStream's screens and Telegram.
 *
 * Plain-words version: the app never talks to Telegram directly. It only
 * talks to THIS contract. Today the [FakeMoonStreamBackend] is plugged in
 * (pretend data, everything works, nothing is real). One day the real
 * Telegram implementation gets plugged in via [BackendProvider.install],
 * and every screen keeps working without changing a single line — because
 * they only ever spoke to this contract.
 *
 * The contract is deliberately Telegram-agnostic: no TDLib types, no
 * session strings, no API keys anywhere in it. The real implementation
 * lives in its own module and is none of the UI's business.
 */

/** What the app needs to know about the Telegram connection. */
enum class BackendAuthState {
    LOGGED_OUT, // show the login screen
    READY,      // session is live; channels/messages/files are readable
}

/** A Telegram channel (or group) the backend can read. */
data class MsChannel(
    val id: Long,
    val title: String,
)

/** One file-bearing message inside a channel. */
data class MsMessage(
    /** Telegram message id (unique inside its channel). */
    val id: Long,
    val channelId: Long,
    /** e.g. "[S01 E16] Seven Deadly Sins [1080p].mkv" */
    val fileName: String,
    /** bytes, as reported by Telegram */
    val fileSize: Long,
    /** e.g. "video/x-matroska"; null when unknown */
    val mimeType: String?,
    /** epoch seconds */
    val date: Long,
)

/** The logged-in Telegram user (for the Account screen). */
data class MsProfile(
    val id: Long,
    val firstName: String,
    /** "@username"; null when the user has none */
    val username: String?,
    /** "+9199…" — masked for display by the UI */
    val phone: String,
)

interface MoonStreamBackend {
    /** Observed by the app to decide login screen vs main screens. */
    val authState: StateFlow<BackendAuthState>

    /**
     * All channels/groups the session can read, newest activity first.
     * Returns an empty list (never null, never throws for "no channels").
     */
    suspend fun listChannels(): List<MsChannel>

    /**
     * Up to [limit] file-bearing messages from [channelId], newest first,
     * starting *below* [fromMessageId] (pass 0 or Long.MAX_VALUE for the
     * newest page; pass the last seen id to page backwards).
     * Non-file messages (text, stickers…) are skipped by the implementation.
     */
    suspend fun fetchMessages(
        channelId: Long,
        fromMessageId: Long,
        limit: Int,
    ): List<MsMessage>

    /**
     * Raw bytes of a file, for the future streaming server (Phase 4/5).
     * Reads [length] bytes starting at [offset]. The implementation may
     * return fewer bytes at end-of-file, but never more than asked.
     */
    suspend fun readFileRange(
        channelId: Long,
        messageId: Long,
        offset: Long,
        length: Int,
    ): ByteArray

    /** The logged-in user. Only called when [authState] is READY. */
    suspend fun getMe(): MsProfile

    /** Ends the session and returns [authState] to LOGGED_OUT. */
    suspend fun logout()

    /** Upload raw bytes as a document to Saved Messages. Returns messageId or null. */
    suspend fun uploadBackup(bytes: ByteArray, fileName: String): Long?

    /** The newest #MoonStreamBackup document in Saved Messages, or null. */
    suspend fun findLatestBackup(): BackupRef?

    /** Download a backup document's full bytes. */
    suspend fun downloadBackup(ref: BackupRef): ByteArray?
}

/** A backup document found in Saved Messages. */
data class BackupRef(
    val messageId: Long,
    val fileId: Int,
    val date: Long,
)
