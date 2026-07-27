# app.confused.anikuta.feature.more

More tab (settings, downloads, stats, about).

**Module path:** `feature/more`
**Type:** Android library with Compose
**Status:** ⚠️ Empty stub — UI lives directly in `:app`'s `MainActivity.kt`

## Why this is a stub

The "More" tab UI is currently rendered inline in `MainActivity.kt` (the
hand-rolled state-machine nav host) rather than in a dedicated feature module.
This was a pragmatic early decision; the More screen is a simple settings-list
router that delegates to the various feature settings screens (trackers, backup,
downloads, extensions, episode-settings, etc.).

This stub has no source files and is **not depended on by `:app`**. When the
Voyager navigation migration happens, the More screen may be extracted into this
module — but that is a future decision.
