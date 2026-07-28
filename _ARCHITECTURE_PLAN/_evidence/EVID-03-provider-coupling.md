# EVID-03 — AniList Provider Coupling Map

**Task ID:** EVID-03-PROVIDER-COUPLING
**Agent:** Explore (research-only)
**Scope:** Every system in the ANIKUTA app at `/home/z/my-project/anikuta/ANIKUTA_PROJECT/ANIKUTA/` — how it depends on AniList, the severity of coupling, what would need to change to decouple, and `file:line` evidence.
**Output purpose:** Drives the provider-abstraction-layer proposal (next phase).
**Verdict in one line:** AniList is woven through the **identity layer** (`anilistId` is the primary key for watch progress, downloads, tracker sync, episode metadata, library cover updates, backup), the **metadata layer** (browse feed, search, details page, schedule, update-check cross-ref, episode metadata merge), and the **tracker layer** (AniList is one of two trackers). Only the **extension-only details page** (ADR-039 `ExtensionDetailsProvider` unlinked mode), **category memberships**, **episode list ordering inside an extension**, and **MAL-tracker stats** are genuinely AniList-free.

---

## 0. The `:core:anilist` module — public surface

### 0.1 Files (5 Kotlin files, ~1,173 lines)

| File | Purpose |
|---|---|
| `core/anilist/.../api/AniListApi.kt` (727 lines) | Raw GraphQL client. **The de-facto AniList repository** — there is no `AniListRepository` class. |
| `core/anilist/.../api/AniListRateLimiter.kt` (96 lines) | Sliding-window rate limiter (80 req/min cap, 40-req fast mode). |
| `core/anilist/.../api/LocalAniListCache.kt` (108 lines) | 24h persistent cache (trending, popular, per-id detail) via `PreferenceStore`. |
| `core/anilist/.../model/AniListAnime.kt` (165 lines) | `@Serializable` `AniListAnime` + nested types + extension helpers (`displayTitle`, `coverUrl`, `coverColorHex`, `seasonDisplay`, `studioName`, `startDateDisplay`, `nextAiringDisplay`). Also `AiringScheduleInfo`. |
| `core/anilist/.../details/AniListAnimeMapper.kt` (77 lines) | `AniListAnime.toUnifiedAnime(matchedSourceId, matchedSourceName)` → `UnifiedAnime` (`DataSource.ANILIST`). |

### 0.2 `AniListApi` public method signatures (every method)

Declared at `core/anilist/src/main/java/app/confused/anikuta/core/anilist/api/AniListApi.kt`:

```kotlin
class AniListApi(
    private val client: OkHttpClient = defaultClient(),
    private val localCache: LocalAniListCache? = null,
    private val rateLimiter: AniListRateLimiter? = null,
)
```

| Method | Signature | Line | Cached? |
|---|---|---|---|
| `fetchTrending` | `suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime>` | L63 | 24h local + in-memory SWR |
| `fetchPopular` | `suspend fun fetchPopular(page: Int = 1, perPage: Int = 20): List<AniListAnime>` | L80 | 24h local + in-memory SWR |
| `searchAnime` | `suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 20): List<AniListAnime>` | L95 | NOT cached |
| `searchByMalId` | `suspend fun searchByMalId(malId: Int): AniListAnime?` | L108 | NOT cached (used by Aniyomi restore) |
| `searchByTitle` | `suspend fun searchByTitle(title: String): AniListAnime?` | L137 | NOT cached (Aniyomi restore) |
| `searchByTitleMultiple` | `suspend fun searchByTitleMultiple(title: String, perPage: Int = 10): List<AniListAnime>` | L167 | NOT cached (manual linking) |
| `searchAnimeWithFilters` | `suspend fun searchAnimeWithFilters(query, page, perPage, genres, year, season, format, status, sort, minScore): List<AniListAnime>` | L201 | NOT cached |
| `getCachedTrending` | `fun getCachedTrending(): List<AniListAnime>?` | L296 | Sync read of in-memory + local cache |
| `fetchAiringSchedule` | `suspend fun fetchAiringSchedule(ids: List<Int>): List<AiringScheduleInfo>` | L354 | 5-min in-memory cache, keyed by sorted-id tuple |
| `fetchById` | `suspend fun fetchById(id: Int): AniListAnime?` | L467 | 5-min in-memory + 24h local persistent |

**GraphQL endpoints:** `https://graphql.anilist.co` (constant `API_URL`, L609). All queries are unauthenticated (browse-only). Authenticated access lives separately in `:core:tracker/anilist/AniListTrackApi.kt`.

### 0.3 `LocalAniListCache` public surface (`LocalAniListCache.kt`)

```kotlin
class LocalAniListCache(private val store: PreferenceStore)
```
- `dailyRefreshMs = 24h` (L49)
- `getCachedTrending(): List<AniListAnime>` (L51)
- `getCachedPopular(): List<AniListAnime>` (L52)
- `getTrendingTimestamp(): Long` / `getPopularTimestamp(): Long` (L54-55)
- `isTrendingStale(): Boolean` / `isPopularStale(): Boolean` (L57-61)
- `saveTrending(list)` / `savePopular(list)` (L63-71)
- `getCachedDetail(id: Int): AniListAnime?` (L92) — 24h TTL per-id map
- `saveDetail(anime: AniListAnime)` (L100)

Persistence keys (in `PreferenceStore`):
- `local_cache_trending`, `local_cache_popular` (lists as JSON)
- `local_cache_trending_ts`, `local_cache_popular_ts` (Long)
- `local_cache_details` (`Map<Int /* anilistId */, String /* JSON */>`)
- `local_cache_details_ts` (`Map<Int, Long>`)

### 0.4 `AniListRateLimiter` public surface (`AniListRateLimiter.kt`)

```kotlin
class AniListRateLimiter
```
- `suspend fun acquire()` (L47) — blocks until safe to send.
- `fun currentRate(): Int` (L92)
- `fun totalRequests(): Int` (L95)

### 0.5 Other public types in `:core:anilist`

- `AniListAnime`, `AniListTitle`, `AniListCoverImage`, `AniListFuzzyDate`, `AniListStudioConnection`, `AniListStudio`, `AniListAiringSchedule`, `AiringScheduleInfo` (model/AniListAnime.kt)
- Extension helpers on `AniListAnime`: `displayTitle`, `coverUrl`, `coverColorHex`, `seasonDisplay`, `studioName`, `startDateDisplay`, `nextAiringDisplay`
- `fun AniListAnime.toUnifiedAnime(matchedSourceId, matchedSourceName): UnifiedAnime` (details/AniListAnimeMapper.kt L31)

### 0.6 `:core:tracker/anilist/AniListTrackApi` — separate authenticated client

A **second** AniList client lives in `:core:tracker` (separate from `:core:anilist`). It is authenticated (OAuth token) and serves the tracker use case only — `fetchViewer`, `fetchUserAnimeList`, `fetchUserStats`, `updateProgress`. See §3 below.

---

## 1. Summary table — every system × AniList coupling

