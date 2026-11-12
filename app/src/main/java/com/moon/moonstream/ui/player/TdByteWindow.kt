package com.moon.moonstream.ui.player

import android.content.Context
import android.os.SystemClock
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.cache.TdStreamCache
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.util.AppLog
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * The byte window for one file being played in the app.
 *
 * Each chunk is fetched from Telegram through the backend contract
 * (`readFileRange`, which maps to TdApi.DownloadFile/ReadFilePart with an
 * offset — no addon server involved), written to disk, and handed to Media3.
 * Chunks the playhead has passed are deleted, so storage stays near
 * [LowStorageWindow.WINDOW_BYTES] no matter how long the video is.
 *
 * This is deliberately a *layer over the frozen contract* rather than new
 * TdApi calls: the contract already exposes offset/length reads, so the app
 * can stream without the Telegram module (moon's lane) changing at all.
 *
 * Two lessons carried over from TMPlayer's engine (read for reference, code
 * written fresh):
 *  - eviction is checked once per chunk crossed, not once per read — a
 *    demuxer does thousands of tiny reads and must not stat a directory each
 *    time;
 *  - TDLib keeps its own copy of every byte we ask for, so the whole file
 *    would stay on the phone even with our window in order. We ask the
 *    module to drop it periodically (see [purgeTelegramCopy]); that call is
 *    a no-op until the module implements it, and skips nothing we need.
 */
