# UNLINK-LINK-SAVE-FIXES — Implementation Record

**Agent:** Z.ai Code (main coordinator + implementer)
**Task ID:** UNLINK-LINK-SAVE-FIXES
**Branch:** `fix/unlink-link-save-state`
**Project root:** `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/`
**Date:** 2025

## Summary

Implemented all 6 surgical fixes from the task spec. All 3 user-reported issues
(linked→reopened shows unsaved / link→save loses favorite / unlink→reopened
sometimes unsaved) trace to a single root cause:
`ExtensionDetailsProvider.persistEpisodes` looked up the existing DB row by
`anilistId` first, missed the extension-only row (which has `anilist_id = NULL`),
and `upsert` overwrote the existing row's `favorite` to `false`. The fix: always
look up by `(sourceId, url)` first — that's the natural key for an
extension-sourced row — and stamp the `anilist_id` via a new targeted update
that doesn't touch `favorite`.

## Files modified (6)

### 1. `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animes.sq`
- Added new SQLDelight query `updateAnilistId` immediately after `clearAnilistId`:
  ```sql
  updateAnilistId:
  UPDATE animes SET anilist_id = :anilistId WHERE _id = :id;
  ```
- This is the companion of `clearAnilistId`: stamps an `anilist_id` on an
  existing row WITHOUT touching `favorite`/`date_added`/category membership.

### 2. `core/common/src/main/java/app/confused/anikuta/core/common/repository/AnimeRepository.kt`
- Added new abstract method on the `AnimeRepository` interface:
  ```kotlin
  suspend fun updateAnilistId(id: Long, anilistId: Int?)
  ```
- Documented the use case (linking flow that finds an existing extension-only
  row by `(sourceId, url)` and needs to stamp the `anilist_id` without going
  through `upsert` which would overwrite `favorite`).

### 3. `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeRepositoryImpl.kt`
- Implemented `updateAnilistId` mirroring `clearAnilistId` but with the
  `anilistId` parameter:
  ```kotlin
  override suspend fun updateAnilistId(id: Long, anilistId: Int?) {
      Log.d(TAG, "updateAnilistId: id=$id, anilistId=$anilistId")
      withContext(dispatchers.io) {
          database.animesQueries.updateAnilistId(
              id = id,
              anilistId = anilistId?.toLong(),
          )
      }
  }
  ```

### 4. `data/extension/src/main/java/app/confused/anikuta/data/extension/details/ExtensionDetailsProvider.kt`
- **`persistEpisodes` lookup fix (Fix 2):** changed the lookup pattern from
  `if (anilistId != null) getByAnilistId else getBySourceAndUrl` to:
  ```kotlin
  var dbAnime = animeRepository.getBySourceAndUrl(sourceId, animeUrl)
  if (dbAnime == null && anilistId != null) {
      dbAnime = animeRepository.getByAnilistId(anilistId)
  }
  ```
  The `(sourceId, url)` lookup is the natural key for an extension-sourced row
  and always finds the existing row (even when `anilist_id = NULL`).

- **Stamp-anilistId branch (Fix 2b):** after lookup, if `dbAnime != null` and
  `anilistId != null` and `dbAnime.anilistId == null`, stamps the `anilist_id`
  via the new `animeRepository.updateAnilistId(dbAnime.id, anilistId)` WITHOUT
  calling `upsert` (which would overwrite `favorite`). Re-fetches the row
  afterward so the downstream `updateMetadataFromExtension` + episode re-insert
  use the same row id.

- **New row path:** kept the existing `newAnime` creation + `upsert` path for
  the truly-new case (`dbAnime == null`). `favorite = false` is correct for a
  NEW row.

### 5. `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt`
- **`observeLibraryState` (Fix 3):** restructured the branching so that when
  `observeByAnilistId` emits `null` AND the active request is `ByExtension`,
  the observer falls back to `getBySourceAndUrl(extSourceId, extUrl)`. This
  keeps `_isSaved` correct during the unlink transition window (the row's
  `anilist_id` was just cleared, but the row is still in the library as
  extension-only). Logged at `Log.i` for the initial branch + `Log.d` for each
  fallback hit.

