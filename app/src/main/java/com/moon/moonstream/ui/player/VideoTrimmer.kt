package com.moon.moonstream.ui.player

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaDataSource
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.moon.moonstream.backend.MoonStreamBackend
import com.moon.moonstream.util.AppLog
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking

/**
 * Video trimmer (moon's pick 2026-11-10): cut a [startMs, endMs) range out
 * of a Telegram stream and save it as an MP4 in Downloads.
 *
 * Lossless — samples are copied, never re-encoded, so the clip keeps the
 * original quality and the cut is fast. The source is NOT a local file,
 * so the extractor reads it through a [MediaDataSource] backed by TDLib
 * byte ranges ([MoonStreamBackend.readFileRange]); TDLib caches the parts
 * on disk, so re-reads are cheap.
 *
 * Two honest limits:
 * - The cut starts at the keyframe at or before startMs, so the clip may
 *   begin a few seconds early. That is normal for lossless cuts.
 * - The output is always MP4. A codec MediaMuxer can't put in MP4 (rare
 *   for moon's files) fails the cut instead of producing a broken file.
 */
object VideoTrimmer {
    private const val TAG = "TRIMMER"

    /** Read-ahead chunk for the random-access source (see TdMediaDataSource). */
    private const val CHUNK_BYTES = 512 * 1024

    /**
     * Random-access reader over a Telegram file. MediaExtractor seeks around
     * (header, index, samples), so small reads are served from a 512 KB
     * read-ahead buffer instead of one TDLib call per byte.
     */
    private class TdMediaDataSource(
        private val backend: MoonStreamBackend,
        private val channelId: Long,
        private val messageId: Long,
        private val size: Long,
    ) : MediaDataSource() {
        private val buf = ByteArray(CHUNK_BYTES)
        private var bufStart = -1L
        private var bufLen = 0

        override fun getSize(): Long = size

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= this.size) return -1
            if (size <= 0) return 0
            // Serve from the buffered chunk when it covers this read.
            if (bufStart < 0 || position < bufStart || position >= bufStart + bufLen) {
                val fetched = runBlocking {
                    backend.readFileRange(channelId, messageId, position, CHUNK_BYTES)
                }
                if (fetched.isEmpty()) return -1
                fetched.copyInto(buf, 0, 0, minOf(fetched.size, CHUNK_BYTES))
                bufStart = position
                bufLen = minOf(fetched.size, CHUNK_BYTES)
            }
            val inBuf = (position - bufStart).toInt()
            val available = bufLen - inBuf
            if (available <= 0) return -1
            val n = minOf(size, available)
            buf.copyInto(buffer, offset, inBuf, inBuf + n)
            return n
        }

        override fun close() {
            bufStart = -1L
            bufLen = 0
        }
    }

    /**
     * Cut the range and save it to Downloads. Runs on the caller's thread
     * (call from Dispatchers.IO/Default — it blocks on TDLib reads).
     * @param onProgress 0..1 as samples are written.
     * @return the Downloads Uri of the clip, or null on failure.
     */
    fun trimToDownloads(
        context: Context,
        backend: MoonStreamBackend,
        channelId: Long,
        messageId: Long,
        fileSize: Long,
        fileName: String,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit,
    ): Uri? {
        if (endMs <= startMs || fileSize <= 0L) return null
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L
        val base = fileName.substringBeforeLast('.').take(80).ifBlank { "clip" }
        val outName = "$base [${formatClipTime(startMs)}-${formatClipTime(endMs)}].mp4"
        var outUri: Uri? = null
        var pfd: android.os.ParcelFileDescriptor? = null
        var muxer: MediaMuxer? = null
        var extractor: MediaExtractor? = null
        try {
            // Reserve the Downloads entry first so a failure still leaves
            // no half-written file behind (we delete it on error).
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, outName)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            outUri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
            ) ?: run {
                AppLog.e(TAG, "trim: could not create Downloads entry")
                return null
            }
            pfd = context.contentResolver.openFileDescriptor(outUri, "w")
                ?: run {
                    AppLog.e(TAG, "trim: could not open Downloads entry")
                    return null
                }
            extractor = MediaExtractor()
            extractor.setDataSource(
                TdMediaDataSource(backend, channelId, messageId, fileSize),
            )
            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // Select video + audio tracks, map extractor index -> muxer index.
            val muxerTrack = IntArray(extractor.trackCount) { -1 }
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    muxerTrack[i] = muxer.addTrack(format)
                }
            }
            if (muxerTrack.none { it >= 0 }) {
                AppLog.e(TAG, "trim: no video/audio tracks found")
                return null
            }
            muxer.start()
            // Start at the keyframe at or before startMs (lossless cuts
            // land on keyframes — the clip may start a few seconds early).
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val sampleBuf = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstUs = -1L
            var written = 0L
            val totalUs = (endUs - startUs).coerceAtLeast(1L)
            while (true) {
                val trackIdx = extractor.sampleTrackIndex
                if (trackIdx < 0) break
                val sampleUs = extractor.sampleTime
                if (sampleUs >= endUs) break
                val muxIdx = muxerTrack.getOrNull(trackIdx) ?: -1
                if (muxIdx < 0) {
                    extractor.advance()
                    continue
                }
                val size = extractor.readSampleData(sampleBuf, 0)
                if (size < 0) break
                // Re-base the timeline so the clip starts at 0.
                if (firstUs < 0) firstUs = sampleUs
                info.set(0, size, sampleUs - firstUs, extractor.sampleFlags)
                muxer.writeSampleData(muxIdx, sampleBuf, info)
                written++
                onProgress(
                    ((sampleUs - startUs).coerceAtLeast(0L).toFloat() / totalUs)
                        .coerceIn(0f, 1f),
                )
                extractor.advance()
            }
            muxer.stop()
            AppLog.i(TAG, "trim: wrote $written samples to $outName")
            onProgress(1f)
            return outUri
        } catch (e: Exception) {
            AppLog.e(TAG, "trim failed: ${e.message}")
            // Don't leave a broken file in Downloads.
            try { outUri?.let { context.contentResolver.delete(it, null, null) } } catch (_: Exception) { }
            return null
        } finally {
            try { muxer?.release() } catch (_: Exception) { }
            try { extractor?.release() } catch (_: Exception) { }
            try { pfd?.close() } catch (_: Exception) { }
        }
    }

    /** "12m34s" for the clip file name. */
    private fun formatClipTime(ms: Long): String {
        val totalSecs = ms / 1000
        val m = totalSecs / 60
        val s = totalSecs % 60
        return if (m > 0) "${m}m${s.toString().padStart(2, '0')}s" else "${s}s"
    }
}
