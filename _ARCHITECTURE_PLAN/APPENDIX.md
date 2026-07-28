# APPENDIX — Code References, Schemas, File Lists

> Quick-reference index of the key files, schemas, and code locations cited throughout this plan. Organized for fast lookup during implementation.

---

## A. Key file index (by area)

### A.1 Content identification (Doc 01)

| File | What's there |
|---|---|
| `core/common/src/main/java/app/confused/anikuta/core/common/model/Anime.kt:20-64` | The `Anime` domain model — `anilistId: Int?` at line 53 |
| `core/common/src/main/java/app/confused/anikuta/core/common/model/details/UnifiedAnime.kt` | `UnifiedAnime` — `anilistId: Int?` nullable |
| `core/common/src/main/java/app/confused/anikuta/core/common/model/details/AnimeDetailsProvider.kt` | The provider interface (ADR-039) |
| `core/common/src/main/java/app/confused/anikuta/core/common/model/details/DetailsRequest.kt` | Sealed: `ByAniListId(anilistId: Int)` + `ByExtension(..., anilistId: Int? = null)` |
| `navigation/Destinations.kt:173` | `downloadKey = anilistId ?: 0` — the `null → 0` conversion |
| `navigation/AppController.kt:367` | `resolveEpisode(..., anilistId: Int)` — offline-playback short-circuit |
| `navigation/AppController.kt:509-512` | The `anilistId == 0` hard gate on downloads |
| `feature/anime-details/.../DetailsViewPreferenceStore.kt` | The HYBRID key template: `anilistId?.toString() ?: "ext:$sourceId:$url"` |
| `feature/anime-details/.../AnimeDetailViewModel.kt:629-632` | `anime.anilistId ?: return` — episode metadata skipped for unlinked |

### A.2 Watch progress + history (Doc 01, 06)

| File | What's there |
|---|---|
| `core/player/.../WatchProgressStore.kt:64` | Keyspace `"$anilistId:$episodeUrl"` in SharedPreferences (`pref_watch_progress_map`) |
| `core/player/.../PlaybackStateStore.kt` | Same keyspace (`pref_playback_state_map`) |
| `feature/history/.../HistoryViewModel.kt:18-23, 49` | Reads from `WatchProgressStore` (NOT the SQLDelight `animehistory` table); `parseKey` extracts anilistId |
| `core/database/.../animehistory.sq:3-12` | The UNUSED SQLDelight history table (keyed by `anime_id + episode_id`) |
| `core/tracker/.../TrackSyncManager.kt:62` | `if (anilistId <= 0) continue` — tracker sync skipped for unlinked |

### A.3 Downloads (Doc 02)

| File | What's there |
|---|---|
| `core/download/.../DownloadModels.kt:26-31` | `DownloadAnimeInfo(anilistId: Int, ...)` — non-nullable |
| `core/download/.../DownloadModels.kt:68-74` | `DownloadEpisodeInfo(episodeUrl, episodeNumber: Float, ...)` — epNum IS persisted |
| `core/download/.../DownloadRequest.kt:39` | `sourceId: Long = 0L` — present but inert |
| `core/download/.../DownloadTask.kt:41` | `val key = "${request.anime.anilistId}:${request.episode.episodeUrl}"` — the composite key definition |
| `core/download/.../DownloadManager.kt` | The interface — 5 of 14 methods take `anilistId` |
| `core/download/.../DefaultDownloadManager.kt:167` | **THE SOURCE-SWITCHING BREAK POINT** — `if (task == null) return false` |
| `core/download/.../DefaultDownloadManager.kt:203` | Inline composite key in `findTask` |
| `core/download/.../DownloadQueue.kt:309-310` | `keyFor(request)` — duplicated composite key |
| `core/download/.../DownloadStore.kt:73` | Persistence: JSON `List<DownloadTask>` in SharedPreferences (`pref_download_tasks_v1`) |
| `core/download/.../DownloadStorageProvider.kt:106-119` | Folder structure: `<root>/ANIKUTA/downloads/anime/<Title [anilistId]>/Episode NNN/` |
| `core/download/.../DownloadStorageProvider.kt:111` | `ensureDir(root, "ANIKUTA")` — the mandatory subfolder |
| `core/download/.../DownloadStorageProvider.kt:461` | `EpisodeMetadataCache.sourceId` — sourceId persisted to metadata.json |
| `core/download/.../HttpDownloader.kt` | Normal downloader (single-threaded, magic-byte validation) |
| `core/download/.../advanced/AdvancedHttpDownloader.kt` | Multi-threaded Range-request downloader (4-thread default, resume via `resume.json`) |
| `core/download/.../HlsDownloader.kt` | HLS `.m3u8` parser + segment downloader |
| `app/.../download/DownloadOrchestrator.kt` | Bridges `:feature:video-resolver` + `:core:download`; auto-picks best quality |
| `app/.../navigation/AppController.kt:584-599` | 4 inline composite-key duplications for download-state lookup |
| `app/.../navigation/AppController.kt:622` | `getDownloadStates` — filters by `"$anilistId:"` prefix + episodeUrl match |
| `feature/download/.../DownloadViewModel.kt:97` | `groupByAnime` — groups by `anilistId` (survives source switch at library level) |

