# app.confused.anikuta.data.tracker

Tracker impls (MAL, AniList, Shikimori, Bangumi, Simkl).

**Module path:** `data/tracker`
**Type:** Android library
**Status:** ⚠️ Empty stub — tracker impls live in `:core:tracker`

## Why this is a stub

The original architecture plan (ARCHITECTURE.md §3) put tracker *implementations*
in `:data:tracker` with interfaces in `:core`. In practice, the tracker system
(AniList + MAL OAuth, `TrackSyncManager`, `StatsCalculator`, `TrackRepository`)
was implemented entirely in `:core:tracker` because:
1. The tracker logic is shared across multiple features (profile, trackers
   settings, backup) → belongs in `:core`.
2. The Aniyomi-compatible tracker interface already lives in `:core:source-api`
   territory, and the OAuth/refresh/sync logic is cohesive enough to live in one
   `:core:tracker` module rather than split across `:core` + `:data`.

This stub has no source files and is **not depended on by `:app`**. Only AniList
+ MAL are implemented; Shikimori/Bangumi/Simkl are deferred (ADR-019 lists them
as "user picks which tracker(s)").
