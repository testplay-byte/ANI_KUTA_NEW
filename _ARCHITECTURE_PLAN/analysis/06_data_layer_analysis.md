# 06 — Data Layer Analysis

> **Phase 1 / Current State.** Every SQLDelight `.sq` file, every table, every column, every repository, every preference key, the backup data model, the episode-metadata cache, watch progress, and downloads. Raw evidence: `_evidence/EVID-06-data-layer.md`.

---

## 1. Executive summary

- **6 `.sq` files**, schema version 2 (single migration `1.sqm`). All under `core/database/src/main/sqldelight/app/confused/anikuta/core/database/`.
- **The dual manga/anime schema pattern (ADR-009) is documented but NOT implemented.** There is no `sqldelightanime/` source set — only `sqldelight/`. The single schema serves anime only.
- **ADR-024 status columns** (`release_date`, `last_refresh`, `last_metadata_fetch`, `next_episode_check`): **present on `animes`** ✅ but **ALL FOUR are MISSING on `episodes`** ❌. ARCHITECTURE.md §5 says they must be on "anime/episode tables."
- **AniList-first keying lives in SharedPreferences, not SQLDelight.** The DB uses surrogate `_id` (AUTOINCREMENT) as PK everywhere; `anilist_id` is a partial-unique nullable column on `animes` only. The `episodes`, `animehistory`, `animetrack`, `anime_category` tables have NO `anilist_id` column. The 7 anilistId-keyed preference stores (watch progress, playback state, downloads, episode-metadata cache, source links, extension links, details-view prefs) carry the actual AniList-first identity.
- **The composite key shape `"$anilistId:$episodeUrl"`** is used by 3 stores (`WatchProgressStore`, `PlaybackStateStore`, `DownloadStore`) + the Aniyomi backup translator. Episode metadata cache uses outer `anilistId` + inner `episodeNumber`.
- **`animehistory` SQLDelight table is wired but unused** — live history reads from `WatchProgressStore` (SharedPreferences). The KDoc calls the SQLDelight repos "Phase 0 infrastructure."
- **Two `TrackRepository` interfaces exist** — a dead-code version in `:core:common` and the actually-used version in `:core:tracker`. Naming collision.
- **Backup format (ADR-036):** `.anikuta` = ZIP with `meta.json.gz` + optional `covers/<anilistId>.jpg`. Schema v1. 10 backup providers. Aniyomi restore works via `AniyomiBackupTranslator` (434 lines) which resolves AniList IDs (tracker → MAL → title-search). Source-ID remapping is NOT yet implemented.

---

## 2. SQLDelight `.sq` files — complete schema dump

### 2.1 `animes.sq`

**Path:** `core/database/src/main/sqldelight/.../animes.sq`

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
    status INTEGER NOT NULL,    -- 0=unknown, 1=ongoing, 2=completed, ...
    thumbnail_url TEXT,
    favorite INTEGER NOT NULL DEFAULT 0,
    source_id INTEGER NOT NULL,
    date_added INTEGER NOT NULL,
    viewer_flags INTEGER NOT NULL DEFAULT 0,
    next_update INTEGER NOT NULL DEFAULT 0,
    update_strategy INTEGER NOT NULL DEFAULT 0,
    cover_last_modified INTEGER NOT NULL DEFAULT 0,

    -- Status-tracking columns (ADR-024)
    release_date INTEGER,            -- when the anime was first released
    last_refresh INTEGER NOT NULL DEFAULT 0,    -- last time refreshed from source
    last_metadata_fetch INTEGER,     -- last time metadata was fetched (AniList/ext)
    next_episode_check INTEGER,      -- when to next check for new episodes (ADR-014)

    -- Library columns (Phase A)
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

