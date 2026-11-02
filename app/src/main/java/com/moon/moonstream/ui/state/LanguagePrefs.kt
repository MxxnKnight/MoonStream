package com.moon.moonstream.ui.state

import android.content.Context

/**
 * First/second language preference for the in-app player.
 *
 * Stored with the same short labels the indexer writes into the DB ("Mal",
 * "Eng", …) so a preference can be compared against a subtitle row directly.
 * The player asks for the first choice, falls back to the second when the
 * first isn't available, and only then leaves tracks to the file.
 */
object LanguagePrefs {

    /** Languages worth offering in Settings (matches FilenameParser's map). */
    val CHOICES = listOf("Mal", "Eng", "Hin", "Tam", "Tel", "Kan", "Jap", "Kor")

    /** Meaning "no preference" in a slot. */
    const val NONE = "None"

    private const val KEY_SUB_FIRST = "lang_sub_first"
    private const val KEY_SUB_SECOND = "lang_sub_second"
    private const val KEY_AUDIO_FIRST = "lang_audio_first"
    private const val KEY_AUDIO_SECOND = "lang_audio_second"

    fun subtitleFirst(context: Context): String =
        SettingsPrefs.getString(context, KEY_SUB_FIRST, "Mal")

    fun subtitleSecond(context: Context): String =
        SettingsPrefs.getString(context, KEY_SUB_SECOND, "Eng")

    fun audioFirst(context: Context): String =
        SettingsPrefs.getString(context, KEY_AUDIO_FIRST, "Mal")

    fun audioSecond(context: Context): String =
        SettingsPrefs.getString(context, KEY_AUDIO_SECOND, "Eng")

    fun setSubtitleFirst(context: Context, label: String) =
        SettingsPrefs.setString(context, KEY_SUB_FIRST, label)

    fun setSubtitleSecond(context: Context, label: String) =
        SettingsPrefs.setString(context, KEY_SUB_SECOND, label)

    fun setAudioFirst(context: Context, label: String) =
        SettingsPrefs.setString(context, KEY_AUDIO_FIRST, label)

    fun setAudioSecond(context: Context, label: String) =
        SettingsPrefs.setString(context, KEY_AUDIO_SECOND, label)

    /** ISO-639-2 code Media3 understands, or null for "no preference". */
    private val ISO = mapOf(
        "Mal" to "mal",
        "Eng" to "eng",
        "Hin" to "hin",
        "Tam" to "tam",
        "Tel" to "tel",
        "Kan" to "kan",
        "Jap" to "jpn",
        "Kor" to "kor",
    )

    fun iso(label: String): String? = ISO[label]

    /**
     * True when an indexed value belongs to [label]. Handles "Mal"/"mal",
     * "malayalam" and mismatched casing without a lookup table per call.
     */
    fun matches(dbValue: String, label: String): Boolean {
        if (label.isBlank() || label == NONE) return false
        val a = dbValue.trim().lowercase()
        val b = label.trim().lowercase()
        if (a.isEmpty() || a == "unknown") return false
        if (a == b) return true
        if (a.length >= 3 && b.length >= 3 && a.take(3) == b.take(3)) return true
        return a.startsWith(b) || b.startsWith(a)
    }

    /** First choice actually present in [languages], or null when neither is. */
    fun chooseSubtitle(languages: List<String>, first: String, second: String): String? =
        when {
            first != NONE && languages.any { matches(it, first) } -> first
            second != NONE && languages.any { matches(it, second) } -> second
            else -> null
        }
}
