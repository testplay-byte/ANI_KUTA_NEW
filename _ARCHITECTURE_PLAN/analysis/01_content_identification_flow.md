# 01 — Content Identification Flow: Current State Analysis

> **Phase 1 / Current State.** This document traces the complete lifecycle of an anime entry through ANIKUTA and maps every point where the AniList ID is used as a primary key, identifier, or required field. Every claim is backed by `file:line` references from the codebase. Raw evidence: `_evidence/EVID-01-content-identification.md`.

---

## 1. Executive summary

ANIKUTA has **two parallel identity systems that do not fully interoperate**, and this is the single most important architectural fact in the entire codebase:

| Identity | Where it is the primary key | Where it is a nullable column | Where it is a composite-key component |
|---|---|---|---|
| `animes._id` (local DB `Long`) | Every SQLDelight table (`animes`, `episodes`, `animehistory`, `animetrack`, `anime_category`) | — | — |
| `anilistId` (`Int?` on the domain model, `Int` on cross-cutting stores) | `WatchProgressStore`, `PlaybackStateStore`, `DownloadTask`, `EpisodeMetadataCache`, `SourceLinkStore`, `DetailsViewPreferenceStore`, legacy `source_pref_<anilistId>` prefs | `animes.anilist_id` (nullable; partial unique index only when `NOT NULL`) | `"${anilistId}:${episodeUrl}"` — used everywhere progress, downloads, and history-sync are keyed |
| `sourceId + url` (`Long`, `String`) | Unlinked extension anime only (fallback when `anilistId == null`) | — | `ExtensionLinkStore` key `"${sourceId}:${animeUrl}"`; `DetailsViewPreferenceStore` key `"ext:${sourceId}:${url}"` |

**The central tension:** `anilistId` is `Int?` (nullable) on the canonical `Anime` domain model (`core/common/.../model/Anime.kt:53`), but it is **non-nullable (`Int`) on every cross-cutting store** (watch progress, playback state, downloads, episode metadata, tracker sync). The database schema is AniList-optional; the cross-cutting stores are not. This asymmetry produces **seven concrete failure modes** for unlinked extension anime (detailed in §6).

The bridge between the two systems is one-directional and lossy: `animes._id` can always be resolved from `anilistId` (via `selectByAnilistId`), but the reverse lookup (from a download task or watch-progress entry back to the local DB row) is brittle and depends on the cross-cutting store having stored a valid `anilistId`.

---

## 2. The canonical `Anime` domain model

**File:** `core/common/src/main/java/app/confused/anikuta/core/common/model/Anime.kt:20-64`

```kotlin
data class Anime(
    val id: Long,                    // animes._id (local SQLDelight PK)
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val coverUrl: String?,
    val status: Int,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val sourceId: Long,
    val dateAdded: Long,
    val viewerFlags: Int,
    val nextUpdate: Long,
    val updateStrategy: Int,
    val coverLastModified: Long,
    // Status-tracking columns (ADR-024)
    val releaseDate: Long?,
    val lastRefresh: Long,
    val lastMetadataFetch: Long?,
    val nextEpisodeCheck: Long?,
    // Library columns
    val anilistId: Int?,             // ← nullable. The whole architecture hinges on this.
    val coverColor: String?,
    val score: Double?,
    val totalEpisodes: Int?,
    val lastWatched: Long,
    val nextAiringEpisode: Int?,
)
```

**Key observations:**
- `anilistId` is `Int?` (nullable) with no default. Callers must explicitly pass `null`. This is the only AniList-specific field on the model.
- `id: Long` is the local DB primary key (`animes._id`), NOT the AniList ID. This is easy to misread because both are "IDs."
- `sourceId: Long` is the extension-source identifier (deterministic `MD5` of name/lang/version per `core/source-api/.../online/AnimeHttpSource.kt:114-118`). It is non-nullable, but for an AniList-only anime (never linked to an extension) it is `0L`.
- There is **no sentinel value** for "missing anilistId" — the model uses nullability. But many downstream callers convert `null → 0` (e.g., `Destinations.kt:173` `downloadKey = anilistId ?: 0`), and `0` then becomes a *de facto* sentinel that pollutes keyed stores (see §6.3).

