# 05-key-flows / Add a manga/anime to the library

> Trace the "tap the favorite (heart) button on a detail screen" gesture
> through `MangaScreenModel.toggleFavorite` / `AnimeScreenModel.toggleFavorite`
> → `UpdateManga.awaitUpdateFavorite` / `UpdateAnime.awaitUpdateFavorite`
> → SQLDelight `UPDATE mangas SET favorite=1, dateAdded=…` → library feed
> re-emits → category-assignment prompt → enhanced-tracker auto-bind.

The `favorite` column is the **only** thing that puts an entry in the
library. It does not affect read/seen status, downloads, or tracking.
This flow covers both the manga and anime sides (structurally identical,
diverging only in class names and DB tables).

## Overview

```
USER: on manga/anime detail screen, tap heart icon
   │
   ▼
MangaScreenModel.toggleFavorite()    /    AnimeScreenModel.toggleFavorite()
   │
   ▼
toggleFavorite(onRemoved = {…}, checkDuplicate = true)
   │
   ├─ if isFavorited:  REMOVE FROM LIBRARY
   │     ├─ updateManga.awaitUpdateFavorite(manga.id, false)
   │     │     └─ MangaRepository.updateManga(MangaUpdate(id, favorite=false, dateAdded=0))
   │     │           └─ SQLDelight: UPDATE mangas SET favorite=0, date_added=0 WHERE _id=?
   │     ├─ manga.removeCovers() → if changed, updateManga.awaitUpdateCoverLastModified(id)
   │     └─ onRemoved()  → snackbar "Delete downloads for this manga?"
   │
   └─ else:  ADD TO LIBRARY
         ├─ (optional) getDuplicateLibraryManga.await(manga)  → if hit, show DuplicateManga dialog and abort
         ├─ categories = getCategories()   (excludes system "Default" category)
         ├─ defaultCategoryId = libraryPreferences.defaultMangaCategory().get()
         │
         ├─ Branch 1: defaultCategoryId matches an existing category
         │     ├─ updateManga.awaitUpdateFavorite(manga.id, true)
         │     │     └─ UPDATE mangas SET favorite=1, date_added=now() WHERE _id=?
         │     └─ moveMangaToCategory(defaultCategory)
         │           └─ setMangaCategories.await(mangaId, [defaultCategoryId])
         │                 └─ DELETE FROM mangas_categories WHERE manga_id=?
         │                 └─ INSERT INTO mangas_categories (manga_id, category_id) VALUES (?, ?)
         │
         ├─ Branch 2: defaultCategoryId == 0 (use "Default"/"Uncategorized") OR no categories exist
         │     ├─ updateManga.awaitUpdateFavorite(manga.id, true)
         │     └─ moveMangaToCategory(null)   ← setMangaCategories.await(mangaId, [])  (uncategorized)
         │
         └─ Branch 3: user must choose a category
               └─ showChangeCategoryDialog()
                     └─ UI: ChangeCategoryDialog (multi-select)
                         onConfirm = { include, exclude →
                             screenModel.moveMangaToCategoriesAndAddToLibrary(manga, include)
                                  ├─ setMangaCategories.await(mangaId, include)
                                  └─ if (!manga.favorite) updateManga.awaitUpdateFavorite(manga.id, true)
                         }
   │
   ▼
SQLDelight emits on mangas table change
   │
   ▼
MangaLibraryScreenModel.getLibraryFlow re-emits
   (the 5-way combine picks up the new favorite=1 row)
   │
   ▼
Library Compose recomposes with the new item
   │
   ▼
addTracks.bindEnhancedTrackers(manga, source)   ← if a Komga/Kavita/Suwayomi source, auto-bind tracker
   │
   ▼
(optional) showTrackDialog()  ← if autoOpenTrack pref is on
```

## Step-by-step

### 1. The heart icon tap → `toggleFavorite`

Both `MangaScreen` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreen.kt`)
and `AnimeScreen` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreen.kt`)
render a heart icon in the top app bar. Tapping it calls
`screenModel.toggleFavorite()`.

`MangaScreenModel.toggleFavorite` /
`AnimeScreenModel.toggleFavorite` are thin wrappers around the
parameterised `toggleFavorite(onRemoved, checkDuplicate = true)`:

```kotlin
fun toggleFavorite() {
    toggleFavorite(onRemoved = {
        screenModelScope.launch {
            if (!hasDownloads()) return@launch
            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.delete_downloads_for_manga),
                actionLabel = context.stringResource(MR.strings.action_delete),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                deleteDownloads()
            }
        }
    })
}
```

The `onRemoved` callback only fires on remove (not add); it offers to
delete the now-orphaned downloads. `checkDuplicate = true` means the add
path will check for an existing library entry with the same title/author
(and offer to migrate instead of adding).

### 2. The remove path

```kotlin
if (isFavorited) {
    if (updateManga.awaitUpdateFavorite(manga.id, false)) {
        if (manga.removeCovers() != manga) {
            updateManga.awaitUpdateCoverLastModified(manga.id)
        }
        withUIContext { onRemoved() }
    }
}
```

`UpdateManga.awaitUpdateFavorite(mangaId, favorite)`
(`../ANIYOMI/app/src/main/java/eu/kanade/domain/entries/manga/interactor/UpdateManga.kt`)
constructs a `MangaUpdate` and calls `MangaRepository.updateManga(...)`:

```kotlin
suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
    val dateAdded = when (favorite) {
        true -> Instant.now().toEpochMilli()
        false -> 0
    }
    return mangaRepository.updateManga(
        MangaUpdate(id = mangaId, favorite = favorite, dateAdded = dateAdded),
    )
}
```

`MangaRepositoryImpl.updateManga`
(`../ANIYOMI/data/src/main/java/tachiyomi/data/entries/manga/MangaRepositoryImpl.kt`)
applies the partial update via the SQLDelight `mangasQueries.update(...)`,
which produces a parameterised `UPDATE mangas SET … WHERE _id = :mangaId
AND version = :version` (the `version` optimistic-concurrency check is
what lets the optional library-sync feature merge without clobbering).

After the update, `manga.removeCovers()` deletes the saved cover files
from `MangaCoverCache` (if the manga had been favorited long enough for
them to be cached). If anything was actually removed, the
`coverLastModified` timestamp is bumped so Coil refetches the cover on
next render.

The anime side is identical, substituting `updateAnime.awaitUpdateFavorite`,
`AnimeRepository.updateManga`, `animesQueries.update`, and
`AnimeCoverCache`.

### 3. The add path — duplicate check

```kotlin
if (checkDuplicate) {
    val duplicate = getDuplicateLibraryManga.await(manga).getOrNull(0)
    if (duplicate != null) {
        updateSuccessState {
            it.copy(dialog = Dialog.DuplicateManga(manga, duplicate))
        }
        return@launchIO
    }
}
```

`GetDuplicateLibraryManga` queries the local DB for any favorited manga
whose `title` (lower-cased) and `author` match the candidate. If a
duplicate exists, the UI shows a `DuplicateManga` dialog offering to
either add anyway, migrate the duplicate (move chapters/read state to
the new one), or cancel. The duplicate check is suppressed when the
toggle is invoked from that dialog's confirm button
(`checkDuplicate = false`).

### 4. The add path — category resolution

The favorite flag is written via
`updateManga.awaitUpdateFavorite(manga.id, true)`. **But** before that,
the ScreenModel decides which category the new library entry should be
filed under. The decision tree:

```kotlin
val categories = getCategories()                              // excludes system "Default"
val defaultCategoryId = libraryPreferences.defaultMangaCategory().get().toLong()
val defaultCategory = categories.find { it.id == defaultCategoryId }

when {
    // Branch 1: a real default category is set
    defaultCategory != null -> {
        updateManga.awaitUpdateFavorite(manga.id, true)
        moveMangaToCategory(defaultCategory)
    }
    // Branch 2: "Default"/"Uncategorized" (id 0) OR no categories exist
    defaultCategoryId == 0L || categories.isEmpty() -> {
        updateManga.awaitUpdateFavorite(manga.id, true)
        moveMangaToCategory(null)              // uncategorized
    }
    // Branch 3: prompt the user
    else -> {
        isFromChangeCategory = true
        showChangeCategoryDialog()
    }
}
```

`libraryPreferences.defaultMangaCategory()` is a preference set from
Settings → Library → Default category. It returns:

- `0` (the `UNCATEGORIZED_ID` sentinel) for "Always ask" — wait, no:
  `0` is "Default" (uncategorized); a separate "always ask" value (-1 or
  similar) routes to branch 3.
