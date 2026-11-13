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

## Known fidelity limits

- Fonts render slightly differently on web (Skia) vs Android — same files,
  tiny rasterization differences.
- Haze blur (the bottom pill) is Android-only; mockups use a translucent
  fill instead. The pill’s shape, size and layout are still exact.
- Shadows draw a little differently on web.
- The phone build is still the final judge for the last ~5%.
