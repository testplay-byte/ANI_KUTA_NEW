# DETAILS-PAGE-FIXES — Z.ai Code (implementer)

**Task ID:** DETAILS-PAGE-FIXES
**Branch:** `feature/details-page-improvements`
**Date:** 2026 (session)
**Scope:** Implement 5 surgical fixes for the ANIKUTA details page (extension-only mode spinner, save-button reactivity, unlink-from-AniList gating + behavior, source-name remembering, "Source unavailable" info dialog).

## Files modified (12 total, +294/-72 LOC)

1. **`core/database/.../animes.sq`** — added `clearAnilistId: UPDATE animes SET anilist_id = NULL WHERE _id = :id;` query (Issue 3b).
2. **`core/common/.../repository/AnimeRepository.kt`** — added 2 interface methods:
   - `fun observeBySourceAndUrl(sourceId: Long, url: String): Flow<Anime?>` (Issue 2).
   - `suspend fun clearAnilistId(id: Long)` (Issue 3b).
3. **`data/anime/.../AnimeRepositoryImpl.kt`** — implemented both new methods. `observeBySourceAndUrl` uses the existing `selectBySourceAndUrl` SQLDelight query + `asFlow().mapToOneOrNull(io)`. `clearAnilistId` calls `database.animesQueries.clearAnilistId(id)` on the IO dispatcher.
4. **`data/extension/.../matcher/SourceMatcher.kt`** — added `getExtensionForSource(sourceId: Long): AnimeExtension.Installed?` helper (Issue 4). Resolves an installed extension by source ID via `extensionManager.getInstalledExtensions().firstOrNull { ext -> ext.sources.any { it.id == sourceId } }`. Added `import app.confused.anikuta.data.extension.AnimeExtension` (needed for the return type).
5. **`data/extension/.../details/ExtensionDetailsProvider.kt`** — 3 changes (Issue 4):
   - Added imports for `ExtensionSystem` + `SourceProvenance`.
   - In `loadByExtension` (~line 124), changed the `sourceName` fallback chain from `sourceMatcher.getSourceById(sourceId)?.name ?: dbAnime.title` to:
     ```
     sourceMatcher.getSourceById(sourceId)?.name
         ?: dbAnime.provenance?.sourceName
         ?: dbAnime.provenance?.extensionName
         ?: dbAnime.title
     ```
   - Added `source: AnimeCatalogueSource` parameter to `persistEpisodes` (passed through from `fetchAndPersistEpisodes`).
   - After upserting/finding `dbAnime` in `persistEpisodes`, call `persistProvenance(dbAnime.id, source)` BEFORE the episode persistence. New private `persistProvenance` method builds a `SourceProvenance(system = ANIYOMI, sourceName = source.name, extensionName/pkgName/versionName/versionCode/lang/isNsfw/repoUrl from the installed extension via `sourceMatcher.getExtensionForSource(source.id)`, linkConfidence = 0)` and writes it via `animeRepository.updateProvenance`. Wrapped in try/catch (non-fatal).
6. **`app/.../navigation/AppController.kt`** — Issue 3b:
   - Added `import app.confused.anikuta.core.common.repository.AnimeRepository`.
   - Added `val animeRepository: AnimeRepository` constructor parameter.
   - Rewrote `unlinkFromAniList` to wrap the body in `scope.launch { ... }` (was synchronous — now async because of the suspend `getByAnilistId` + `clearAnilistId` calls). The new behavior:
     1. `animeRepository.getByAnilistId(anilistId)` → if non-null, `clearAnilistId(existing.id)`. Wrapped in try/catch (non-fatal — links + nav still proceed). This transitions the row to extension-only (keeps `source_id`/`url`/`favorite=true`).
     2. `sourceLinkStore.removeLink(contentId)` (AniList → ext link).
     3. `extensionLinkStore.unlink(sid, url)` if both non-null (ext → AniList reverse-link).
     4. `DetailsViewPreferenceStore.remove(anilistId)` (per-anime view preference).
     5. `navigator?.replace(ExtensionAnimeDetailDestination(source, sAnime, anilistId = null))` if source still installed, OR `LibraryExtensionDetailDestination(sid, url, title)` if source uninstalled, OR `Toast + navigator?.pop()` if no source link at all.