- **`findLibraryAnime` (Fix 4):** changed the `anilistId != null` branch from a
  plain `getByAnilistId` to:
  ```kotlin
  anilistId != null -> {
      val byAnilist = animeRepository.getByAnilistId(anilistId)
      if (byAnilist != null) byAnilist
      else if (sourceId != null) animeRepository.getBySourceAndUrl(sourceId, anime.url)
      else null
  }
  ```
  Same fallback rationale. Keeps `toggleSave` / `saveToCategories` /
  `getAnimeCategories` working through the unlink transition window without
  inserting a duplicate row.

### 6. `app/src/main/java/app/confused/anikuta/navigation/AppController.kt`
- **`onLinked` (Fix 5):** added a `sourceLinkStore.saveLink("al:$anilistId",
  source.id, sAnime.url, sAnime.title)` call before navigation. This persists
  the reverse link (AniList → extension) so cold starts +
  `ExtensionDetailsProvider.load(ByAniListId)` reverse-lookup works, and so a
  later `unlinkFromAniList` can resolve the source/url without relying on the
  live destination. Wrapped in try/catch — non-fatal if the write fails.

- **`openLibraryAnime` (Fix 6):** restructured the branching so that when
  `anime.sourceId > 0`, the anime ALWAYS opens in Extension mode (passing
  `anilistId` along if present), regardless of whether `anilistId` is also
  non-null. This survives stale library snapshots: after `unlinkFromAniList`
  clears `anilist_id`, the library list re-emits asynchronously, so a brief
  window exists where the snapshot still has the old `anilist_id` but the DB
  row's `anilist_id` is already NULL. Opening in AniList mode during that
  window would put the new VM in AniList mode → `observeByAnilistId` returns
  null → `_isSaved = false` (the "saved anime sometimes shows unsaved" bug).
  New branching:
  - **Branch 1 (`sourceId > 0`):** Extension mode — live source →
    `pushExtensionDetail`; uninstalled source → `LibraryExtensionDetailDestination`.
  - **Branch 2 (`sourceId == 0 && anilistId != null`):** AniList mode →
    `pushDetail(anilistId)`.
  - **Branch 3 (neither):** defensive toast + log.

  Tagged all branches with `Log.i(TAG, ...)` for diagnostics.

## Issues encountered

- **`openLibraryAnime` original log tag:** the original code used the literal
  `"AnikutaLibrary"` tag for the no-source warning. I normalized this to `TAG`
  (`"AnikutaAppController"`) for consistency with the rest of the file.
  Functionally identical, just better filterable.
- **`observeLibraryState` polling fallback removed:** the original `else`
  branch (no usable identity) polled `animeState.collect { ... findLibraryAnime
  ... }`. The spec replaced this with `_isSaved.value = false`. I followed the
  spec exactly. The polling branch was defensive code for a state that's
  unreachable with the current `DetailsRequest` model (ByAniListId always has
  anilistId; ByExtension always has sourceId + url), so removing it is safe.
- **`loadByExtension` DB-first short-circuit NOT touched:** the spec only
  mentioned `persistEpisodes`. The short-circuit at lines 117–138 has the same
  `if (anilistId != null) getByAnilistId else getBySourceAndUrl` pattern, but
  it's only an optimization (a miss falls through to network fetch → eventually
  calls the now-fixed `persistEpisodes`). It doesn't cause the
  favorite-overwrite bug, so it's left untouched per the spec's "surgical
  fixes" instruction.

## Verification

- All changes are purely additive or in-place rewrites — no public API removed,
  no schema migration needed (the new SQLDelight query is generated at compile
  time, no DB column added).
- Imports verified: `SAnimeImpl`, `Toast`, `Log`, `TAG`, `Dispatchers`,
  `withContext`, `sourceLinkStore` — all already present in their respective
  files.
- New code adds `Log.i`/`Log.d`/`Log.w` diagnostics for every observable
  behavior change (per the spec's logging requirement).
- No tests written (per project rule "do not write any test code").