### A.4 AniList provider (Doc 03)

| File | What's there |
|---|---|
| `core/anilist/.../api/AniListApi.kt` (727 lines) | The de-facto AniList repository (no `AniListRepository` class). 10 public methods. |
| `core/anilist/.../api/AniListRateLimiter.kt` | 80 req/min cap |
| `core/anilist/.../api/LocalAniListCache.kt` | 24h persistent cache (trending, popular, per-id) |
| `core/anilist/.../model/AniListAnime.kt:13-37` | `AniListAnime` model — `id: Int` non-null, `idMal: Int?` |
| `core/anilist/.../details/AniListAnimeMapper.kt` | `toUnifiedAnime(matchedSourceId, matchedSourceName)` |
| `core/tracker/anilist/AniListTrackApi.kt` | The 2nd AniList HTTP client (authenticated, tracker) |
| `feature/backup/.../aniyomi/AniyomiBackupTranslator.kt` (434 lines) | The 3rd AniList client (ad-hoc, for ID resolution during restore) |

### A.5 Module architecture (Doc 04)

| File | What's there |
|---|---|
| `settings.gradle.kts:85` | The phantom `:i18n` declaration (no directory) |
| `core/backup/build.gradle.kts:40` | VIOLATION: `:core:backup → :data:extension` |
| `feature/episode-settings/build.gradle.kts:19` | VIOLATION: `:feature:episode-settings → :feature:anime-details` |
| `feature/watch/build.gradle.kts:18` | VIOLATION: `:feature:watch → :feature:video-resolver` |
| `feature/download/build.gradle.kts:28` | VIOLATION: `:feature:download → :feature:video-resolver` |
| `buildSrc/src/main/kotlin/anikuta.library.gradle.kts` | Convention plugin (base Android library, no Compose) |
| `buildSrc/src/main/kotlin/anikuta.library.compose.gradle.kts` | Convention plugin (adds Compose) |
| `buildSrc/src/main/kotlin/anikuta.android.application.gradle.kts` | Convention plugin (base app) |
| `buildSrc/src/main/kotlin/anikuta.android.application.compose.gradle.kts` | Convention plugin (app + Compose) |
| `buildSrc/src/main/kotlin/anikuta/buildlogic/AndroidConfig.kt` | SDK/NDK/version constants |

### A.6 Extension system (Doc 05)

| File | What's there |
|---|---|
| `core/source-api/build.gradle.kts:1-8` | Android-only (NOT KMP, despite README claim) |
| `core/source-api/.../online/AnimeHttpSource.kt:114-118` | Source ID: `MD5("name/lang/versionId").takeLowest64Bits() and Long.MAX_VALUE` |
| `core/source-api/.../ConfigurableAnimeSource.kt:46-53` | `ExtensionAppHolder` (the one ANIKUTA-specific addition) |
| `data/extension/.../loader/AnimeExtensionLoader.kt:64-195` | Loading pipeline (PackageManager → validate → trust → load → instantiate) |
| `data/extension/.../matcher/SourceMatcher.kt:346-378` | Title normalization + Levenshtein (threshold 0.80) |
| `data/extension/.../SourceLinkStore.kt` | `Map<anilistId.toString(), SourceLinkItem>` |
| `data/extension/.../ExtensionLinkStore.kt` | `Map<"$sourceId:$url", Int(anilistId)>` |
| `feature/video-resolver/.../ResolverService.kt:85-139` | Tries `getHosterList` first, falls back to `getVideoList(episode)` |

### A.7 Data layer (Doc 06)

