package com.moon.moonstream.ui.state

import android.content.Context

/**
 * Real persistence for Settings toggles/fields. Replaces the old fakeSaved()
 * toasts — every setting now actually saves and restores.
 */
object SettingsPrefs {
    private const val PREFS = "moonstream_settings"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBool(context: Context, key: String, default: Boolean): Boolean =
        prefs(context).getBoolean(key, default)

    fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun getString(context: Context, key: String, default: String): String =
        prefs(context).getString(key, default) ?: default

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun getInt(context: Context, key: String, default: Int): Int =
        prefs(context).getInt(key, default)

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }
}
