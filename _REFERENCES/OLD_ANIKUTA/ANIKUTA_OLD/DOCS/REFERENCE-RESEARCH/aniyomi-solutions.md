# How aniyomi solves each problem

> Reference notes for the Library / History / Search revamp.
> Source: pristine aniyomi copy at `REFERENCE/` (commit `2f5cf77`).
> Read the actual code for full context — paths are relative to `REFERENCE/`.

---

## 1. Watched / Unwatched tracking

**Two SQLDelight tables work together:**

| Table | File | Key columns |
|-------|------|-------------|
| `episodes` | `data/src/main/sqldelight/dataanime/episodes.sq` | `seen` (Boolean), `last_second_seen` (Real), `total_seconds` (Real) |
| `animehistory` | `data/src/main/sqldelight/dataanime/animehistory.sq` | `episode_id`, `last_seen` (epoch ms) |

**When is an episode marked "watched"?**
- In `PlayerViewModel.onSecondReached` (~L1557):
  `if (seconds >= totalSeconds * progress)` → mark `seen = true`
- `progress` comes from `PlayerPreferences.progressPreference()` =
  `getFloat("pref_progress_preference", 0.85F)` — **default 85%, configurable.**

**How the unwatched count reaches the library card:**
- SQL **view** `animelibView` precomputes `seenCount` per anime
  (`data/src/main/sqldelight/dataanime/animelibView.sq`).
- Library reads `animelibView` (one row per favorited anime, all stats joined
  in) — no per-render computation.
- Card shows `UnviewedBadge(count)` in the top-start corner when
  `unreadBadge` pref is true (default TRUE).

**Key files:**
- `domain/src/main/java/tachiyomi/domain/items/episode/interactor/SetSeenStatus.kt`
- `domain/src/main/java/tachiyomi/domain/history/anime/interactor/GetAnimeHistory.kt`
- `data/src/main/java/tachiyomi/data/items/episode/EpisodeRepositoryImpl.kt`

**ANI-KUTA gap:** Has the `episodes` + `animehistory` tables + repositories +
interactors but they're **dead code** (not in DI). Wiring them up = the fix.
Currently uses `WatchProgressStore` (SharedPreferences JSON map) which only
stores position, not seen-status.

---

## 2. Library categories

**Model:** `Category` (`domain/.../category/model/Category.kt`) —
`id`, `name`, `order`, `flags` (display + sort per category).

**NO preset categories.** Only a "Default" category (id=0, undeletable).
Everything else is user-created.

**Many-to-many** via `animes_categories` join table
(`data/.../sqldelight/.../animes_categories.sq`).
`SetAnimeCategories` = delete-all-then-insert (transactional).

**Tab UI:**
- M3 `PrimaryScrollableTabRow` (horizontally scrollable, NOT pills)
- `HorizontalPager` for swipeable category pages
- Per-category sort + display flags
- Pill-shaped count badge (`Pill`, `shapes.extraLarge`, `surfaceContainerHigh`)
- File: `app/.../ui/library/AnimeLibraryTab.kt`

**Management UI:**
- `AnimeCategoryScreen` — `LazyColumn` with drag-to-reorder + FAB
- Create / rename / hide / delete dialogs
- File: `app/.../ui/category/AnimeCategoryScreen.kt`

**Key interactors:**
- `CreateCategory`, `RenameCategory`, `ReorderCategory`, `DeleteCategory`
- `SetAnimeCategories` (assigns anime → categories)

**ANI-KUTA gap:** Has `LibraryPreferences.categoryTabs()` etc. but **dead code**.
The `Category` model + `AnimeCategoryRepository` + interactors exist but are
not in DI. The user wants: ONE "Default" + user-created (watching, watched,
planning, etc.) — this matches aniyomi's model exactly.

---

## 3. History UI

**Layout:** SINGLE flat `FastScrollLazyColumn` with date-separator headers
(via `insertSeparators` + `relativeDateText`).