internal class TdByteWindow(
    private val context: Context,
    private val channelId: Long,
    private val messageId: Long,
    private val fileSize: Long,
) {
    private val dir: File = TdStreamCache.dirFor(context, channelId, messageId)
    private val mutex = Mutex()
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var openIndex = -1L
    private var openFile: RandomAccessFile? = null

    /** Chunk index the last eviction/prefetch pass ran for. */
    private var lastAdvanceIndex = -1L

    /** Bytes deleted from our window since the last Telegram-side purge. */
    private var deletedSincePurge = 0L
    private var purgeRunning = false

    /** Only one prefetch run may be alive; a seek cancels the old one. */
    private var prefetchJob: Job? = null

    /** Range the live prefetch job is covering [from, until).
     *  A normal chunk crossing inside this range does NOT cancel the job —
     *  cancelling every ~1.5s (1 MB chunks) threw away the in-flight
     *  download and the prefetch never got ahead (moon's buffer fix
     *  2026-11-13). Only a jump outside the range (a seek) cancels it. */
    private var prefetchFrom = -1L
    private var prefetchUntil = -1L

    /**
     * Rate limiting. The backend reports every failure as "no bytes", so a
     * Telegram FLOOD_WAIT is indistinguishable from "not downloaded yet".
     * Treat empty replies as pressure: slow down, then back off exponentially
     * with jitter, and never retry faster than [MIN_FETCH_GAP_MS] apart.
     */
    @Volatile
    private var nextFetchAtMs = 0L

    @Volatile
    private var backoffUntilMs = 0L

    /** A reader is blocked on the network, so prefetch should yield to it. */
    @Volatile
    private var readerWaiting = false

    /** Consecutive empty replies (only touched while holding [mutex]). */
    private var emptyRun = 0

    @Volatile
    private var closed = false

    init {
        AppLog.i(
            "TDLOCAL",
            "window open msg=$messageId: ${fileSize / (1024 * 1024)} MB in " +
                "${LowStorageWindow.chunkCount(fileSize)} chunks of " +
                "${LowStorageWindow.CHUNK_BYTES / (1024 * 1024)} MB, keeping " +
                "${LowStorageWindow.WINDOW_BYTES / (1024 * 1024)} MB around the playhead",
        )
    }

    fun chunkFile(index: Long): File = File(dir, "chunk_$index.bin")

    private fun have(index: Long): Boolean {
        val want = LowStorageWindow.chunkLength(index, fileSize)
        if (want <= 0L) return false
        val f = chunkFile(index)
        return f.exists() && f.length() == want
    }

    /**
     * Reads from [position] into [buffer]. Blocks while the needed chunk is
     * fetched (Media3 reads on its loading thread). Returns -1 at end of file.
     */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= fileSize) return -1
        val index = LowStorageWindow.chunkIndex(position)
        ensureChunk(index)
        if (closed) return -1

        val chunkStart = LowStorageWindow.chunkStart(index)
        val chunkLen = LowStorageWindow.chunkLength(index, fileSize)
        val inChunk = position - chunkStart
        val toRead = minOf(length.toLong(), chunkLen - inChunk, fileSize - position).toInt()
        if (toRead <= 0) return -1

        val raf = handleFor(index)
        raf.seek(inChunk)
        val n = raf.read(buffer, offset, toRead)
        if (n <= 0) return -1

        advance(index)
        return n
    }

    /**
     * Runs once per chunk crossing: delete what has been watched, keep the
     * next chunks warm, and occasionally ask Telegram to drop its copy.
     */
    private fun advance(index: Long) {
        if (index == lastAdvanceIndex) return
        lastAdvanceIndex = index
        evictBefore(LowStorageWindow.keepFromChunk(index, fileSize), index)
        prefetch(index + 1, LowStorageWindow.prefetchChunks(AppUiState.playerBufferS))
        if (deletedSincePurge >= PURGE_AFTER_BYTES) purgeTelegramCopy()
    }

    private fun handleFor(index: Long): RandomAccessFile {
        if (openIndex == index && openFile != null) return openFile!!
        try {
            openFile?.close()
        } catch (e: Exception) { /* never break playback */ }
        openFile = RandomAccessFile(chunkFile(index), "r")
        openIndex = index
        return openFile!!
    }

    private fun ensureChunk(index: Long) {
        if (have(index)) return
        try {
            readerWaiting = true
            runBlocking {
                withTimeout(READ_TIMEOUT_MS) {
                    mutex.withLock {
                        if (!have(index)) download(index)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Telegram did not deliver chunk $index in ${READ_TIMEOUT_MS / 1000}s", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Cancelled while loading chunk $index")
        } finally {
            readerWaiting = false
        }
    }

    /** Must be called while holding [mutex]. */
    private suspend fun download(index: Long) {
        if (closed) return
        val start = LowStorageWindow.chunkStart(index)
        val want = LowStorageWindow.chunkLength(index, fileSize).toInt()
        if (want <= 0) return

        val target = chunkFile(index)
        val partial = File(dir, "chunk_$index.part")
        partial.delete()
        var got = 0L
        var emptyReplies = 0
        var requests = 0
        val startedAt = SystemClock.elapsedRealtime()

        while (got < want) {
            if (closed) return
            awaitRateLimit()
            requests++
            val bytes = try {
                BackendProvider.backend.readFileRange(
                    channelId = channelId,
                    messageId = messageId,
                    offset = start + got,
                    length = (want - got).toInt(),
                )
            } catch (e: Exception) {
                throw IOException("Telegram read failed at chunk $index", e)
            }
            if (bytes.isEmpty()) {
                emptyReplies++
                if (emptyReplies >= LowStorageWindow.MAX_EMPTY_REPLIES) {
                    partial.delete()
                    throw IOException("No bytes for chunk $index (offset ${start + got})")
                }
                registerEmptyReply(index, emptyReplies)
                continue
            }
            emptyReplies = 0
            registerBytes()
            partial.appendBytes(bytes)
            got += bytes.size
        }

        if (!partial.renameTo(target)) {
            // Rename can fail if the target lingered; fall back to a copy.
            target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
        }
        AppLog.d(
            "TDLOCAL",
            "chunk $index/${LowStorageWindow.chunkCount(fileSize)} stored: " +
                "${want / 1024} KB in ${SystemClock.elapsedRealtime() - startedAt} ms " +
                "($requests request${if (requests == 1) "" else "s"})",
        )
    }

    /**
     * Waits until it is polite to talk to Telegram again: the pacing gap after
     * the last request, plus any backoff from empty replies. Called while
     * holding [mutex], so nothing else is fetching meanwhile.
     */
    private suspend fun awaitRateLimit() {
        while (!closed) {
            val now = SystemClock.elapsedRealtime()
            val wait = maxOf(nextFetchAtMs - now, backoffUntilMs - now)
            if (wait <= 0L) return
            delay(minOf(wait, 1_000L))
        }
    }

    /** An empty reply: assume we are being throttled and back off. */
    private fun registerEmptyReply(index: Long, attempt: Int) {
        emptyRun++
        val base = BASE_BACKOFF_MS shl minOf(emptyRun - 1, 5)
        val capped = minOf(base, MAX_BACKOFF_MS)
        val jitter = (capped / 4.0 * Math.random()).toLong()
        backoffUntilMs = SystemClock.elapsedRealtime() + capped + jitter
        AppLog.i(
            "TDLOCAL",
            "no bytes for chunk $index (attempt $attempt), backing off ${capped + jitter} ms",
        )
    }

    /** Real bytes arrived: clear the backoff and pace the next request. */
    private fun registerBytes() {
        emptyRun = 0
        backoffUntilMs = 0L
        nextFetchAtMs = SystemClock.elapsedRealtime() + MIN_FETCH_GAP_MS
    }

    /**
     * Fetches the next [count] chunks in one background run, best effort.
     * A new run replaces the old one, and a blocked reader always goes first:
     * prefetching must never make playback wait longer for its own chunk.
     */
    private fun prefetch(fromIndex: Long, count: Int) {
        if (closed) return
        // Already covering this range: let the live job run on. Cancelling
        // it here would throw away its in-flight chunk and the prefetch
        // would never get ahead of a 1 MB-chunk playhead (moon's buffer
        // fix 2026-11-13 — the old code cancelled on every chunk crossing).
        if (prefetchJob?.isActive == true && fromIndex in prefetchFrom until prefetchUntil) return
        val last = LowStorageWindow.chunkCount(fileSize)
        val todo = (0 until count)
            .map { fromIndex + it }
            .filter { it < last && !have(it) }
        if (todo.isEmpty()) return
        prefetchJob?.cancel()
        prefetchFrom = fromIndex
        prefetchUntil = fromIndex + count
        prefetchJob = prefetchScope.launch {
            for (idx in todo) {
                if (closed) return@launch
                try {
                    // Yield to a blocked reader — but not forever. An
                    // unbounded yield starves the prefetch when the buffer
                    // is empty and the reader is downloading back-to-back:
                    // the prefetch never runs, so the buffer never builds
                    // (moon's "no ahead buffer" 2026-11-13). Wait up to
                    // ~500ms, then queue on the mutex like everyone else —
                    // the fair mutex still lets the reader go first when it
                    // already holds it, but the prefetch gets its turn.
                    var waitedMs = 0L
                    while (readerWaiting && !closed && waitedMs < 500L) {
                        delay(50L)
                        waitedMs += 50L
                    }
                    mutex.withLock {
                        if (!closed && !have(idx)) download(idx)
                    }
                } catch (e: Exception) {
                    // Prefetch is an optimisation — playback still works, and
                    // one failure means stop piling on extra requests.
                    AppLog.i("TDLOCAL", "prefetch chunk $idx skipped: ${e.message}")
                    return@launch
                }
            }
        }
    }

    /**
     * End byte of the contiguous downloaded run starting at [fromByte]'s
     * chunk — i.e. how far ahead of the playhead the video is already on
     * the phone. Only finished chunk files (.bin) count; a partial (.part)
     * or missing chunk ends the run. Used by the player seekbar to show
     * the buffered band the way mpv does (moon's 2026-11-06).
     */
    fun downloadedAheadBytes(fromByte: Long): Long {
        var idx = LowStorageWindow.chunkIndex(fromByte.coerceAtLeast(0L))
        while (true) {
            val f = chunkFile(idx)
            if (!f.exists() || f.length() <= 0L) break
            idx++
        }
        return minOf(LowStorageWindow.chunkStart(idx), fileSize)
    }

    /**
     * Deletes chunks below [keepFrom], then trims oldest-first until the
     * folder is back under the window cap. [current] is never evicted.
     */
    private fun evictBefore(keepFrom: Long, current: Long) {
        try {
            var freed = 0L
            var removed = 0
            dir.listFiles()?.forEach { f ->
                val idx = f.name.removePrefix("chunk_").substringBefore('.').toLongOrNull()
                if (idx != null && idx < keepFrom && idx != openIndex) {
                    freed += f.length()
                    if (f.delete()) removed++
                }
            }
            val held = dir.listFiles()?.filter { it.name.startsWith("chunk_") } ?: return
            var heldBytes = held.sumOf { it.length() }
            if (heldBytes > LowStorageWindow.WINDOW_BYTES + LowStorageWindow.SLACK_BYTES) {
                val indexes = held.mapNotNull {
                    it.name.removePrefix("chunk_").substringBefore('.').toLongOrNull()
                }
                LowStorageWindow.chunksToEvict(heldBytes, indexes, current).forEach { idx ->
                    if (idx != openIndex && idx != current) {
                        val f = chunkFile(idx)
                        freed += f.length()
                        if (f.delete()) removed++
                    }
                }
                heldBytes -= freed
            }
            deletedSincePurge += freed
            if (removed > 0) {
                AppLog.d(
                    "TDLOCAL",
                    "deleted $removed watched chunk(s) at chunk $current: " +
                        "${freed / 1024} KB freed, window now ${heldBytes / 1024} KB " +
                        "(${deletedSincePurge / (1024 * 1024)} MB deleted since Telegram purge)",
                )
            }
        } catch (e: Exception) {
            // Eviction is best effort; never break playback over a delete.
        }
    }

    /**
     * Asks the Telegram module to drop its own downloaded copy of this file.
     *
     * Our chunks are already complete, so nothing being played depends on
     * those bytes; the next chunk download fetches its own range. Without
     * this, TDLib's dir grows to the whole video even in low-storage mode.
     * Runs under [mutex] so it can never fire between our request and its
     * reply. The contract default is a no-op, so this is safe today and
     * starts working the moment the module implements `deleteCachedFile`.
     */
    private fun purgeTelegramCopy() {
        if (closed || purgeRunning) return
        purgeRunning = true
        val watchedMb = deletedSincePurge / (1024 * 1024)
        deletedSincePurge = 0L
        AppLog.i("TDLOCAL", "purging Telegram's copy of msg=$messageId after ${watchedMb} MB watched")
        prefetchScope.launch {
            try {
                mutex.withLock {
                    if (!closed) {
                        val ok = BackendProvider.backend.deleteCachedFile(channelId, messageId)
                        if (ok) {
                            AppLog.i("TDLOCAL", "Telegram copy deleted for msg=$messageId")
                        } else {
                            AppLog.w(
                                "TDLOCAL",
                                "Telegram copy NOT deleted for msg=$messageId " +
                                    "(backend said no — it will try again at the next window)",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.w("TDLOCAL", "purge failed for msg=$messageId: ${e.message}")
            } finally {
                purgeRunning = false
            }
        }
    }

    /** Closes handles and stops the prefetch. Does NOT delete the chunks:
     *  ExoPlayer closes/reopens the DataSource during playback (seeks,
     *  reopens), and deleting here wiped the whole buffer every time —
     *  the 0.15.151 stall (moon's log 2026-11-13: the 11 MB window was
     *  deleted on every reopen, so the buffer never grew past 11 MB).
     *  Chunks are deleted explicitly on player close (PlayerScreen
     *  onDispose) and by eviction while playing. */
    fun release() {
        closed = true
        prefetchJob?.cancel()
        prefetchScope.cancel()
        try {
            openFile?.close()
        } catch (e: Exception) { /* ignore */ }
        openFile = null
        openIndex = -1L
        TdStreamCache.markInactive(channelId, messageId)
        AppLog.i("TDLOCAL", "window closed for msg=$messageId")
    }

    private companion object {
        /** Ceiling on one chunk fetch; a stalled Telegram reply must not hang playback. */
        const val READ_TIMEOUT_MS = 90_000L

        /** Delete Telegram's copy after this much of our window has been deleted. */
        const val PURGE_AFTER_BYTES = 96L * 1024 * 1024

        /** Minimum gap between two network requests, so we never spam TDLib.
         *  50ms (moon's buffer fix 2026-11-13): was 200ms, tuned for 8 MB
         *  chunks where the gap was negligible. With 1 MB chunks the 200ms
         *  gap capped downloads at ~2.5 MB/s on a fast connection and the
         *  player starved — play 3s, buffer, play 3s, buffer. 50ms still
         *  paces requests (max ~20/s, real rate is download-bound at
         *  1-7/s) and the empty-reply backoff still guards flood waits. */
        const val MIN_FETCH_GAP_MS = 50L

        /** First backoff step; doubles per consecutive empty reply. */
        const val BASE_BACKOFF_MS = 400L

        /** Ceiling on a single backoff wait (plus up to 25% jitter). */
        const val MAX_BACKOFF_MS = 8_000L
    }
}
