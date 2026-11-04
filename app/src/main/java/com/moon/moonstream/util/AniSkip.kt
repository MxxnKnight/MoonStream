package com.moon.moonstream.util

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AniSkip — free community database of anime opening/ending timestamps.
 * https://api.aniskip.com
 *
 * No API key needed. Rate limits are generous for personal use.
 */
object AniSkip {
    private const val TAG = "AniSkip"
    private const val BASE = "https://api.aniskip.com/v2/skip-times"

    data class SkipTimes(
        /** Opening start/end in seconds, null if not in DB. */
        val opStart: Double?,
        val opEnd: Double?,
        /** Ending start/end in seconds, null if not in DB. */
        val edStart: Double?,
        val edEnd: Double?,
    )

    /**
     * Fetches skip times for an anime episode.
     * @param anilistId AniList anime ID (mapped from Kitsu at index time)
     * @param episode Episode number (1-based)
     * @param episodeLength Episode length in seconds (for the API)
     * @return SkipTimes or null if not found / on error.
     */
    suspend fun getSkipTimes(
        anilistId: String,
        episode: Int,
        episodeLength: Long,
    ): SkipTimes? = withContext(Dispatchers.IO) {
        try {
            // AniSkip v2 real route: /v2/skip-times/{anilistId}/{episode}
            // with types[] and episodeLength query params.
            val url = "$BASE/$anilistId/$episode" +
                "?types[]=op&types[]=ed&episodeLength=${episodeLength.coerceAtLeast(1)}"
            AppLog.i(TAG, "Fetching: $url")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "MoonStream")
            }
            try {
                if (conn.responseCode != 200) {
                    AppLog.w(TAG, "AniSkip HTTP ${conn.responseCode} for anilist=$anilistId ep=$episode")
                    return@withContext null
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                if (json.optBoolean("found", false).not()) {
                    AppLog.i(TAG, "AniSkip: no data for anilist=$anilistId ep=$episode")
                    return@withContext null
                }
                val results = json.optJSONArray("results") ?: return@withContext null
                var opStart: Double? = null
                var opEnd: Double? = null
                var edStart: Double? = null
                var edEnd: Double? = null
                for (i in 0 until results.length()) {
                    val r = results.optJSONObject(i) ?: continue
                    // Real API uses camelCase: skipType, interval.startTime/endTime.
                    val type = r.optString("skipType", "")
                    val interval = r.optJSONObject("interval") ?: continue
                    val start = interval.optDouble("startTime", -1.0)
                    val end = interval.optDouble("endTime", -1.0)
                    if (start < 0 || end < 0) continue
                    when (type) {
                        "op" -> { opStart = start; opEnd = end }
                        "ed" -> { edStart = start; edEnd = end }
                    }
                }
                AppLog.i(TAG, "AniSkip: op=$opStart-$opEnd ed=$edStart-$edEnd")
                SkipTimes(opStart, opEnd, edStart, edEnd)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "AniSkip failed: ${e.message}")
            null
        }
    }
}
