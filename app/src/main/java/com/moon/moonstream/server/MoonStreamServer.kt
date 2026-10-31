package com.moon.moonstream.server

import android.content.Context
import android.util.Log
import com.moon.moonstream.BuildConfig
import com.moon.moonstream.backend.MoonStreamBackend
import com.moon.moonstream.index.db.MediaItemEntity
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.db.StreamFileEntity
import com.moon.moonstream.index.db.SubtitleFileEntity
import com.moon.moonstream.index.StremioIds
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 4 (hardened): embedded Stremio addon HTTP server backed by the real
 * Room library — no fake data anywhere in the response path.
 *
 * Two sockets (per PLAN.md): the ADDON port serves manifest/catalog/meta/
 * stream-list/subtitle-list JSON; the STREAM port serves the same routes plus
 * the actual file bytes (/stream/file/…, /sub/…). Stremio only ever sees URLs
 * built with the stream port for bytes.
 *
 * Stremio id scheme: "ms_<mediaDbId>" for titles, "ms_<mediaDbId>:<s>:<e>"
 * for episodes. /meta also accepts raw IMDb ids ("tt1234567").
 */
class MoonStreamServer(
    appContext: Context,
    private val backend: MoonStreamBackend,
    private val addonPort: Int,
    private val streamPort: Int,
) {
    private val appContext = appContext.applicationContext
    private val db by lazy { MsDatabase.get(appContext) }

    private var addonSocket: ServerSocket? = null
    private var streamSocket: ServerSocket? = null
    private var executor = Executors.newFixedThreadPool(8)
    @Volatile
    private var running = false

    fun isRunning(): Boolean = running

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        return try {
            if (executor.isShutdown) executor = Executors.newFixedThreadPool(8)
            addonSocket = bind(addonPort, "addon")
            streamSocket = bind(streamPort, "stream")
            running = true
            Log.i(TAG, "Server started: addon=$addonPort stream=$streamPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            stop()
            false
        }
    }

    private fun bind(port: Int, name: String): ServerSocket {
        val socket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
        thread(name = "MoonStream-$name-acceptor", isDaemon = true) {
            while (running && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (!running) break
                    Log.e(TAG, "Error accepting $name connection", e)
                }
            }
        }
        return socket
    }

    @Synchronized
    fun stop() {
        if (!running && addonSocket == null && streamSocket == null) return
        running = false
        try { addonSocket?.close() } catch (_: Exception) {}
        try { streamSocket?.close() } catch (_: Exception) {}
        addonSocket = null
        streamSocket = null
        try { executor.shutdownNow() } catch (_: Exception) {}
        Log.i(TAG, "Server stopped")
    }

    fun addonManifestUrl(): String = "http://${getLocalIpAddress()}:$addonPort/manifest.json"
    fun streamBaseUrl(): String = "http://${getLocalIpAddress()}:$streamPort"

    // ------------------------------------------------------------------ HTTP

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val output = client.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = URLDecoder.decode(rawPath.substringBefore("?"), "UTF-8")

            // Read headers (we only care about Range).
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] =
                    line.substring(idx + 1).trim()
            }

            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                return
            }
            val headOnly = method == "HEAD"
            if (method != "GET" && !headOnly) {
                sendJson(output, 405, JSONObject().put("error", "Method Not Allowed").toString(), headOnly)
                return
            }

            when {
                path == "/" || path == "/manifest.json" ->
                    sendJson(output, 200, buildManifest().toString(), headOnly)
                path.startsWith("/catalog/") ->
                    sendJson(output, 200, handleCatalog(path).toString(), headOnly)
                path.startsWith("/meta/") ->
                    sendJson(output, 200, handleMeta(path).toString(), headOnly)
                path.startsWith("/stream/") && !path.startsWith("/stream/file/") ->
                    sendJson(output, 200, handleStreamList(path).toString(), headOnly)
                path.startsWith("/subtitles/") ->
                    sendJson(output, 200, handleSubtitles(path).toString(), headOnly)
                path.startsWith("/stream/file/") ->
                    serveMediaBytes(output, path.removePrefix("/stream/file/"), headers, headOnly)
                path.startsWith("/sub/") ->
                    serveSubtitleBytes(output, path.removePrefix("/sub/"), headOnly)
                else ->
                    sendJson(output, 404, JSONObject().put("error", "Not Found").toString(), headOnly)
            }
        } catch (e: Exception) {
            // Never leak stack traces to the client; log locally.
            Log.e(TAG, "Error handling client request", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------- addon routes

    private fun buildManifest(): JSONObject {
        val catalogs = JSONArray()
        catalogs.put(JSONObject().put("type", "movie").put("id", "moonstream_movies").put("name", "MoonStream Movies"))
        catalogs.put(JSONObject().put("type", "series").put("id", "moonstream_series").put("name", "MoonStream Series & Anime"))
        return JSONObject()
            .put("id", "org.moonstream.addon")
            .put("version", BuildConfig.VERSION_NAME)
            .put("name", "MoonStream")
            .put("description", "Personal Telegram media library")
            .put("resources", JSONArray(listOf("catalog", "meta", "stream", "subtitles")))
            .put("types", JSONArray(listOf("movie", "series")))
            .put("catalogs", catalogs)
            // tt = real IMDb IDs, tg = stable synthetic IDs for everything
            // else (Telegram-Stremio scheme — this is what lets clients like
            // Nuvio accept and route our items), ms_ = legacy MoonStream IDs.
            .put("idPrefixes", JSONArray(listOf("tt", "tg", "ms_")))
    }

    /** /catalog/{movie|series}/{id}.json */
    private fun handleCatalog(path: String): JSONObject {
        val trimmed = path.removePrefix("/catalog/").removeSuffix(".json")
        val type = trimmed.split("/").firstOrNull().orEmpty()
        val wantSeries = type == "series"
        val items = runBlocking {
            db.mediaDao().listVisible().filter {
                if (wantSeries) (it.type == "series" || it.type == "anime") else it.type == "movie"
            }
        }
        val metas = JSONArray()
        for (m in items) metas.put(metaJson(m))
        return JSONObject().put("metas", metas)
    }

    /** /meta/{movie|series}/{id}.json — id is tt... / tg... [:s:e], or legacy ms_<dbId>[:s:e]. */
    private fun handleMeta(path: String): JSONObject {
        val trimmed = path.removePrefix("/meta/").removeSuffix(".json")
        val rawId = trimmed.split("/").getOrNull(1).orEmpty()
        val media = runBlocking { resolveMedia(rawId) }
        if (media == null) return JSONObject().put("meta", JSONObject())

        val meta = metaJson(media)
        val sid = StremioIds.forMedia(media)
        val files = runBlocking { db.streamDao().listByMedia(media.id) }
        val videos = JSONArray()
        if (media.type == "movie") {
            for (f in files) {
                videos.put(JSONObject()
                    .put("id", sid)
                    .put("title", f.fileName)
                    .put("released", ""))
            }
        } else {
            val episodes = files
                .filter { it.season != null && it.episode != null }
                .groupBy { it.season!! to it.episode!! }
                .toSortedMap(compareBy({ it.first }, { it.second }))
            for ((se, _) in episodes) {
                val (s, e) = se
                videos.put(JSONObject()
                    .put("id", "$sid:$s:$e")
                    .put("title", if (s == 1) "Episode $e" else "S${s} E$e")
                    .put("season", s)
                    .put("episode", e))
            }
        }
        meta.put("videos", videos)
        return JSONObject().put("meta", meta)
    }

    /** /stream/{movie|series}/{id}.json - id is tt... / tg... [:s:e], or legacy ms_<dbId>[:s:e]. */
    private fun handleStreamList(path: String): JSONObject {
        val trimmed = path.removePrefix("/stream/").removeSuffix(".json")
        val rawId = trimmed.split("/").getOrNull(1).orEmpty()
        val streams = JSONArray()
        val media = runBlocking { resolveMedia(rawId) } ?: return JSONObject().put("streams", streams)
        val (season, episode) = parseSeasonEpisode(rawId)
        val files = runBlocking { db.streamDao().listByMedia(media.id) }
            .filter { f ->
                if (season != null && episode != null) f.season == season && f.episode == episode
                else true
            }

        // Group split parts (.001, .002, etc.) so they stream as a joined single stream
        val splitRegex = Regex("""(?i)\.(?:(?:mkv|mp4|avi|webm)\.)?0*\d{1,3}$""")
        val groups = files.groupBy { f ->
            val base = splitRegex.replace(f.fileName, "")
            "$base|${f.season ?: ""}|${f.episode ?: ""}"
        }

        val base = streamBaseUrl()
        for ((_, groupFiles) in groups) {
            val sorted = groupFiles.sortedBy { it.fileName }
            val first = sorted.first()
            val isSplit = sorted.size > 1 || splitRegex.containsMatchIn(first.fileName)
            val totalSize = sorted.sumOf { it.fileSize }

            val label = buildString {
                append(media.title)
                if (isSplit) append(" [MULTI-PART ${sorted.size} files]")
                else if (first.quality.isNotBlank()) append(" [${first.quality}]")
                append(" (${formatSize(totalSize)})")
            }

            streams.put(JSONObject()
                .put("name", "MoonStream")
                .put("title", label)
                .put("url", "$base/stream/file/${media.id}/${first.id}"))
        }
        return JSONObject().put("streams", streams)
    }
    private fun handleSubtitles(path: String): JSONObject {
        val trimmed = path.removePrefix("/subtitles/").removeSuffix(".json")
        val rawId = trimmed.split("/").getOrNull(1).orEmpty()
        val subs = JSONArray()
        val media = runBlocking { resolveMedia(rawId) } ?: return JSONObject().put("subtitles", subs)
        val (season, episode) = parseSeasonEpisode(rawId)
        val list = runBlocking { db.subtitleDao().listByMedia(media.id) }
            .filter { s ->
                if (season != null && episode != null) s.season == season && s.episode == episode
                else true
            }
        val base = streamBaseUrl()
        for (s in list) {
            subs.put(JSONObject()
                .put("id", s.id.toString())
                .put("url", "$base/sub/${s.id}")
                .put("lang", stremioLang(s.language)))
        }
        return JSONObject().put("subtitles", subs)
    }

    // ---------------------------------------------------------- byte routes

    /** GET/HEAD /stream/file/{mediaId}/{fileId} — real bytes via the backend, HTTP Range aware. Supports joined .001 split files. */
    private fun serveMediaBytes(output: OutputStream, rest: String, headers: Map<String, String>, headOnly: Boolean) {
        val parts = rest.split("/")
        val mediaId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val fileId = parts.getOrNull(1)?.toLongOrNull()
        if (fileId == null) { sendJson(output, 400, errJson("Bad file id").toString(), headOnly); return }
        val targetFile = runBlocking { db.streamDao().getById(fileId) }
        if (targetFile == null) { sendJson(output, 404, errJson("File not found").toString(), headOnly); return }

        // Determine if targetFile is part of a split sequence (.001, .002, etc.)
        val splitRegex = Regex("""(?i)\.(?:(?:mkv|mp4|avi|webm)\.)?0*\d{1,3}$""")
        val allMediaFiles = runBlocking { db.streamDao().listByMedia(mediaId) }
        val baseName = splitRegex.replace(targetFile.fileName, "")
        val sequence = allMediaFiles.filter { f ->
            splitRegex.replace(f.fileName, "") == baseName &&
            f.season == targetFile.season && f.episode == targetFile.episode
        }.sortedBy { it.fileName }

        val isSplit = sequence.size > 1
        val partsList = if (isSplit) sequence else listOf(targetFile)
        val total = partsList.sumOf { it.fileSize }
        if (total <= 0) { sendJson(output, 502, errJson("Unknown file size").toString(), headOnly); return }

        val range = parseRange(headers["range"], total)
        val start = range?.first ?: 0L
        val end = range?.second ?: (total - 1)
        if (start >= total || end < start) {
            sendResponse(output, 416, "Range Not Satisfiable", "text/plain", ByteArray(0),
                extraHeaders = mapOf("Content-Range" to "bytes */$total"))
            return
        }
        val length = (end - start + 1).coerceAtMost(total - start)
        val mime = mimeFor(targetFile.fileName)
        val extra = mutableMapOf(
            "Accept-Ranges" to "bytes",
            "Content-Type" to mime,
        )
        val status = if (range != null) {
            extra["Content-Range"] = "bytes $start-$end/$total"
            206 to "Partial Content"
        } else 200 to "OK"

        if (headOnly) {
            sendResponse(output, status.first, status.second, mime, ByteArray(0),
                contentLengthOverride = length, extraHeaders = extra)
            return
        }
        try {
            val header = buildHeader(status.first, status.second, mime, length, extra)
            output.write(header.toString().toByteArray(Charsets.UTF_8))

            // Helper to read bytes across split boundaries
            fun readGlobalRange(globalOffset: Long, requestSize: Int): ByteArray {
                var currentGlobal = 0L
                for (p in partsList) {
                    val partStart = currentGlobal
                    val partEnd = currentGlobal + p.fileSize
                    if (globalOffset >= partStart && globalOffset < partEnd) {
                        val offsetInPart = globalOffset - partStart
                        val availableInPart = (p.fileSize - offsetInPart).coerceAtMost(requestSize.toLong()).toInt()
                        val bytes = runBlocking {
                            backend.readFileRange(p.channelId, p.messageId, offsetInPart, availableInPart)
                        }
                        return bytes
                    }
                    currentGlobal += p.fileSize
                }
                return ByteArray(0)
            }

            var offset = start
            var remaining = length
            while (remaining > 0) {
                val chunk = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                val bytes = readGlobalRange(offset, chunk)
                if (bytes.isEmpty()) break
                output.write(bytes)
                offset += bytes.size
                remaining -= bytes.size
            }
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming file ${targetFile.id}", e)
        }
    }
    private fun serveSubtitleBytes(output: OutputStream, rest: String, headOnly: Boolean) {
        val subId = rest.split("/").firstOrNull()?.toLongOrNull()
        if (subId == null) { sendJson(output, 400, errJson("Bad subtitle id").toString(), headOnly); return }
        val sub = runBlocking { db.subtitleDao().getById(subId) }
        if (sub == null) { sendJson(output, 404, errJson("Subtitle not found").toString(), headOnly); return }
        if (sub.fileSize <= 0 || sub.fileSize > MAX_SUB_BYTES) {
            sendJson(output, 502, errJson("Bad subtitle size").toString(), headOnly); return
        }
        try {
            val bytes: ByteArray = runBlocking {
                backend.readFileRange(sub.channelId, sub.messageId, 0, sub.fileSize.toInt())
            }
            val mime = mimeFor(sub.fileName)
            if (headOnly) {
                sendResponse(output, 200, "OK", mime, ByteArray(0),
                    contentLengthOverride = bytes.size.toLong())
            } else {
                sendResponse(output, 200, "OK", mime, bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error serving subtitle ${sub.id}", e)
            sendJson(output, 502, errJson("Upstream read failed").toString(), headOnly)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun metaJson(m: MediaItemEntity): JSONObject {
        // The Stremio-facing ID: real tt... when matched, else a stable
        // synthetic tg... — every item gets one, so clients like Nuvio
        // accept the whole library, not just matched titles.
        val sid = StremioIds.forMedia(m)
        val o = JSONObject()
        o.put("id", sid)
        o.put("imdb_id", sid)
        o.put("type", if (m.type == "anime") "series" else m.type)
        o.put("name", m.title)
        if (!m.year.isNullOrBlank()) o.put("year", m.year)
        if (!m.overview.isNullOrBlank()) o.put("description", m.overview)
        if (!m.posterUrl.isNullOrBlank()) {
            o.put("poster", m.posterUrl)
            o.put("background", m.posterUrl)
        }
        return o
    }

    /** "tt...[:s:e]" / "tg...[:s:e]" / "ms_<dbId>[:s:e]" → (season, episode). */
    private fun parseSeasonEpisode(rawId: String): Pair<Int?, Int?> {
        val parts = rawId.split(":")
        return parts.getOrNull(1)?.toIntOrNull() to parts.getOrNull(2)?.toIntOrNull()
    }

    /** Parses legacy ms_<id>[:s:e]; returns null when unparseable. */
    private fun parseStremioId(raw: String): Triple<Long, Int?, Int?>? {
        val id = raw.removePrefix("ms_")
        if (id == raw) return null
        val parts = id.split(":")
        val mediaId = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        return Triple(mediaId, season, episode)
    }

    private suspend fun resolveMedia(rawId: String): MediaItemEntity? {
        val dao = db.mediaDao()
        // Strip any :season:episode suffix — the lookup key is parts[0].
        val base = rawId.split(":").first()
        if (base.startsWith("tt")) {
            dao.findByImdb(base)?.let { return it }
            return dao.findByStremioId(base)
        }
        if (base.startsWith("tg")) return dao.findByStremioId(base)
        // Legacy ms_<dbId> links (older installs / old copy-links).
        val parsed = parseStremioId(rawId) ?: return null
        return dao.getById(parsed.first)
    }

    /** "bytes=123-456" / "bytes=123-" → Pair(start, end) clamped to [0, total). Null = no/invalid Range. */
    private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header == null) return null
        val m = Regex("""bytes=(\d*)-(\d*)""").matchEntire(header.trim()) ?: return null
        val startStr = m.groupValues[1]
        val endStr = m.groupValues[2]
        return try {
            when {
                startStr.isNotEmpty() && endStr.isNotEmpty() ->
                    startStr.toLong() to minOf(endStr.toLong(), total - 1)
                startStr.isNotEmpty() -> startStr.toLong() to (total - 1)
                endStr.isNotEmpty() -> { // suffix range: last N bytes
                    val n = endStr.toLong().coerceAtMost(total)
                    (total - n) to (total - 1)
                }
                else -> null
            }
        } catch (_: NumberFormatException) { null }
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "webm" -> "video/webm"
        "ts", "m2ts" -> "video/mp2t"
        "mov" -> "video/quicktime"
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/plain"
        "vtt" -> "text/vtt"
        else -> "application/octet-stream"
    }

    /** Parser language tags ("Mal","Eng","unknown") → Stremio lang codes. */
    private fun stremioLang(raw: String): String = when (raw.trim().lowercase()) {
        "eng", "en", "english" -> "eng"
        "mal", "ml", "malayalam" -> "mal"
        "hin", "hi", "hindi" -> "hin"
        "tam", "ta", "tamil" -> "tam"
        "tel", "te", "telugu" -> "tel"
        "unknown", "" -> "und"
        else -> raw.trim().lowercase().take(3)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "?"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
    }

    private fun errJson(msg: String) = JSONObject().put("error", msg)

    private fun sendJson(output: OutputStream, statusCode: Int, json: String, headOnly: Boolean) {
        val bytes = if (headOnly) ByteArray(0) else json.toByteArray(Charsets.UTF_8)
        val text = when (statusCode) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"
            405 -> "Method Not Allowed"; 416 -> "Range Not Satisfiable"
            502 -> "Bad Gateway"; else -> "Error"
        }
        // For HEAD, still advertise the real body length a GET would return.
        val len = if (headOnly) json.toByteArray(Charsets.UTF_8).size.toLong() else bytes.size.toLong()
        sendResponse(output, statusCode, text, "application/json; charset=utf-8", bytes,
            contentLengthOverride = len)
    }

    private fun buildHeader(
        statusCode: Int,
        statusText: String,
        contentType: String,
        contentLength: Long,
        extraHeaders: Map<String, String> = emptyMap(),
    ): StringBuilder {
        val header = StringBuilder()
        header.append("HTTP/1.1 $statusCode $statusText\r\n")
        header.append("Content-Type: $contentType\r\n")
        header.append("Content-Length: $contentLength\r\n")
        header.append("Access-Control-Allow-Origin: *\r\n")
        header.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        header.append("Access-Control-Allow-Headers: *\r\n")
        for ((k, v) in extraHeaders) {
            if (k.equals("Content-Type", ignoreCase = true)) continue
            header.append("$k: $v\r\n")
        }
        header.append("Connection: close\r\n\r\n")
        return header
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        contentLengthOverride: Long? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val header = buildHeader(statusCode, statusText, contentType,
            contentLengthOverride ?: body.size.toLong(), extraHeaders)
        output.write(header.toString().toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    companion object {
        private const val TAG = "MoonStreamServer"
        private const val CHUNK_SIZE = 1_048_576 // 1 MB per Telegram read
        private const val MAX_SUB_BYTES = 8 * 1_048_576 // sanity cap for subtitle files

        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
                for (intf in interfaces) {
                    if (intf.isLoopback || !intf.isUp) continue
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: ""
                            if (host.isNotBlank() && !host.startsWith("127.")) return host
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not determine local IP", e)
            }
            return "127.0.0.1"
        }

        /** Stremio-safe URL segment. */
        fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