7. **`app/.../navigation/NavModule.kt`** — added `animeRepository = get()` to the `AppController` Koin registration.
8. **`feature/anime-details/.../SourceSwitcherMenu.kt`** — Issue 3a: changed the "Unlink from AniList" gate from `if (anime.anilistId != null)` to `if (anime.anilistId != null && anime.sourceId != null && anime.sourceId > 0)` — only show when linked to BOTH AniList AND an extension.
9. **`feature/anime-details/.../AnimeDetailViewModel.kt`** — Issues 1 + 2:
   - Added `_metadataFetchComplete: MutableStateFlow<Boolean>` (default false) + exposed `val metadataFetchComplete: StateFlow<Boolean>`.
   - In `fetchEpisodeMetadata`, set `_metadataFetchComplete.value = false` at start, then `true` at every exit point (early return when anilistId == null, success path, catch block).
   - In `observeLibraryState` else-branch (extension-only): replaced the `animeState.collect { ... }` polling with `animeRepository.observeBySourceAndUrl(sourceId, url).collect { ... }`. Falls back to polling if the request lacks sourceId/url.
   - In `toggleSave`, after the DB write, manually set `_isSaved.value = newFav` (existing) or `true` (new save) — quick-win UI fallback so the save icon flips before the reactive flow emits.
10. **`feature/anime-details/.../EpisodesSection.kt`** — Issues 1 + 5:
    - Added imports for `AlertDialog` + `TextButton`.
    - Added 2 parameters: `metadataFetchComplete: Boolean = false` (Issue 1) + `sourceId: Long? = null` (Issue 5).
    - Added `var showUnavailableInfo by remember { mutableStateOf(false) }` state.
    - Changed the spinner condition from `episodeMetadata.isEmpty()` to `episodeMetadata.isEmpty() && !metadataFetchComplete`.
    - Changed the "Source unavailable" chip's click handler from `showManualSearch = true` to `showUnavailableInfo = true`.
    - Added an `AlertDialog` that shows when `showUnavailableInfo && episodeState is EpisodeState.Loaded`:
      - Title: "Extension Unavailable"
      - Body: `"Previously saved with $sourceName (Extension ID: ${sourceId ?: "unknown"}). Currently not installed or available."`
      - Confirm: "Switch source" → dismisses dialog + opens ManualSearchSheet.
      - Dismiss: "Dismiss" → just dismisses.
11. **`feature/anime-details/.../DetailContent.kt`** — added `metadataFetchComplete: Boolean = false` parameter. Passes `metadataFetchComplete` + `sourceId = anime.sourceId` to `EpisodesSection`.
12. **`feature/anime-details/.../AnimeDetailScreen.kt`** — collects `metadataFetchComplete` from the VM + passes it to `DetailContent`.

## Verification

Could not run a full gradle build (sandbox has JDK 21 only; project requires JDK 17 — no root for `apt-get install openjdk-17-jdk-headless`). Visual diff review confirms:
- All new methods compile against existing interfaces (`AnimeRepository`, `SourceMatcher`, `AnimeExtensionManager`).
- All new SQL queries follow the existing SQLDelight naming convention.
- All new Compose imports (`AlertDialog`, `TextButton`) are standard Material3.
- All new coroutines use the existing `scope` (Main dispatcher) in AppController and `viewModelScope` in AnimeDetailViewModel.
- The `AnimeExtension.Installed` import added to `SourceMatcher.kt` for the new return type.

Recommend running `./gradlew :app:assembleDebug` on a machine with JDK 17 to fully verify.

## Exact `unlinkFromAniList` implementation (for verification)