---

## 3. Database schema — every table, every `anilistId`-related column

All `.sq` files live in `core/database/src/main/sqldelight/app/confused/anikuta/core/database/`. There are exactly 6 tables; schema version 2 (single migration `1.sqm`).

### 3.1 `animes.sq` — the library table

**File:** `core/database/src/main/sqldelight/.../animes.sq:3-39`

```sql
CREATE TABLE animes (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT,
    author TEXT,
    description TEXT,
    genre TEXT,           -- comma-separated
    cover_url TEXT,
    status INTEGER NOT NULL,
    thumbnail_url TEXT,
    favorite INTEGER NOT NULL DEFAULT 0,
    source_id INTEGER NOT NULL,
    date_added INTEGER NOT NULL,
    viewer_flags INTEGER NOT NULL DEFAULT 0,
    next_update INTEGER NOT NULL DEFAULT 0,
    update_strategy INTEGER NOT NULL DEFAULT 0,
    cover_last_modified INTEGER NOT NULL DEFAULT 0,
    release_date INTEGER,
    last_refresh INTEGER NOT NULL DEFAULT 0,
    last_metadata_fetch INTEGER,
    next_episode_check INTEGER,
    anilist_id INTEGER,              -- AniList media ID (nullable for non-AniList entries)
    cover_color TEXT,
    score REAL,
    total_episodes INTEGER,
    last_watched INTEGER NOT NULL DEFAULT 0,
    next_airing_episode INTEGER
);

-- Partial unique index: anilist_id is unique when not null
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_anilist_id
ON animes(anilist_id) WHERE anilist_id IS NOT NULL;
```

**Findings:**
- `_id` (INTEGER AUTOINCREMENT) is the **primary key**, not `anilist_id`.
- `anilist_id` is **nullable**, with a **partial unique index** enforcing uniqueness only when `NOT NULL`. Two unlinked extension anime can coexist; a linked one cannot be duplicated.
- `source_id + url` has **no unique index** — `selectBySourceAndUrl` is `LIMIT 1`, the practical "find the first" lookup.

**AniList-keyed queries** (`animes.sq:47-51, 98-99, 113-114, 116-124, 129-133`):
```sql
selectByAnilistId:                  SELECT * FROM animes WHERE anilist_id = :anilistId;
selectIdByAnilistId:                SELECT _id FROM animes WHERE anilist_id = :anilistId;
updateFavoriteByAnilistId:          UPDATE animes SET ... WHERE anilist_id = :anilistId;
updateLastWatchedByAnilistId:       UPDATE animes SET last_watched = :lastWatched WHERE anilist_id = :anilistId;
updateAnilistMetadataByAnilistId:   UPDATE animes SET title=..., cover_url=..., ... WHERE anilist_id = :anilistId;
updatePreferredCoverByAnilistId:    UPDATE animes SET cover_url=..., cover_color=... WHERE anilist_id = :anilistId;
```

> ⚠️ **Silent no-op failure mode:** Every "by anilistId" UPDATE silently no-ops if the row doesn't exist (SQLite `UPDATE` on zero rows is a no-op, not an error). When an unlinked extension anime is watched, the `last_watched` bump goes nowhere — no exception, no log.

### 3.2 `episodes.sq`, `animehistory.sq`, `animetrack.sq`, `anime_category.sq`, `categories.sq`

None of these tables have an `anilist_id` column. They are all keyed by `anime_id` (= `animes._id`, the local DB PK) or by composite `(anime_id, episode_id)` / `(anime_id, tracker_id)`.

