# EVID-01 — Content Identification: Where AniList ID Is (and Isn't) the Primary Key

**Task ID:** EVID-01-CONTENT-ID
**Agent:** Explore (research-only)
**Scope:** ANIKUTA Android project at `ANIKUTA_PROJECT/ANIKUTA/`
**Method:** Direct code reading. Every claim is cited `file:line`.

---

## 0. TL;DR — the central tension

ANIKUTA has **two parallel identity systems** that don't always agree:

| Identity | Where it's the PK | Where it's a nullable column | Where it's a composite-key component |
|---|---|---|---|
| `animes._id` (local DB Long) | All SQLDelight tables (`animes`, `episodes`, `animehistory`, `animetrack`, `anime_category`) | — | — |
| `anilistId` (Int?) | `WatchProgressStore`, `PlaybackStateStore`, `DownloadTask`, `EpisodeMetadataCache`, `SourceLinkStore`, `DetailsViewPreferenceStore`, per-anime `source_pref_<anilistId>` SharedPreferences | `animes.anilist_id` (nullable; partial unique index only when NOT NULL) | `"${anilistId}:${episodeUrl}"` (used everywhere progress/downloads/history-sync is keyed) |
| `sourceId + url` (Long, String) | Unlinked extension anime only (fallback when `anilistId == null`) | — | ExtensionLinkStore key `"${sourceId}:${animeUrl}"`; DetailsViewPreferenceStore key `"ext:${sourceId}:${url}"` |

**The single most important fact:** `anilistId` is `Int?` (nullable) on the canonical `Anime` domain model, but **non-nullable (`Int`) on every cross-cutting store** (watch progress, playback state, downloads, episode metadata, tracker sync). Every cross-cutting store silently excludes unlinked extension anime, and `AppController.downloadEpisode` hard-blocks them with a Toast. This is the architectural crack.

---

## 1. The `Anime` domain model — full definition

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
    // Library columns (Phase A)
    val anilistId: Int?,             // ← nullable. The whole architecture hinges on this.
    val coverColor: String?,
    val score: Double?,
    val totalEpisodes: Int?,
    val lastWatched: Long,
    val nextAiringEpisode: Int?,
) {
    val releasedEpisodes: Int? get() = when {
        nextAiringEpisode != null && nextAiringEpisode > 0 -> nextAiringEpisode - 1
        totalEpisodes != null -> totalEpisodes
        else -> null
    }
}
```

**Interpretation:** `anilistId` is `Int?` (nullable) with no default — callers must explicitly pass null. Per the KDoc on line 14: *"`anilistId` — the AniList media ID, used to link WatchProgressStore entries."* The library DB schema (§2) enforces a partial unique index only on non-null `anilist_id`.

**Companion object:** `AnimeStatus` (lines 67-75) defines int constants 0-6. **No "missing anilistId" sentinel is defined anywhere in the model.**

---

## 2. Database schema — every table, every `anilistId`-related column

**All `.sq` files live in `core/database/src/main/sqldelight/app/confused/anikuta/core/database/`.** There are NO per-module `.sq` files anywhere else in the codebase (confirmed via Glob).

### 2.1 `animes.sq` — the library table

**File:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animes.sq:3-39`

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

**Key findings:**
- `_id` (INTEGER AUTOINCREMENT) is the **primary key** — NOT `anilist_id`.
- `anilist_id` is **nullable**, no NOT NULL, no default.
- A **partial unique index** (line 38-39) enforces uniqueness *only when `anilist_id IS NOT NULL`*. Two unlinked extension anime can coexist; a linked one can't be duplicated.
- `source_id + url` has **no unique index** — `selectBySourceAndUrl` (line 59-60) is `LIMIT 1`, which is the practical "find the first" lookup.

**Queries keyed by anilistId (lines 47-51, 98-99, 113-114, 116-124, 129-133):**
```sql
selectByAnilistId:        SELECT * FROM animes WHERE anilist_id = :anilistId;
selectIdByAnilistId:      SELECT _id FROM animes WHERE anilist_id = :anilistId;
updateFavoriteByAnilistId:UPDATE animes SET favorite = :favorite, date_added = :dateAdded WHERE anilist_id = :anilistId;
updateLastWatchedByAnilistId: UPDATE animes SET last_watched = :lastWatched WHERE anilist_id = :anilistId;
updateAnilistMetadataByAnilistId: UPDATE animes SET title=..., cover_url=..., cover_color=..., score=..., total_episodes=..., next_airing_episode=... WHERE anilist_id = :anilistId;
updatePreferredCoverByAnilistId:  UPDATE animes SET cover_url=..., cover_color=... WHERE anilist_id = :anilistId;
```
*Interpretation:* Every "by anilistId" update silently no-ops if the row doesn't exist (SQLite `UPDATE` on zero rows is a no-op, not an error). This is a hidden failure mode when an unlinked extension anime is watched — the `last_watched` bump goes nowhere.

### 2.2 `episodes.sq` — episode table

**File:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/episodes.sq:3-24`

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
**No `anilist_id` column.** Episodes are keyed by `anime_id` (= `animes._id`, NOT anilistId). The bridge from "anilistId" to "episodes for that anime" goes through `AnimeRepository.getByAnilistId(anilistId) → anime._id → EpisodeRepository.getByAnimeId(_id)`.

### 2.3 `animehistory.sq` — watch history table

**File:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animehistory.sq:3-12`

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
**No `anilist_id` column.** Keyed by `(anime_id, episode_id)` — both referencing local DB PKs.

> ⚠️ **THIS TABLE IS NOT USED.** `HistoryViewModel` reads from `WatchProgressStore` (a JSON-in-SharedPreferences map keyed by `"${anilistId}:${episodeUrl}"`), NOT from this table. The KDoc at `feature/history/src/main/java/app/confused/anikuta/feature/history/HistoryViewModel.kt:18-23` is explicit:
> > "We do NOT use `HistoryRepository` (the SQLDelight-backed `animehistory` table) — per the project's current architecture, `WatchProgressStore` is the source of truth for AniList-keyed progress until source URLs are fully resolved."

### 2.4 `animetrack.sq` — tracker bindings table

