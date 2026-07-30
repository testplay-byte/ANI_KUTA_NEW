# ANIKUTA Details Page — Architecture Analysis (EXT-DETAILS-TASK2)

> Scope: the unified `AnimeDetailScreen` in `:feature:anime-details` — the page that will receive extension data via a future pluggable data-translation layer. Plus the soon-to-be-deprecated `ExtensionDetailScreen.kt` "before" picture, and the Phase 9 palette/cover-color integration points.
>
> All paths are relative to repo root (`/home/z/my-project/anikuta/`).
> Branch at time of analysis: `feature/extension-details-page` (HEAD `6cf3526`, branched from `feature/voyager-navigation`).

---

## 0. Module at a Glance

`:feature:anime-details` lives at `ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/`. It declares only the `anikuta.library.compose` convention plugin and depends on `:core:common`, `:core:designsystem`, `:core:anilist`, `:core:preferences`, `:core:episode-metadata`, `:core:source-api`, `:data:extension`, plus Koin, Coil3, coroutines, and Lifecycle (see `ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/build.gradle.kts:9-41`). The module's README still says "Status: Skeleton (Phase 1)" — **stale**; the module is fully implemented (15 source files, ~4.5 KLOC).

Source files in `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/`:

| File | LOC | Role |
|---|---:|---|
| `AnimeDetailScreen.kt` | 235 | Top-level Composable entry point for AniList-driven details. Owns VM + dynamic-theme wrap. |
| `AnimeDetailViewModel.kt` | 785 | The 3-stage load orchestrator + state holder. |
| `DetailContent.kt` | 171 | The `LazyColumn` that lays out banner → genres → synopsis → episodes → info, wrapped in `PullToRefreshBox`. |
| `DetailBanner.kt` | 214 | Blurred-cover banner with action buttons + cover thumbnail + title/meta. |
| `DetailInfo.kt` | 201 | `GenresRow`, `SynopsisSection`, `InfoSection`, `InfoRow`, `LoadingState`, `ErrorState`. |
| `EpisodesSection.kt` | 952 | Section header + source chip + episode list + `EpisodeRow` (the big two-section row layout) + display-prefs snapshot + watched effect. |
| `EpisodeStates.kt` | 249 | `SearchingState`, `EpisodesLoadingState`, `NoSourcesState`, `EpisodesErrorState`, `NotReleasedState`. |
| `EpisodeDownloadControl.kt` | 176 | The state-driven per-episode download control (button/progress/check/error). |
| `EpisodeDownloadState.kt` | 45 | `sealed interface EpisodeDownloadState` (7 states). |
| `EpisodeDisplayPreferences.kt` | 107 | `EpisodeDisplayPreferences` (PreferenceStore-backed toggles for the row). |
| `SourceSwitcherDialog.kt` | 92 | **DEAD CODE** — declared `internal` but never invoked (source switching is now done via `ManualSearchSheet`; see §6). |
| `ManualSearchSheet.kt` | 487 | The bottom sheet for picking one source and searching it manually. |
| `ExtensionDetailScreen.kt` | 429 | The extension-only details page — slated for removal (see §8). |
| `ExtensionDetailViewModel.kt` | 307 | The extension-only VM — mirrors `AnimeDetailViewModel` minus AniList. |
| `WatchEpisodeContext.kt` | 22 | Tiny DTO that carries anime title + cover URL + metadata map to the watch page. |

---

## 1. Data Model

### 1.1 The `Anime` domain model

Defined at `ANIKUTA_PROJECT/ANIKUTA/core/common/src/main/java/app/confused/anikuta/core/common/model/Anime.kt:20-50`. The details page does **not** consume this model directly — the screen renders `AniListAnime` (see 1.2). `Anime` is only used inside the VM as the SQLDelight-persisted library row shape (for `saveAnimeToLibrary` at `AnimeDetailViewModel.kt:316-345` and `saveEpisodesToDb` at `AnimeDetailViewModel.kt:640-696`). Every field is reproduced below because the unified page will need to render from this shape when data comes from an extension instead of AniList.

| Field | Type | Source column | Used by details page? | Notes |
|---|---|---|---|---|
| `id` | `Long` | `anime._id` | Indirectly (DB key for episode FKs) | 0 means "new insert". |
| `url` | `String` | `url` | No | Per source (e.g. `"anilist:$anilistId"` when saved from AniList; raw extension URL when saved from extension-only page — see `ExtensionDetailViewModel.kt:140-145`). |
| `title` | `String` | `title` | No (rendered from AniListAnime) | Library sort/display fallback. |
| `artist` | `String?` | `artist` | No | Always null in current code. |
| `author` | `String?` | `author` | No | Always null. |
| `description` | `String?` | `description` | No (AniList version used) | Persisted for offline. |
| `genre` | `List<String>` | `genre` | No (AniList version used) | Persisted for offline. |
| `coverUrl` | `String?` | `cover_url` | No (AniList `coverImage.best` used) | |
| `status` | `Int` | `status` | No | `AnimeStatus` constants at `Anime.kt:67-75` (UNKNOWN=0…ON_HIATUS=6). |
| `thumbnailUrl` | `String?` | `thumbnail_url` | No | Always null on the AniList path. |
| `favorite` | `Boolean` | `favorite` | Yes — drives `isSaved` StateFlow (`AnimeDetailViewModel.kt:175`) | |
| `sourceId` | `Long` | `source_id` | No | 0 on the AniList path; the extension's source ID on the extension-only path. |
| `dateAdded` | `Long` | `date_added` | No | |
| `viewerFlags` | `Int` | `viewer_flags` | No | Always 0. |
| `nextUpdate` | `Long` | `next_update` | No | Always 0. |
| `updateStrategy` | `Int` | `update_strategy` | No | Always 0. |
| `coverLastModified` | `Long` | `cover_last_modified` | No | Always 0. |
| `releaseDate` | `Long?` | `release_date` | No | Always null (ADR-024 status-tracking column, unused). |
| `lastRefresh` | `Long` | `last_refresh` | No | Set to `System.currentTimeMillis()` on save. |
| `lastMetadataFetch` | `Long?` | `last_metadata_fetch` | No | Set to `now` on AniList save, `null` on extension save. |
| `nextEpisodeCheck` | `Long?` | `next_episode_check` | No | Always null (ADR-014 not implemented). |
| `anilistId` | `Int?` | `anilist_id` | Yes — the link key used by every store + repo lookup | `null` for extension-only anime. |
| `coverColor` | `String?` | `cover_color` | No (read from AniListAnime directly, see §9) | Persisted for library cells; never read back on the details page. |
| `score` | `Double?` | `score` | No | |
| `totalEpisodes` | `Int?` | `total_episodes` | No | |
| `lastWatched` | `Long` | `last_watched` | No | Always 0 on save (set elsewhere during playback). |
| `nextAiringEpisode` | `Int?` | `next_airing_episode` | No | |
| `releasedEpisodes` | `Int?` (computed) | — | No | Derived getter at `Anime.kt:58-63`. |

### 1.2 The `AniListAnime` model — what the details page actually consumes

