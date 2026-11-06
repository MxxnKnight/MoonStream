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
 */
@UnstableApi
object TdStreamUri {
    const val SCHEME = "tdfile"

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

    class Spec(
        val channelId: Long,
        val messageId: Long,
        val fileSize: Long,
        val fileName: String,
    )

    fun parse(uri: Uri): Spec? {
        if (uri.scheme != SCHEME) return null
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val channelId = segments[0].toLongOrNull() ?: return null
        val messageId = segments[1].toLongOrNull() ?: return null
        val size = uri.getQueryParameter("size")?.toLongOrNull() ?: -1L
        if (size <= 0L) return null
        return Spec(channelId, messageId, size, segments.getOrNull(2).orEmpty())
    }

    /**
     * Container type from the file name, or null when it says nothing useful.
     *
     * Media3 tries extractors in a fixed order (FLV, FLAC, WAV, MP4, …,
     * Matroska last), each reading the head of the stream to say no. A
     * Content-Type puts the matching one first, which is the difference
     * between an instant first frame and several dead reads on a big MKV.
     */
    fun contentMimeFor(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase()) {
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
 */
@UnstableApi
class TdDataSource(private val context: Context) : BaseDataSource(true) {

    companion object {
        /** The TdDataSource currently serving the player, if any (one player at a time). */
        @Volatile
        var current: TdDataSource? = null
            private set
    }

    private var window: TdByteWindow? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var uri: Uri? = null
    private var headers: Map<String, List<String>> = emptyMap()
    private var fileSize = -1L

    /**
     * Fraction of the file (0..1) downloaded contiguously from the playhead
     * onward — the seekbar's buffer band. Null when this source isn't serving
     * (no window). Byte fraction ≈ time fraction; the band is indicative.
     */
    fun downloadedAheadFraction(playheadMs: Long, durationMs: Long): Float? {
        val w = window ?: return null
        val size = fileSize
        if (size <= 0L || durationMs <= 0L) return null
        val playheadByte = (playheadMs.toDouble() / durationMs * size).toLong()
        return (w.downloadedAheadBytes(playheadByte).toFloat() / size).coerceIn(0f, 1f)
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
        headers = TdStreamUri.contentMimeFor(parsed.fileName)
            ?.let { mapOf("Content-Type" to listOf(it)) }
            ?: emptyMap()
        aheadAt = -1L
        aheadLength = 0

        val size = parsed.fileSize
        if (dataSpec.position >= size) {
            // Past EOF: report an empty source rather than an error, so a
            // container probe at the end of the file stays harmless.
            position = size
            bytesRemaining = 0L
            transferStarted(dataSpec)
            return 0L
        }

        TdStreamCache.markActive(parsed.channelId, parsed.messageId)
        window = TdByteWindow(context, parsed.channelId, parsed.messageId, size)
        fileSize = size
        current = this
        position = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            size - position
        } else {
            minOf(dataSpec.length, size - position)
        }
        transferStarted(dataSpec)
        AppLog.i(
            "TDLOCAL",
            "opening direct stream msg=${parsed.messageId} from ${position}B " +
                "of ${size / (1024 * 1024)} MB",
        )
        return bytesRemaining
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
        val w = window ?: return -1
        return try {
            w.read(position, dest, destOffset, length)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("tdfile read failed at $position", e)
        }
    }

    override fun close() {
        try {
            window?.release()
        } catch (e: Exception) { /* never break playback on close */ }
        if (current == this) current = null
        window = null
        uri = null
        headers = emptyMap()
        position = 0L
        bytesRemaining = 0L
        aheadAt = -1L
        aheadLength = 0
        fileSize = -1L
        transferEnded()
    }

    private companion object {
        const val AHEAD_BYTES = 64 * 1024

        /** Reads smaller than this are worth filling the buffer for. */
        const val SMALL_READ_BYTES = 4 * 1024
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
