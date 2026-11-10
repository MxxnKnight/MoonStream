package com.moon.moonstream.backup

import android.content.Context
import android.provider.Settings
import com.moon.moonstream.index.db.MsDatabase
import com.moon.moonstream.index.StremioIds
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

    /** SHA-256 of the last successfully pushed backup's content. */
    const val PREF_LAST_BACKUP_HASH = "last_backup_hash"

    /**
     * Stable hash of a backup's CONTENT (the "timestamp" field is excluded —
     * it changes on every build even when nothing else did, which would
     * defeat the changed-only auto-backup gate).
     */
    fun contentHash(json: JSONObject): String {
        val copy = JSONObject(json.toString())
        copy.remove("timestamp")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(copy.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Result of a push: the new message id (null = upload failed) and
     *  whether the new file was verified present in Saved Messages. */
    data class PushResult(val messageId: Long?, val verified: Boolean)

    /**
     * Push a backup, replacing the old ones (moon's 2026-11-06: Saved
     * Messages was flooding — one new file per scan). Finds the existing
     * backup messages FIRST, uploads the new file, then deletes the old
     * messages — BUT only after verifying the new file is actually in Saved
     * Messages (moon's 2026-11-06: uploads reported success yet no file was
     * visible, and the old safety net was already gone). If the new file
     * can't be confirmed, the old backups are KEPT and verified=false.
     * Delete failures are logged, never fatal.
     */
    suspend fun uploadBackupReplacingOld(
        backend: com.moon.moonstream.backend.MoonStreamBackend,
        gz: ByteArray,
        fileName: String,
    ): PushResult {
        val log = com.moon.moonstream.util.AppLog
        val oldRefs = runCatching { backend.findAllBackups() }.getOrDefault(emptyList())
        log.i("BACKUP", "Push: found ${oldRefs.size} old backup(s) in Saved Messages")
        val msgId = backend.uploadBackup(gz, fileName)
        if (msgId == null) {
            log.e("BACKUP", "Push: upload failed")
            return PushResult(null, false)
        }
        log.i("BACKUP", "Push: uploaded new backup msg $msgId, verifying it is in Saved Messages")
        // Verify by direct fetch first (moon's 2026-11-07 fix): the old
        // text-search check missed the just-sent message every time,
        // because a new message isn't in Telegram's search index yet.
        // GetMessage looks it up directly, so it works immediately.
        // The text search stays as a fallback.
        val direct = runCatching { backend.getBackupMessage(msgId) }.getOrNull()
        val latest = if (direct?.messageId == msgId) direct
        else runCatching { backend.findLatestBackup() }.getOrNull()
        if (latest?.messageId != msgId) {
            log.e(
                "BACKUP",
                "Push: new backup msg $msgId NOT found in Saved Messages " +
                    "(latest is ${latest?.messageId}) — keeping ${oldRefs.size} old backup(s)",
            )
            return PushResult(msgId, false)
        }
        var deleted = 0
        for (ref in oldRefs) {
            if (runCatching { backend.deleteBackup(ref) }.getOrDefault(false)) {
                deleted++
            } else {
                log.w("BACKUP", "Push: could not delete old backup msg ${ref.messageId}")
            }
        }
        log.i("BACKUP", "Push: verified msg $msgId, deleted $deleted old backup(s)")
        return PushResult(msgId, true)
    }

    data class RestoreSummary(
        val channels: Int,
        val media: Int,
        val files: Int,
        val subtitles: Int,
        val progress: Int,
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
                    .put("manualOnly", c.manualOnly)
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
                    .put("kitsuId", m.kitsuId)
                    .put("anilistId", m.anilistId)
                    .put("stremioId", m.stremioId)
                    .put("posterUrl", m.posterUrl)
                    .put("backdropUrl", m.backdropUrl)
                    .put("overview", m.overview)
                    .put("totalSize", m.totalSize)
                    .put("episodeCount", m.episodeCount)
                    .put("fileCount", m.fileCount)
                    .put("updatedAt", m.updatedAt)
                    .put("hidden", m.hidden)
                    .put("isCustom", m.isCustom)
                    .put("genres", m.genres)
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
                    .put("episodeEnd", f.episodeEnd)
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
            .put("watch_progress", arr(db.watchProgressDao().getAll().map { w ->
                JSONObject()
                    .put("mediaId", w.mediaId)
                    .put("streamFileId", w.streamFileId)
                    .put("title", w.title)
                    .put("episodeLabel", w.episodeLabel)
                    .put("posterUrl", w.posterUrl)
                    .put("playedAt", w.playedAt)
                    .put("positionMs", w.positionMs)
                    .put("durationMs", w.durationMs)
            }))
            .put("unmatched_files", arr(db.unmatchedDao().getAll().map { u ->
                JSONObject()
                    .put("id", u.id)
                    .put("channelId", u.channelId)
                    .put("messageId", u.messageId)
                    .put("fileName", u.fileName)
                    .put("fileSize", u.fileSize)
                    .put("caption", u.caption)
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
                        manualOnly = o.optBoolean("manualOnly", false),
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
                    kitsuId = o.optString("kitsuId", null),
                    anilistId = o.optString("anilistId", null),
                    // Preserved from the backup when present; old backups
                    // (or hand-edited ones) fall back to the deterministic
                    // synthetic ID — never null after this point.
                    stremioId = o.optString("stremioId", null)
                        ?: StremioIds.forNewItem(
                            o.getLong("channelId"),
                            o.optString("normalizedTitle", o.optString("title", "")),
                            o.optString("imdbId", null),
                        ),
                    posterUrl = o.optString("posterUrl", null),
                    backdropUrl = o.optString("backdropUrl", null),
                    overview = o.optString("overview", null),
                    totalSize = o.optLong("totalSize", 0),
                    episodeCount = o.optInt("episodeCount", 0),
                    fileCount = o.optInt("fileCount", 0),
                    updatedAt = updatedAt,
                    hidden = o.optBoolean("hidden", false),
                    isCustom = o.optBoolean("isCustom", false),
                    genres = o.optString("genres", null),
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
                        episodeEnd = if (o.isNull("episodeEnd")) null else o.optInt("episodeEnd"),
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

        // Watch progress (resume spots, moon's 2026-11-06): remap the media
        // id through mediaIdMap; when a row already exists for the same file,
        // the newer playedAt wins — a restore never rewinds a newer spot.
        var progress = 0
        json.optJSONArray("watch_progress")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val mediaId = mediaIdMap[o.getLong("mediaId")] ?: continue
                val streamFileId = o.getLong("streamFileId")
                val playedAt = o.optLong("playedAt", 0)
                val existing = db.watchProgressDao().get(mediaId, streamFileId)
                if (existing != null && existing.playedAt >= playedAt) continue
                db.watchProgressDao().upsert(
                    com.moon.moonstream.index.db.WatchProgressEntity(
                        id = existing?.id ?: 0,
                        mediaId = mediaId,
                        streamFileId = streamFileId,
                        title = o.optString("title", ""),
                        episodeLabel = o.optString("episodeLabel", null),
                        posterUrl = o.optString("posterUrl", null),
                        playedAt = playedAt,
                        positionMs = o.optLong("positionMs", 0),
                        durationMs = o.optLong("durationMs", 0),
                    ),
                )
                progress++
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
                        caption = o.optString("caption", ""),
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

        return RestoreSummary(channels, media, files, subtitles, progress, settingsApplied)
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
