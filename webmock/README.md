# MoonStream Web Mockups

Compose Multiplatform (wasmJs) mockups for MoonStream — the mockup UI is
written in the **same Jetpack Compose code as the app**, so what you see in
the browser is what the app will look like (same theme, same fonts, same
components). A mockup composable pastes straight into the app.

Live site: https://mxxnknight.github.io/MoonStream/

> Lives inside the MoonStream repo (`webmock/`) because the GitHub token
> is scoped to that repo only — a standalone `moonstream-webmock` repo
> exists but the token cannot push to it. If the token ever gets access
> to it, this folder moves there as-is.

## How it works

- `composeApp/src/commonMain/kotlin/com/moon/webmock/` — the mockup code.
  - `theme/Theme.kt` — MoonStream design tokens ported 1:1 from the app
    (`MsColors`, `MsAccent`, Inter/Doto/Redaction35 fonts, `MoonStreamTheme`).
    Reads `MockUiState` the way the app reads `AppUiState` (moon’s setup:
    Moon Blue + AMOLED on; flip them to preview other themes).
  - `components/` — app components ported verbatim (`LimePillButton`,
    `MsOutlinePill`, `MsToggle`, the Library hero card).
  - `App.kt` — the current mockup screen. Replace its content with the
    screen being designed.
- Fonts are the app’s own `.ttf` files, in
  `composeApp/src/commonMain/composeResources/font/`.

## Building

Every push to `main` rebuilds the site via GitHub Actions and deploys it
to GitHub Pages. Local builds do **not** work in the Muse sandbox: Java
networking there is intercepted (Maven Central times out, dl.google.com
fails TLS), so Gradle cannot download the Kotlin/Compose plugins. CI is
the only build path — same as the app’s APK builds.

## Gotchas (learned setting this up, 2026-11-13)

- **Res package:** the generated `Res` class defaults to a package derived
  from the Gradle module name (`composeapp.generated.resources`), NOT the
  Kotlin package. `compose.resources { packageOfResClass = ... }` pins it.
- **Font accessors are extension properties** (`Res.font.x`) — each font
  must be imported by name or the reference won’t resolve.
- **`org.jetbrains.compose.resources.Font` is @Composable** — font
  families must be loaded inside composables, not top-level vals like
  the app’s. (And `androidx.compose.ui.text.font.Font` only takes an
  Android res id — wrong import for resource fonts.)
- **wasmJs needs its own index.html** in `wasmJsMain/resources/` — the
  distribution has no entry page without it (site 404s even though the
  build succeeds).

## Known fidelity limits

- Fonts render slightly differently on web (Skia) vs Android — same files,
  tiny rasterization differences.
- Haze blur (the bottom pill) is Android-only; mockups use a translucent
  fill instead. The pill’s shape, size and layout are still exact.
- Shadows draw a little differently on web.
- The phone build is still the final judge for the last ~5%.
