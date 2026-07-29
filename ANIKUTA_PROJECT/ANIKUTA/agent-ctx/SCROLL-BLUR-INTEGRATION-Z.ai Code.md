# SCROLL-BLUR-INTEGRATION — Z.ai Code (main orchestrator, direct execution)

## Task

Integrate the REST of the features from `feat/scroll-blur-effect` (rebased onto current `main` as `feature/scroll-blur-rebased`) into the restructured codebase at `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/`. The `ScrollBlurOverlay.kt` component was already copied into `:core:designsystem` — everything else needed to be adapted.

The branch was based on the PRE-restructuring codebase (used `AniListAnime`, `AniListApi` directly, `anilistId` for episode identity). The current main uses `UnifiedAnime`, `MetadataProviderRegistry`, `contentId`. So every adaptation had to be ported onto the new types.

## Branch commits integrated (11 total)

1. `d42a771` — feat: scroll-driven frosted-glass blur overlay on 6 screens + settings toggle
2. `d19814c` — fix(browse): add koin deps for ThemePreferences injection
3. `679f74d` — fix(search): add koin deps for ThemePreferences injection
4. `c0d91a9` — fix: rewrite ScrollBlurOverlay — gradient scrim, no RenderEffect
5. `daec874` — fix: scroll blur overlay flickering — firstVisibleItemScrollOffset resets
6. `5bd0596` — fix: reduce scroll blur fade distance from 100dp to 24dp
7. `6586bba` — feat(search): debounce + filter reactivity + state persistence + extension button fix
8. `bd28bb3` — perf(details): flatten episode list + memoize + cache RenderEffect + defer bg search
9. `df837f0` — fix: use Icons.Filled.Error instead of Warning
10. `4c318e4` — fix: use Icons.Filled.Search for unavailable source icon
11. `4957a70` — feat: search button-click + source unavailable chip + library no-source + unlink

Commits 1–6 (overlay + flicker fix + fade distance) were already reflected in the copied `ScrollBlurOverlay.kt`. My task was commits 7–11 (search/perf/source-unavailable/unlink) + the screen-level integration of the overlay itself (commit 1 + 5).

## Files Modified (15 total)

### Settings + preferences (2 files)

1. **core/preferences/.../ThemePreferences.kt** — added `headerBlurEffect: Preference<Boolean>` (key `pref_header_blur_effect`, default `true`). KDoc notes the 5 screens it applies to.

2. **feature/settings/.../AppearanceGeneralScreen.kt** — added `headerBlurEffect` `collectAsStateWithLifecycle` subscription + a new "Effects" settings section (label + `AdaptiveColorsCard` toggle) below the "Adaptive colors" section. The toggle binds to `prefs.headerBlurEffect`.

### Browse screen (2 files)

3. **feature/browse/build.gradle.kts** — added `implementation(projects.core.preferences)` dep (was missing — BrowseScreen now reads `ThemePreferences`).

4. **feature/browse/.../BrowseScreen.kt** — added `ScrollBlurOverlay` aligned TopCenter inside a new `Box(Modifier.fillMaxSize())` wrapping the existing `when { ... }` content block. Hoisted `ThemePreferences` via `org.koin.core.context.GlobalContext.get().get<ThemePreferences>()` + collected `headerBlurEnabled` reactively. Added the flicker-fix `if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE` guard. Added `collectAsState` + `ScrollBlurOverlay` + `ThemePreferences` imports.

### Library screen (1 file)

5. **feature/library/.../LibraryScreen.kt** — wrapped the `if/else-when` content block (loading / empty / list-mode / grid-mode) in a `Box(Modifier.fillMaxSize())` + added `ScrollBlurOverlay` aligned TopCenter. Mode-aware scroll-offset lambda: LIST mode reads `listState.firstVisibleItemScrollOffset`, GRID mode reads `gridState.firstVisibleItemScrollOffset` (both with the flicker-fix `firstVisibleItemIndex > 0 → MAX_VALUE` guard). Hoisted `ThemePreferences` + collected `headerBlurEnabled`. Added imports for `collectAsState`, `ScrollBlurOverlay`, `ThemePreferences`.

