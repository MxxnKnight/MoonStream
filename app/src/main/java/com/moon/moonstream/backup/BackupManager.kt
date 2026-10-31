package com.moon.moonstream.backup

import android.content.Context
import android.provider.Settings
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.state.MsAccent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Tier 1 local backup & restore. Serializes the library (all 6 Room tables)
 * plus safe settings to versioned JSON, gzipped for sharing. Restore MERGES
 * by natural keys — it never wipes local data.
 *
 * Auth/session data (API ID, API hash, session, logged_in flag) is NEVER
 * included. A restore never logs anyone in or out.
 */
object BackupManager {

    const val BACKUP_VERSION = 1

    data class RestoreSummary(
        val channels: Int,
        val media: Int,
        val files: Int,
        val subtitles: Int,
        val settingsApplied: Boolean,
    )

    fun backupFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "MoonStream_backup_${ts}.json.gz"
    }

    // ------------------------------------------------------------ build

    suspend fun buildBackupJson(context: Context): JSONObject {
        val db = MsDatabase.get(context)
        val deviceId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID,
        ) ?: "unknown"

        val settings = JSONObject()
            .put("port", AppUiState.port)
            .put("streamPort", AppUiState.streamPort)
            .put("accent", AppUiState.accent.name)
            .put("amoled", AppUiState.amoled)

        fun arr(items: List<JSONObject>) = JSONArray().apply {
            items.forEach { put(it) }
        }

        return JSONObject()
            .put("version", BACKUP_VERSION)
            .put("app", "MoonStream")
            .put("deviceId", deviceId)
            .put("timestamp", System.currentTimeMillis())
            .put("settings", settings)
            .put("channel_config", arr(db.channelConfigDao().all().map { c ->
                JSONObject()
                    .put("channelId", c.channelId)
                    .put("title", c.title)
                    .put("enabled", c.enabled)
                    .put("isAnime", c.isAnime)
                    .put("removed", c.removed)
                    .put("lastSyncedAt", c.lastSyncedAt)
            }))
            .put("scan_cursors", arr(db.scanCursorDao().all().map { s ->
                JSONObject()
                    .put("channelId", s.channelId)
                    .put("lastMessageId", s.lastMessageId)
            }))
            .put("media_items", arr(db.mediaDao().getAll().map { m ->
                JSONObject()
                    .put("id", m.id)
                    .put("channelId", m.channelId)
                    .put("title", m.title)
                    .put("normalizedTitle", m.normalizedTitle)
                    .put("year", m.year)
                    .put("type", m.type)
                    .put("imdbId", m.imdbId)
                    .put("tmdbId", m.tmdbId)
                    .put("posterUrl", m.posterUrl)
                    .put("overview", m.overview)
                    .put("totalSize", m.totalSize)
                    .put("episodeCount", m.episodeCount)
                    .put("fileCount", m.fileCount)
                    .put("updatedAt", m.updatedAt)
                    .put("hidden", m.hidden)
            }))
            .put("stream_files", arr(db.streamDao().getAll().map { f ->
                JSONObject()
                    .put("id", f.id)
                    .put("mediaId", f.mediaId)
                    .put("channelId", f.channelId)
                    .put("messageId", f.messageId)
                    .put("fileName", f.fileName)
                    .put("fileSize", f.fileSize)
                    .put("season", f.season)
                    .put("episode", f.episode)
                    .put("quality", f.quality)
            }))
            .put("subtitle_files", arr(db.subtitleDao().getAll().map { s ->
                JSONObject()
                    .put("id", s.id)
                    .put("channelId", s.channelId)
                    .put("messageId", s.messageId)
                    .put("fileName", s.fileName)
                    .put("fileSize", s.fileSize)
                    .put("mediaId", s.mediaId)
                    .put("season", s.season)
                    .put("episode", s.episode)
                    .put("language", s.language)
            }))
            .put("unmatched_files", arr(db.unmatchedDao().getAll().map { u ->
                JSONObject()
                    .put("id", u.id)
                    .put("channelId", u.channelId)
                    .put("messageId", u.messageId)
                    .put("fileName", u.fileName)
                    .put("fileSize", u.fileSize)
            }))
    }

    // ------------------------------------------------------------ restore

    suspend fun restore(context: Context, json: JSONObject): RestoreSummary {
        val version = json.optInt("version", -1)
        require(version == BACKUP_VERSION) {
            "Unsupported backup version: $version (this app reads v$BACKUP_VERSION)"
        }
        val db = MsDatabase.get(context)
        var channels = 0
        var media = 0
        var files = 0
        var subtitles = 0

        // Channel configs + cursors: upsert by channelId (primary key).
        json.optJSONArray("channel_config")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.channelConfigDao().upsert(
                    com.moon.moonstream.index.db.ChannelConfigEntity(
                        channelId = o.getLong("channelId"),
                        title = o.optString("title", ""),
                        enabled = o.optBoolean("enabled", true),
                        isAnime = o.optBoolean("isAnime", false),
                        removed = o.optBoolean("removed", false),
                        lastSyncedAt = o.optLong("lastSyncedAt", 0),
                    ),
                )
                channels++
            }
        }
        json.optJSONArray("scan_cursors")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.scanCursorDao().set(
                    com.moon.moonstream.index.db.ScanCursorEntity(
                        channelId = o.getLong("channelId"),
                        lastMessageId = o.getLong("lastMessageId"),
                    ),
                )
            }
        }

        // Media: match by (channelId, normalizedTitle); updatedAt-wins.
        // Remembers old id -> new id so file links can be remapped.
        val mediaIdMap = mutableMapOf<Long, Long>()
        json.optJSONArray("media_items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val channelId = o.getLong("channelId")
                val norm = o.optString("normalizedTitle", "")
                val existing = if (norm.isNotEmpty()) {
                    db.mediaDao().findByTitle(channelId, norm)
                } else null
                val updatedAt = o.optLong("updatedAt", 0)
                if (existing != null && existing.updatedAt >= updatedAt) {
                    mediaIdMap[o.getLong("id")] = existing.id
                    continue
                }
                val entity = com.moon.moonstream.index.db.MediaItemEntity(
                    id = existing?.id ?: 0,
                    channelId = channelId,
                    title = o.optString("title", ""),
                    normalizedTitle = norm,
                    year = o.optString("year", null),
                    type = o.optString("type", "movie"),
                    imdbId = o.optString("imdbId", null),
                    tmdbId = if (o.isNull("tmdbId")) null else o.optInt("tmdbId"),
                    posterUrl = o.optString("posterUrl", null),
                    overview = o.optString("overview", null),
                    totalSize = o.optLong("totalSize", 0),
                    episodeCount = o.optInt("episodeCount", 0),
                    fileCount = o.optInt("fileCount", 0),
                    updatedAt = updatedAt,
                    hidden = o.optBoolean("hidden", false),
                )
                val newId = db.mediaDao().upsert(entity)
                // upsert() with id=0 returns the new row id; with an existing
                // id it returns that id.
                mediaIdMap[o.getLong("id")] = if (existing != null) existing.id else newId
                media++
            }
        }

        // Stream files: skip when (channelId, messageId) already present.
        json.optJSONArray("stream_files")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val channelId = o.getLong("channelId")
                val messageId = o.getLong("messageId")
                if (db.streamDao().exists(channelId, messageId)) continue
                val mediaId = mediaIdMap[o.getLong("mediaId")] ?: continue
                db.streamDao().insert(
                    com.moon.moonstream.index.db.StreamFileEntity(
                        mediaId = mediaId,
                        channelId = channelId,
                        messageId = messageId,
                        fileName = o.optString("fileName", ""),
                        fileSize = o.optLong("fileSize", 0),
                        season = if (o.isNull("season")) null else o.optInt("season"),
                        episode = if (o.isNull("episode")) null else o.optInt("episode"),
                        quality = o.optString("quality", ""),
                    ),
                )
                files++
            }
        }

        // Subtitles: same dedupe; media link remapped, may stay null.
        json.optJSONArray("subtitle_files")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val channelId = o.getLong("channelId")
                val messageId = o.getLong("messageId")
                if (db.subtitleDao().exists(channelId, messageId)) continue
                val rawMediaId = if (o.isNull("mediaId")) null else o.optLong("mediaId")
                db.subtitleDao().insert(
                    com.moon.moonstream.index.db.SubtitleFileEntity(
                        channelId = channelId,
                        messageId = messageId,
                        fileName = o.optString("fileName", ""),
                        fileSize = o.optLong("fileSize", 0),
                        mediaId = rawMediaId?.let { mediaIdMap[it] },
                        season = if (o.isNull("season")) null else o.optInt("season"),
                        episode = if (o.isNull("episode")) null else o.optInt("episode"),
                        language = o.optString("language", "unknown"),
                    ),
                )
                subtitles++
            }
        }

        // Unmatched: insert-ignore handles dupes.
        json.optJSONArray("unmatched_files")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.unmatchedDao().insert(
                    com.moon.moonstream.index.db.UnmatchedFileEntity(
                        channelId = o.getLong("channelId"),
                        messageId = o.getLong("messageId"),
                        fileName = o.optString("fileName", ""),
                        fileSize = o.optLong("fileSize", 0),
                    ),
                )
            }
        }

        // Settings: apply field-by-field; never touches auth/session.
        var settingsApplied = false
        json.optJSONObject("settings")?.let { s ->
            if (s.has("port")) {
                val p = s.optInt("port", AppUiState.port)
                if (p in 1..65535) AppUiState.port = p
            }
            if (s.has("streamPort")) {
                val p = s.optInt("streamPort", AppUiState.streamPort)
                if (p in 1..65535) AppUiState.streamPort = p
            }
            if (s.has("accent")) {
                MsAccent.values().find { it.name == s.optString("accent") }
                    ?.let { AppUiState.setAccent(it, context) }
            }
            if (s.has("amoled")) AppUiState.setAmoled(s.optBoolean("amoled"), context)
            settingsApplied = true
        }

        return RestoreSummary(channels, media, files, subtitles, settingsApplied)
    }

    // ------------------------------------------------------------ gzip

    fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    fun gunzip(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    /** Accepts raw gzip bytes or plain JSON (detects via magic bytes). */
    fun parseBackup(data: ByteArray): JSONObject {
        val jsonBytes = if (data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            gunzip(data)
        } else {
            data
        }
        return JSONObject(jsonBytes.toString(Charsets.UTF_8))
    }
}
