# app.confused.anikuta.feature.player

Fullscreen player.

**Module path:** `feature/player`
**Type:** Android library with Compose
**Status:** ⚠️ Empty stub — fullscreen player overlay lives in `:core:player` + `:feature:watch`

## Why this is a stub

Per ADR-025, the app uses a **single MPV instance** — the watch page's
mini-player and the fullscreen player share one MPV surface. "Maximize" swaps
the Compose overlay (the fullscreen controls live in
`:core:player/controls/FullscreenControls.kt`), it does **not** navigate to a
separate fullscreen player activity/screen. So there is no separate
`:feature:player` screen.

This stub has no source files but **IS depended on by `:app`**
(`implementation(projects.feature.player)` in `app/build.gradle.kts`). The
dependency is currently a no-op (empty module). It can be removed from the
`:app` deps + `settings.gradle.kts` in a future cleanup pass, or repurposed if
we ever split the fullscreen overlay into its own feature module.

**Note:** The Voyager navigation migration may revisit this — a dedicated player
screen could make sense if we want the fullscreen player to be a proper
backstack entry rather than an overlay swap.