| Table | PK | `anilist_id` column? | AniList dependency |
|---|---|---|---|
| `animes` | `_id` (Long AUTOINCREMENT) | YES — nullable, partial unique index | MODERATE — secondary lookup key, not PK |
| `episodes` | `_id` | NO — `anime_id` → `animes._id` | NONE |
| `animehistory` | `_id` + `UNIQUE(anime_id, episode_id)` | NO | NONE — **but table is unused** (see §5) |
| `animetrack` | `_id` + `UNIQUE(anime_id, tracker_id)` | NO — `remote_id` is tracker-side ID | NONE at DB layer |
| `anime_category` | `_id` | NO | NONE |
| `categories` | `_id` | NO | NONE |

**The DB layer is fully local-PK-based.** AniList-ID dependence is introduced entirely at the repository, store, ViewModel, and orchestrator layers — not by the schema. This is the single most important enabler for the proposed restructuring: the schema does not need to change.

---

## 4. Discovery flow — Browse + Search

### 4.1 BrowseScreen — AniList-only, identified by `AniListAnime.id`

**File:** `feature/browse/.../BrowseScreen.kt:62-65, 164`

```kotlin
@Composable
fun BrowseScreen(
    api: AniListApi,
    onOpenAnime: (Int) -> Unit = {},    // ← the AniList ID is the only identity propagated forward
)
// ...
AnimeCard(anime = item, onClick = { onOpenAnime(item.id) })   // item is AniListAnime; item.id: Int
```

BrowseScreen is AniList-only. It never sees an unlinked extension anime. Identity = `AniListAnime.id` (non-nullable `Int`).

### 4.2 SearchScreen — dual-source, two distinct identity types

**File:** `feature/search/.../viewmodel/SearchViewModel.kt:41-51`

```kotlin
sealed class SearchResult {
    data class AniList(val anime: AniListAnime) : SearchResult() {
        val id: Int get() = anime.id              // ← non-null Int
    }
    data class Extension(
        val source: AnimeCatalogueSource,
        val sAnime: SAnime,
        val sourceName: String,
    ) : SearchResult()                            // ← NO anilistId field
}
```

