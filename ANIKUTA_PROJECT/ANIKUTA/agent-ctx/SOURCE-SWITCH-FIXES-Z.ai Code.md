# SOURCE-SWITCH-FIXES — Z.ai Code (implementer)

**Task ID:** SOURCE-SWITCH-FIXES
**Branch:** `fix/source-switching-data-refresh`
**Date:** 2026 (session)
**Scope:** Implement 6 surgical fixes for the ANIKUTA source-switching / data-refresh flow.

## Summary

All 6 fixes implemented. The fixes address three shared root causes:
1. Navigation loses the extension source + SAnime (Fix 1).
2. `persistEpisodes` doesn't update metadata on existing DB rows (Fix 3 — the keystone fix that also resolves the "stale data" symptom in Fixes 2/4/5).
3. DB-first short-circuit returns stale data when a fresh fetch is needed (Fixes 2 + 4).

## Files modified (8 total)

### Fix 1 — Thread extension source + SAnime through the auto-link flow

1. **`app/.../navigation/AppController.kt`** — rewrote `onLinked`:
   - Changed signature from `onLinked(anilistId, wasCached, sAnimeTitle: String)` to `onLinked(anilistId, wasCached, source: AnimeCatalogueSource, sAnime: SAnime)`.
   - Navigation target changed from `AnimeDetailDestination(anilistId)` → `ExtensionAnimeDetailDestination(source, sAnime, anilistId = anilistId)`. This opens the details page in **Extension mode** (using the tapped extension as the source), passes `anilistId` for Stage-D AniList merge, and avoids re-matching via SourceMatcher.
   - Added `Log.i("AnikutaSearch", "onLinked: navigating to ExtensionAnimeDetailDestination ...")`.

2. **`app/.../navigation/AnikutaRoot.kt`** — updated the `ExtensionLinkingSheet` `onLinked` lambda to forward `source` + `sAnime` (already in scope from `linkingTarget`) to `appController.onLinked(anilistId, wasCached, source, sAnime)`.

### Fix 2 — Unlink doesn't refresh the page (forceInitialRefresh flag)

3. **`app/.../navigation/Destinations.kt`** — added `forceInitialRefresh: Boolean = false` parameter to:
   - `ExtensionAnimeDetailDestination` (passed through to `AnimeDetailScreen`).
   - `LibraryExtensionDetailDestination` (passed through to `AnimeDetailScreen`).
   - The Voyager `key` is NOT affected (still hardcoded per source+url), so no SaveableStateHolder collision risk.

4. **`feature/anime-details/.../AnimeDetailScreen.kt`** — added `forceInitialRefresh: Boolean = false` parameter to the `AnimeDetailScreen` composable. Passes it through to the `AnimeDetailViewModel` factory.

5. **`feature/anime-details/.../AnimeDetailViewModel.kt`** — added `forceInitialRefresh: Boolean = false` constructor param. Changed `init { load() }` to:
   ```kotlin
   init {
       if (forceInitialRefresh) {
           Log.i(TAG, "init: forceInitialRefresh=true — bypassing DB-first short-circuit")
           loadInternal(forceRefresh = true)
       } else {
           load()
       }
       observeLibraryState()
       ...
   }
   ```

6. **`app/.../navigation/AppController.kt`** (same file as Fix 1) — in `unlinkFromAniList`, both navigation paths now pass `forceInitialRefresh = true`:
   - `ExtensionAnimeDetailDestination(source, sAnime, anilistId = null, forceInitialRefresh = true)`.
   - `LibraryExtensionDetailDestination(sourceId, animeUrl, animeTitle, forceInitialRefresh = true)`.
   - Added `Log.i(TAG, "unlinkFromAniList: navigating to ...")` for both branches.

### Fix 3 — ExtensionDetailsProvider doesn't update metadata for existing DB rows (KEYSTONE FIX)

7. **`core/database/.../animes.sq`** — added a new SQLDelight query:
   ```sql
   updateMetadataFromExtension:
   UPDATE animes SET
       title = :title,
       description = :description,
       genre = :genre,
       cover_url = :coverUrl,
       cover_color = :coverColor,
       status = :status,
       artist = :artist,
       author = :author
   WHERE _id = :id;
   ```

8. **`core/common/.../repository/AnimeRepository.kt`** — added the interface method:
   ```kotlin
   suspend fun updateMetadataFromExtension(
       id: Long, title: String, description: String?, genre: String?,
       coverUrl: String?, coverColor: String?, status: Int, artist: String?, author: String?,
   )
   ```

