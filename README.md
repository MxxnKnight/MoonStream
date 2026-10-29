# MoonStream

A personal Telegram-to-Stremio Android app, built from scratch.

Turn your Android phone into a personal Stremio addon that streams movies, series
and anime live from your own Telegram channels — no server, no hosting, no
subscription.

## Status

Phase 1 — UI shell from the design mockup (in progress). See `docs/PLAN.md`.

## Project layout

- `app/` — the Android app (Kotlin + Jetpack Compose, Material 3)
  - `ui/screens/` — one file per screen (Server, Library, Detail, Channels,
    Create, Settings), mirroring the approved UI mockup
  - `ui/theme/` — app theme (dark, large touch targets)
- `docs/` — plans and notes

## Build

Requires Android SDK (compileSdk 35) and Gradle 8.x:

```sh
gradle :app:assembleDebug
```

A debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- Package: `com.moon.moonstream` (must stay unique — do not reuse NoxMedia's
  `com.tgserver.streamer`, or the two apps will clash on install)
- A bundled font will be added before any release build (system handwriting
  fonts restyle unlabeled UIs on the target device)
- Backend design is informed by public behaviour of TeleStremio-style apps;
  all code here is written fresh — no copied code
