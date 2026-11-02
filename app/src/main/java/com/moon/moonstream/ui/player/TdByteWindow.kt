package com.moon.moonstream.ui.player

import android.content.Context
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.cache.TdStreamCache
import com.moon.moonstream.util.AppLog
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
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

    @Volatile
    private var closed = false

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
        prefetch(index + 1, LowStorageWindow.PREFETCH_CHUNKS)
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

        while (got < want) {
            if (closed) return
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
                delay(250L)
                continue
            }
            emptyReplies = 0
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
    }

    /** Fetches the next [count] chunks in the background, best effort. */
    private fun prefetch(fromIndex: Long, count: Int) {
        if (closed) return
        val last = LowStorageWindow.chunkCount(fileSize)
        for (i in 0 until count) {
            val idx = fromIndex + i
            if (idx >= last) return
            if (have(idx)) continue
            prefetchScope.launch {
                try {
                    mutex.withLock {
                        if (!closed && !have(idx)) download(idx)
                    }
                } catch (e: Exception) {
                    // Prefetch is an optimisation — playback still works.
                    AppLog.i("TDLOCAL", "prefetch chunk $idx skipped: ${e.message}")
                }
            }
        }
    }

    /**
     * Deletes chunks below [keepFrom], then trims oldest-first until the
     * folder is back under the window cap. [current] is never evicted.
     */
    private fun evictBefore(keepFrom: Long, current: Long) {
        try {
            var freed = 0L
            dir.listFiles()?.forEach { f ->
                val idx = f.name.removePrefix("chunk_").substringBefore('.').toLongOrNull()
                if (idx != null && idx < keepFrom && idx != openIndex) {
                    freed += f.length()
                    f.delete()
                }
            }
            val held = dir.listFiles()?.filter { it.name.startsWith("chunk_") } ?: return
            val heldBytes = held.sumOf { it.length() }
            if (heldBytes > LowStorageWindow.WINDOW_BYTES + LowStorageWindow.SLACK_BYTES) {
                val indexes = held.mapNotNull {
                    it.name.removePrefix("chunk_").substringBefore('.').toLongOrNull()
                }
                LowStorageWindow.chunksToEvict(heldBytes, indexes, current).forEach { idx ->
                    if (idx != openIndex && idx != current) {
                        freed += chunkFile(idx).length()
                        chunkFile(idx).delete()
                    }
                }
            }
            deletedSincePurge += freed
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
        deletedSincePurge = 0L
        prefetchScope.launch {
            try {
                mutex.withLock {
                    if (!closed) {
                        val ok = BackendProvider.backend.deleteCachedFile(channelId, messageId)
                        if (ok) AppLog.i("TDLOCAL", "Telegram copy purged for msg=$messageId")
                    }
                }
            } catch (e: Exception) {
                AppLog.i("TDLOCAL", "purge skipped: ${e.message}")
            } finally {
                purgeRunning = false
            }
        }
    }

    /** Closes handles and removes this file's chunks from disk. */
    fun release() {
        closed = true
        prefetchScope.cancel()
        try {
            openFile?.close()
        } catch (e: Exception) { /* ignore */ }
        openFile = null
        openIndex = -1L
        TdStreamCache.markInactive(channelId, messageId)
        TdStreamCache.clearFile(context, channelId, messageId)
    }

    private companion object {
        /** Ceiling on one chunk fetch; a stalled Telegram reply must not hang playback. */
        const val READ_TIMEOUT_MS = 90_000L

        /** Delete Telegram's copy after this much of our window has been deleted. */
        const val PURGE_AFTER_BYTES = 96L * 1024 * 1024
    }
}
