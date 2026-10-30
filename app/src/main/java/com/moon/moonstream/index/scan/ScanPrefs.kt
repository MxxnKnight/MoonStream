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

    fun isAnimeAbsolute(context: Context): Boolean =
        prefs(context).getInt(KEY_ANIME_NUMBERING, 0) == 1

    fun setAnimeNumbering(context: Context, absolute: Boolean) {
        prefs(context).edit().putInt(KEY_ANIME_NUMBERING, if (absolute) 1 else 0).apply()
    }
}