**AniList-keyed queries:**
```sql
selectByAnilistId:                  SELECT * FROM animes WHERE anilist_id = :anilistId;
selectIdByAnilistId:                SELECT _id FROM animes WHERE anilist_id = :anilistId;
updateFavoriteByAnilistId:          UPDATE animes SET favorite = :favorite, date_added = :dateAdded WHERE anilist_id = :anilistId;
updateLastWatchedByAnilistId:       UPDATE animes SET last_watched = :lastWatched WHERE anilist_id = :anilistId;
updateAnilistMetadataByAnilistId:   UPDATE animes SET title=..., cover_url=..., cover_color=..., score=..., total_episodes=..., next_airing_episode=... WHERE anilist_id = :anilistId;
updatePreferredCoverByAnilistId:    UPDATE animes SET cover_url=..., cover_color=... WHERE anilist_id = :anilistId;
```

**Source-keyed queries (for unlinked extension anime):**
```sql
selectBySourceAndUrl:               SELECT * FROM animes WHERE source_id = :sourceId AND url = :url LIMIT 1;
```

> ⚠️ `selectBySourceAndUrl` has no unique index — it's `LIMIT 1`. Two unlinked extension anime with the same source+url could coexist (a data-integrity gap).

### 2.2 `episodes.sq`

```sql
CREATE TABLE episodes (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    url TEXT,
    name TEXT NOT NULL,
    episode_number REAL NOT NULL,
    scanlator TEXT,
    seen INTEGER NOT NULL DEFAULT 0,
    bookmark INTEGER NOT NULL DEFAULT 0,
    last_second_seen INTEGER NOT NULL DEFAULT 0,
    total_seconds INTEGER NOT NULL DEFAULT 0,
    source_order INTEGER NOT NULL,
    date_fetch INTEGER NOT NULL,
    date_upload INTEGER,
    fillermark TEXT,
    summary TEXT,
    preview_url TEXT,
    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE
);
```

**🔴 ADR-024 VIOLATION:** The `episodes` table has **NONE** of the four status-tracking columns (`release_date`, `last_refresh`, `last_metadata_fetch`, `next_episode_check`). ARCHITECTURE.md §5 (lines 151-163) explicitly requires them on "anime/episode tables."

**No `anilist_id` column.** Episodes are keyed by `anime_id` (= `animes._id`).

**No unique constraint** on `(anime_id, episode_number)` or `(anime_id, url)`. Two episodes of the same anime with the same number could coexist.

### 2.3 `animehistory.sq`

```sql
CREATE TABLE animehistory (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    episode_id INTEGER NOT NULL,
    seen_at INTEGER NOT NULL,
    last_second_seen INTEGER NOT NULL DEFAULT 0,
    UNIQUE(anime_id, episode_id),
    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES episodes(_id) ON DELETE CASCADE
);
```

> ⚠️ **THIS TABLE IS NOT USED.** `HistoryViewModel` reads from `WatchProgressStore` (SharedPreferences), not from this table. The KDoc at `feature/history/.../HistoryViewModel.kt:18-23`: *"We do NOT use `HistoryRepository` ... `WatchProgressStore` is the source of truth."*

### 2.4 `animetrack.sq`

```sql
CREATE TABLE animetrack (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    tracker_id INTEGER NOT NULL,        -- 1=MAL, 2=AniList
    remote_id INTEGER NOT NULL,         -- AniList mediaId OR MAL anime id
    remote_url TEXT,
    last_seen INTEGER NOT NULL DEFAULT 0,
    score REAL NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 0,
    total_episodes INTEGER NOT NULL DEFAULT 0,
    display_score TEXT,
    UNIQUE(anime_id, tracker_id),
    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE
);
```

**No `anilist_id` column.** `anime_id` references `animes._id` (local PK). `remote_id` is the tracker-side ID (AniList mediaId or MAL anime id) — distinct from the local `anilist_id` column on `animes`.

### 2.5 `anime_category.sq`

```sql
CREATE TABLE anime_category (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    category_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (anime_id) REFERENCES animes(_id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(_id) ON DELETE CASCADE
);
```

### 2.6 `categories.sq`