9. **`data/anime/.../AnimeRepositoryImpl.kt`** — implemented `updateMetadataFromExtension`. Calls `database.animesQueries.updateMetadataFromExtension(...)` on `dispatchers.io`, converting `status: Int` → `Long` for SQLDelight. Added `Log.d(TAG, "updateMetadataFromExtension: ...")`.

10. **`data/extension/.../details/ExtensionDetailsProvider.kt`** — 3 changes:
    - Added `coverColorHex: String? = null` parameter to `fetchAndPersistEpisodes` + `persistEpisodes`. Threaded from `loadByExtension` (where Palette extraction already happens) → `fetchAndPersistEpisodes` → `persistEpisodes`.
    - In `persistEpisodes`, after the DB row exists (newly inserted OR already existed), call `animeRepository.updateMetadataFromExtension(...)` with the enriched SAnime's fields (`title`, `description`, `genre`, `thumbnail_url`, `status`, `artist`, `author`) + the Palette-extracted `coverColorHex`.
    - **Guard:** only call `updateMetadataFromExtension` when `sAnime.initialized == true` (i.e. `getAnimeDetails` was called). The `loadEpisodes()` path (used by `reloadEpisodesOnly` in AniList mode) builds a partial SAnime with just `url`+`title` + `initialized = false` — calling `updateMetadataFromExtension` there would null out `description`/`genre`/`artist`/`author`/`cover_url`. The `loadByExtension()` path always enriches the SAnime via `enrichAnimeDetails` before reaching `persistEpisodes`, so the guard passes there.
    - Wrapped in try/catch (non-fatal) + added `Log.i(TAG, "persistEpisodes: updateMetadataFromExtension applied to row id=...")` + `Log.w` on failure + `Log.d` when skipped.

### Fix 4 — switchExtension doesn't force-refresh

11. **`feature/anime-details/.../AnimeDetailViewModel.kt`** (same file as Fix 2) — in `switchExtension`, changed the Extension-mode branch from `load()` to `loadInternal(forceRefresh = true)`. This bypasses the DB-first short-circuit so the provider re-fetches from the new extension + calls `updateMetadataFromExtension` (Fix 3) to overwrite the row's metadata. Added `Log.i(TAG, "switchExtension: Extension mode — calling loadInternal(forceRefresh=true) ...")`.

### Fix 5 — setupCurrentMatch overwrites the user-picked SAnime

12. **`feature/anime-details/.../AnimeDetailViewModel.kt`** (same file) — rewrote `setupCurrentMatch` with a defensive guard:
    ```kotlin
    private fun setupCurrentMatch(anime: UnifiedAnime) {
        val sourceId = anime.sourceId ?: return
        // Don't clobber a freshly-set _currentMatch from switchExtension.
        val existing = _currentMatch.value
        if (existing != null && existing.source.id == sourceId) {
            Log.d(TAG, "setupCurrentMatch: skipping overwrite — ...")
            return
        }
        val source = sourceMatcher.getSourceById(sourceId) ?: return
        val sAnime = SAnimeImpl().apply { url = anime.url; title = anime.title }
        _currentMatch.value = SourceMatcher.SourceMatch(source, sAnime, 1.0)
    }
    ```
    This prevents `loadInternal` (called after `switchExtension`) from clobbering the user-picked SAnime (which has the correct title from the new source) with a fresh SAnime built from the (possibly stale) DB row's title. Added `Log.i`/`Log.d`/`Log.w` diagnostics.

### Fix 6 — "AniList" should be "unknown" when no source has the anime

13. **`feature/anime-details/.../SourceSwitcherMenu.kt`** — in the data-source indicator `DropdownMenuItem`, replaced the hardcoded `"Current: ${anime.sourceName}"` + `"Data source: ${currentDataSource.name.lowercase()}"` with two computed labels:
    ```kotlin
    val currentSourceLabel = when {
        anime.sourceId != null && !anime.sourceName.isNullOrBlank() -> anime.sourceName
        else -> "unknown"
    }
    val dataSourceLabel = when {
        currentDataSource == DataSource.ANILIST && anime.sourceId == null -> "unknown"
        else -> currentDataSource.name.lowercase()
    }
    ```
    This shows "unknown" when `anime.sourceId == null` (no extension matched) instead of the misleading "AniList" fallback (which provides metadata only, no episodes).