- A category id for "always use this category" (branch 1).
- The "always ask" value routes to branch 3 even when categories exist.

`getCategories()` returns the user's categories **excluding** the system
`UNCATEGORIZED_ID = 0` row (which always exists, protected by a trigger
against deletion). This is why `defaultCategoryId == 0L` falls into
branch 2 — there's no real category with id 0 to find in branch 1.

### 5. `moveMangaToCategory` → `SetMangaCategories` → SQLDelight

```kotlin
private fun moveMangaToCategory(categoryIds: List<Long>) {
    screenModelScope.launchIO {
        setMangaCategories.await(mangaId = manga.id, categoryIds = categoryIds.toList())
    }
}
```

`SetMangaCategories`
(`../ANIYOMI/domain/src/main/java/tachiyomi/domain/category/manga/interactor/SetMangaCategories.kt`)
delegates to `MangaCategoryRepository.setMangaCategories(mangaId,
categoryIds)`, which in `:data` runs a small transaction:

```sql
DELETE FROM mangas_categories WHERE manga_id = :mangaId;
INSERT INTO mangas_categories (manga_id, category_id) VALUES (:mangaId, :categoryId1), …;
```

> An entry can be in **multiple** categories simultaneously — the
> `mangas_categories` table is a many-to-many join. The
> `ChangeCategoryDialog` (step 6) lets the user pick a set, not a single
> value.

### 6. The "choose a category" dialog (branch 3)

When the user has categories but no default is set, the ScreenModel shows
`Dialog.ChangeCategory(manga, initialSelection)`. The Compose UI renders
this as `ChangeCategoryDialog`
(`../ANIYOMI/presentation/.../category/components/ChangeCategoryDialog.kt`),
a multi-select checklist with the user's categories.

On confirm, the ScreenModel calls
`moveMangaToCategoriesAndAddToLibrary(manga, include)`:

```kotlin
fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
    moveMangaToCategory(categories)
    if (manga.favorite) return
    screenModelScope.launchIO {
        updateManga.awaitUpdateFavorite(manga.id, true)
    }
}
```

Two subtleties:

- The categories are set **first**, then the favorite flag is flipped.
  This way the library feed (which reacts to `favorite = 1`) sees the
  new entry already correctly categorised.
- `if (manga.favorite) return` — if the manga was already favorited
  (e.g. the user opened the dialog from a re-categorisation action on
  an existing library entry), the favorite write is skipped.

### 7. SQLDelight emits → library feed re-emits

SQLDelight's `Flow` queries emit on any change to the underlying table.
The library feed is built on `getLibraryAnime.subscribe()` /
`getLibraryManga.subscribe()` (a `SELECT … FROM animes WHERE favorite = 1
JOIN animes_categories …` flow), so the moment the
`UPDATE animes SET favorite = 1` commits:

1. SQLDelight's `Query` listener fires.
2. The `Flow<List<LibraryManga>>` re-emits with the new row.
3. The 5-way `combine` inside `*LibraryScreenModel` (documented in
   [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md))
   re-runs `applyFilters` → `applySort` → `groupBy(category)` →
   `filter(searchQuery)` → new `State`.
4. `state` is `collectAsState()`-ed by the Compose `LibraryContent`,
   which recomposes with the new item.

**No explicit "refresh library" call is made** — the reactivity is
end-to-end. This is the same mechanism the Updates feed and the History
feed use; see [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md)
and [`../03-subsystems/updates.md`](../03-subsystems/updates.md) for the
`updatesView` SQL view that powers the Updates tab.

### 8. Enhanced-tracker auto-bind

After the favorite flag is set, `toggleFavorite` does one more thing on
the add path:

```kotlin
addTracks.bindEnhancedTrackers(manga, state.source)
if (autoOpenTrack) {
    showTrackDialog()
}
```