```sql
CREATE TABLE categories (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    category_order INTEGER NOT NULL DEFAULT 0,
    hidden INTEGER NOT NULL DEFAULT 0   -- added in 1.sqm
);
```

### 2.7 Migration `1.sqm` (v1 → v2)

Adds the AniList linkage columns + Phase A library columns + `hidden` category column + Default-category seed to `animes` via `ALTER TABLE`. The partial unique index is created in `1.sqm:19-20`.

---

## 3. DB tables summary

| Table | PK | `anilist_id` column? | ADR-024 columns? | AniList dependency |
|---|---|---|---|---|
| `animes` | `_id` (Long AUTOINCREMENT) | YES — nullable, partial unique index | ✅ All 4 present | MODERATE — secondary lookup key |
| `episodes` | `_id` | NO | ❌ All 4 MISSING | NONE |
| `animehistory` | `_id` + `UNIQUE(anime_id, episode_id)` | NO | n/a | NONE — **table unused** |
| `animetrack` | `_id` + `UNIQUE(anime_id, tracker_id)` | NO — `remote_id` is tracker-side | n/a | NONE at DB layer |
| `anime_category` | `_id` | NO | n/a | NONE |
| `categories` | `_id` | NO | n/a | NONE |

**The DB layer is fully local-PK-based.** AniList-ID dependence is introduced entirely at the repository + store + ViewModel + orchestrator layers — NOT by the schema. This is the single most important enabler for the proposed restructuring: **the schema does not need to change** (except adding the missing ADR-024 columns to `episodes`).

---

## 4. Repository layer

### 4.1 Interfaces in `:core:common`

| Interface | File | Key methods |
|---|---|---|
| `AnimeRepository` | `:core:common/repository/AnimeRepository.kt` | `getById(id): Anime?`, `getByAnilistId(anilistId): Anime?`, `getBySourceAndUrl(sourceId, url): Anime?`, `upsert(anime): Long`, `updateFavorite(id, fav)`, `updateLastWatchedByAnilistId(anilistId, ts)`, `refreshFromAniList(anilistId)` |
| `EpisodeRepository` | `:core:common/repository/EpisodeRepository.kt` | `getByAnimeId(animeId): List<Episode>`, `upsert(episode)`, `upsertAll(episodes)` |
| `HistoryRepository` | `:core:common/repository/HistoryRepository.kt` | `getByAnimeId(animeId): List<History>`, `upsert(history)` — **UNUSED** |
| `CategoryRepository` | `:core:common/repository/CategoryRepository.kt` | `getCategories()`, `addAnimeToCategory(animeId, categoryId)`, ... |
| `TrackRepository` (DEAD) | `:core:common/repository/TrackRepository.kt` | **NOT the one used** — naming collision with `:core:tracker/TrackRepository.kt` |

### 4.2 Implementations

| Impl | Module | Notes |
|---|---|---|
| `AnimeRepositoryImpl` | `:data:anime` | Uses SQLDelight `animes.sq` queries. `updateLastWatchedByAnilistId` silently no-ops for unlinked anime. |
| `EpisodeRepositoryImpl` | `:data:anime` | Uses `episodes.sq`. |
| `CategoryRepositoryImpl` | `:data:anime` | Uses `categories.sq` + `anime_category.sq`. |
| `HistoryRepositoryImpl` | `:data:history` | Uses `animehistory.sq` — **UNUSED** (live history reads from `WatchProgressStore`). |

### 4.3 The dead `TrackRepository` collision

- `:core:common/repository/TrackRepository.kt` — **dead code** (not the one used).
- `:core:tracker/TrackRepository.kt` — the actually-used interface, with different methods.

**Recommendation:** Delete the dead `:core:common` version to avoid confusion.

---

## 5. Domain models (`:core:common/model/`)

8 domain models + `UnifiedAnime` (in `details/`):

