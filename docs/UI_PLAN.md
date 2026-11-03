# MoonStream UI plan — making the app look designed, not assembled

Written 2026-11-03 after reading `ui/theme/Theme.kt`, `ui/components/MsControls.kt`,
every screen file, and `MainActivity.kt`. Nothing here is implemented yet: this is
the proposal, ordered so each phase stands on its own.

There is no emulator in this workspace, so nothing below is a screenshot review.
Every number quoted (radius counts, sizes, colours) was read out of the code.

---

## 0. The one real problem

The app already has a colour system (`MsColors`, accent-aware, AMOLED-aware) and
bundled fonts. What it does **not** have is a **shape and size system**:

- `MsRadii` defines 4 tokens, but only **3 of 12** UI files use it. The rest
  hard-code **15 different corner radii**: 2, 3, 4, 6, 8, 10, 12, 14, 16, 18,
  20, 24, 26.5, 28, pill.
- `MaterialTheme` has **no `shapes`** — Material components (Slider, DropdownMenu,
  Sheets, Dialogs) fall back to Material's 4dp/28dp defaults, which is why sheets
  and menus look like a different app from the cards.
- Corner sizes were chosen one at a time, so the same *kind* of object gets
  different radii in different screens (a chip is 6, 8 and 10dp depending where
  you are; a card is 14, 16, 18, 20, 24 and 26.5dp).
- Sizes are ad-hoc too: `fontSize` literals 9, 10, 11, 13, 22, 44, 72 exist next
  to Material styles; icon buttons are 40dp in one place, 44 in another.

That inconsistency, more than any single screen, is what reads as "not quite
designed". Phase 1 fixes it with tokens only — no layout changes, so it is
low-risk.

---

## Phase 1 — Tokens (do this first, ~1 commit, no visual redesign)

### 1a. One shape scale

```kotlin
object MsShapes {
    val Xs = 6.dp      // badges, tiny tags
    val Sm = 10.dp     // chips, small inner boxes
    val Md = 14.dp     // posters, list thumbnails, inputs inside cards
    val Lg = 20.dp     // cards, sheets
    val Xl = 28.dp     // hero cards, modals
    val Pill = 50.dp   // buttons, nav pill, toggles
}

// and in MoonStreamTheme:
shapes = Shapes(
    extraSmall = RoundedCornerShape(MsShapes.Xs),
    small      = RoundedCornerShape(MsShapes.Sm),
    medium     = RoundedCornerShape(MsShapes.Md),
    large      = RoundedCornerShape(MsShapes.Lg),
    extraLarge = RoundedCornerShape(MsShapes.Xl),
)
```

Rule: **one geometry per object kind** —
card 20, inner media 14, chip 10, badge 6, button pill.
A 2dp and a 3dp radius should not exist anywhere; 26.5dp exists because a shape
was hand-tuned against a screenshot — replace it with 28.

### 1b. One spacing scale

4 / 8 / 12 / 16 / 20 / 24 / 32. Today 3, 6, 10, 13, 14 all appear as padding.
Map: row inner padding 12 or 16 (never 14), card padding 16, screen gutter 16,
section gap 20, list item gap 10→12.

### 1c. One type scale

| role | size / weight | used for |
|---|---|---|
| `labelSmall` | 11sp / medium, tracking 0.4 | badges, timestamps (`9.sp` is below the legible floor — the "12 EPS" badge) |
| `label` | 12sp / semibold | chips, section headers (uppercase, tracking 0.6) |
| `bodySmall` | 13sp / normal | secondary lines, logs |
| `body` | 15sp / normal | dense rows, sheet options |
| `title` | 17sp / semibold | card titles, sheet titles |
| `headline` | 22sp / semibold (Doto) | screen titles |
| `display` | 28sp / bold (Doto) | detail hero title |

Replace the one-off `44.sp` initial-letter tile (Library placeholder) with 28sp
and lower alpha; replace `72.sp` splash numerals with display 28 unless it is a
deliberate mark.

### 1d. One control size set

| control | height | notes |
|---|---|---|
| text field | 48dp (keep) | already hand-drawn so text can't clip — good |
| primary button (`LimePillButton`) | 48dp fixed | today it is padding-driven (~46dp), so it never lines up with a 48dp field beside it |
| secondary (`MsOutlinePill`) | 44dp | |
| chip | 36dp, min touch 44 | currently 40dp min |
| icon button | 44dp box, 24dp glyph, min touch 48 | `BareIconButton` is a stock 48dp touch target with a 24dp glyph — fine; the custom `PlayerIconButton` is a 45dp circle with a 20dp glyph, so the two families read as different sizes side by side. Pick 44+48 |

Buttons also need a **pressed state**: `LimePillButton` has no visual feedback at
all right now (no ripple tint, no scale, no dim). Add `interactionSource` →
`background = accent.copy(alpha = if (pressed) 0.85f else 1f)`.