**NO "Continue Watching" carousel.** (ANI-KUTA's carousel is custom.)

**Per-entry (96.dp Row):**
```
[ ItemCover.Book (2:3 anime cover) | title + "Ep X • timestamp" | fav icon | delete icon ]
```
- NO progress bar, NO episode thumbnail
- Covers: real anime covers from `animes.thumbnail_url` via coil3

**aniyomi HAS `episodes.preview_url`** (episode thumbnails) but uses it only
on the detail screen, NOT in history.

**Reactive:** YES — Flow-based via `getHistory.subscribe(query)`.

**Key files:**
- `app/.../ui/history/HistoryScreen.kt`
- `app/.../ui/history/HistoryViewModel.kt`
- `domain/.../history/anime/AnimeHistoryRepository.kt`
- `data/.../history/anime/AnimeHistoryRepositoryImpl.kt`
- `data/.../sqldelight/.../animehistoryView.sq` (one row per most-recently-watched episode per anime)

**ANI-KUTA gap:** Uses `WatchProgressStore` (SharedPreferences), non-reactive,
placeholder covers. The user wants **episode thumbnails** (our app supports
them — beyond what aniyomi does) with anime cover as fallback.

---

## 4. Resume + server/source fallback

**On history tap:**
- Launches `PlayerActivity` directly with ONLY `animeId + episodeId` (no
  hoster info)
- Calls `getNextEpisodes.await(animeId, episodeId, onlyUnseen=false).first()`
  → opens NEXT episode if current is seen, else current

**Resume position:**
- `PlayerActivity.setVideo` reads `episode.last_second_seen` from DB
- Seeks via MPV `start` property
- If `seen && !preserveWatchingPosition` → resume at 0

**Server memory:**
- aniyomi does **NOT** remember which server/video was played.
- Always re-resolves via `EpisodeLoader.getHosters(episode, anime, source)`

**Dead URL fallback:**
- **AUTO-fallback** — `loadVideo` calls `HosterLoader.selectBestVideo` to pick
  another hoster+video
- If ALL fail → toast "No available videos" + `finish()`
- NO user prompt

**Key files:**
- `app/.../ui/player/PlayerActivity.kt`
- `app/.../player/loader/EpisodeLoader.kt`
- `app/.../player/loader/HosterLoader.kt`

**ANI-KUTA gap:** aniyomi doesn't save last server/resolution/subtitles. The
user wants this as a CUSTOM feature — save last video URL + audio track +
subtitle track + resolution per episode; try that first, fall back if dead.
This needs new storage (a `PlaybackState` store keyed by episode).

---

## 5. "Last watched" library sort

**Enum:** `AnimeLibrarySort.Type.LastSeen` (1 of 11 types).

**Caching:** YES — `libraryAnime.lastSeen` is precomputed by the
`animehistorystatsView` SQL view:
```sql
-- animehistorystatsView.sq (simplified)
SELECT anime_id, max(last_seen) AS lastSeen
FROM animehistory GROUP BY anime_id
```
This is joined into `animelibView`. Sort just compares two Longs.

**Key files:**
- `domain/.../library/model/AnimeLibrarySort.kt`
- `data/.../sqldelight/.../animehistorystatsView.sq`
- `data/.../sqldelight/.../animelibView.sq`

**ANI-KUTA gap:** Recomputes on every sort by calling
`WatchProgressStore.getAll()` and scanning all keys per anime. Fix = migrate
to the SQLDelight view (or cache a `Map<Int, Long>` in the VM).

---

## 6. Search

**AniList = TRACKER ONLY** (syncs progress). **NOT used for search.**
(ANI-KUTA's AniList search is custom.)

**Two search modes:**

| Mode | Screen | Pagination | Filters |
|------|--------|------------|---------|
| Global search | `GlobalAnimeSearchScreen` | NO (first page only) | NO |
| Per-source browse | `BrowseAnimeSourceScreen` | YES (Paging 3, pageSize=25, infinite scroll) | YES (`AnimeFilterList` — genre/year/format/status, source-defined) |

**Global search:** concurrent across ALL enabled sources (5-thread pool),
results grouped by source in `LazyColumn` of `LazyRow`s.

**Library search:** in-memory filter; "Global search" button appears when no
in-library matches.

**Key files:**
- `app/.../ui/browse/GlobalAnimeSearchScreen.kt`
- `app/.../ui/browse/source/BrowseAnimeSourceScreen.kt`
- `domain/.../source/anime/AnimeSourceManager.kt`
- `source-api/.../animesource/AnimeHttpSource.kt` (`getSearchAnime`)

**ANI-KUTA gap:** AniList-only, capped at 25, no filters, no extension search.
The user wants a **toggle** (AniList vs Sources) at the top. For source
search, use `AniyomiSourceBridge` + Paging 3 + source filters.

---

## 7. Sub / Dub episode counts

**NOT an aniyomi feature.** Confirmed:
- 0 references to sub/dub counts in app code
- `episodes` table has no `is_dub` column
- `Video.audioTracks: List<Track>` is per-Video at player time only (not persisted)

**The only audio-track data** lives in `Video.audioTracks` (a list of
`{url, lang}`), available when an episode's videos are resolved by the
extension. It's not persisted.

**To implement in ANI-KUTA (custom feature):**
- When `DetailViewModel` resolves an episode's videos, inspect each
  `Video.audioTracks` for language codes
- Cache a boolean `hasSub` / `hasDub` per episode (and/or the count)
- Aggregate per anime for the library card badge
- Library page only (not search — the user confirmed this)

**No aniyomi reference to copy.** This is net-new work.

---

## 8. Overall UI design language

**Theme:** Material 3 (`androidx.compose.material3`). 18 color schemes + Monet
(dynamic) on API 31+. AMOLED toggle. No Material 2.

**Cards:** 4 display modes (`LibraryDisplayMode`):
- `CompactGrid` — cover + title overlay at bottom with gradient
- `ComfortableGrid` — cover + title below (default for browse)
- `CoverOnlyGrid` — cover only
- `List` — row layout

All use `ItemCover.Book` = **2:3 portrait**, ~4dp rounded, flat (no elevation).

**Top-bar:** M3 small `TopAppBar` (NOT Large/Medium). `SearchToolbar` variant
embeds `BasicTextField` in the title slot.

**Bottom-nav:** M3 `NavigationBar` (80dp). 5 visible + 1 in "More".

**Tab pattern:** `PrimaryScrollableTabRow` + `HorizontalPager`.

**Animation:** `materialFadeThroughIn/Out` (200ms) for tab switching;
`animateItemFastScroll` for list items.

**Note:** ANI-KUTA uses **Material 3 Expressive** (a step beyond vanilla M3) —
see `design-language.md` for ANI-KUTA's specific design tokens and patterns.
aniyomi itself uses vanilla M3. The user wants to keep ANI-KUTA's Expressive
look, NOT downgrade to aniyomi's vanilla M3.

---

## Cross-cutting: SQLDelight is the source of truth

aniyomi uses SQLDelight with **6 materialized views** to pre-aggregate
per-anime counts/timestamps:
- `animelibView` — one row per favorited anime, all stats joined
- `animehistoryView` — one row per most-recently-watched episode per anime
- `animehistorystatsView` — `max(last_seen)` per anime (for sort)
- `episodestatsView`, `animeupdatesView`, `animeseasonstatsView`

All reactive via SQLDelight's `.asFlow().mapToOne() / .mapToList()`.

**ANI-KUTA's `data/` module has all these views defined** (10 tables + 8 views
in `data/src/main/sqldelight/dataanime/`) but they're not wired into DI.
Migrating from SharedPreferences (LibraryStore, WatchProgressStore) to
SQLDelight unlocks all the reactive flows + precomputed views for free.
