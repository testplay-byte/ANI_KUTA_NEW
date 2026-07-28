# 03 — Provider Coupling Map

> **Phase 1 / Current State.** For every system in the app, this document maps how it depends on AniList, the severity of coupling, what would need to change to decouple it, and `file:line` evidence. Raw evidence: `_evidence/EVID-03-provider-coupling.md`.

---

## 1. Executive summary

AniList is woven through ANIKUTA at **three layers**:

1. **The identity layer** — `anilistId` is the primary key for watch progress, downloads, tracker sync, episode metadata, library cover updates, and backup. (Covered in detail in `01_content_identification_flow.md`.)
2. **The metadata layer** — AniList is the sole source for the browse feed, is one of two search tabs, is the default details-page provider, is the sole source for the airing schedule, and is one of three episode-metadata sources.
3. **The tracker layer** — AniList is one of two trackers (the other is MAL), but MAL is *indirectly* coupled because tracker sync keys off the anilistId-keyed watch progress.

**What's genuinely AniList-free** (the green shoots):
- The extension-only details page (ADR-039 `ExtensionDetailsProvider` unlinked mode)
- Category memberships (DB junction uses `anime_id`, not `anilistId`)
- Episode list ordering inside an extension source
- MAL-tracker stats (MAL has its own API)
- The download *engine* (the coupling is upstream, in `AppController`)

**The single highest-leverage change:** introducing a `WatchableId` value type (`AniListId(Int)` | `ExtensionId(sourceId, url)`) in `:core:common`. Once introduced, all the `"$anilistId:$episodeUrl"` keyspaces can migrate to it, unblocking downloads + tracker sync + watch progress + episode metadata for unlinked extension anime. (See `proposals/01_internal_id_system.md` + `proposals/02_provider_abstraction.md`.)

**A hybrid keying model already exists** in `DetailsViewPreferenceStore` (`anilistId.toString()` OR `"ext:$sourceId:$url"`) — this is the pattern to generalize.

**The provider abstraction already exists** for: the details page (`AnimeDetailsProvider`), the tracker layer (`Tracker` interface), episode metadata (`EpisodeMetadataSource`), and the download engine (`DownloadManager`). Missing abstractions: `HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`, and the cross-cutting `WatchableId`.

---

## 2. The `:core:anilist` module — public surface

**5 Kotlin files, ~1,173 lines:**

| File | Purpose |
|---|---|
| `core/anilist/.../api/AniListApi.kt` (727 lines) | Raw GraphQL client. **The de-facto AniList repository** — there is no `AniListRepository` class. |
| `core/anilist/.../api/AniListRateLimiter.kt` (96 lines) | Sliding-window rate limiter (80 req/min cap, 40-req fast mode). |
| `core/anilist/.../api/LocalAniListCache.kt` (108 lines) | 24h persistent cache (trending, popular, per-id detail) via `PreferenceStore`. |
| `core/anilist/.../model/AniListAnime.kt` (165 lines) | `@Serializable` model + helpers (`displayTitle`, `coverUrl`, `coverColorHex`, `seasonDisplay`, `studioName`, `startDateDisplay`, `nextAiringDisplay`). Also `AiringScheduleInfo`. |
| `core/anilist/.../details/AniListAnimeMapper.kt` (77 lines) | `AniListAnime.toUnifiedAnime(matchedSourceId, matchedSourceName)` → `UnifiedAnime` (`DataSource.ANILIST`). |

### `AniListApi` public methods (every one)