Search results are **not unified into a single type**. An AniList result carries `anime.id`; an Extension result carries only `source + sAnime` (the Aniyomi `SAnime` interface has `url: String`, `title: String`, **no `anilistId``). The UI branches on the sealed type at `SearchScreen.kt:100-104`.

### 4.3 The extension → AniList linking flow

When a user taps an Extension search result, `AppController.startLinking(source, sAnime)` (`navigation/AppController.kt:295-297`) stashes the target, and `ExtensionLinkingSheet` renders. The linking state machine (`ExtensionLinkingViewModel`) offers two outcomes:

1. **Linked** — the user picks an AniList anime; `anilistId: Int` is recorded.
2. **Go without linking** — `anilistId = null`; the anime is opened via the `ExtensionAnimeDetailDestination`.

Both paths converge on the unified details page (ADR-039), but they carry different identity:

```
   ╔═══════════════════════════════════════════════════════════════╗
   ║ DETAILS PAGE (unified — ADR-039)                              ║
   ╠═══════════════════════════════════════════════════════════════╣
   ║ DetailsRequest (sealed):                                      ║
   ║   • ByAniListId(anilistId: Int)              ◄── NON-NULLABLE  ║
   ║   • ByExtension(sourceId, animeUrl, animeTitle,               ║
   ║                 anilistId: Int? = null)       ◄── NULLABLE     ║
   ╚═══════════════════════════════════════════════════════════════╝
```

`AnimeDetailViewModel.currentAnilistId(): Int?` resolves to:
- `ByAniListId` → `req.anilistId`
- `ByExtension` → `req.anilistId ?: extensionLinkStore.getAniListId(sourceId, url)`

Provider dispatch (`registry.forSource(currentDataSource)`):
- **`AniListDetailsProvider.load`** — `ByAniListId` → `loadByAniListId(anilistId)`; `ByExtension` → `anilistId ?: linkStore.getAniListId(...)`, if null → returns null (**unlinked excluded**).
- **`ExtensionDetailsProvider.load`** — `ByExtension` → DB-first: if `anilistId != null` then `getByAnilistId` else `getBySourceAndUrl` (**unlinked OK**). AniList merge: if `effectiveAnilistId != null` then fetch + merge else skip merge (**unlinked OK**).

**`UnifiedAnime.anilistId: Int?`** — null for unlinked extension anime.

---

## 5. Watch progress — the `"$anilistId:$episodeUrl"` keyspace

**This is the most consequential cross-cutting store.** Watch progress lives in `WatchProgressStore` (JSON-in-SharedPreferences), NOT in the SQLDelight `animehistory` table.

**File:** `core/player/.../WatchProgressStore.kt`

- Pref key: `pref_watch_progress_map`
- Map key: `"$anilistId:$episodeUrl"`
- Map value: `Progress(positionSeconds, durationSeconds, title, updatedAt, coverUrl?, animeTitle?, episodeNumber, thumbnailUrl?)`

> ⚠️ **The SQLDelight `animehistory` table is wired but unused.** `HistoryViewModel` reads from `WatchProgressStore`, not from `HistoryRepository`. The KDoc at `feature/history/.../HistoryViewModel.kt:18-23` is explicit:
> *"We do NOT use `HistoryRepository` (the SQLDelight-backed `animehistory` table) — per the project's current architecture, `WatchProgressStore` is the source of truth for AniList-keyed progress until source URLs are fully resolved."*

**Why this matters:** The `animehistory` table is keyed by `(anime_id, episode_id)` — both local DB PKs — which would be AniList-independent. But because the live system reads from the anilistId-keyed `WatchProgressStore` instead, the AniList coupling is reintroduced at the read path even though the schema is clean.

### 5.1 The history-row-openability bug

`HistoryViewModel.parseKey(key)`:
```kotlin
key.substringBefore(':').toIntOrNull() ?: 0
→ HistoryEntry(anilistId, episodeUrl, progress)
```

`HistoryScreen` row tap: `onOpenAnime(entry.anilistId)`.
- If `anilistId == 0` → `AnimeDetailDestination(0)` → `AniListApi.fetchById(0)` → error state.
- **Result: history rows for unlinked extension anime are unopenable.** The progress was saved (under key `"0:<url>"`) but tapping the row can't navigate back to the anime.

---

## 6. The seven failure modes for unlinked extension anime

These are the concrete, code-verified symptoms of the dual-identity-system mismatch:

| # | System | Failure | Evidence |
|---|---|---|---|
| 1 | Downloads | Hard-blocked with a Toast "Cannot download — anime not linked" | `AppController.kt:509-512` |
| 2 | Episode metadata | Skipped entirely (metadata enrichment returns early) | `AnimeDetailViewModel.kt:629-632` |
| 3 | Watch progress | Pollutes the map under key `"0:<url>"`; history rows become unopenable | `WatchScreen.kt:644, 682`; `WatchProgressStore.kt:64` |
| 4 | Tracker sync | Skipped (`if (anilistId <= 0) continue`) | `TrackSyncManager.kt:62` |
| 5 | Backup | Partially excluded — watch progress, cover images, source links all gate on `anilistId > 0` / `!= null` | `WatchProgressBackupProvider.kt:70`; `CoverImageProvider.kt:44`; `SourceLinkBackupProvider.kt:64` |
| 6 | Updates schedule | Silently skipped (only anilistId-keyed anime appear) | `UpdatesViewModel.kt:136-140` |
| 7 | Library "last watched" sort | `updateLastWatchedByAnilistId` UPDATEs zero rows for unlinked anime (silent no-op) | `animes.sq` query + `AnimeRepositoryImpl` |

**Net effect:** An anime discovered via an extension and opened "without linking" can be viewed and watched, but it cannot be downloaded, its watch progress is saved under a degenerate key that makes the history row unopenable, it won't sync to any tracker, it won't appear in updates, and its "last watched" timestamp won't update. It is a second-class citizen.

---

## 7. Backup / restore — the cross-device identity problem

**ANIKUTA backup format** (ADR-036): `.anikuta` = ZIP containing `meta.json.gz` + optional `covers/<anilistId>.jpg`. Schema version 1. 10 backup providers.

**How anime are identified in the backup:**

| Backup entry | Key shape | Unlinked behavior |
|---|---|---|
| Library / AnimeDetails | `AnimeBackup(anilistId: Long?)` — upsert uses `selectIdByAnilistId` if non-null, else `selectBySourceAndUrl` | ✅ Included (fallback to source+url) |
| Episodes | `Map<key, List<EpisodeBackup>>` where `key = anilistId?.toString() ?: "${sourceId}:${url}"` | ✅ Included |
| WatchProgress | `Map<String, WatchProgressItem>` where `key = "$anilistId:$episodeUrl"` (verbatim) | ❌ Import gates on `anilistId > 0` — unlinked entries dropped |
| SourceLinks | `sourceLinks: Map<anilistId.toString(), SourceLinkItem>` + `extensionLinks: Map<"$sourceId:$animeUrl", Int(anilistId)>` | ⚠ Source links for unlinked anime are orphaned |
| Tracker | `TrackerBackupModel(...)` with `bindings: List<TrackerTrackItem(animeId, ...)>` where `animeId = animes._id` | ❌ `animeId` is the local DB PK from the source device — **not remapped** on restore. Aniyomi-translated path sets `animeId = anilistId.toLong()` — **mismatch** with the local `_id`. |
| Categories | Builds an `anilistId → localDbId` remap; falls back to direct `_id` | ⚠ Falls back to local `_id` for unlinked, which is device-specific |
| CoverImages | `Map<anilistId.toString(), coverUrl>` | ❌ Export gates on `anilistId != null` — unlinked excluded |
| EpisodeMetadata | `Map<anilistId.toString(), Map<epNum, item>>` | ❌ Unlinked excluded |

**The cross-device identity problem:** Because `animes._id` is AUTOINCREMENT and device-specific, the backup cannot rely on it for cross-device restore. The backup therefore uses `anilistId` as the cross-device identity — but this only works for *linked* anime. Unlinked extension anime fall back to `sourceId + url`, which is stable across devices **only if the same extension is installed on both devices**. If the target device doesn't have the extension, the restore silently fails to match.

**The Aniyomi restore path** (`AniyomiBackupTranslator`, 434 lines) resolves AniList IDs via: tracker binding → MAL ID lookup (`searchByMalId`) → title search (`searchByTitle`). Source-ID remapping (Aniyomi source IDs → ANIKUTA extension source IDs) is **NOT yet implemented**.

---

## 8. The complete identification flow (ASCII)

```
   DISCOVERY
   ═════════
   BrowseScreen ──────── AniList trending/popular ──────── onOpenAnime(anilistId: Int)
                                                              │
   SearchScreen ─────┬──── AniList tab ──────────────────── onOpenAnime(anilistId: Int)
                     │                                           │
                     └──── Extension tab ──── startLinking ───┐  │
                              │                               │  │
                  ┌───────────┴────────────┐                  │  │
                  ▼                        ▼                  │  │
           Linked(anilistId)        GoWithoutLinking           │  │
              : Int                 anilistId=null            │  │
                  │                        │                  │  │
                  └────────────┬───────────┘                  │  │
                               ▼                              │  │
   ═══════════════════════════════════════════════════════════ │ ═
   DETAILS PAGE (unified — ADR-039)                           │  │
   ═══════════════════════════════════════════════════════════ │ ═
   DetailsRequest:                                            │  │
     • ByAniListId(anilistId: Int)            ◄────────────────┘  │
     • ByExtension(sourceId, url, title, anilistId: Int?) ◄────────┘
                                                               │
   Episode metadata enrichment: anime.anilistId ?: return  ◄── SKIPPED if unlinked
                                                               │
   LIBRARY SAVE                                                ▼
   ═══════════
   findLibraryAnime(unified):
     anilistId != null → getByAnilistId(anilistId)
     else              → getBySourceAndUrl(sourceId, url)
   saveAnimeToLibrary → AnimeRepository.upsert(Anime(id=0, anilistId=unified.anilistId, ...))
       ▼
   animes table (anilist_id nullable, partial unique index)
     _id (Long, AUTOINCREMENT) is the PK — NOT anilistId
                                                               │
   WATCH FLOW                                                  ▼
   ═════════
   resolveEpisode(..., anilistId: Int):    ◄── caller passes 0 if unlinked
     if (anilistId != 0 && downloadManager.isEpisodeDownloaded(...))
        ▲                              ▲
        └── offline short-circuit      └── SKIPPED for anilistId = 0
   WatchRequest(anilistId: Int, ...)      ◄── NON-NULLABLE Int (0 for unlinked)
   WatchScreen saves progress:
     watchProgressStore.save(anilistId, episodeUrl, ...)
       key = "$anilistId:$episodeUrl"
       if anilistId = 0 → key = "0:<url>"  ◄── POLLUTES THE MAP
                                                               │
   DOWNLOAD FLOW                                               ▼
   ═════════════
   downloadEpisode(..., anilistId: Int):
     if (anilistId == 0) {              ◄── HARD GATE
        Toast "Cannot download — anime not linked"
        return
     }
   DownloadTask.key = "${anime.anilistId}:${episode.episodeUrl}"
   On-disk folder: "<root>/ANIKUTA/downloads/anime/<Title [anilistId]>/Episode NNN/"
                                                               │
   HISTORY                                                     ▼
   ═══════
   WatchProgressStore — JSON-in-SharedPreferences
     Map key: "$anilistId:$episodeUrl"
   HistoryViewModel.parseKey → HistoryEntry(anilistId, episodeUrl, progress)
   HistoryScreen row tap: onOpenAnime(entry.anilistId)
     if anilistId = 0 → fetchById(0) → ERROR STATE  ◄── UNOPENABLE ROWS
                                                               │
   TRACKER SYNC                                                ▼
   ════════════
   TrackSyncManager listens to WatchProgressStore.changes
     extractAnilistId: parse "$anilistId:$episodeUrl" → anilistId
     if (anilistId <= 0) continue              ◄── UNLINKED SKIPPED
     anime = animeRepository.getByAnilistId(anilistId) ?: return
     tracks = trackRepository.getTracks(anime.id)    ◄── animes._id, NOT anilistId
   animetrack table: anime_id=animes._id; remote_id=AniList/MAL id
                                                               │
   BACKUP / RESTORE                                            ▼
   ═══════════════
   .anikuta = ZIP{meta.json.gz, covers/<anilistId>.jpg}
   AnimeBackup(anilistId: Long?) — upsert by anilistId ?: sourceId+url
   WatchProgress key = "$anilistId:$episodeUrl" — import GATES on anilistId > 0
   CoverImages — export GATES on anilistId != null
   Tracker bindings: animeId = animes._id ◄── NOT remapped across devices
```

---

## 9. Where AniList ID enters the flow (entry-point map)

| Entry point | File:line | What it does | Nullable? |
|---|---|---|---|
| `BrowseScreen.onOpenAnime` | `feature/browse/.../BrowseScreen.kt:62` | Carries `AniListAnime.id: Int` forward | No (AniList-only screen) |
| `SearchScreen` AniList result tap | `feature/search/.../SearchScreen.kt:100-104` | Calls `onOpenAnime(result.id)` | No |
| `SearchScreen` Extension result tap | `feature/search/.../SearchScreen.kt:100-104` | Calls `onOpenExtensionResult(result)` → `startLinking` | n/a (enters linking flow) |
| `AnimeDetailDestination(anilistId: Int)` | `navigation/Destinations.kt` | Voyager screen param | No (0 = unlinked sentinel) |
| `ExtensionAnimeDetailDestination(downloadKey: Int = anilistId ?: 0, ...)` | `navigation/Destinations.kt:173` | Voyager screen param; `0` = unlinked | **`null → 0` conversion here** |
| `AppController.resolveEpisode(anilistId: Int, ...)` | `navigation/AppController.kt:367` | Builds `WatchRequest` | No (0 = unlinked) |
| `AppController.downloadEpisode(anilistId: Int, ...)` | `navigation/AppController.kt:509` | Enqueues download; gates on `== 0` | No (0 = blocked) |
| `WatchProgressStore.save(anilistId, episodeUrl, ...)` | `core/player/.../WatchProgressStore.kt:64` | Persists progress under `"$anilistId:$episodeUrl"` | No (0 pollutes) |
| `DownloadAnimeInfo(anilistId: Int, ...)` | `core/download/.../DownloadModels.kt:27` | The download data model | **No — non-nullable by type** |
| `TrackSyncManager` progress listener | `core/tracker/.../TrackSyncManager.kt:62` | Extracts anilistId from composite key; skips if `<= 0` | No |
| `EpisodeMetadataCache` key | `core/episode-metadata/.../EpisodeMetadataCache.kt` | `anilistId.toString()` outer key | No (skipped if null) |
| `SourceLinkStore` key | `data/extension/.../SourceLinkStore.kt` | `anilistId.toString()` | No (anilistId-keyed by design) |
| `DetailsViewPreferenceStore` key | `feature/anime-details/.../DetailsViewPreferenceStore.kt` | `anilistId.toString()` OR `"ext:$sourceId:$url"` | **Yes — hybrid (the only store that handles unlinked)** |

---

## 10. Key insight: one store already does it right

`DetailsViewPreferenceStore` (per-anime display preferences) uses a **hybrid key**:
```kotlin
val key = anilistId?.toString() ?: "ext:$sourceId:$url"
```

This is the **only cross-cutting store that correctly handles unlinked extension anime**. It proves the pattern works: a store can be AniList-aware without being AniList-dependent. The proposed restructuring (see `proposals/01_internal_id_system.md`) generalizes this pattern to a typed `WatchableId` value class.

---

## 11. Conclusion — what this analysis establishes

1. **The database schema is NOT the blocker.** All 6 SQLDelight tables use local `_id` as PK. `anilist_id` is a nullable secondary column with a partial unique index. The schema already supports unlinked anime.

2. **The blocker is the cross-cutting stores.** `WatchProgressStore`, `PlaybackStateStore`, `DownloadTask`, `EpisodeMetadataCache`, `SourceLinkStore`, and several preference stores all key by non-nullable `anilistId: Int`. These are the systems that silently exclude or break for unlinked anime.

3. **The composite key `"$anilistId:$episodeUrl"` is duplicated across 9+ files** with no central helper. This is the single most pervasive coupling point. (Full table in `_evidence/EVID-01` §7.4.)

4. **The `null → 0` conversion at `Destinations.kt:173` is the root of the "0" pollution.** It converts a meaningful null into a degenerate sentinel that then propagates through every store.

5. **The backup format's reliance on `anilistId` for cross-device identity** means unlinked anime cannot be reliably restored across devices (especially if the target device lacks the same extension).

6. **One store (`DetailsViewPreferenceStore`) already demonstrates the correct hybrid-key pattern.** This is the template for the proposed `WatchableId` abstraction.

These findings directly motivate the proposals in Phase 2:
- `proposals/01_internal_id_system.md` — a typed `WatchableId` to replace the `"$anilistId:$episodeUrl"` string keys.
- `proposals/02_provider_abstraction.md` — decoupling the metadata layer from AniList.
- `proposals/03_download_system_redesign.md` — removing the anilistId gate and the composite-key dependency.

---

*Evidence source: `_evidence/EVID-01-content-identification.md` (2,326 lines, 471 `anilistId` references inventoried, 15 composite-key occurrences, 32 anilistId-null/zero checks).*
