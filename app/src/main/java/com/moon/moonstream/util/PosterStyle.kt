package com.moon.moonstream.util

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Poster providers (moon's 2026-11-06: FREE providers only — RPDB was
 * removed from Settings because its API key needs a paid Patreon tier).
 *
 * Two different mechanisms, because the providers work differently:
 * - BetterPosters (btttr.cc) is a pure URL build from the IMDb id — no key,
 *   no network call. Applied at DISPLAY time (library, detail, server rows,
 *   Stremio addon), so switching to it changes every existing poster
 *   instantly.
 * - Fanart.tv needs an API call per title (free personal key, movies by
 *   TMDB id), so it's resolved at MATCH time in the scanner and stored as
 *   the entry's posterUrl. Switching to Fanart only changes NEWLY scanned
 *   titles; a full rescan refreshes the rest. Series fall back to the
 *   provider poster — Fanart's TV endpoint needs a TVDB id, which we don't
 *   store.
 */
object PosterStyle {
    const val PROVIDER = 0
    const val BETTER_POSTERS = 1
    const val FANART = 2

    /** BetterPosters poster URL — free, keyless, IMDb id only. With a custom
     *  style address (moon's 2026-11-06, from btttr.cc/configure), the IMDb id
     *  is swapped into its {imdb_id} placeholder; otherwise the default look.
     *  Accepts {imdb_id} / {imdbId}, raw or percent-encoded (iOS clipboard
     *  can encode the braces). Falls back to the default on any bad input. */
    fun betterPostersUrl(imdbId: String?, template: String? = null): String? {
        val id = imdbId?.takeIf { it.startsWith("tt") } ?: return null
        val tpl = template?.trim().orEmpty()
        if (tpl.isNotEmpty() && (tpl.startsWith("http://") || tpl.startsWith("https://"))) {
            val decoded = normalizeTemplate(tpl)
            if (decoded.contains("{imdb_id}")) {
                return decoded.replace("{imdb_id}", id)
            }
        }
        return "https://btttr.cc/poster/imdb/poster-default/$id.jpg"
    }

    /** Blank = use the default look; otherwise needs an http(s) address with
     *  the {imdb_id} placeholder in it. */
    fun isValidTemplate(template: String?): Boolean {
        val t = template?.trim().orEmpty()
        if (t.isEmpty()) return true
        if (!t.startsWith("http://") && !t.startsWith("https://")) return false
        return normalizeTemplate(t).contains("{imdb_id}")
    }

    private fun normalizeTemplate(tpl: String): String =
        tpl
            .replace("%7Bimdb_id%7D", "{imdb_id}", ignoreCase = true)
            .replace("%7BimdbId%7D", "{imdb_id}", ignoreCase = true)
            .replace("{imdbId}", "{imdb_id}")

    /**
     * Effective poster for display: BetterPosters rewrites from the IMDb id
     * (falling back to the stored poster for titles without one); every
     * other style shows the stored poster as-is.
     */
    fun displayUrl(
        storedPosterUrl: String?,
        imdbId: String?,
        style: Int,
        template: String? = null,
    ): String? {
        if (style == BETTER_POSTERS) {
            return betterPostersUrl(imdbId, template) ?: storedPosterUrl
        }
        return storedPosterUrl
    }

    // ---- Fanart.tv (match time) ----

    @Volatile
    private var lastFanartCallMs = 0L

    /**
     * Poster to STORE for a newly matched entry. Fanart only for movies
     * with a TMDB id and a saved key; anything else (or any failure) keeps
     * the provider poster. [cache] is per-scan-run, keyed by TMDB id.
     */
    suspend fun resolveForStore(
        providerPosterUrl: String?,
        imdbId: String?,
        tmdbId: Int?,
        type: String,
        style: Int,
        fanartKey: String,
        cache: MutableMap<Int, String?>,
    ): String? {
        if (style != FANART || type != "movie" || tmdbId == null || fanartKey.isBlank()) {
            return providerPosterUrl
        }
        return fetchFanartPoster(tmdbId, fanartKey, cache) ?: providerPosterUrl
    }

    /**
     * One Fanart.tv movie-poster lookup. Picks the best poster: English
     * first, then the most-liked. Throttled to ~2 requests/second (the
     * personal-key limit) and cached per run. Never throws — null on any
     * failure, so a Fanart outage never breaks a scan.
     */
    suspend fun fetchFanartPoster(
        tmdbId: Int,
        apiKey: String,
        cache: MutableMap<Int, String?>,
    ): String? {
        if (cache.containsKey(tmdbId)) return cache[tmdbId]
        val now = System.currentTimeMillis()
        val waitMs = 600L - (now - lastFanartCallMs)
        if (waitMs > 0) delay(waitMs)
        lastFanartCallMs = System.currentTimeMillis()
        val result = runCatching {
            val url =
                "https://webservice.fanart.tv/v3/movies/$tmdbId?api_key=$apiKey"
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val posters = JSONObject(body).optJSONArray("movieposter") ?: return@runCatching null
                var bestUrl: String? = null
                var bestLikes = -1
                var bestEnUrl: String? = null
                var bestEnLikes = -1
                for (i in 0 until posters.length()) {
                    val p = posters.optJSONObject(i) ?: continue
                    val u = p.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val likes = p.optString("likes").toIntOrNull() ?: 0
                    if (likes > bestLikes) {
                        bestLikes = likes
                        bestUrl = u
                    }
                    if (p.optString("lang") == "en" && likes > bestEnLikes) {
                        bestEnLikes = likes
                        bestEnUrl = u
                    }
                }
                bestEnUrl ?: bestUrl
            } finally {
                conn?.disconnect()
            }
        }.getOrNull()
        cache[tmdbId] = result
        return result
    }
}
