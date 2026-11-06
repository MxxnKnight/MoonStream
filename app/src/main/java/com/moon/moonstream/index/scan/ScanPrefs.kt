package com.moon.moonstream.index.scan

import android.content.Context

/**
 * Phase 3 settings persistence. Lives in the same SharedPreferences file the
 * rest of the app uses ("moonstream_prefs") so Settings and the scanner
 * always agree.
 */
object ScanPrefs {
    private const val PREFS = "moonstream_prefs"
    private const val KEY_TMDB = "tmdb_key"
    private const val KEY_TVDB = "tvdb_key"
    private const val KEY_ANIME_NUMBERING = "anime_numbering" // 0 = seasonal, 1 = absolute
    private const val KEY_AUTO_SCAN = "auto_scan_on_open" // scan on app open (moon's pick 2026-11-06)
    // Poster style (moon's 2026-11-06 — free providers only, RPDB removed):
    // 0 = Provider (TMDB / Cinemeta / Kitsu), 1 = BetterPosters (btttr.cc,
    // free, no key, IMDb id only), 2 = Fanart.tv (free key required).
    private const val KEY_POSTER_STYLE = "poster_style"
    private const val KEY_FANART = "fanart_key"
    // Custom BetterPosters style address (moon's 2026-11-06): a btttr.cc URL
    // pattern with {imdb_id} in it, e.g. from btttr.cc/configure.
    private const val KEY_POSTER_TEMPLATE = "poster_template"
    // GitHub Gist second backup (moon's 2026-11-06): personal access token
    // with the `gist` scope (created at github.com/settings/tokens) and the
    // id of the private gist the backups live in.
    private const val KEY_GIST_TOKEN = "gist_token"
    private const val KEY_GIST_ID = "gist_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getTmdbKey(context: Context): String =
        prefs(context).getString(KEY_TMDB, "") ?: ""

    fun setTmdbKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_TMDB, value.trim()).apply()
    }

    fun getTvdbKey(context: Context): String =
        prefs(context).getString(KEY_TVDB, "") ?: ""

    fun setTvdbKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_TVDB, value.trim()).apply()
    }

    fun getPosterStyle(context: Context): Int =
        prefs(context).getInt(KEY_POSTER_STYLE, 0)

    fun setPosterStyle(context: Context, style: Int) {
        prefs(context).edit().putInt(KEY_POSTER_STYLE, style).apply()
    }

    /** Custom BetterPosters style address ({imdb_id} pattern), or "" for default. */
    fun getPosterTemplate(context: Context): String =
        prefs(context).getString(KEY_POSTER_TEMPLATE, "") ?: ""

    fun setPosterTemplate(context: Context, value: String) {
        prefs(context).edit().putString(KEY_POSTER_TEMPLATE, value.trim()).apply()
    }

    /** Fanart.tv personal API key (free, from fanart.tv/get-an-api-key). */
    fun getFanartKey(context: Context): String =
        prefs(context).getString(KEY_FANART, "") ?: ""

    fun setFanartKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_FANART, key.trim()).apply()
    }

    /** GitHub personal access token with the `gist` scope, or "". */
    fun getGistToken(context: Context): String =
        prefs(context).getString(KEY_GIST_TOKEN, "") ?: ""

    fun setGistToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_GIST_TOKEN, token.trim()).apply()
    }

    /** Id of the private gist holding the backups, or "" before the first push. */
    fun getGistId(context: Context): String =
        prefs(context).getString(KEY_GIST_ID, "") ?: ""

    fun setGistId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_GIST_ID, id.trim()).apply()
    }

    fun isAnimeAbsolute(context: Context): Boolean =
        prefs(context).getInt(KEY_ANIME_NUMBERING, 0) == 1

    fun setAnimeNumbering(context: Context, absolute: Boolean) {
        prefs(context).edit().putInt(KEY_ANIME_NUMBERING, if (absolute) 1 else 0).apply()
    }

    fun isAutoScan(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SCAN, true)

    fun setAutoScan(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SCAN, value).apply()
    }
}
