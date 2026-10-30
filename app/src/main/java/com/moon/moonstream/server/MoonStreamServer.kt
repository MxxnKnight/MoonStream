package com.moon.moonstream.server

import android.util.Log
import com.moon.moonstream.ui.model.fakeTitles
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 4: Embedded Stremio / Nuvio Addon HTTP Server.
 * Serves /manifest.json, /catalog, /meta, and /stream endpoints
 * over local Wi-Fi to Stremio on Android TV, PC, phone, or Web.
 */
object MoonStreamServer {
    private const val TAG = "MoonStreamServer"

    private var serverSocket: ServerSocket? = null
    private var executor = Executors.newFixedThreadPool(8)
    @Volatile
    private var running = false

    fun isRunning(): Boolean = running

    @Synchronized
    fun start(port: Int): Boolean {
        if (running) return true
        return try {
            val socket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
            serverSocket = socket
            running = true
            if (executor.isShutdown) {
                executor = Executors.newFixedThreadPool(8)
            }

            thread(name = "MoonStream-Acceptor", isDaemon = true) {
                while (running && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        executor.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (!running) break
                        Log.e(TAG, "Error accepting connection", e)
                    }
                }
            }
            Log.i(TAG, "Server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server on port $port", e)
            false
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        try {
            executor.shutdownNow()
        } catch (_: Exception) {}
        Log.i(TAG, "Server stopped")
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine local IP", e)
        }
        return "127.0.0.1"
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val output = client.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = URLDecoder.decode(rawPath.substringBefore("?"), "UTF-8")

            // Handle CORS Preflight
            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                return
            }

            // Route endpoints
            when {
                path == "/" || path == "/manifest.json" -> {
                    val manifest = buildManifest()
                    sendJson(output, 200, manifest.toString())
                }
                path.startsWith("/catalog/") -> {
                    val catalogResponse = handleCatalog(path)
                    sendJson(output, 200, catalogResponse.toString())
                }
                path.startsWith("/meta/") -> {
                    val metaResponse = handleMeta(path)
                    sendJson(output, 200, metaResponse.toString())
                }
                path.startsWith("/stream/") -> {
                    val streamResponse = handleStream(path)
                    sendJson(output, 200, streamResponse.toString())
                }
                else -> {
                    val notFound = JSONObject().put("error", "Not Found")
                    sendJson(output, 404, notFound.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request", e)
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun buildManifest(): JSONObject {
        val manifest = JSONObject()
        manifest.put("id", "org.moonstream.addon")
        manifest.put("version", "0.5.2")
        manifest.put("name", "MoonStream")
        manifest.put("description", "Personal Telegram Stremio & Nuvio Addon Server")
        manifest.put("resources", JSONArray(listOf("catalog", "meta", "stream")))
        manifest.put("types", JSONArray(listOf("movie", "series", "anime")))

        val catalogs = JSONArray()
        catalogs.put(
            JSONObject()
                .put("type", "movie")
                .put("id", "moonstream_movies")
                .put("name", "MoonStream Movies")
        )
        catalogs.put(
            JSONObject()
                .put("type", "series")
                .put("id", "moonstream_series")
                .put("name", "MoonStream Series & Anime")
        )
        manifest.put("catalogs", catalogs)
        manifest.put("idPrefixes", JSONArray(listOf("ms_")))
        return manifest
    }

    private fun handleCatalog(path: String): JSONObject {
        // e.g. /catalog/movie/moonstream_movies.json or /catalog/series/moonstream_series.json
        val trimmed = path.removePrefix("/catalog/").removeSuffix(".json")
        val parts = trimmed.split("/")
        val type = if (parts.isNotEmpty()) parts[0] else "movie"

        val response = JSONObject()
        val metas = JSONArray()

        val matchedTitles = fakeTitles.filter {
            if (type == "movie") it.type == "movie" else (it.type == "series" || it.type == "anime")
        }

        for (title in matchedTitles) {
            val item = JSONObject()
            item.put("id", "ms_${title.id}")
            item.put("type", if (title.type == "anime") "series" else title.type)
            item.put("name", title.title)
            item.put("year", title.year)
            item.put("description", title.desc)
            // Stremio poster placeholder or color poster
            item.put("poster", "https://via.placeholder.com/300x450/161922/7DD956?text=" + title.title.replace(" ", "+"))
            metas.put(item)
        }
        response.put("metas", metas)
        return response
    }

    private fun handleMeta(path: String): JSONObject {
        // e.g. /meta/movie/ms_dune2.json
        val trimmed = path.removePrefix("/meta/").removeSuffix(".json")
        val parts = trimmed.split("/")
        val id = if (parts.size >= 2) parts[1].removePrefix("ms_") else ""

        val title = fakeTitles.find { it.id == id }
        val response = JSONObject()

        if (title != null) {
            val meta = JSONObject()
            meta.put("id", "ms_${title.id}")
            meta.put("type", if (title.type == "anime") "series" else title.type)
            meta.put("name", title.title)
            meta.put("year", title.year)
            meta.put("description", title.desc)
            meta.put("poster", "https://via.placeholder.com/300x450/161922/7DD956?text=" + title.title.replace(" ", "+"))

            if (title.type != "movie" && title.seasons.isNotEmpty()) {
                val videos = JSONArray()
                for (season in title.seasons) {
                    for (ep in season.episodes) {
                        val video = JSONObject()
                        video.put("id", "ms_${title.id}:${season.season}:${ep.number}")
                        video.put("title", ep.title)
                        video.put("season", season.season)
                        video.put("episode", ep.number)
                        videos.put(video)
                    }
                }
                meta.put("videos", videos)
            }
            response.put("meta", meta)
        } else {
            response.put("meta", JSONObject())
        }
        return response
    }

    private fun handleStream(path: String): JSONObject {
        // e.g. /stream/movie/ms_dune2.json or /stream/series/ms_frieren:1:1.json
        val trimmed = path.removePrefix("/stream/").removeSuffix(".json")
        val parts = trimmed.split("/")
        val streamId = if (parts.size >= 2) parts[1].removePrefix("ms_") else ""

        val response = JSONObject()
        val streams = JSONArray()

        val titleId = streamId.substringBefore(":")
        val title = fakeTitles.find { it.id == titleId }

        if (title != null) {
            val host = getLocalIpAddress()
            val availableStreams = title.allStreams()
            for (st in availableStreams) {
                val streamObj = JSONObject()
                streamObj.put("name", "MoonStream")
                streamObj.put("title", "${title.title} [${st.quality}] (${st.size})")
                // Local streaming endpoint with direct HTTP link
                streamObj.put("url", "http://$host:8080/stream/file/${title.id}/${st.id}")
                streams.put(streamObj)
            }
        }
        response.put("streams", streams)
        return response
    }

    private fun sendJson(output: OutputStream, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        sendResponse(output, statusCode, if (statusCode == 200) "OK" else "Error", "application/json; charset=utf-8", bytes)
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = StringBuilder()
        header.append("HTTP/1.1 $statusCode $statusText\r\n")
        header.append("Content-Type: $contentType\r\n")
        header.append("Content-Length: ${body.size}\r\n")
        header.append("Access-Control-Allow-Origin: *\r\n")
        header.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        header.append("Access-Control-Allow-Headers: *\r\n")
        header.append("Connection: close\r\n")
        header.append("\r\n")

        output.write(header.toString().toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }
}