**File:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/animetrack.sq:3-16`

```sql
CREATE TABLE animetrack (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    anime_id INTEGER NOT NULL,
    tracker_id INTEGER NOT NULL,
    remote_id INTEGER NOT NULL,
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
**No `anilist_id` column.** `anime_id` references `animes._id` (local PK). `remote_id` is the tracker-side ID (AniList mediaId or MAL anime id) — distinct from the local `anilist_id` column.

### 2.5 `anime_category.sq` — junction table

**File:** `core/database/src/main/sqldelight/app/confused/anikuta/core/database/anime_category.sq:3-10`

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
**No `anilist_id` column.** Keyed by `anime_id` (= `animes._id`).

### 2.6 `categories.sq` + `1.sqm` migration

`categories.sq:3-9` — `_id` PK, no anilist reference.
`1.sqm:12-17` — migration adds `anilist_id` (and other Phase A columns) to `animes` via `ALTER TABLE`. The partial unique index is created in `1.sqm:19-20` (same statement as `animes.sq:38-39`).

### 2.7 DB tables summary

| Table | PK | `anilist_id` column? | AniList dependency |
|---|---|---|---|
| `animes` | `_id` (Long AUTOINCREMENT) | YES — nullable, partial unique index | MODERATE — used as secondary lookup key, NOT the PK |
| `episodes` | `_id` | NO — `anime_id` → `animes._id` | NONE |
| `animehistory` | `_id` + `UNIQUE(anime_id, episode_id)` | NO | NONE — **but the table is unused; progress lives in prefs** |
| `animetrack` | `_id` + `UNIQUE(anime_id, tracker_id)` | NO — `remote_id` is the tracker-side ID | NONE at DB layer |
| `anime_category` | `_id` | NO | NONE |
| `categories` | `_id` | NO | NONE |

**The DB layer is fully local-PK-based.** AniList-ID dependence is introduced entirely at the repository + store + ViewModel + orchestrator layers — NOT by the schema.

---

## 3. Discovery flow — Browse + Search

### 3.1 BrowseScreen — AniList-only, identified by `AniListAnime.id`

**File:** `feature/browse/src/main/java/app/confused/anikuta/feature/browse/BrowseScreen.kt:62-65`

```kotlin
@Composable
fun BrowseScreen(
    api: AniListApi,
    onOpenAnime: (Int) -> Unit = {},    // ← the AniList ID is the only identity propagated forward
)
```

Line 164: `AnimeCard(anime = item, onClick = { onOpenAnime(item.id) })` — `item` is `AniListAnime`, `item.id` is `Int` (the AniList media ID).

**Source of `AniListAnime`:** `core/anilist/src/main/java/app/confused/anikuta/core/anilist/model/AniListAnime.kt:13-37` — `val id: Int` is the AniList media ID, non-nullable. Other fields are nullable; `idMal: Int?` (line 36) is the MAL cross-reference.

**Wire-up:** `app/src/main/java/app/confused/anikuta/navigation/Destinations.kt:48-57`:
```kotlin
object BrowseTabDestination : Screen {
    @Composable override fun Content() {
        val appController = koinInject<AppController>()
        BrowseScreen(
            api = appController.anilistApi,
            onOpenAnime = { id -> appController.pushDetail(id) },   // ← Voyager push, id is the AniList media ID
        )
    }
}
```

**Interpretation:** BrowseScreen is AniList-only. It never sees an unlinked extension anime. Identity = `AniListAnime.id` (non-nullable Int).

### 3.2 SearchScreen — dual-source, two identity types

**File:** `feature/search/src/main/java/app/confused/anikuta/feature/search/viewmodel/SearchViewModel.kt:41-51`

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

**Interpretation:** Search results are NOT unified into a single type. An AniList result carries `anime.id`; an Extension result carries only `source + sAnime` (the Aniyomi `SAnime` interface has `url: String`, `title: String`, NO `anilistId`). The UI branches on the sealed type at `SearchScreen.kt:100-104`:
```kotlin
onResultTap = { result ->
    when (result) {
        is SearchResult.AniList -> onOpenAnime(result.id)
        is SearchResult.Extension -> onOpenExtensionResult(result)
    }
}
```

### 3.3 Extension→AniList linking flow

**Trigger:** `app/src/main/java/app/confused/anikuta/navigation/Destinations.kt:82-85`:
```kotlin
onOpenExtensionResult = { result ->
    appController.startLinking(result.source, result.sAnime)
}
```

`AppController.startLinking` (line 295-297):
```kotlin
fun startLinking(source: AnimeCatalogueSource, sAnime: SAnime) {
    linkingTarget = source to sAnime
}
```
Just stashes the target; `ExtensionLinkingSheet` is rendered by `AnikutaRoot` and constructs `ExtensionLinkingViewModel`.

### 3.4 `ExtensionLinkingViewModel` — the linking state machine

**File:** `feature/search/src/main/java/app/confused/anikuta/feature/search/viewmodel/ExtensionLinkingViewModel.kt:32-61`

```kotlin
sealed class ExtensionLinkingState {
    data object Loading : ExtensionLinkingState()
    data class Linked(val anilistId: Int, val wasCached: Boolean = false) : ExtensionLinkingState()
    data class NeedsManualLink(val results: List<AniListAnime>, val error: String? = null) : ExtensionLinkingState()
    data class GoWithoutLinking(val source: AnimeCatalogueSource, val sAnime: SAnime) : ExtensionLinkingState()
}
```

**`attemptLink()` flow (lines 103-138):**

1. **Cache check first (line 105):** `linkStore.getAniListId(source.id, sAnime.url)` — if ExtensionLinkStore has a cached link, emit `Linked(cached, wasCached=true)` and SKIP the sheet entirely (line 108-110).
2. **Auto-search AniList (lines 116-118):** `anilistApi.searchAnime(sAnime.title, perPage = 10)` — searches by the extension's title.
3. **Auto-link the first result (lines 119-125):** If results non-empty, `linkStore.link(source.id, sAnime.url, best.id)` and emit `Linked(best.id)` — auto-linking is silent and unconditional. There's **no confidence threshold** — the first AniList search result wins, even if it's a wrong match.
4. **No results → NeedsManualLink (lines 126-128).**
5. **API error → NeedsManualLink with error (lines 130-136).**

**Manual flow:**
- `selectManual(anime)` (line 161-165): `linkStore.link(source.id, sAnime.url, anime.id)` → `Linked(anime.id)`.
- `goWithoutLinking()` (line 168-170): emits `GoWithoutLinking(source, sAnime)` — **does NOT write to ExtensionLinkStore**. The anime remains unlinked.

**Interpretation:** Auto-linking happens on the FIRST search result with no confidence check. The user can override via the sheet OR choose "go without linking" — but the latter leaves NO trace in ExtensionLinkStore, so the next time the user opens the same extension anime, the auto-link search fires again.

### 3.5 `ExtensionLinkStore` — persistence

**File:** `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/ExtensionLinkStore.kt:34-115`

```kotlin
class ExtensionLinkStore(private val preferenceStore: PreferenceStore) {
    // Key format: "$sourceId:$animeUrl" → AniList ID (Int)
    private val store = preferenceStore.getObject(
        key = KEY,                                 // "pref_extension_anilist_links" (line 113)
        defaultValue = emptyMap<String, Int>(),
        ...
    )
    private fun key(sourceId: Long, animeUrl: String) = "$sourceId:$animeUrl"

    fun getAniListId(sourceId: Long, animeUrl: String): Int? = store.get()[key(sourceId, animeUrl)]
    fun link(sourceId: Long, animeUrl: String, anilistId: Int) { ... }
    fun unlink(sourceId: Long, animeUrl: String) { ... }

    /** Reverse lookup: anilistId → sourceId (parses the key's first segment). */
    fun getPreferredSourceForAnilist(anilistId: Int): Long? { ... }   // line 84-90
    fun getAll(): Map<String, Int> = store.get()                       // line 93
}
```

**Interpretation:** ExtensionLinkStore is a `Map<String, Int>` (JSON in SharedPreferences). Key = `"$sourceId:$animeUrl"`, value = `anilistId` (non-nullable Int — once linked, always linked). **There is no entry for unlinked anime.** The reverse-lookup `getPreferredSourceForAnilist` (line 84) iterates the map's values — linear scan, not indexed.

### 3.6 `SourceLinkStore` — the reverse direction

**File:** `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/SourceLinkStore.kt:22-69`

```kotlin
class SourceLinkStore(private val preferenceStore: PreferenceStore) {
    @Serializable
    data class SourceLink(val sourceId: Long, val animeUrl: String, val animeTitle: String)

    // Key: anilistId (Int, as String). Value: SourceLink.
    private val store = preferenceStore.getObject(
        key = KEY,                                 // "pref_source_links" (line 67)
        defaultValue = emptyMap<String, SourceLink>(),
        ...
    )
    fun getLink(anilistId: Int): SourceLink? = store.get()[anilistId.toString()]      // line 45
    fun saveLink(anilistId: Int, sourceId: Long, animeUrl: String, animeTitle: String) { ... }  // line 48
    fun removeLink(anilistId: Int) { ... }                                            // line 55
    fun getAll(): Map<String, SourceLink> = store.get()                              // line 62
}
```

**Interpretation:** SourceLinkStore is the **reverse** of ExtensionLinkStore: keyed by anilistId (as String), value is `(sourceId, animeUrl, animeTitle)`. The pair of stores is a denormalized bidirectional map. They are kept in sync manually — `AppController.switchAnilistAnime` (line 277-293) moves a source link from old anilistId to new, then calls `extensionLinkStore.link(...)` to update the reverse map.

### 3.7 `DetailsViewPreferenceStore` — per-anime view pref (linked vs unlinked keys)

**File:** `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/DetailsViewPreferenceStore.kt:33-95`

Keys (lines 17-18):
- **Linked anime:** `anilistId.toString()` (e.g. `"12345"`)
- **Unlinked extension anime:** `"ext:${sourceId}:${url}"` (e.g. `"ext:6789:anime/some-url"`)

API:
- `get(anilistId: Int): DataSource?` — line 52
- `get(sourceId: Long, url: String): DataSource?` — line 55
- `set(anilistId: Int, dataSource: DataSource)` — line 58
- `set(sourceId: Long, url: String, dataSource: DataSource)` — line 65
- `remove(anilistId: Int)` — line 72

**Interpretation:** This store DOES handle unlinked anime — but via a separate key namespace (`"ext:..."`). The dual-key design is the only place where unlinked extension anime have a parallel persistence path.

---

## 4. Library entry flow

### 4.1 `AnimeRepository` interface — anilistId methods

**File:** `core/common/src/main/java/app/confused/anikuta/core/common/repository/AnimeRepository.kt:17-77`

```kotlin
interface AnimeRepository {
    fun observeAll(): Flow<List<Anime>>
    fun observeFavorites(): Flow<List<Anime>>
    fun observeById(id: Long): Flow<Anime?>
    fun observeBySource(sourceId: Long): Flow<List<Anime>>
    fun observeByAnilistId(anilistId: Int): Flow<Anime?>                       // line 27

    suspend fun getById(id: Long): Anime?
    suspend fun getByAnilistId(anilistId: Int): Anime?                         // line 31
    suspend fun getBySourceAndUrl(sourceId: Long, url: String): Anime?         // line 33
    suspend fun searchByName(query: String): List<Anime>
    suspend fun upsert(anime: Anime): Long                                     // line 37

    suspend fun updateFavorite(id: Long, favorite: Boolean, dateAdded: Long)
    suspend fun updateFavoriteByAnilistId(anilistId: Int, favorite: Boolean, dateAdded: Long)   // line 41

    suspend fun updateLastRefresh(id: Long, lastRefresh: Long)
    suspend fun updateLastMetadataFetch(id: Long, lastMetadataFetch: Long)
    suspend fun updateNextEpisodeCheck(id: Long, nextEpisodeCheck: Long?)
    suspend fun updateLastWatched(id: Long, lastWatched: Long)
    suspend fun updateLastWatchedByAnilistId(anilistId: Int, lastWatched: Long)  // line 51

    suspend fun updateAnilistMetadata(
        anilistId: Int,                                                          // line 53-61
        title: String, coverUrl: String?, coverColor: String?,
        score: Double?, totalEpisodes: Int?, nextAiringEpisode: Int?,
    )
    suspend fun updatePreferredCoverByAnilistId(anilistId: Int, coverUrl: String?, coverColor: String?)   // line 68
    suspend fun updatePreferredCoverBySourceAndUrl(sourceId: Long, url: String, coverUrl: String?, coverColor: String?)  // line 74
    suspend fun delete(id: Long)
}
```

**Interpretation:** The repository exposes **two parallel APIs**: `*ById` (using `animes._id`) and `*ByAnilistId` (using `anilist_id`). The latter is used by `WatchProgressStore` consumers + `LibraryViewModel.updateLastWatched` (which gets `anilistId` from `ContinueWatchingItem`). **Critically: `updateLastWatchedByAnilistId` silently no-ops for unlinked anime** (the UPDATE hits zero rows).

### 4.2 `AnimeRepositoryImpl` — how the parallel API maps to SQLDelight

**File:** `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeRepositoryImpl.kt`

- `getByAnilistId` (line 58-60): `selectByAnilistId(anilistId.toLong(), AnimeMapper::map).executeAsOneOrNull()`
- `observeByAnilistId` (line 49-52): same query, as a Flow.
- `updateFavoriteByAnilistId` (line 145-152): `updateFavoriteByAnilistId(anilistId = anilistId.toLong(), ...)`.
- `updateLastWatchedByAnilistId` (line 170-175): `updateLastWatchedByAnilistId(anilistId = anilistId.toLong(), ...)`.
- `updateAnilistMetadata` (line 177-195): `updateAnilistMetadataByAnilistId(anilistId = anilistId.toLong(), ...)`.
- `updatePreferredCoverByAnilistId` (line 197-205): `updatePreferredCoverByAnilistId(anilistId = anilistId.toLong(), ...)`.
- `upsert` (line 70-134): on insert (line 124) and update (line 93), `anilistId = anime.anilistId?.toLong()` — nullable handled by SQLDelight's Long? parameter.

**Interpretation:** `Int? → Long?` conversion happens at the repository boundary. The Int? from the domain model becomes a Long? in SQLDelight. There's no validation that anilistId > 0; `Anime(anilistId = 0)` would round-trip as Long(0) and collide with anything else that's 0 — but the partial unique index `WHERE anilist_id IS NOT NULL` would treat 0 as a valid (colliding) value.

### 4.3 `AnimeMapper` — column-by-column mapping

**File:** `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeMapper.kt:16-73`

The mapper signature takes **26 positional Long/String parameters** matching the `animes` table column order. Line 38: `anilistId: Long?` (the SQLDelight column type) → line 66: `anilistId = anilistId?.toInt()` (the domain model type).

The test `AnimeMapperTest.kt:43` confirms `anilistId = 12345L` → `12345` and `AnimeMapperTest.kt:85` confirms `null` round-trips.

### 4.4 Library add path — `AnimeDetailViewModel.toggleSave` / `saveAnimeToLibrary`

**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt:451-471` (toggleSave)
**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt:688-729` (saveAnimeToLibrary)

```kotlin
private suspend fun saveAnimeToLibrary(anime: UnifiedAnime): Long {
    val now = System.currentTimeMillis()
    val newAnime = Anime(
        id = 0,
        url = anime.url,
        title = anime.title,
        ...
        anilistId = anime.anilistId,     // line 719 — nullable; null for unlinked extension anime
        coverColor = anime.coverColorHex,
        score = anime.averageScore?.toDouble(),
        totalEpisodes = anime.episodeCount,
        ...
    )
    val id = animeRepository.upsert(newAnime)
    categoryRepository.setAnimeCategories(id, listOf(Category.DEFAULT_ID))
    return id
}
```

**Library lookup at `findLibraryAnime` (line 650-658):**
```kotlin
private suspend fun findLibraryAnime(anime: UnifiedAnime): Anime? {
    val anilistId = anime.anilistId
    val sourceId = anime.sourceId
    return when {
        anilistId != null -> animeRepository.getByAnilistId(anilistId)
        sourceId != null -> animeRepository.getBySourceAndUrl(sourceId, anime.url)
        else -> null
    }
}
```

**Interpretation:** Library entries for unlinked extension anime ARE persisted (`anilistId = null`), and CAN be found again via `getBySourceAndUrl`. This is the *only* system that fully supports both linked and unlinked anime at the DB layer. But the **reactive observer** at line 668-685 falls back to polling for unlinked anime (no `observeBySourceAndUrl` Flow exists):
```kotlin
private fun observeLibraryState() {
    viewModelScope.launch {
        val anilistId = currentAnilistId()
        if (anilistId != null) {
            animeRepository.observeByAnilistId(anilistId).collect { anime -> _isSaved.value = anime?.favorite == true }
        } else {
            // Unlinked extension anime — refresh the saved flag after each load.
            animeState.collect { state -> if (state is DetailState.Success) { ... } }
        }
    }
}
```

### 4.5 Library open path — `AppController.openLibraryAnime`

**File:** `app/src/main/java/app/confused/anikuta/navigation/AppController.kt:197-219`

```kotlin
fun openLibraryAnime(anime: app.confused.anikuta.core.common.model.Anime) {
    val anilistId = anime.anilistId
    if (anilistId != null) {
        pushDetail(anilistId)                  // → AnimeDetailDestination(animeId = anilistId)
        return
    }
    // Unlinked extension anime — resolve the source.
    val source = sourceMatcher.getSourceById(anime.sourceId)
    if (source != null) {
        val sAnime = eu.kanade.tachiyomi.animesource.model.SAnimeImpl().apply {
            url = anime.url
            title = anime.title
        }
        pushExtensionDetail(source, sAnime, anilistId = null)   // → ExtensionAnimeDetailDestination(..., anilistId = null)
    } else {
        android.widget.Toast.makeText(context, "Source no longer installed for '${anime.title}'", ...).show()
    }
}
```

**Interpretation:** Opening a library anime branches on `anilistId != null`. **Failure mode for unlinked anime:** if the extension source is no longer installed, the user gets a Toast and is stuck on the library page — there is no fallback to push an AniList-detail page (`anilistId = 0` would just show an error state per the KDoc at line 192: *"falls back to pushing the AniList details page with `anilistId = 0` (which will show an error state)"* — but the code does NOT actually do this fallback, it just shows the Toast).

---

## 5. Details page flow

### 5.1 `DetailsRequest` — the unified identity type

**File:** `core/common/src/main/java/app/confused/anikuta/core/common/model/details/DetailsRequest.kt:14-41`

```kotlin
sealed interface DetailsRequest {
    data class ByAniListId(val anilistId: Int) : DetailsRequest                       // line 20

    data class ByExtension(
        val sourceId: Long,
        val animeUrl: String,
        val animeTitle: String,
        val anilistId: Int? = null,                                                    // line 39 — nullable
    ) : DetailsRequest
}
```

**Interpretation:** `ByAniListId.anilistId` is non-null. `ByExtension.anilistId` is nullable (null = unlinked). This is the canonical identity type for the unified details page.

### 5.2 `AnimeDetailViewModel` — entry mode + initial request

**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailScreen.kt:96-107`

```kotlin
val initialRequest: DetailsRequest = remember(animeId, extensionSource, extensionSAnime) {
    when {
        animeId != null -> DetailsRequest.ByAniListId(animeId)
        extensionSource != null && extensionSAnime != null -> DetailsRequest.ByExtension(
            sourceId = extensionSource.id,
            animeUrl = extensionSAnime.url,
            animeTitle = extensionSAnime.title,
            anilistId = extensionAnilistId,
        )
        else -> error("AnimeDetailScreen requires either animeId or (extensionSource + extensionSAnime)")
    }
}
```

**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt:530-545`

```kotlin
private fun initialDataSource(): DataSource {
    val pref = when (initialRequest) {
        is DetailsRequest.ByAniListId -> viewPreferenceStore.get(initialRequest.anilistId)
        is DetailsRequest.ByExtension -> {
            viewPreferenceStore.get(initialRequest.sourceId, initialRequest.animeUrl)
                ?: initialRequest.anilistId?.let { viewPreferenceStore.get(it) }       // line 536 — linked ext anime fall back to anilistId-keyed pref
        }
    }
    if (pref != null) return pref
    return when (initialRequest) {
        is DetailsRequest.ByAniListId -> DataSource.ANILIST
        is DetailsRequest.ByExtension -> DataSource.EXTENSION
    }
}
```

### 5.3 `AnimeDetailViewModel.currentAnilistId()` — the resolver

**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt:580-588`

```kotlin
private fun currentAnilistId(): Int? {
    val req = activeRequest
    return when (req) {
        is DetailsRequest.ByAniListId -> req.anilistId
        is DetailsRequest.ByExtension -> req.anilistId
            ?: extensionLinkStore.getAniListId(req.sourceId, req.animeUrl)            // line 586 — reverse-lookup at call time
    }
}
```

**Interpretation:** The "current anilistId" is resolved lazily — for unlinked extension anime, it's `null` (since ExtensionLinkStore has no entry). This is what's passed to watch/download flows downstream.

### 5.4 `AniListDetailsProvider` — AniList-keyed provider

**File:** `data/anime/src/main/java/app/confused/anikuta/data/anime/details/AniListDetailsProvider.kt:49-71`

```kotlin
class AniListDetailsProvider(
    private val anilistApi: AniListApi,
    private val sourceMatcher: SourceMatcher,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val sourceLinkStore: SourceLinkStore,
    private val extensionLinkStore: ExtensionLinkStore,
    private val appContext: Context,
) : AnimeDetailsProvider {

    override val dataSource: DataSource = DataSource.ANILIST

    override suspend fun load(request: DetailsRequest, forceRefresh: Boolean): DetailsResult? = when (request) {
        is DetailsRequest.ByAniListId -> loadByAniListId(request.anilistId)
        is DetailsRequest.ByExtension -> {
            // AniList provider can only serve AniList-keyed lookups. If the extension
            // anime is linked, load by its anilistId; otherwise return null (the
            // ExtensionDetailsProvider handles the unlinked case).
            val anilistId = request.anilistId
                ?: extensionLinkStore.getAniListId(request.sourceId, request.animeUrl)   // line 68
            if (anilistId != null) loadByAniListId(anilistId) else null                  // line 69
        }
    }
```

**Interpretation:** The AniList provider REQUIRES anilistId. For unlinked extension anime, it returns null and the ExtensionDetailsProvider takes over.

**Stage 1 (line 73-76):** `anilistApi.fetchById(anilistId)` — AniList GraphQL by ID. Non-recoverable if the ID is wrong.
**Stage 2+3 (line 132-198):** DB-first short-circuit → SourceLinkStore → fresh `sourceMatcher.matchAll(title)`. `matchAll` searches every trusted extension source by title with a 0.80 similarity threshold (see §10.3 below).
**Persistence gate (line 212-216):** `if (anilistId != null) { saveEpisodesToDb(...) }` — episodes are ONLY persisted to DB for linked anime. Unlinked extension anime can't use this provider anyway (it returns null at line 69).

**Anime row creation (line 236-264):** When saving episodes to DB, if the anime row doesn't exist, the provider creates one with `url = "anilist:$anilistId"`, `sourceId = 0`, `anilistId = anilistId`. The `sourceId = 0` is a placeholder — the real sourceId comes from the saved SourceLinkStore entry.

### 5.5 `ExtensionDetailsProvider` — extension-keyed provider

**File:** `data/extension/src/main/java/app/confused/anikuta/data/extension/details/ExtensionDetailsProvider.kt:63-184`

```kotlin
override suspend fun load(request: DetailsRequest, forceRefresh: Boolean): DetailsResult? = when (request) {
    is DetailsRequest.ByExtension -> loadByExtension(
        sourceId = request.sourceId,
        animeUrl = request.animeUrl,
        animeTitle = request.animeTitle,
        anilistId = request.anilistId,                                                 // nullable
        forceRefresh = forceRefresh,
    )
    is DetailsRequest.ByAniListId -> {
        // Extension provider serving an AniList-keyed request: reverse-lookup the
        // preferred extension source + reconstruct the SAnime, then load by extension.
        val savedLink = sourceLinkStore.getLink(request.anilistId) ?: return null      // line 91 — needs a saved SourceLink
        loadByExtension(
            sourceId = savedLink.sourceId,
            animeUrl = savedLink.animeUrl,
            animeTitle = savedLink.animeTitle,
            anilistId = request.anilistId,
            forceRefresh = forceRefresh,
        )
    }
}
```

**DB-first short-circuit (line 114-132):**
```kotlin
if (!forceRefresh) {
    val dbAnime = when {
        anilistId != null -> animeRepository.getByAnilistId(anilistId)
        else -> animeRepository.getBySourceAndUrl(sourceId, animeUrl)
    }
    ...
}
```

**AniList merge stage (line 169-177):**
```kotlin
val effectiveAnilistId = anilistId ?: extensionLinkStore.getAniListId(source.id, animeUrl)   // line 170
if (effectiveAnilistId != null) {
    val anilistMerge = withContext(Dispatchers.IO) { anilistApi.fetchById(effectiveAnilistId) }
    if (anilistMerge != null) {
        unified = unified.mergeAniListMetadata(anilistMerge.toUnifiedAnime())
    }
}
```

**Interpretation:** The ExtensionDetailsProvider is the ONLY code path that fully handles unlinked anime (anilistId = null). It does so by:
1. Using `sourceId + animeUrl` as the DB key (line 117: `getBySourceAndUrl`).
2. Skipping the AniList merge stage when `effectiveAnilistId == null` (line 171).
3. Persisting episodes via `persistEpisodes` (line 295-354) which itself branches on `anilistId != null` (line 305) for the anime row lookup.

### 5.6 `UnifiedAnime` — the unified details value

**File:** `core/common/src/main/java/app/confused/anikuta/core/common/model/details/UnifiedAnime.kt:56-86`

```kotlin
data class UnifiedAnime(
    val dataSource: DataSource,
    // Identity
    val anilistId: Int?,               // line 59 — nullable; null for unlinked extension anime
    val malId: Int?,                   // line 60 — AniList-only
    val sourceId: Long?,               // line 61 — null in pure-AniList mode
    val sourceName: String,
    val url: String,
    // Display
    val title: String,
    val coverUrl: String?,
    val coverColorHex: String?,
    ...
)
```

**Interpretation:** `UnifiedAnime.anilistId` is `Int?`. The KDoc (line 21) says: *"Drives tracker-button visibility + episode-metadata enrichment."* The "tracker button visibility" check + "episode-metadata enrichment skip" are the two concrete downstream gates.

### 5.7 Episode metadata enrichment — gated on anilistId

**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt:627-647`

```kotlin
private suspend fun fetchEpisodeMetadata(anime: UnifiedAnime, episodeCount: Int) {
    try {
        val anilistId = anime.anilistId ?: run {
            Log.i(TAG, "Skipping episode metadata — no anilistId (unlinked extension anime)")
            return                                                                                  // line 630 — HARD SKIP
        }
        val request = EpisodeMetadataRequest(
            animeId = anilistId,                                                                    // line 634
            ...
        )
        ...
    }
}
```

**Interpretation:** Episode metadata (Jikan/MAL/Anikage/AniList streaming episode titles, descriptions, thumbnails, air dates) is **completely skipped** for unlinked extension anime. The user sees raw episode names from the extension only.

### 5.8 `EpisodeMetadataRequest` + `EpisodeMetadataCache` — anilistId-keyed

**File:** `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/model/EpisodeMetadata.kt:37-44`
```kotlin
data class EpisodeMetadataRequest(
    val animeId: Int,             // The AniList anime ID (per KDoc line 29)
    ...
)
```

**File:** `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/repository/EpisodeMetadataCache.kt:55-71`
```kotlin
fun get(animeId: Int): Map<Int, EpisodeMetadata>? {
    val jsonStr = prefs.get()[animeId.toString()] ?: return null     // ← key = anilistId.toString()
    ...
}
fun save(animeId: Int, metadata: Map<Int, EpisodeMetadata>) {
    val map = prefs.get().toMutableMap()
    map[animeId.toString()] = json.encodeToString(metadataSerializer, metadata)
    prefs.set(map)
}
```

**Interpretation:** The EpisodeMetadataCache is keyed exclusively by anilistId. There is no `"ext:..."` fallback path (unlike DetailsViewPreferenceStore). Unlinked extension anime have nowhere to cache metadata even if it could be fetched.

---

## 6. Watch flow

### 6.1 `WatchRequest` — non-nullable `anilistId: Int`

**File:** `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchRequest.kt:18-38`

```kotlin
data class WatchRequest(
    val videoUrl: String,
    val videoHeaders: String?,
    val videoTitle: String,
    val anilistId: Int,                // line 22 — NON-NULLABLE
    val animeTitle: String,
    val coverUrl: String?,
    val coverColor: Int?,
    val episodeUrl: String,
    val episodeNumber: Float,
    val sourceId: Long,
    val source: AnimeSource? = null,
    ...
)
```

**Interpretation:** `WatchRequest.anilistId` is `Int` (not `Int?`). The only way to construct one for an unlinked extension anime is to pass `0` — which the call sites do (`AppController.kt:186`, `Destinations.kt:186`).

### 6.2 WatchRequest construction — `AppController.resolveEpisode`

**File:** `app/src/main/java/app/confused/anikuta/navigation/AppController.kt:358-402`

```kotlin
fun resolveEpisode(
    episode: SEpisode,
    source: AnimeSource,
    episodeList: List<SEpisode>,
    watchCtx: WatchEpisodeContext,
    anilistId: Int,                                  // ← non-nullable Int (callers pass 0 for unlinked)
) {
    val epNum = episode.episode_number.toInt().let { if (it > 0) it else 0 }
    scope.launch {
        // ── Offline-playback short-circuit ──
        try {
            if (anilistId != 0 && downloadManager.isEpisodeDownloaded(anilistId, episode.url)) {   // line 370 — HARD GATE
                ...
            }
        } catch (e: Exception) { ... }

        // ── Streaming path ──
        resolveTarget = ResolveTarget(episode, source, episodeList, watchCtx, anilistId)
        ...
    }
}
```

**The gate at line 370:** `anilistId != 0 && downloadManager.isEpisodeDownloaded(anilistId, episode.url)`. For an unlinked extension anime (anilistId = 0), the offline-playback short-circuit is **skipped entirely** — the user is forced to stream even if they downloaded the episode. (They can't have downloaded it though — see §7.)

### 6.3 WatchRequest construction — `AppController.onVideoSelected`

**File:** `app/src/main/java/app/confused/anikuta/navigation/AppController.kt:440-486`

```kotlin
fun onVideoSelected(video: ResolverVideo) {
    ...
    val target = resolveTarget
    ...
    pushWatch(
        WatchRequest(
            videoUrl = video.url,
            ...
            anilistId = target.anilistId,                                  // line 468 — propagated from resolveEpisode
            ...
            episodeUrl = target.episode.url,
            ...
        )
    )
}
```

### 6.4 `WatchScreen` — progress save uses `watchRequest.anilistId`

**File:** `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchScreen.kt`

- **Resume lookup (line 514-517):**
  ```kotlin
  val progress = watchProgressStore.get(
      watchRequest.anilistId,    // ← if 0, the lookup will return null (no key starts with "0:")
      currentEpUrl,
  )
  ```
- **Save on dispose (line 643-652):**
  ```kotlin
  watchProgressStore.save(
      anilistId = watchRequest.anilistId,           // ← if 0, the key is "0:<episodeUrl>"
      episodeUrl = currentEpUrl,
      positionSeconds = pos,
      ...
  )
  ```
- **Periodic save (line 681-684):** same `watchRequest.anilistId` parameter.

**Interpretation:** If an unlinked extension anime is watched (anilistId = 0), `WatchProgressStore.save(0, ...)` writes a progress entry with key `"0:<episodeUrl>"`. **This entry will collide across different unlinked anime that share the same episode URL** (e.g. a generic `episode/1` URL on two different sources). It also can't be cross-referenced back to the library anime (the library uses `animes._id`, not anilistId).

### 6.5 `Destinations.kt` — ExtensionAnimeDetailDestination passes 0

**File:** `app/src/main/java/app/confused/anikuta/navigation/Destinations.kt:158-207`

```kotlin
data class ExtensionAnimeDetailDestination(
    val source: AnimeCatalogueSource,
    val sAnime: SAnime,
    val anilistId: Int? = null,
) : Screen {
    ...
    @Composable override fun Content() {
        ...
        // For unlinked extension anime, download states are keyed by sourceId+url
        // (not anilistId). Use 0 as the download-key fallback — the download
        // orchestrator resolves by episode URL regardless.
        val downloadKey = anilistId ?: 0                                           // line 173
        ...
        AnimeDetailScreen(
            ...
            onOpenEpisode = { episode, src, episodeList, watchCtx ->
                appController.resolveEpisode(
                    episode, src, episodeList, watchCtx,
                    anilistId = anilistId ?: 0,                                    // line 186
                )
            },
            onDownloadEpisode = { episode, src, watchCtx ->
                appController.downloadEpisode(episode, src, watchCtx, downloadKey) // line 190 — passes 0 for unlinked
            },
            ...
            onDownloadCancel = { episodeUrl -> appController.cancelDownload(downloadKey, episodeUrl) },   // line 193
            ...
        )
    }
}
```

**The KDoc on line 170-172 is misleading:** *"For unlinked extension anime, download states are keyed by sourceId+url (not anilistId). Use 0 as the download-key fallback — the download orchestrator resolves by episode URL regardless."* But:
1. `DownloadManager.episodeDownloadStates` is keyed by `"${anilistId}:${episodeUrl}"` (see §7.1). Passing 0 means the download state map key for an unlinked anime is `"0:<episodeUrl>"`.
2. `downloadEpisode` blocks on `anilistId == 0` (see §7.3) — so no download task is ever created for unlinked anime, so the `0:` key never actually gets populated.
3. The comment "the download orchestrator resolves by episode URL regardless" is **false** — every `DownloadManager` lookup method takes `anilistId: Int` as a primary key (see §7.1).

---

## 7. Downloads flow — the composite key + the `anilistId == 0` gate

### 7.1 `DownloadManager` interface — every method keyed by `anilistId: Int`

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadManager.kt:30-122`

```kotlin
interface DownloadManager {
    val activeDownloads: Flow<List<DownloadTask>>
    val completedDownloads: Flow<List<DownloadTask>>
    val allDownloads: Flow<List<DownloadTask>>

    suspend fun enqueueDownload(request: DownloadRequest): Long

    /** Delete ALL completed downloads for an anime (the whole anime folder). */
    suspend fun deleteAnimeDownloads(anilistId: Int)                                  // line 70

    suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean      // line 97
    suspend fun getDownloadedVideoUri(anilistId: Int, episodeUrl: String): String?    // line 103
    suspend fun getDownloadedSubtitleUris(anilistId: Int, episodeUrl: String): List<String>  // line 109
    suspend fun getDownloadedEpisodes(anilistId: Int): List<DownloadedEpisode>         // line 112

    /**
     * A reactive map of download state for ALL tasks, keyed by
     * `"$anilistId:$episodeUrl"`. Used by the episode-row UI to show
     * download/progress/downloaded state per episode without per-row queries.
     */
    val episodeDownloadStates: Flow<Map<String, DownloadTask>>                        // line 121 — composite key
}
```

**Interpretation:** Every offline-playback query method takes `anilistId: Int` as a primary parameter. There is NO method that takes `sourceId + url`. Unlinked extension anime have no way to query their own downloads through this interface.

### 7.2 `DownloadAnimeInfo` — full definition

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadModels.kt:25-31`

```kotlin
@Serializable
data class DownloadAnimeInfo(
    val anilistId: Int,                // line 27 — NON-NULLABLE. The AniList ID — primary key for folder structure.
    val title: String,
    val coverUrl: String? = null,
    val coverColor: Int? = null,
)
```

**Interpretation:** `DownloadAnimeInfo.anilistId` is `Int` (non-nullable). The KDoc (line 20) explicitly says: *"The AniList ID — the primary key for the folder structure."*

### 7.3 The `anilistId == 0` gate

**File:** `app/src/main/java/app/confused/anikuta/navigation/AppController.kt:503-540`

```kotlin
fun downloadEpisode(
    episode: SEpisode,
    source: AnimeSource,
    watchCtx: WatchEpisodeContext,
    anilistId: Int,
) {
    if (anilistId == 0) {                                                              // line 509 — THE GATE
        Toast.makeText(context, "Cannot download — anime not linked", Toast.LENGTH_SHORT).show()
        return
    }
    val animeInfo = app.confused.anikuta.core.download.DownloadAnimeInfo(
        anilistId = anilistId,
        title = watchCtx.animeTitle.ifBlank { "Anime $anilistId" },
        coverUrl = watchCtx.coverUrl,
    )
    ...
}
```

**Interpretation:** For an unlinked extension anime (anilistId = 0), the download button is **completely disabled** — the user gets a Toast and the function returns. **This is the single most consequential anilistId hard-gate in the app.**

### 7.4 The composite key `"${anilistId}:${episodeUrl}"` — every occurrence

I used ripgrep to find every occurrence of this exact pattern. Total occurrences:

| File | Line | Code |
|---|---|---|
| `core/download/src/main/java/app/confused/anikuta/core/download/DownloadTask.kt` | 41 | `val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"` |
| `core/download/src/main/java/app/confused/anikuta/core/download/DownloadQueue.kt` | 310 | `"${request.anime.anilistId}:${request.episode.episodeUrl}"` (in `keyFor`) |
| `core/download/src/main/java/app/confused/anikuta/core/download/DefaultDownloadManager.kt` | 203 | `queue.tasks.value.firstOrNull { it.key == "$anilistId:$episodeUrl" }` (in `findTask`) |
| `core/download/src/main/java/app/confused/anikuta/core/download/DownloadManager.kt` | 116 | KDoc: *"`"$anilistId:$episodeUrl"`. Used by the episode-row UI..."* |
| `core/download/src/main/java/app/confused/anikuta/core/download/DefaultDownloadManager.kt` | 107 | KDoc: *"Reactive map keyed by `"$anilistId:$episodeUrl"` → task..."* |
| `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` | 584 | `downloadTasksFlow.value["$anilistId:$episodeUrl"] ?: return` (cancelDownload) |
| `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` | 589 | same (resumeDownload) |
| `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` | 594 | same (retryDownload) |
| `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` | 599 | same (deleteDownload) |
| `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` | 622 | `it.startsWith("$anilistId:")` (getDownloadStates filter) |
| `core/player/src/main/java/app/confused/anikuta/core/player/WatchProgressStore.kt` | 63-64 | KDoc + `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"` |
| `core/player/src/main/java/app/confused/anikuta/core/player/PlaybackStateStore.kt` | 59-60 | KDoc + `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"` |
| `core/tracker/src/main/java/app/confused/anikuta/core/tracker/TrackSyncManager.kt` | 73 | KDoc: *"Extract the AniList ID from a progress key (format: `"$anilistId:$episodeUrl"`)."* |
| `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/WatchProgressBackupProvider.kt` | 22 | KDoc: *"The key format is `"$anilistId:$episodeUrl"` — stable across devices and sessions."* |
| `core/backup/src/main/java/app/confused/anikuta/core/backup/model/WatchProgressBackup.kt` | 9, 16 | KDoc + `"Key: "$anilistId:$episodeUrl". Value: playback position + metadata."` |
| `core/backup/src/main/java/app/confused/anikuta/core/backup/translation/AniyomiBackupTranslator.kt` | 352 | `val key = "${res.anilistId}:${hist.url}"` (builds watch-progress entries from Aniyomi history) |

**Interpretation:** The composite key `"${anilistId}:${episodeUrl}"` is the **de facto primary key** for downloads, watch progress, and playback state. It's the single most-replicated identifier pattern in the codebase. The KDoc at `HistoryViewModel.kt:127-131` notes the parsing convention: *"Keys are `"anilistId:episodeUrl"`. The episode URL may itself contain colons (e.g. `https://...`), so we split on the FIRST colon only."*

### 7.5 `DownloadTask` identity

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadTask.kt:27-49`

```kotlin
@Serializable
data class DownloadTask(
    val id: Long,                              // Unique task ID (assigned by the manager)
    val request: DownloadRequest,
    val status: DownloadStatus,
    ...
) {
    /** Composite key for dedup + offline-playback lookup. */
    val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"    // line 41
    ...
}
```

**Interpretation:** `DownloadTask.id` is the local monotonic Long (DB-style autoincrement). `DownloadTask.key` is the composite string used for dedup + offline lookup. The dedup logic in `DownloadQueue.enqueue` (line 87): `_tasks.value.firstOrNull { it.key == keyFor(request) }` — so enqueuing the same `(anilistId, episodeUrl)` twice is a no-op.

### 7.6 On-disk folder structure — anilistId in the path

**File:** `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStorageProvider.kt:17-29` (KDoc) + `85-89` (folder name):

```
<USER_FOLDER>/ANIKUTA/
└── downloads/
    └── anime/
        └── <Anime Title [anilistId]>/       ← anilistId in the folder name
            └── Episode NNN/
                ├── video.<ext>
                └── data/
                    ├── subtitles/
                    └── metadata.json
```

```kotlin
fun animeFolderName(anime: DownloadAnimeInfo): String {
    val safeTitle = sanitizeFileName(anime.title.ifBlank { "Unknown" })
    return "$safeTitle [${anime.anilistId}]"     // line 88 — anilistId in folder name
}
```

**Anime folder deletion by anilistId (line 345-356):**
```kotlin
fun deleteAnime(anilistId: Int, animeTitle: String): Boolean {
    val root = rootTree() ?: return false
    val animeDir = root.findFile("ANIKUTA")
        ?.findFile("downloads")
        ?.findFile("anime")
        ?.listFiles()
        ?.firstOrNull { it.name?.endsWith("[$anilistId]") == true }    // line 351 — looks up by [anilistId] suffix
        ?: return false
    val ok = animeDir.delete()
    ...
}
```

**Interpretation:** The on-disk folder name encodes anilistId as `"Anime Title [anilistId]"`. Folder deletion is by anilistId-suffix match. If two anime had anilistId = 0 (hypothetically, if the gate at §7.3 were removed), they would collide on `"Anime Title [0]"` only if titles matched — but the download engine would already dedup them at the composite-key level.

### 7.7 `DownloadedAnimeKey` — UI grouping key

**File:** `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadUiState.kt:29-34`

```kotlin
data class DownloadedAnimeKey(
    val anilistId: Int,                // NON-NULLABLE — drives LazyColumn item key
    val title: String,
    val coverUrl: String?,
    val coverColor: Int?,
)
```

**Used at:** `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadedFilesScreen.kt:101`: `item(key = "downloaded_${animeKey.anilistId}")` — the LazyColumn item key. If anilistId collisions occurred, Compose would crash with duplicate keys.

---

## 8. Watch history flow

### 8.1 `WatchProgressStore` — anilistId-keyed JSON map

**File:** `core/player/src/main/java/app/confused/anikuta/core/player/WatchProgressStore.kt:40-145`

```kotlin
class WatchProgressStore(private val store: PreferenceStore) {
    private val progressPref: Preference<Map<String, Progress>> = store.getObject(
        key = "pref_watch_progress_map",                              // line 47
        defaultValue = emptyMap(),
        ...
    )

    val changes: Flow<Map<String, Progress>> = progressPref.changes().map { it }

    /** Key = "$anilistId:$episodeUrl" — stable across sessions. */
    private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"   // line 64

    fun save(
        anilistId: Int,
        episodeUrl: String,
        positionSeconds: Int,
        durationSeconds: Int,
        title: String,
        coverUrl: String? = null,
        animeTitle: String? = null,
        episodeNumber: Float = -1f,
        thumbnailUrl: String? = null,
    ) {
        val map = progressPref.get().toMutableMap()
        map[key(anilistId, episodeUrl)] = Progress(...)
        progressPref.set(map)
    }

    fun get(anilistId: Int, episodeUrl: String): Progress? = progressPref.get()[key(anilistId, episodeUrl)]
    fun clear(anilistId: Int, episodeUrl: String) { ... }
    fun clearAnime(anilistId: Int) { val prefix = "$anilistId:"; map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) } }   // line 112-117
    fun deleteAll() { progressPref.set(emptyMap()) }
    fun getAll(): Map<String, Progress> = progressPref.get()

    @Serializable
    data class Progress(
        val positionSeconds: Int,
        val durationSeconds: Int,
        val title: String,
        val updatedAt: Long,
        val coverUrl: String? = null,
        val animeTitle: String? = null,
        val episodeNumber: Float = -1f,
        val thumbnailUrl: String? = null,
    )
}
```

**Interpretation:** WatchProgressStore is keyed **exclusively** by `"${anilistId}:${episodeUrl}"`. There is NO alternative key for unlinked extension anime. **`WatchScreen` will happily save a `"0:<url>"` entry for unlinked anime** (per §6.4), polluting the map with entries that:
1. Collide across different unlinked anime that share an episode URL.
2. Cannot be cross-referenced back to a library anime row (`anilistId = 0` matches no `animes.anilist_id` — and even if it did, the partial unique index would force only one such row).
3. Get picked up by `TrackSyncManager.syncPendingProgress` (§9.1) which would try to find an anime row with `anilistId = 0` (none exists) and silently skip it (line 62: `if (anilistId <= 0) continue`).

### 8.2 `PlaybackStateStore` — same composite-key pattern

**File:** `core/player/src/main/java/app/confused/anikuta/core/player/PlaybackStateStore.kt:27-104`

Same shape: `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"` (line 60). Same `"pref_playback_state_map"` SharedPreferences key (line 47). Same fundamental design.

### 8.3 `HistoryViewModel` — parses the composite key

**File:** `feature/history/src/main/java/app/confused/anikuta/feature/history/HistoryViewModel.kt:63-145`

```kotlin
class HistoryViewModel(
    private val watchProgressStore: WatchProgressStore,
) : ViewModel() {
    init {
        viewModelScope.launch {
            watchProgressStore.changes
                .catch { ... }
                .collect { progressMap ->
                    val entries = progressMap.map { (key, progress) ->
                        val (anilistId, episodeUrl) = parseKey(key)                  // line 79
                        HistoryEntry(anilistId, episodeUrl, progress)
                    }.sortedByDescending { it.progress.updatedAt }
                    ...
                }
        }
    }
    ...
    private fun parseKey(key: String): Pair<Int, String> {
        val idx = key.indexOf(':')
        if (idx < 0) return 0 to key
        val idPart = key.substring(0, idx)
        val urlPart = key.substring(idx + 1)
        val anilistId = idPart.toIntOrNull() ?: 0                                    // line 138 — 0 on parse failure
        return anilistId to urlPart
    }
}
```

**`HistoryEntry` (file `feature/history/src/main/java/app/confused/anikuta/feature/history/HistoryState.kt:27-49`):**
```kotlin
data class HistoryEntry(
    val anilistId: Int,                // parsed from the composite key
    val episodeUrl: String,
    val progress: WatchProgressStore.Progress,
)
```

**Interpretation:** The history page renders every entry from `WatchProgressStore.getAll()` — including the `"0:<url>"` entries from unlinked anime. `parseKey` falls back to `0` on parse failure. The row's tap handler `onOpenAnime(entry.anilistId)` (file `HistoryScreen.kt:140`) would push `AnimeDetailDestination(animeId = 0)` — which **will show an error state** because AniList has no media ID 0. **This is a real bug path: history entries for unlinked extension anime are unopenable.**

### 8.4 `HistoryRepository` + `animehistory.sq` — UNUSED

**File:** `core/common/src/main/java/app/confused/anikuta/core/common/repository/HistoryRepository.kt:7-18`

```kotlin
interface HistoryRepository {
    fun observeAll(): Flow<List<History>>
    fun observeByAnimeId(animeId: Long): Flow<List<History>>
    suspend fun upsert(animeId: Long, episodeId: Long, seenAt: Long, lastSecondSeen: Long)
    suspend fun delete(id: Long)
    suspend fun deleteByAnimeId(animeId: Long)
}
```

`data/history/src/main/java/app/confused/anikuta/data/history/HistoryRepositoryImpl.kt:11-37` implements it via `animehistoryQueries`. The implementation exists and is wired (per `HistoryModule`), but **NO ViewModel consumes it**. `HistoryViewModel` uses `WatchProgressStore` exclusively (per its KDoc at lines 18-23, quoted in §2.3 above). The `animehistory` table sits empty.

The interface uses `animeId: Long` (= `animes._id`, NOT anilistId). If the table were used, it would be local-PK-based — but it isn't.

---

## 9. Tracking/sync flow

### 9.1 `TrackSyncManager` — parses anilistId from `WatchProgressStore` keys

**File:** `core/tracker/src/main/java/app/confused/anikuta/core/tracker/TrackSyncManager.kt:26-118`

```kotlin
class TrackSyncManager(
    private val watchProgressStore: WatchProgressStore,
    private val trackRepository: TrackRepository,
    private val trackerManager: TrackerManager,
    private val animeRepository: AnimeRepository,
) {
    fun start() {
        watchProgressStore.changes.onEach { progressMap ->
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(DEBOUNCE_MS)   // 10s
                syncPendingProgress(progressMap)
            }
        }.launchIn(scope)
    }

    private suspend fun syncPendingProgress(progressMap: Map<String, Progress>) {
        if (progressMap.isEmpty()) return
        syncMutex.withLock {
            val latestByAnime = progressMap.values
                .filter { it.episodeNumber >= 0 }
                .groupBy { extractAnilistId(progressMap, it) }                       // line 58
                .mapValues { (_, entries) -> entries.maxByOrNull { it.updatedAt } }

            for ((anilistId, progress) in latestByAnime) {
                if (anilistId <= 0) continue                                          // line 62 — HARD SKIP for anilistId <= 0
                val p = progress ?: continue
                try { syncAnimeProgress(anilistId, p) }
                catch (e: Exception) { Log.e(TAG, "Sync failed for anilistId=$anilistId", e) }
            }
        }
    }

    private fun extractAnilistId(
        progressMap: Map<String, Progress>,
        progress: Progress,
    ): Int {
        val key = progressMap.entries.find { it.value === progress }?.key ?: return -1
        val anilistIdStr = key.substringBefore(":")
        return anilistIdStr.toIntOrNull() ?: -1
    }

    private suspend fun syncAnimeProgress(anilistId: Int, progress: Progress) {
        val anime = animeRepository.getByAnilistId(anilistId) ?: return              // line 86 — needs an animes row with this anilistId
        val tracks = trackRepository.getTracks(anime.id)                             // line 87 — uses animes._id, NOT anilistId
        if (tracks.isEmpty()) return
        ...
        for (track in tracks) {
            val tracker = trackerManager.getTracker(track.trackerId.toInt()) ?: continue
            if (!tracker.isLoggedIn) continue
            tracker.updateProgress(
                remoteAnimeId = track.remoteId.toInt(),                              // line 98 — tracker-side remote ID
                episodeNumber = episodeNumber,
                status = status,
            )
            trackRepository.updateLastSeen(track.id, episodeNumber.toLong())
        }
    }
}
```

**Interpretation:** `TrackSyncManager` is the bridge between `WatchProgressStore` (anilistId-keyed) and `animetrack` (animes._id-keyed). The bridge is `animeRepository.getByAnilistId(anilistId) → anime.id → trackRepository.getTracks(anime.id)`. For anilistId ≤ 0 (unlinked or parse failures), sync is skipped. For anilistId > 0 with no `animes` row, sync is also skipped (the `?: return` at line 86).

### 9.2 `TrackRepository` + `animetrack.sq` — local-PK-based

**File:** `core/tracker/src/main/java/app/confused/anikuta/core/tracker/TrackRepository.kt:13-101`

```kotlin
class TrackRepository(private val database: AnikutaDatabase) {
    suspend fun getTracks(animeId: Long): List<AnimeTrack> = ... selectByAnimeId(animeId, ::mapTrack) ...     // line 17
    suspend fun getTrack(animeId: Long, trackerId: Int): AnimeTrack? = ... selectByAnimeIdAndTrackerId(animeId, ...) ...  // line 22
    suspend fun bind(
        animeId: Long,                       // ← animes._id
        trackerId: Int,                      // 1=MAL, 2=AniList (per Tracker.ANILIST_ID/MAL_ID)
        remoteId: Int,                       // ← tracker-side media ID
        remoteUrl: String? = null,
        ...
    ) = ... animetrackQueries.upsert(animeId = animeId, trackerId = trackerId.toLong(), remoteId = remoteId.toLong(), ...) ...
    ...
}
```

**`AnimeTrack` domain model (`core/tracker/src/main/java/app/confused/anikuta/core/tracker/AnimeTrack.kt:20-31`):**
```kotlin
data class AnimeTrack(
    val id: Long,                 // animetrack._id
    val animeId: Long,            // animes._id (NOT anilistId)
    val trackerId: Long,          // 1=MAL, 2=AniList
    val remoteId: Long,           // tracker-side media ID (AniList mediaId OR MAL anime id)
    val remoteUrl: String?,
    val lastSeen: Long,
    val score: Double,
    val status: Long,
    val totalEpisodes: Long,
    val displayScore: String?,
)
```

**Interpretation:** `animetrack.anime_id` is `animes._id` (local PK). `animetrack.remote_id` is the tracker-side ID (separate concept from `animes.anilist_id`). The AniList tracker's `remoteId` happens to equal the AniList media ID — but they're stored in different columns for different reasons. **A tracker binding for an unlinked extension anime is technically possible** (you could `bind(animeId = <local _id>, trackerId = 2, remoteId = <AniList mediaId>)`) — but no UI surfaces this. The tracker settings UI only binds anime that have anilistId.

### 9.3 `AniListTracker.updateProgress` — remote-side

**File:** `core/tracker/src/main/java/app/confused/anikuta/core/tracker/anilist/AniListTracker.kt:95-106`

```kotlin
override suspend fun updateProgress(remoteAnimeId: Int, episodeNumber: Int, status: TrackStatus) {
    val token = tokenPref.get()
    if (token.isEmpty()) { Log.w(TAG, "updateProgress: not logged in"); return }
    try { api.updateProgress(token, remoteAnimeId, episodeNumber, status) }
    catch (e: Exception) { Log.e(TAG, "updateProgress failed for mediaId=$remoteAnimeId", e) }
}
```

**Interpretation:** The AniList tracker's `updateProgress` takes `remoteAnimeId: Int` — which is the AniList media ID (same value as `animes.anilist_id`, just routed through `animetrack.remote_id`). The Tracker interface (`Tracker.kt:32`) defines this contract uniformly across AniList + MAL.

### 9.4 `MalTracker` — MAL-side, same shape

**File:** `core/tracker/src/main/java/app/confused/anikuta/core/tracker/mal/MalTracker.kt:101-111` — `updateProgress(remoteAnimeId: Int, episodeNumber: Int, status: TrackStatus)` calls `api.updateProgress(token, remoteAnimeId, episodeNumber, status)`. Same shape, different remote.

### 9.5 `TrackerBackupProviderImpl` — backs up bindings by animes._id

**File:** `core/tracker/src/main/java/app/confused/anikuta/core/tracker/TrackerBackupProviderImpl.kt:28-118`

Reads `trackRepository.getAllTracks()` (line 41) → serializes each `AnimeTrack` (including `animeId` = `animes._id`). On restore, calls `trackRepository.bind(animeId = track.animeId, ...)` (line 86-96) — using the **original local DB _id from the backup device**. This is a portability bug if the anime's `_id` differs across devices (which it will, since `_id` is autoincrement). The CategoryBackupProvider handles this via an `anilistId → localDbId` remap (§11.3) — but the tracker backup does NOT.

**However:** the Aniyomi-translated backup path (§11.6) rewrites `animeId = res.anilistId.toLong()` (line 373 in AniyomiBackupTranslator), making the animeId an anilistId-as-Long. The `CategoryBackupProvider.resolveLocalAnimeIdForLink` (§11.3) handles this case — but the `TrackerBackupProviderAdapter.import` (§11.5) passes the animeId straight through to `trackRepository.bind`, which writes it as `animetrack.anime_id`. **A restored Aniyomi-translated tracker binding will have a wrong `anime_id`** (it'll be the AniList ID, not the local `_id`).

---

## 10. Categories flow

### 10.1 `CategoryRepository` interface — local-PK-based

**File:** `core/common/src/main/java/app/confused/anikuta/core/common/repository/CategoryRepository.kt:70-90`

```kotlin
interface CategoryRepository {
    fun observeAllLinks(): Flow<List<AnimeCategoryLink>>
    fun observeCategoriesForAnime(animeId: Long): Flow<List<Category>>     // animes._id
    suspend fun getAnimeCategories(animeId: Long): List<Category>
    suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>)  // animes._id
    suspend fun countAnimeInCategory(categoryId: Long): Int
}
```

**`AnimeCategoryLink` (`core/common/src/main/java/app/confused/anikuta/core/common/model/AnimeCategoryLink.kt:8-11`):**
```kotlin
data class AnimeCategoryLink(val animeId: Long, val categoryId: Long)     // animes._id, categories._id
```

### 10.2 `CategoryRepositoryImpl.setAnimeCategories` — full-replace by `animes._id`

**File:** `data/anime/src/main/java/app/confused/anikuta/data/anime/CategoryRepositoryImpl.kt:175-187`

```kotlin
override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
    database.transaction {
        database.anime_categoryQueries.deleteByAnimeId(animeId)
        categoryIds.forEachIndexed { index, categoryId ->
            database.anime_categoryQueries.insert(
                animeId = animeId,
                categoryId = categoryId,
                order = index.toLong(),
            )
        }
    }
}
```

**Interpretation:** Categories are assigned by `animes._id` — NO anilistId anywhere. This works for both linked and unlinked anime because it's purely local-PK-based.

### 10.3 `AnimeDetailViewModel.saveToCategories` — gets `_id` from `findLibraryAnime`

**File:** `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt:490-509`

```kotlin
fun saveToCategories(categoryIds: Set<Long>) {
    viewModelScope.launch {
        try {
            val unified = (_animeState.value as? DetailState.Success)?.anime ?: return@launch
            val existing = findLibraryAnime(unified)                      // line 494 — linked→anilistId, unlinked→sourceId+url
            val animeId = if (existing != null) {
                if (!existing.favorite) {
                    animeRepository.updateFavorite(existing.id, favorite = true, dateAdded = System.currentTimeMillis())
                }
                existing.id                                                // ← animes._id
            } else {
                saveAnimeToLibrary(unified)                                // returns the new _id
            }
            categoryRepository.setAnimeCategories(animeId, categoryIds.toList())
            ...
        }
    }
}
```

**Interpretation:** Categories are correctly bridged via `findLibraryAnime` (which handles both linked and unlinked), then the local `_id` is used. **Categories are the most anilistId-agnostic subsystem.**

---

## 11. Backup/restore flow

### 11.1 `BackupContainer` + `BackupEntry` — the polymorphic backup model

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/model/BackupContainer.kt:21-35`

```kotlin
@Serializable
data class BackupContainer(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,   // = 1 (line 30)
    val createdAt: Long,
    val appVersion: String = "",
    val deviceName: String = "",
    val entries: List<BackupEntry> = emptyList(),
)
```

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/BackupEntry.kt:31-112`

The sealed BackupEntry has 10 subclasses: `Library`, `AnimeDetails`, `Episodes`, `EpisodeMetadata`, `WatchProgress`, `SourceLinks`, `Tracker`, `Categories`, `Preferences`, `CoverImages`. Each has its own data model in `core/backup/src/main/java/app/confused/anikuta/core/backup/model/`.

### 11.2 `AnimeBackup` — full row mirror (anilistId nullable)

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/model/AnimeBackup.kt:18-49`

```kotlin
@Serializable
data class AnimeBackup(
    val _id: Long = 0,
    val url: String,
    val title: String,
    ...
    val anilistId: Long? = null,                  // line 43 — nullable, matches DB schema
    val coverColor: String? = null,
    val score: Double? = null,
    val totalEpisodes: Long? = null,
    val lastWatched: Long = 0,
    val nextAiringEpisode: Long? = null,
)
```

**Interpretation:** `AnimeBackup.anilistId` is `Long?` (mirrors the SQLDelight column type, not the domain-model `Int?`). Default is null, so old backups deserialize cleanly.

### 11.3 `LibraryBackupProvider` + `AnimeDetailsBackupProvider` — upsert by anilistId OR source+url

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/AnimeBackupProviders.kt:126-194`

```kotlin
internal fun upsertAnime(database: AnikutaDatabase, anime: AnimeBackup) {
    val queries = database.animesQueries
    val existingId: Long? = if (anime.anilistId != null) {                                     // line 128 — branch on anilistId
        queries.selectIdByAnilistId(anime.anilistId).executeAsOneOrNull()
    } else {
        queries.selectBySourceAndUrl(anime.sourceId, anime.url) { _id, _, ... -> _id }.executeAsOneOrNull()   // line 131
    }
    if (existingId != null) { queries.update(..., anilistId = anime.anilistId, ...) }          // line 136-162
    else { queries.insert(..., anilistId = anime.anilistId, ...) }                              // line 165-192
}
```

**Interpretation:** The backup import path correctly handles both linked and unlinked anime — it tries `selectIdByAnilistId` first, falls back to `selectBySourceAndUrl`. This is the **second** subsystem (after the ExtensionDetailsProvider) that fully supports unlinked anime.

### 11.4 `EpisodeBackupProvider` — episodes keyed by anilistId-or-source:url

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/EpisodeBackupProvider.kt:30-103`

Export (line 41-44):
```kotlin
// Key by anilistId if available, else by sourceId:url (for stable cross-device mapping)
val key = anime.anilistId?.toString() ?: "${anime.sourceId}:${anime.url}"
byAnime[key] = episodes
```

Import resolution (`resolveLocalAnimeId`, line 85-103):
```kotlin
internal fun resolveLocalAnimeId(database: AnikutaDatabase, key: String): Long? {
    val queries = database.animesQueries
    val anilistId = key.toLongOrNull()
    if (anilistId != null) {
        queries.selectIdByAnilistId(anilistId).executeAsOneOrNull()?.let { return it }       // line 90
    }
    val colonIdx = key.indexOf(':')
    if (colonIdx > 0) {
        val sourceId = key.substring(0, colonIdx).toLongOrNull()
        val url = key.substring(colonIdx + 1)
        if (sourceId != null) {
            queries.selectBySourceAndUrl(sourceId, url) { ... }.executeAsOneOrNull()?.let { return it }   // line 98
        }
    }
    return null
}
```

**Interpretation:** Episodes backup also supports both keys. The backup system is one of the few places where the dual-key strategy is fully embraced.

### 11.5 `CategoryBackupProvider` — builds an `anilistId → localDbId` lookup table

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/CategoryBackupProvider.kt:53-165`

```kotlin
override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
    ...
    // Build a comprehensive anilistId → localDbId lookup table
    val allAnime = database.animesQueries.selectAll(BackupMappers::mapAnime).executeAsList()
    val anilistToLocalId = mutableMapOf<Long, Long>()
    val localIdSet = mutableSetOf<Long>()
    allAnime.forEach { anime ->
        localIdSet.add(anime._id)
        if (anime.anilistId != null) {
            anilistToLocalId[anime.anilistId] = anime._id                          // line 67
        }
    }
    ...
    entry.links.forEach { link ->
        val newCategoryId = categoryIdRemap[link.categoryId] ?: ...
        val localAnimeId = when {
            localIdSet.contains(link.animeId) -> link.animeId                       // line 106 — ANIKUTA backup: animeId IS the local _id
            anilistToLocalId.containsKey(link.animeId) -> anilistToLocalId[link.animeId]!!   // line 107 — Aniyomi-translated: animeId is anilistId
            else -> resolveLocalAnimeIdForLink(database, link.animeId)              // line 110 — fallback
        }
        ...
    }
}
```

**`resolveLocalAnimeIdForLink` (line 207-215):** tries `selectById(animeId)` first, then `selectIdByAnilistId(animeId)`.

**Interpretation:** This is the most sophisticated remap in the codebase. It handles THREE cases: (a) ANIKUTA backup with local `_id`, (b) Aniyomi-translated backup with anilistId-as-Long, (c) fallback via DB queries. **Anime without an anilistId fall back to direct `_id` match — but only if the local DB happens to have the same `_id` (which is unlikely after a fresh install).**

### 11.6 `SourceLinkBackupProvider` — backs up both link stores

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/SourceLinkBackupProvider.kt:26-96`

Combines `SourceLinkStore.getAll()` (keyed by anilistId) + `ExtensionLinkStore.getAll()` (keyed by `"$sourceId:$animeUrl"`). On restore, iterates both maps and calls `sourceLinkStore.saveLink(...)` + `extensionLinkStore.link(...)`.

**Interpretation:** SourceLinkBackup fully round-trips both stores. The keys are preserved verbatim. No anilistId remapping is needed because the keys ARE the anilistIds.

### 11.7 `WatchProgressBackupProvider` — preserves the composite key verbatim

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/WatchProgressBackupProvider.kt:25-104`

```kotlin
override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
    val all = watchProgressStore.getAll()
    val items = all.mapValues { (_, progress) -> WatchProgressItem(...) }
    BackupEntry.WatchProgress(progress = WatchProgressBackup(entries = items))
}

override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
    ...
    entry.progress.entries.forEach { (key, item) ->
        val local = existing[key]
        if (local != null && local.updatedAt >= item.updatedAt) { skipped++ }
        else {
            val (anilistId, episodeUrl) = parseKey(key)
            if (anilistId > 0 && episodeUrl.isNotEmpty()) {                          // line 70 — gate: anilistId > 0
                watchProgressStore.save(anilistId = anilistId, ...)
                imported++
            } else { skipped++ }
        }
    }
}

private fun parseKey(key: String): Pair<Int, String> {
    val idx = key.indexOf(':')
    if (idx < 0) return 0 to key
    val idPart = key.substring(0, idx)
    val urlPart = key.substring(idx + 1)
    return (idPart.toIntOrNull() ?: 0) to urlPart
}
```

**Interpretation:** WatchProgressBackup explicitly GATES import on `anilistId > 0` (line 70). Any `"0:<url>"` entries from unlinked anime are silently dropped during restore. This is **the only backup provider with an explicit anilistId > 0 gate**.

### 11.8 `CoverImageProvider` — anilistId-only

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/CoverImageProvider.kt:35-54`

```kotlin
override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
    ...
    favorites.forEach { anime ->
        val anilistId = anime.anilistId
        val coverUrl = anime.coverUrl ?: anime.thumbnailUrl
        if (anilistId != null && !coverUrl.isNullOrBlank()) {                         // line 44 — gate: anilistId != null
            covers[anilistId.toString()] = coverUrl
        }
    }
    ...
}
```

**Interpretation:** Cover image backup is **linked-anime-only**. Unlinked extension anime have no cover images in the backup.

### 11.9 `TrackerBackupProviderAdapter` + `TrackerBackupProviderImpl`

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/TrackerBackupProviderAdapter.kt:28-97`

The adapter wraps `TrackerBackupProvider` (from `:core:tracker`) into the `BackupProvider` contract. On import (line 65-96), it constructs `AnimeTrack` objects with `id = -1, animeId = item.animeId, ...` and passes them to `trackerBackupProvider.restore(data)`.

**`TrackerBackupProviderImpl.restore` (file `core/tracker/src/main/java/app/confused/anikuta/core/tracker/TrackerBackupProviderImpl.kt:66-107`):**
```kotlin
override suspend fun restore(data: TrackerBackupData) {
    withContext(Dispatchers.IO) {
        try {
            ...
            data.bindings.forEach { track ->
                try {
                    trackRepository.bind(
                        animeId = track.animeId,                                       // line 87 — used as-is (no remapping!)
                        trackerId = track.trackerId.toInt(),
                        remoteId = track.remoteId.toInt(),
                        ...
                    )
                } catch (e: Exception) { ... }
            }
        }
    }
}
```

**Interpretation:** Tracker bindings are restored with `animeId = track.animeId` used as-is. For ANIKUTA backups this is the local `_id` (which may not exist on the new device). For Aniyomi-translated backups this is `res.anilistId.toLong()` (AniyomiBackupTranslator line 373) — so `animetrack.anime_id` ends up holding an AniList ID, which won't match any local `animes._id`. **This is a latent bug for Aniyomi-format restore.**

### 11.10 `AniyomiBackupTranslator` — resolves anilistId for every Aniyomi anime

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/translation/AniyomiBackupTranslator.kt:232-269`

```kotlin
private suspend fun resolveAnilistId(ani: AniyomiBackupAnime): AnilistResolution {
    // Strategy 1: AniList tracker binding (syncId == 2, mediaId = AniList ID)
    val anilistTrack = ani.tracking.firstOrNull { it.syncId == 2 && it.mediaId != 0L }
    if (anilistTrack != null) {
        val anilistId = anilistTrack.mediaId.toInt()
        val anilistAnime = try { anilistApi.fetchById(anilistId) } catch (e: Exception) { null }
        return AnilistResolution.Resolved(anilistId, anilistAnime, "tracker", ani.title)
    }
    // Strategy 2: MAL tracker binding → AniList lookup
    val malTrack = ani.tracking.firstOrNull { it.syncId == 1 && it.mediaId != 0L }
    if (malTrack != null) {
        val malId = malTrack.mediaId.toInt()
        val anilistAnime = try { anilistApi.searchByMalId(malId) } catch (e: Exception) { null }
        if (anilistAnime != null) {
            return AnilistResolution.Resolved(anilistAnime.id, anilistAnime, "mal-lookup", ani.title)
        }
    }
    // Strategy 3: Title search
    val title = ani.title.ifBlank { return AnilistResolution.Failed(ani.title, "Empty title") }
    val anilistAnime = try { anilistApi.searchByTitle(title) } catch (e: Exception) {
        return AnilistResolution.RateLimited(ani.title, "API error: ${e.message}")
    }
    if (anilistAnime != null) {
        return AnilistResolution.Resolved(anilistAnime.id, anilistAnime, "title-search", ani.title)
    }
    return AnilistResolution.Failed(ani.title, "No AniList match")
}
```

**`buildContainer` (line 274-406):** For each resolved anime, builds:
- `AnimeBackup` with `anilistId = res.anilistId.toLong()` (line 426)
- `EpisodeBackup` list with `animeId = res.anilistId.toLong()` (line 298) — **the episodes are keyed by anilistId, not by local _id**
- `WatchProgressItem` with key `"${res.anilistId}:${hist.url}"` (line 352)
- `TrackerTrackItem` with `animeId = res.anilistId.toLong()` (line 373)
- `SourceLinkItem` with key `res.anilistId.toString()` (line 390)

**Interpretation:** The Aniyomi-translated backup is **anilistId-keyed throughout** — every cross-reference uses anilistId-as-Long. This works on the ANIKUTA-restore side because the `LibraryBackupProvider`/`EpisodeBackupProvider`/`CategoryBackupProvider` all handle anilistId-as-animeId. **The exception is `TrackerBackupProviderImpl.restore`** (§11.9), which doesn't remap.

### 11.11 Backup format — gzipped JSON inside a zip

**File:** `core/backup/src/main/java/app/confused/anikuta/core/backup/format/AnikutaBackupFormat.kt:49-159`

```
backup.anikuta (ZIP)
├── meta.json.gz   — gzipped JSON of BackupContainer
└── covers/        — optional cover images
    ├── 12345.jpg    ← filename = anilistId
    └── 67890.jpg
```

Cover image filename = anilistId (line 80: `"$COVERS_DIR$anilistId.jpg"`). Read-back parses filename → `anilistId.toIntOrNull()` (line 128). **Unlinked anime can't have bundled covers** (consistent with §11.8).

---

## 12. Preferences — every anilistId-keyed preference

I grepped for `anilistId` across `core/preferences` — **zero matches**. The PreferenceStore itself is generic (key-value), but several stores in OTHER modules use anilistId-derived keys:

| Store | File | SharedPreferences key | Key format | Value |
|---|---|---|---|---|
| `WatchProgressStore` | `core/player/.../WatchProgressStore.kt:47` | `pref_watch_progress_map` | `"$anilistId:$episodeUrl"` (composite) | `Progress` JSON |
| `PlaybackStateStore` | `core/player/.../PlaybackStateStore.kt:47` | `pref_playback_state_map` | `"$anilistId:$episodeUrl"` (composite) | `PlaybackState` JSON |
| `SourceLinkStore` | `data/extension/.../SourceLinkStore.kt:67` | `pref_source_links` | `anilistId.toString()` | `SourceLink` JSON |
| `ExtensionLinkStore` | `data/extension/.../ExtensionLinkStore.kt:113` | `pref_extension_anilist_links` | `"$sourceId:$animeUrl"` (composite) | `Int` (anilistId) |
| `DetailsViewPreferenceStore` | `data/extension/.../DetailsViewPreferenceStore.kt:93` | `pref_details_view_preference` | `anilistId.toString()` OR `"ext:$sourceId:$url"` | `DataSource.name` String |
| `EpisodeMetadataCache` | `core/episode-metadata/.../EpisodeMetadataCache.kt:32` | `episode_metadata_cache` | `anilistId.toString()` | JSON of `Map<Int, EpisodeMetadata>` |
| Per-anime source pref (legacy) | `feature/anime-details/.../AnimeDetailViewModel.kt:731` + `data/anime/.../AniListDetailsProvider.kt:281` | `anikuta_source_prefs` (file) → `source_pref_$anilistId` (key) | `"source_pref_$anilistId"` | `Long` (sourceId) |

**Interpretation:** Five distinct SharedPreferences-backed maps + one legacy SharedPreferences file (`anikuta_source_prefs`) use anilistId-derived keys. Of these, only `DetailsViewPreferenceStore` has a parallel `"ext:..."` namespace for unlinked anime. The other four have **no fallback** — unlinked anime are silently excluded.

### 12.1 The legacy `source_pref_$anilistId` SharedPreferences

**`AnimeDetailViewModel.kt:159` + `731`:**
```kotlin
private val sourcePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  // PREFS_NAME = "anikuta_source_prefs"
private fun sourcePrefKey(anilistId: Int) = "source_pref_$anilistId"
```

Used at lines 329, 395 (write) and `AniListDetailsProvider.kt:174` (read):
```kotlin
val explicitPrefId = sourcePrefs.getLong(sourcePrefKey(anilistId), -1L)     // line 174
```

**Interpretation:** This is a SECOND per-anime source preference (in addition to `DetailsViewPreferenceStore`). It's keyed by anilistId only. The KDoc at `DetailsViewPreferenceStore.kt:12-32` doesn't mention this legacy store. **The two stores are not synchronized** — `DetailsViewPreferenceStore.set(anilistId, DataSource.EXTENSION)` and `sourcePrefs.edit().putLong("source_pref_$anilistId", source.id).apply()` are called from different code paths (`saveViewPreference` vs `switchExtension`/`switchSource`) and store different things (DataSource enum vs sourceId Long).

---

## 13. Extensions — SourceLinkStore, ExtensionLinkStore, SourceMatcher

(Covered in detail in §3.5, §3.6, and §10.3 of the SourceMatcher analysis below.)

### 13.1 `SourceMatcher` — title-based matching, returns `SourceMatch`

**File:** `data/extension/src/main/java/app/confused/anikuta/data/extension/matcher/SourceMatcher.kt:46-384`

```kotlin
class SourceMatcher(private val extensionManager: AnimeExtensionManager) {
    data class SourceMatch(val source: AnimeCatalogueSource, val sAnime: SAnime, val score: Double)
    data class ManualSearchResult(val source: AnimeCatalogueSource, val sAnime: SAnime, val sourceName: String, val title: String, val thumbnailUrl: String?)
    data class SourceInfo(val id: Long, val name: String)

    sealed class SourceSearchOutcome<out T> {
        data class Success<T>(val results: List<T>) : SourceSearchOutcome<T>()
        data class Failed(val sourceName: String, val error: String) : SourceSearchOutcome<Nothing>()
    }

    sealed class Result {
        data class Match(val match: SourceMatch) : Result()
        data object NoMatch : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun match(title: String): Result                                       // line 186
    suspend fun matchAll(title: String): List<SourceMatch>                          // line 233 — sequential, priority-ordered, short-circuits on exact match
    suspend fun searchOneSource(sourceId: Long, query: String): SourceSearchOutcome<ManualSearchResult>   // line 149
    fun getAvailableSources(): List<SourceInfo>                                     // line 134
    fun getSourceById(sourceId: Long): AnimeCatalogueSource?                        // line 340
}
```

**Title matching (line 346-364):**
- `normalizeTitle`: lowercase, strip parentheticals, strip "season N" / "Nth season", strip non-alphanumerics, collapse whitespace.
- `similarity`: exact = 1.0, substring = 0.95, else Levenshtein-based.
- `THRESHOLD = 0.80` (line 382).

**`matchAll` behavior (line 233-284):** Sequential search of installed sources in priority order. If a source returns an exact match (score = 1.0), the search short-circuits (line 259-263). Otherwise all sources are searched, results sorted by score descending.

**Interpretation:** SourceMatcher has NO anilistId dependency — it's a pure title→source bridge. It runs entirely on extension-source titles + Aniyomi `SAnime.url`. The anilistId linkage happens AFTER SourceMatcher returns (in `AniListDetailsProvider.loadEpisodes` line 185-190, which calls `sourceLinkStore.saveLink(anilistId, ...)`).

---

## 14. Summary tables

### 14.1 System-by-system AniList dependency matrix

| System | How it identifies anime | AniList dependency | Evidence |
|---|---|---|---|
| **BrowseScreen** | `AniListAnime.id` (Int) | TIGHT — AniList-only | `feature/browse/.../BrowseScreen.kt:164` (`onOpenAnime(item.id)`) |
| **SearchScreen (AniList mode)** | `AniListAnime.id` (Int) | TIGHT — AniList-only | `feature/search/.../SearchViewModel.kt:43` (`val id: Int get() = anime.id`) |
| **SearchScreen (Extension mode)** | `source.id + sAnime.url` (Long, String) | NONE — until linking | `feature/search/.../SearchViewModel.kt:46-50` (SearchResult.Extension) |
| **ExtensionLinkingSheet** | Resolves to `anilistId: Int` OR stays unlinked | MODERATE — auto-link always tries AniList | `feature/search/.../ExtensionLinkingViewModel.kt:32-61` (sealed state) |
| **ExtensionLinkStore** | Key: `"$sourceId:$animeUrl"`, Value: `anilistId: Int` | TIGHT for values — every entry IS a link | `data/extension/.../ExtensionLinkStore.kt:34-115` |
| **SourceLinkStore** | Key: `anilistId.toString()`, Value: `SourceLink` | TIGHT — every key IS an anilistId | `data/extension/.../SourceLinkStore.kt:22-69` |
| **DetailsViewPreferenceStore** | Key: `anilistId.toString()` OR `"ext:$sourceId:$url"` | MODERATE — dual-key namespace | `data/extension/.../DetailsViewPreferenceStore.kt:33-95` |
| **AniListDetailsProvider** | `anilistId: Int` (non-null) — returns null for unlinked | TIGHT — refuses unlinked | `data/anime/.../AniListDetailsProvider.kt:67-69` |
| **ExtensionDetailsProvider** | `sourceId + animeUrl` + optional `anilistId` | LOOSE — full unlinked support | `data/extension/.../ExtensionDetailsProvider.kt:80-184` |
| **UnifiedAnime** | `anilistId: Int?` + `sourceId: Long?` + `url: String` | MODERATE — nullable anilistId | `core/common/.../details/UnifiedAnime.kt:56-86` |
| **AnimeRepository.upsert** | `Anime.id` (Long) primary; `anilistId: Int?` secondary | LOOSE — accepts nullable | `data/anime/.../AnimeRepositoryImpl.kt:70-134` |
| **AnimeRepository.getByAnilistId** | `anilistId: Int` | TIGHT — query method | `data/anime/.../AnimeRepositoryImpl.kt:58-60` |
| **AnimeRepository.getBySourceAndUrl** | `sourceId + url` | NONE — used for unlinked | `data/anime/.../AnimeRepositoryImpl.kt:62-64` |
| **EpisodeRepository** | `animeId: Long` (= `animes._id`) | NONE | `core/common/.../repository/EpisodeRepository.kt:7-26` |
| **CategoryRepository** | `animeId: Long` (= `animes._id`) | NONE | `core/common/.../repository/CategoryRepository.kt:70-90` |
| **HistoryRepository** (unused) | `animeId: Long` (= `animes._id`) | NONE | `core/common/.../repository/HistoryRepository.kt:7-18` |
| **TrackRepository** | `animeId: Long` (= `animes._id`) + `trackerId: Int` + `remoteId: Int` | NONE at DB layer | `core/tracker/.../TrackRepository.kt:13-101` |
| **WatchProgressStore** | Key: `"$anilistId:$episodeUrl"` | TIGHT — anilistId is half the key | `core/player/.../WatchProgressStore.kt:64` |
| **PlaybackStateStore** | Key: `"$anilistId:$episodeUrl"` | TIGHT — same as WatchProgressStore | `core/player/.../PlaybackStateStore.kt:60` |
| **TrackSyncManager** | Parses anilistId from WatchProgressStore keys; bridges via `getByAnilistId` | TIGHT — depends on WatchProgressStore keying | `core/tracker/.../TrackSyncManager.kt:73-104` |
| **DownloadManager** | `anilistId: Int` (primary key for ALL methods) | TIGHT — non-nullable in every method | `core/download/.../DownloadManager.kt:30-122` |
| **DownloadAnimeInfo** | `anilistId: Int` (non-nullable) | TIGHT — required field | `core/download/.../DownloadModels.kt:26-31` |
| **DownloadTask** | `id: Long` + composite `key = "${anilistId}:${episodeUrl}"` | TIGHT — key is anilistId-derived | `core/download/.../DownloadTask.kt:41` |
| **DownloadStorageProvider** | Folder name `"Anime Title [anilistId]"` | TIGHT — anilistId in path | `core/download/.../DownloadStorageProvider.kt:88` |
| **DownloadOrchestrator (AppController.downloadEpisode)** | `anilistId: Int` — hard-gates on `== 0` | TIGHT — refuses anilistId = 0 | `app/.../AppController.kt:509-512` |
| **EpisodeMetadataRepository / Cache** | `animeId: Int` (the AniList ID per KDoc) | TIGHT — anilistId-keyed cache | `core/episode-metadata/.../EpisodeMetadataCache.kt:55` |
| **AnimeDetailViewModel.fetchEpisodeMetadata** | Skips if `anime.anilistId == null` | TIGHT — explicit null check | `feature/anime-details/.../AnimeDetailViewModel.kt:629-632` |
| **UpdateChecker** | `anime.anilistId?.let { anilistApi.fetchById(it) }` | MODERATE — best-effort AniList cross-ref | `core/update-checker/.../UpdateChecker.kt:363-369` |
| **UpdatesViewModel.fetchSchedule** | `library.mapNotNull { it.anilistId }` — skips nulls | TIGHT — null anilistId silently skipped | `feature/updates/.../UpdatesViewModel.kt:136-145` |
| **LibraryViewModel.deriveContinueWatching** | Parses anilistId from WatchProgressStore keys | TIGHT — depends on key format | `feature/library/.../LibraryViewModel.kt:179-215` |
| **LibraryViewModel.updateLastWatched** | `anilistId: Int` — calls `updateLastWatchedByAnilistId` | TIGHT — silently no-ops for unlinked | `feature/library/.../LibraryViewModel.kt:391-399` |
| **HistoryViewModel** | Parses anilistId from WatchProgressStore keys | TIGHT — depends on key format | `feature/history/.../HistoryViewModel.kt:79, 133-140` |
| **AnimeBackup (model)** | `anilistId: Long?` (nullable) | LOOSE — nullable in schema | `core/backup/.../model/AnimeBackup.kt:43` |
| **LibraryBackupProvider** | Tries anilistId first, falls back to source+url | LOOSE — dual-key | `core/backup/.../AnimeBackupProviders.kt:128-132` |
| **EpisodeBackupProvider** | Key: anilistId OR `"sourceId:url"` | LOOSE — dual-key | `core/backup/.../EpisodeBackupProvider.kt:42, 85-103` |
| **WatchProgressBackupProvider** | Preserves composite key verbatim; GATES import on `anilistId > 0` | TIGHT — drops 0-entries | `core/backup/.../WatchProgressBackupProvider.kt:62-86` |
| **CategoryBackupProvider** | Builds anilistId→localId remap; falls back to direct _id | LOOSE — handles both | `core/backup/.../CategoryBackupProvider.kt:61-69, 105-112` |
| **SourceLinkBackupProvider** | Keys are anilistId strings; preserved verbatim | TIGHT — anilistId IS the key | `core/backup/.../SourceLinkBackupProvider.kt:26-96` |
| **CoverImageProvider** | Key: `anilistId.toString()`; gates on `anilistId != null` | TIGHT — unlinked excluded | `core/backup/.../CoverImageProvider.kt:42-46` |
| **TrackerBackupProviderImpl** | `animeId` used as-is (no remap) | MODERATE — but bug for Aniyomi-translated | `core/tracker/.../TrackerBackupProviderImpl.kt:87` |
| **AniyomiBackupTranslator** | Resolves anilistId via tracker / MAL / title search | TIGHT — requires anilistId for every anime | `core/backup/.../AniyomiBackupTranslator.kt:232-269` |
| **AnikutaBackupFormat (file structure)** | Cover filenames = `"$anilistId.jpg"` | TIGHT — anilistId in filename | `core/backup/.../format/AnikutaBackupFormat.kt:80, 127-130` |
| **PreferencesBackupProvider** | Generic key-value; no anilistId-specific logic | NONE | `core/backup/.../PreferencesBackupProvider.kt:28-97` |

### 14.2 Every `anilistId` parameter or composite-key component

| File:line | Signature / Usage |
|---|---|
| `core/common/.../model/Anime.kt:44` | `val anilistId: Int?` (domain model field) |
| `core/common/.../model/details/UnifiedAnime.kt:59` | `val anilistId: Int?` |
| `core/common/.../model/details/DetailsRequest.kt:20` | `data class ByAniListId(val anilistId: Int)` |
| `core/common/.../model/details/DetailsRequest.kt:39` | `ByExtension(..., val anilistId: Int? = null)` |
| `core/common/.../repository/AnimeRepository.kt:27` | `fun observeByAnilistId(anilistId: Int): Flow<Anime?>` |
| `core/common/.../repository/AnimeRepository.kt:31` | `suspend fun getByAnilistId(anilistId: Int): Anime?` |
| `core/common/.../repository/AnimeRepository.kt:41` | `suspend fun updateFavoriteByAnilistId(anilistId: Int, favorite: Boolean, dateAdded: Long)` |
| `core/common/.../repository/AnimeRepository.kt:51` | `suspend fun updateLastWatchedByAnilistId(anilistId: Int, lastWatched: Long)` |
| `core/common/.../repository/AnimeRepository.kt:53-61` | `suspend fun updateAnilistMetadata(anilistId: Int, title: String, coverUrl: String?, coverColor: String?, score: Double?, totalEpisodes: Int?, nextAiringEpisode: Int?)` |
| `core/common/.../repository/AnimeRepository.kt:68` | `suspend fun updatePreferredCoverByAnilistId(anilistId: Int, coverUrl: String?, coverColor: String?)` |
| `core/anilist/.../model/AniListAnime.kt:14` | `val id: Int` (the AniList media ID — implicit anilistId) |
| `core/anilist/.../model/AniListAnime.kt:118` | `data class AiringScheduleInfo(val anilistId: Int, ...)` |
| `core/anilist/.../details/AniListAnimeMapper.kt:39` | `anilistId = id` (in `toUnifiedAnime`) |
| `core/database/.../animes.sq:48` | `selectByAnilistId: SELECT * FROM animes WHERE anilist_id = :anilistId` |
| `core/database/.../animes.sq:51` | `selectIdByAnilistId: SELECT _id FROM animes WHERE anilist_id = :anilistId` |
| `core/database/.../animes.sq:99` | `updateFavoriteByAnilistId` |
| `core/database/.../animes.sq:114` | `updateLastWatchedByAnilistId` |
| `core/database/.../animes.sq:124` | `updateAnilistMetadataByAnilistId` |
| `core/database/.../animes.sq:133` | `updatePreferredCoverByAnilistId` |
| `core/player/.../WatchProgressStore.kt:64` | `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"` |
| `core/player/.../WatchProgressStore.kt:74-83` | `fun save(anilistId: Int, episodeUrl: String, ...)` |
| `core/player/.../WatchProgressStore.kt:100` | `fun get(anilistId: Int, episodeUrl: String): Progress?` |
| `core/player/.../WatchProgressStore.kt:105` | `fun clear(anilistId: Int, episodeUrl: String)` |
| `core/player/.../WatchProgressStore.kt:112` | `fun clearAnime(anilistId: Int)` |
| `core/player/.../PlaybackStateStore.kt:60` | `private fun key(anilistId: Int, episodeUrl: String) = "$anilistId:$episodeUrl"` |
| `core/player/.../PlaybackStateStore.kt:63-74` | `fun save(anilistId: Int, episodeUrl: String, ...)` |
| `core/player/.../PlaybackStateStore.kt:91` | `fun get(anilistId: Int, episodeUrl: String): PlaybackState?` |
| `core/player/.../PlaybackStateStore.kt:99` | `fun clear(anilistId: Int, episodeUrl: String)` |
| `core/download/.../DownloadModels.kt:27` | `data class DownloadAnimeInfo(val anilistId: Int, ...)` |
| `core/download/.../DownloadManager.kt:70` | `suspend fun deleteAnimeDownloads(anilistId: Int)` |
| `core/download/.../DownloadManager.kt:97` | `suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String): Boolean` |
| `core/download/.../DownloadManager.kt:103` | `suspend fun getDownloadedVideoUri(anilistId: Int, episodeUrl: String): String?` |
| `core/download/.../DownloadManager.kt:109` | `suspend fun getDownloadedSubtitleUris(anilistId: Int, episodeUrl: String): List<String>` |
| `core/download/.../DownloadManager.kt:112` | `suspend fun getDownloadedEpisodes(anilistId: Int): List<DownloadedEpisode>` |
| `core/download/.../DownloadTask.kt:41` | `val key: String get() = "${request.anime.anilistId}:${request.episode.episodeUrl}"` |
| `core/download/.../DownloadQueue.kt:309-310` | `private fun keyFor(request: DownloadRequest): String = "${request.anime.anilistId}:${request.episode.episodeUrl}"` |
| `core/download/.../DefaultDownloadManager.kt:163` | `override suspend fun isEpisodeDownloaded(anilistId: Int, episodeUrl: String)` |
| `core/download/.../DefaultDownloadManager.kt:171` | `override suspend fun getDownloadedVideoUri(anilistId: Int, episodeUrl: String)` |
| `core/download/.../DefaultDownloadManager.kt:177-180` | `override suspend fun getDownloadedSubtitleUris(anilistId: Int, episodeUrl: String)` |
| `core/download/.../DefaultDownloadManager.kt:187` | `override suspend fun getDownloadedEpisodes(anilistId: Int)` |
| `core/download/.../DefaultDownloadManager.kt:202-204` | `private fun findTask(anilistId: Int, episodeUrl: String): DownloadTask?` |
| `core/download/.../DownloadStorageProvider.kt:88` | `return "$safeTitle [${anime.anilistId}]"` |
| `core/download/.../DownloadStorageProvider.kt:345` | `fun deleteAnime(anilistId: Int, animeTitle: String): Boolean` |
| `core/download/.../DownloadStorageProvider.kt:455` | `data class EpisodeMetadataCache(val anilistId: Int, ...)` |
| `core/episode-metadata/.../EpisodeMetadata.kt:16` | `data class EpisodeMetadata(val animeId: Int, ...)` — KDoc: "the AniList anime ID" |
| `core/episode-metadata/.../EpisodeMetadata.kt:38` | `data class EpisodeMetadataRequest(val animeId: Int, ...)` — KDoc: "The AniList anime ID" |
| `core/episode-metadata/.../EpisodeMetadataCache.kt:55` | `fun get(animeId: Int): Map<Int, EpisodeMetadata>?` — key = anilistId |
| `core/episode-metadata/.../EpisodeMetadataCache.kt:67` | `fun save(animeId: Int, ...)` |
| `core/episode-metadata/.../EpisodeMetadataCache.kt:74` | `fun clear(animeId: Int)` |
| `core/episode-metadata/.../source/anikage/AnikageCcSource.kt:50` | `.url("https://anikage.cc/api/media/anime/${request.animeId}/episodes")` |
| `core/episode-metadata/.../source/anilist/AniListStreamingSource.kt:47` | GraphQL `Media(id: ${request.animeId}, type: ANIME)` |
| `core/tracker/.../TrackSyncManager.kt:74-82` | `private fun extractAnilistId(progressMap, progress): Int` |
| `core/tracker/.../TrackSyncManager.kt:85` | `private suspend fun syncAnimeProgress(anilistId: Int, progress: Progress)` |
| `core/update-checker/.../UpdateCheckerPreferences.kt:116` | `data class StoredResult(val animeId: Long, val anilistId: Int? = null, ...)` |
| `core/update-checker/.../UpdateChecker.kt:363` | `anime.anilistId?.let { aid -> anilistApi.fetchById(aid) }` |
| `data/anime/.../AnimeRepositoryImpl.kt:49-52` | `observeByAnilistId(anilistId: Int)` |
| `data/anime/.../AnimeRepositoryImpl.kt:58-60` | `getByAnilistId(anilistId: Int)` |
| `data/anime/.../AnimeRepositoryImpl.kt:145-152` | `updateFavoriteByAnilistId(anilistId: Int, ...)` |
| `data/anime/.../AnimeRepositoryImpl.kt:170-175` | `updateLastWatchedByAnilistId(anilistId: Int, ...)` |
| `data/anime/.../AnimeRepositoryImpl.kt:177-195` | `updateAnilistMetadata(anilistId: Int, ...)` |
| `data/anime/.../AnimeRepositoryImpl.kt:197-205` | `updatePreferredCoverByAnilistId(anilistId: Int, ...)` |
| `data/anime/.../AniListDetailsProvider.kt:174` | `sourcePrefs.getLong(sourcePrefKey(anilistId), -1L)` |
| `data/anime/.../AniListDetailsProvider.kt:281` | `private fun sourcePrefKey(anilistId: Int) = "source_pref_$anilistId"` |
| `data/extension/.../cache/ExtensionLinkStore.kt:62` | `private fun key(sourceId: Long, animeUrl: String) = "$sourceId:$animeUrl"` (value = anilistId) |
| `data/extension/.../cache/ExtensionLinkStore.kt:68` | `fun getAniListId(sourceId: Long, animeUrl: String): Int?` |
| `data/extension/.../cache/ExtensionLinkStore.kt:84` | `fun getPreferredSourceForAnilist(anilistId: Int): Long?` |
| `data/extension/.../cache/ExtensionLinkStore.kt:96` | `fun link(sourceId: Long, animeUrl: String, anilistId: Int)` |
| `data/extension/.../cache/SourceLinkStore.kt:45` | `fun getLink(anilistId: Int): SourceLink?` |
| `data/extension/.../cache/SourceLinkStore.kt:48` | `fun saveLink(anilistId: Int, sourceId: Long, animeUrl: String, animeTitle: String)` |
| `data/extension/.../cache/SourceLinkStore.kt:55` | `fun removeLink(anilistId: Int)` |
| `data/extension/.../cache/DetailsViewPreferenceStore.kt:52` | `fun get(anilistId: Int): DataSource?` |
| `data/extension/.../cache/DetailsViewPreferenceStore.kt:58` | `fun set(anilistId: Int, dataSource: DataSource)` |
| `data/extension/.../cache/DetailsViewPreferenceStore.kt:72` | `fun remove(anilistId: Int)` |
| `data/extension/.../details/ExtensionDetailsProvider.kt:170` | `val effectiveAnilistId = anilistId ?: extensionLinkStore.getAniListId(source.id, animeUrl)` |
| `feature/anime-details/.../AnimeDetailViewModel.kt:254` | `val anilistId = currentAnilistId()` |
| `feature/anime-details/.../AnimeDetailViewModel.kt:325` | `val anilistId = currentAnilistId()` |
| `feature/anime-details/.../AnimeDetailViewModel.kt:393` | `val anilistId = currentAnilistId()` |
| `feature/anime-details/.../AnimeDetailViewModel.kt:580-588` | `private fun currentAnilistId(): Int?` |
| `feature/anime-details/.../AnimeDetailViewModel.kt:731` | `private fun sourcePrefKey(anilistId: Int) = "source_pref_$anilistId"` |
| `feature/library/.../LibraryViewModel.kt:391` | `fun updateLastWatched(anilistId: Int)` |
| `feature/library/.../LibraryState.kt:29` | `data class ContinueWatchingItem(val anilistId: Int, ...)` |
| `feature/history/.../HistoryState.kt:28` | `data class HistoryEntry(val anilistId: Int, val episodeUrl: String, ...)` |
| `feature/updates/.../UpdatesState.kt:41` | `data class ScheduleEntry(val anilistId: Int, ...)` |
| `feature/download/.../DownloadUiState.kt:30` | `data class DownloadedAnimeKey(val anilistId: Int, ...)` |
| `feature/download/.../DownloadViewModel.kt:78` | `fun deleteAnime(anilistId: Int)` |
| `feature/watch/.../WatchRequest.kt:22` | `val anilistId: Int` (non-nullable) |
| `feature/watch/.../WatchScreen.kt:515, 644, 682` | `watchRequest.anilistId` passed to WatchProgressStore |
| `app/.../navigation/AppController.kt:363` | `resolveEpisode(..., anilistId: Int)` |
| `app/.../navigation/AppController.kt:370` | `if (anilistId != 0 && downloadManager.isEpisodeDownloaded(anilistId, episode.url))` |
| `app/.../navigation/AppController.kt:468` | `anilistId = target.anilistId` (in WatchRequest construction) |
| `app/.../navigation/AppController.kt:507, 509` | `downloadEpisode(..., anilistId: Int)` + the `== 0` gate |
| `app/.../navigation/AppController.kt:513-517` | `DownloadAnimeInfo(anilistId = anilistId, ...)` |
| `app/.../navigation/AppController.kt:576, 588, 593, 598` | `cancelDownload / resumeDownload / retryDownload / deleteDownload(anilistId: Int, episodeUrl: String)` |
| `app/.../navigation/AppController.kt:617-618` | `getDownloadStates(anilistId: Int, tasksMap)` |
| `app/.../navigation/AppController.kt:622` | `it.startsWith("$anilistId:")` |
| `app/.../navigation/AppController.kt:277` | `fun switchAnilistAnime(currentAnilistId: Int, newAnilistId: Int)` |
| `app/.../navigation/Destinations.kt:173` | `val downloadKey = anilistId ?: 0` |
| `app/.../navigation/Destinations.kt:186` | `anilistId = anilistId ?: 0` |
| `app/.../navigation/Destinations.kt:190` | `appController.downloadEpisode(episode, src, watchCtx, downloadKey)` |

### 14.3 Every `anilistId == 0` / `anilistId == null` check

| File:line | Check | Consequence |
|---|---|---|
| `app/.../AppController.kt:370` | `if (anilistId != 0 && downloadManager.isEpisodeDownloaded(...))` | Offline-playback short-circuit SKIPPED for unlinked (anilistId = 0) |
| `app/.../AppController.kt:509` | `if (anilistId == 0) { Toast "Cannot download — anime not linked"; return }` | Download BLOCKED for unlinked |
| `app/.../AppController.kt:199` | `if (anilistId != null) { pushDetail(anilistId); return }` | Library open: linked → AniList detail, unlinked → extension detail |
| `app/.../Destinations.kt:201` | `if (anilistId != null) { appController.switchAnilistAnime(anilistId, newId) }` | "Switch anime" only works for linked extension anime |
| `feature/anime-details/.../AnimeDetailViewModel.kt:256` | `if (anilistId != null) { viewPreferenceStore.set(anilistId, dataSource) } else { ... set(sourceId, url, dataSource) }` | View-pref save branches on linked/unlinked |
| `feature/anime-details/.../AnimeDetailViewModel.kt:295` | `if (anilistId != null) { animeRepository.updatePreferredCoverByAnilistId(...) } else { ... updatePreferredCoverBySourceAndUrl(...) }` | Library cover update branches |
| `feature/anime-details/.../AnimeDetailViewModel.kt:327` | `if (anilistId != null) { sourceLinkStore.saveLink(...); sourcePrefs.edit().putLong(...).apply() }` | Source link save is linked-only (unlinked don't persist source-pref) |
| `feature/anime-details/.../AnimeDetailViewModel.kt:394` | `if (anilistId != null) { sourcePrefs.edit().putLong(sourcePrefKey(anilistId), match.source.id).apply() }` | switchSource's per-anime source pref save is linked-only |
| `feature/anime-details/.../AnimeDetailViewModel.kt:569` | `if (anilistId != null) DetailsRequest.ByAniListId(anilistId) else current` | Can't switch to AniList view if unlinked |
| `feature/anime-details/.../AnimeDetailViewModel.kt:629-632` | `val anilistId = anime.anilistId ?: run { Log.i("Skipping episode metadata — no anilistId"); return }` | Episode metadata SKIPPED for unlinked |
| `feature/anime-details/.../AnimeDetailViewModel.kt:654` | `anilistId != null -> animeRepository.getByAnilistId(anilistId); sourceId != null -> getBySourceAndUrl(...)` | Library lookup branches |
| `feature/anime-details/.../AnimeDetailViewModel.kt:671` | `if (anilistId != null) { observeByAnilistId(...).collect { ... } } else { poll after each load }` | Library save-state observation: linked = reactive, unlinked = polling |
| `feature/anime-details/.../SourceSwitcherMenu.kt:110` | `if (anime.anilistId != null) { ... "View from AniList" menu item ... }` | "View from AniList" menu item hidden for unlinked |
| `feature/anime-details/.../SourceSwitcherMenu.kt:129` | `if (anime.anilistId != null) { "Switch anime" } else { "Link to AniList" }` | Menu item label branches |
| `feature/library/.../LibraryScreen.kt:212, 237` | `if (anilistId != null) onOpenAnime(anilistId) else onOpenExtensionAnime(anime)` | Library tap branches |
| `feature/library/.../LibraryViewModel.kt:185, 191` | `anime.anilistId?.let { it to anime }`; `entry.key.substringBefore(':').toIntOrNull() ?: continue` | Continue-watching drops entries with unparseable anilistId |
| `feature/updates/.../UpdatesScreen.kt:260-262` | `result.anime.anilistId?.let { anilistId -> onOpenAnime(anilistId, result.anime.id) }` | Updates tap no-ops for unlinked |
| `feature/updates/.../UpdatesViewModel.kt:136-140` | `val ids = library.mapNotNull { it.anilistId }` + log `"${library.size - ids.size} skipped — null anilistId"` | Schedule fetch silently skips unlinked |
| `feature/my/.../BehindStatusSection.kt:80` | `val anilistId = item.anime.anilistId ?: return` | "Behind" row early-returns for unlinked (no navigation) |
| `feature/my/.../GenreAnimeSheet.kt:112` | `animeItem.anilistId?.let { onOpenAnime(it) }` | Genre-sheet anime tap no-ops for unlinked |
| `data/anime/.../AniListDetailsProvider.kt:69` | `if (anilistId != null) loadByAniListId(anilistId) else null` | AniList provider returns null for unlinked |
| `data/anime/.../AniListDetailsProvider.kt:214` | `if (anilistId != null) { saveEpisodesToDb(...) }` | Episodes persisted to DB only for linked anime (via this provider) |
| `data/extension/.../ExtensionDetailsProvider.kt:116` | `anilistId != null -> animeRepository.getByAnilistId(anilistId); else -> getBySourceAndUrl(sourceId, animeUrl)` | DB-first short-circuit branches |
| `data/extension/.../ExtensionDetailsProvider.kt:170-171` | `val effectiveAnilistId = anilistId ?: extensionLinkStore.getAniListId(...); if (effectiveAnilistId != null) { AniList merge }` | AniList merge skipped for unlinked |
| `data/extension/.../ExtensionDetailsProvider.kt:305` | `var dbAnime = if (anilistId != null) { getByAnilistId(...) } else { getBySourceAndUrl(...) }` | Episode persistence branches |
| `core/tracker/.../TrackSyncManager.kt:62` | `if (anilistId <= 0) continue` | Tracker sync SKIPS anilistId ≤ 0 |
| `core/backup/.../AnikutaBackupFormat.kt:129` | `if (anilistId != null) { covers[anilistId] = zip.readBytes() }` (in readCovers) | Cover-image restore: unlinked excluded |
| `core/backup/.../provider/WatchProgressBackupProvider.kt:70` | `if (anilistId > 0 && episodeUrl.isNotEmpty()) { save(...) } else { skipped++ }` | Watch-progress import DROPS entries with anilistId ≤ 0 |
| `core/backup/.../provider/CoverImageProvider.kt:44` | `if (anilistId != null && !coverUrl.isNullOrBlank()) { covers[anilistId.toString()] = coverUrl }` | Cover-image export: unlinked excluded |
| `core/backup/.../provider/AnimeBackupProviders.kt:128` | `if (anime.anilistId != null) { selectIdByAnilistId(...) } else { selectBySourceAndUrl(...) }` | Library/AnimeDetails import: dual-key resolution |
| `core/backup/.../provider/CategoryBackupProvider.kt:66` | `if (anime.anilistId != null) { anilistToLocalId[anime.anilistId] = anime._id }` | Category remap built from linked anime only (unlinked fall back to direct _id) |
| `core/backup/.../provider/EpisodeBackupProvider.kt:89` | `if (anilistId != null) { selectIdByAnilistId(...) }` | Episode import: tries anilistId first |
| `core/backup/.../provider/SourceLinkBackupProvider.kt:64` | `if (anilistId != null) { sourceLinkStore.saveLink(...) }` | SourceLink restore: only linked anime |
| `core/update-checker/.../UpdateChecker.kt:363` | `anime.anilistId?.let { aid -> try { anilistApi.fetchById(aid) } catch ... }` | AniList cross-ref best-effort, skipped for unlinked |

---

## 15. ASCII flow diagram — anime identity from discovery → backup

```
                        DISCOVERY
                        =========
   ┌─────────────────────────────────────────────────────────────┐
   │  BrowseScreen (AniList-only)                                 │
   │    data: AniListAnime                                        │
   │    identity: AniListAnime.id : Int  ◄─── NON-NULLABLE        │
   │    onOpenAnime(id: Int) → AppController.pushDetail(id)       │
   └─────────────────────────┬───────────────────────────────────┘
                             │
   ┌─────────────────────────┴───────────────────────────────────┐
   │  SearchScreen (dual-source)                                  │
   │  ┌──────────────────────┐  ┌──────────────────────────────┐ │
   │  │ SearchResult.AniList │  │ SearchResult.Extension       │ │
   │  │  id: Int             │  │  source + sAnime             │ │
   │  │  (non-null)          │  │  (NO anilistId)              │ │
   │  └──────────┬───────────┘  └──────────┬───────────────────┘ │
   └─────────────┼─────────────────────────┼─────────────────────┘
                 │                         │
                 │                         ▼
                 │       ┌─────────────────────────────────────────┐
                 │       │ ExtensionLinkingViewModel               │
                 │       │  1. linkStore.getAniListId(source,url)  │
                 │       │     → cache hit? Linked(anilistId)      │
                 │       │  2. anilistApi.searchAnime(sAnime.title)│
                 │       │     → first result? auto-link           │
                 │       │  3. no match? NeedsManualLink           │
                 │       │  4. user picks "go without"?            │
                 │       │     → GoWithoutLinking (anilistId=null) │
                 │       └────────┬──────────────┬─────────────────┘
                 │                │              │
                 │    linked      │              │ unlinked
                 │                ▼              ▼
                 │   ┌──────────────────┐  ┌──────────────────┐
                 │   │ Linked(anilistId)│  │ GoWithoutLinking │
                 │   │    : Int         │  │   anilistId=null │
                 │   └────────┬─────────┘  └────────┬─────────┘
                 │            │                     │
                 ▼            ▼                     ▼
   ════════════════════════════════════════════════════════════════
   DETAILS PAGE (unified)
   ════════════════════════════════════════════════════════════════
   DetailsRequest (sealed):
     • ByAniListId(anilistId: Int)                  ◄── NON-NULLABLE
     • ByExtension(sourceId, animeUrl, animeTitle,
                   anilistId: Int? = null)          ◄── NULLABLE

   AnimeDetailViewModel.currentAnilistId(): Int?
     • ByAniListId  → req.anilistId
     • ByExtension  → req.anilistId
                      ?: extensionLinkStore.getAniListId(sourceId, url)

   Provider dispatch (registry.forSource(currentDataSource)):
     • AniListDetailsProvider.load:
         - ByAniListId  → loadByAniListId(anilistId)
         - ByExtension  → anilistId ?: linkStore.getAniListId(...)
                          if null → returns null  ◄── UNLINKED EXCLUDED
     • ExtensionDetailsProvider.load:
         - ByExtension  → loadByExtension(..., anilistId: Int?)
                          DB-first: anilistId != null
                                       → getByAnilistId
                                     else → getBySourceAndUrl  ◄── UNLINKED OK
                          AniList merge: effectiveAnilistId != null
                                       → fetchById + merge
                                     else → skip merge         ◄── UNLINKED OK
         - ByAniListId  → needs SourceLinkStore.getLink(anilistId)
                          if null → returns null

   UnifiedAnime.anilistId: Int?  (null for unlinked extension anime)

   Episode metadata enrichment (fetchEpisodeMetadata):
     anime.anilistId ?: return   ◄── UNLINKED SKIPPED
                 │
                 ▼
   ════════════════════════════════════════════════════════════════
   LIBRARY SAVE (AnimeDetailViewModel.toggleSave → saveAnimeToLibrary)
   ════════════════════════════════════════════════════════════════
   findLibraryAnime(unified):
     anilistId != null → getByAnilistId(anilistId)
     sourceId  != null → getBySourceAndUrl(sourceId, url)

   saveAnimeToLibrary → AnimeRepository.upsert:
     Anime(id=0, anilistId = unified.anilistId, ...)
       ▼
   animes table (anilist_id nullable, partial unique index)
     _id (Long, AUTOINCREMENT) is the PK — NOT anilistId
                 │
                 ▼
   ════════════════════════════════════════════════════════════════
   WATCH FLOW (AppController.resolveEpisode → WatchScreen)
   ════════════════════════════════════════════════════════════════
   resolveEpisode(..., anilistId: Int):   ◄── caller passes 0 if unlinked
     if (anilistId != 0 && downloadManager.isEpisodeDownloaded(...))
        ▲                              ▲
        └── offline short-circuit      └── SKIPPED for anilistId = 0
            (also blocked because no download could exist — see below)

   WatchRequest(anilistId: Int, ...)   ◄── NON-NULLABLE Int (0 for unlinked)

   WatchScreen saves progress:
     watchProgressStore.save(anilistId, episodeUrl, ...)
       key = "$anilistId:$episodeUrl"
       if anilistId = 0 → key = "0:<url>"  ◄── POLLUTES THE MAP
                 │
                 ▼
   ════════════════════════════════════════════════════════════════
   DOWNLOAD FLOW (AppController.downloadEpisode)
   ════════════════════════════════════════════════════════════════
   downloadEpisode(..., anilistId: Int):
     if (anilistId == 0) {              ◄── HARD GATE
        Toast "Cannot download — anime not linked"
        return
     }
     animeInfo = DownloadAnimeInfo(anilistId, title, coverUrl)
     downloadOrchestrator.enqueueDownload(animeInfo, episode, source)

   DownloadTask.key = "${anime.anilistId}:${episode.episodeUrl}"
   DownloadQueue.enqueue dedup: firstOrNull { it.key == keyFor(request) }

   On-disk folder: "<ANIKUTA>/downloads/anime/<Title [anilistId]>/Episode NNN/"

   DownloadManager.episodeDownloadStates: Map<String, DownloadTask>
     keyed by "$anilistId:$episodeUrl"
                 │
                 ▼
   ════════════════════════════════════════════════════════════════
   HISTORY (WatchProgressStore — JSON-in-SharedPreferences)
   ════════════════════════════════════════════════════════════════
   Pref key: "pref_watch_progress_map"
   Map key: "$anilistId:$episodeUrl"
   Map value: Progress(positionSeconds, durationSeconds, title, updatedAt,
                       coverUrl?, animeTitle?, episodeNumber, thumbnailUrl?)

   HistoryViewModel.parseKey(key):
     key.substringBefore(':').toIntOrNull() ?: 0
     → HistoryEntry(anilistId, episodeUrl, progress)

   HistoryScreen row tap: onOpenAnime(entry.anilistId)
     if anilistId = 0 → AnimeDetailDestination(0) → AniList fetchById(0)
                       → error state  ◄── UNOPENABLE HISTORY ROWS

   (SQLDelight `animehistory` table exists but is UNUSED — HistoryViewModel
    reads from WatchProgressStore, not from HistoryRepository)
                 │
                 ▼
   ════════════════════════════════════════════════════════════════
   TRACKER SYNC (TrackSyncManager)
   ════════════════════════════════════════════════════════════════
   Listens to WatchProgressStore.changes
   extractAnilistId: parse "$anilistId:$episodeUrl" → anilistId
   for each anilistId:
     if (anilistId <= 0) continue              ◄── UNLINKED SKIPPED
     anime = animeRepository.getByAnilistId(anilistId) ?: return
     tracks = trackRepository.getTracks(anime.id)    ◄── animes._id, NOT anilistId
     for each track:
       tracker.updateProgress(remoteAnimeId = track.remoteId, ...)

   animetrack table:
     anime_id : Long (= animes._id, NOT anilistId)
     tracker_id : Long (1=MAL, 2=AniList)
     remote_id : Long (AniList mediaId OR MAL anime id)
     UNIQUE(anime_id, tracker_id)
                 │
                 ▼
   ════════════════════════════════════════════════════════════════
   BACKUP / RESTORE
   ════════════════════════════════════════════════════════════════
   AnikutaBackupFormat → ZIP{meta.json.gz, covers/<anilistId>.jpg}

   BackupContainer (schemaVersion=1) → List<BackupEntry>:
     • Library / AnimeDetails → AnimeBackup(anilistId: Long?)
         upsertAnime: anilistId != null → selectIdByAnilistId
                      else              → selectBySourceAndUrl
     • Episodes → Map<key, List<EpisodeBackup>>
         key = anilistId?.toString() ?: "${sourceId}:${url}"
     • WatchProgress → Map<String, WatchProgressItem>
         key = "$anilistId:$episodeUrl" (verbatim)
         import GATES on anilistId > 0  ◄── UNLINKED ENTRIES DROPPED
     • SourceLinks → SourceLinkBackup(sourceLinks, extensionLinks)
         sourceLinks: Map<anilistId.toString(), SourceLinkItem>
         extensionLinks: Map<"$sourceId:$animeUrl", Int(anilistId)>
     • Tracker → TrackerBackupModel(anilistToken, malOAuthJson, bindings)
         bindings: List<TrackerTrackItem(animeId, trackerId, remoteId, ...)>
         ⚠ animeId is animes._id from backup device — NOT remapped
         ⚠ Aniyomi-translated path: animeId = anilistId.toLong() — MISMATCH
     • Categories → List<CategoryBackup> + List<AnimeCategoryBackup>
         builds anilistId→localDbId remap; falls back to direct _id
     • CoverImages → Map<anilistId.toString(), coverUrl>
         export GATES on anilistId != null  ◄── UNLINKED EXCLUDED
     • Preferences → bulk key-value dump
     • EpisodeMetadata → Map<anilistId.toString(), Map<epNum, item>>

   AniyomiBackupTranslator:
     resolveAnilistId(ani) via tracker → MAL → title search
     builds BackupContainer anilistId-keyed throughout
```

---

## 16. The five concrete failure modes for unlinked extension anime

Based on the evidence above, here's exactly what breaks when `Anime.anilistId == null` (or `== 0` at the WatchRequest level):

1. **Downloads are impossible** (`AppController.kt:509-512`). The user gets a Toast. No `DownloadTask` is created. No folder is created on disk.

2. **Episode metadata enrichment is skipped** (`AnimeDetailViewModel.kt:629-632`). The user sees raw extension episode names only — no titles/descriptions/thumbnails/air dates from Jikan/AniList/Anikage.

3. **Watch progress is saved under key `"0:<episodeUrl>"`** (`WatchScreen.kt:644, 682` + `WatchProgressStore.kt:64`). This:
   - Collides across different unlinked anime that share an episode URL.
   - Cannot be cross-referenced back to the library anime (no `animes.anilist_id = 0`).
   - Shows up in History as a row that, when tapped, pushes `AnimeDetailDestination(0)` → AniList `fetchById(0)` → error state.

4. **Tracker sync is skipped** (`TrackSyncManager.kt:62`). The user's progress on unlinked anime never reaches AniList/MAL.

5. **Backup partially excludes them**:
   - `WatchProgressBackupProvider.kt:70` drops `"0:..."` entries on import.
   - `CoverImageProvider.kt:44` excludes them from cover-image bundle.
   - `SourceLinkBackupProvider.kt:64` excludes them from source-link restore.
   - `LibraryBackupProvider` / `EpisodeBackupProvider` / `CategoryBackupProvider` DO handle them (via the source+url fallback), so the library + episodes + categories DO round-trip — but the cross-cutting state (progress, covers, links) does not.

6. **Updates schedule silently skips them** (`UpdatesViewModel.kt:136-140`). Unlinked library anime are not included in the schedule fetch (which uses `library.mapNotNull { it.anilistId }`).

7. **Library "last watched" sort breaks** (`LibraryViewModel.kt:391-399`). `updateLastWatched(anilistId: Int)` calls `updateLastWatchedByAnilistId(anilistId, ...)` which UPDATEs zero rows for unlinked anime (their `anilist_id` column is NULL, not 0). The `last_watched` column on the `animes` row never updates for unlinked anime, so they sort as if never watched.

---

## 17. Files read for this evidence

**Code files read in full:**
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Anime.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Episode.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/History.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Track.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Source.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/AnimeCategoryLink.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/Category.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/AnimeDetailsProvider.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/UnifiedAnime.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/DetailsRequest.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/AnimeDetailsProviderRegistry.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/model/details/DataSource.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/AnimeRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/EpisodeRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/HistoryRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/CategoryRepository.kt`
- `core/common/src/main/java/app/confused/anikuta/core/common/repository/TrackRepository.kt`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/{animes,episodes,animehistory,animetrack,anime_category,categories}.sq`
- `core/database/src/main/sqldelight/app/confused/anikuta/core/database/1.sqm`
- `core/database/src/main/java/app/confused/anikuta/core/database/DatabaseDriverFactory.kt`
- `core/anilist/src/main/java/app/confused/anikuta/core/anilist/model/AniListAnime.kt`
- `core/anilist/src/main/java/app/confused/anikuta/core/anilist/details/AniListAnimeMapper.kt`
- `core/player/src/main/java/app/confused/anikuta/core/player/WatchProgressStore.kt`
- `core/player/src/main/java/app/confused/anikuta/core/player/PlaybackStateStore.kt`
- `core/tracker/src/main/java/app/confused/anikuta/core/tracker/{AnimeTrack,TrackRepository,TrackSyncManager,Tracker,TrackerManager}.kt`
- `core/tracker/src/main/java/app/confused/anikuta/core/tracker/anilist/AniListTracker.kt`
- `core/tracker/src/main/java/app/confused/anikuta/core/tracker/mal/MalTracker.kt`
- `core/tracker/src/main/java/app/confused/anikuta/core/tracker/{TrackerBackupProvider,TrackerBackupProviderImpl}.kt`
- `core/download/src/main/java/app/confused/anikuta/core/download/{DownloadManager,DownloadModels,DownloadRequest,DownloadTask,DownloadQueue,DefaultDownloadManager,DownloadStorageProvider}.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/model/EpisodeMetadata.kt`
- `core/episode-metadata/src/main/java/app/confused/anikuta/core/episodemetadata/repository/{EpisodeMetadataCache,EpisodeMetadataRepository}.kt`
- `core/update-checker/src/main/java/app/confused/anikuta/core/updatechecker/UpdateCheckerPreferences.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/{BackupEntry,BackupFormatType}.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/format/AnikutaBackupFormat.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/model/{AnimeBackup,WatchProgressBackup,SourceLinkBackup,BackupContainer,TrackerBackupModel}.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/provider/{AnimeBackupProviders,WatchProgressBackupProvider,EpisodeBackupProvider,SourceLinkBackupProvider,CategoryBackupProvider,CoverImageProvider,TrackerBackupProviderAdapter,EpisodeMetadataBackupProvider,PreferencesBackupProvider,BackupMappers}.kt`
- `core/backup/src/main/java/app/confused/anikuta/core/backup/translation/AniyomiBackupTranslator.kt`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/{AnimeMapper,AnimeRepositoryImpl,CategoryRepositoryImpl}.kt`
- `data/anime/src/main/java/app/confused/anikuta/data/anime/details/AniListDetailsProvider.kt`
- `data/anime/src/test/java/app/confused/anikuta/data/anime/AnimeMapperTest.kt`
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/{ExtensionLinkStore,SourceLinkStore,DetailsViewPreferenceStore}.kt`
- `data/extension/src/main/java/app/confused/anikuta/data/extension/details/{ExtensionDetailsProvider,SAnimeMapper}.kt`
- `data/extension/src/main/java/app/confused/anikuta/data/extension/matcher/SourceMatcher.kt`
- `data/history/src/main/java/app/confused/anikuta/data/history/HistoryRepositoryImpl.kt`
- `feature/browse/src/main/java/app/confused/anikuta/feature/browse/BrowseScreen.kt`
- `feature/search/src/main/java/app/confused/anikuta/feature/search/viewmodel/{SearchViewModel,ExtensionLinkingViewModel}.kt`
- `feature/search/src/main/java/app/confused/anikuta/feature/search/ui/{ExtensionLinkingSheet,SearchScreen}.kt`
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/{AnimeDetailScreen,AnimeDetailViewModel,SourceSwitcherMenu,WatchEpisodeContext,EpisodeDownloadControl}.kt`
- `feature/library/src/main/java/app/confused/anikuta/feature/library/{LibraryScreen,LibraryViewModel,LibraryState}.kt`
- `feature/history/src/main/java/app/confused/anikuta/feature/history/{HistoryViewModel,HistoryState}.kt`
- `feature/updates/src/main/java/app/confused/anikuta/feature/updates/{UpdatesViewModel,UpdatesState}.kt`
- `feature/download/src/main/java/app/confused/anikuta/feature/download/{DownloadViewModel,DownloadUiState,DownloadedFilesScreen}.kt`
- `feature/watch/src/main/java/app/confused/anikuta/feature/watch/{WatchRequest,WatchScreen}.kt` (partial — relevant sections)
- `app/src/main/java/app/confused/anikuta/navigation/{AppController,Destinations}.kt`

**Greps performed:**
- `anilistId` (471 matches across ~70 files — every match inventoried above)
- `anilistId == 0|anilistId == null|anilistId != 0|anilistId != null` (every match inventoried in §14.3)
- `"\${anilistId}:\${episodeUrl}"` composite-key pattern (every match in §7.4)
- `source_pref_$anilistId|pref_source_links|pref_extension_anilist_links|pref_details_view_preference|pref_watch_progress_map|pref_playback_state_map` (every SharedPreferences key found)

---

## 18. Conclusion — the architectural crack

The codebase has **two parallel identity systems that don't fully interoperate**:

1. **The SQLDelight layer** (animes, episodes, animehistory, animetrack, anime_category) is **local-PK-based** (`animes._id`). AniList ID is just a nullable secondary column with a partial unique index. **This layer fully supports unlinked anime.**

2. **The cross-cutting stores** (WatchProgressStore, PlaybackStateStore, DownloadManager, EpisodeMetadataCache, SourceLinkStore, the legacy `source_pref_<anilistId>` prefs) are **anilistId-keyed**. **This layer excludes unlinked anime by construction.**

The bridge between the two layers is **one-directional and lossy**:
- `AnimeRepository.getByAnilistId(anilistId)` → `Anime` (works for linked anime only).
- `AnimeRepository.getBySourceAndUrl(sourceId, url)` → `Anime` (works for unlinked, but is only called from `ExtensionDetailsProvider`, `findLibraryAnime`, and the backup providers — NOT from `WatchProgressStore`, `DownloadManager`, `TrackSyncManager`, etc.).

The result: **an unlinked extension anime can be discovered, viewed, saved to the library, and assigned categories — but it cannot be downloaded, its progress pollutes the watch-progress map under key `"0:..."`, its history rows are unopenable, its tracker sync is skipped, its episode metadata is skipped, its library "last watched" timestamp never updates, and its schedule entries are silently dropped.**

The architectural question for the proposal: **should ANIKUTA commit fully to anilistId as the universal primary key (requiring every anime to be linked, eliminating the unlinked code path), OR should it refactor the cross-cutting stores to use the local `_id` (or a stable composite key like `sourceId:url`) as the primary key, with anilistId as a secondary lookup?** Either path has significant migration implications. The evidence above is the foundation for that decision.

— End of EVID-01 —
