package com.moon.moonstream.ui.player

import android.content.Context
import android.graphics.Color

/**
 * Per-entry subtitle prefs, set from the player's Subs sheet.
 * Delay: the server shifts SRT/VTT cue timestamps when ?delayMs= is present.
 * Appearance: applied to the player's SubtitleView (moon's pick 2026-11-02 —
 * all subtitle settings live in the player).
 */
object SubDelayPrefs {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    fun get(ctx: Context, mediaId: Long): Long =
        prefs(ctx).getLong("sub_delay_$mediaId", 0L)

    fun set(ctx: Context, mediaId: Long, ms: Long) {
        prefs(ctx).edit().putLong("sub_delay_$mediaId", ms).apply()
    }

    fun getTextSize(ctx: Context, mediaId: Long): Float =
        prefs(ctx).getFloat("sub_size_$mediaId", 0.06f)

    fun setTextSize(ctx: Context, mediaId: Long, v: Float) {
        prefs(ctx).edit().putFloat("sub_size_$mediaId", v).apply()
    }

    fun getStrokeOn(ctx: Context, mediaId: Long): Boolean =
        prefs(ctx).getBoolean("sub_stroke_$mediaId", true)

    fun setStrokeOn(ctx: Context, mediaId: Long, on: Boolean) {
        prefs(ctx).edit().putBoolean("sub_stroke_$mediaId", on).apply()
    }

    fun getTextColor(ctx: Context, mediaId: Long): Int =
        prefs(ctx).getInt("sub_text_color_$mediaId", Color.WHITE)

    fun setTextColor(ctx: Context, mediaId: Long, color: Int) {
        prefs(ctx).edit().putInt("sub_text_color_$mediaId", color).apply()
    }

    fun getStrokeColor(ctx: Context, mediaId: Long): Int =
        prefs(ctx).getInt("sub_stroke_color_$mediaId", Color.BLACK)

    fun setStrokeColor(ctx: Context, mediaId: Long, color: Int) {
        prefs(ctx).edit().putInt("sub_stroke_color_$mediaId", color).apply()
    }

    /**
     * Subtitle vertical position as bottom-padding fraction (0f = at the
     * very bottom, 0.4f = 40% up). Fixes subtitles being cropped when the
     * video is zoomed (moon's pick 2026-11-02).
     */
    fun getPosition(ctx: Context, mediaId: Long): Float =
        prefs(ctx).getFloat("sub_pos_$mediaId", 0.08f)

    fun setPosition(ctx: Context, mediaId: Long, v: Float) {
        prefs(ctx).edit().putFloat("sub_pos_$mediaId", v).apply()
    }
}