`AddMangaTracks.bindEnhancedTrackers(manga, source)` walks the
`TrackerManager`'s `EnhancedMangaTracker`s (Komga, Kavita, Suwayomi) and
calls `tracker.accept(source)` on each. If the source's class matches
the tracker's `getAcceptedSources()`, the tracker auto-binds to the
manga without prompting the user — see
[`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) for the
enhanced-tracker contract. This is why adding a Komga-sourced manga to
the library instantly tracks it on the Komga server.

If the user has `autoOpenTrack` enabled, the track dialog also opens so
they can manually bind to MAL/AniList/etc.

## The dual manga/anime pattern

| Concern | Manga | Anime |
|---|---|---|
| ScreenModel | `MangaScreenModel` | `AnimeScreenModel` |
| Interactor | `UpdateManga.awaitUpdateFavorite` | `UpdateAnime.awaitUpdateFavorite` |
| Repository | `MangaRepository.updateManga` | `AnimeRepository.updateAnime` |
| SQLDelight query | `mangasQueries.update` | `animesQueries.update` |
| Categories table | `mangas_categories` | `animes_categories` |
| Categories interactor | `SetMangaCategories` | `SetAnimeCategories` |
| Default-category pref | `libraryPreferences.defaultMangaCategory()` | `libraryPreferences.defaultAnimeCategory()` |
| Duplicate check | `GetDuplicateLibraryManga` | `GetDuplicateLibraryAnime` |
| Cover cache | `MangaCoverCache` | `AnimeCoverCache` |
| Library feed | `MangaLibraryScreenModel.getLibraryFlow` | `AnimeLibraryScreenModel.getLibraryFlow` |
| Enhanced trackers | Komga, Kavita, Suwayomi | Jellyfin |

The two paths are structurally identical; only the class names and SQL
table names differ.

## Sequence diagram (manga side; anime is identical)

```
USER: on MangaScreen, tap heart
   │
   ▼
MangaScreenModel.toggleFavorite()
   └─ toggleFavorite(onRemoved = {…}, checkDuplicate = true)
        ├─ if isFavorited:
        │     └─ updateManga.awaitUpdateFavorite(id, false)
        │           └─ MangaRepositoryImpl.updateManga(MangaUpdate(favorite=false, dateAdded=0))
        │                 └─ UPDATE mangas SET favorite=0, date_added=0 WHERE _id=? AND version=?
        │     └─ manga.removeCovers() → if changed: updateManga.awaitUpdateCoverLastModified(id)
        │     └─ onRemoved() → snackbar "Delete downloads?"
        │
        └─ else:
              ├─ getDuplicateLibraryManga.await(manga)  → if hit: show DuplicateManga dialog, abort
              ├─ categories = getCategories()
              ├─ defaultCategoryId = libraryPreferences.defaultMangaCategory().get()
              │
              ├─ Branch 1/2 (default or uncategorized):
              │     ├─ updateManga.awaitUpdateFavorite(id, true)
              │     │     └─ UPDATE mangas SET favorite=1, date_added=now() WHERE _id=? AND version=?
              │     └─ setMangaCategories.await(id, [defaultCategoryId] or [])
              │           └─ DELETE + INSERT INTO mangas_categories
              │
              └─ Branch 3 (prompt):
                    └─ showChangeCategoryDialog() → ChangeCategoryDialog (Compose)
                        onConfirm = { include →
                            moveMangaToCategoriesAndAddToLibrary(manga, include)
                              ├─ setMangaCategories.await(id, include)
                              └─ updateManga.awaitUpdateFavorite(id, true)
                        }
   │
   ▼
SQLDelight Query listener fires on mangas / animes table change
   │
   ▼
MangaLibraryScreenModel.getLibraryFlow re-emits
   (5-way combine: getLibraryManga.subscribe + library prefs + tracks + downloadCache.changes)
   → applyFilters → applySort → groupBy(category) → new State
   │
   ▼
LibraryContent Compose recomposes — new card appears in the right category
   │
   ▼
addTracks.bindEnhancedTrackers(manga, source)
   └─ for each EnhancedMangaTracker (Komga/Kavita/Suwayomi):
        if tracker.accept(source):  auto-bind tracker to manga
```

## See also

- [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md) — the Library ScreenModel pipeline that this flow feeds into.
- [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) — the enhanced-tracker auto-bind and the manual track dialog.
- [`../02-modules/data.md`](../02-modules/data.md) — the `mangas` / `animes` / `mangas_categories` / `animes_categories` schemas.
- [`../02-modules/domain.md`](../02-modules/domain.md) — the `UpdateManga` / `UpdateAnime` / `SetMangaCategories` / `SetAnimeCategories` interactors.
- [`read-manga.md`](read-manga.md) and [`watch-anime.md`](watch-anime.md) — what the user does next, once the entry is in the library.
