package com.moon.moonstream.ui.player

import android.content.Context

/**
 * Per-entry remembered subtitle/audio language (moon's pick 2026-11-10).
 *
 * Pick English subs once for episode 1 and every later episode of the same
 * entry opens with English subs — no re-picking each episode. Same for the
 * audio track. Stored as the track's ISO-639 language code ("eng", "mal")
 * keyed by mediaId (all episodes of a series share one entry).
 * Null = nothing remembered → the global Settings language wins.
 * Picking "Off" for subtitles clears the remembered subtitle language.
 */
object TrackLangPrefs {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    fun getSubLang(ctx: Context, mediaId: Long): String? =
        prefs(ctx).getString("track_sub_lang_$mediaId", null)
            ?.takeIf { it.isNotBlank() && it != "und" }

    fun setSubLang(ctx: Context, mediaId: Long, iso: String?) {
        prefs(ctx).edit().apply {
            if (iso.isNullOrBlank() || iso == "und") remove("track_sub_lang_$mediaId")
            else putString("track_sub_lang_$mediaId", iso)
        }.apply()
    }

    fun getAudioLang(ctx: Context, mediaId: Long): String? =
        prefs(ctx).getString("track_audio_lang_$mediaId", null)
            ?.takeIf { it.isNotBlank() && it != "und" }

    fun setAudioLang(ctx: Context, mediaId: Long, iso: String?) {
        prefs(ctx).edit().apply {
            if (iso.isNullOrBlank() || iso == "und") remove("track_audio_lang_$mediaId")
            else putString("track_audio_lang_$mediaId", iso)
        }.apply()
    }
}