---

## Phase 2 — Gradients that look intentional

Right now there are exactly two gradients in the whole app (player bottom scrim,
detail hero scrim). Use a **named brush family** in `MsColors`, accent-aware so
it follows the picker:

1. **Card sheen** — gives flat `#161B24` cards depth without shadows:
   `verticalGradient(SurfaceAlt.copy(alpha = 0.55f) → Surface)` + a 1dp top
   hairline `White.copy(alpha = 0.06f)`. No drop shadows (they look muddy on AMOLED).
2. **Hero fade** — today the detail screen has no hero fade at all: the poster
   ends abruptly and a fixed 110dp `transparent → #CC000000` strip sits behind
   the back button. Replace it with a 4-stop fade from the poster into `Bg`
   (`transparent → black@0.35 → Bg@0.85 → Bg`) sized to ~40% of the poster
   height instead of a fixed dp. Keep one short top scrim for the back/overflow
   icons (black@0.6 → transparent, 96dp).
3. **Accent aura** — radial `Accent.copy(alpha = 0.18f) → transparent` behind the
   primary CTA / active nav tab. `GlowLight`/`GlowDeep` already exist for the
   server card and splash — reuse them instead of inventing new stops.
4. **Player scrim** — go from 2 stops to 4 (top controls, bottom controls) so the
   fade is gradient-smooth: `transparent → black@0.15 → black@0.55 → black@0.8`.

Rules: **never gradient text**, gradient alpha tops out ≤ 0.85, and every stop is
`Bg`/`Surface`/white — never a literal `#0E1116`. The existing 110dp detail strip
uses literal black (`0xCC000000`) which is fine over artwork, but any fade that
reaches page background must use `MsColors.Bg`, otherwise the AMOLED toggle
leaves a grey band.

---

## Phase 3 — Screens

### Library (the screen you actually live in)
- `GridCells.Fixed(2)` → `GridCells.Adaptive(minSize = 104.dp)`: 3 columns on a
  6.6" phone, 2 on narrow ones or with large font.
- Poster radius 14 → `MsShapes.Md`, and add the 1dp hairline border: dark posters
  currently bleed into the background with no edge.
- Grid gap 8 → 12 vertical / 12 horizontal, so titles get breathing room.
- Title under poster: 2 lines max with `lineHeight = 18.sp`, count badge 11sp
  (not 9sp) at pill radius, top-left (already correct).
- Filter tabs: selected pill should use the accent aura, unselected should be
  text-only (no outline) — one visual step instead of two.
- Empty state: poster-shaped placeholder + one lime pill ("Add a channel").

### Home / Server
- The status card is the app's signature object — give it the glow ring
  (`GlowLight` 1dp border + inner accent 4% fill) when the server is **running**,
  and drop to a plain card when stopped. State becomes legible at a glance.
- Recent rail: 16:9 thumbs, 14dp radius, 12dp gap, title + "S1 · E4" caption on
  two lines.

### Channels
- Replace the thin progress bar with a **26dp circular progress ring** (accent,
  3dp stroke, % in the middle at 10sp). One scanning row becomes self-contained
  and the card height stops jumping between states.
- Row: title 15sp/600, breakdown 13sp secondary on one line with `·` separators
  (already), synced-at 11sp. Scan button only appears on hover/while stopped —
  collapsing it to a 44dp icon keeps the row calm.
- Overflow menu: `MsShapes.Md`, 8dp vertical padding, 48dp item height, destructive
  item in red with a divider above it.

### Detail
- Hero: poster (2:3) beside title block, then a 4-stop scrim into `Bg`; title in
  Doto 28, meta chips 10dp radius.
- Primary CTA = "Play" lime pill 48dp *sticky at the bottom* over the scroll with
  a gradient above it; secondary actions (download, add to library, fix metadata)
  as 44dp icon pills in a row.
- Episode list: each row a 20dp card with a 16:9 thumbnail (radius 14), number
  badge, title, duration; currently compact rows — thumbnails are what make an
  episode list feel like a streaming app.
- Section headers: 12sp uppercase, tracking 0.6, secondary colour.

### Settings
- Group rows into cards by section (Playback / Telegram / Appearance / Data /
  About). Section title above each card in 11sp uppercase secondary.
- `SettingRow` height 56dp, label 15sp, value 13sp secondary right-aligned,
  chevron 16dp secondary. Toggle switch uses accent as thumb-on colour, not
  Material's default secondary.
- Danger zone (clear cache, wipe) gets a red-tinted 20dp card with a 1dp
  `DestructiveRed.copy(alpha = .25f)` border, not red text on a normal card.