| Method | Signature | Line | Cached? |
|---|---|---|---|
| `fetchTrending` | `suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime>` | L63 | 24h local + in-memory SWR |
| `fetchPopular` | `suspend fun fetchPopular(page: Int = 1, perPage: Int = 20): List<AniListAnime>` | L80 | 24h local + in-memory SWR |
| `searchAnime` | `suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 20): List<AniListAnime>` | L95 | NOT cached |
| `searchByMalId` | `suspend fun searchByMalId(malId: Int): AniListAnime?` | L108 | NOT cached (Aniyomi restore) |
| `searchByTitle` | `suspend fun searchByTitle(title: String): AniListAnime?` | L137 | NOT cached (Aniyomi restore) |
| `searchByTitleMultiple` | `suspend fun searchByTitleMultiple(title: String, perPage: Int = 10): List<AniListAnime>` | L167 | NOT cached (manual linking) |
| `searchAnimeWithFilters` | `suspend fun searchAnimeWithFilters(query, page, perPage, genres, year, season, format, status, sort, minScore): List<AniListAnime>` | L201 | NOT cached |
| `getCachedTrending` | `fun getCachedTrending(): List<AniListAnime>?` | L296 | Sync read of in-memory + local cache |
| `fetchAiringSchedule` | `suspend fun fetchAiringSchedule(ids: List<Int>): List<AiringScheduleInfo>` | L354 | 5-min in-memory cache, keyed by sorted-id tuple |
| `fetchById` | `suspend fun fetchById(id: Int): AniListAnime?` | L467 | 5-min in-memory + 24h local persistent |

**GraphQL endpoint:** `https://graphql.anilist.co` (constant `API_URL`, L609). All queries are **unauthenticated** (browse-only). Authenticated access lives separately in `:core:tracker/anilist/AniListTrackApi.kt`.

> ⚠️ **Three separate AniList HTTP clients exist:**
> 1. `AniListApi` (browse/search/details — unauthenticated GraphQL)
> 2. `AniListTrackApi` (tracker — authenticated GraphQL with user token)
> 3. The Aniyomi backup translator's ad-hoc calls
> Consolidation is overdue.

---

## 3. System-by-system coupling map

### Summary table

| # | System | AniList coupling | What depends on AniList | What breaks if AniList vanished | Decoupling effort |
|---|---|---|---|---|---|
| 1 | Library | MODERATE | Cover/metadata refresh, "last watched" sort | Library still works (DB is local-PK); metadata goes stale | Small |
| 2 | Tracking/sync | TIGHT (AniList) + LOOSE (MAL) | AniList tracker; MAL indirect via anilistId-keyed progress | AniList tracker dies; MAL tracker loses its progress trigger | Medium |
| 3 | Downloads | TIGHT | `DownloadAnimeInfo.anilistId` (non-null), composite key, folder name, gate | Downloads entirely blocked for unlinked anime | Large (see Doc 02) |
| 4 | Watch history | TIGHT | Keyspace `"$anilistId:$episodeUrl"` | Unlinked anime progress pollutes under `"0:<url>"`; history rows unopenable | Medium |
| 5 | Categories | NONE (direct) | DB junction uses `anime_id` | Nothing | None |
| 6 | Search | TIGHT (AniList tab) + LOOSE (Extension tab) + TIGHT (linking) | AniList tab, linking flow | AniList tab empty; linking impossible | Medium |
| 7 | Details page | TIGHT (AniList mode) + MODERATE (linked ext) + NONE (unlinked ext) | `AniListDetailsProvider` default | AniList mode dies; linked-ext loses AniList merge; unlinked-ext unaffected | Small (abstraction exists) |
| 8 | Browse/Home | TIGHT | Sole feed source | Home screen empty | Medium (needs HomeFeedProvider) |
| 9 | Extensions | LOOSE | Extensions themselves are AniList-free; coupling is in link stores | Extensions still work; linking breaks | Small |
| 10 | Preferences | NONE (in `:core:preferences`) | Scattered anilistId-keyed prefs in 8 modules | Per-anime prefs for unlinked anime lost | Medium |
| 11 | Backup/restore | TIGHT | AniList is SOLE cross-device identity; Aniyomi translator's only dep is `AniListApi` | Cross-device restore of unlinked anime fails; Aniyomi restore dies | Large |
| 12 | Profile/Stats | MODERATE | Local-mode fallback exists | AniList-stats mode dies; local-stats mode survives | Small |
| 13 | Updates/Schedule | TIGHT (Schedule) + LOOSE (Updates) | Schedule = AniList airing; Updates = anilistId-keyed | Schedule dies; Updates skips unlinked | Medium |
| 14 | Episode metadata | TIGHT keying + 1 of 3 sources | `EpisodeMetadataCache` keyed by anilistId; AniList is one source | Unlinked anime get no metadata; AniList-source metadata dies | Medium |

---

### 3.1 Library — MODERATE

