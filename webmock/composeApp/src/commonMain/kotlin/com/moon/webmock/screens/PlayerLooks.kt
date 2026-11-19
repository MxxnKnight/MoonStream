package com.moon.webmock.screens

// ---------------------------------------------------------------------------
// Player looks (moon's 2026-11-19): three LAYOUTS for the SAME player —
// same buttons, same styling, same behaviour; only the ARRANGEMENT
// differs, so the comparison is purely about layout. (The first round —
// Current/Air/Bar — only restyled one layout, and moon rightly said they
// were all the same; that round is deleted, this one replaces it.)
// The look is a plain parameter to PlayerScreen, so switching looks never
// resets playback state (it lives in PlayerLandscapeMock's remember).
// Sheets, drawers, trimmer, NextUp and the unlock slider are identical
// in every look — only the main control layer is arranged differently.
// ---------------------------------------------------------------------------

/** Which arrangement of the player controls to draw. */
enum class PlayerLook(val id: String, val label: String, val blurb: String) {
    /** The approved design, exactly as it is today — the baseline. */
    CURRENT(
        "current",
        "Current",
        "The design you know — top bar, center transport, bottom buttons.",
    ),

    /** YouTube-style: empty center, transport bar at the bottom. */
    TUBE(
        "tube",
        "Tube",
        "Empty center — play, -10/+10 and the rest live in a bottom bar.",
    ),

    /** Side rail: every tool in a vertical capsule on the right edge. */
    RAIL(
        "rail",
        "Rail",
        "Side rail — all the tools stacked in a capsule on the right.",
    ),
}

object PlayerLooks {
    val current = PlayerLook.CURRENT
    val all = PlayerLook.entries
}