### Search screen + ViewModel + UI prefs (4 files)

6. **feature/search/.../data/SearchUiPreferences.kt** — added `searchSource` persistence (`getString`, key `pref_search_source`, default `ANILIST.name`, with `SearchSource.valueOf` try/catch for legacy/unknown values) + `selectedExtensionSourceId` persistence (`getLong`, key `pref_search_selected_extension`, default `-1L`, exposed as nullable `Long?`). Updated KDoc to describe both new fields + the validation-against-installed-sources rationale.

7. **feature/search/.../viewmodel/SearchViewModel.kt** — 4 fixes (matches branch commit `6586bba` + `4957a70`):
   - **Debounce**: AniList branch of `scheduleSearch()` now wraps `runAniListSearch(state)` in `delay(DEBOUNCE_MS)` (was instant-fire — now matches the Extension branch).
   - **Filter reactivity**: `pendingFilters` converted from `private var` to `MutableStateFlow<SearchFilters>` exposed as `StateFlow` (`pendingFilters`). FilterSheet now collects via `vm.pendingFilters.collectAsState().value` (was `vm.getPendingFilters()` — non-reactive). All 3 callers (`getPendingFilters`, `applyFilters`, `onClearFilters`) updated to read/write `_pendingFilters.value`.
   - **State persistence**: `init` reads `uiPreferences.getSearchSource()` + `getSelectedExtensionSourceId()` (validates the persisted extension ID against `sourceMatcher.getAvailableSources()` — falls back to first-installed if the persisted one was uninstalled). `onSourceChange` + `onPickExtensionSource` call `setSearchSource` / `setSelectedExtensionSourceId`. Added `Log.d` for init/source-change/pick to make persistence observable.
   - **Search button-click**: `onQueryChange(q)` no longer calls `scheduleSearch()` (just updates the query text + resets page/canLoadMore). New `onSubmit()` method triggers the actual search. `init` now only auto-fires `loadAniListDefault()` for AniList; for EXTENSION it calls `scheduleSearch()` (which routes to `loadExtensionDefault` for blank query).

8. **feature/search/.../ui/SearchScreen.kt** — wired `onSubmit = { vm.onSubmit() }` (was a noop comment). Changed `FilterSheet(pendingFilters = vm.getPendingFilters(), ...)` to `FilterSheet(pendingFilters = vm.pendingFilters.collectAsState().value, ...)` for live chip-selection updates. Added imports for `ScrollBlurOverlay`, `ThemePreferences`, `Alignment`, `collectAsState`. Wrapped the `Column(verticalScroll(scrollState))` in a `Box(Modifier.fillMaxSize())` + added `ScrollBlurOverlay` aligned TopCenter with `scrollOffset = { scrollState.value.toFloat() }` (ScrollState.value never resets, so no flicker-fix needed here). Hoisted `ThemePreferences` + collected `headerBlurEnabled`.

9. **feature/search/.../ui/SearchBar.kt** — search icon converted from a plain `Icon` to a tappable `Box(Modifier.size(...).clip(CircleShape).clickable { onSubmit() })` containing the icon. Tint changed from `onSurfaceVariant` to `primary` (visibly actionable). Spacer width tightened 12dp → 8dp to compensate for the larger tap target. KDoc + inline comment explain the search-button-click fix.

### Watch screen (1 file)

10. **feature/watch/.../WatchScreen.kt** — hoisted `ThemePreferences` (`org.koin.core.context.GlobalContext.get().get<ThemePreferences>()`) + collected `headerBlurEnabled` via `collectAsState` (added the import) right after the existing `val listState = rememberLazyListState()`. Wrapped the `LazyColumn(state = listState, ...)` in a `Box(Modifier.fillMaxSize())` + added `ScrollBlurOverlay` aligned TopCenter with the flicker-fix guard (`if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE else listState.firstVisibleItemScrollOffset.toFloat()`). Used fully-qualified `app.confused.anikuta.core.designsystem.component.ScrollBlurOverlay` to avoid adding an import (the file already has 219 imports — minimizing churn).