### Login / Splash
- Login: single card, 28dp radius, fields 48dp, one 48dp pill, "Continue with
  Telegram" secondary outline. Add 8dp between field groups, 20 between form and
  button. No gradient on the card — keep gradients for hero objects only.
- Splash: the glow already animates; make sure it ends on the same accent used
  by the nav pill so the transition doesn't jump colour.

### Logs
- Monospace 12sp, `lineHeight 16`, level chips (ERROR red / WARN amber / INFO
  secondary) at 6dp radius, and a 1px divider between entries instead of boxes.

---

## Phase 4 — Player (biggest win, and it's where you spend your time)

Current state: mpvex-style layout already exists (centre transport with -10/+10
at the edges, 76dp centre circle, corner clusters, seek row with monospace
timers, slide-to-unlock, brightness/volume HUD, double-tap seek pill, Skip Intro
pill, sheets for subs/audio/playlist/episodes). What's missing:

1. **Custom seek bar.** The current seek bar is a stock Material `Slider` with a
   default 20dp thumb sitting inside a 160dp video scrim — it's the single most
   "unfinished" looking part of the player. Replace with a `Canvas`-free custom
   row: 4dp track (`White@0.25`), buffered range (`White@0.4`), played
   (`Accent`), 12dp thumb that scales to 16dp only while scrubbing, and a
   2dp-per-second chapter tick every 10 min. Height 32dp for the touch area.
   *Note: the repo bans `Canvas`/`drawBehind` for the server card — draw this
   with nested `Box` + `fillMaxWidth(fraction)`, same as the volume HUD does.*

2. **Scrubbing feedback.** While dragging, show a centred pill
   `12:41 / 24:05` (monospace 15sp) and keep controls from auto-hiding.

3. **Playback speed** (0.5 / 0.75 / 1 / 1.25 / 1.5 / 2) in the audio sheet,
   persisted per app, not per file. ExoPlayer: `player.setPlaybackSpeed()`.

4. **Subtitle style sheet.** The prefs already exist (size, stroke, stroke
   colour) — surface them: size slider 50–150%, stroke toggle, 6 colour dots,
   live preview line of text in the sheet.

5. **Controls bar anatomy.** Today: icon row, then seek row, all inside one
   gradient column. Tighten to: seek row at the bottom (thumb 4dp above the
   bottom edge), icon row above it with 12dp gaps, first icon (subs) and last
   (aspect) aligned to the 16dp gutter, all buttons 44dp. Add a
   **back / title / resolution** line above the icons: `‹ ` 44dp, title 15sp
   ellipsised, `1080p` chip 10dp radius — the top scrim currently has only a
   title pill.

6. **Auto-hide rules.** 3.5s while playing, never while paused, never while a
   sheet is open or scrubbing, and re-show on any tap. Enter/exit = 120ms fade +
   6dp slide, not scale.

7. **Buffering.** Replace the spinner with a 44dp ring, 2dp accent stroke,
   `White@0.15` track; show it only after 400ms so quick seeks don't flash it.

8. **Up-next card.** 10s before `duration`, slide a 20dp card (16:9 thumb,
   "Next: S1 · E5", Play / Cancel) over the bottom-right; tapping the pill plays
   the next episode. You already have `nextGroup`/`switchEpisode` — this is just
   the affordance.

9. **Gesture HUD polish.** 6dp wide × 120dp tall bar is right; add the step
   value ("72%") in monospace 12sp and trigger a light haptic every 10% so you
   can feel where you are without looking.

10. **Insets.** Landscape with a notch: apply `WindowInsets.displayCutout`
    to the control rows (16dp gutter + cutout), otherwise the aspect button sits
    under the camera punch-hole on your POCO in landscape.

---

## Phase 5 — Motion (one spec, everywhere)

- Screen switch 110ms fade (already) — keep.
- Sheets: 220ms `FastOutSlowInEasing` slide + 100ms fade of the scrim.
- Press: 90ms colour change only; no scale on cards (looks cheap at 60Hz).
- Selection: 140ms colour transition on chips/tabs/segmented buttons.

---

## Order of work, and what each phase touches

| phase | files | risk | payoff |
|---|---|---|---|
| 1 Tokens | `Theme.kt`, `MsControls.kt` + radius/spacing sweep | low (no layout change) | app stops looking hand-assembled |
| 2 Gradients | `Theme.kt`, `MsCard`, Detail, Player | low | depth on AMOLED without shadows |
| 3 Screens | Library, Channels, Detail, Settings | medium (layout edits) | looks like a real streaming app |
| 4 Player | `PlayerScreen.kt` (seek bar, speed, subs sheet, up-next) | medium, needs device test | the screen you stare at |
| 5 Motion | scattered | low | polish |

Everything in phase 4 needs a device check; phases 1–2 can be verified by
compiling plus a screenshot from you.