| File | What's there |
|---|---|
| `core/database/src/main/sqldelight/.../animes.sq:3-39` | `animes` table — `_id` PK, `anilist_id` nullable + partial unique index |
| `core/database/src/main/sqldelight/.../episodes.sq:3-24` | `episodes` table — **missing ADR-024 columns** |
| `core/database/src/main/sqldelight/.../animehistory.sq:3-12` | UNUSED history table |
| `core/database/src/main/sqldelight/.../animetrack.sq:3-16` | Tracker bindings — `anime_id` + `tracker_id` + `remote_id` |
| `core/database/src/main/sqldelight/.../anime_category.sq` | Junction table — `anime_id` + `category_id` |
| `core/database/src/main/sqldelight/.../categories.sq` | Categories table |
| `core/database/src/main/sqldelight/.../1.sqm` | Migration v1→v2 (adds AniList columns + Default category) |
| `core/common/.../repository/TrackRepository.kt` | DEAD CODE (naming collision with `:core:tracker/TrackRepository.kt`) |
| `core/preferences/.../PreferenceStore.kt` | String-keyed, typed via `Preference<T>`, reactive via `changes()` |

---

## B. The composite key `"$anilistId:$episodeUrl"` — all 9+ occurrences

| # | File:line | Context |
|---|---|---|
| 1 | `core/download/.../DownloadTask.kt:41` | The definition: `val key get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"` |
| 2 | `core/download/.../DownloadQueue.kt:309-310` | `keyFor(request)` — duplicated for dedup |
| 3 | `core/download/.../DefaultDownloadManager.kt:203` | Inline in `findTask(anilistId, episodeUrl)` |
| 4 | `core/download/.../DefaultDownloadManager.kt:167` | Inline in `isEpisodeDownloaded` — **the source-switching break** |
| 5 | `navigation/AppController.kt:584` | Inline for download-state lookup |
| 6 | `navigation/AppController.kt:589` | Inline (duplicate) |
| 7 | `navigation/AppController.kt:594` | Inline (duplicate) |
| 8 | `navigation/AppController.kt:599` | Inline (duplicate) |
| 9 | `core/player/.../WatchProgressStore.kt` | Same key shape for watch progress |
| 10 | `core/player/.../PlaybackStateStore.kt` | Same key shape for playback state |

---

## C. The anilistId gates (every `== 0` / `<= 0` / `== null` check)

| File:line | Check | Behavior |
|---|---|---|
| `navigation/AppController.kt:509-512` | `if (anilistId == 0)` | Hard block: Toast "Cannot download — anime not linked", return |
| `navigation/AppController.kt:370` | `if (anilistId != 0 && downloadManager.isEpisodeDownloaded(...))` | Offline short-circuit skipped for anilistId = 0 |
| `core/tracker/.../TrackSyncManager.kt:62` | `if (anilistId <= 0) continue` | Tracker sync skipped |
| `feature/anime-details/.../AnimeDetailViewModel.kt:629-632` | `anime.anilistId ?: return` | Episode metadata enrichment skipped |
| `feature/backup/.../WatchProgressBackupProvider.kt:70` | Import gates on `anilistId > 0` | Unlinked entries dropped |
| `feature/backup/.../CoverImageProvider.kt:44` | Export gates on `anilistId != null` | Unlinked excluded |
| `feature/backup/.../SourceLinkBackupProvider.kt:64` | Gates on anilistId | Orphaned for unlinked |
| `feature/updates/.../UpdatesViewModel.kt:136-140` | Silently skips | Unlinked not in schedule |

---

## D. SQLDelight schema (complete)

### D.1 `animes` table (v2)

```sql
CREATE TABLE animes (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT,
    author TEXT,
    description TEXT,
    genre TEXT,
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
    anilist_id INTEGER,
    cover_color TEXT,
    score REAL,
    total_episodes INTEGER,
    last_watched INTEGER NOT NULL DEFAULT 0,
    next_airing_episode INTEGER
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_anilist_id
ON animes(anilist_id) WHERE anilist_id IS NOT NULL;
```

### D.2 Proposed `2.sqm` migration (v2 → v3)

```sql
ALTER TABLE animes ADD COLUMN watchable_id_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE episodes ADD COLUMN release_date INTEGER;
ALTER TABLE episodes ADD COLUMN last_refresh INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN last_metadata_fetch INTEGER;
ALTER TABLE episodes ADD COLUMN next_episode_check INTEGER;
CREATE UNIQUE INDEX IF NOT EXISTS idx_episodes_anime_epnum ON episodes(anime_id, episode_number);
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_source_url ON animes(source_id, url);
```

### D.3 Other tables

See `analysis/06_data_layer_analysis.md` §2 for `episodes`, `animehistory`, `animetrack`, `anime_category`, `categories` full schemas.

