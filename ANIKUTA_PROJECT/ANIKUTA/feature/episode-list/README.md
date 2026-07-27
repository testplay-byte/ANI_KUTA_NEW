# app.confused.anikuta.feature.episodelist

Episode list component.

**Module path:** `feature/episode-list`
**Type:** Android library with Compose
**Status:** ⚠️ Empty stub — functionality lives in `:feature:anime-details`

## Why this is a stub

The episode list is implemented as `EpisodesSection.kt` (+ `EpisodeRow`) inside
`:feature:anime-details` rather than as a standalone module. The original
architecture plan (ARCHITECTURE.md §3) listed a separate `:feature:episode-list`
module, but in practice the episode list is tightly coupled to the details page
(they share the `AnimeDetailViewModel` and the `EpisodeDisplayPreferences`), so
extracting it would add coupling overhead without benefit.

This stub has no source files and is **not depended on by `:app`**. The episode
list is also rendered on the `:feature:watch` page (mini-player + episode list
below), which consumes the same row composable via a shared dependency on
`:feature:anime-details`.