| Model | File | Key fields |
|---|---|---|
| `Anime` | `Anime.kt:20-64` | `id: Long`, `anilistId: Int?`, `sourceId: Long`, `url`, `title`, ... (30 fields — see Doc 01 §2) |
| `Episode` | `Episode.kt` | `id: Long`, `animeId: Long`, `url: String?`, `name: String`, `episodeNumber: Float`, `seen: Boolean`, ... |
| `History` | `History.kt` | `id: Long`, `animeId: Long`, `episodeId: Long`, `seenAt: Long`, `lastSecondSeen: Long` |
| `Track` | `Track.kt` | `id: Long`, `animeId: Long`, `trackerId: Long`, `remoteId: Long`, `status: Int`, `score: Double`, ... |
| `Category` | `Category.kt` | `id: Long`, `name: String`, `order: Int`, `hidden: Boolean` |
| `AnimeCategoryLink` | `AnimeCategoryLink.kt` | `animeId: Long`, `categoryId: Long`, `order: Int` |
| `Source` | `Source.kt` | `id: Long`, `name: String`, `lang: String` |
| `UnifiedAnime` | `details/UnifiedAnime.kt` | `source: DataSource`, `anilistId: Int?`, `sourceId: Long?`, ... (see Doc 05 §6.3) |

**AniList-specific fields:** only `Anime.anilistId: Int?` and `UnifiedAnime.anilistId: Int?`. Everything else is generic. The models are well-positioned for the `WatchableId` migration.

---

## 6. `:core:preferences` — PreferenceStore

### 6.1 The PreferenceStore mechanism

**File:** `core/preferences/.../PreferenceStore.kt` + `AndroidPreferenceStore.kt`

- Keyed by `String` keys.
- Typed via `Preference<T>` (generic wrapper).
- Reactive via `Preference.changes(): Flow<T>`.
- Backed by `SharedPreferences`.
- JSON serialization for complex types (via kotlinx.serialization).

### 6.2 The preference-key catalog (by feature, anilistId-keyed ones flagged)

| Feature | Preference class | Keys | anilistId-keyed? |
|---|---|---|---|
| Theme | `ThemePreferences` | themeMode, amoled, accentPreset, customAccentColor, paletteMode, ... | ❌ |
| Player | `PlayerPreferences` | defaultSpeed, skipIntro, skipOutro, ... | ❌ |
| Player (per-anime) | `PlayerEpisodePreferences` | per-anime speed/quality | 🔴 `source_pref_<anilistId>` (legacy) |
| Watch progress | `WatchProgressStore` | `pref_watch_progress_map` (JSON map) | 🔴 `"$anilistId:$episodeUrl"` keys |
| Playback state | `PlaybackStateStore` | `pref_playback_state_map` (JSON map) | 🔴 `"$anilistId:$episodeUrl"` keys |
| Downloads | `DownloadPreferences` | 15 prefs (see Doc 02 §12) | ❌ (but `DownloadStore` keys tasks by anilistId) |
| Downloads (queue) | `DownloadStore` | `pref_download_tasks_v1` (JSON list) | 🔴 task.key = `"$anilistId:$episodeUrl"` |
| Episode metadata cache | `EpisodeMetadataCache` | `pref_ep_metadata_cache` (JSON map) | 🔴 outer `anilistId`, inner `episodeNumber` |
| Episode display | `EpisodeDisplayPreferences` | 17 prefs | ❌ |
| Episode settings | `EpisodeMetadataPreferences` | 5 prefs | ❌ |
| Library | `LibraryPreferences` | grid/list, badges, sort, ... | ❌ |
| Search UI | `SearchUiPreferences` | recent searches, filters | ❌ |
| Profile | `ProfilePreferences` | customization, ... | ❌ |
| Update checker | `UpdateCheckerPreferences` | lastKnownEpisodeCount | ⚠ keyed by local `_id` (should be anilistId for cross-device) |
| Backup | `BackupPreferences` | auto-backup, frequency, ... | ❌ |
| Source links | `SourceLinkStore` | `pref_source_links` (JSON map) | 🔴 `anilistId.toString()` |
| Extension links | `ExtensionLinkStore` | `pref_extension_links` (JSON map) | 🔴 `"$sourceId:$url"` → anilistId |
| Details view prefs | `DetailsViewPreferenceStore` | per-anime display prefs | ✅ HYBRID `anilistId?.toString() ?: "ext:$sourceId:$url"` |