---

## E. Module dependency graph (compact)

```
:app (31 deps) → 12 core + 3 data + 16 feature

:core:common          (0 deps — leaf)
:core:designsystem    → common, preferences
:core:database        (0 deps — leaf)
:core:preferences     (0 deps — leaf)
:core:anilist         → common, preferences
:core:tracker         → common, database, preferences, anilist, player  ⚠ (smell)
:core:episode-metadata → common, preferences
:core:source-api      → (external: OkHttp, Injekt)
:core:player          → common, preferences, database, source-api
:core:update-checker  → common, preferences, anilist, database
:core:download        → common, preferences, source-api
:core:backup          → common, preferences, database, anilist, source-api, data:extension  🔴 VIOLATION
:core:network, :core:notification, :core:source-local  (empty stubs)

:data:anime           → common, database, preferences, data:extension  ⚠ (blurred)
:data:extension       → common, source-api, preferences, database
:data:history         → common, database
:data:manga, :data:tracker  (empty stubs)

:feature:browse       → common, designsystem, anilist, preferences
:feature:library      → common, designsystem, preferences, database, data:anime
:feature:anime-details → common, designsystem, anilist, source-api, preferences, database, data:anime, data:extension
:feature:watch        → common, designsystem, preferences, player, source-api, database, episode-metadata, data:anime, feature:video-resolver  🔴 VIOLATION
:feature:download     → common, designsystem, download, preferences, source-api, feature:video-resolver  🔴 VIOLATION
:feature:episode-settings → common, designsystem, preferences, feature:anime-details  🔴 VIOLATION
:feature:video-resolver → common, designsystem, source-api, preferences
:feature:search       → common, designsystem, anilist, source-api, preferences, data:extension
:feature:history      → common, designsystem, preferences, player, database, data:history
:feature:updates      → common, designsystem, preferences, anilist, database, data:anime
:feature:my           → common, designsystem, preferences, anilist, tracker, database, data:anime
:feature:trackers     → common, designsystem, tracker, preferences
:feature:backup       → common, designsystem, backup, preferences
:feature:extensions-settings → common, designsystem, preferences, data:extension
:feature:settings     → common, designsystem, preferences
:feature:home, :feature:more, :feature:player, :feature:episode-list  (empty stubs)
```

See `analysis/04_module_dependencies.md` for the full graph + violations table.

---

## F. Empty stub modules (9)

| Module | Inbound deps | Removable? |
|---|---|---|
| `:core:network` | 0 | ✅ Yes |
| `:core:notification` | 0 | ✅ Yes (keep if ADR-014 notifications planned) |
| `:core:source-local` | 0 | ✅ Yes (keep if local-files planned) |
| `:data:manga` | 0 | ✅ Yes (keep — ADR-009 manga deferred) |
| `:data:tracker` | 0 | ✅ Yes |
| `:feature:home` | 0 | ✅ Yes (= `:feature:browse`) |
| `:feature:more` | 0 | ✅ Yes (inline in MainActivity) |
| `:feature:player` | 1 (`:app`) | ⚠ Remove `:app` dep first |
| `:feature:episode-list` | 0 | ✅ Yes (= `:feature:anime-details`/EpisodesSection) |

Plus `:i18n` — declared at `settings.gradle.kts:85`, no directory, zero dependents.

---

## G. Preference-key catalog (anilistId-keyed stores flagged)

| Store | Pref key | Key shape | anilistId-keyed? |
|---|---|---|---|
| `WatchProgressStore` | `pref_watch_progress_map` | `"$anilistId:$episodeUrl"` | 🔴 |
| `PlaybackStateStore` | `pref_playback_state_map` | `"$anilistId:$episodeUrl"` | 🔴 |
| `DownloadStore` | `pref_download_tasks_v1` | task.key = `"$anilistId:$episodeUrl"` | 🔴 |
| `EpisodeMetadataCache` | `pref_ep_metadata_cache` | outer `anilistId`, inner `episodeNumber` | 🔴 |
| `SourceLinkStore` | `pref_source_links` | `anilistId.toString()` | 🔴 |
| `ExtensionLinkStore` | `pref_extension_links` | `"$sourceId:$url"` → anilistId | 🔴 |
| Legacy `source_pref_*` | `source_pref_<anilistId>` | `anilistId` | 🔴 |
| `DetailsViewPreferenceStore` | (per-anime) | `anilistId?.toString() ?: "ext:$sourceId:$url"` | ✅ HYBRID |
| `UpdateCheckerPreferences` | `pref_update_last_known` | local `_id` (should be anilistId) | ⚠ |
| `ThemePreferences`, `PlayerPreferences`, `DownloadPreferences`, `EpisodeDisplayPreferences`, `LibraryPreferences`, `SearchUiPreferences`, `ProfilePreferences`, `BackupPreferences` | various | (not anime-keyed) | ❌ |