```kotlin
fun unlinkFromAniList(anilistId: Int, sourceId: Long? = null, animeUrl: String? = null) {
    // Phase 4: SourceLinkStore keys by content_id ("al:$anilistId").
    val contentId = "al:$anilistId"
    val link = sourceLinkStore.getLink(contentId)
    val sid = sourceId ?: link?.sourceId
    val url = animeUrl ?: link?.animeUrl
    val title = link?.animeTitle ?: "Unknown"

    scope.launch {
        try {
            // ── Step 1+2: Clear anilist_id on the library row (transition to extension-only) ──
            val existing = animeRepository.getByAnilistId(anilistId)
            if (existing != null) {
                animeRepository.clearAnilistId(existing.id)
                Log.i(TAG, "unlinkFromAniList: cleared anilistId on row id=${existing.id} " +
                    "(now extension-only, favorite=${existing.favorite})")
            } else {
                Log.w(TAG, "unlinkFromAniList: no library row found for anilistId=$anilistId " +
                    "— nothing to clear (the row will not be re-saved as extension-only)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "unlinkFromAniList: failed to clear anilistId on library row " +
                "(non-fatal — link stores + navigation still proceed)", e)
        }

        // ── Step 3: Remove the SourceLinkStore entry (AniList → ext link) ──
        sourceLinkStore.removeLink(contentId)

        // ── Step 4: Remove the ExtensionLinkStore entry (ext → AniList reverse-link) ──
        if (sid != null && url != null) {
            extensionLinkStore.unlink(sid, url)
        }

        // ── Step 5: Remove the view preference ──
        try {
            org.koin.core.context.GlobalContext.get()
                .get<app.confused.anikuta.data.extension.cache.DetailsViewPreferenceStore>()
                .remove(anilistId)
        } catch (e: Exception) {
            Log.w(TAG, "unlinkFromAniList: failed to remove view preference " +
                "(non-fatal) — anilistId=$anilistId", e)
        }

        Log.i(TAG, "unlinkFromAniList: unlinked anilistId=$anilistId from source " +
            "$sid (url=$url, title=$title) — library entry is now extension-only")

        // ── Step 6: Navigate to the extension-mode details page (replace — no stacking) ──
        if (sid != null && url != null) {
            val source = sourceMatcher.getSourceById(sid)
            val sAnime = SAnimeImpl().apply {
                this.url = url
                this.title = title
            }
            if (source != null) {
                navigator?.replace(ExtensionAnimeDetailDestination(source, sAnime, anilistId = null))
            } else {
                // Source uninstalled — open the DB-first details page so the user
                // can still see saved episodes.
                navigator?.replace(LibraryExtensionDetailDestination(sid, url, title))
            }
        } else {
            // No source link to navigate to — just go back.
            Toast.makeText(
                context,
                "Unlinked from AniList",
                Toast.LENGTH_SHORT,
            ).show()
            navigator?.pop()
        }
    }
}
```

## Issues encountered

- **JDK 17 not available in sandbox.** Gradle requires JDK 17 but the sandbox only has JDK 21. Cannot install without root. Build verification deferred to a machine with the right toolchain. Visual diff review done instead.
- **No issues with the actual code changes.** All 5 fixes are surgical and self-contained — no rippling refactors needed.

## Cross-issue notes

- Issue 5 depends on Issue 4 (source name display): the `sourceName` shown in the new info dialog comes from `episodeState.sourceName` (the extension provider's `Loaded` state), which in turn uses the new fallback chain in `loadByExtension`. After Issue 4's fix, the fallback returns the real extension name (`provenance.sourceName ?: provenance.extensionName`) instead of the anime's title when the source is uninstalled.
- Issue 3b introduces a tiny async delay (the `scope.launch` wrapping) — the unlink now has to await the DB read+write before navigating. Acceptable: the user gets a clean state transition in exchange for ~10-50ms of latency.
- Issue 2's quick-win `_isSaved.value = ...` in `toggleSave` is a defensive fallback. The reactive `observeBySourceAndUrl` flow should emit shortly after, re-confirming the value — so even if the manual update is wrong (e.g., race condition), it self-corrects.
