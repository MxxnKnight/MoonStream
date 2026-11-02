package com.moon.moonstream.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.util.AppLog
import com.moon.moonstream.util.SubtitleTiming
import java.io.IOException

/**
 * `tdsub://` — subtitles from Telegram, with the delay already applied.
 *
 * The addon server serves subtitles at `http://127.0.0.1:8081/sub/<id>?delayMs=`
 * and applies the offset itself. In-app, we do the same work here: read the
 * document through the backend contract, shift its cue times, hand Media3 the
 * bytes. So the player needs no server at all — video *and* subtitles work
 * with the addon off.
 *
 * Subtitles are small (a few hundred KB), so this fetches the whole file once
 * rather than using the chunked window, and keeps the shifted result in memory
 * so switching delay back and forth doesn't hit Telegram again.
 */
@UnstableApi
object TdSubUri {
    const val SCHEME = "tdsub"

    /** Refuse anything bigger than this — a subtitle that large is a mis-typed file. */
    const val MAX_BYTES = 8L * 1024 * 1024

    fun build(
        channelId: Long,
        messageId: Long,
        fileSize: Long,
        fileName: String,
        delayMs: Long,
    ): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority("sub")
        .appendPath(channelId.toString())
        .appendPath(messageId.toString())
        .appendPath(fileName)
        .appendQueryParameter("size", fileSize.toString())
        .appendQueryParameter("delay", delayMs.toString())
        .build()

    class Spec(
        val channelId: Long,
        val messageId: Long,
        val fileSize: Long,
        val fileName: String,
        val delayMs: Long,
    )

    fun parse(uri: Uri): Spec? {
        if (uri.scheme != SCHEME) return null
        val segments = uri.pathSegments
        if (segments.size < 3) return null
        val channelId = segments[0].toLongOrNull() ?: return null
        val messageId = segments[1].toLongOrNull() ?: return null
        val size = uri.getQueryParameter("size")?.toLongOrNull() ?: return null
        if (size <= 0L || size > MAX_BYTES) return null
        val delay = uri.getQueryParameter("delay")?.toLongOrNull() ?: 0L
        return Spec(channelId, messageId, size, segments[2], delay)
    }
}

/** Small in-memory cache of fetched+shifted subtitles, keyed by message and delay. */
internal object TdSubCache {
    private const val MAX_ENTRIES = 8

    private val map = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): ByteArray? = map[key]

    @Synchronized
    fun put(key: String, bytes: ByteArray) {
        map[key] = bytes
    }
}

@UnstableApi
class TdSubDataSource : BaseDataSource(true) {

    private var bytes: ByteArray = EMPTY
    private var position = 0
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val spec = TdSubUri.parse(dataSpec.uri)
            ?: throw IOException("Bad subtitle URI: ${dataSpec.uri}")

        val key = "${spec.channelId}_${spec.messageId}_${spec.delayMs}"
        val cached = TdSubCache.get(key)
        bytes = cached ?: fetch(spec, key)
        position = dataSpec.position.coerceIn(0L, bytes.size.toLong()).toInt()
        val remaining = (bytes.size - position).toLong()
        transferStarted(dataSpec)
        return remaining
    }

    private fun fetch(spec: TdSubUri.Spec, key: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(spec.fileSize.toInt())
        var got = 0
        var emptyReplies = 0
        while (got < spec.fileSize) {
            val part = try {
                kotlinx.coroutines.runBlocking {
                    BackendProvider.backend.readFileRange(
                        channelId = spec.channelId,
                        messageId = spec.messageId,
                        offset = got.toLong(),
                        length = (spec.fileSize - got).toInt(),
                    )
                }
            } catch (e: Exception) {
                throw IOException("Subtitle read failed at $got", e)
            }
            if (part.isEmpty()) {
                emptyReplies++
                if (emptyReplies >= 3) {
                    throw IOException("No bytes for subtitle at $got")
                }
                try {
                    Thread.sleep(150)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Cancelled while loading subtitle", e)
                }
                continue
            }
            emptyReplies = 0
            out.write(part)
            got += part.size
        }
        val shifted = SubtitleTiming.shift(out.toByteArray(), spec.fileName, spec.delayMs)
        AppLog.i("TDLOCAL", "subtitle loaded (${shifted.size / 1024} KB, delay=${spec.delayMs}ms)")
        TdSubCache.put(key, shifted)
        return shifted
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= bytes.size) return C.RESULT_END_OF_INPUT
        val count = minOf(length, bytes.size - position)
        System.arraycopy(bytes, position, buffer, offset, count)
        position += count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        bytes = EMPTY
        position = 0
        uri = null
        transferEnded()
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}

/** Factory so Media3 can build a fresh [TdSubDataSource] per subtitle track. */
@UnstableApi
class TdSubDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = TdSubDataSource()
}
