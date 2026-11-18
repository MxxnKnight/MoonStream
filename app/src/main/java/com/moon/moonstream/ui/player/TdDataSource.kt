package com.moon.moonstream.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.moon.moonstream.cache.TdStreamCache
import com.moon.moonstream.index.parse.SplitFiles
import com.moon.moonstream.index.parse.ZipStream
import com.moon.moonstream.util.AppLog
import java.io.IOException

/**
 * `tdfile://` — the in-app player's direct-from-Telegram stream.
 *
 * The addon server path (`http://127.0.0.1:8081/stream/...`) needs
 * MoonStreamServerService running. This path does not: Media3 asks this
 * DataSource for bytes, the DataSource pulls only the window it needs from
 * the backend contract and deletes what's been watched (see
 * [TdByteWindow]). The HTTP server stays untouched for Stremio and external
 * players.
 *
 * URI shape:
 * `tdfile://stream/<channelId>/<messageId>/<fileName>?size=<bytes>`
 * The file name rides along as the last path segment so this source can name
 * the container to Media3 (see [contentMimeFor]) instead of making its
 * extractor sniff every format before the right one.
 *
 * Split files (.001, .002, … — moon's 2026-11-15) carry every part in a
 * `parts` query param as `channel:message:size` triples, comma-separated;
 * the path still carries the first part (for logging) and the base file
 * name (`Movie.mkv`, so the container hint sees the real extension).
 * [TdDataSource] then serves the parts as one virtual file.
 */
@UnstableApi
object TdStreamUri {
    const val SCHEME = "tdfile"

    /** One part of a (possibly split) stream: a Telegram file + its size. */
    data class Part(val channelId: Long, val messageId: Long, val size: Long)

    fun build(
        channelId: Long,
        messageId: Long,
        fileSize: Long,
        fileName: String,
    ): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority("stream")
        .appendPath(channelId.toString())
        .appendPath(messageId.toString())
        .appendPath(fileName)
        .appendQueryParameter("size", fileSize.toString())
        .build()

    /** Multi-part build for split files: every part rides in `parts`. */
    fun buildParts(parts: List<Part>, fileName: String): Uri {
        val first = parts.first()
        return Uri.Builder()
            .scheme(SCHEME)
            .authority("stream")
            .appendPath(first.channelId.toString())
            .appendPath(first.messageId.toString())
            .appendPath(fileName)
            .appendQueryParameter("size", parts.sumOf { it.size }.toString())
            .appendQueryParameter(
                "parts",
                parts.joinToString(",") { "${it.channelId}:${it.messageId}:${it.size}" },
            )
            .build()
    }

    class Spec(
        val channelId: Long,
        val messageId: Long,
        val fileSize: Long,
        val fileName: String,
        /** All parts in play order; a single part for a normal file. */
        val parts: List<Part>,
    )

    fun parse(uri: Uri): Spec? {
        if (uri.scheme != SCHEME) return null
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val channelId = segments[0].toLongOrNull() ?: return null
        val messageId = segments[1].toLongOrNull() ?: return null
        val size = uri.getQueryParameter("size")?.toLongOrNull() ?: -1L
        if (size <= 0L) return null
        val fileName = segments.getOrNull(2).orEmpty()
        val partsParam = uri.getQueryParameter("parts")
        val parts = if (partsParam.isNullOrBlank()) {
            listOf(Part(channelId, messageId, size))
        } else {
            val parsed = partsParam.split(",").mapNotNull { triple ->
                val t = triple.split(":")
                if (t.size != 3) return@mapNotNull null
                val ch = t[0].toLongOrNull() ?: return@mapNotNull null
                val msg = t[1].toLongOrNull() ?: return@mapNotNull null
                val sz = t[2].toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
                Part(ch, msg, sz)
            }
            // A malformed parts list falls back to the single file in the
            // path — better a truncated play than no play at all.
            if (parsed.isEmpty()) listOf(Part(channelId, messageId, size)) else parsed
        }
        return Spec(channelId, messageId, parts.sumOf { it.size }, fileName, parts)
    }

