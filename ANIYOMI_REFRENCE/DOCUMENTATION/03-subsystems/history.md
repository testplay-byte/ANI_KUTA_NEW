# 03-subsystems / `history.md` — History & resume

> Aniyomi records every chapter page the user reads and every episode
> position the user reaches, so that:
>
> 1. The **History** tab can show "recently read / recently watched" with a
>    one-tap "resume" button.
> 2. The **Library** can show "continue reading / continue watching" buttons
>    that jump straight to the next unread chapter / unseen episode.
> 3. The **reader / player** can resume mid-chapter at the last page read
>    and mid-episode at the last second seen.
>
> Like every other Aniyomi subsystem, history is **dual**: a manga side
> keyed on chapters and an anime side keyed on episodes, each with its own
> table, model, repository, and screen.

## Purpose

The history subsystem answers three questions:

- *"What did I just read / watch?"* — the History tab.
- *"Where do I pick up from?"* — the resume point used by the library's
  "continue" buttons and by the reader/player on reopen.
- *"How long did I spend reading this chapter?"* — manga history records a
  `time_read` (session read duration) per chapter; the anime side does not
  record duration (only the last seen timestamp).

## The history tables (manga + anime, parallel)

Two near-identical tables live in the two SQLDelight databases (see
[`data.md`](../02-modules/data.md) for the full schema).

### Manga: `history` (in `data/src/main/sqldelight/data/history.sq`)

```sql
CREATE TABLE history(
    _id        INTEGER NOT NULL PRIMARY KEY,
    chapter_id INTEGER NOT NULL UNIQUE,        -- FK chapters(_id) ON DELETE CASCADE
    last_read  INTEGER AS Date,                -- when this chapter was last read
    time_read  INTEGER NOT NULL                -- cumulative read duration (ms)
);
```

One row **per chapter** (note `UNIQUE` on `chapter_id`). Re-reading a
chapter updates the existing row rather than inserting a new one. The
`upsert` query (below) updates `last_read` and **adds** the new
`time_read` to the running total.

```sql
upsert:
INSERT INTO history(chapter_id, last_read, time_read)
VALUES (:chapterId, :readAt, :time_read)
ON CONFLICT(chapter_id)
DO UPDATE SET
    last_read = :readAt,
    time_read = time_read + :time_read
WHERE chapter_id = :chapterId;
```

### Anime: `animehistory` (in `sqldelightanime/dataanime/animehistory.sq`)

```sql
CREATE TABLE animehistory(
    _id        INTEGER NOT NULL PRIMARY KEY,
    episode_id INTEGER NOT NULL UNIQUE,        -- FK episodes(_id) ON DELETE CASCADE
    last_seen  INTEGER AS Date
);
```

One row **per episode**. There is no duration field on the anime side —
mid-episode resume position lives on the `episodes` table itself as
`last_second_seen` / `total_seconds` (written by the player, not by the
history subsystem). The `upsert` here simply replaces `last_seen`:

```sql
upsert:
INSERT INTO animehistory(episode_id, last_seen)
VALUES (:episodeId, :seenAt)
ON CONFLICT(episode_id)
DO UPDATE SET last_seen = :seenAt
WHERE episode_id = :episodeId;
```

### The `*historyView` views

The History UI does not query the bare `history` / `animehistory` tables;
it queries two pre-joined views that include the parent manga/anime title,
chapter/episode number, cover data, and a `max_last_read` /
`max_last_seen` subquery to find the most-recently-read chapter per
manga.

| View (file)                                                | Joins                                                         |
|------------------------------------------------------------|---------------------------------------------------------------|
| `historyView` (`sqldelight/view/historyView.sq`)           | `mangas ⋈ chapters ⋈ history ⋈ max(last_read) per manga`     |
| `animehistoryView` (`sqldelightanime/view/animehistoryView.sq`) | `animes ⋈ episodes ⋈ animehistory ⋈ max(last_seen) per anime` |