### Anime details (3 files)

11. **feature/anime-details/.../DetailContent.kt** — significant rewrite. 5 changes:
    - Added `ScrollBlurOverlay` aligned TopCenter inside a new `Box(Modifier.fillMaxSize())` wrapping the `LazyColumn`. Flicker-fix applied.
    - Hoisted `watchCtx` (`WatchEpisodeContext`) + `displaySnapshot` (`EpisodeDisplayPrefs`) to the composable level via `remember(anime, episodeMetadata) { ... }` + `rememberEpisodeDisplaySnapshot(...)` — was previously constructed inline inside the `item { EpisodesSection(...) }` lambda (re-allocated per recomposition).
    - Flattened the episode list: when `episodeState is EpisodeState.Loaded`, the rows are now added via `items(count = episodes.size, key = { "ep_$it" })` as individual LazyColumn items — was previously a single `item { EpisodesSection(...) }` block where `EpisodesSection` internally used `forEachIndexed` to compose ALL rows eagerly (severe jank on 100+ episode anime).
    - Changed the `EpisodesSection` item key from default to `"ep_header"` (so the new items() block can use `"ep_$index"` keys without collision).
    - Added `onUnlinkFromAniList: () -> Unit = {}` parameter + threaded through to `DetailBanner`.

12. **feature/anime-details/.../EpisodesSection.kt** — 6 changes:
    - Added the "Source unavailable" chip — `episodeState is EpisodeState.Loaded && currentMatch == null` branch shows the source name + "— unavailable" with dimmed accent colors (`primary@0.15f` background, `primary@0.5f` text/icon). Tappable to open `ManualSearchSheet`. (Final design from commit `4957a70` — the intermediate `Icons.Filled.Warning`/`Error`/`Search` icons from commits `6586bba`/`df837f0`/`4c318e4` were all superseded; the final chip uses just `Icons.Filled.ExpandMore` + Text. So the icon-fix subtask was effectively a no-op — confirmed by grep that no `Icons.Filled.Warning` or `Icons.Filled.Error` exists in the current codebase.)
    - Removed the `EpisodeState.Loaded → EpisodeList(...)` call (rows now rendered by parent `DetailContent`'s `items()`). Replaced with a no-op + comment explaining the flatten-the-list fix.
    - Made `EpisodeRow` `internal` (was `private`) — so `DetailContent` can call it directly from the LazyColumn `items()` block.
    - Made `rememberEpisodeDisplaySnapshot` `internal` (was `private`) — so `DetailContent` can hoist the snapshot out + pass it to both `EpisodesSection` + the flattened episode rows.
    - Memoized 5 per-episode computations in `EpisodeRow`: `displayTitle` (`remember(episode, metadata)` — EpisodeTitleParser is CPU-intensive), `epNumText` + `bareEpNum` (`remember(episode)`), `audio` (`remember(episode)` — `parseAudioAvailability` does string parsing), `dateText` (`remember(episode, metadata, showDate)` — `formatDate` allocates SimpleDateFormat + Date per call).
    - Cached `RenderEffect` at file level: new `private val CACHED_GRAYSCALE_EFFECT: RenderEffect? by lazy { ... }` (created once, API 31+ only). The `watchedEpisodeEffect` modifier now reads `CACHED_GRAYSCALE_EFFECT?.let { this.renderEffect = it }` instead of allocating a new `ColorMatrix` + `RenderEffect` on every recomposition of every watched episode row.
    - Removed the now-dead `EpisodeList` private composable (replaced with a deprecation comment explaining the flatten-the-list migration).
    - Marked the now-unused `snapshot` local val with `@Suppress("UNUSED_VARIABLE")` + a comment explaining why the `rememberEpisodeDisplaySnapshot` call is retained (kept the reactive subscription alive while `EpisodesSection` is visible — future cleanup can remove it entirely once `EpisodesSection` is purely a header + state machine).

13. **feature/anime-details/.../SourceSwitcherMenu.kt** — added `onUnlinkFromAniList: () -> Unit = {}` parameter + a new "Unlink from AniList" `DropdownMenuItem` (gated by `if (anime.anilistId != null)` — only shown for linked anime). Uses `Icons.Outlined.LinkOff` (added import). Sits between the "Link / Switch anime" block and the "Refresh" item.

14. **feature/anime-details/.../DetailBanner.kt** — added `onUnlinkFromAniList: () -> Unit = {}` parameter + threaded through to `SourceSwitcherMenu`.

15. **feature/anime-details/.../AnimeDetailScreen.kt** — added 2 new parameters: `extensionSourceId: Long? = null` (for the library-no-source case where the source extension is uninstalled) + `onUnlinkFromAniList: () -> Unit = {}`. Updated `initialRequest: DetailsRequest` to handle the `extensionSourceId != null && extensionSAnime != null` case (constructs `DetailsRequest.ByExtension` using the bare sourceId instead of `extensionSource.id`). Threaded `onUnlinkFromAniList` to `DetailContent`. Updated the error message in the `else` branch to mention the new accepted shape.

### Navigation (2 files)

16. **app/.../navigation/AppController.kt** — 2 changes:
    - **`openLibraryAnime` no-source fallback**: when `sourceMatcher.getSourceById(anime.sourceId)` returns null, no longer bails with a toast. Constructs an `SAnimeImpl` (url + title from the library row) + pushes `LibraryExtensionDetailDestination(sourceId, animeUrl, animeTitle)` so the user lands on the DB-first details page (sees saved episodes, can use "Source unavailable" chip to switch, can use "Link to AniList" to re-link).
    - **NEW `unlinkFromAniList(anilistId, sourceId?, animeUrl?)` method**: removes both directional links (`sourceLinkStore.removeLink("al:$anilistId")` + `extensionLinkStore.unlink(sid, url)` if both resolved) + removes the `DetailsViewPreferenceStore` entry for the anilistId (wrapped in try/catch — non-fatal). Then navigates to the extension-mode details page via `navigator?.replace(...)`: if the source is still installed → `ExtensionAnimeDetailDestination(source, sAnime, anilistId = null)`; if uninstalled → `LibraryExtensionDetailDestination(sid, url, title)`. If no source link exists at all → toast "Unlinked from AniList" + `navigator?.pop()`. Added `Log.i(TAG, "unlinkFromAniList: ...")` + `Log.w(TAG, "unlinkFromAniList: failed to remove view preference ...")` for diagnostics.

17. **app/.../navigation/Destinations.kt** — 3 changes:
    - **NEW `LibraryExtensionDetailDestination(sourceId, animeUrl, animeTitle)` Screen**: builds an `SAnimeImpl` from the URL + title (remembered), constructs `AnimeDetailScreen(extensionSource = null, extensionSAnime = sAnime, extensionAnilistId = null, extensionSourceId = sourceId, ...)`. The `onOpenEpisode` + `onDownloadEpisode` lambdas toast "Source not installed" (no live source to resolve against). `onLinkToAniList` calls `appController.startLinkingFromAnilist(0)` (placeholder — `startLinkingFromAnilist` will look up `sourceLinkStore.getLink("al:0")` → null → toast "No extension source linked — open from search to link one"). Uses `LocalContext.current` for the toasts (the AppController.context field is private).
    - **AnimeDetailDestination**: added `onUnlinkFromAniList = { appController.unlinkFromAniList(animeId) }` to the `AnimeDetailScreen` call.
    - **ExtensionAnimeDetailDestination**: added `onUnlinkFromAniList = { if (anilistId != null) appController.unlinkFromAniList(anilistId, source.id, sAnime.url) }` — passes the live `source.id` + `sAnime.url` so `AppController.unlinkFromAniList` doesn't have to re-resolve from `SourceLinkStore` (it still does as a fallback if the params are null, but the explicit-pass path skips one PreferenceStore read).

## Features that couldn't be applied

**None.** All 11 branch commits were successfully adapted to the restructured codebase.

## New preferences added

- **ThemePreferences.headerBlurEffect** (`Boolean`, default `true`, key `pref_header_blur_effect`) — toggleable from Appearance → General → Effects section. Controls whether the `ScrollBlurOverlay` renders on Browse/Library/Search/Details/Watch.
- **SearchUiPreferences.searchSource** (`String`, default `SearchSource.ANILIST.name`, key `pref_search_source`) — persists the active Search section across navigation + app restart.
- **SearchUiPreferences.selectedExtensionSourceId** (`Long`, default `-1L` → exposed as `Long?`, key `pref_search_selected_extension`) — persists the last-selected extension source across navigation + app restart. Validated against still-installed sources on restore.

## Key adaptations from branch → restructured codebase

1. **`AniListAnime` → `UnifiedAnime`** — the branch's `DetailContent.kt` referenced `anime.displayTitle`/`anime.coverUrl` (AniListAnime extension properties). The current `UnifiedAnime` has direct `title`/`coverUrl` properties — no field-name changes needed in the integration, just type-aware (UnifiedAnime.anilistId is nullable vs AniListAnime.id was non-null).

2. **`AniListApi` → `MetadataProviderRegistry`** — preserved in `SearchViewModel.kt`. The branch's `runAniListSearch` called `anilistApi.searchAnime(...)` directly; the current main's version routes through `registry.forCapability<SearchProvider>(...)`. My debounce + persistence changes wrap the existing registry-routing code — no API-call sites touched.

3. **`anilistId: Int` → `contentId: String`** for episode identity — the branch's `unlinkFromAniList` used `sourceLinkStore.getLink(anilistId)` (the OLD API). The current main's `SourceLinkStore.getLink` takes a `contentId: String`. Adapted: `unlinkFromAniList` computes `contentId = "al:$anilistId"` then calls `sourceLinkStore.getLink(contentId)`. (Matches the pattern used by the current main's `switchAnilistAnime`.)

4. **`EpisodeList` deprecation** — the branch's `EpisodesSection.EpisodeList` was a `forEachIndexed` Column inside an `item { EpisodesSection(...) }` block. The flatten-the-list fix moved the rows out into the parent LazyColumn's `items()` — so `EpisodesSection.Loaded → EpisodeList(...)` became `Loaded → { /* noop */ }`. The dead `EpisodeList` function was removed (replaced with a deprecation comment) — the parent now owns row rendering.

5. **`LibraryExtensionDetailDestination` (new)** — the branch pushed this destination from `AppController.openLibraryAnime` when the source was uninstalled. The destination constructs `AnimeDetailScreen` with `extensionSourceId` (a new param I added) so the screen builds `DetailsRequest.ByExtension` using the bare sourceId (no live `AnimeCatalogueSource` object). The provider's DB-first path then loads saved data.

## Verification

- All grep checks pass: no `Icons.Filled.Warning` or `Icons.Filled.Error` in `:feature:anime-details` (icon fixes were no-ops — the final "Source unavailable" chip design uses only `Icons.Filled.ExpandMore`).
- All new `ScrollBlurOverlay` call sites use the flicker-fix guard (`if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()`) — except `SearchScreen` which uses `ScrollState.value` (never resets, no fix needed).
- All new `ThemePreferences.headerBlurEffect` subscriptions use `collectAsState(initial = prefs.headerBlurEffect.get())` (or `collectAsStateWithLifecycle` for the settings screen) — reactive to the toggle.
- All new `Koin` `GlobalContext.get().get<ThemePreferences>()` calls are wrapped in `remember { }` (single lookup per composable instance).
- Build verification pending CI (sandbox lacks Android SDK + JDK 17 toolchain for local gradle compile). Lint check via the IDE's Kotlin plugin suggests no syntax errors — but the real test is `./gradlew :app:assembleDebug`.