| # | System | Coupling | What depends on AniList | What breaks if AniList vanished | Evidence (file:line) | Decoupling effort |
|---|---|---|---|---|---|---|
| 1 | Library | **Moderate** | Library DB row has `anilist_id` (nullable); continue-watching derivation groups by `anilistId` parsed from progress key; `updateLastWatchedByAnilistId` is the only refresh hook | Library still loads (anime rows survive — `anilist_id` is nullable); continue-watching breaks for rows with `anilistId=null` (filter `substringBefore(':').toIntOrNull() ?: continue` skips them) | `animes.sq:29,38-39,113-124`; `AnimeRepositoryImpl.kt:49-60,145-152,170-205`; `LibraryViewModel.kt:184-214,391-399` | Medium |
| 2 | Tracking/sync (AniList) | **Tight** | `AniListTracker` is hardcoded as one of two trackers; `TrackerManager.trackers = listOf(anilistTracker, malTracker)`; `AniListTrackApi` is the AniList OAuth + GraphQL surface | AniList tracker login + progress sync + user-list fetch + user-stats all die | `TrackerManager.kt:13-21`; `Tracker.kt:41-43` (ANILIST_ID=2, MAL_ID=1); `AniListTracker.kt:25-147`; `AniListTrackApi.kt:35-305` | Medium (tracker interface is pluggable; just remove one impl) |
| 3 | Tracking/sync (MAL) | **Loose (via AniList)** | MAL tracker is independent of AniList at the API level, but `TrackSyncManager.syncAnimeProgress` keys off `anilistId` extracted from `WatchProgressStore` keys → MAL tracker can't be reached without an `anilistId` | Without AniList: library anime with `anilistId=null` cannot be tracker-synced at all (even if MAL-bound) | `TrackSyncManager.kt:74-104` (extractAnilistId from progress key, then `animeRepository.getByAnilistId(anilistId)`); `TrackRepository.kt:17-101`; `animetrack.sq:1-64` | Medium |
| 4 | Downloads | **Tight** | `DownloadAnimeInfo.anilistId: Int` is the PRIMARY KEY for folder structure (`Anime Title [anilistId]`), dedup (`"$anilistId:$episodeUrl"`), `deleteAnimeDownloads(anilistId)`, offline-playback lookup `isEpisodeDownloaded(anilistId, episodeUrl)` | All download folder paths + dedup + offline-lookup break. No fallback key (no `sourceId:url` variant) | `DownloadModels.kt:8-31,40-50`; `DownloadTask.kt:41` (`key = "${request.anime.anilistId}:${request.episode.episodeUrl}"`); `DownloadManager.kt:70,97-112`; `DownloadRequest.kt:21-46`; `DownloadOrchestrator.kt:61-65,332-356` | Large (touches DB schema, folder layout, dedup keys, offline-playback keys) |
| 5 | Watch history | **Tight** | `WatchProgressStore` keys are `"$anilistId:$episodeUrl"`; `save()` and `get()` require an `Int anilistId` | Watch progress for unlinked extension anime (`anilistId=null`) cannot be saved at all. Continue-watching + Library sort + History page all parse this key | `WatchProgressStore.kt:46-117,131-145`; `HistoryViewModel.kt:78-81,133-140`; `LibraryViewModel.kt:188-214` | Large (touches: store schema, backup schema, tracker sync, library continue-watching, history page) |
| 6 | Categories | **None (direct)** | `anime_category.sq` uses `anime_id` (the SQLDelight `_id`), never `anilist_id`. CategoryRepository is anilist-agnostic | Nothing breaks directly. But the junction table's `anime_id` references `animes._id`, and an anime row often has `anilist_id` populated — so categories inherit AniList-keyed identity transitively | `anime_category.sq:1-45`; `CategoryRepositoryImpl.kt:29-197` | None |
| 7 | Search | **Tight (AniList tab)** + **Loose (Extension tab)** | `SearchViewModel` directly injects `AniListApi`; default source is `ANILIST`; the FilterSheet UI is 100% AniList filter enums (genres, season, format, status, sort) | AniList tab dies entirely. Extension tab + extension default view (Popular/Latest) survive (they call `source.getSearchAnime` / `getPopularAnime` / `getLatestUpdates` directly). Recents are per-source so AniList recents die but Extension recents survive | `SearchViewModel.kt:6-9,32-33,153-186,196-212,343-402`; `FilterSheet.kt` (AniList enums) | Medium (need a `CatalogueSearchProvider` abstraction + pluggable filter UI per source type) |
| 8 | Search (extension→AniList linking) | **Tight** | `ExtensionLinkingViewModel.attemptLink()` calls `anilistApi.searchAnime(sAnime.title, perPage = 10)` and auto-links the first result. `ExtensionLinkStore` keys by `"$sourceId:$animeUrl"` → value is `anilistId` | Without AniList: tapping an extension search result can only open the extension-only detail page (`GoWithoutLinking` state). Library persistence for unlinked extension anime works via `getBySourceAndUrl` | `ExtensionLinkingViewModel.kt:6-8,82-138`; `ExtensionLinkStore.kt:11-115`; `SourceLinkStore.kt:9-69` | Medium |
| 9 | Details page (AniList mode) | **Tight** | `AniListDetailsProvider.loadByAniListId` calls `anilistApi.fetchById` then `sourceMatcher.matchAll(title)` to get episodes | Without AniList: `ByAniListId` requests can't be served. `ByExtension` requests with a linked `anilistId` would also fail (the provider tries AniList merge) | `AniListDetailsProvider.kt:49-87,227-278`; `AnimeDetailViewModel.kt:82-95,184-220,322-379,547-578` | Medium (registry already pluggable via `AnimeDetailsProviderRegistry`) |
| 10 | Details page (Extension mode, linked) | **Moderate** | `ExtensionDetailsProvider.loadByExtension` does an AniList merge: `anilistApi.fetchById(effectiveAnilistId)` → `mergeAniListMetadata` | Without AniList: linked extension anime lose score, format, season, studios, next-airing, cover color (but the extension's own metadata + episodes survive via `getAnimeDetails`) | `ExtensionDetailsProvider.kt:80-184` (esp. L169-177); `SAnimeMapper.kt:117-143` (`mergeAniListMetadata`) | Medium |
| 11 | Details page (Extension mode, unlinked) | **None** | `ExtensionDetailsProvider` skips AniList merge when `effectiveAnilistId == null` | Nothing breaks. This is the **only AniList-free details mode** (ADR-039 unlinked-extension flow) | `ExtensionDetailsProvider.kt:170-177`; `AnimeDetailViewModel.kt:649-685` (`findLibraryAnime` branches on anilistId) | None |
| 12 | Browse / Home | **Tight** | `BrowseScreen` directly injects `AniListApi`; home feed is `fetchTrending(perPage=30)` with 24h local cache | Without AniList: home page is empty (no extension-popular fallback). `getCachedTrending()` returns stale cache only | `BrowseScreen.kt:41-44,62-65,108-125` | Medium (need a `HomeFeedProvider` that can also surface trusted-extension Popular feeds) |
| 13 | Extensions | **Loose** | Extensions themselves don't know about `anilistId`; `AnimeExtensionManager` only loads APKs. AniList coupling is in `SourceMatcher` (which uses `AnimeCatalogueSource.getSearchAnime` — independent) and the link stores (which bridge AniList↔extension by ID) | Without AniList: extensions still install, load, search, fetch episodes. The link stores become orphaned (no AniList IDs to link to) | `AnimeExtensionManager.kt:48-100`; `SourceMatcher.kt:46-343`; `ExtensionLinkStore.kt:11-115`; `SourceLinkStore.kt:9-69` | Small (extensions are already AniList-free; just the link stores would need a new identity model) |
| 14 | Preferences (`:core:preferences`) | **None** | No `anilistId`-keyed prefs in this module. Only `ThemePreferences` mentions AniList in a comment | Nothing | `ThemePreferences.kt:109` (comment only) | None |
| 15 | Preferences (scattered across modules) | **Tight** (key-space) | Multiple prefs are keyed by `anilistId` — see §11.1 below | Per-anime source preference, view preference, watch progress, download dedup, episode-metadata cache, extension link store, source link store — all orphaned | See §11.1 table | Large (key-space migration required) |
| 16 | Backup / restore (ANIKUTA format) | **Tight** | `AnimeBackup.anilistId: Long?` is a primary keying field. `EpisodeBackupProvider` keys episodes by `anilistId.toString()` (falls back to `"sourceId:url"`). `WatchProgressBackupProvider` keys by `"$anilistId:$episodeUrl"`. `CoverImageProvider` keys covers by `anilistId`. `CategoryBackupProvider` builds `anilistId → localDbId` lookup table | Backups restore for AniList-keyed anime only; fallback `sourceId:url` keying exists for episodes but is weaker | `AnimeBackup.kt:43`; `EpisodeBackupProvider.kt:41-42,87-90`; `WatchProgressBackupProvider.kt:22,69-72`; `CoverImageProvider.kt:42-45`; `CategoryBackupProvider.kt:60-67,104-105,202-212`; `BackupMappers.kt:42-70` | Large (backup schema is stable, can't remove field; only deprecate + add new identity fields) |
| 17 | Backup (Aniyomi restore translator) | **Tight — AniList is the SOLE source of truth for cross-format identity** | `AniyomiBackupTranslator.resolveAnilistId` strategy: 1) AniList tracker binding → 2) MAL tracker binding → AniList `searchByMalId` lookup → 3) AniList `searchByTitle` | Without AniList: Aniyomi restore cannot map Aniyomi anime to ANIKUTA's identity model at all. The whole translator fails | `AniyomiBackupTranslator.kt:99-101,121-269,408-432`; `AniyomiRestoreViewModel.kt:7-8,51,95-99,258-263` | Large (the translator's purpose is AniList ID resolution) |
| 18 | Profile / Stats | **Moderate** | `StatsCalculator.observeStats()` is local-only (library + watch progress) — works without AniList. `fetchAniListStats()` enriches via `trackerManager.anilist.fetchUserStats()` only if logged in | Without AniList: profile still works in local mode. Format distribution + country distribution + mean-score-from-AniList are unavailable. `localStats.formatDistribution` is empty by design | `StatsCalculator.kt:25-178`; `ProfileViewModel.kt:34-72,93-138` | Small (already has local-mode fallback) |
| 19 | Updates (Schedule tab) | **Tight** | `UpdatesViewModel.fetchSchedule` reads library favorites, collects `anilistId`s, chunks into 50s, calls `anilistApi.fetchAiringSchedule(chunk)`. Library anime with `anilistId=null` are silently skipped | Without AniList: Schedule tab is empty. No fallback airing-data source | `UpdatesViewModel.kt:6-7,42-46,131-213`; `AniListApi.kt:354-398` (`fetchAiringSchedule`) | Medium (need an `AiringScheduleProvider` abstraction; could use MAL/Jikan as fallback) |
| 20 | Updates (Updates tab — new-episode check) | **Loose** | `UpdateChecker.checkAnimeInternal` calls `episodeFetchGateway.fetchEpisodes(title)` (extension-based) AND does an AniList cross-ref `anilistApi.fetchById(aid)` for `nextAiringEpisode` enrichment | Without AniList: new-episode detection still works (extension `getEpisodeList`). The "next airing" badge enrichment dies (non-fatal — it's not currently surfaced in the UI) | `UpdateChecker.kt:4,86-91,307-381` (esp. L363-369) | Small (cross-ref is non-fatal) |
| 21 | Episode metadata | **Tight (keyspace)** + **Moderate (one of 3 sources)** | Cache is keyed by `anilistId.toString()`. Three sources: Jikan/MAL (uses `malId`), AniList streaming (`animeId = anilistId`), Anikage.cc (uses anime title). Merge priority includes AniList for thumbnail | Without AniList: AniList streaming source dies. Cache key would need to change. Jikan + Anikage survive. The `EpisodeMetadataRequest.animeId` is documented as the AniList ID | `EpisodeMetadataRepository.kt:44-170`; `EpisodeMetadataCache.kt:21-90`; `AniListStreamingSource.kt:32-89` (URL: `https://graphql.anilist.co`); `JikanMalSource.kt:28-129`; `EpisodeMetadataSource.kt:23-55` | Medium (need an `animeId` abstraction that's not implicitly AniList) |
| 22 | Tracker sync (auto) | **Tight (AniList path) + Loose (MAL path)** | `TrackSyncManager.syncPendingProgress` parses `anilistId` from `WatchProgressStore` keys → `animeRepository.getByAnilistId(anilistId)` → `trackRepository.getTracks(anime.id)` → `tracker.updateProgress(track.remoteId, …)` | Without AniList: every anime in the library that has `anilistId=null` (unlinked extension anime) can't be auto-synced even if MAL-tracked. MAL-tracked anime with `anilistId != null` still work IF the AniList ID is preserved | `TrackSyncManager.kt:26-118` (esp. L74-104) | Large (the sync manager's identity model is `anilistId` everywhere) |

---

## 2. System-by-system narrative

### 2.1 Library (`:feature:library` + `:data:anime` `AnimeRepository`)

**Coupling: Moderate.**

The library is fundamentally a favorites filter on the `animes` SQLDelight table. The `animes` schema (`core/database/.../animes.sq:3-35`) has `anilist_id INTEGER` as a nullable column (L29) with a partial unique index `idx_animes_anilist_id` (L38-39) — so a library entry CAN have `anilistId=null`, but AniList-keyed lookups are first-class (`selectByAnilistId` L47-48, `updateFavoriteByAnilistId` L98-99, `updateLastWatchedByAnilistId` L113-114, `updateAnilistMetadataByAnilistId` L116-124, `updatePreferredCoverByAnilistId` L129-133).

`AnimeRepositoryImpl` (`data/anime/.../AnimeRepositoryImpl.kt`) exposes 5 AniList-keyed methods: `observeByAnilistId` (L49), `getByAnilistId` (L58), `updateFavoriteByAnilistId` (L145), `updateLastWatchedByAnilistId` (L170), `updateAnilistMetadata` (L177), `updatePreferredCoverByAnilistId` (L197). Each has a `sourceId+url` variant for unlinked extension anime (`getBySourceAndUrl` L62, `updatePreferredCoverBySourceAndUrl` L207).

`LibraryViewModel` (`feature/library/.../LibraryViewModel.kt`):
- L58: `animeRepository.observeFavorites()` — no AniList dependency.
- L184-186: continue-watching lookup is `libraryAnime.mapNotNull { anime.anilistId?.let { it to anime } }.toMap()` — **anime with `anilistId=null` are excluded from continue-watching**.
- L188-193: progress entries are grouped by `key.substringBefore(':').toIntOrNull() ?: continue` — **progress entries with non-integer prefixes are silently dropped** (this is the AniList keying surfacing in the VM).
- L391-399: `updateLastWatched(anilistId: Int)` is called from the player on watch — it requires an `Int anilistId`.

**What breaks if AniList vanished:** Library grid still renders (the DB row survives). Continue-watching section is empty for any anime without `anilistId`. Library sort by `LAST_WATCHED` works only for AniList-keyed anime.

**Decoupling effort:** Medium. The library table schema is fine; the issue is `LibraryViewModel.deriveContinueWatching` hardcoding `substringBefore(':')`. Need a unified identity model (e.g., a `WatchableId` type that can be either `AniListId(Int)` or `ExtensionId(sourceId, url)`).

---

### 2.2 Tracking/sync (`:core:tracker`)

**Coupling: Tight (AniList) + Loose-but-indirect (MAL via AniList-keyed progress).**

The tracker system has 4 layers:

1. **`Tracker` interface** (`core/tracker/.../Tracker.kt:11-44`) — pluggable contract. `Tracker.ANILIST_ID = 2`, `Tracker.MAL_ID = 1` (Aniyomi syncId convention). Already abstracted.
2. **`TrackerManager`** (`TrackerManager.kt:12-41`) — **HARDCODES** `listOf(anilistTracker, malTracker)` (L16). Adding a third tracker (Shikimori/Bangumi/Simkl per ADR-019) = add a constructor param + a list entry. There's no `Set<Tracker>` Koin multi-binding.
3. **`TrackSyncManager`** (`TrackSyncManager.kt:26-118`) — auto-syncs watch progress. **Identity leak:** L74-82 `extractAnilistId(progressMap, progress)` parses `anilistId` from the `WatchProgressStore` key (format `"$anilistId:$episodeUrl"`). L85-104 `syncAnimeProgress(anilistId, progress)` then calls `animeRepository.getByAnilistId(anilistId)` → `trackRepository.getTracks(anime.id)` → `tracker.updateProgress(track.remoteId, …)`. **So even MAL-tracker sync only works for anime with a non-null `anilistId`.**
4. **`animetrack.sq`** (`core/database/.../animetrack.sq:1-64`) — the binding table is provider-agnostic in schema (`anime_id`, `tracker_id`, `remote_id`, `remote_url`, …). `UNIQUE(anime_id, tracker_id)` (L14). The AniList coupling is purely in *how* `TrackSyncManager` resolves the local `anime_id` — via `anilistId`.

`AniListTracker` (`core/tracker/.../anilist/AniListTracker.kt:25-147`):
- Stores token/username/avatar/userId in `PreferenceStore` keys `pref_tracker_anilist_token`, `pref_tracker_anilist_username`, `pref_tracker_anilist_avatar`, `pref_tracker_anilist_user_id` (L138-141).
- Uses its OWN OkHttp client (`AniListTrackApi`, `core/tracker/.../anilist/AniListTrackApi.kt:35-305`) — separate from `:core:anilist`'s unauthenticated `AniListApi`. Two AniList clients exist.
- CLIENT_ID is hardcoded `"5338"` (L145, Aniyomi's value).

`MalTracker` (`core/tracker/.../mal/MalTracker.kt:27-165`):
- Independent of AniList at the API level (PKCE OAuth, MAL v2 API).
- CLIENT_ID hardcoded `"686b980ff4240fccce7f6a654cea07ce"` (L163).
- BUT it's only reachable from `TrackSyncManager` via `trackerManager.getTracker(track.trackerId)` after `animeRepository.getByAnilistId(anilistId)` — see point 3.

`AniListViewer`, `MalOAuth`, `PkceUtil` — supporting types in the same package.

**What breaks if AniList vanished:** AniList tracker login + sync dies. MAL tracker still works but ONLY for library anime that have a non-null `anilistId` — which, ironically, is itself an AniList concept, so "AniList vanished" implies no `anilistId` was ever written, implies MAL tracker can never be reached.

**Decoupling effort:** Medium. The `Tracker` interface is already pluggable. The real work is in `TrackSyncManager`: replace `extractAnilistId` with a `WatchableId` abstraction. The `TrackerManager` should be `Set<Tracker>` Koin multi-bound.

---

### 2.3 Downloads (`:core:download` + `:feature:download` + `DownloadOrchestrator`)

**Coupling: Tight (identity is AniList everywhere).**

`DownloadAnimeInfo` (`core/download/.../DownloadModels.kt:26-31`):
```kotlin
data class DownloadAnimeInfo(
    val anilistId: Int,         // PRIMARY KEY for folder structure
    val title: String,
    val coverUrl: String? = null,
    val coverColor: Int? = null,
)
```
The KDoc is explicit (L8): *"ANIKUTA is AniList-first (ADR-010), so downloads are keyed by `anilistId`."*

`DownloadEpisodeInfo` (L68-74) keys on `episodeUrl` (string) — provider-agnostic at the episode level.

`DownloadTask.key` (`DownloadTask.kt:41`): `"${request.anime.anilistId}:${request.episode.episodeUrl}"` — **this composite key is the dedup key, the offline-playback lookup key, and the UI's per-episode state map key.** There is no `sourceId:url` variant.

`DownloadManager` interface (`DownloadManager.kt`):
- L70: `suspend fun deleteAnimeDownloads(anilistId: Int)` — takes `anilistId`.
- L97: `suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean`.
- L103: `suspend fun getDownloadedVideoUri(anilistId: Int, episodeUrl: String): String?`.
- L109: `suspend fun getDownloadedSubtitleUris(anilistId: Int, episodeUrl: String): List<String>`.
- L112: `suspend fun getDownloadedEpisodes(anilistId: Int): List<DownloadedEpisode>`.
- L121: `val episodeDownloadStates: Flow<Map<String, DownloadTask>>` — keyed by `"$anilistId:$episodeUrl"`.

**Gates (the hard AniList gates):**
1. `DownloadOrchestrator.enqueueDownload(anime: DownloadAnimeInfo, episode, source)` (`app/.../download/DownloadOrchestrator.kt:61-65`) — the caller must construct a `DownloadAnimeInfo` with a non-null `anilistId: Int` (it's a non-null field on the data class). There is NO code path to enqueue a download for an unlinked extension anime.
2. The folder structure: `Anime Title [anilistId]/Episode NNN/...` (per `:core:download` README; `DownloadAnimeInfo.title` + `anilistId` drive the folder name).

**What breaks if AniList vanished:** The entire download system fails for unlinked extension anime. Even if you somehow called `enqueueDownload`, you'd have to fabricate an `anilistId`. The only way to download an unlinked extension anime today is to first link it to AniList.

**Decoupling effort:** Large. Need to make `anilistId: Int?` nullable, change the folder structure to `Anime Title [sourceId:url-hash]` for unlinked, change `DownloadTask.key` to a stable composite that works for both, migrate the persisted `DownloadStore` JSON (which currently has `anilistId: Int` baked into each task). See the separate Downloads deep-dive agent for the full picture.

---

### 2.4 Watch history (`:core:player` `WatchProgressStore` + `:data:history` + `:feature:history`)

**Coupling: Tight (keyspace is `anilistId:episodeUrl`).**

`WatchProgressStore` (`core/player/.../WatchProgressStore.kt`):
- L46-54: `progressPref: Preference<Map<String, Progress>>` — the map key IS the identity.
- L64: `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"`.
- L74-97: `save(anilistId: Int, episodeUrl: String, …)` — non-null `Int anilistId` required.
- L100: `get(anilistId: Int, episodeUrl: String): Progress?`.
- L105: `clear(anilistId: Int, episodeUrl: String)`.
- L112: `clearAnime(anilistId: Int)` — clears by `"$anilistId:"` prefix.
- L131-144: `Progress` data class — no `anilistId` field (the ID is in the key, not the value).

The KDoc is explicit (L31-33): *"Our app is AniList-first, and `animes.source` + `url` (NOT NULL) aren't available until AniyomiSourceBridge resolves. Until that gap is closed, WatchProgressStore remains the source of truth for AniList-keyed progress."*

`HistoryViewModel` (`feature/history/.../HistoryViewModel.kt`):
- L72-95: collects `watchProgressStore.changes` directly. **Does NOT use `HistoryRepository`** (the SQLDelight `animehistory` table) — the KDoc (L18-23) is explicit: *"We do NOT use `HistoryRepository` (the SQLDelight-backed `animehistory` table) — per the project's current architecture, `WatchProgressStore` is the source of truth."*
- L78-81: parses each key into `(anilistId, episodeUrl)`.
- L133-140: `parseKey(key)` splits on the FIRST colon; non-integer anilistId yields `0` (the row still renders but its tap won't navigate).

`HistoryRepositoryImpl` (`data/history/.../HistoryRepositoryImpl.kt`) — exists, is wired, but is **NOT used by `HistoryViewModel`**. The SQLDelight `animehistory` table is keyed by `anime_id + episode_id` (DB row IDs, not AniList IDs) — but it's dormant.

`TrackSyncManager` reads the same `WatchProgressStore.changes` flow and parses `anilistId` the same way (see §2.2).

**What breaks if AniList vanished:** Watch progress for unlinked extension anime cannot be saved at all (the `save()` signature rejects `anilistId=null`). Continue-watching, Library sort by `LAST_WATCHED`, History page, auto tracker sync — all break for unlinked extension anime.

**Decoupling effort:** Large. Need to change the key format to support both `anilistId:episodeUrl` and `ext:sourceId:url:episodeUrl` (or migrate to a structured `ProgressKey` data class). This is a persisted-schema migration (existing JSON keys are stringly-typed). Backup/restore also keys on this (see §2.13).

---

### 2.5 Categories (`:data:anime` `CategoryRepository`)

**Coupling: None (direct) — moderate (transitive).**

`anime_category.sq` (`core/database/.../anime_category.sq:1-45`):
- L3-10: junction table with `anime_id`, `category_id`, `category_order`. **No `anilist_id` column.**
- FK to `animes(_id)` (the local DB row ID), not to AniList.

`CategoryRepositoryImpl` (`data/anime/.../CategoryRepositoryImpl.kt:29-197`): all methods use `animeId: Long` (the local DB row ID). No AniList references.

`CategoryBackupProvider` (`core/backup/.../CategoryBackupProvider.kt`):
- L57-70: builds `anilistToLocalId: Map<Long, Long>` for restore — **because the backup file keys anime by `anilistId`**, the restore must translate. This is AniList-keyed at the backup layer, NOT the category layer.
- L104-105: "For Aniyomi-translated backups: link.animeId is the anilistId (check anilistToLocalId)."
- L202-212: `resolveAnimeId` tries direct `_id` first, then AniList ID lookup.

**What breaks if AniList vanished:** Category membership in the live app works fine. Backup restore is AniList-keyed (see §2.13).

**Decoupling effort:** None at the category layer. The backup layer needs the identity abstraction.

---

### 2.6 Search (`:feature:search`)

**Coupling: Tight (AniList tab) + Loose (Extension tab) + Tight (linking flow).**

`SearchViewModel` (`feature/search/.../SearchViewModel.kt`):
- L6-9: imports `AniListApi`, `AniListAnime`. Direct dependency.
- L32: `enum class SearchSource { ANILIST, EXTENSION }`.
- L41-51: `sealed class SearchResult { AniList(anime: AniListAnime); Extension(source, sAnime, sourceName) }` — dual-source by design.
- L152-158: constructor injects `anilistApi: AniListApi`.
- L176-187: `init` defaults to `SearchSource.ANILIST`, calls `loadAniListDefault()`.
- L196-212: `onSourceChange` swaps the source — UI is dual-source aware.
- L343-355: `scheduleSearch` branches on source.
- L358-402: `runAniListSearch` — three sub-modes:
  - blank query + no filters → `anilistApi.fetchPopular(perPage = PAGE_SIZE)` (L363)
  - filters active → `anilistApi.searchAnimeWithFilters(...)` (L365-375)
  - plain query → `anilistApi.searchAnime(query.trim(), perPage = PAGE_SIZE)` (L377)
- L292-336: `onLoadMore` — pagination, AniList only.
- L408-462: `loadExtensionDefault` + `loadOneExtensionRow` — calls `source.getPopularAnime(1)` / `getLatestUpdates(1)` directly on the `AnimeCatalogueSource`. **No AniList dependency here.**
- L464-501: `runExtensionSearch` — calls `sourceMatcher.searchOneSource(sourceId, query)`. **No AniList dependency.**

`FilterSheet.kt` (UI): the filter UI is 100% AniList filter enums (`MediaSeason`, `MediaFormat`, `MediaStatus`, sort enums like `POPULARITY_DESC`). No extension-filter support.

**Extension→AniList linking flow** (`feature/search/.../ExtensionLinkingViewModel.kt`):
- L6-8: imports `AniListApi`, `AniListAnime`.
- L83-88: constructor takes `anilistApi: AniListApi` + `linkStore: ExtensionLinkStore`.
- L103-138: `attemptLink` — first checks `linkStore.getAniListId(source.id, sAnime.url)` (cache hit), else `anilistApi.searchAnime(sAnime.title, perPage = 10)` → auto-link first result OR emit `NeedsManualLink` state.
- L141-158: `manualSearch(query)` — `anilistApi.searchAnime(q, perPage = 10)`.
- L161-165: `selectManual(anime: AniListAnime)` — `linkStore.link(source.id, sAnime.url, anime.id)`.
- L168-170: `goWithoutLinking()` — emit `GoWithoutLinking(source, sAnime)` state (the AniList-free path; opens the extension-only details page).

`SourceLinkStore` (`data/extension/.../SourceLinkStore.kt`):
- L19-20: *"Key: anilistId (Int)"* — explicit.
- L34-43: persisted as `Map<String, SourceLink>` keyed by `anilistId.toString()`.
- L45, 48, 55: `getLink(anilistId: Int)`, `saveLink(anilistId: Int, sourceId, animeUrl, animeTitle)`, `removeLink(anilistId: Int)`.

`ExtensionLinkStore` (`data/extension/.../ExtensionLinkStore.kt`):
- L11-13: bridges extension anime (`"$sourceId:$animeUrl"`) → AniList ID (`Int`).
- L62: `key(sourceId: Long, animeUrl: String) = "$sourceId:$animeUrl"`.
- L68, 84-90, 96: `getAniListId(sourceId, animeUrl): Int?`, `getPreferredSourceForAnilist(anilistId): Long?`, `link(sourceId, animeUrl, anilistId)`.
- L113: persistence key `pref_extension_anilist_links`.

**What breaks if AniList vanished:** AniList search tab dies entirely. Extension tab survives. Tapping an extension result emits `GoWithoutLinking` (the user explicitly opts out of linking) — extension-only details page opens.

**Decoupling effort:** Medium. Need a `SearchProvider` interface with `AniListSearchProvider` + `ExtensionSearchProvider` implementations; the VM selects via a registry, not a hardcoded enum. The linking flow can stay (AniList linking is a real feature) but should be optional, not the default path.

---

### 2.7 Details page (`:feature:anime-details` + ADR-039 `AnimeDetailsProvider`)

**Coupling: Tight (AniList mode) + Moderate (Extension mode, linked) + None (Extension mode, unlinked).**

The architecture is **already pluggable** via `AnimeDetailsProvider` + `AnimeDetailsProviderRegistry` (ADR-039).

`AnimeDetailsProvider` interface (`core/common/.../details/AnimeDetailsProvider.kt:37-68`):
```kotlin
interface AnimeDetailsProvider {
    val dataSource: DataSource
    suspend fun load(request: DetailsRequest, forceRefresh: Boolean = false): DetailsResult?
    suspend fun loadEpisodes(request: DetailsRequest): List<Episode>?
}
```

`DetailsRequest` (`core/common/.../details/DetailsRequest.kt:14-41`): sealed type with `ByAniListId(anilistId: Int)` and `ByExtension(sourceId, animeUrl, animeTitle, anilistId: Int? = null)`.

`DataSource` (`core/common/.../details/DataSource.kt:16-22`): `enum class DataSource { ANILIST, EXTENSION }`.

`AnimeDetailsProviderRegistry` (`core/common/.../details/AnimeDetailsProviderRegistry.kt:18-31`): Koin multi-binding `List<AnimeDetailsProvider>`; `forSource(dataSource)` returns the matching provider. **Adding a third provider (Kitsu, etc.) = one new class + one Koin line.**

**`AniListDetailsProvider`** (`data/anime/.../details/AniListDetailsProvider.kt:49-287`):
- L59: `override val dataSource: DataSource = DataSource.ANILIST`.
- L61-71: `load()` — `ByAniListId` → `loadByAniListId(anilistId)`; `ByExtension` → tries `request.anilistId` or `extensionLinkStore.getAniListId(sourceId, animeUrl)` → if neither, returns `null` (the extension provider handles unlinked).
- L73-87: `loadByAniListId` calls `anilistApi.fetchById(anilistId)` (Stage 1), then `loadEpisodes(anilistId, anilistAnime.displayTitle)` (Stages 2-3).
- L132-198: `loadEpisodes` — DB-first short-circuit → `sourceLinkStore.getLink(anilistId)` → fresh `sourceMatcher.matchAll(title)`.
- L227-278: `saveEpisodesToDb` — creates the `Anime` DB row with `anilistId = anilistId` (L258) + `url = "anilist:$anilistId"` (L238).

**`ExtensionDetailsProvider`** (`data/extension/.../details/ExtensionDetailsProvider.kt:63-359`):
- L72: `override val dataSource: DataSource = DataSource.EXTENSION`.
- L80-100: `load()` — `ByExtension` → `loadByExtension(sourceId, animeUrl, animeTitle, anilistId)`; `ByAniListId` → reverse-lookup via `sourceLinkStore.getLink(anilistId)` → `loadByExtension`.
- L102-184: `loadByExtension` — DB-first short-circuit (L114-132) → `source.getAnimeDetails(sAnime)` enrichment (Stage A) → Palette cover color (Stage B) → `SAnime.toUnifiedAnime` (Stage C) → **optional AniList merge** (Stage D, L169-177) → fetch + persist episodes (Stage E).
- L169-177: `val effectiveAnilistId = anilistId ?: extensionLinkStore.getAniListId(source.id, animeUrl); if (effectiveAnilistId != null) { anilistApi.fetchById(effectiveAnilistId)?.let { unified = unified.mergeAniListMetadata(it.toUnifiedAnime()) } }`. **If `effectiveAnilistId == null`, the AniList merge is skipped — this is the AniList-free path.**
- L273-274 (KDoc): *"For linked anime (anilistId != null): keyed by anilistId. For unlinked anime (anilistId == null): keyed by `sourceId + url`."*

`AnimeDetailViewModel` (`feature/anime-details/.../AnimeDetailViewModel.kt:82-737`):
- L82-95: constructor — `registry: AnimeDetailsProviderRegistry`, `api: AniListApi` (used for the metadata-enrichment request + fallback in `fetchEpisodeMetadata`).
- L184-220: `loadInternal` — calls `registry.forSource(_currentDataSource.value).load(activeRequest, forceRefresh)`. The VM never calls `AniListApi` directly for the main load.
- L229-241: `switchDataSource(target: DataSource)` — the source-switcher menu. Persists per-anime preference via `viewPreferenceStore`.
- L547-578: `requestForDataSource` — rebuilds the request when switching; relies on `extensionLinkStore.getAniListId` + `sourceLinkStore.getLink` to translate between identity forms.
- L626-647: `fetchEpisodeMetadata` — **skipped for unlinked extension anime**: `val anilistId = anime.anilistId ?: run { Log.i(TAG, "Skipping episode metadata — no anilistId (unlinked extension anime)"); return }`.
- L649-658: `findLibraryAnime` — branches on `anilistId != null` (uses `getByAnilistId`) vs `sourceId != null` (uses `getBySourceAndUrl`).

**What happens for an anime with NO anilistId:**
- The `ExtensionDetailsProvider.loadByExtension` path is taken.
- AniList merge is skipped.
- `fetchEpisodeMetadata` is skipped (returns early).
- Library keying uses `sourceId + url` instead of `anilistId`.
- The user can still save to library, watch, and (mostly) download — except `DownloadOrchestrator.enqueueDownload` requires `DownloadAnimeInfo(anilistId: Int, …)`, so **downloads are blocked** (see §2.3).
- Tracker sync is blocked (see §2.2 — `TrackSyncManager.extractAnilistId` returns -1).

**What breaks if AniList vanished:** AniList-mode details page dies (the `AniListDetailsProvider.load` returns null for `ByAniListId`). Extension-mode details page survives for unlinked anime. For linked anime, the metadata enrichment (score, format, season, studios, next-airing, AniList cover color) is lost — but the extension's own `getAnimeDetails` data + episodes survive.

**Decoupling effort:** Medium. The registry is already pluggable. The work is: (1) make `AniListDetailsProvider` optional (gracefully degrade when AniList is unreachable), (2) make `DownloadOrchestrator` accept unlinked anime, (3) make `TrackSyncManager` work for unlinked anime.

---

### 2.8 Browse / Home (`:feature:browse`)

**Coupling: Tight.**

`BrowseScreen.kt` (`feature/browse/.../BrowseScreen.kt`):
- L41-44: imports `AniListApi`, `AniListAnime`, `coverUrl`, `displayTitle`.
- L62-65: `fun BrowseScreen(api: AniListApi, onOpenAnime: (Int) -> Unit)` — direct `AniListApi` injection at the Composable level (not via a ViewModel).
- L70: `api.getCachedTrending()` for instant display.
- L85-98: `manualRefresh` — `api.fetchTrending(perPage = 30)`.
- L108-125: `LaunchedEffect(Unit)` — `api.fetchTrending(perPage = 30)`. The 24h local cache lives inside `AniListApi` (via `LocalAniListCache`).

`LocalAniListCache` (`core/anilist/.../LocalAniListCache.kt`):
- L24-26: `class LocalAniListCache(private val store: PreferenceStore)`.
- L48-49: `dailyRefreshMs = 24 * 60 * 60 * 1000L` — 24h TTL.
- L57-61: `isTrendingStale()` / `isPopularStale()` — 24h check.
- L89-90: detail-cache TTL = 24h.
- L92-98: `getCachedDetail(id: Int): AniListAnime?` — keyed by AniList ID.
- L100-107: `saveDetail(anime: AniListAnime)` — saves under `anime.id`.

**What breaks if AniList vanished:** Home page is empty. The 24h local cache would show stale data for one day, then nothing. No extension-popular fallback in `BrowseScreen` (the extension Popular/Latest feeds exist in `SearchViewModel.loadExtensionDefault` but are NOT surfaced on the home tab).

**Decoupling effort:** Medium. Need a `HomeFeedProvider` interface that returns sections; `AniListTrendingHomeFeedProvider` + `ExtensionPopularHomeFeedProvider` implementations. `BrowseScreen` should depend on a `HomeFeedViewModel`, not `AniListApi` directly.

---

### 2.9 Extensions (`:data:extension`)

**Coupling: Loose.**

Extensions themselves are AniList-free:
- `AnimeExtensionManager` (`data/extension/.../AnimeExtensionManager.kt:48-100`) — loads APKs, manages trust, registers install receiver. No AniList references in the file (verified).
- `AnimeExtensionLoader`, `AnimeExtensionInstaller`, `AnimeExtensionApi`, `AnimeExtension` model, `TrustExtension`, `HashUtil`, `ChildFirstPathClassLoader` — all extension-system infrastructure, no AniList.
- Extensions interact with the app via the Aniyomi `AnimeSource` / `AnimeCatalogueSource` contract (in `:core:source-api`). They return `SAnime` / `SEpisode` objects, NOT `AniListAnime`.

The AniList coupling is in the **bridging layer**:
- `SourceMatcher` (`data/extension/.../matcher/SourceMatcher.kt:46-384`) — searches extensions by title. **No AniList dependency** — uses `source.getSearchAnime(1, query, AnimeFilterList(emptyList()))`. The `matchAll` results are `SourceMatch(source, sAnime, score)` — pure extension types.
- `ExtensionLinkStore` (`data/extension/.../cache/ExtensionLinkStore.kt`) — bridges `"$sourceId:$animeUrl"` → `anilistId: Int`. **AniList coupling is the VALUE, not the key.**
- `SourceLinkStore` (`data/extension/.../cache/SourceLinkStore.kt`) — bridges `anilistId: Int` → `SourceLink(sourceId, animeUrl, animeTitle)`. **AniList coupling is the KEY.**
- `DetailsViewPreferenceStore` (`data/extension/.../cache/DetailsViewPreferenceStore.kt`) — keys by `anilistId.toString()` for linked anime, or `"ext:$sourceId:$url"` for unlinked (L16-17, L51-58, L64-69). **Hybrid keying already exists here** — this is the model to copy elsewhere.
- `ExtensionDetailsProvider` — see §2.7.
- `SAnimeMapper.kt` (`data/extension/.../details/SAnimeMapper.kt`) — `SAnime.toUnifiedAnime(sourceId, sourceName, anilistId, coverColorHex)` and `UnifiedAnime.mergeAniListMetadata(anilistMerge)`. The mapper itself is fine; the merge function only fires when `anilistId != null`.

**What breaks if AniList vanished:** Extensions themselves keep working. The link stores become orphaned (no AniList IDs to bridge to). `SourceMatcher` continues to work. The extension-only details page (unlinked mode) continues to work. Downloads + tracker sync + watch progress + episode metadata all break for unlinked anime (because of downstream systems requiring `anilistId`).

**Decoupling effort:** Small at the extension layer. The link stores would need to be deprecated or repurposed.

---

### 2.10 Preferences (`:core:preferences`)

**Coupling (this module): None.**

`:core:preferences` contains `PreferenceStore`, `AndroidPreferenceStore`, `Preference`, `AndroidPreference`, `ThemePreferences`, `PreferenceModule`. None of these reference `anilistId` or AniList-specific state.

The only mention of "AniList" in this module is `ThemePreferences.kt:109` (a comment: *"Whether to apply dynamic cover-color theming to the AniList anime details"*).

**AniList-specific prefs live SCATTERED across other modules:**

| Pref key | Where defined | Key type | Purpose |
|---|---|---|---|
| `pref_tracker_anilist_token` | `:core:tracker/anilist/AniListTracker.kt:138` | String | AniList OAuth token |
| `pref_tracker_anilist_username` | `:core:tracker/anilist/AniListTracker.kt:139` | String | AniList username |
| `pref_tracker_anilist_avatar` | `:core:tracker/anilist/AniListTracker.kt:140` | String | AniList avatar URL |
| `pref_tracker_anilist_user_id` | `:core:tracker/anilist/AniListTracker.kt:141` | Int | AniList user ID |
| `pref_tracker_mal_oauth` | `:core:tracker/mal/MalTracker.kt:161` | JSON | MAL OAuth tokens |
| `pref_tracker_mal_username` | `:core:tracker/mal/MalTracker.kt:162` | String | MAL username |
| `local_cache_trending` | `:core:anilist/LocalAniListCache.kt:31` | JSON list | Trending cache |
| `local_cache_popular` | `:core:anilist/LocalAniListCache.kt:38` | JSON list | Popular cache |
| `local_cache_trending_ts` | `:core:anilist/LocalAniListCache.kt:45` | Long | Trending cache timestamp |
| `local_cache_popular_ts` | `:core:anilist/LocalAniListCache.kt:46` | Long | Popular cache timestamp |
| `local_cache_details` | `:core:anilist/LocalAniListCache.kt:75` | `Map<Int anilistId, String JSON>` | Per-id detail cache |
| `local_cache_details_ts` | `:core:anilist/LocalAniListCache.kt:82` | `Map<Int anilistId, Long>` | Per-id detail timestamps |
| `pref_watch_progress_map` | `:core:player/WatchProgressStore.kt:46-54` | `Map<String "$anilistId:$epUrl", Progress>` | Watch progress |
| `pref_download_tasks_v1` | `:core:download/DownloadStore.kt:36-48,73` | `List<DownloadTask>` (each task embeds `DownloadAnimeInfo.anilistId`) | Download queue |
| `pref_source_links` | `:data:extension/cache/SourceLinkStore.kt:34,67` | `Map<String anilistId, SourceLink>` | AniList→extension links |
| `pref_extension_anilist_links` | `:data:extension/cache/ExtensionLinkStore.kt:40,113` | `Map<String "$sourceId:$url", Int anilistId>` | Extension→AniList links |
| `pref_details_view_preference` | `:data:extension/cache/DetailsViewPreferenceStore.kt:38,93` | `Map<String anilistId-or-ext-key, String DataSource>` | Per-anime source preference |
| `episode_metadata_cache` | `:core:episode-metadata/repository/EpisodeMetadataCache.kt:31,55-71` | `Map<String anilistId, String JSON>` | Per-anime episode metadata |
| `anikuta_source_prefs` (SharedPreferences, not PreferenceStore) | `:data:anime/details/AniListDetailsProvider.kt:280,285` + `:feature/anime-details/AnimeDetailViewModel.kt:159,731-735` | `Map<String "source_pref_$anilistId", Long sourceId>` | Per-anime extension preference (legacy, parallel to DetailsViewPreferenceStore) |

**What breaks if AniList vanished:** AniList-token prefs become meaningless. The `local_cache_*` prefs become orphaned. The anilistId-keyed prefs (`pref_source_links`, `pref_extension_anilist_links`, `episode_metadata_cache`, `local_cache_details`) become unindexable for new anime. The `pref_watch_progress_map` and `pref_download_tasks_v1` keys become orphaned for any anime that was AniList-keyed.

**Decoupling effort:** Large (key-space migration). The hybrid `DetailsViewPreferenceStore` (which already supports `"ext:$sourceId:$url"` keys) is the model to copy.

---

### 2.11 Backup / restore (`:core:backup` + `:feature:backup`)

**Coupling: Tight — AniList is the SOLE source of truth for cross-format identity.**

#### 2.11.1 ANIKUTA backup format

`AnimeBackup` (`core/backup/.../model/AnimeBackup.kt:18-49`):
- L43: `val anilistId: Long? = null` — nullable, but it's the primary identity field for restore.

`AnimeBackupProviders.kt` (`core/backup/.../provider/AnimeBackupProviders.kt`):
- L17-18: *"Export reads only the favorite rows. Import upserts each anime by AniList ID (falls back to source+url if no AniList ID)"*.
- L74: *"browsed but didn't favorite). On import, it upserts by AniList ID."*
- L120-121: *"1. If `anilistId` is set, look up by AniList ID. If found, update. If not, insert. 2. If no AniList ID, look up by `sourceId + url`."*
- L128-129: `if (anime.anilistId != null) { queries.selectIdByAnilistId(anime.anilistId)... }`.
- L155, 186: `anilistId = anime.anilistId` written to the DB on upsert.

`EpisodeBackupProvider.kt` (`core/backup/.../provider/EpisodeBackupProvider.kt`):
- L41-42: `val key = anime.anilistId?.toString() ?: "${anime.sourceId}:${anime.url}"` — **fallback exists for unlinked anime**.
- L87-90: `val anilistId = key.toLongOrNull(); if (anilistId != null) { queries.selectIdByAnilistId(anilistId)... }`.

`WatchProgressBackupProvider.kt` (`core/backup/.../provider/WatchProgressBackupProvider.kt`):
- L22: KDoc — *"The key format is `"$anilistId:$episodeUrl"` — stable across devices and sessions."*
- L69-72: import parses the key and calls `watchProgressStore.save(anilistId = anilistId, ...)`. **If `anilistId <= 0`, the entry is skipped (L70).**

`CoverImageProvider.kt` (`core/backup/.../provider/CoverImageProvider.kt`):
- L26: KDoc — *"The `BackupEntry.CoverImages` entry records which anilistIds have bundled covers."*
- L42-45: `val anilistId = anime.anilistId; if (anilistId != null && !coverUrl.isNullOrBlank()) { covers[anilistId.toString()] = coverUrl }`.
- L103-104: KDoc — *"`urls`: map of anilistId → cover URL."* No fallback key.

`CategoryBackupProvider.kt` (`core/backup/.../provider/CategoryBackupProvider.kt`):
- L57-70: builds `anilistToLocalId: Map<Long, Long>` lookup table for restore.
- L104-105, L202-212: `resolveAnimeId` tries direct `_id` first, then `anilistId` lookup.

`SourceLinkBackupProvider.kt` (`core/backup/.../provider/SourceLinkBackupProvider.kt`):
- L17-21: KDoc — *"Backs up AniList↔extension source links. `SourceLinkStore`: AniList ID → extension source match. `ExtensionLinkStore`: extension anime (sourceId:animeUrl) → AniList ID."*
- L60-95: restores both stores by anilistId key.

`TrackerBackupProvider.kt` (`core/tracker/.../TrackerBackupProvider.kt:11-39`):
- L11-22: KDoc — documents the prefs that hold AniList OAuth token + MAL OAuth + bindings.
- L32-39: `TrackerBackupData(anilistToken, anilistUsername, anilistUserId, malOAuthJson, malUsername, bindings: List<AnimeTrack>)` — `bindings` reference `anime_id` (local DB ID), not `anilistId` directly, but the anime rows themselves are AniList-keyed.

#### 2.11.2 Aniyomi restore translator (`AniyomiBackupTranslator`)

`core/backup/.../translation/AniyomiBackupTranslator.kt:99-433`:
- L99-101: `class AniyomiBackupTranslator(private val anilistApi: AniListApi)`. **The translator's ONLY dependency is AniListApi.**
- L121-173: `translate(aniyomiBackup)` — for each Aniyomi favorite anime, calls `resolveAnilistId(ani)`.
- L232-269: `resolveAnilistId` — three strategies, ALL require AniList:
  1. L233-242: **AniList tracker binding** — `ani.tracking.firstOrNull { it.syncId == 2 && it.mediaId != 0L }` → `anilistApi.fetchById(anilistId)`.
  2. L244-255: **MAL tracker binding → AniList lookup** — `ani.tracking.firstOrNull { it.syncId == 1 && it.mediaId != 0L }` → `anilistApi.searchByMalId(malId)`.
  3. L257-268: **Title search** — `anilistApi.searchByTitle(title)`.
- L274-406: `buildContainer` — every translated entry is keyed by `res.anilistId.toString()` (L298, L315, L339, L352, L373, L390).
- L408-432: `buildAnimeBackup` — `anilistId = res.anilistId.toLong()`.

`AniyomiRestoreViewModel` (`feature/backup/.../aniyomi/AniyomiRestoreViewModel.kt`):
- L7-8, 51: injects `AniListApi`.
- L95-99: `val trans = AniyomiBackupTranslator(anilistApi); trans.translate(aniyomi)`.
- L258-263: `searchAniList(query)` + `manuallyLink(failed, anime)` — manual linking UI for failed resolutions.

`DOCS/aniyomi-backup-format/05-translation-plan.md` (at `/home/z/my-project/anikuta/DOCS/`, NOT inside the ANIKUTA dir) — the plan document. **AniList ID resolution strategy is the entire document** (Step 1: Resolve AniList IDs; the three strategies are documented as the design). The "Dependencies" section (L46-50) is explicit: *"AniListApi (for title search + MAL→AniList lookup)"*.

**What breaks if AniList vanished:** ANIKUTA-format backups still restore for AniList-keyed anime (the anilistId field is preserved). Aniyomi backups cannot be translated at all — the translator's purpose is AniList ID resolution. Cover images can't be bundled (no anilistId key).

**Decoupling effort:** Large for the Aniyomi translator (its purpose IS AniList ID resolution). Medium for the ANIKUTA format (the schema can't drop `anilistId` without a version bump; could add `sourceId`+`url` as parallel identity keys).

---

### 2.12 Profile / Stats (`:feature:my` + `StatsCalculator`)

**Coupling: Moderate.**

`StatsCalculator` (`core/tracker/.../StatsCalculator.kt:25-178`):
- L25-29: constructor — `watchProgressStore`, `animeRepository`, `trackerManager`.
- L35-41: `observeStats()` — combines `animeRepository.observeFavorites()` + `watchProgressStore.changes` → `computeLocalStats(libraryAnime, progressMap)`. **Fully local, no AniList.**
- L44-52: `fetchAniListStats()` — `if (!trackerManager.anilist.isLoggedIn) return null; trackerManager.anilist.fetchUserStats()`. **Optional enrichment.**
- L55-58: `observeAniListUsername()` / `observeAniListAvatar()` — flow from `trackerManager.anilist`.
- L61: `isAniListLinked(): Boolean = trackerManager.anilist.isLoggedIn`.
- L66-148: `computeLocalStats` — works on library + progress map. **L156-160**: `computeBehindAnime` parses `anilistId` from `progressMap.keys` (`substringBefore(':')`) — same AniList keying. L163-167: `anime.anilistId ?: return@mapNotNull null` — **anime without `anilistId` are excluded from "behind anime" calc**.

`ProfileViewModel` (`feature/my/.../ProfileViewModel.kt:24-166`):
- L34-38: `init` — `observeLocalStats()`, `observeAniListState()`, `observePreferences()`.
- L41-50: `observeLocalStats()` — always active.
- L53-72: `observeAniListState()` — collects `trackerManager.anilist.username`; if linked, fetches avatar + stats.
- L94-100: `fetchAniListAvatar()`.
- L103-112: `fetchAniListStats()` — one-shot enrichment.
- L121-138: `refresh()` — re-fetches AniList stats if linked.

`ProfilePreferences` (`feature/my/.../ProfilePreferences.kt`): user-customizable display name, avatar URL, `useTrackerStats` toggle (so the user can choose local vs tracker stats).

`ProfileTrackersMoreEntries.kt` — UI entry point for the trackers settings.

**What breaks if AniList vanished:** Profile still works in local mode. "Behind anime" excludes unlinked extension anime (L163-167). AniList-specific enrichments (format distribution, country distribution, mean score from tracker) are unavailable. The user can still see total anime, total episodes watched, total watch time, local genre distribution, local score distribution (from `anime.score` which is itself AniList-derived — see `animes.sq:31`).

**Decoupling effort:** Small. The architecture already has a local-mode fallback. The work is making `computeBehindAnime` work for unlinked extension anime (parse a `WatchableId` instead of `substringBefore(':')`).

---

### 2.13 Updates / Schedule (`:feature:updates` + `:core:update-checker`)

**Coupling: Tight (Schedule) + Loose (Updates).**

#### Schedule tab

`UpdatesViewModel.fetchSchedule()` (`feature/updates/.../UpdatesViewModel.kt:131-213`):
- L135: `val library = animeRepository.observeFavorites().first()`.
- L136: `val ids = library.mapNotNull { it.anilistId }` — **anime with `anilistId=null` are silently skipped**.
- L137-141: log includes the skip count.
- L142-145: empty-id short-circuit.
- L149-150: `for (chunk in ids.chunked(50)) { anilistApi.fetchAiringSchedule(chunk) }` — AniList's `id_in` practical max is 50.
- L156-194: flattens `nextAiringEpisode` + `upcomingEpisodes` into `ScheduleEntry` list, keyed by `anilistId`.
- L198: `sortedBy { it.airingAtMillis }`.

`AniListApi.fetchAiringSchedule(ids: List<Int>)` (`core/anilist/.../AniListApi.kt:354-398`):
- 5-min in-memory cache keyed by sorted-id tuple.
- Uses the `AIRING_SCHEDULE_QUERY` (L713-725) — `Page(page: 1, perPage: 50) { media(id_in: $ids, type: ANIME) { id title coverImage nextAiringEpisode airingSchedule(notYetAired: true) { nodes { id episode airingAt } } } }`.

#### Updates tab (new-episode detection)

`UpdateChecker` (`core/update-checker/.../UpdateChecker.kt:86-386`):
- L86-91: constructor — `animeRepository`, `anilistApi: AniListApi`, `episodeFetchGateway: EpisodeFetchGateway`, `preferences`.
- L184-252: `checkForUpdates()` — iterates library, calls `checkAnimeInternal(anime, now)`.
- L307-381: `checkAnimeInternal`:
  - L312: `episodeFetchGateway.fetchEpisodes(anime.title)` — uses `SourceMatcher.matchAll(title)` under the hood (`EpisodeFetchGatewayImpl.kt:37-56`). **No AniList dependency for the core check.**
  - L333-339: diff against `preferences.lastKnownEpisodeCount(anime.id)` — local.
  - L363-369: **AniList cross-reference** — `anime.anilistId?.let { aid -> try { anilistApi.fetchById(aid) } catch (...) {} }`. KDoc at L359-362: *"AniList cross-reference (best-effort, non-blocking on failure). We don't currently surface this on the Updates list row, but it's fetched so the Updates page can show a 'next airing' badge later."*

`EpisodeFetchGatewayImpl` (`data/extension/.../updatechecker/EpisodeFetchGatewayImpl.kt:37-56`):
- L46: `sourceMatcher.matchAll(animeTitle)` — extension-based, no AniList.

**What breaks if AniList vanished:** Schedule tab is empty. Updates tab still works (new-episode detection via extensions). The AniList cross-ref is non-fatal — silently fails.

**Decoupling effort:** Medium for Schedule (need an `AiringScheduleProvider` interface; MAL/Jikan could be a fallback). Small for Updates (already extension-driven; just remove the cross-ref call or make it lazy/optional).

---

### 2.14 Episode metadata (`:core:episode-metadata`)

**Coupling: Tight (keyspace) + Moderate (one of 3 sources).**

`EpisodeMetadataRepository` (`core/episode-metadata/.../repository/EpisodeMetadataRepository.kt:44-170`):
- L47: constructor — `registry: EpisodeMetadataSourceRegistry`, `preferences`, `localCache: EpisodeMetadataCache?`.
- L49: in-memory cache `Map<Int, Map<Int, EpisodeMetadata>>` — keyed by `animeId`.
- L59-160: `fetchAll(request: EpisodeMetadataRequest)`:
  - L62-65: enabled-check.
  - L68-71: in-memory cache hit by `request.animeId`.
  - L74-82: local persistent cache hit (`localCache.get(request.animeId)`).
  - L84-103: parallel fetch from all registered sources.
  - L106-151: per-field merge (first non-null wins, in source registration order).

`EpisodeMetadataRequest` (in `model/EpisodeMetadata.kt`):
- Fields: `animeId: Int`, `animeTitle: String`, `episodeNumber: Int`, `malId: Int?`, `bannerImage: String?`, `episodeCount: Int`.
- **`animeId` is documented as the AniList ID** (used by `AniListStreamingSource`).

`EpisodeMetadataCache` (`core/episode-metadata/.../repository/EpisodeMetadataCache.kt:21-90`):
- L31-48: persisted as `Map<String, String>` keyed by `animeId.toString()`.
- L55: `get(animeId: Int): Map<Int, EpisodeMetadata>?` — `prefs.get()[animeId.toString()]`.
- L67: `save(animeId: Int, metadata: Map<Int, EpisodeMetadata>)` — `map[animeId.toString()] = json.encodeToString(...)`.
- KDoc L52-54: *"@param animeId the AniList anime ID"*.

`EpisodeMetadataSource` interface (`core/episode-metadata/.../source/EpisodeMetadataSource.kt:23-55`):
- Pluggable contract. `id`, `name`, `supports(request)`, `fetchAll(request): Map<Int, EpisodeMetadata>`, `providedFields: Set<EpisodeMetadataField>`.

Sources (3 registered):

1. **`JikanMalSource`** (`source/jikan/JikanMalSource.kt:28-129`):
   - L37: `supports(request) = request.malId != null && request.malId > 0` — **MAL-keyed, not AniList**.
   - Fetches from `https://api.jikan.moe/v4/anime/$malId/episodes?page=$page`.
   - Provides: `TITLE`, `AIR_DATE`.

2. **`AniListStreamingSource`** (`source/anilist/AniListStreamingSource.kt:32-89`):
   - L41: `supports(request) = request.animeId > 0` — **AniList-keyed**.
   - L47: GraphQL query — `Media(id: ${request.animeId}, type: ANIME) { streamingEpisodes { title thumbnail } }`.
   - URL: `https://graphql.anilist.co` (L51) — **direct AniList call, NOT via `:core:anilist`'s `AniListApi`** (third AniList client in the codebase).
   - Provides: `TITLE`, `THUMBNAIL`.

3. **`AnikageCcSource`** (`source/anikage/AnikageCcSource.kt`):
   - Anikage.cc API (community episode metadata).
   - Uses anime title (not AniList ID).

`EpisodeMetadataSourceRegistry` (`source/EpisodeMetadataSourceRegistry.kt`) — Koin multi-binding of all sources.

`EpisodeMetadataPreferences` (`EpisodeMetadataPreferences.kt`) — per-field enable toggles (`fetchTitles`, `fetchSummaries`, `fetchThumbnails`, `fetchAirDates`).

**How is metadata keyed?** The CACHE is keyed by `animeId: Int` (the AniList ID per the KDoc). The MERGE is per-episode-number (`Map<Int, EpisodeMetadata>` keyed by episode number). So an anime's metadata is `(anilistId, episodeNumber) → EpisodeMetadata`. **An unlinked extension anime has no `animeId` — the request would have `animeId = 0`** (per `AnimeDetailViewModel.fetchEpisodeMetadata` L633-640 which builds `EpisodeMetadataRequest(animeId = anilistId, …)` — and the VM **early-returns if `anilistId == null`** at L629-632, so unlinked anime never request episode metadata).

**What breaks if AniList vanished:** AniList streaming source dies. The cache key space (anilistId) becomes orphaned for new anime. Jikan + Anikage survive but only fire if `malId != null` (Jikan) or by title (Anikage). Without anilistId, the request would need a different `animeId` field.

**Decoupling effort:** Medium. Need an `animeId` abstraction that's either `anilistId` or `sourceId:url-hash`. The source registry is already pluggable.

---

## 3. "AniList is the SOLE source of truth for X" — no fallback

These are things that have **NO fallback if AniList vanished tomorrow**:

1. **AniList-keyed watch progress persistence** (`WatchProgressStore.kt:64,74`). The `save()` signature REQUIRES `Int anilistId` — there is no `save(sourceId, url, …)` variant. Unlinked extension anime cannot save progress at all.
2. **AniList-keyed download dedup + offline-lookup** (`DownloadTask.kt:41`, `DownloadManager.kt:70,97,103,109,112`). `DownloadAnimeInfo.anilistId` is non-null. Unlinked extension anime cannot be downloaded.
3. **AniList airing schedule** (`AniListApi.kt:354` `fetchAiringSchedule`). The Schedule tab has no other source.
4. **Browse home feed** (`BrowseScreen.kt:62-65,113`). Only AniList trending is shown; no extension-popular fallback on the home tab.
5. **AniList search** (`SearchViewModel.kt:152-158,176-187`). The default tab; no alternative metadata-search backend.
6. **AniList tracker login + sync** (`AniListTracker.kt:25-147`). Token storage + `fetchUserAnimeList` + `fetchUserStats` + `updateProgress` all go to AniList.
7. **Aniyomi backup translation** (`AniyomiBackupTranslator.kt:99-101,232-269`). The translator's only dependency is `AniListApi`. Without AniList, Aniyomi backups cannot be restored.
8. **Cover-color hex from AniList** (`AniListAnimeMapper.kt:46` `coverColorHex = coverColorHex`). Extension anime get Palette-extracted colors as a fallback (`ExtensionDetailsProvider.kt:159,247-268`), but AniList-mode anime have NO fallback — the `PaletteExtraction.extractCoverColor(url)` skeleton returns `null` (`core/designsystem/.../PaletteExtraction.kt:78`).
9. **Episode metadata cache key** (`EpisodeMetadataCache.kt:55,67,52-54`). The cache is keyed by `anilistId`; there is no `sourceId:url` variant. Unlinked extension anime have no episode metadata persistence.
10. **AniList metadata enrichment for linked extension anime** (`ExtensionDetailsProvider.kt:169-177`). Score, format, season, studios, next-airing — these come from AniList ONLY. Extensions don't provide them.

---

## 4. "AniList is OPTIONAL for X" — already works without AniList

These already work for unlinked extension anime or with AniList unreachable:

1. **Extension-only details page (unlinked mode)** — `ExtensionDetailsProvider.loadByExtension` with `effectiveAnilistId == null` (`ExtensionDetailsProvider.kt:170-177`). Calls `source.getAnimeDetails` for metadata + `source.getEpisodeList` for episodes. The user explicitly chooses "go without linking" in `ExtensionLinkingViewModel.goWithoutLinking()` (`feature/search/.../ExtensionLinkingViewModel.kt:168-170`).
2. **Library grid rendering** — `LibraryViewModel.init` collects `animeRepository.observeFavorites()` (`LibraryViewModel.kt:58`). The `animes` table allows `anilist_id IS NULL` (`animes.sq:29`). Anime with `anilistId=null` render normally.
3. **Category membership** — `anime_category.sq` keys on `anime_id` (local DB row), not AniList ID. `CategoryRepositoryImpl` is AniList-agnostic.
4. **Extension search** — `SearchViewModel.runExtensionSearch` + `loadExtensionDefault` (`SearchViewModel.kt:408-501`) call `source.getSearchAnime` / `getPopularAnime` / `getLatestUpdates` directly. No AniList.
5. **Source matching** — `SourceMatcher.matchAll` / `match` / `searchOneSource` (`data/extension/.../SourceMatcher.kt:186-205,233-284,149-177`) use `source.getSearchAnime`. No AniList.
6. **Extension install / trust / loader** — `AnimeExtensionManager`, `AnimeExtensionLoader`, `TrustExtension`, `AnimeExtensionInstaller` have no AniList references.
7. **New-episode detection (Updates tab)** — `UpdateChecker.checkAnimeInternal` (`UpdateChecker.kt:307-381`) uses `episodeFetchGateway.fetchEpisodes(title)` which delegates to `SourceMatcher.matchAll` (`EpisodeFetchGatewayImpl.kt:46`). The AniList cross-ref at L363-369 is non-fatal.
8. **Local profile stats** — `StatsCalculator.observeStats()` + `computeLocalStats` (`StatsCalculator.kt:35-148`) work on library + watch-progress. No AniList.
9. **MAL tracker login + API** — `MalTracker` (`MalTracker.kt:27-165`) is independent at the API level (PKCE OAuth, MAL v2 API). The coupling is only in `TrackSyncManager`'s identity resolution.
10. **ANIKUTA-format backup/restore** — `BackupManager` + 10 backup providers work for AniList-keyed AND source-keyed anime (`EpisodeBackupProvider.kt:42` has the fallback). The AniList coupling is at the identity layer, not the format layer.
11. **Cover-color extraction for extension anime** — `ExtensionDetailsProvider.extractCoverColorHex` (`ExtensionDetailsProvider.kt:247-268`) uses OkHttp + `PaletteExtraction.extractFromBitmap`. No AniList.
12. **Jikan episode metadata** — `JikanMalSource.supports(request) = request.malId != null` (`JikanMalSource.kt:37`). MAL-keyed, not AniList.
13. **Anikage.cc episode metadata** — uses anime title (`AnikageCcSource.kt`).
14. **Downloads FOR LINKED anime** — works fine, but only because `anilistId` is non-null. (Strictly, downloads require AniList, so this is "optional" only in the sense that the *download engine* doesn't talk to AniList, only the identity requirement does.)
15. **Watch progress FOR LINKED anime** — same caveat as #14.

---

## 5. The `AniListRepository` / `AniListApi` public surface (every method signature)

**There is no `AniListRepository` class.** The `:core:anilist` module exposes its functionality directly through `AniListApi`. (Searching for `class AniListRepository` / `interface AniListRepository` returns no matches.)

### 5.1 `AniListApi` (`core/anilist/src/main/java/app/confused/anikuta/core/anilist/api/AniListApi.kt`)

```kotlin
class AniListApi(
    private val client: OkHttpClient = defaultClient(),
    private val localCache: LocalAniListCache? = null,
    private val rateLimiter: AniListRateLimiter? = null,
)
```

**Public methods (every one):**

```kotlin
// List queries (with 24h local persistent cache + in-memory SWR)
suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime>          // L63
suspend fun fetchPopular(page: Int = 1, perPage: Int = 20): List<AniListAnime>            // L80

// Search (NOT cached)
suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 20): List<AniListAnime>  // L95

// Reverse lookups (used by Aniyomi backup translator)
suspend fun searchByMalId(malId: Int): AniListAnime?                                     // L108
suspend fun searchByTitle(title: String): AniListAnime?                                  // L137
suspend fun searchByTitleMultiple(title: String, perPage: Int = 10): List<AniListAnime>  // L167

// Filtered search (Search page FilterSheet)
suspend fun searchAnimeWithFilters(
    query: String?,
    page: Int = 1,
    perPage: Int = 20,
    genres: Set<String> = emptySet(),
    year: Int? = null,
    season: String? = null,
    format: String? = null,
    status: String? = null,
    sort: String = "POPULARITY_DESC",
    minScore: Int = 0,
): List<AniListAnime>                                                                    // L201

// Sync cache read (instant display on app open)
fun getCachedTrending(): List<AniListAnime>?                                              // L296

// Airing schedule (Schedule tab)
suspend fun fetchAiringSchedule(ids: List<Int>): List<AiringScheduleInfo>                // L354

// Single-anime fetch (5-min in-memory + 24h local persistent)
suspend fun fetchById(id: Int): AniListAnime?                                             // L467
```

**Companion constants:**
- `API_URL = "https://graphql.anilist.co"` (L609)
- `AIRING_CACHE_TTL_MS = 5 * 60 * 1000L` (L613)
- `ANIME_FIELDS` (L623-647) — the GraphQL fragment every list/detail query selects.
- `TRENDING_QUERY`, `POPULAR_QUERY`, `SEARCH_QUERY`, `BY_ID_QUERY`, `BY_MAL_ID_QUERY`, `AIRING_SCHEDULE_QUERY` (L649-725).

### 5.2 `LocalAniListCache` (`core/anilist/.../api/LocalAniListCache.kt`)

```kotlin
class LocalAniListCache(private val store: PreferenceStore)

val dailyRefreshMs: Long  // = 24h

// List cache (trending/popular)
fun getCachedTrending(): List<AniListAnime>
fun getCachedPopular(): List<AniListAnime>
fun getTrendingTimestamp(): Long
fun getPopularTimestamp(): Long
fun isTrendingStale(): Boolean
fun isPopularStale(): Boolean
fun saveTrending(list: List<AniListAnime>)
fun savePopular(list: List<AniListAnime>)

// Detail cache (per AniList ID, 24h TTL)
fun getCachedDetail(id: Int): AniListAnime?
fun saveDetail(anime: AniListAnime)
```

### 5.3 `AniListRateLimiter` (`core/anilist/.../api/AniListRateLimiter.kt`)

```kotlin
class AniListRateLimiter

suspend fun acquire()       // blocks until safe to send
fun currentRate(): Int      // requests in last 60s
fun totalRequests(): Int    // lifetime counter
```

### 5.4 `AniListAnimeMapper` (`core/anilist/.../details/AniListAnimeMapper.kt`)

```kotlin
fun AniListAnime.toUnifiedAnime(
    matchedSourceId: Long? = null,
    matchedSourceName: String? = null,
): UnifiedAnime    // L31
```

### 5.5 `AniListAnime` model + helpers (`core/anilist/.../model/AniListAnime.kt`)

`@Serializable` types: `AniListAnime`, `AniListTitle`, `AniListCoverImage`, `AniListFuzzyDate`, `AniListStudioConnection`, `AniListStudio`, `AniListAiringSchedule`, `AiringScheduleInfo`.

Extension helpers on `AniListAnime`:
- `val displayTitle: String`
- `val coverUrl: String?`
- `val coverColorHex: String?`
- `val seasonDisplay: String?`
- `val studioName: String?`
- `val startDateDisplay: String?`
- `val nextAiringDisplay: String?`

### 5.6 AniList authenticated client (in `:core:tracker`, separate from `:core:anilist`)

`AniListTrackApi` (`core/tracker/.../anilist/AniListTrackApi.kt:35-305`):
```kotlin
class AniListTrackApi(private val client: OkHttpClient = defaultClient())

suspend fun fetchViewer(token: String): AniListViewer?
suspend fun fetchUserAnimeList(token: String, userId: Int): List<TrackAnimeEntry>
suspend fun fetchUserStats(token: String, userId: Int): TrackerUserStats?
suspend fun updateProgress(token: String, mediaId: Int, episodeNumber: Int, status: TrackStatus)
```

`AniListTracker` (`core/tracker/.../anilist/AniListTracker.kt:25-147`) implements `Tracker`:
```kotlin
class AniListTracker(
    private val preferences: PreferenceStore,
    private val api: AniListTrackApi = AniListTrackApi(),
) : Tracker

override val id: Int = Tracker.ANILIST_ID      // = 2
override val name: String = "AniList"
override val isLoggedIn: Boolean
override val username: Flow<String?>
val avatar: Flow<String?>

override fun getAuthUrl(): String
override suspend fun handleAuthCallback(callbackUrl: String): Boolean
override fun logout()
override suspend fun updateProgress(remoteAnimeId: Int, episodeNumber: Int, status: TrackStatus)
override suspend fun fetchUserAnimeList(): List<TrackAnimeEntry>
override suspend fun fetchUserStats(): TrackerUserStats
```

`AniListViewer` (`core/tracker/.../anilist/AniListViewer.kt`) — `data class AniListViewer(id, name, avatarUrl, bannerUrl, scoreFormat)`.

**Three separate AniList HTTP clients exist in the codebase:**
1. `:core:anilist` `AniListApi` (unauthenticated, browse/search/details/schedule).
2. `:core:tracker/anilist` `AniListTrackApi` (authenticated, tracker ops).
3. `:core:episode-metadata/source/anilist` `AniListStreamingSource` (unauthenticated, streaming episodes only — uses its own OkHttpClient + raw JSON, doesn't go through `AniListApi`).

---

## 6. Cross-cutting observations driving the provider-abstraction-layer proposal

1. **`anilistId: Int` is the de-facto universal primary key.** It's in: `animes.sq.anilist_id`, `WatchProgressStore` keys, `DownloadTask.key`, `EpisodeMetadataCache` keys, `SourceLinkStore` keys, `DetailsViewPreferenceStore` keys (linked variant), `anikuta_source_prefs` SharedPreferences, backup `AnimeBackup.anilistId`, backup `EpisodeBackup` keys, backup `WatchProgressBackup` keys, backup `CoverImageProvider` keys, `TrackSyncManager.extractAnilistId`, `StatsCalculator.computeBehindAnime`, `AnimeDetailViewModel.fetchEpisodeMetadata`.

2. **The hybrid keying model already exists** in `DetailsViewPreferenceStore` (`data/extension/.../cache/DetailsViewPreferenceStore.kt:16-17`): keys are EITHER `anilistId.toString()` OR `"ext:$sourceId:$url"`. This is the pattern to copy.

3. **The provider abstraction already exists** for the details page (`AnimeDetailsProvider` + `AnimeDetailsProviderRegistry`), the tracker system (`Tracker` + `TrackerManager`), the episode metadata system (`EpisodeMetadataSource` + `EpisodeMetadataSourceRegistry`), and the download engine (`DownloadManager` + `DefaultDownloadManager`). What's MISSING is:
   - A `HomeFeedProvider` for `BrowseScreen`.
   - A `SearchProvider` for `SearchViewModel`.
   - An `AiringScheduleProvider` for `UpdatesViewModel.fetchSchedule`.
   - A `WatchableId` value type that's either `AniListId(Int)` or `ExtensionId(sourceId, url)` — replacing the stringly-typed `"$anilistId:$episodeUrl"` keys.
   - An `AnimeMetadataProvider` separating AniList metadata from identity.

4. **Three AniList HTTP clients exist** (see §5.6). Consolidation is overdue — but care is needed: the tracker client is authenticated, the browse client is cached, and the streaming-episodes client is a one-off. A unified `AniListHttpClient` with multiple facades would be cleaner.

5. **AniList OAuth + user identity prefs are in `:core:tracker/anilist/AniListTracker.kt`**, NOT in `:core:preferences`. If a provider-abstraction layer is built, these prefs should move with the `AniListTracker` impl (they're provider-specific).

6. **The Aniyomi backup translator is the hardest coupling to break.** Its entire purpose is resolving AniList IDs from Aniyomi backup data. Without AniList, there is no translator. Decoupling here means: provide an alternative identity-resolution strategy (e.g., title → extension-source match → store under `sourceId:url` identity), which would be a fundamentally different translator.

7. **The `animes` DB schema is already AniList-optional** (`anilist_id INTEGER` nullable, partial unique index). The schema is NOT the blocker. The blocker is the downstream consumers that hardcode `anilistId` in their key spaces.

8. **`animes.sq.url` defaults to `"anilist:$anilistId"` for AniList-mode library entries** (`AniListDetailsProvider.kt:238`). For extension-mode entries, `url` is the source-relative URL. So `url` is NOT a stable cross-provider identity field today.

9. **`Anime.sourceId` is `0L` for AniList-only library entries** (`AniListDetailsProvider.kt:248`). For extension entries, it's the `AnimeCatalogueSource.id`. So `sourceId` is also NOT a stable cross-provider identity field.

10. **The blocker is a `WatchableId` type.** Once introduced, every `"$anilistId:$episodeUrl"` key space (`WatchProgressStore`, `DownloadTask.key`, `EpisodeMetadataCache`, backup watch-progress) can be migrated to `"$watchableId:$episodeUrl"` where `watchableId` serializes to either `"$anilistId"` or `"ext:$sourceId:$url"`. This is the single highest-leverage change.

---

## 7. Recommended next actions (for the provider-abstraction-layer proposal)

1. **Introduce a `WatchableId` value type** in `:core:common`. Sealed: `AniListId(Int)` | `ExtensionId(sourceId: Long, url: String)`. Serializable. Has a stable string serialization (`"$anilistId"` or `"ext:$sourceId:$url"`).
2. **Migrate `WatchProgressStore` keys** to `WatchableId` serialization. Provide a one-time migration that parses existing `"$anilistId:$episodeUrl"` keys into `AniListId(anilistId)` + `episodeUrl`.
3. **Migrate `DownloadAnimeInfo.anilistId: Int`** to `WatchableId` (nullable for unlinked extension anime). Update `DownloadTask.key` and `DownloadManager` method signatures.
4. **Migrate `EpisodeMetadataCache` keys** to `WatchableId`. Update `EpisodeMetadataRequest.animeId` to `WatchableId`.
5. **Add a `HomeFeedProvider` interface** in `:core:common`. `AniListTrendingHomeFeedProvider` + `ExtensionPopularHomeFeedProvider` implementations. `BrowseScreen` consumes via a `HomeFeedViewModel`.
6. **Add a `SearchProvider` interface** in `:core:common`. Refactor `SearchViewModel` to use a registry.
7. **Add an `AiringScheduleProvider` interface**. `AniListAiringScheduleProvider` (current) + future `JikanAiringScheduleProvider` (fallback).
8. **Refactor `TrackSyncManager`** to use `WatchableId` instead of `extractAnilistId` from the progress key. Then MAL tracker can sync unlinked extension anime.
9. **Make `TrackerManager` a Koin multi-bound `Set<Tracker>`** instead of hardcoded `listOf(anilistTracker, malTracker)`. Add a third tracker (Shikimori/Bangumi/Simkl per ADR-019) as proof of extensibility.
10. **Consolidate the three AniList HTTP clients** into a single `AniListHttpClient` with separate authenticated/unauthenticated facades.
11. **Document the `WatchableId` migration in a new ADR** (call it ADR-040 or similar) — it touches backup schema, watch progress, downloads, episode metadata, tracker sync, library continue-watching, and stats. This is a major-version backup schema bump.
12. **Make `AniListDetailsProvider` degrade gracefully** when AniList is unreachable — currently it returns `null` (which the VM treats as "Anime not found"). It should fall through to the `ExtensionDetailsProvider` if a saved source link exists.

---

## 8. Files touched by this evidence (read, not modified)

All reads were `file:line` evidence-gathering only — no code was modified.

Key files read (alphabetical by module):
- `:core:anilist` — `AniListApi.kt`, `AniListRateLimiter.kt`, `LocalAniListCache.kt`, `model/AniListAnime.kt`, `details/AniListAnimeMapper.kt`, `README.md`
- `:core:backup` — `model/AnimeBackup.kt`, `provider/AnimeBackupProviders.kt`, `provider/EpisodeBackupProvider.kt`, `provider/WatchProgressBackupProvider.kt`, `provider/CoverImageProvider.kt`, `provider/CategoryBackupProvider.kt`, `provider/SourceLinkBackupProvider.kt`, `provider/BackupMappers.kt`, `translation/AniyomiBackupTranslator.kt`
- `:core:common` — `model/details/AnimeDetailsProvider.kt`, `AnimeDetailsProviderRegistry.kt`, `DataSource.kt`, `DetailsRequest.kt`
- `:core:database` — `animes.sq`, `anime_category.sq`, `animetrack.sq`
- `:core:download` — `DownloadManager.kt`, `DownloadModels.kt`, `DownloadRequest.kt`, `DownloadStore.kt`, `DownloadTask.kt`
- `:core:episode-metadata` — `repository/EpisodeMetadataRepository.kt`, `repository/EpisodeMetadataCache.kt`, `source/EpisodeMetadataSource.kt`, `source/anilist/AniListStreamingSource.kt`, `source/jikan/JikanMalSource.kt`
- `:core:preferences` — `ThemePreferences.kt` (grep only)
- `:core:tracker` — `Tracker.kt`, `TrackerManager.kt`, `TrackSyncManager.kt`, `TrackRepository.kt`, `AnimeTrack.kt`, `StatsCalculator.kt`, `TrackerBackupProvider.kt`, `anilist/AniListTracker.kt`, `anilist/AniListTrackApi.kt`, `mal/MalTracker.kt`
- `:core:update-checker` — `UpdateChecker.kt`
- `:core:player` — `WatchProgressStore.kt`
- `:data:anime` — `AnimeRepositoryImpl.kt`, `CategoryRepositoryImpl.kt`, `details/AniListDetailsProvider.kt`
- `:data:extension` — `AnimeExtensionManager.kt`, `matcher/SourceMatcher.kt`, `cache/SourceLinkStore.kt`, `cache/ExtensionLinkStore.kt`, `cache/DetailsViewPreferenceStore.kt`, `details/ExtensionDetailsProvider.kt`, `details/SAnimeMapper.kt`, `updatechecker/EpisodeFetchGatewayImpl.kt`
- `:data:history` — `HistoryRepositoryImpl.kt`
- `:feature:anime-details` — `AnimeDetailViewModel.kt`
- `:feature:backup` — `aniyomi/AniyomiRestoreViewModel.kt`
- `:feature:browse` — `BrowseScreen.kt`
- `:feature:history` — `HistoryViewModel.kt`
- `:feature:library` — `LibraryViewModel.kt`
- `:feature:my` — `ProfileViewModel.kt`
- `:feature:search` — `viewmodel/SearchViewModel.kt`, `viewmodel/ExtensionLinkingViewModel.kt`
- `:feature:updates` — `UpdatesViewModel.kt`
- `:app` — `download/DownloadOrchestrator.kt`
- `/home/z/my-project/anikuta/DOCS/aniyomi-backup-format/05-translation-plan.md` (outside the ANIKUTA dir — the translation plan)
