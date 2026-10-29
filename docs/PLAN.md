# MoonStream build plan

Authoritative phase list. Each phase ends with a debug APK tested on a real
device before the next phase starts.

## Phase 1 — UI shell (current)
Recreate the approved UI mockup as real Compose screens with fake data and
working navigation. No Telegram, no server, nothing plays.
Screens: Server (home), Library, Detail, Channels, Create Custom Content,
Settings. Bottom nav: Home / Library / Channels / Settings.

Mockup features to carry over:
- Metadata Fix sheet (search title -> re-match entry/file, merge + move files)
- Link to Title for unmatched content (existing entry / new episode / new entry)
- Per-channel Scan Now + Scan All, anime-channel toggle on add
- Settings groups with info tooltips
- Create form: poster URL + preview, title, description, year, Telegram file
  links (add-another), movie/series switch with season+episode, subtitle file
  links with language picker (add-another)
- Server card: gradient card, animated status, big power switch, uptime line
- No blue tap-highlight anywhere; bundled font (handwriting-font-proof)

## Phase 2 — Telegram login
API ID/Hash -> phone -> code -> 2FA password -> QR option.
Session persisted securely. TDLib-based (native), following the architecture
learned from REA inspection of TeleStremio v1.0.7 (own fresh implementation).

## Phase 3 — Scan, index, library
Channel add (incl. anime toggle), ScanManager (paged history, resume cursors),
Indexer queue, Room database (media, streams, episodes, parts, subtitles,
catalogs), filename parsing + fuzzy title matching, metadata via
TMDB/TVDB/Kitsu/Cinemeta, Metadata Fix + Link to Title wired to real data.

## Phase 4 — In-phone Stremio server
Foreground service + socket servers: /manifest.json, /catalog, /meta,
/stream, /subtitles. Separate addon/stream ports, rebind on network change,
Tailscale-friendly advertise host.

## Phase 5 — Streaming + extras
1 MB chunk streaming with HTTP range seeking, split-file joining, STORE-mode
ZIP entry streaming, per-stream Copy Link, subtitle serving, cache limits +
post-stream cleanup, backup/restore to Telegram Saved Messages, OTA update
check, Android TV layout pass.

## Non-goals for v1
- iOS (cannot host this kind of background server)
- Web panel / Telegram bot management
- Jellyfin/Emby bridge