**7 anilistId-keyed stores** (flagged 🔴). These are the stores that must migrate to `WatchableId` in the proposed restructuring. **1 hybrid store** (`DetailsViewPreferenceStore`, flagged ✅) — the template.

### 6.3 The legacy `source_pref_<anilistId>` SharedPreferences

Per-source per-anime settings (e.g., a specific extension's per-anime preferences) use the key `source_pref_<anilistId>`. This is legacy Aniyomi behavior. It's anilistId-keyed and would need migration.

---

## 7. Watch progress (`WatchProgressStore`)

**File:** `core/player/.../WatchProgressStore.kt`

- **Mechanism:** JSON map in SharedPreferences (`pref_watch_progress_map`).
- **Key:** `"$anilistId:$episodeUrl"`.
- **Value:** `Progress(positionSeconds, durationSeconds, title, updatedAt, coverUrl?, animeTitle?, episodeNumber, thumbnailUrl?)`.

**The unused SQLDelight `animehistory` table** is keyed by `(anime_id, episode_id)` — both local DB PKs — which would be AniList-independent. The KDoc calls it "Phase 0 infrastructure." Migrating watch progress from SharedPreferences to SQLDelight would (a) eliminate the anilistId keying, (b) enable proper queries (by date, by anime, by episode), and (c) fix the history-row-openability bug (Doc 01 §5.1).

---

## 8. Episode metadata cache

**File:** `core/episode-metadata/.../EpisodeMetadataCache.kt`

- **Mechanism:** JSON map in SharedPreferences.
- **Outer key:** `anilistId.toString()`.
- **Inner key:** `episodeNumber.toString()`.
- **Value:** `EpisodeMetadata(thumbnailUrl, title, summary, airDate, ...)`.
- **TTL:** 24h.

**Three sources** (each implements `EpisodeMetadataSource`):
1. Jikan/MAL — by MAL ID (resolved via AniList `idMal`).
2. Anikage.cc — by title + episode number.
3. AniList — by AniList ID + episode number.

**Coupling:** The cache is anilistId-keyed, so even Jikan/Anikage lookups require an anilistId. Unlinked anime get no metadata (`AnimeDetailViewModel.kt:629-632`: `anime.anilistId ?: return`).

---

## 9. Download persistence (`DownloadStore`)

**File:** `core/download/.../DownloadStore.kt`

- **Mechanism:** JSON `List<DownloadTask>` in SharedPreferences (`pref_download_tasks_v1`).
- **Throttling:** Max 1 write/sec.
- **Startup:** CANCELLED tasks purged; DOWNLOADING → QUEUED (partial files discarded).
- **Task key:** `task.key = "$anilistId:$episodeUrl"` (the composite key).
- **Task ID:** `task.id: Long` (monotonic, persisted, internal PK).

(See Doc 02 for the full download data-model dump.)

---

## 10. Backup data model

### 10.1 The `.anikuta` format (ADR-036)

- **Container:** ZIP.
- **Contents:** `meta.json.gz` (gzipped JSON of `BackupContainer`) + optional `covers/<anilistId>.jpg`.
- **Schema version:** 1.
- **Providers:** 10 (registered as `List<BackupProvider>` to avoid a historical Koin-overwrite bug).

### 10.2 `BackupContainer` (schema v1)

```kotlin
@Serializable
data class BackupContainer(
    val schemaVersion: Int = 1,
    val library: List<AnimeBackup>? = null,
    val episodes: Map<String, List<EpisodeBackup>>? = null,        // key = anilistId ?: "sourceId:url"
    val watchProgress: Map<String, WatchProgressItem>? = null,     // key = "$anilistId:$episodeUrl"
    val sourceLinks: SourceLinkBackup? = null,
    val tracker: TrackerBackupModel? = null,
    val categories: List<CategoryBackup>? = null,
    val animeCategories: List<AnimeCategoryBackup>? = null,
    val coverImages: Map<String, String>? = null,                  // key = anilistId.toString()
    val preferences: Map<String, String>? = null,
    val episodeMetadata: Map<String, Map<String, EpisodeMetadataBackup>>? = null,  // outer = anilistId, inner = epNum
)
```

### 10.3 The 10 backup providers

| Provider | Entry | Unlinked behavior |
|---|---|---|
| `LibraryBackupProvider` | `AnimeBackup(anilistId: Long?)` | ✅ Included (fallback to sourceId+url) |
| `EpisodesBackupProvider` | `Map<key, List<EpisodeBackup>>` | ✅ Included |
| `WatchProgressBackupProvider` | `Map<String, WatchProgressItem>` | ❌ Import gates on `anilistId > 0` |
| `SourceLinkBackupProvider` | `SourceLinkBackup(...)` | ⚠ Orphaned for unlinked |
| `TrackerBackupProvider` | `TrackerBackupModel(...)` | ❌ `animeId` not remapped across devices |
| `CategoryBackupProvider` | `List<CategoryBackup>` + `List<AnimeCategoryBackup>` | ⚠ Falls back to local `_id` |
| `CoverImageProvider` | `Map<anilistId.toString(), coverUrl>` | ❌ Export gates on `anilistId != null` |
| `PreferencesBackupProvider` | `Map<String, String>` | ✅ Bulk dump |
| `EpisodeMetadataBackupProvider` | `Map<anilistId.toString(), Map<epNum, item>>` | ❌ Unlinked excluded |
| `ExtensionLinksBackupProvider` | (part of SourceLinkBackup) | ⚠ Orphaned for unlinked |

### 10.4 Aniyomi restore (`AniyomiBackupTranslator`)

**File:** `feature/backup/.../aniyomi/AniyomiBackupTranslator.kt` (434 lines)

- Reads `.tachibk` (gzip + protobuf).
- Restore-only (never exports Aniyomi format — ADR-036).
- Resolves AniList IDs via: tracker binding → `searchByMalId` → `searchByTitle`.
- **Source-ID remapping (Aniyomi source IDs → ANIKUTA extension source IDs) is NOT yet implemented.**
- Its only external dependency is `AniListApi` — if AniList is unavailable, Aniyomi restore dies.

### 10.5 The cross-device identity problem

Because `animes._id` is AUTOINCREMENT and device-specific, the backup cannot rely on it for cross-device restore. It uses `anilistId` instead — but this only works for *linked* anime. Unlinked extension anime fall back to `sourceId + url`, which is stable across devices **only if the same extension is installed**.

**The tracker-binding mismatch:** Tracker bindings serialize `animeId = animes._id` (device-specific). On restore, this `_id` doesn't match the target device's `_id`. The Aniyomi-translated path sets `animeId = anilistId.toLong()` — a mismatch with the local `_id` convention. **Tracker bindings are effectively broken across devices for non-AniList-keyed anime.**

---

## 11. Primary-key map (per entity, today)

| Entity | DB PK | Natural key | AniList-keyed? |
|---|---|---|---|
| Anime | `animes._id` (AUTOINCREMENT) | `anilist_id` (nullable) OR `source_id + url` | Partially |
| Episode | `episodes._id` (AUTOINCREMENT) | `(anime_id, episode_number)` OR `(anime_id, url)` — **no unique constraint** | No (via `anime_id`) |
| History | `animehistory._id` — **UNUSED** | `(anime_id, episode_id)` UNIQUE | No — but live history uses `"$anilistId:$episodeUrl"` in prefs |
| Track | `animetrack._id` | `(anime_id, tracker_id)` UNIQUE | No (via `anime_id`) |
| Category | `categories._id` | `name` | No |
| AnimeCategoryLink | `anime_category._id` | `(anime_id, category_id)` — no unique constraint | No |
| DownloadTask | `task.id` (monotonic) | `task.key = "$anilistId:$episodeUrl"` | 🔴 Yes (composite key) |
| WatchProgress | (no DB row — prefs) | `"$anilistId:$episodeUrl"` | 🔴 Yes |
| PlaybackState | (no DB row — prefs) | `"$anilistId:$episodeUrl"` | 🔴 Yes |
| EpisodeMetadata | (no DB row — prefs) | `anilistId` + `episodeNumber` | 🔴 Yes |
| SourceLink | (no DB row — prefs) | `anilistId.toString()` | 🔴 Yes |
| ExtensionLink | (no DB row — prefs) | `"$sourceId:$url"` → anilistId | Hybrid |

---

## 12. Data-layer issues + recommendations

| # | Issue | Severity | Recommendation |
|---|---|---|---|
| 1 | ADR-024 status columns missing on `episodes` | Medium | Add them + `2.sqm` migration (or update ARCHITECTURE.md §5 to say "anime table only") |
| 2 | `animehistory` table unused | Low | Either activate it (move watch progress to SQLDelight) or remove it |
| 3 | Dead `TrackRepository` in `:core:common` | Low | Delete it (naming collision with `:core:tracker`) |
| 4 | No unique constraint on `episodes(anime_id, episode_number)` | Medium | Add one (prevents duplicate episodes per anime per source) |
| 5 | `UpdateCheckerPreferences.lastKnownEpisodeCount` keyed by local `_id` | Low | Should be keyed by anilistId (or `WatchableId`) for cross-device consistency |
| 6 | Aniyomi source-ID remapping not implemented | Medium | Implement in `AniyomiBackupTranslator` |
| 7 | Dual manga/anime schema (ADR-009) not implemented | Low | Either implement `sqldelightanime/` or update ADR-009 |
| 8 | 7 anilistId-keyed preference stores | High | Migrate to `WatchableId` (see proposal 01) |
| 9 | Tracker bindings serialize `animes._id` (device-specific) | High | Serialize `anilistId` or `WatchableId` instead |
| 10 | `selectBySourceAndUrl` has no unique index | Low | Add one |

---

## 13. Conclusion

The data layer reveals the **paradox at the heart of the AniList coupling**:

- **The SQLDelight schema is AniList-optional.** All 6 tables use local `_id` as PK. `anilist_id` is a nullable secondary column. The schema already supports unlinked anime.
- **But the cross-cutting preference stores are AniList-required.** 7 stores key by non-nullable `anilistId`. These are the systems that silently exclude or break for unlinked anime.

**The restructuring implications:**

1. **The schema barely needs to change.** Add the missing ADR-024 columns to `episodes`. Add unique constraints. That's it.
2. **The big work is migrating the 7 preference stores** to `WatchableId` keys. This is the bulk of the identity-layer restructuring.
3. **Moving watch progress from SharedPreferences to SQLDelight** (`animehistory` table) would (a) eliminate anilistId keying, (b) enable proper queries, (c) fix the history-row-openability bug, and (d) activate currently-unused schema. This is a high-value, medium-effort change.
4. **The backup format needs a stable cross-device identity.** `anilistId` works for linked anime; `sourceId + url` works only if the same extension is installed. A deterministic `WatchableId` (or a deterministic component of it) would close this gap.
5. **Tracker bindings must stop serializing `animes._id`.** Serialize `WatchableId` instead, and remap on restore.

These findings drive `proposals/01_internal_id_system.md`, `proposals/02_provider_abstraction.md`, `proposals/03_download_system_redesign.md`, and `proposals/05_migration_strategy.md`.

---

*Evidence source: `_evidence/EVID-06-data-layer.md` (1,520 lines).*
