# MoonStream

<p align="center">
  <img src="logo.png" alt="MoonStream logo" width="160" />
</p>

<p align="center">
  <b>Your personal Telegram media library — movies, series and anime,<br/>
  streamed straight from your own Telegram channels. No server, no hosting, no subscription.</b>
</p>

MoonStream is a personal Android app (Kotlin + Jetpack Compose, Material 3).
You connect your own Telegram account; the app scans your channels for movies
and TV shows, builds a personal library with real metadata (TMDB / Cinemeta /
Kitsu), and plays everything in a built-in player — or serves it to Stremio
as a local addon running on your phone.

## What it does

- **Library** — scans your Telegram channels, matches files to real titles
  (movies, series, anime), groups episodes, tracks watch progress.
- **In-app player** — plays video straight from Telegram with a custom
  low-storage streaming engine (sliding ~200 MB window, so a 7 GB file
  never fills your phone). Subtitles with delay / appearance / position
  controls, audio track picker, brightness & volume gestures, screenshot,
  landscape fullscreen.
- **Stremio addon** — optional in-phone server (ports 8080/8081) so Stremio
  and external players can stream your library too.
- **Low-storage friendly** — stream cache auto-deletes watched chunks;
  Telegram's own copy is purged too. Clear Cache wipes both.
- **Backup** — local versioned backups of your library (cloud tier via
  Telegram Saved Messages).
- **OTA updates** — Settings → Check for updates installs new versions
  without logging you out.

## Status

Actively developed — currently **v0.15.0** (in-app streaming engine, no
addon server needed). See `CHANGELOG.md` for the full history and
`docs/BUG_CHECKLIST.md` for the live bug tracker.

## Project layout

- `app/` — the Android app (Kotlin + Jetpack Compose, Material 3)
  - `ui/screens/` — one file per screen (Server, Library, Detail, Channels,
    Create, Settings)
  - `ui/player/` — the in-app player + `tdfile://` streaming engine
  - `ui/theme/` / `ui/components/` — design system (MsControls)
  - `index/` — scanner, filename parser, metadata matcher, library tools
  - `server/` — the in-phone Stremio addon / stream server
  - `backend/` — the `MoonStreamBackend` contract + TDLib implementation
  - `cache/` — cache management
- `docs/` — plans, backend contract, bug checklist
- `AGENT.md` — briefing for AI contributors (ownership split, conventions)

## Build

CI (`.github/workflows/build.yml`) is manual-only: Actions tab →
"Run workflow", enter version, optionally publish a release. It uploads one
APK per ABI (14-day retention) — the POCO M4 Pro takes **arm64-v8a**.

Local builds need the Android SDK (compileSdk 35) and Gradle 8.x, plus the
offline Maven mirror this project uses for media3:

```sh
gradle :app:assembleDebug
```

A debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
Bump `versionCode`/`versionName` in `app/build.gradle` for every release.

## Notes

- Package: `com.moon.moonstream` (must stay unique — do not reuse
  NoxMedia's `com.tgserver.streamer`, or the two apps will clash on install)
- Inter font is bundled and forced theme-wide (the target device's
  handwriting system font restyles unlabeled UIs otherwise)
- The debug keystore is pinned in-repo (`keystore/debug.keystore`) so every
  build installs over the previous one — updates never log the user out
- Never commit secrets: no API ID/hash, no session strings, no keystore
  passwords
- Backend design is informed by public behaviour of TeleStremio-style apps;
  all code here is written fresh — no copied code