## Cross-fix dependencies (verified)

- **Fix 2 (unlink refresh)** depends on **Fix 3** (`updateMetadataFromExtension`): without Fix 3, the force-refresh would re-fetch from the extension but NOT overwrite the stale AniList metadata on the DB row. With both fixes, the post-unlink page shows fresh extension data.
- **Fix 4 (switchExtension force-refresh)** depends on **Fix 3**: same dependency — the force-refresh re-fetches, and Fix 3 overwrites the stale metadata.
- **Fix 5 (setupCurrentMatch guard)** is a defensive quick-win independent of Fixes 3/4. It prevents the stale-title-overwrite race regardless of whether the DB row's title has been updated yet.
- **Fix 6** is independent (pure UI labeling).
- **Fix 1** is independent (navigation target change).

## Issues encountered

- **No Android SDK / JDK 17 in the environment.** The build cannot be verified locally — `./gradlew :app:compileDebugKotlin` fails at "SDK location not found" + the buildSrc requires JDK 17 (only JDK 21 is on PATH; a JDK 17 exists at `/tmp/jdk17/jdk-17.0.20+8` but the Android SDK is not installed). Per the project README ("Built only via CI" — ADR-003), compilation is deferred to CI. All changes were reviewed manually for type-safety, SQLDelight parameter naming, and call-site compatibility.
- **Pre-existing race in `switchExtension`** (unlinked extension anime only): `updateSourceAndUrl` is launched in a fire-and-forget coroutine, then `loadInternal(forceRefresh = true)` runs concurrently. If `loadInternal`'s `persistEpisodes` runs first, it may INSERT a new row (with the new source_id + url) before `updateSourceAndUrl` updates the OLD row — causing a unique-constraint violation on `idx_animes_source_url`. This is a pre-existing issue (not introduced by these fixes) and is out of scope. Flagged for a follow-up.
- **`forceInitialRefresh` not in the Voyager `key`:** intentional — the key is hardcoded per source+url so it doesn't change with `forceInitialRefresh`. The VM is freshly created on each `navigator?.replace(...)` (new screen entry → new ViewModelStoreOwner), so the `forceInitialRefresh` value is captured correctly at VM creation time.

## Files read fully (for verification)

- `app/.../navigation/AppController.kt` (1005 lines — `onLinked`, `unlinkFromAniList`, `pushExtensionDetail`, `onGoWithoutLinking`, `openLibraryAnime`)
- `app/.../navigation/AnikutaRoot.kt` (215 lines — `ExtensionLinkingSheet` call + `onLinked` lambda)
- `app/.../navigation/Destinations.kt` (623 lines — `ExtensionAnimeDetailDestination`, `LibraryExtensionDetailDestination`, `AnimeDetailDestination`)
- `feature/anime-details/.../AnimeDetailScreen.kt` (316 lines — VM factory + parameter threading)
- `feature/anime-details/.../AnimeDetailViewModel.kt` (868 lines — `init`, `loadInternal`, `switchExtension`, `setupCurrentMatch`)
- `feature/anime-details/.../SourceSwitcherMenu.kt` (252 lines — data-source indicator)
- `data/extension/.../details/ExtensionDetailsProvider.kt` (444 lines — `loadByExtension`, `fetchAndPersistEpisodes`, `persistEpisodes`, `loadEpisodes`, `enrichAnimeDetails`)
- `data/extension/.../details/SAnimeMapper.kt` (143 lines — `toUnifiedAnime`, `mergeAniListMetadata`)
- `core/database/.../animes.sq` (252 lines — `update`, `updateSourceAndUrl`, `clearAnilistId`)
- `core/common/.../repository/AnimeRepository.kt` (145 lines — interface)
- `data/anime/.../AnimeRepositoryImpl.kt` (377 lines — `upsert`, `updatePreferredCoverBySourceAndUrl`)
- `data/anime/.../AnimeMapper.kt` (151 lines — column → field mapping)
- `core/common/.../model/Anime.kt` (87 lines — `status: Int` confirmed)
- `core/source-api/.../SAnime.kt` (70 lines — `genre: String?`, `getGenres(): List<String>?`, `initialized: Boolean` confirmed)
- `feature/search/.../ui/ExtensionLinkingSheet.kt` (441 lines — `onLinked` callback signature)
- `feature/search/.../viewmodel/ExtensionLinkingViewModel.kt` (190 lines — `Linked` state shape)
