package com.moon.moonstream.ui.player

/**
 * Sliding byte-window policy for the in-app player.
 *
 * Plain words: a 2 GB episode must not cost 2 GB of phone storage. The player
 * asks TDLib for one chunk at a time (1 MB), keeps a few ahead, and deletes
 * everything it has already played — keeping only a small margin behind the
 * play position so a quick rewind doesn't have to hit the network.
 *
 * Single buffer (moon's 2026-11-12): there is ONE buffer setting now — the
 * player buffer in seconds. It drives both how much the player holds ready
 * and how far ahead this window prefetches (see [prefetchChunks]). The old
 * separate "Buffer MB" setting is gone.
 *
 * Pure numbers + arithmetic only: no Android, no I/O. The downloads and file
 * deletes live in `TdByteWindow.kt`; this file just decides "how much".
 */
object LowStorageWindow {

    /** One network request = one chunk on disk. 1 MB keeps seeks responsive
     * and wastes little when the viewer jumps away (moon's 2026-11-12: was
     * 8 MB — the NoxMedia/TMPlayer way is small chunks pulled on demand). */
    const val CHUNK_BYTES = 1L * 1024 * 1024

    /** Absolute cap on bytes held for one playing file. */
    const val WINDOW_BYTES = 200L * 1024 * 1024

    /** Below this, rolling isn't worth the churn (kept for tuning/debug). */
    const val MIN_WINDOW_BYTES = 32L * 1024 * 1024

    /** Keep this much *behind* the play position (small rewinds stay free).
     *  10 MB (moon's 2026-11-13: was 2 MB, only ~2-3s back — a -10s
     *  rewind had to re-download). 10 MB covers a 10s rewind at typical
     *  bitrates (5-8 Mbps); still small next to the 200 MB window cap. */
    const val BACK_BYTES = 10L * 1024 * 1024

    /** Extra headroom so container index reads near EOF don't evict the start. */
    const val SLACK_BYTES = 16L * 1024 * 1024

    /** Upper bound for prefetch (the window itself caps held bytes at 200 MB). */
    const val MAX_PREFETCH_CHUNKS = 32

    /**
     * Chunks to prefetch for the single buffer setting: one 1 MB chunk per
     * second of player buffer, so the download-ahead always matches what
     * the player wants to hold. The default 50 s buffer prefetches 32
     * chunks (the cap) = 32 MB ahead; a 20 s buffer prefetches 20.
     * Pure arithmetic; the setting itself is wired in TdByteWindow.
     */
    fun prefetchChunks(playerBufferS: Int): Int =
        playerBufferS.coerceIn(4, MAX_PREFETCH_CHUNKS)

    /**
     * Give up (and surface an error) after this many empty backend replies in
     * a row. With the player's exponential backoff this spends roughly 20 s
     * waiting — long enough for a Telegram flood-wait to pass, short enough
     * that a dead file still shows an error.
     */
    const val MAX_EMPTY_REPLIES = 6

    /** Chunk index containing [position]. */
    fun chunkIndex(position: Long): Long =
        if (position <= 0L) 0L else position / CHUNK_BYTES

    /** First byte offset of [index]. */
    fun chunkStart(index: Long): Long = index * CHUNK_BYTES

    /** Bytes in [index] for a file of [size] bytes (short for the last chunk). */
    fun chunkLength(index: Long, size: Long): Long {
        val start = chunkStart(index)
        if (start >= size) return 0L
        return minOf(CHUNK_BYTES, size - start)
    }

    /** Number of chunks a file of [size] bytes needs. */
    fun chunkCount(size: Long): Long =
        if (size <= 0L) 0L else (size + CHUNK_BYTES - 1) / CHUNK_BYTES

    /**
     * Earliest chunk index that may stay on disk while [current] is playing:
     * everything below it has been watched and gets deleted.
     */
    fun keepFromChunk(current: Long, fileSize: Long = Long.MAX_VALUE): Long {
        // Files small enough to fit the window are never rolled: rolling them
        // would re-download bytes we can simply keep.
        if (fileSize <= MIN_WINDOW_BYTES) return 0L
        val backChunks = (BACK_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES
        return (current - backChunks).coerceAtLeast(0L)
    }

    /**
     * Chunks to delete, oldest first, so the folder drops back under
     * [WINDOW_BYTES]. Never returns the chunk being read or anything after it.
     */
    fun chunksToEvict(
        heldBytes: Long,
        heldChunks: List<Long>,
        current: Long,
    ): List<Long> {
        if (heldBytes <= WINDOW_BYTES + SLACK_BYTES) return emptyList()
        var over = heldBytes - WINDOW_BYTES
        val doomed = mutableListOf<Long>()
        for (idx in heldChunks.sorted()) {
            if (over <= 0L) break
            if (idx >= current) break
            doomed.add(idx)
            over -= CHUNK_BYTES
        }
        return doomed
    }
}