**How it depends on AniList:**
- `animes.anilist_id` is a nullable column with a partial unique index (`animes.sq:38-39`).
- Metadata refresh: `AnimeRepositoryImpl.refreshFromAniList(anilistId)` calls `AniListApi.fetchById` and updates title/cover/score/total_episodes/next_airing (`AnimeRepositoryImpl` + `animes.sq:updateAnilistMetadataByAnilistId`).
- "Last watched" sort: `updateLastWatchedByAnilistId` (`animes.sq`) — silently no-ops for unlinked anime.

**What would break if AniList vanished:** Library still works (the DB is local-PK). Metadata goes stale. "Last watched" sort breaks for any anime not already linked (but linked anime keep their last-watched).

**Decoupling:** Replace `updateLastWatchedByAnilistId` with `updateLastWatchedById(_id)` (the local PK). Make metadata refresh go through a `MetadataProvider` abstraction (see proposal 02). **Effort: Small.**

### 3.2 Tracking/sync — TIGHT (AniList) + LOOSE (MAL)

**How it depends on AniList:**
- `TrackSyncManager` (`core/tracker/.../TrackSyncManager.kt:62`) listens to `WatchProgressStore.changes`, extracts `anilistId` from the composite key, skips if `<= 0`.
- It then calls `animeRepository.getByAnilistId(anilistId)` to get the local `_id`, then `trackRepository.getTracks(anime.id)`.
- `animetrack` table: `anime_id = animes._id`, `tracker_id` (1=MAL, 2=AniList), `remote_id` (AniList mediaId OR MAL anime id).
- `AniListTracker` + `MALTracker` both implement a `Tracker` interface.
- AniList tracker uses `AniListTrackApi` (authenticated GraphQL).
- MAL tracker uses its own OAuth (PKCE) + REST API.

**What would break if AniList vanished:** AniList tracker dies entirely. MAL tracker loses its *progress trigger* (because `TrackSyncManager` extracts anilistId from the watch-progress key) — but MAL tracker itself is independent. The `remote_id` for AniList tracks becomes meaningless.

**Decoupling:** `TrackSyncManager` should key off `WatchableId` (not anilistId) → resolve to local `_id` → read tracks. This makes tracker sync work for unlinked extension anime too (if they have a track binding). **Effort: Medium.**

### 3.3 Downloads — TIGHT

(See `02_download_system_analysis.md` for the full deep-dive.)

**Headline:** `DownloadAnimeInfo.anilistId: Int` is non-nullable by type. The composite key `"$anilistId:$episodeUrl"` is used everywhere. The hard gate at `AppController.kt:509-512` blocks unlinked anime entirely.

**Decoupling effort: Large** — requires the `WatchableId` type, a new download key, folder-name changes, and a migration. See `proposals/03_download_system_redesign.md`.

### 3.4 Watch history — TIGHT

**How it depends on AniList:**
- `WatchProgressStore` keyspace: `"$anilistId:$episodeUrl"` (`WatchProgressStore.kt:64`).
- `HistoryViewModel.parseKey` extracts anilistId from the composite key.
- `HistoryScreen` row tap calls `onOpenAnime(entry.anilistId)` — fails for `anilistId = 0`.

**What would break if AniList vanished:** Progress for unlinked anime is saved under `"0:<url>"` (pollution). History rows for unlinked anime are unopenable. The SQLDelight `animehistory` table (which is AniList-free) is unused.

**Decoupling:** Migrate `WatchProgressStore` keyspace to `WatchableId`. Optionally, move progress into the SQLDelight `animehistory` table (already AniList-free). **Effort: Medium.**

### 3.5 Categories — NONE (direct)

**How it depends on AniList:** It doesn't. `anime_category` junction uses `anime_id = animes._id`. `CategoryRepository` is fully local-PK.

**What would break if AniList vanished:** Nothing.

**Decoupling effort: None.** ✅

### 3.6 Search — TIGHT (AniList tab) + LOOSE (Extension tab) + TIGHT (linking)