Both views expose two named queries used by the repositories:

- `history` / `animehistory` — the list shown in the History tab, filtered
  by `readAt > 0`, filtered to the most-recent chapter per manga
  (`maxReadAtChapterId = historyView.chapterId`), filtered by title
  (`LIKE '%query%'`), ordered by `readAt DESC`.
- `getLatestHistory` / `getLatestAnimeHistory` — single most-recent row
  across the whole library (used by the resume shortcut and the
  "resume last" tab action).

There is also an `animehistorystatsView` (anime side only) used by the
stats screen.

## Domain layer (`tachiyomi.domain.history.{manga,anime}`)

### Models

| Manga                                                | Anime                                                |
|------------------------------------------------------|------------------------------------------------------|
| `MangaHistory` (`id, chapterId, readAt: Date?, readDuration: Long`) | `AnimeHistory` (`id, episodeId, seenAt: Date?`) |
| `MangaHistoryUpdate` (`chapterId, readAt, sessionReadDuration`) | `AnimeHistoryUpdate` (`episodeId, seenAt`)      |
| `MangaHistoryWithRelations` (`id, chapterId, mangaId, title, chapterNumber, readAt, readDuration, coverData: MangaCover`) | `AnimeHistoryWithRelations` (`id, episodeId, animeId, title, episodeNumber, seenAt, coverData: AnimeCover`) |

`MangaHistory.create()` / `AnimeHistory.create()` are null-object factories
(`id = -1`, `chapterId = -1`, …) used as sentinels.

`*HistoryWithRelations` is the row type the UI actually consumes — it's
what the `*historyView` view returns. It carries a `*Cover` payload so the
list can render cover thumbnails via Coil without a separate lookup (see
[`library-management.md`](library-management.md) for the cover pipeline).

### Repository interfaces

| Manga                                                | Anime                                                |
|------------------------------------------------------|------------------------------------------------------|
| `MangaHistoryRepository`                             | `AnimeHistoryRepository`                             |

Methods (per side): `getHistory`, `getLatestHistory`, `subscribe(query)` /
`subscribeAll()`, `upsert*History(update)`, `delete*History(history)`,
`deleteAll*History()`.

### Interactors

| Manga interactor             | Anime interactor             | Purpose                                                  |
|------------------------------|------------------------------|----------------------------------------------------------|
| `GetMangaHistory`            | `GetAnimeHistory`            | `subscribe(query)` → `Flow<List<…WithRelations>>`        |
| `UpsertMangaHistory`         | `UpsertAnimeHistory`         | Insert or update a row (called by reader/player)         |
| `RemoveMangaHistory`         | `RemoveAnimeHistory`         | Delete one row / all rows for a manga / all rows         |
| `GetNextChapters`            | `GetNextEpisodes`            | Resume computation (see below)                           |
| `GetTotalReadDuration`       | —                            | Manga-only: total `time_read` across all history         |

### Resume computation — `GetNextChapters` / `GetNextEpisodes`

This is the heart of "where do I pick up from?". Each side has three
overloads:

1. `await(onlyUnread: Boolean = true)` — uses the **latest** history row
   across the whole library (`historyRepository.getLastMangaHistory()` /
   `getLastAnimeHistory()`), then calls overload 3 with that manga/anime
   id and chapter/episode id. Used by the History tab's "Resume" button
   (`resumeLastChapterReadEvent` / `resumeLastEpisodeSeenEvent`) and by
   the HistoriesTab's `onReselect` (the tab title's "resume last episode"
   action).
2. `await(mangaId, onlyUnread: Boolean = true)` — fetches all
   chapters/episodes for the entry, sorted ascending per the user's
   per-entry sort (`getChapterSort(manga, sortDescending = false)` /
   `getEpisodeSort(anime, sortDescending = false)`), optionally filtered
   to unread/unseen. Used by the Library's "continue reading" button (it
   takes `.firstOrNull()` of the result).