    /**
     * Container type from the file name, or null when it says nothing useful.
     *
     * Media3 tries extractors in a fixed order (FLV, FLAC, WAV, MP4, …,
     * Matroska last), each reading the head of the stream to say no. A
     * Content-Type puts the matching one first, which is the difference
     * between an instant first frame and several dead reads on a big MKV.
     *
     * A split part (`Movie.mkv.001`) names its container before the part
     * number — the suffix is stripped first or the hint is lost.
     */
    fun contentMimeFor(fileName: String): String? =
        when (SplitFiles.baseName(fileName).substringAfterLast('.', "").lowercase()) {
            "mkv", "mka", "mk3d" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp4", "m4v", "mov", "3gp" -> "video/mp4"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            else -> null
        }
}

/**
 * Media3 [DataSource] for one `tdfile://` stream. Reads block while the
 * needed chunk is fetched — that's normal for a network source; Media3 calls
 * us on its loading thread, never the UI thread.
 *
 * Split files (.001, .002, … — moon's 2026-11-15): the stream is one
 * [TdByteWindow] per part, and this class maps the virtual file position
 * (0..total) onto the part that holds it. A read never crosses a part
 * boundary in one call — Media3 just calls read() again and lands in the
 * next part. Each window keeps its own chunk dir, prefetch and rate limit,
 * so the per-file machinery is untouched.
 *
 * Split ZIP archives (`.zip.001`, … — moon's 2026-11-18): a STORED zip's
 * video streams from its data offset inside the joined archive (see
 * [ZipStream]). [position] and [fileSize] are then VIRTUAL (video)
 * coordinates — virtual position P reads joined position P + [zipOffset] —
 * so ExoPlayer sees a clean video with the right size and MIME.
 */
@UnstableApi
class TdDataSource(private val context: Context) : BaseDataSource(true) {

    private var windows: List<TdByteWindow> = emptyList()
    private var parts: List<TdStreamUri.Part> = emptyList()

    /** Virtual start offset of each part (cumulative sizes). */
    private var partStarts: LongArray = longArrayOf()
    private var position = 0L
    private var bytesRemaining = 0L
    private var uri: Uri? = null
    private var headers: Map<String, List<String>> = emptyMap()
    private var fileSize = -1L

    /**
     * Zip offset: virtual position P reads joined position P + zipOffset.
     * 0 for a normal file; the video's data offset inside the joined
     * archive for a stored .zip (see [ZipStream]).
     */
    private var zipOffset = 0L

    /** Which part holds this virtual position (parts are few — linear scan). */
    private fun partIndexFor(pos: Long): Int {
        var idx = 0
        while (idx < partStarts.size - 1 && pos >= partStarts[idx + 1]) idx++
        return idx
    }

    /**
     * Fraction of the file (0..1) downloaded contiguously from the playhead
     * onward — the seekbar's buffer band. Null when this source isn't serving
     * (no window). Byte fraction ≈ time fraction; the band is indicative.
     * For a split file the run walks across parts: a part whose chunks run
     * to its end continues into the next part from byte 0.
     */
    fun downloadedAheadFraction(playheadMs: Long, durationMs: Long): Float? {
        val ws = windows
        if (ws.isEmpty()) return null
        val size = fileSize
        if (size <= 0L || durationMs <= 0L) return null
        val playheadByte = (playheadMs.toDouble() / durationMs * size)
            .toLong().coerceIn(0L, size)
        // Positions are virtual (video) coordinates; the windows live in
        // real (joined archive) coordinates, shifted by the zip offset.
        val joinedTotal = partStarts.last() + parts.last().size
        val realPlayhead = (playheadByte + zipOffset).coerceIn(0L, joinedTotal - 1)
        var idx = partIndexFor(realPlayhead)
        var localFrom = realPlayhead - partStarts[idx]
        var virtualEnd = playheadByte
        while (idx < ws.size) {
            val runEnd = ws[idx].downloadedAheadBytes(localFrom)
            virtualEnd = partStarts[idx] + runEnd - zipOffset
            if (runEnd < parts[idx].size) break
            idx++
            localFrom = 0L
        }
        return (virtualEnd.toFloat() / size).coerceIn(0f, 1f)
    }