Defined at `ANIKUTA_PROJECT/ANIKUTA/core/anilist/src/main/java/app/confused/anikuta/core/anilist/model/AniListAnime.kt:13-37` (a `@Serializable` data class, a curated subset of AniList's `Media`). The screen receives it via `DetailState.Success(val anime: AniListAnime)` (declared at `AnimeDetailViewModel.kt:767`) and passes it down through `DetailContent` → `DetailBanner` / `GenresRow` / `SynopsisSection` / `InfoSection` / `EpisodesSection`.

| Field | Type | Used by details page? | Where |
|---|---|---|---|
| `id: Int` | AniList media ID | Indirectly (passed to VM as `animeId`) | `AnimeDetailScreen.kt:58`, used for VM key + `saveAnimeToLibrary` |
| `title: AniListTitle` | romaji/english/native | Yes | `displayTitle` extension at `AniListAnime.kt:129` → `DetailBanner.kt:139`, `DetailContent.kt:128,144`, `InfoSection` indirectly |
| `coverImage: AniListCoverImage?` | medium/large/extraLarge/color | Yes | `coverUrl` extension (`AniListAnime.kt:132`) → `DetailBanner.kt:73,127,129`; `coverColorHex` extension (`AniListAnime.kt:135`) → `AnimeDetailScreen.kt:141`, `DetailContent.kt:80` |
| `averageScore: Int?` | 0–100 | Yes | `DetailBanner.kt:149` ("★ N%"), `DetailInfo.kt:130` ("Score: N / 100") |
| `meanScore: Int?` | 0–100 | No | |
| `popularity: Int?` | | No | |
| `favourites: Int?` | | No | |
| `format: String?` | e.g. `"TV"` | Yes | `DetailInfo.kt:126` ("Format") |
| `episodes: Int?` | total | Yes | `DetailBanner.kt:151` ("N eps"), `DetailInfo.kt:129` ("Episodes") |
| `status: String?` | e.g. `"RELEASING"`, `"NOT_YET_RELEASED"` | Yes | `DetailBanner.kt:150` (lower-cased), `DetailInfo.kt:127`; AND gates source search — `AnimeDetailViewModel.kt:441` (`if (anime.status == "NOT_YET_RELEASED") _episodeState.value = EpisodeState.NotReleased`) |
| `description: String?` | HTML | Yes | `DetailContent.kt:117-119` (conditionally rendered), `DetailInfo` indirectly; HTML stripped at `DetailInfo.kt:78` |
| `bannerImage: String?` | | Indirectly (passed to `EpisodeMetadataRequest.bannerImage` at `AnimeDetailViewModel.kt:724`, falls back to cover) | |
| `genres: List<String>?` | | Yes | `DetailInfo.kt:45-46` (GenresRow early-returns if null/empty) |
| `season: String?` | | Yes (via `seasonDisplay`) | `AniListAnime.kt:138-142` → `DetailInfo.kt:128` |
| `seasonYear: Int?` | | Yes (via `seasonDisplay`) | same |
| `startDate: AniListFuzzyDate?` | | Yes (via `startDateDisplay`) | `AniListAnime.kt:148` → `DetailInfo.kt:132` |
| `endDate: AniListFuzzyDate?` | | No | |
| `studios: AniListStudioConnection?` | | Yes (via `studioName`) | `AniListAnime.kt:145` → `DetailInfo.kt:131` |
| `nextAiringEpisode: AniListAiringSchedule?` | | Yes (via `nextAiringDisplay`) | `AniListAnime.kt:151-165` → `DetailBanner.kt:162-177` (pill) |
| `source: String?` | e.g. `"MANGA"` | Yes | `DetailInfo.kt:133` ("Source") |
| `countryOfOrigin: String?` | | No | |
| `isAdult: Boolean?` | | No | |
| `idMal: Int?` | MAL ID | Indirectly | Used for episode-metadata fetch at `AnimeDetailViewModel.kt:722` (`malId = anime.idMal`) |

### 1.3 Extension contract — `SAnime`, `SEpisode`, `AnimeSource`

These are the extension-side types (located in `:core:source-api`, package `eu.kanade.tachiyomi.animesource`). The unified page will eventually need to translate **SAnime fields → AniListAnime-shaped display data** (or accept either). Field inventory for the future mapping comparison:

`SAnime` — `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt:7-33`:

| SAnime field | AniListAnime equivalent | Notes |
|---|---|---|
| `url: String` | — (AniList has `id: Int`) | Per-source anime URL, used as the key for `getEpisodeList` |
| `title: String` | `title.display` | |
| `artist: String?` | — (no AniList equiv. used) | |
| `author: String?` | — | |
| `description: String?` | `description` | |
| `genre: String?` (comma-joined) | `genres: List<String>?` | `SAnime.getGenres()` at line 35 splits + trims |
| `status: Int` (0–6, same constants as `AnimeStatus`) | `status: String?` (AniList string) | Constants at lines 57-63 mirror `AnimeStatus` |
| `thumbnail_url: String?` | `coverImage.best` | |
| `background_url: String?` | `bannerImage` | |
| `update_strategy: AnimeUpdateStrategy` | — | |
| `fetch_type: FetchType` | — | |
| `season_number: Double` | — | |
| `initialized: Boolean` | — | |
| _no score / episodes / next-airing / format / studio / season-year / start-date / MAL-ID / cover-color_ | _missing_ | These are the gaps `ExtensionDetailScreen` has to live with (see §8) |

`SEpisode` — `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt:7-23`: `url`, `name`, `date_upload: Long`, `episode_number: Float`, `fillermark: Boolean`, `scanlator: String?`, `summary: String?`, `preview_url: String?`. (The DB `Episode` model — `core/common/.../model/Episode.kt:9-27` — mirrors these and adds `id`, `animeId`, `seen`, `bookmark`, `lastSecondSeen`, `totalSeconds`, `sourceOrder`, `dateFetch`.)

`AnimeSource` — `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt:13-113`. Key members: `id: Long`, `name: String`, `lang: String`, plus `suspend fun getAnimeDetails(SAnime): SAnime`, `getEpisodeList(SAnime): List<SEpisode>`, `getSeasonList(SAnime): List<SAnime>`, `getHosterList(SEpisode)`, `getVideoList(Hoster|SEpisode)`. Sub-interface `AnimeCatalogueSource` (passed around as the concrete type for source-switcher + manual-link) adds search.

### 1.4 `WatchEpisodeContext` — the details → watch DTO

`ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/WatchEpisodeContext.kt:18-22`: `data class WatchEpisodeContext(animeTitle: String, coverUrl: String?, episodeMetadata: Map<Int, EpisodeMetadata> = emptyMap())`. Built at `DetailContent.kt:127-131` and threaded through `onOpenEpisode`. The extension-only details page builds a slimmed version at `Destinations.kt:157-160`.

---

## 2. Data Sources & the 3-Stage Load

The screen is parameterised at `AnimeDetailScreen.kt:56-73`:

```
AnimeDetailScreen(
    animeId: Int,                                    // AniList media ID
    api: AniListApi,                                 // injected by AppController (NOT Koin)
    extensionManager: AnimeExtensionManager,         // trusted + installed sources
    sourceMatcher: SourceMatcher,                    // title-similarity matcher
    extensionLinkStore: ExtensionLinkStore,          // "$sourceId:$url" → anilistId cache
    onBack, onOpenEpisode, onDownloadEpisode,        // host callbacks
    downloadStates: Map<String, EpisodeDownloadState>,
    onDownloadCancel / Resume / Retry / Delete,
)
```

The other repositories (`AnimeRepository`, `CategoryRepository`, `EpisodeMetadataRepository`, `SourceLinkStore`, `EpisodeRepository`) are pulled from Koin **inside the composable body** at `AnimeDetailScreen.kt:77-81` via `GlobalContext.get().get()`.

### 2.1 The 3-stage load flow

All three stages live in `AnimeDetailViewModel.kt` and are kicked off from the VM `init` block at `AnimeDetailViewModel.kt:154-167`:

| Stage | Method | Lines | What happens |
|---|---|---|---|
| **0 (boot)** | `init` | `AnimeDetailViewModel.kt:154-167` | Calls `loadAnimeDetails()`; launches two collectors: `animeRepository.observeByAnilistId` → `_isSaved`, `categoryRepository.observeVisible()` → `_categories`. |
| **1 (AniList)** | `loadAnimeDetails(refreshing)` | `AnimeDetailViewModel.kt:435-468` | Sets `_animeState = Loading`; calls `api.fetchById(anilistId)`. If null → `DetailState.Error("Anime not found")`. If `anime.status == "NOT_YET_RELEASED"` → `_episodeState = NotReleased`, **skips stage 2/3**. Otherwise calls `findAndLoadEpisodes(anime)`. On any `Throwable` → `DetailState.Error(e.message)`. Finally clears `isRefreshing` if `refreshing=true`. |
| **2 (source match)** | `findAndLoadEpisodes(anime)` | `AnimeDetailViewModel.kt:470-567` | Three sub-paths in order: (a) **DB-first short-circuit** at lines 476-506 — if `animeRepository.getByAnilistId` returns a row with episodes in `episodeRepository.getByAnimeId`, immediately publishes `EpisodeState.Loaded(sEpisodes, sourceName)`, reconstructs `currentMatch` from `sourceLinkStore.getLink`, and launches background `searchAllSourcesInBackground` + `fetchEpisodeMetadata` in parallel; (b) **SourceLinkStore hit** at lines 510-529 — if a saved source link exists AND `sourceMatcher.getSourceById` resolves, builds an `SAnimeImpl` from the saved url/title and calls `loadEpisodes`; (c) **fresh search** at lines 533-567 — sets `_episodeState = Searching`, calls `sourceMatcher.matchAll(title)`, picks the preferred source (explicit pref via `sourcePrefs.getLong(sourcePrefKey(anilistId))` at line 540 → `extensionLinkStore.getPreferredSourceForAnilist(anilistId)` at line 541 → first match), saves the link, calls `loadEpisodes`. |
| **2′ (background)** | `searchAllSourcesInBackground(title)` | `AnimeDetailViewModel.kt:569-578` | Re-runs `sourceMatcher.matchAll` to populate `_allMatches` for the source-switcher without blocking. Catches all exceptions as non-fatal. |
| **3 (episodes)** | `loadEpisodes(match)` | `AnimeDetailViewModel.kt:580-607` | Sets `_episodeState = Loading(sourceName)`; `withContext(Dispatchers.IO) { match.source.getEpisodeList(match.sAnime) }`; if empty → `NoMatch`, else → `Loaded(episodes, sourceName)`, then calls `saveEpisodesToDb(episodes)` and `fetchEpisodeMetadata(episodes.size)`. On `Throwable` → `EpisodeState.Error("Failed to load episodes: …")` + Toast. |
| **3′ (persist)** | `saveEpisodesToDb(episodes)` | `AnimeDetailViewModel.kt:638-696` | Ensures the `Anime` row exists (creates a minimal one if not, mirroring AniList fields), then `episodeRepository.deleteByAnimeId` + per-episode `episodeRepository.upsert` with `sourceOrder = index`. Converts `SEpisode → Episode` inline at lines 670-685 (preserves `url`, `name`, `episode_number`, `scanlator`, `date_upload`, `summary`, `preview_url`, `fillermark = if (ep.fillermark) "filler" else null`). |
| **3″ (metadata enrich)** | `fetchEpisodeMetadata(episodeCount)` | `AnimeDetailViewModel.kt:698-722` | Builds `EpisodeMetadataRequest(animeId, animeTitle, episodeNumber=1, malId, bannerImage = anime.bannerImage ?: coverImage.best, episodeCount)`; calls `episodeMetadataRepository.fetchAll(request)`; updates `_episodeMetadata`. Non-fatal on failure (episodes still show with extension data). |

### 2.2 Reverse mapping (DB → UI)

When the DB-first short-circuit runs, `Episode.toSEpisode()` at `AnimeDetailViewModel.kt:754-766` converts each DB `Episode` back to an `SEpisodeImpl` so the UI's `EpisodeRow` (which only knows about `SEpisode`) doesn't need to change. This is the existing "translation layer" — the future unified page will likely generalise this pattern.

### 2.3 Pull-to-refresh

`refresh()` at `AnimeDetailViewModel.kt:335-341` early-returns if `_isRefreshing` is already true (debounces double-pull), sets the flag, and re-calls `loadAnimeDetails(refreshing = true)`. The flag is cleared in the `finally` block of `loadAnimeDetails` (line 466). `isRefreshing` is observed by `DetailContent` and drives the `PullToRefreshBox` indicator (`DetailContent.kt:92-95`).

### 2.4 Manual search / re-link

`manualSearch(sourceId, query)` at `AnimeDetailViewModel.kt:360-398` is `suspend` — called from the UI via `scope.launch { onManualSearch(sourceId, q) }` inside `ManualSearchSheet.kt:277`. Updates `_manualSearchResults`, `_manualSearchErrors`, `_isSearching`, `_hasSearched`. `linkManual(source, sAnime)` at `AnimeDetailViewModel.kt:402-414` constructs a fake `SourceMatch` with `score = 1.0`, persists both `sourcePrefs` and `sourceLinkStore.saveLink`, then calls `loadEpisodes`.

---

## 3. UI Structure

The screen renders the following sections top → bottom inside a `LazyColumn` (declared in `DetailContent.kt:97-169`). The `PullToRefreshBox` wrapper is at `DetailContent.kt:92-96`.

| # | Section | Composable | File:Line | Notes |
|---|---|---|---|---|
| 0 | Outer wrap | `Box(fillMaxSize + background(MaterialTheme.colorScheme.background))` | `AnimeDetailScreen.kt:163-167` | Conditionally wrapped in `MaterialTheme(dynamicScheme)` at lines 208-212 when adaptive colors + cover color are available (see §9). |
| 1 | Loading fallback | `LoadingState()` | `DetailInfo.kt:162-174` (`AnimeDetailScreen.kt:169`) | Centered `CircularProgressIndicator` (32dp, 3dp stroke). |
| 1′ | Error fallback | `ErrorState(message)` | `DetailInfo.kt:176-201` (`AnimeDetailScreen.kt:170`) | Centered "Couldn't load anime" + message. |
| 2 | Banner | `DetailBanner(anime, coverColor, saved, onBack, onToggleSave, onLongPressSave)` | `DetailContent.kt:103-110` → `DetailBanner.kt:56-181` | 360dp tall. Layer order (back→front): blurred cover (`AsyncImage` + 8dp `blur`, lines 73-78) OR `surfaceVariant` placeholder (lines 79-81); `coverColor.copy(alpha = 0.2f)` tint (line 83); `Brush.verticalGradient(Black 20% → Transparent → background)` (lines 85-97). Top row: `ArrowBack` (left), `Bookmark`/`BookmarkBorder` + `MoreHoriz` (right) — `ActionButton` shapes at lines 184-214. Bottom row: 100×150dp cover thumbnail (rounded 12dp) + `displayTitle` (20sp ExtraBold) + meta line (`★ N% · status · N eps`, lines 148-152) + optional `nextAiringDisplay` pill (lines 162-177). |
| 3 | Genres row | `GenresRow(anime)` | `DetailContent.kt:114` → `DetailInfo.kt:43-70` | Horizontal scroll of `primaryContainer@0.6f` chips with 50% rounded corners, 11sp ExtraBold. **Early-returns** if `genres` is null or empty (line 45-46). |
| 4 | Synopsis | `SynopsisSection(anime.description!!)` | `DetailContent.kt:117-119` → `DetailInfo.kt:75-110` | Strips HTML via `Regex("<[^>]*>")` at line 78; 2-line collapsed with "Show more/less" toggle (lines 97-108) only when `cleanDesc.length > 100`. **Skipped entirely** if description is null/blank (predicate at `DetailContent.kt:117`). |
| 5 | Episodes | `EpisodesSection(...)` | `DetailContent.kt:122-162` → `EpisodesSection.kt:64-235` | Section header (`"Episodes"` 18sp ExtraBold + spinner when metadata loading) on the left; right side is either a source-name chip (tappable, opens `ManualSearchSheet`) or a "Search manually" CTA. Body is a `when (episodeState)` switch (`EpisodesSection.kt:206-220`) → `SearchingState` / `EpisodesLoadingState` / `EpisodeList` / `NoSourcesState` / `NotReleasedState` / `EpisodesErrorState`. |
| 5a | Episode row | `EpisodeRow(episode, index, isWatched, metadata, displayPrefs, onClick, onToggleWatched, onDownload, …)` | `EpisodesSection.kt:294-535` (private) | Two-section card: **top** = 120×68dp (medium) thumbnail + EP-N badge overlay (`EpisodeThumbnail` lines 545-588) OR `CircleEpisodeNumber` fallback (lines 591-605); right column = title (`surface@0.5f` background if `showTitleBg`) + DatePill + AudioPills (`SUB•DUB`/`HSUB`, full names). **Bottom** = synopsis (`surface@0.35f` bg, 12sp, 15sp lineHeight). Right edge = `EpisodeDownloadControl` (lines 537-539). Watched rows get `watchedEpisodeEffect` — grayscale `RenderEffect` (API 31+) + `alpha = 0.55f` (`EpisodesSection.kt:935-951`). |
| 6 | Information | `InfoSection(anime)` | `DetailContent.kt:165-168` → `DetailInfo.kt:115-135` | "Information" header + `InfoRow`s for Format, Status, Season (conditional), Episodes, Score (conditional), Studio (conditional), Aired (conditional), Source (conditional). |
| 7 | (overlay) Category picker dialog | `CategoryPickerDialog` / `AddCategoryDialog` | `AnimeDetailScreen.kt:215-234` | Shown when `showCategoryPicker` is true (long-press on bookmark). `AddCategoryDialog` swap-in when adding a new category. Both come from `:core:designsystem`. |
| 8 | (overlay) Manual search sheet | `ManualSearchSheet(...)` | `EpisodesSection.kt:223-242` | Shown when `showManualSearch` local state is true (tapping the source chip or "Search manually" CTA, or `NoSourcesState`'s button). Modal bottom sheet, no drag handle (`ManualSearchSheet.kt:121`). |

---

## 4. Conditional Rendering

Every predicate that decides whether a UI element renders, with file:line.

### 4.1 Top-level state switch

`AnimeDetailScreen.kt:168-203` — `when (val state = animeState)`:
- `DetailState.Loading` → `LoadingState()`.
- `DetailState.Error(message)` → `ErrorState(message)`.
- `DetailState.Success(anime)` → `DetailContent(...)`. (Only this branch renders the scrollable content.)

### 4.2 Banner

| Predicate | File:Line | When true | When false |
|---|---|---|---|
| `anime.coverUrl != null` | `DetailBanner.kt:72` | `AsyncImage` blurred cover | `Box(surfaceVariant)` placeholder |
| `saved` (from `vm.isSaved`) | `DetailBanner.kt:111` | `Icons.Filled.Bookmark` ("Remove from library") | `Icons.Filled.BookmarkBorder` ("Add to library") |
| `anime.coverUrl != null` (again, bottom row) | `DetailBanner.kt:127` | 100×150 cover thumbnail rendered | Column starts at left edge (no thumbnail) |
| `metaParts.isNotEmpty()` (score/status/episodes — each added via `let` at `DetailBanner.kt:148-152`) | `DetailBanner.kt:153` | Meta line shown | Meta line hidden |
| `anime.nextAiringDisplay != null` | `DetailBanner.kt:162` | Pill with "EP N in 2d 5h" | No pill |

### 4.3 Genres & Synopsis

| Predicate | File:Line | Effect |
|---|---|---|
| `genres != null && genres.isNotEmpty()` | `DetailInfo.kt:45-46` (early return) | GenresRow hidden entirely when no genres |
| `!anime.description.isNullOrBlank()` | `DetailContent.kt:117` | SynopsisSection rendered only when description has content |
| `cleanDesc.length > 100` | `DetailInfo.kt:97` | "Show more/less" toggle hidden for short descriptions |

### 4.4 Episodes section (per `EpisodeState`)

The `when` switch at `EpisodesSection.kt:206-220`:

| State | Renders | File:Line |
|---|---|---|
| `EpisodeState.Idle` | nothing | `EpisodeStates.kt` (not used — Idle only appears transiently) |
| `EpisodeState.Searching` | `SearchingState()` | `EpisodeStates.kt:32-51` ("Searching sources…") |
| `EpisodeState.Loading(sourceName)` | `EpisodesLoadingState(sourceName)` | `EpisodeStates.kt:53-73` ("Loading episodes from {name}…") |
| `EpisodeState.Loaded(episodes, sourceName)` | `EpisodeList(...)` | `EpisodesSection.kt:218-235` |
| `EpisodeState.NoMatch` | `NoSourcesState(onSearchManually, autoMatchErrors)` | `EpisodeStates.kt:91-194` ("No sources have this anime" + per-source error cards + "Search manually" CTA) |
| `EpisodeState.NotReleased` | `NotReleasedState()` | `EpisodeStates.kt:227-249` ("Not yet released") |
| `EpisodeState.Error(message)` | `EpisodesErrorState(message)` | `EpisodeStates.kt:196-218` ("Failed to load episodes") |

### 4.5 Section header source chip

`EpisodesSection.kt:138-205` — three-way `when`:
- `currentMatch != null` → primary-container chip with source name + `ExpandMore` icon (tappable).
- `episodeState is EpisodeState.NoMatch` → primary-filled "Search manually" CTA button.
- otherwise (Searching/Loading) → empty (no chip).

### 4.6 Metadata loading indicator

`EpisodesSection.kt:148-157` — `if (showMetadataLoading && episodeState is EpisodeState.Loaded && episodeMetadata.isEmpty())` → 14dp spinner next to "Episodes" header. The `showMetadataLoading` parameter defaults to `true` and is explicitly set to `false` from `ExtensionDetailContent` at `ExtensionDetailScreen.kt:223`.

### 4.7 Episode row element visibility (driven by `EpisodeDisplayPrefs`)

`EpisodesSection.kt:309-328` — every visible element is gated by a `displayPrefs` flag:
- `showThumbnails` → thumbnail renders (lines 460-466); else falls back to `CircleEpisodeNumber` if `showNumber` is on (lines 467-470).
- `showTitle` → title renders (with or without bg depending on `showTitleBg`).
- `showDate && dateText != null` → `DatePill` renders.
- `showAudioPills && (hasSub || hasDub || hasHsub)` → `AudioPills` render.
- `showSummary && !description.isNullOrBlank()` → synopsis renders (with or without bg per `showSynopsisBg`).
- `showDownloadBtn || downloadState != EpisodeDownloadState.NotDownloaded` → `EpisodeDownloadControl` always renders once a download exists, even if the toggle is off (lines 533-540).
- `thumbnailUrl == null && !showNumber` → spacer is rendered instead (lines 530-532).
- Date text computation itself is conditional on `metadata.airDate > 0 || episode.date_upload > 0` (`EpisodesSection.kt:386-392`); otherwise `dateText = null` and the pill is skipped.

### 4.8 Info section conditionals

`DetailInfo.kt:126-133` — `Format` and `Status` always render (with "Unknown" fallbacks), but `Season`, `Score`, `Studio`, `Aired`, `Source` only render `if` their nullable extension returns non-null. `Episodes` always renders with `(anime.episodes ?: 0)`.

### 4.9 Manual search sheet states

`ManualSearchSheet.kt:298-421` — five-way `when`:
- `selectedSourceId == -1L` → "Select a source above…" prompt.
- `isSearching` → spinner + "Searching {sourceName}…".
- `!hasSearched` → "Type a title and tap the search icon…" prompt.
- `errors.isNotEmpty()` → "Search failed." + per-source error cards.
- `results.isEmpty()` → "No results found." message.
- else → `LazyColumn` of `ManualSearchResultRow`s (line 401-419).

Plus: `currentMatch != null` (line 166) → "Currently connected to" card at the top showing the sAnime thumbnail + title + source name.

### 4.10 Category picker dialog

`AnimeDetailScreen.kt:215-234` — `if (showCategoryPicker)` → either `CategoryPickerDialog` (when `!showAddCategory`) or `AddCategoryDialog` (when `showAddCategory`).

---

## 5. Navigation (Voyager)

On `feature/voyager-navigation`, navigation was migrated from the old hand-rolled state machine in `MainActivity.kt` to Voyager (ADR-037). The Voyager root + destinations live at `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/navigation/`:

- `AnikutaRoot.kt` — single root `Navigator(BrowseTabDestination)` with `FadeTransition` (lines 66-100). Floating bottom nav shown only at stack depth ≤ 1 (line 90). Three overlay sheets (resolver, linking, download picker) render on top via `AppOverlays` (lines 127-191).
- `AppController.kt` — the Koin-scoped orchestrator. Holds `navigator: Navigator?`, `currentTab: String`, overlay-state fields (`resolverState`, `linkingTarget`, `downloadPickerTarget`), and all push/navigation helpers.
- `Destinations.kt` — every `Screen` implementation (4 tabs + ~20 pushed screens).
- `NavModule.kt` — Koin module binding `AppController` (not read for this task).
- `MoreScreens.kt` — `MoreScreen` content (not read for this task).

### 5.1 How the user reaches `AnimeDetailScreen`

The Voyager destination is `AnimeDetailDestination(val animeId: Int)` — `Destinations.kt:109-140`. It's a `data class … : Screen`. Its `Content()` (lines 110-139) `koinInject`s `AppController`, collects `appController.downloadTasksFlow`, computes `downloadStates` via `appController.getDownloadStates(animeId, downloadTasksMap)` (line 118), then calls `AnimeDetailScreen(...)` with all the host callbacks wired.

Pushed by `AppController.pushDetail(anilistId: Int)` at `AppController.kt:167-169`:

```kotlin
fun pushDetail(anilistId: Int) {
    navigator?.push(AnimeDetailDestination(anilistId))
}
```

This is called from **5 entry points** (all in `Destinations.kt`):

| Caller | Trigger | File:Line |
|---|---|---|
| `BrowseTabDestination` | Tapping a trending/popular grid cell | `Destinations.kt:54` (`onOpenAnime = { id -> appController.pushDetail(id) }`) |
| `LibraryTabDestination` | Tapping a library cell OR a "Continue watching" item | `Destinations.kt:64,65` |
| `SearchTabDestination` | Tapping an AniList search result | `Destinations.kt:80` |
| `HistoryDestination` | Tapping a history row | `Destinations.kt:194` |
| `UpdatesDestination` | Tapping an update row | `Destinations.kt:206` |
| `ProfileDestination` | Tapping a profile stats entry | `Destinations.kt:221` |
| `DownloadedFilesDestination` | Tapping a downloaded file ("Play episode") | `Destinations.kt:329` |

### 5.2 Parameters passed

Just one parameter is threaded through Voyager: `animeId: Int` (the AniList media ID). Every other dependency (`AniListApi`, `AnimeExtensionManager`, `SourceMatcher`, `ExtensionLinkStore`, all the download-state plumbing) is supplied by `AppController` inside `AnimeDetailDestination.Content()` — Voyager itself doesn't carry them.

### 5.3 How `ExtensionDetailScreen` is reached (for comparison)

The Voyager destination is `ExtensionDetailDestination(val source: AnimeCatalogueSource, val sAnime: SAnime)` — `Destinations.kt:142-170`. Unlike `AnimeDetailDestination`, it carries the **full** `AnimeCatalogueSource` and `SAnime` instances (both `Serializable`, so they survive Voyager's back-stack save/restore).

Pushed by `AppController.pushExtensionDetail(source, sAnime)` at `AppController.kt:171-173` and by `AppController.onGoWithoutLinking(source, sAnime)` at `AppController.kt:557-565` — the latter is the callback from `ExtensionLinkingSheet`'s "go without linking" button (`AnikutaRoot.kt:171-173`). `onGoWithoutLinking` guards against double-push (line 563: `if (nav.lastItem !is ExtensionDetailDestination) nav.push(...)`).

The screen-to-screen transition from Extension → AniList (when the user links via the sheet's "A" button) is handled by `AppController.onLinked(...)` at `AppController.kt:537-552`: if `nav.lastItem is ExtensionDetailDestination`, it calls `nav.replace(AnimeDetailDestination(anilistId))` (line 541) — replacing the extension screen with the AniList one instead of stacking them.

### 5.4 Watch page handoff

`AnimeDetailDestination.Content()` wires `onOpenEpisode = { episode, source, episodeList, watchCtx -> appController.resolveEpisode(episode, source, episodeList, watchCtx, animeId) }` (`Destinations.kt:127-129`). `AppController.resolveEpisode(...)` at `AppController.kt:239-…` first checks `downloadManager.isEpisodeDownloaded(anilistId, episode.url)` for an offline short-circuit (builds a `WatchRequest` from the local content URI); otherwise it triggers the resolver overlay (`resolverState = VideoResolverState.Resolving(...)`) which eventually calls `pushWatch(WatchRequest(...))` (`AppController.kt:256,328`). The Voyager destination for the watch page is `WatchDestination(val watchRequest: WatchRequest)` at `Destinations.kt:172-181`.

---

## 6. Interactions

Every user-actionable callback the screen exposes, with the host-side wiring.

### 6.1 Action button row (banner)

| Action | Trigger | Callback | VM handler / host | File:Line |
|---|---|---|---|---|
| Back | Tap `ArrowBack` button | `onBack: () -> Unit` | `navigator.pop()` | `AnimeDetailScreen.kt:63`, `DetailBanner.kt:108`, `Destinations.kt:126` |
| Toggle library save (short-press) | Tap `Bookmark`/`BookmarkBorder` | `onToggleSave: () -> Unit` | `vm::toggleSave` | `AnimeDetailScreen.kt:186`, `DetailBanner.kt:113`, `AnimeDetailViewModel.kt:178-201` |
| Open category picker (long-press) | Long-press `Bookmark` | `onLongPressSave: () -> Unit` | `vm::openCategoryPicker` | `AnimeDetailScreen.kt:187`, `DetailBanner.kt:114`, `AnimeDetailViewModel.kt:203-214` |
| "More" (three-dot) | Tap `MoreHoriz` | `onClick = {}` (no-op) | **STUB** — currently does nothing | `DetailBanner.kt:116` |

> **Note for the unified-page plan:** the three-dot button at `DetailBanner.kt:116` is the obvious place to hang the "switch source" / "switch between AniList and extension data" menu. It is currently a no-op.

### 6.2 Category picker dialog

| Action | Trigger | Callback | VM handler | File:Line |
|---|---|---|---|---|
| Confirm category selection | Tap "OK" in `CategoryPickerDialog` | `onConfirm = { ids -> vm.saveToCategories(ids) }` | `saveToCategories` | `AnimeDetailScreen.kt:221`, `AnimeDetailViewModel.kt:216-238` |
| Dismiss | Tap "Cancel" / outside | `onDismiss = { vm.dismissCategoryPicker() }` | `dismissCategoryPicker` | `AnimeDetailScreen.kt:222`, `AnimeDetailViewModel.kt:216` |
| Add new category | Tap "Add new category" | `onAddNewCategory = { showAddCategory = true }` | (local state) | `AnimeDetailScreen.kt:223` |
| Confirm new category name | Tap "OK" in `AddCategoryDialog` | `onConfirm = { name -> vm.createCategory(name); showAddCategory = false }` | `createCategory` | `AnimeDetailScreen.kt:227-230`, `AnimeDetailViewModel.kt:240-248` |

### 6.3 Episodes section

| Action | Trigger | Callback | VM handler / host | File:Line |
|---|---|---|---|---|
| Pull to refresh | Drag down from top of `PullToRefreshBox` | `onRefresh: () -> Unit` | `vm::refresh` | `DetailContent.kt:94`, `AnimeDetailViewModel.kt:335-341` |
| Open episode | Tap episode row body | `onOpenEpisode(episode, source, episodes, watchCtx)` | `appController.resolveEpisode(...)` | `DetailContent.kt:145-147`, `EpisodesSection.kt:296-300`, `Destinations.kt:127-129` |
| Toggle watched | (Wired but **no UI trigger** in `EpisodeRow` — see note below) | `onToggleWatched: (String) -> Unit` | `vm::toggleWatched` | `AnimeDetailScreen.kt:190`, `AnimeDetailViewModel.kt:154-160`. The `EpisodeRow` signature has `onToggleWatched` (`EpisodesSection.kt:285`) but no Composable invokes it. |
| Switch source (via chip) | Tap source-name chip in section header | Opens `ManualSearchSheet` (local `showManualSearch = true`) | (sheet-driven) | `EpisodesSection.kt:148-167` |
| "Search manually" CTA (header) | Tap CTA when `NoMatch` | Opens `ManualSearchSheet` | (sheet-driven) | `EpisodesSection.kt:170-202` |
| "Search manually" CTA (NoSourcesState body) | Tap CTA | `onSearchManually = { showManualSearch = true }` | (sheet-driven) | `EpisodeStates.kt:167-192` |
| Manual search | Tap search icon in sheet | `onManualSearch(sourceId, query)` (suspend) | `vm::manualSearch` | `ManualSearchSheet.kt:272-280`, `AnimeDetailViewModel.kt:360-398` |
| Link manual result | Tap a result row | `onLinkManual(result) → vm.linkManual(result.source, result.sAnime)` | `vm::linkManual` | `EpisodesSection.kt:233-237`, `ManualSearchSheet.kt:407-417`, `AnimeDetailViewModel.kt:402-414` |
| Dismiss manual search sheet | Swipe down / tap scrim | `onDismiss → showManualSearch = false; vm.clearManualSearch()` | `vm::clearManualSearch` | `EpisodesSection.kt:239-241`, `AnimeDetailViewModel.kt:400-405` |
| Pick source in sheet | Tap a `FilterChip` | local `selectedSourceId = source.id` | — | `ManualSearchSheet.kt:234-251` |
| Download episode | Tap `Download` icon on row | `onDownload(episode, source)` → `onDownloadEpisode(episode, source, watchCtx)` | `appController.downloadEpisode(...)` | `EpisodesSection.kt:303-305`, `DetailContent.kt:153-155`, `Destinations.kt:130-132` |
| Cancel download | Tap `Close` icon | `onDownloadCancel(episode.url)` | `appController.cancelDownload(animeId, episodeUrl)` | `EpisodesSection.kt:306`, `Destinations.kt:134` |
| Resume download (paused) | Tap `PlayArrow` | `onDownloadResume(episode.url)` | `appController.resumeDownload(...)` | `EpisodeDownloadControl.kt:121`, `Destinations.kt:135` |
| Retry download (error) | Tap `Refresh` | `onDownloadRetry(episode.url)` | `appController.retryDownload(...)` | `EpisodeDownloadControl.kt:133`, `Destinations.kt:136` |
| Delete download (downloaded) | Tap `Delete` | `onDownloadDelete(episode.url)` | `appController.deleteDownload(...)` | `EpisodeDownloadControl.kt:153`, `Destinations.kt:137` |

### 6.4 `SourceSwitcherDialog` — DEAD CODE

The `SourceSwitcherDialog` at `SourceSwitcherDialog.kt:29-92` is `internal` but **never invoked** — confirmed by Grep: the only match is its own declaration. Source switching happens via the source-name chip → `ManualSearchSheet` path instead. This file is a leftover from an earlier design and can be deleted during the unified-page refactor.

### 6.5 Actions **not** on the page (gaps to consider for unified page)

The current details page does **not** offer:
- **Track** (AniList/MAL status update) — only via the auto-sync from `WatchProgressStore` (`TrackSyncManager.start()` in `App.kt`); no manual "set status" button.
- **Share** — no share intent.
- **Web search** (e.g. open MAL/AniList page in browser) — no button.
- **Open external links** — none.
- **Mark all episodes seen/unseen** — no batch action.
- **Three-dot menu** — `MoreHoriz` button at `DetailBanner.kt:116` is a no-op stub.

---

## 7. State Management

### 7.1 ViewModel

`AnimeDetailViewModel` (`AnimeDetailViewModel.kt:42-107`) is a plain `androidx.lifecycle.ViewModel` (not a Voyager `ScreenModel`). It's instantiated inside the Composable via `viewModel(key = "detail_$animeId", factory = …)` at `AnimeDetailScreen.kt:96-115` — keyed on `animeId` so it survives configuration changes and is reused when the same anime is re-opened.

The VM constructor takes 11 dependencies (`AnimeDetailViewModel.kt:54-65`): `anilistId`, `api`, `extensionManager`, `sourceMatcher`, `animeRepository`, `categoryRepository`, `episodeRepository`, `extensionLinkStore`, `sourceLinkStore`, `episodeMetadataRepository`, `appContext`.

> **Quirk worth noting:** `extensionManager` is injected but **never used** inside the VM (Grep confirms no `extensionManager.` call). It's likely a leftover from earlier code; `sourceMatcher` is the actual workhorse for source lookups. The future unified-page refactor should drop it.

### 7.2 StateFlow inventory

All state is exposed as `StateFlow` (read-only) backed by `MutableStateFlow` (private). 16 distinct flows:

| Flow | Type | Declared at | Updated by |
|---|---|---|---|
| `animeState` | `DetailState` (Loading/Success/Error) | line 71-72 | `loadAnimeDetails` |
| `episodeState` | `EpisodeState` (Idle/Searching/Loading/Loaded/NoMatch/NotReleased/Error) | line 75-76 | every stage |
| `episodeMetadata` | `Map<Int, EpisodeMetadata>` | line 79-80 | `fetchEpisodeMetadata` |
| `allMatches` | `List<SourceMatcher.SourceMatch>` | line 83-84 | `findAndLoadEpisodes` + `searchAllSourcesInBackground` |
| `currentMatch` | `SourceMatcher.SourceMatch?` | line 87-88 | `findAndLoadEpisodes`, `switchSource`, `linkManual` |
| `watchedEpisodes` | `Set<String>` | line 91-92 | `toggleWatched` (in-memory only — comment at line 89: "Phase 5 = no persistence") |
| `isSaved` | `Boolean` | line 95-96 | `init` collector on `animeRepository.observeByAnilistId` |
| `categories` | `List<Category>` | line 99-100 | `init` collector on `categoryRepository.observeVisible` |
| `showCategoryPicker` | `Boolean` | line 103-104 | `openCategoryPicker` / `dismissCategoryPicker` / `saveToCategories` |
| `currentAnimeCategoryIds` | `Set<Long>` | line 107-108 | `openCategoryPicker` (loads existing categories first) |
| `isRefreshing` | `Boolean` | line 116-117 | `refresh` (set true) / `loadAnimeDetails` finally (set false) |
| `isSearching` | `Boolean` | line 124-125 | `manualSearch` |
| `manualSearchResults` | `List<ManualSearchResult>` | line 128-129 | `manualSearch` / `clearManualSearch` |
| `manualSearchErrors` | `List<Pair<String, String>>` | line 138-139 | `manualSearch` / `clearManualSearch` |
| `autoMatchErrors` | `List<Pair<String, String>>?` | line 147-148 | `findAndLoadEpisodes` + `searchAllSourcesInBackground` (null = hasn't run) |
| `hasSearched` | `Boolean` | line 153-154 | `manualSearch` / `clearManualSearch` |

Additionally, `sourcePrefs = appContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)` (line 157) holds the per-anime explicit source preference (key `"source_pref_$anilistId"` → `Long` sourceId, written by `switchSource` and `linkManual`, read by `findAndLoadEpisodes`).

### 7.3 State-flow to UI

`AnimeDetailScreen.kt:117-135` `collectAsState()`s every flow above into Compose `State`. The collected values are passed as plain parameters down through `DetailContent` → its children. No `Flow` is plumbed below `DetailContent` — it's a one-way value pipeline, which keeps the lower Composables pure-testable.

`availableSources` at line 135 is computed once via `remember { vm.getAvailableSources() }` (not a StateFlow) because the list doesn't change while the screen is open.

### 7.4 Local UI state

- `AnimeDetailScreen.kt:216` — `var showAddCategory by remember { mutableStateOf(false) }` toggles between `CategoryPickerDialog` and `AddCategoryDialog`.
- `EpisodesSection.kt:107` — `var showManualSearch by remember { mutableStateOf(false) }` toggles the manual-search sheet.
- `DetailInfo.kt:77` — `var expanded by remember { mutableStateOf(false) }` for the synopsis show-more/less.
- `ManualSearchSheet.kt:105,110` — `var query` and `var selectedSourceId` use `rememberSaveable` so they survive rotation.

### 7.5 Episode display preferences

`EpisodeDisplayPreferences` (`EpisodeDisplayPreferences.kt:20-107`) is Koin-injected at `EpisodesSection.kt:114` via `koinInject()`. Its 20 `Preference<T>` accessors are each collected reactively via `Preference.changes()` in the `rememberEpisodeDisplaySnapshot` helper (`EpisodesSection.kt:810-870`) — producing an immutable `EpisodeDisplayPrefs` data class snapshot that's `remember`ed with all 20 keys as keys. This means **settings changes from the `:feature:episode-settings` screens propagate live** to the open details page.

### 7.6 Theme-prefs state

`AnimeDetailScreen.kt:88-94` reads three `ThemePreferences` flows via `collectAsStateWithLifecycle`:
- `adaptiveColorsDetails: Preference<Boolean>` (default true) — defined at `core/preferences/src/main/java/app/confused/anikuta/core/preferences/ThemePreferences.kt:114`.
- `themeMode: Preference<ThemeMode>`.
- `amoled: Preference<Boolean>`.

These drive the dynamic-scheme computation (see §9).

---

## 8. ExtensionDetailScreen.kt Analysis (the "before" picture)

This screen is the existing parallel path for anime not on AniList. It's slated for removal once the unified `AnimeDetailScreen` can render either AniList or extension data via a translation layer. Below: what it does today, how it differs from `AnimeDetailScreen`, and its shortcomings.

### 8.1 File location & signature

`ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/ExtensionDetailScreen.kt:85-155`:

```kotlin
@Composable
fun ExtensionDetailScreen(
    source: AnimeCatalogueSource,
    sAnime: SAnime,
    onBack: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>) -> Unit,
    onRelinkAnilist: () -> Unit = {},
)
```

### 8.2 Data source — SAnime directly (no AniList)

The screen consumes `SAnime` (the extension contract) directly. The VM `ExtensionDetailViewModel` (`ExtensionDetailViewModel.kt:41-48`) wraps it into a local `ExtensionAnime` data class (`ExtensionDetailViewModel.kt:283-293`):

```kotlin
data class ExtensionAnime(
    val title: String,
    val description: String?,
    val genre: List<String>,           // split from SAnime.genre via ", "
    val coverUrl: String?,             // SAnime.thumbnail_url
    val backgroundUrl: String?,        // SAnime.background_url
    val status: Int,                   // SAnime.status (0/1/2)
    val sourceName: String,
    val url: String,
    val sourceId: Long,
)
```

This mapping happens at `ExtensionDetailViewModel.kt:87-99` in `loadExtensionAnime()`. The result is published as `ExtensionDetailState.Success(ExtensionAnime)`.

### 8.3 VM differences from `AnimeDetailViewModel`

| Aspect | `AnimeDetailViewModel` | `ExtensionDetailViewModel` |
|---|---|---|
| ID strategy | `anilistId: Int` everywhere | No anilistId — keyed by `source.id + sAnime.url` |
| Stage 1 (AniList fetch) | Yes (`api.fetchById`) | None — directly builds `ExtensionAnime` from `sAnime` |
| Stage 2 (source match) | Yes (`sourceMatcher.matchAll`) | None — source is already known; constructs a fake `SourceMatch` with `score=1.0` at line 101 |
| Stage 3 (episodes) | `source.getEpisodeList` via `SourceMatcher.SourceMatch` | `source.getEpisodeList(sAnime)` directly (`ExtensionDetailViewModel.kt:117`) |
| Episode metadata enrichment | Yes (`fetchEpisodeMetadata`) | **No** — `episodeMetadata = emptyMap()` is hardcoded at `ExtensionDetailScreen.kt:207` and `showMetadataLoading = false` at line 223 |
| Manual search / source switching | Yes | **No** — `onSwitchSource = {}`, `onManualSearch = { _, _ -> }`, `onLinkManual = { _, _ -> }` all no-ops (`ExtensionDetailScreen.kt:219-222`) |
| Library save | By `anilistId` (`AnimeDetailViewModel.kt:180`) | By `source.id + sAnime.url` (`animeRepository.getBySourceAndUrl`, `ExtensionDetailViewModel.kt:72,104,136,207`) |
| Refresh | Re-runs all 3 stages | Just calls `loadEpisodesFromSource` then 500ms delay clears `isRefreshing` (`ExtensionDetailViewModel.kt:260-268`) |
| Watched-episodes toggle | Yes (in-memory) | `toggleWatched` is **missing** entirely — `onToggleWatched = {}` at `ExtensionDetailScreen.kt:218` |
| Download controls | Yes (5 callbacks wired) | **Missing** — `EpisodesSection` is called without any download params (defaults kick in, all no-ops) |

### 8.4 UI sections (mirror of `AnimeDetailScreen`)

`ExtensionDetailContent` at `ExtensionDetailScreen.kt:161-227` builds the same `LazyColumn` layout:

1. `ExtensionDetailBanner` (lines 240-360) — copy-paste of `DetailBanner` with `ExtensionAnime` substituted for `AniListAnime`. Same 360dp blurred cover, same gradient, same action buttons. **Differences:**
   - Cover-color tint is hardcoded to `surfaceVariant` (line 247) — no AniList `coverImage.color` available, and `PaletteExtraction.extractCoverColor` is a stub (see §9).
   - Adds an extra `"A"` button next to the bookmark (lines 300-306) — opens `onRelinkAnilist` to start the linking flow.
   - Meta line only includes `status` (mapped: 1→"ongoing", 2→"completed") + `sourceName` (lines 340-347) — no score, no episode count, no next-airing pill.
2. `ExtensionGenresRow(genres)` (lines 362-388) — local copy of `GenresRow` taking `List<String>` instead of `AniListAnime`. Uses `LazyRow` (slightly different from `GenresRow`'s `Row + horizontalScroll`). Style differs subtly: `surfaceVariant@0.6f` vs `primaryContainer@0.6f`, 12sp Medium vs 11sp ExtraBold.
3. `SynopsisSection(anime.description!!)` (line 197) — **reused** from `DetailInfo.kt:75`.
4. `EpisodesSection(...)` (lines 201-225) — **reused** from `EpisodesSection.kt:64` but with all the source-switcher / manual-search / download / metadata parameters stubbed out (see §8.3 table).
5. **No InfoSection** — extension data doesn't have format/studio/season-year/start-date/MAL-id, so the information table is omitted entirely.

### 8.5 Shortcomings (why it should be removed)

1. **Massive code duplication.** `ExtensionDetailBanner` is a ~120-line near-verbatim copy of `DetailBanner` with only the data type swapped. `ExtensionGenresRow` duplicates `GenresRow`. The `ActionButton` helper is even re-declared locally (`ExtensionDetailScreen.kt:391-425`) — the same name as `DetailBanner.kt:184-214`'s `ActionButton` but with a `text: String?` parameter added.
2. **Feature gaps that the unified page should fill:**
   - No episode metadata enrichment (no Jikan/Anikage/AniList-streaming titles/descriptions/thumbnails/air-dates).
   - No source switching — the user is locked to the source they came from. The `SourceSwitcherDialog` and `ManualSearchSheet` are not wired.
   - No download buttons on episode rows.
   - No watched-toggle on episode rows.
   - No category picker (long-press bookmark just toggles, no `setAnimeCategories` path).
3. **Inconsistent persistence.** Saves with `sourceId` + `url` instead of `anilistId`, so library entries created here are invisible to every `observeByAnilistId` consumer (library page, history page, etc.) until the user re-links to AniList via the "A" button. Once linked, `AppController.onLinked` calls `nav.replace(AnimeDetailDestination(anilistId))` (`AppController.kt:541`) — but the saved `Anime` row's `anilistId` field is still null; only `ExtensionLinkStore` knows the mapping.
4. **No adaptive theming.** `coverColor = MaterialTheme.colorScheme.surfaceVariant` hardcoded at line 247 — the dynamic-scheme wrap from `AnimeDetailScreen.kt:208-212` is absent. The `PaletteExtraction` skeleton (see §9) was designed to fill this gap but isn't wired yet.
5. **Different bookmark long-press behaviour.** `ActionButton` here always takes `onLongClick` (default `{}`); the bookmark long-press at `ExtensionDetailScreen.kt:298` doesn't pass anything, so it silently does nothing — no category picker.
6. **Internal `items` shadow.** A local `private fun LazyListScope.items(count, itemContent)` is declared at lines 427-429 to work around an import shadow. Fragile.
7. **Refresh is fake.** `ExtensionDetailViewModel.refresh()` at lines 260-268 just calls `loadEpisodesFromSource` and then unconditionally waits 500ms before clearing `isRefreshing` — it doesn't actually wait for the load to complete.

### 8.6 What it gets right (preserve in the unified page)

- The `ExtensionAnime` data class is a **clean, minimal shape** that's close to what a unified `DetailAnime` view-model would look like — title, description, genres, coverUrl, status, sourceName. The mapping at `ExtensionDetailViewModel.kt:87-99` (SAnime → ExtensionAnime) is essentially the translation layer the unified page needs.
- The reuse of `SynopsisSection` and `EpisodesSection` proves those composables are mostly data-agnostic — they only need an `AniListAnime`-shaped or `SEpisode`-shaped input. The unified page can keep them as-is.
- The "A" (relink) button is a UX precedent for the future three-dot menu's "link to AniList" action.

---

## 9. Phase 9 Integration Points (Cover Color / Palette / Theme)

### 9.1 The dynamic-theme wrap in `AnimeDetailScreen`

`AnimeDetailScreen.kt:83-160` is the entire Phase 9 integration on the details page. Quoted verbatim from the source (with the inline comments stripped for brevity):

```kotlin
// AnimeDetailScreen.kt:88-94
val themePrefs = remember { org.koin.core.context.GlobalContext.get().get<ThemePreferences>() }
val adaptiveColorsDetails by themePrefs.adaptiveColorsDetails.changes()
    .collectAsStateWithLifecycle(initialValue = themePrefs.adaptiveColorsDetails.get())
val themeMode by themePrefs.themeMode.changes()
    .collectAsStateWithLifecycle(initialValue = themePrefs.themeMode.get())
val amoled by themePrefs.amoled.changes()
    .collectAsStateWithLifecycle(initialValue = themePrefs.amoled.get())

// AnimeDetailScreen.kt:138-160
val coverColorArgb: Int = remember(animeState) {
    val state = animeState
    if (state is DetailState.Success) {
        val hex = state.anime.coverColorHex     // ← imported at line 21
        if (hex != null) {
            runCatching { AndroidColor.parseColor(hex) }.getOrDefault(0)
        } else {
            0
        }
    } else {
        0
    }
}
val isDark = when (themeMode) { … }
val dynamicScheme = if (adaptiveColorsDetails && coverColorArgb != 0) {
    generateDynamicScheme(coverColorArgb, darkTheme = isDark, amoled = amoled)   // ← imported at line 22
} else {
    null
}

// AnimeDetailScreen.kt:207-212
if (dynamicScheme != null) {
    MaterialTheme(colorScheme = dynamicScheme, content = screenContent)
} else {
    screenContent()
}
```

Key observations:
- The cover color is sourced from `AniListAnime.coverColorHex` (the `coverImage.color` field — a hex string AniList provides per anime, defined at `AniListAnime.kt:54`). NOT from Palette extraction.
- `generateDynamicScheme(coverColorArgb, darkTheme, amoled)` returns `ColorScheme?` — null when `coverColor == 0`. The `if (dynamicScheme != null)` branch wraps the whole screen content in a MaterialTheme override; otherwise the user's selected palette (set in `AnikutaTheme` at the app root) shows through.
- The `coverColorArgb` computation is keyed on `animeState` (`remember(animeState)`) so it re-computes when `Loading → Success` resolves.
- The wrap is **outside** the `when (animeState)` switch, so loading + error states also get the dynamic theme — but since `coverColorArgb = 0` while `state !is DetailState.Success`, they fall back to the user palette.

### 9.2 The cover-color tint in `DetailBanner` / `DetailContent`

Independently of the MaterialTheme wrap, `DetailContent.kt:79-86` parses the same `coverColorHex` into a Compose `Color` (with a hardcoded fallback `Color(0xFF1A1A2E)`), and `DetailBanner.kt:83` tints the blurred cover image at 20% alpha:

```kotlin
// DetailContent.kt:79-86
val coverColor = remember(anime) {
    anime.coverColorHex?.let { hex ->
        runCatching {
            val rgb = if (hex.startsWith("#")) hex.substring(1) else hex
            Color(AndroidColor.parseColor("#$rgb"))
        }.getOrNull()
    } ?: Color(0xFF1A1A2E)   // ← hardcoded fallback when no color
}
```

This tint is purely cosmetic on the banner — it does NOT depend on `adaptiveColorsDetails` and is always applied (with the fallback). The dynamic MaterialTheme wrap at `AnimeDetailScreen.kt:208-212` is a separate, additional layer that re-themes the entire subtree.

### 9.3 `generateDynamicScheme` (Phase 9 implementation)

Lives at `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/CoverColor.kt:32-84`. Returns a full `ColorScheme` (light or dark, AMOLED-aware) with `primary = dominant cover color`, `primaryContainer = cover@30% over Black`, etc. Returns `null` when `coverColor == 0` — the caller handles fallback.

Companion helper `extractDominantColor(bitmap: Bitmap?): Int` at lines 90-98 uses `androidx.palette.graphics.Palette` to pull the dominant color from a Bitmap. This is the intended future path for extension-only anime whose covers have no AniList-provided color.

### 9.4 `PaletteExtraction` (Phase 9 skeleton)

Lives at `ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/PaletteExtraction.kt:25-85`. It's an `object` with two functions:

- `extractFromBitmap(bitmap: Bitmap?): Int?` (lines 39-53) — **implemented**, takes a pre-loaded Bitmap, delegates to `extractDominantColor` (from `CoverColor.kt`), returns ARGB or null. Used nowhere yet.
- `extractCoverColor(coverUrl: String): Int?` (lines 77-84) — **STUB**. Always logs a warning and returns null. The intended implementation (documented in the KDoc at lines 64-72) would download the cover via Coil's `ImageLoader` (which lives in the feature modules, not in `:core:designsystem` — see commit `4cd3e66`'s message: "remove Coil/OkHttp deps (not in :core:designsystem)").

The intended usage pattern (documented in the file's header KDoc, lines 6-23): the **calling feature module** (e.g. `:feature:anime-details`, which has Coil) downloads the cover via `ImageLoader`, then calls `PaletteExtraction.extractFromBitmap(bitmap)` to get the ARGB color. This is the path the unified page should use when rendering extension-only anime whose `SAnime` has no `coverColorHex`.

### 9.5 The `AnikutaTheme` (root theme) — for context

`ANIKUTA_PROJECT/ANIKUTA/core/designsystem/src/main/java/app/confused/anikuta/core/designsystem/theme/Theme.kt:47-112`. Composes the app's outer `MaterialTheme` with the user-selected `ThemeMode`, `AccentPreset`, `paletteMode`, AMOLED toggle, and full-palette overrides. Cross-fade animation (~400ms tween per color role) at lines 121-162. The details page's dynamic-scheme wrap (§9.1) **overrides** this for the detail screen's subtree only — backing out restores the user's palette.

### 9.6 Commits that touched this integration

Per `git log -- AnimeDetailScreen.kt`:

| Commit | Date | Effect |
|---|---|---|
| `a14fafe` | 2026-07-27 | "feat: add adaptive colors toggle preferences + fix generateDynamicScheme + wire dynamic theming" — added `adaptiveColorsDetails` + `adaptiveColorsPlayer` prefs to `ThemePreferences`, made `generateDynamicScheme` return nullable, wired the dynamic-scheme wrap into `AnimeDetailScreen.kt` (added 126 lines, removed 74). Also added `lifecycle-runtime-compose` dep to `:feature:anime-details` for `collectAsStateWithLifecycle`. |
| `4cd3e66` | 2026-07-27 | "fix(build): PaletteExtraction — remove Coil/OkHttp deps (not in :core:designsystem)" — restructured `PaletteExtraction.kt` so the bitmap-based function compiles without Coil and the URL-based function is a documented skeleton. Did NOT touch `AnimeDetailScreen.kt`. |
| `fd985f4` | 2026-07-27 | "fix(build): fix drag gestures, Canvas ambiguity, formatTime conflict, type inference" — fixed `coverColorArgb` type inference in `AnimeDetailScreen.kt` (added explicit `Int` annotation + local `val state` for smart-cast, 19-line diff). |
| `fa9ba8a` | 2026-07-27 | "fix(build): import coverColorHex extension property in AnimeDetailScreen" — single-line import fix. |

### 9.7 What the unified-page plan must preserve

1. **The `adaptiveColorsDetails` toggle** — read at `AnimeDetailScreen.kt:89-90`. The unified page should keep honoring this preference for both AniList-sourced and extension-sourced data.
2. **The `coverColorArgb` computation + `MaterialTheme(dynamicScheme)` wrap** — lines 138-160, 207-212. When extension data is the source, `coverColorHex` will be null (SAnime has no color field), so the wrap will fall through to the user's palette UNLESS the page calls `PaletteExtraction.extractFromBitmap` on the cover thumbnail. **Recommendation:** wire `PaletteExtraction` into the extension-data translation layer so the dynamic theme works for extension-only anime too.
3. **The `DetailContent.kt:79-86` cover-color tint** — should keep working when AniList data is present. For extension data, either re-use the Palette-extracted color or fall back to `surfaceVariant` (as `ExtensionDetailScreen.kt:247` does today).
4. **The `amoled` + `themeMode` flow subscriptions** — these drive `isDark` and the AMOLED surface overrides inside `generateDynamicScheme`. The unified page should keep subscribing to them.
5. **The `remember(animeState)` keying** — the cover-color computation re-runs whenever `animeState` changes (e.g. AniList data arrives). For the unified page, the key should be the unified state holder (so the scheme re-computes when switching from AniList data → extension data).

---

## 10. Summary — Key Findings for the Unified-Page Plan

- **One entry parameter today:** `animeId: Int`. The unified page will need to accept either an `animeId` OR a `(source, sAnime)` pair — Voyager's `AnimeDetailDestination` will need a sealed/union parameter, OR a separate `ExtensionDetailDestination`-as-`AnimeDetailDestination` rewrite.
- **The 3-stage load is the right shape** for the unified page: stage 1 becomes "fetch metadata from the active source" (AniList OR extension), stage 2 becomes "find episodes" (extension `getEpisodeList`), stage 3 stays "fetch episode metadata" (only when anilistId is known).
- **`ExtensionAnime` is a good template** for the unified view-state — extend it to add the optional AniList fields (score, format, studio, season, next-airing, etc.) as nullable, and have the translation layer populate what it can.
- **The dead `SourceSwitcherDialog`** can be deleted; the source-switcher UX is already handled by `ManualSearchSheet`.
- **The no-op three-dot button** at `DetailBanner.kt:116` is the natural home for the new "switch source / switch between AniList and extension data" menu.
- **Phase 9 palette integration** is fully wired for AniList data (`adaptiveColorsDetails` + `coverColorHex` → `generateDynamicScheme`). Extension data needs `PaletteExtraction` to be wired (currently a skeleton) to get the same treatment.
- **`extensionManager` is dead weight** in `AnimeDetailViewModel` — drop it during the refactor.
- **The `onToggleWatched` callback** is plumbed through `EpisodeRow` but no Composable actually invokes it — a latent feature waiting for a UI trigger.