3. `await(mangaId, fromChapterId, onlyUnread: Boolean = true)` — same as
   2 but anchored at a specific chapter/episode, returning the sublist
   from that point onward. If `onlyUnread = false` and the anchor chapter
   is not yet fully read, it includes the anchor; if it's fully read, it
   starts at the next one. Used by the History tab's per-row "resume"
   button (`getNextChapterForManga(mangaId, chapterId)` /
   `getNextEpisodeForAnime(animeId, episodeId)`).

## The History screen

`HistoriesTab` (`app/.../ui/history/HistoriesTab.kt`) is a Voyager tab
with two pages (anime first, manga second) hosted in a `TabbedScreen`:

```
HistoriesTab (Tab)
  ├── TabbedScreen(titleRes = label_recent_manga)
  │     ├── animeHistoryTab(context, fromMore)   — page 0
  │     └── mangaHistoryTab(context, fromMore)   — page 1
  │
  └── onReselect(navigator) → resumeLastEpisodeSeenEvent.send(Unit)
                              (one-tap resume of the last-watched episode
                               from anywhere in the app)
```

The tab's bottom-nav index depends on `NavStyle` (`MOVE_HISTORY_TO_MORE`
pushes it off the bottom bar into More; `MOVE_BROWSE_TO_MORE` /
`MOVE_MANGA_TO_MORE` shift it).

### `*HistoryTab.kt` (the per-page Composables)

Each `mangaHistoryTab` / `animeHistoryTab` is a `@Composable` extension on
`Screen` returning a `TabContent`. They:

1. Hoist a `*HistoryScreenModel` via `rememberScreenModel { … }`.
2. Render `*HistoryScreen` (in `:app`'s `eu.kanade.presentation.history`
   package) with `state`, `searchQuery`, an `onClickCover` (push
   `MangaScreen` / `AnimeScreen`), `onClickResume`
   (`screenModel::getNextChapterForManga` / `getNextEpisodeForAnime`), an
   `onClickFavorite` (the "add to library" affordance when looking at a
   non-favorite entry), and `onDialogChange`.
