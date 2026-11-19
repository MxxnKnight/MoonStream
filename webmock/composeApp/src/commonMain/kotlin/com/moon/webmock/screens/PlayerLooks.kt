package com.moon.webmock.screens

// ---------------------------------------------------------------------------
// Player looks (moon's 2026-11-19): three full player DESIGNS — the
// approved Current one, plus one in Stremio's style and one in Nuvio's,
// built from moon's own direction ("I want stremio kinda and nuvio kinda
// separate ui's"). Each look is a full UI in that app's style — its own
// arrangement AND its own button treatments, icons and seekbar. The 13
// FUNCTIONS never change (Back, Episodes, Playlist, Screenshot, Trim,
// Lock, -10s, Play/Pause, +10s, Subtitles, Audio, Next episode, Aspect
// ratio) — no speed, no PiP, no extra buttons — and no look gets a
// seekbar thumb dot.
// Sourcing, stated honestly: the Nuvio look IS based on the real
// NuvioMobile player source (NuvioMedia/NuvioMobile, branch
// cmp-rewrite: PlayerControls.kt + PlayerTimeline.kt +
// PlayerControlActions.kt, current non-legacy layout). The Stremio look
// is INSPIRED BY Stremio, not copied from its code: Stremio's Android
// player is not open source, and its public web player (stremio-web
// ControlBar.js/SeekBar.js) is a bottom bar with a solid-accent seekbar
// WITH a thumb. Only the purple accent here is Stremio's real one
// (#7B5BF5, --primary-accent-color); the glass pill, glowing play and
// gradient seekbar are our take on its style.
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
     * Inspired by Stremio: a glass top pill (back + title + menu),
     * glass center buttons with a glowing play button, and a glass
     * bottom card holding only the purple-gradient seekbar — our take
     * on its style, not its code (see the header note). The other
     * tools live in the pill's overflow menu.
     */
    STREMIO(
        "stremio",
        "Stremio",
        "Stremio-inspired — glass top pill, glowing play, purple gradient seekbar.",
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