**How it depends on AniList:**
- AniList tab calls `AniListApi.searchAnime` / `searchAnimeWithFilters`.
- Extension tab calls `AnimeCatalogueSource.fetchSearchManga`.
- The linking flow (`ExtensionLinkingSheet` + `ExtensionLinkingViewModel`) searches AniList by title to find a match.
- `SourceMatcher` (`data/extension/.../matcher/SourceMatcher.kt:346-378`) auto-matches an extension `SAnime` to an AniList anime via title normalization + Levenshtein (threshold 0.80).

**What would break if AniList vanished:** AniList tab empty. Linking flow dies (no AniList to link to). `SourceMatcher` dies. Extension tab still works (but results can't be linked).

**Decoupling:** Introduce a `SearchProvider` abstraction (like `AnimeDetailsProvider`). AniList becomes one provider; MAL/TMDB/Kitsu could be others. The linking flow becomes "link to any provider." **Effort: Medium.**

### 3.7 Details page — TIGHT (AniList mode) + MODERATE (linked ext) + NONE (unlinked ext)

**How it depends on AniList:**
- `AniListDetailsProvider` (default) calls `AniListApi.fetchById`.
- `ExtensionDetailsProvider` (ADR-039) loads from the extension source; if `effectiveAnilistId != null`, merges AniList metadata on top.
- The source-switcher menu (`SourceSwitcherMenu`) lets the user pick the data source in-place.

**What would break if AniList vanished:** AniList mode dies. Linked-extension mode loses the AniList merge (cover color, score, total episodes, next airing). Unlinked-extension mode is **unaffected** (this is the one green shoot).

**Decoupling effort: Small** — the abstraction (`AnimeDetailsProvider`) already exists. Adding a third provider (e.g., `MALDetailsProvider` or `TMDBDetailsProvider`) is one new class + one Koin line (`app/.../di/DetailsModule.kt:38-62`). The pattern is proven.

### 3.8 Browse/Home — TIGHT

**How it depends on AniList:**
- `BrowseScreen` calls `AniListApi.fetchTrending` + `fetchPopular` (`feature/browse/.../BrowseScreen.kt:62-65`).
- `LocalAniListCache` (24h TTL) provides offline access to the cached feed.
- There is NO extension-popular fallback. Home is AniList-only.

**What would break if AniList vanished:** Home screen empty (only the 24h cache remains, then nothing).

**Decoupling:** Introduce a `HomeFeedProvider` abstraction. AniList is one; extensions could contribute "popular" feeds; TMDB could be another. **Effort: Medium.**

### 3.9 Extensions — LOOSE

**How it depends on AniList:** Extensions themselves are AniList-free — they implement `AnimeSource` and know only about `SAnime.url` + `SAnime.title`. The coupling is in the *link stores*:
- `SourceLinkStore`: `Map<anilistId.toString(), SourceLinkItem(sourceId, sourceName, animeUrl, animeTitle)>`.
- `ExtensionLinkStore`: `Map<"$sourceId:$animeUrl", Int(anilistId)>`.

**What would break if AniList vanished:** Extensions still work (fetch episode lists, resolve videos). But the link between an extension anime and its AniList metadata breaks — so the details page can't merge AniList data, and tracker sync can't find the anilistId.

**Decoupling effort: Small** — extensions are already decoupled. The link stores need to migrate to `WatchableId`.

### 3.10 Preferences — NONE (in `:core:preferences`)

**How it depends on AniList:** `:core:preferences` has zero anilistId references. But **8 other modules** have anilistId-keyed preferences:
- `:core:player` — `WatchProgressStore`, `PlaybackStateStore`, `PlayerEpisodePreferences` (per-anime)
- `:core:episode-metadata` — `EpisodeMetadataCache`
- `:data:extension` — `SourceLinkStore`, `ExtensionLinkStore`
- `:feature:anime-details` — `DetailsViewPreferenceStore` (the one hybrid store)
- `:core:update-checker` — `UpdateCheckerPreferences.lastKnownEpisodeCount` (currently keyed by local `_id`, which is a bug — should be anilistId for cross-device consistency)
- Legacy `source_pref_<anilistId>` SharedPreferences (per-source per-anime settings)

**Decoupling effort: Medium** — migrate each store to `WatchableId` keys.

### 3.11 Backup/restore — TIGHT

**How it depends on AniList:**
- AniList is the **SOLE source of truth for cross-device identity**. The backup uses `anilistId` as the cross-device key for library entries, watch progress, episode metadata, cover images, and source links.
- The Aniyomi restore translator (`AniyomiBackupTranslator`, 434 lines) resolves AniList IDs via: tracker binding → `searchByMalId` → `searchByTitle`. Its only external dependency is `AniListApi`.
- Source-ID remapping (Aniyomi source IDs → ANIKUTA extension source IDs) is **NOT yet implemented**.
- Tracker bindings serialize `animeId = animes._id` (device-specific) — **not remapped** across devices. The Aniyomi-translated path sets `animeId = anilistId.toLong()` — a mismatch with the local `_id`.

**What would break if AniList vanished:** Cross-device restore of unlinked anime fails (no stable cross-device identity). Aniyomi restore dies entirely (it depends on `AniListApi`). Tracker bindings don't remap correctly.

**Decoupling effort: Large** — requires a stable cross-device identity (the `WatchableId` proposal includes a deterministic component for this) + source-ID remapping. See `proposals/05_migration_strategy.md`.

### 3.12 Profile/Stats — MODERATE

**How it depends on AniList:**
- `StatsCalculator` has two modes: local (from watch progress + library) and AniList (from the user's AniList OAuth).
- Local mode works without AniList OAuth (but still keys off anilistId-keyed watch progress).
- AniList mode requires OAuth and calls the authenticated AniList API.

**What would break if AniList vanished:** AniList-stats mode dies. Local-stats mode survives (but misses unlinked anime).

**Decoupling effort: Small** — local mode already exists. Just needs the `WatchableId` migration to include unlinked anime.

### 3.13 Updates/Schedule — TIGHT (Schedule) + LOOSE (Updates)

**How it depends on AniList:**
- **Schedule tab:** `AniListApi.fetchAiringSchedule(ids: List<Int>)` (`AniListApi.kt:354`). The schedule is entirely AniList-sourced. 5-min in-memory cache.
- **Updates tab:** Pull-to-refresh; `UpdatesViewModel.kt:136-140` silently skips anime without anilistId. Manual only (no WorkManager).

**What would break if AniList vanished:** Schedule tab dies. Updates tab skips unlinked anime (already broken).

**Decoupling:** Introduce an `AiringScheduleProvider` abstraction. AniList is one; extensions could contribute schedule data (some sources post schedules). **Effort: Medium.**

### 3.14 Episode metadata — TIGHT keying + 1 of 3 sources

**How it depends on AniList:**
- `EpisodeMetadataCache` keyed by `anilistId.toString()` (outer) + `episodeNumber` (inner).
- Three sources: Jikan/MAL, Anikage.cc, AniList. Each implements `EpisodeMetadataSource`.
- `AnimeDetailViewModel.kt:629-632`: `anime.anilistId ?: return` — unlinked anime get no metadata.

**What would break if AniList vanished:** AniList-source metadata dies. Unlinked anime still get no metadata (already broken). Jikan + Anikage sources survive (but the cache key is anilistId, so they'd need the `WatchableId` migration).

**Decoupling effort: Medium** — the source abstraction exists; the cache key needs `WatchableId`.

---

## 4. Things with NO AniList fallback (red list)

These are the systems that have zero fallback if AniList is unavailable:

1. **Browse/Home feed** — AniList is the sole source. No extension-popular fallback.
2. **Airing schedule** — AniList is the sole source.
3. **AniList tracker** — AniList is the tracker (can't fall back to itself).
4. **Cross-device backup identity for unlinked anime** — `sourceId + url` only works if the same extension is installed on the target device.
5. **Aniyomi restore** — depends on `AniListApi` for ID resolution.
6. **Episode metadata for linked anime** — AniList is one of three sources, but the cache key is anilistId (so even Jikan/Anikage lookups require an anilistId).
7. **Downloads for unlinked anime** — hard-blocked.
8. **Tracker sync for unlinked anime** — skipped.
9. **Cover image refresh** — AniList is the sole source for `coverUrl` + `coverColor`.
10. **Source matching auto-link** — `SourceMatcher` matches extension anime to AniList anime.

---

## 5. Things that already work WITHOUT AniList (green list)

These are the systems that already operate AniList-free (the foundation to build on):

1. **Extension-only details page** (ADR-039 unlinked mode) — `ExtensionDetailsProvider` with `anilistId = null`.
2. **Category memberships** — DB junction uses `anime_id`.
3. **Episode list inside an extension source** — keyed by `anime_id`.
4. **The download engine** (`:core:download` internals) — zero anilistId gates.
5. **MAL tracker** (independent API).
6. **Local-stats mode** in `StatsCalculator`.
7. **The `AnimeDetailsProvider` abstraction** — already supports multiple providers via Koin multi-binding.
8. **The `Tracker` interface** — already supports AniList + MAL.
9. **The `EpisodeMetadataSource` interface** — already supports Jikan + Anikage + AniList.
10. **`DetailsViewPreferenceStore` hybrid key** — `anilistId?.toString() ?: "ext:$sourceId:$url"`.
11. **The `UnifiedAnime` model** — `anilistId: Int?` is nullable.
12. **The `animes` DB schema** — `anilist_id` is nullable with a partial unique index.
13. **Extension loading** — `AnimeExtensionManager` is AniList-free.
14. **Video resolution** — `ResolverService` is AniList-free.
15. **The `DownloadManager` engine** — accepts already-resolved `DownloadRequest`.

---

## 6. Three AniList HTTP clients (consolidation opportunity)

| Client | Location | Auth | Purpose |
|---|---|---|---|
| `AniListApi` | `:core:anilist/.../api/AniListApi.kt` | None (browse) | Trending, popular, search, details, schedule |
| `AniListTrackApi` | `:core:tracker/anilist/AniListTrackApi.kt` | OAuth token | Tracker sync (progress, status, score) |
| Ad-hoc (Aniyomi translator) | `:feature:backup/.../AniyomiBackupTranslator.kt` | None | ID resolution during restore |

**Recommendation:** Consolidate into a single `AniListClient` with authenticated + unauthenticated modes. This reduces code duplication, unifies rate limiting, and simplifies the provider-abstraction layer. (See `proposals/02_provider_abstraction.md`.)

---

## 7. The hybrid keying pattern (the template to generalize)

`DetailsViewPreferenceStore` (`feature/anime-details/.../DetailsViewPreferenceStore.kt`) already does it right:

```kotlin
val key = anilistId?.toString() ?: "ext:$sourceId:$url"
```

This is the **only cross-cutting store that correctly handles unlinked extension anime**. It proves the pattern: a store can be AniList-aware without being AniList-dependent.

**The proposal:** generalize this into a typed `WatchableId` value class in `:core:common`:

```kotlin
@Serializable
sealed class WatchableId {
    @Serializable data class AniList(val id: Int) : WatchableId()
    @Serializable data class Extension(val sourceId: Long, val url: String) : WatchableId()

    fun stableKey(): String = when (this) {
        is AniList -> "al:$id"
        is Extension -> "ext:$sourceId:$url"
    }
}
```

Every cross-cutting store (`WatchProgressStore`, `PlaybackStateStore`, `DownloadTask.key`, `EpisodeMetadataCache`, `SourceLinkStore`) migrates from `"$anilistId:$episodeUrl"` to `WatchableId.stableKey()`. This is the single highest-leverage change in the entire restructuring.

---

## 8. Conclusion

The provider coupling is **concentrated in the identity layer and the metadata-feed layer**, not in the engine layers. The download engine, the tracker interface, the episode-metadata source interface, and the details-provider abstraction are all already clean. The work is:

1. **Introduce `WatchableId`** (unblocks downloads, tracker sync, watch progress, episode metadata for unlinked anime).
2. **Add `HomeFeedProvider` + `SearchProvider` + `AiringScheduleProvider` abstractions** (unblocks browse, search, schedule).
3. **Consolidate the three AniList HTTP clients** (reduces duplication).
4. **Migrate the cross-cutting stores** to `WatchableId` keys.

The green list (§5) shows the foundation is solid — 15 systems already work without AniList. The red list (§4) shows the 10 systems that need work. The hybrid-key pattern (§7) shows the template.

---

*Evidence source: `_evidence/EVID-03-provider-coupling.md` (907 lines).*