3. Handle a `Dialog` sealed interface: `Delete` (single row, with an "all
   for this manga" toggle), `DeleteAll` (clears all history), `Duplicate*
   ` (when adding to library and a duplicate exists), `ChangeCategory`,
   `Migrate`.
4. Listen on `screenModel.events` (`OpenChapter` / `OpenEpisode`,
   `InternalError`, `HistoryCleared`) and react — e.g. open the
   reader/player activity or show a snackbar.
5. Listen on `resumeLastChapterReadEvent` / `resumeLastEpisodeSeenEvent`
   (global `Channel<Unit>`) to one-tap resume the latest read/watched.

The reader is launched via `ReaderActivity.newIntent(context,
chapter.mangaId, chapter.id)`; the player via
`MainActivity.startPlayerActivity(context, episode.animeId, episode.id,
extPlayer)` (where `extPlayer` comes from
`PlayerPreferences.alwaysUseExternalPlayer()`).

### `*HistoryScreenModel.kt`

A `StateScreenModel<State>` whose `State` carries `searchQuery`, `list:
List<*HistoryUiModel>?` (nullable = "loading"), and `dialog: Dialog?`.
Its `init` block chains `_query` (a `MutableStateFlow<String?>`) into
`getHistory.subscribe(query)` → `distinctUntilChanged` → `map {
toHistoryUiModels() }` → `flowOn(Dispatchers.IO)` → state update.

`toHistoryUiModels()` calls `insertSeparators` (from `eu.kanade.core.util`)
to inject a `Header(localDate)` row between items whose `readAt` /
`seenAt` dates fall on different calendar days — so the list groups by
"Today", "Yesterday", specific dates, etc.

The screen model also doubles as a "quick add to library" surface: it
exposes `addFavorite(mangaId)` / `addFavorite(anime)` that does duplicate
detection (`GetDuplicateLibrary*`) and either shows a `Duplicate*`
dialog, a `ChangeCategory` dialog, or directly favorites + binds enhanced
trackers (mirroring the logic in `MangaScreen` / `AnimeScreen`).

## How resume works

There are two distinct resume flows:

### Resume to the *next unread chapter / unseen episode*

Used by the Library's "continue reading" / "continue watching" button on
each card (visible when `showContinueViewingButton()` pref is on).

```
Library card "Continue" button
        │
        ▼
screenModel.getNextUnreadChapter(manga)   /  getNextUnseenEpisode(anime)
        │  (in MangaLibraryScreenModel / AnimeLibraryScreenModel)
        ▼
getChaptersByMangaId.await(mangaId, applyScanlatorFilter = true)
    .getNextUnread(manga, downloadManager)        (manga)
        │
        ▼
ReaderActivity.newIntent(ctx, chapter.mangaId, chapter.id)         (manga)
MainActivity.startPlayerActivity(ctx, episode.animeId, episode.id, extPlayer)  (anime)
```

The `getNextUnread` extension (in `app/.../util/chapter/`) is a thin
wrapper over `GetNextChapters.await(mangaId, onlyUnread = true).firstOrNull()`.

### Resume to the *last-read chapter / episode*

Used by:
- The History tab's per-row "Resume" button (which knows the exact chapter
  the user was last on) → `screenModel.getNextChapterForManga(mangaId,
  chapterId)` / `getNextEpisodeForAnime(animeId, episodeId)`.
- The HistoriesTab title re-tap → `resumeLastEpisodeSeenEvent` → calls
  `screenModel.getNextEpisode()` (no-arg form, uses the latest history
  row across the whole library) → opens the player.

### Resume to the *last page / last second*

Mid-chapter and mid-episode resume positions are stored on the
`chapters.last_page_read` and `episodes.last_second_seen` columns (not
on the history table). The reader's `ReaderViewModel` reads
`chapter.last_page_read` when loading a chapter and seeks to that page;
the player's `PlayerViewModel` seeks to `episode.last_second_seen` when
the MPV view is ready. See [`manga-reader.md`](manga-reader.md) and
[`anime-player.md`](anime-player.md) for the per-page / per-second
resume logic.

## How the reader / player write history

### Reader (`ReaderViewModel.kt`)

`ReaderViewModel` injects `UpsertMangaHistory` (aliased as
`upsertHistory`). Whenever a chapter is left (user navigates away,
activity paused, or chapter finished), the `updateHistory(readerChapter)`
method runs:

```kotlin
private suspend fun updateHistory(readerChapter: ReaderChapter) {
    if (incognitoMode) return
    val chapterId = readerChapter.chapter.id!!
    val readAt = Date()
    val sessionReadDuration = chapterReadStartTime?.let { readAt.time - it } ?: 0
    upsertHistory.await(MangaHistoryUpdate(chapterId, readAt, sessionReadDuration))
    chapterReadStartTime = null
}
```

`chapterReadStartTime` is set when a chapter becomes the active chapter and
cleared after each history write, so each session's duration is added
exactly once. Skipped entirely in incognito mode.

The same `setAsRead`/`setAsUnread` flows that flip `chapter.read` also
trigger `TrackChapter.await(...)` (see [`trackers.md`](trackers.md)) —
*tracking* is separate from *history*, though both fire on the same
"chapter finished" event.

### Player (`PlayerViewModel.kt`)

`PlayerViewModel` injects `UpsertAnimeHistory` (aliased as
`upsertHistory`) and `UpdateEpisode`. Two distinct writes happen on
episode change / activity pause:

```kotlin
private fun saveWatchingProgress(episode: Episode) {
    viewModelScope.launchNonCancellable {
        saveEpisodeProgress(episode)   // updates episodes.last_second_seen etc.
        saveEpisodeHistory(episode)    // upserts animehistory row
    }
}

private suspend fun saveEpisodeProgress(episode: Episode) {
    if (!incognitoMode || hasTrackers) {           // keep progress if tracking
        updateEpisode.await(EpisodeUpdate(
            id = episode.id!!,
            seen = episode.seen,
            bookmark = episode.bookmark,
            fillermark = episode.fillermark,
            lastSecondSeen = episode.last_second_seen,
            totalSeconds = episode.total_seconds,
        ))
    }
}

private suspend fun saveEpisodeHistory(episode: Episode) {
    if (!incognitoMode) {
        val seenAt = Date()
        upsertHistory.await(AnimeHistoryUpdate(episode.id!!, seenAt))
    }
}
```

Three call sites:
- `saveCurrentEpisodeWatchingProgress()` — called from the activity's
  `onPause` and on episode change.
- `onSaveInstanceStateNonConfigurationChange()` — only writes
  `saveEpisodeProgress` (not history); used when the activity is
  recreated for a config change.
- Per-tick progress updates from the MPV observer update
  `currentEpisode.value.last_second_seen` in-memory; the actual DB write
  happens on `saveWatchingProgress`.

`hasTrackers` is `true` if the current anime has any track row. The
asymmetry (manga always saves progress when not incognito; anime also
requires `!incognitoMode || hasTrackers`) means an anime in incognito
mode with no trackers will *not* persist `last_second_seen`, so the next
open starts from 0 — intentional, to avoid leaking watch progress during
private browsing.

## The "continue reading" buttons on library cards

Each library card can optionally show a small "play" / "book" button that
jumps straight to the next unread chapter / unseen episode. Visibility is
gated by `LibraryPreferences.showContinueViewingButton()` (default false).
Wiring (in `*LibraryTab.kt`):

```kotlin
onContinueReadingClicked = { it: LibraryManga ->
    scope.launchIO {
        val chapter = screenModel.getNextUnreadChapter(it.manga)
        if (chapter != null) {
            context.startActivity(ReaderActivity.newIntent(ctx, chapter.mangaId, chapter.id))
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
    Unit
}.takeIf { state.showMangaContinueButton }
```

The anime twin uses `getNextUnseenEpisode(anime)` and
`MainActivity.startPlayerActivity(...)`. If there's no next chapter /
episode, a "no next chapter" snackbar is shown.

## Deleting history

The History screen exposes three delete affordances:

- **Per-row swipe / delete dialog** — `Dialog.Delete(history)`. The dialog
  has an "all for this entry" toggle: if on, calls
  `removeHistory.await(mangaId)` (which runs `resetHistoryByMangaId` +
  `removeResettedHistory` — first zeros `last_read`, then deletes zeroed
  rows); if off, calls `removeHistory.await(history)` (deletes one row).
- **"Clear history" action** (`Dialog.DeleteAll`) — calls
  `removeHistory.awaitAll()` which runs `DELETE FROM history` /
  `DELETE FROM animehistory`. Emits `Event.HistoryCleared` → snackbar.

Deleting history does **not** delete the underlying chapter/episode rows
or affect `last_page_read` / `last_second_seen` (those live on the
chapter/episode rows). The library's "continue" button will simply fall
back to `getNextUnreadChapter` (next unread) if there is no history row.

## Key files

### UI (`app/src/main/java/eu/kanade/tachiyomi/ui/history/`)

| File                                       | Role                                                  |
|--------------------------------------------|-------------------------------------------------------|
| `HistoriesTab.kt`                          | Voyager tab; hosts the two pages; onReselect → resume | 
| `manga/MangaHistoryTab.kt`                 | Per-page Composable; wires dialogs + events           |
| `manga/MangaHistoryScreenModel.kt`         | State, query flow, delete/add-to-library actions      |
| `anime/AnimeHistoryTab.kt`                 | Anime twin                                            |
| `anime/AnimeHistoryScreenModel.kt`         | Anime twin                                            |

### Domain (`domain/src/main/java/tachiyomi/domain/history/`)

| File                                                          | Role                              |
|---------------------------------------------------------------|-----------------------------------|
| `manga/model/MangaHistory.kt`                                 | Bare row model                    |
| `manga/model/MangaHistoryUpdate.kt`                           | Upsert payload                    |
| `manga/model/MangaHistoryWithRelations.kt`                    | Joined row (used by UI)           |
| `manga/repository/MangaHistoryRepository.kt`                  | Repo interface                    |
| `manga/interactor/GetMangaHistory.kt`                         | `subscribe(query)`                |
| `manga/interactor/UpsertMangaHistory.kt`                      | Insert / update                   |
| `manga/interactor/RemoveMangaHistory.kt`                      | Delete (one / by manga / all)     |
| `manga/interactor/GetNextChapters.kt`                         | Resume computation (3 overloads)  |
| `manga/interactor/GetTotalReadDuration.kt`                    | Manga-only total read time        |
| `anime/model/AnimeHistory.kt`                                 | Bare row model                    |
| `anime/model/AnimeHistoryUpdate.kt`                           | Upsert payload                    |
| `anime/model/AnimeHistoryWithRelations.kt`                    | Joined row                        |
| `anime/repository/AnimeHistoryRepository.kt`                  | Repo interface                    |
| `anime/interactor/GetAnimeHistory.kt`                         | `subscribe(query)`                |
| `anime/interactor/UpsertAnimeHistory.kt`                      | Insert / update                   |
| `anime/interactor/RemoveAnimeHistory.kt`                      | Delete                            |
| `anime/interactor/GetNextEpisodes.kt`                         | Resume computation (3 overloads)  |

### Data (`data/src/main/`)

| File                                                                    | Role                              |
|-------------------------------------------------------------------------|-----------------------------------|
| `sqldelight/data/history.sq`                                            | Manga `history` table + queries   |
| `sqldelight/view/historyView.sq`                                        | Manga joined view                 |
| `sqldelightanime/dataanime/animehistory.sq`                             | Anime `animehistory` table        |
| `sqldelightanime/view/animehistoryView.sq`                              | Anime joined view                 |
| `sqldelightanime/view/animehistorystatsView.sq`                         | Stats-screen view (anime only)    |
| `java/tachiyomi/data/history/manga/MangaHistoryRepositoryImpl.kt`       | Repo impl                         |
| `java/tachiyomi/data/history/manga/MangaHistoryMapper.kt`               | Row → domain model                |
| `java/tachiyomi/data/history/anime/AnimeHistoryRepositoryImpl.kt`       | Anime twin                        |
| `java/tachiyomi/data/history/anime/AnimeHistoryMapper.kt`               | Anime twin                        |

### Writers (in `app/src/main/java/eu/kanade/tachiyomi/ui/`)

| File                                  | Write site                                                    |
|---------------------------------------|---------------------------------------------------------------|
| `reader/ReaderViewModel.kt`           | `updateHistory(readerChapter)` → `upsertHistory.await(...)`   |
| `player/PlayerViewModel.kt`           | `saveEpisodeHistory(episode)` → `upsertHistory.await(...)`    |

## See also

- [`library-management.md`](library-management.md) — the "continue reading" / "continue watching" buttons live on library cards.
- [`trackers.md`](trackers.md) — `TrackChapter` / `TrackEpisode` fire on the same chapter/episode-finished events that write history.
- [`manga-reader.md`](manga-reader.md) / [`anime-player.md`](anime-player.md) — per-page and per-second resume (the `last_page_read` / `last_second_seen` columns on chapters/episodes).
- [`../02-modules/data.md`](../02-modules/data.md) — the `history` / `animehistory` tables and views in context.
- [`../05-key-flows/read-manga.md`](../05-key-flows/read-manga.md) / [`../05-key-flows/watch-anime.md`](../05-key-flows/watch-anime.md) — end-to-end flows that include the history write step.