    /**
     * Read-ahead for small reads.
     *
     * A demuxer walking a Matroska index asks for one or two bytes at a time;
     * served straight from the window, that is a directory check and a file
     * seek per byte. Reading 64 KB once and handing out slices turns thousands
     * of those into one.
     */
    private val ahead = ByteArray(AHEAD_BYTES)
    private var aheadAt = -1L
    private var aheadLength = 0

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = headers

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val parsed = TdStreamUri.parse(dataSpec.uri)
            ?: throw IOException("Not a tdfile:// stream: ${dataSpec.uri}")
        uri = dataSpec.uri
        aheadAt = -1L
        aheadLength = 0
        zipOffset = 0L

        val size = parsed.fileSize
        val parts = parsed.parts
        parts.forEach { TdStreamCache.markActive(it.channelId, it.messageId) }
        windows = parts.map { TdByteWindow(context, it.channelId, it.messageId, it.size) }
        this.parts = parts
        partStarts = LongArray(parts.size).also { arr ->
            var acc = 0L
            parts.forEachIndexed { i, p -> arr[i] = acc; acc += p.size }
        }
        fileSize = size

        // Zip-aware playback (moon's 2026-11-18): a STORED .zip's video
        // streams from its data offset inside the joined archive, so the
        // virtual file becomes just the video — its size, its MIME. The
        // probe reads the header from the first chunk playback fetches
        // anyway, and caches, so a seek re-open costs nothing. A compressed
        // zip can't stream by offset: fail with a clear message instead of
        // a cryptic extractor error. Anything unparseable falls through to
        // plain joined playback, as today.
        var innerMime: String? = null
        if (ZipStream.isZipArchive(SplitFiles.baseName(parsed.fileName))) {
            val first = parts.first()
            val key = "${first.channelId}:${first.messageId}:$size"
            val probe = try {
                ZipStream.probeCached(key, size, parts.map { it.size }) { off, len ->
                    readJoinedAt(off, len)
                }
            } catch (e: Exception) {
                AppLog.w("TDLOCAL", "zip probe failed for ${parsed.fileName}: ${e.message}")
                ZipStream.Probe.Unreadable
            }
            when (probe) {
                is ZipStream.Probe.Playable -> {
                    zipOffset = probe.info.dataOffset
                    fileSize = probe.info.videoSize
                    innerMime = TdStreamUri.contentMimeFor(probe.info.innerName)
                    AppLog.i(
                        "TDLOCAL",
                        "zip stream: inner '${probe.info.innerName}' at +$zipOffset, " +
                            "${fileSize / (1024 * 1024)} MB",
                    )
                }
                ZipStream.Probe.Compressed -> throw IOException(
                    "This file is a compressed zip, so it can't stream. " +
                        "Extract the video out of the zip first to watch it.",
                )
                ZipStream.Probe.Unreadable -> { /* plain joined playback, as today */ }
            }
        }
        headers = (innerMime ?: TdStreamUri.contentMimeFor(parsed.fileName))
            ?.let { mapOf("Content-Type" to listOf(it)) }
            ?: emptyMap()

        if (dataSpec.position >= fileSize) {
            // Past EOF: report an empty source rather than an error, so a
            // container probe at the end of the file stays harmless.
            position = fileSize
            bytesRemaining = 0L
            transferStarted(dataSpec)
            return 0L
        }

