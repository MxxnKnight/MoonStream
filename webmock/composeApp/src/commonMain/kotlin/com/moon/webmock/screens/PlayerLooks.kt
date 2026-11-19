package com.moon.webmock.screens

// ---------------------------------------------------------------------------
// Player looks (moon's 2026-11-19): three full player DESIGNS — the
// approved Current one, plus one in Stremio's style and one in Nuvio's,
// built from moon's own direction ("I want stremio kinda and nuvio kinda
// separate ui's"). Each look is a full UI in that app's style — its own
// arrangement AND its own button treatments, icons and seekbar —
// grounded in the real apps' open-source player code (stremio-native /
// stremio-android PlayerControls.kt, NuvioMedia / NuvioMobile
// PlayerControls.kt + PlayerTimeline.kt). The 13 FUNCTIONS never change
// (Back, Episodes, Playlist, Screenshot, Trim, Lock, -10s, Play/Pause,
// +10s, Subtitles, Audio, Next episode, Aspect ratio) — no speed, no
// PiP, no extra buttons — and no look gets a seekbar thumb dot.
// The look is a plain parameter to PlayerScreen, so switching looks
// never resets playback state (it lives in PlayerLandscapeMock's
// remember). Sheets, drawers, trimmer, NextUp and the unlock slider are
// identical in every look — only the main control layer is redesigned.
// History: round 1 (Current/Air/Bar) only restyled one layout — moon
// said they were all the same. Round 2 (Current/Tube/Rail) moved the
// controls instead — moon said he meant APPEARANCE, not arrangement.
// Round 3 is this file: real app styles, Tube/Rail deleted (git keeps
// them). Webmock only — nothing here goes into the app until he picks.
// ---------------------------------------------------------------------------

/** Which player design to draw. */
enum class PlayerLook(val id: String, val label: String, val blurb: String) {
    /** The approved design, exactly as it is today — the baseline. */
    CURRENT(
        "current",
        "Current",
        "The design you know — top bar, center transport, bottom buttons.",
    ),

    /**
     * Stremio's modern player (stremio-android 2.x): a glass top pill
     * (back + title + menu), glass center buttons with a glowing play
     * button, and a glass bottom card holding only the wave-gradient
     * seekbar. The other tools live in the pill's overflow menu, like
     * the real app.
     */
    STREMIO(
        "stremio",
        "Stremio",
        "Stremio's modern player — glass top pill, glowing play, wave seekbar.",
    ),

    /**
     * Nuvio's player (NuvioMobile): bare white icons with no circles,
     * back + lock in a top-right toolbar, and the title, gradient
     * seekbar and a scrollable action row stacked at the bottom.
     */
    NUVIO(
        "nuvio",
        "Nuvio",
        "Nuvio's player — bare icons, title and actions stacked at the bottom.",
    ),
}

object PlayerLooks {
    val current = PlayerLook.CURRENT
    val all = PlayerLook.entries
}
