package com.moon.moonstream.util

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Anime Skip — free community database of anime opening/ending timestamps.
 * https://anime-skip.com — reads need a free client id (create an account,
 * then API Clients at anime-skip.com/account/api-clients). The id is sent
 * as the X-Client-ID header, NOT as a GraphQL variable.
 *
 * Fallback for anime when AniSkip has no data for an episode. The show is
 * resolved by AniList id (the app already saves it at index time), then
 * the episode is matched by season + number (falling back to the absolute
 * episode number), then that episode's timestamps are fetched.
 *
 * Timestamps are single points, not ranges: a segment starting at one
 * marker ends where the NEXT marker starts, so consecutive markers are
 * paired into intervals (the same pairing Kari and mpvRx use).
 */
object AnimeSkip {
    private const val TAG = "AnimeSkip"
    private const val BASE = "https://api.anime-skip.com/graphql"

    data class SkipTimes(
        /** Opening start/end in seconds, null if not in DB. */
        val opStart: Double?,
        val opEnd: Double?,
        /** Ending start/end in seconds, null if not in DB. */
        val edStart: Double?,
        val edEnd: Double?,
        /** Recap ("previously on") start/end in seconds, null if not in DB. */
        val recapStart: Double?,
        val recapEnd: Double?,
        /** "Next time" preview start/end in seconds, null if not in DB. */
        val previewStart: Double?,
        val previewEnd: Double?,
    )

    /**
     * Fetches skip times for an anime episode.
     * @param anilistId AniList anime ID (mapped from Kitsu at index time)
     * @param season Season number (may be null for single-season anime)
     * @param episode Episode number (1-based)
     * @param clientId The user's Anime Skip client id (Settings → Metadata)
     * @return SkipTimes or null if not found / on error / no client id.
     */
    suspend fun getSkipTimes(
        anilistId: String,
        season: Int?,
        episode: Int,
        clientId: String,
    ): SkipTimes? = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext null
        try {
            // 1. Resolve the show by AniList id.
            val showId = findShowIdByAnilistId(anilistId, clientId) ?: run {
                AppLog.i(TAG, "AnimeSkip: no show for anilist=$anilistId")
                return@withContext null
            }
            // 2. List the show's episodes and match by season + number
            // (both are strings in the API), falling back to the
            // absolute episode number.
            val episodeId = findEpisodeId(showId, season, episode, clientId) ?: run {
                AppLog.i(TAG, "AnimeSkip: no episode S$season E$episode for show $showId")
                return@withContext null
            }
            // 3. Fetch that episode's timestamps and pair consecutive
            // markers into intervals.
            val times = findTimestamps(episodeId, clientId) ?: return@withContext null
            AppLog.i(TAG, "AnimeSkip: op=${times.opStart}-${times.opEnd} ed=${times.edStart}-${times.edEnd} recap=${times.recapStart}-${times.recapEnd} preview=${times.previewStart}-${times.previewEnd}")
            times
        } catch (e: Exception) {
            AppLog.w(TAG, "AnimeSkip failed: ${e.message}")
            null
        }
    }

    /** POSTs one GraphQL query and returns its `data` object, or null. */
    private fun postGraphql(query: String, variables: JSONObject, clientId: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val body = JSONObject().apply {
                put("query", query)
                put("variables", variables)
            }.toString()
            conn = (URL(BASE).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "MoonStream")
                setRequestProperty("X-Client-ID", clientId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body) }
            if (conn.responseCode != 200) {
                AppLog.w(TAG, "AnimeSkip HTTP ${conn.responseCode}")
                return null
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            json.optJSONObject("data")
        } catch (e: Exception) {
            AppLog.w(TAG, "AnimeSkip GraphQL failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun findShowIdByAnilistId(anilistId: String, clientId: String): String? {
        val query = "query(\$id: String!) { findShowsByExternalId(service: ANILIST, serviceId: \$id) { id } }"
        val variables = JSONObject().apply { put("id", anilistId) }
        val data = postGraphql(query, variables, clientId) ?: return null
        // findShowsByExternalId returns a list of shows (or a single show).
        val arr = data.optJSONArray("findShowsByExternalId")
        if (arr != null && arr.length() > 0) {
            return arr.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }
        return data.optJSONObject("findShowsByExternalId")?.optString("id")?.takeIf { it.isNotBlank() }
    }

    private fun findEpisodeId(showId: String, season: Int?, episode: Int, clientId: String): String? {
        val query = "query(\$showId: ID!) { findEpisodesByShowId(showId: \$showId) { id season number absoluteNumber } }"
        val variables = JSONObject().apply { put("showId", showId) }
        val data = postGraphql(query, variables, clientId) ?: return null
        val arr = data.optJSONArray("findEpisodesByShowId") ?: return null
        val seasonStr = season?.toString()
        val epStr = episode.toString()
        // Prefer the season-relative number.
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val num = e.optString("number")
            val seas = e.optString("season")
            if (num == epStr && (seasonStr == null || seas == seasonStr)) {
                return e.optString("id").takeIf { it.isNotBlank() }
            }
        }
        // Fall back to the absolute episode number.
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optString("absoluteNumber") == epStr) {
                return e.optString("id").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun findTimestamps(episodeId: String, clientId: String): SkipTimes? {
        val query = "query(\$epId: ID!) { findTimestampsByEpisodeId(episodeId: \$epId) { at type { name } } }"
        val variables = JSONObject().apply { put("epId", episodeId) }
        val data = postGraphql(query, variables, clientId) ?: return null
        val arr = data.optJSONArray("findTimestampsByEpisodeId") ?: return null
        // Pair consecutive markers into intervals: a segment starting at
        // marker[i] ends where marker[i+1] starts. Sort by time first.
        val markers = mutableListOf<Pair<Double, String>>()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val at = t.optDouble("at", -1.0)
            if (at < 0) continue
            val typeName = t.optJSONObject("type")?.optString("name") ?: continue
            markers += at to typeName
        }
        markers.sortBy { it.first }
        var opStart: Double? = null
        var opEnd: Double? = null
        var edStart: Double? = null
        var edEnd: Double? = null
        var recapStart: Double? = null
        var recapEnd: Double? = null
        var previewStart: Double? = null
        var previewEnd: Double? = null
        for (i in markers.indices) {
            val (at, typeName) = markers[i]
            // The last marker has no next marker, so it has no end.
            val end = markers.getOrNull(i + 1)?.first ?: continue
            when (typeName) {
                "Intro", "New Intro", "Mixed Intro", "Opening", "OP" -> {
                    if (opStart == null) { opStart = at; opEnd = end }
                }
                "Credits", "New Credits", "Mixed Credits", "Ending", "Outro", "ED" -> {
                    if (edStart == null) { edStart = at; edEnd = end }
                }
                // Recap / Preview markers pair the same way — the player
                // shows them in the same Skip button (moon's 2026-11-10).
                "Recap" -> {
                    if (recapStart == null) { recapStart = at; recapEnd = end }
                }
                "Preview" -> {
                    if (previewStart == null) { previewStart = at; previewEnd = end }
                }
                else -> {}
            }
        }
        if (opStart == null && edStart == null && recapStart == null && previewStart == null) return null
        return SkipTimes(opStart, opEnd, edStart, edEnd, recapStart, recapEnd, previewStart, previewEnd)
    }
}
