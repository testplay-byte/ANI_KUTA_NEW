# Extension-to-AniList Linking Architecture

> How the app handles anime from extension sources.
> Last updated: Session 31 (Phase I — revised).

---

## Overview

When a user discovers anime through extension sources (search or browse),
the app links the extension entry to an AniList entry so the full detail
page (episodes, player, downloads, tracking) can be used.

If the anime is NOT on AniList, the linking screen shows search results
and a manual search field so the user can pick the right entry.

---

## The Flow

```
User taps extension anime result
         │
         ▼
  Check ExtensionLinkStore cache
  (sourceId:animeUrl → AniList ID)
         │
    ┌────┴────┐
    │         │
  HIT       MISS
    │         │
    ▼         ▼
Navigate to  Show SourceLinkingScreen
DetailScreen  (cover + "Processing...")
(anilistId)         │
              Search AniList by title
              (Step 1: without adult filter)
                    │
              ┌─────┴─────┐
              │           │
           Found       Not Found
              │           │
              ▼           ▼
          Cache link   Retry with adult filter ON
          Navigate to       │
          DetailScreen  ┌───┴───┐
                        │       │
                     Found   Not Found
                        │       │
                        ▼       ▼
                   Cache link  Show NotFound screen:
                   Navigate to  - "Did you mean?" results list
                   DetailScreen   (if any results from either search)
                                - Manual search field
                                - User taps a result → link + navigate
                                - User types different title → re-search
```

---

## Files

| File | Role |
|------|------|
| `ExtensionLinkStore.kt` | SharedPreferences cache: `sourceId:animeUrl` → AniList ID |
| `SourceLinkingScreen.kt` | Loading card + AniList search (with/without adult) + results list + manual search |
| `AnikutaNavGraph.kt` | Route: `source-link/{sourceId}/{animeUrl}/{title}/{thumbnailUrl}` + cache check |
| `SearchScreen.kt` | Constructs the `source-link` route when extension results are tapped |
| `AniListRepository.kt` | `searchAnime()` (no adult) + `searchAnimeWithAdult()` (isAdult: true) |
| `AniListQueries.kt` | `searchAnime` + `searchAnimeWithAdult` GraphQL queries |

---

## SourceLinkingScreen States

| State | What happens |
|-------|-------------|
| `Searching` | Shows cover + "Processing... Just wait a moment" + "Searching AniList for [title]" |
| `Linked` | Auto-match found → caches link → navigates to DetailScreen |
| `NotFound(results)` | Auto-match failed. Shows: "Did you mean?" results list (if any) + manual search field. User can tap a result or type a different title. |

---

## Search Strategy

The linking screen searches AniList in this order:
1. **Without adult filter** (`searchAnime`) — most anime are non-adult
2. **With adult filter** (`searchAnimeWithAdult`) — if step 1 returned nothing
3. **Show results + manual search** — if both searches returned nothing

The manual search field also tries both filters (without → with adult).

---

## What happens when the user selects a result

1. `linkStore.link(sourceId, animeUrl, anilistId)` — caches the link
2. `_state.value = SourceLinkingState.Linked(anilistId)`
3. `LaunchedEffect` detects the `Linked` state → calls `onLinked(anilistId)`
4. NavGraph navigates to `detail/$anilistId`
5. Future taps on the same extension anime → cache hit → goes directly to DetailScreen

---

## What happens when NO result is found at all

The `NotFound` state shows:
- "No matches found on AniList. Try searching manually below."
- An `OutlinedTextField` for manual search
- The user types a different title → `manualSearch(query)` → searches again (both filters)
- If results are found → they appear in the "Did you mean?" list
- The user taps one → links + navigates

This handles:
- Misidentified titles (the extension title doesn't match AniList's)
- Adult anime (excluded by default, included with the adult filter)
- Different transliterations (user can type the correct title)
