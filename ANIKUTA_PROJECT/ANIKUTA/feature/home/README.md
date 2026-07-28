# app.confused.anikuta.feature.home

Home screen (AniList trending/seasonal).

**Module path:** `feature/home`
**Type:** Android library with Compose
**Status:** ⚠️ Empty stub — functionality lives in `:feature:browse`

## Why this is a stub

The original architecture plan (ARCHITECTURE.md §3) listed a separate
`:feature:home` module. In practice, the home/browse page was implemented as a
single `:feature:browse` module (the "Home" bottom-nav tab renders
`BrowseScreen`). This stub module exists only because the original module list
included it; it has no source files and is **not depended on by `:app`**.

**Do not add code here.** Home/browse work goes in `:feature:browse`. This stub
is kept for now and may be removed in a future cleanup pass (pending owner
decision).