**7 stores need migration to `WatchableId`.** 1 store (`DetailsViewPreferenceStore`) is the template.

---

## H. Download folder structure (current + proposed)

### Current
```
<USER_PICKED_SAF_FOLDER>/
└── ANIKUTA/                                          ◄── always created (DownloadStorageProvider.kt:111)
    └── downloads/
        └── anime/
            └── <Anime Title> [<anilistId>]/          ◄── anilistId in folder name
                ├── Episode 001/
                │   ├── video.mp4
                │   └── data/
                │       ├── subtitles/<lang>.<ext>
                │       └── metadata.json             ◄── EpisodeMetadataCache
                └── ...
```

### Proposed
```
<USER_PICKED_SAF_FOLDER>/                              ◄── used directly (no ANIKUTA subfolder)
└── <Anime Title> [<watchableId.stableKey()>]/         ◄── source-independent
    ├── Episode 001/
    │   ├── video.mp4
    │   └── data/
    │       ├── subtitles/<lang>.<ext>
    │       └── metadata.json
    └── ...
```

---

## I. The 9-step migration (summary)

| Step | What | Complexity | Risk |
|---|---|---|---|
| 0 | Pre-migration checks | Low | None |
| 1 | Schema migration (`2.sqm`) | Low | Low (additive) |
| 2 | Backfill `watchable_id_json` on `animes` | Low | Low |
| 3 | Re-key `WatchProgressStore` | Medium | Medium (drops anilistId=0 entries) |
| 4 | Re-key `PlaybackStateStore` | Medium | Medium |
| 5 | Re-key `DownloadStore` + move folders | **High** | **High** (file I/O) |
| 6 | Re-key `EpisodeMetadataCache` | Low | Low |
| 7 | Re-key `SourceLinkStore` + `ExtensionLinkStore` | Low | Low |
| 8 | Re-key legacy `source_pref_*` | Low | Low |
| 9 | Mark migration done | Trivial | None |

See `proposals/05_migration_strategy.md` §3 for the full step-by-step.

---

## J. The 10 proposed ADRs (quick index)

| ADR | Title | Doc |
|---|---|---|
| 040 | Typed `WatchableId` (hybrid) | `proposals/01_internal_id_system.md` |
| 041 | Provider abstraction + registry | `proposals/02_provider_abstraction.md` |
| 042 | Download identity: `WatchableId + episodeNumber` | `proposals/03_download_system_redesign.md` |
| 043 | Download folder: user-direct | `proposals/03_download_system_redesign.md` |
| 044 | Extension: parallel modules | `proposals/04_extension_evolution.md` |
| 045 | Migration: automatic + dual-read | `proposals/05_migration_strategy.md` |
| 046 | Module: extract `:core:video-resolver` + `:core:history` | `analysis/04_module_dependencies.md` |
| 047 | Consolidate 3 AniList HTTP clients | `proposals/02_provider_abstraction.md` |
| 048 | Schema: `watchable_id_json` + ADR-024 on episodes | `analysis/06_data_layer_analysis.md` |
| 049 | Empty stub modules: remove or document | `analysis/04_module_dependencies.md` |

Full alternatives + trade-offs: `plan/04_decision_records.md`.

---

## K. Evidence files (raw research)

| File | Lines | Focus |
|---|---|---|
| `_evidence/EVID-01-content-identification.md` | 2,326 | AniList ID usage across the codebase |
| `_evidence/EVID-02A-downloads-data.md` | 805 | Download data models + persistence + storage |
| `_evidence/EVID-02B-downloads-pipeline.md` | 770 | Download pipeline + offline playback + source-switching |
| `_evidence/EVID-03-provider-coupling.md` | 907 | Per-system AniList coupling |
| `_evidence/EVID-04-module-deps.md` | 776 | All 41 module dependencies + violations |
| `_evidence/EVID-05-extensions.md` | 1,216 | Extension system architecture |
| `_evidence/EVID-06-data-layer.md` | 1,520 | SQLDelight schema + preferences + backup |

These are the raw research outputs. The `analysis/` documents are the polished synthesis.

---

*This appendix is a quick-reference index. For full context, read the linked documents.*
