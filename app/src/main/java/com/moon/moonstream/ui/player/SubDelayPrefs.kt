package com.moon.moonstream.ui.player

import android.content.Context

/**
 * Per-entry subtitle delay (ms), set from the player's Subs sheet.
 * The server shifts SRT/VTT cue timestamps when ?delayMs= is present.
 */
object SubDelayPrefs {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    fun get(ctx: Context, mediaId: Long): Long =
        prefs(ctx).getLong("sub_delay_$mediaId", 0L)

    fun set(ctx: Context, mediaId: Long, ms: Long) {
        prefs(ctx).edit().putLong("sub_delay_$mediaId", ms).apply()
    }
}