        current = this
        position = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            fileSize - position
        } else {
            minOf(dataSpec.length, fileSize - position)
        }
        transferStarted(dataSpec)
        AppLog.i(
            "TDLOCAL",
            "opening direct stream msg=${parsed.messageId} from ${position}B " +
                "of ${fileSize / (1024 * 1024)} MB" +
                if (parts.size > 1) " (${parts.size} split parts)" else "" +
                if (zipOffset > 0) " [zip +$zipOffset]" else "",
        )
        return bytesRemaining
    }

    /**
     * Reads the joined parts at a REAL global offset (zip probing: the
     * probe reads the archive's own bytes, before any video offset
     * applies). Loops across part boundaries; returns what it got (short
     * at EOF). Throws on a window failure — the probe treats that as
     * unreadable and playback falls back to plain bytes.
     */
    private fun readJoinedAt(offset: Long, size: Int): ByteArray {
        val out = ByteArray(size)
        var filled = 0
        var at = offset
        while (filled < size) {
            if (at < 0) break
            val idx = partIndexFor(at)
            if (idx >= parts.size) break
            val inPart = at - partStarts[idx]
            val availInPart = parts[idx].size - inPart
            if (availInPart <= 0) break
            val want = minOf((size - filled).toLong(), availInPart).toInt()
            val n = windows[idx].read(inPart, out, filled, want)
            if (n <= 0) break
            filled += n
            at += n
        }
        return if (filled == size) out else out.copyOf(filled)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val read = readOnce(buffer, offset, length)
        if (read <= 0) return C.RESULT_END_OF_INPUT
        position += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    private fun readOnce(buffer: ByteArray, offset: Int, length: Int): Int {
        // Served from what a previous small read already brought in.
        if (aheadAt >= 0 && position >= aheadAt && position < aheadAt + aheadLength) {
            val from = (position - aheadAt).toInt()
            val count = minOf(length.toLong(), (aheadLength - from).toLong(), bytesRemaining).toInt()
            System.arraycopy(ahead, from, buffer, offset, count)
            return count
        }
        if (length < SMALL_READ_BYTES) {
            val want = minOf(AHEAD_BYTES.toLong(), bytesRemaining).toInt()
            val filled = windowRead(ahead, 0, want)
            aheadAt = position
            aheadLength = if (filled > 0) filled else 0
            if (aheadLength == 0) return -1
            val count = minOf(length, aheadLength)
            System.arraycopy(ahead, 0, buffer, offset, count)
            return count
        }
        return windowRead(buffer, offset, length)
    }

    private fun windowRead(dest: ByteArray, destOffset: Int, length: Int): Int {
        if (windows.isEmpty()) return -1
        // Never read past the holding part's end in one call — the next
        // read() lands in the following part (see the class comment).
        // position is VIRTUAL (video) coordinates; the zip offset shifts
        // it into the joined archive's real coordinates (0 for non-zips).
        val real = position + zipOffset
        val idx = partIndexFor(real)
        val inPart = real - partStarts[idx]
        // Cap by bytesRemaining too: for a zip, the archive continues past
        // the video (central directory), and a read past the video's end
        // would hand the extractor zip bytes. (Also fixes a latent
        // over-read for bounded DataSpecs on plain files.)
        val capped = minOf(length.toLong(), bytesRemaining, parts[idx].size - inPart).toInt()
        if (capped <= 0) return -1
        return try {
            windows[idx].read(inPart, dest, destOffset, capped)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("tdfile read failed at $position", e)
        }
    }

    override fun close() {
        try {
            windows.forEach { w ->
                try {
                    w.release()
                } catch (e: Exception) { /* never break playback on close */ }
            }
        } catch (e: Exception) { /* never break playback on close */ }
        if (current == this) current = null
        windows = emptyList()
        parts = emptyList()
        partStarts = longArrayOf()
        uri = null
        headers = emptyMap()
        position = 0L
        bytesRemaining = 0L
        aheadAt = -1L
        aheadLength = 0
        fileSize = -1L
        zipOffset = 0L
        transferEnded()
    }

    companion object {
        const val AHEAD_BYTES = 64 * 1024

        /** Reads smaller than this are worth filling the buffer for. */
        const val SMALL_READ_BYTES = 4 * 1024

        /** The TdDataSource currently serving the player, if any (one player at a time). */
        @Volatile
        var current: TdDataSource? = null
            private set
    }
}

/**
 * Factory for the in-app player: `tdfile://` and `tdsub://` go to Telegram,
 * everything else (http, file://) falls through to Media3's defaults, so the
 * addon-server path keeps working exactly as before.
 */
@UnstableApi
class MsDataSourceFactory(context: Context) : DataSource.Factory {
    private val appContext = context.applicationContext

    override fun createDataSource(): DataSource = TdRoutingDataSource(appContext)
}

@UnstableApi
private class TdRoutingDataSource(private val context: Context) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source: DataSource = when (dataSpec.uri.scheme) {
            TdStreamUri.SCHEME -> TdDataSource(context)
            TdSubUri.SCHEME -> TdSubDataSource()
            else -> DefaultDataSource.Factory(context).createDataSource()
        }
        listeners.forEach { source.addTransferListener(it) }
        delegate = source
        return try {
            source.open(dataSpec)
        } catch (e: IOException) {
            delegate = null
            try {
                source.close()
            } catch (ignored: Exception) { /* ignore */ }
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val d = delegate ?: throw IOException("DataSource not open")
        return d.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        val d = delegate
        delegate = null
        d?.close()
    }
}
