# 05-key-flows / Browsing a source's catalog

> Trace a user opening Browse, picking a source, paging through its catalog,
> applying filters, searching within a source, and finally running a global
> search. Covers both the manga and anime sides.

## Overview

```
BrowseTab (root, 6 sub-tabs)
   └─► MangaSourcesTab  ──► MangaSourcesScreenModel
                              └─ getEnabledMangaSources.subscribe()
                                     └─ SourcePreferences.enabledLanguages
                                         + disabledSources + pinnedSources
                                     └─ AndroidMangaSourceManager.getOnlineSources()
        │
        └─[tap a source]──► navigator.push(BrowseMangaSourceScreen(sourceId, query))
                                │
                                └─ BrowseMangaSourceScreenModel
                                     ├─ source = sourceManager.getOrStub(sourceId)
                                     ├─ listing: Listing  (Popular / Latest / Search)
                                     └─ mangaPagerFlowFlow:
                                          Pager(PagingConfig(25)) {
                                            getRemoteManga.subscribe(sourceId, listing.query, listing.filters)
                                          }
                                            └─ when(query):
                                                QUERY_POPULAR → repo.getPopularManga
                                                QUERY_LATEST  → repo.getLatestManga
                                                else          → repo.searchManga(query, filters)
                                                   └─ SourcePagingSource
                                                        └─ source.fetchPopularManga(page) / fetchLatestUpdates / fetchSearchManga
                                                             └─ OkHttp → Jsoup → MangasPage(mangas, hasNextPage)
        │
        └─[tap a manga card]──► navigator.push(MangaScreen(mangaId))   (see add-to-library.md / read-manga.md)
```

The anime side (`BrowseAnimeSourceScreenModel`, `AnimeSourcePagingSource`,
`fetchPopularAnime`/`fetchSearchAnime`) is structurally identical; the only
divergences are the class names and the use of `AnimeCatalogueSource` /
`SAnime` instead of `CatalogueSource` / `SManga`. See
[`../03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md)
for the contract-side details.

## Step-by-step

### 1. Browse tab → Sources picker

`BrowseTab` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt`)
is a Voyager `Tab` that hosts a 6-page horizontal pager:
**Anime Sources · Manga Sources · Anime Extensions · Manga Extensions ·
Migrate Anime Source · Migrate Manga Source**. Only the first two are
relevant here.

The "Manga Sources" page is `MangaSourcesTab`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/MangaSourcesTab.kt`).
Its `Content()` instantiates `MangaSourcesScreenModel`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/MangaSourcesScreenModel.kt`)
and renders a list of `MangaSourceUiModel.Header` + `.Item` rows.

`MangaSourcesScreenModel.init` collects `GetEnabledMangaSources.subscribe()`,
which combines:

- `SourcePreferences.enabledLanguages()` — the set of language codes the
  user has enabled (defaults to the device locale + English).
- `SourcePreferences.disabledMangaSources()` — source ids the user has
  toggled off via the filter sheet.
- `SourcePreferences.pinnedMangaSources()` — source ids the user has pinned
  to the top.
- `AndroidMangaSourceManager.getOnlineSources()` — the live in-memory map
  of source id → `CatalogueSource` (populated at startup by
  `MangaExtensionManager`, see
  [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md)).

The list is grouped into a `TreeMap` by a synthetic key — `LAST_USED_KEY`
first, then `PINNED_KEY`, then the source's `lang` code, then a trailing
empty-lang group. Each group is rendered as `Header(name) + Item(source) +
Item(source) + …`. The toolbar has two icons:

- **FilterList icon** → `MangaSourcesFilterScreen` for enabling/disabling
  languages and individual sources.
- **TravelExplore icon** → `GlobalMangaSearchScreen` (global search, see
  step 6).

### 2. Tapping a source → `BrowseMangaSourceScreen`

The list item's onClick does `navigator.push(BrowseMangaSourceScreen(sourceId, listingQuery))`,
where `listingQuery` defaults to `GetRemoteManga.QUERY_POPULAR` (the
sentinel string `"eu.kanade.domain.source.manga.interactor.POPULAR"`).

`BrowseMangaSourceScreen` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/browse/BrowseMangaSourceScreen.kt`)
constructs a `BrowseMangaSourceScreenModel(sourceId, listingQuery)` and
renders a Compose grid (`BrowseSourceContent`) bound to
`LazyPagingItems` (AndroidX Paging 3).

### 3. `BrowseMangaSourceScreenModel` — state + pager

`BrowseMangaSourceScreenModel` holds a `State` data class:

```kotlin
data class State(
    val listing: Listing,
    val filters: FilterList = FilterList(),
    val toolbarQuery: ?String = null,
    val dialog: Dialog? = null,
)
```

`Listing` is the sealed class that selects which feed to load:

| `Listing` | Encoded as | Triggers |
|---|---|---|
| `Popular` | `GetRemoteManga.QUERY_POPULAR` | Initial load; tapping the "Popular" chip. |
| `Latest` | `GetRemoteManga.QUERY_LATEST` | Tapping the "Latest" chip — only shown if `source.supportsLatest`. |
| `Search(query, filters)` | the raw query string | Typing in the search box; applying filters. |

`init {}` calls `source.getFilterList()` and caches it in `State.filters`,
and writes `sourcePreferences.lastUsedMangaSource()` so the source bubbles
to the top of the picker next time (unless incognito mode is on).

### 4. The paged flow: `mangaPagerFlowFlow`

The core is a `Flow<Flow<PagingData<Manga>>>` keyed on `State.listing`:

```kotlin
val mangaPagerFlowFlow = state.map { it.listing }
    .distinctUntilChanged()
    .map { listing ->
        Pager(PagingConfig(pageSize = 25)) {
            getRemoteManga.subscribe(sourceId, listing.query ?: "", listing.filters)
        }.flow.map { pagingData ->
            pagingData.map {
                networkToLocalManga.await(it.toDomainManga(sourceId))   // upsert
                    .let { local -> getManga.subscribe(local.url, local.source) }
                    .filterNotNull()
                    .stateIn(ioCoroutineScope)
            }
            .filter { !hideInLibraryItems || !it.value.favorite }
        }
        .cachedIn(ioCoroutineScope)
    }
    .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())
```

Two subtleties:

- **`distinctUntilChanged` on `listing`** — when the user changes the
  listing (Popular → Search, or types a new query), the entire inner `Pager`
  is torn down and rebuilt. The Paging 3 `Pager` is not designed to be
  re-keyed, so a new one is created.
- **`networkToLocalManga.await(...)`** — every `SManga` returned by the
  source is **upserted into the local `mangas` table** before being
  rendered. The UI then observes the local row (`getManga.subscribe`) so
  subsequent favorite/cover/state changes flow back into the list via Flow
  without re-fetching from the source.

### 5. `GetRemoteManga` → `SourcePagingSource`

`GetRemoteManga` (`../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/manga/interactor/GetRemoteManga.kt`)
dispatches on the sentinel query string:

```kotlin
fun subscribe(sourceId, query, filterList) = when (query) {
    QUERY_POPULAR -> repository.getPopularManga(sourceId)
    QUERY_LATEST  -> repository.getLatestManga(sourceId)
    else          -> repository.searchManga(sourceId, query, filterList)
}
```

The `MangaSourceRepository` impl in `:data` constructs a
`SourcePopularPagingSource` / `SourceLatestPagingSource` /
`SourceSearchPagingSource` (all subclasses of `MangaSourcePagingSource`,
which extends `androidx.paging.PagingSource<Long, SManga>`). Each
`load(params)` calls the corresponding suspend API on the
`CatalogueSource`:

| `Listing` | Source API | Returns |
|---|---|---|
| Popular | `source.fetchPopularManga(page)` | `MangasPage(mangas, hasNextPage)` |
| Latest | `source.fetchLatestUpdates(page)` | `MangasPage` |
| Search | `source.fetchSearchManga(page, query, filters)` | `MangasPage` |

Each `fetchXxx` runs on `Dispatchers.IO`, hits the source's base URL with
OkHttp (`NetworkHelper.client`), parses the response with Jsoup, and maps
each result to an `SManga` (title, url, thumbnail_url). An empty first
page throws `NoChaptersException`, which Paging translates into a "no
results" UI rather than endless paging.

Paging 3 then passes the `PagingData` through the
`networkToLocalManga.await` + `getManga.subscribe` transform (step 4), and
the resulting items flow into `LazyPagingItems` in Compose. Tapping a card
pushes `MangaScreen(mangaId)` (see [`read-manga.md`](read-manga.md) and
[`add-to-library.md`](add-to-library.md)).

### 6. Filters — `SourceFilterMangaDialog`

Tapping the FilterList chip (or the filter icon in the toolbar) opens
`SourceFilterMangaDialog`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/browse/SourceFilterMangaDialog.kt`),
a bottom sheet that renders the source's `FilterList` into Compose. The
`:source-api` `Filter` sealed class hierarchy maps to UI:

| `Filter` subtype | Compose component |
|---|---|
| `Header` | `HeadingItem` |
| `Separator` | `HorizontalDivider` |
| `Text` | `TextItem` |
| `CheckBox` | `CheckboxItem` |
| `TriState` | `TriStateItem` |
| `Group<*>` | `CollapsibleBox` |
| `Select<*>` | `SelectItem` (dropdown) |
| `Sort` | `SortItem` (radio + order toggle) |

The sheet has **Reset** (restores `source.getFilterList()`) and **Filter**
(calls `screenModel.setFilters(filters)` then `search(filters = filters)`).
`search()` updates `State.listing` to `Listing.Search(query, filters)`,
which re-keys `mangaPagerFlowFlow` and rebuilds the `Pager` with a
`SourceSearchPagingSource(source, query, filters)`.

### 7. In-source text search

Typing in the toolbar's search box and submitting triggers `search(query)`:

```kotlin
fun search(query: String? = null, filters: FilterList? = null) {
    val input = state.value.listing as? Listing.Search
        ?: Listing.Search(query = null, filters = source.getFilterList())
    mutableState.update {
        it.copy(
            listing = input.copy(query = query ?: input.query, filters = filters ?: input.filters),
            toolbarQuery = query ?: input.query,
        )
    }
}
```

The state change re-keys `mangaPagerFlowFlow`, which rebuilds the `Pager`
with the new query. The next page-load calls
`source.fetchSearchManga(page, query, filters)`.

A specialised variant, **`searchGenre(genreName)`**, is invoked when the
user long-presses a genre tag on a manga details screen. It walks the
source's default `FilterList` looking for a matching `Filter.Group<TriState>`
/ `Filter.Group<CheckBox>` / `Filter.Select<String>` and sets it directly;
if no matching filter is found, it falls back to a plain text search.
This is how Aniyomi offers "more like this" without each source having to
opt in.

### 8. Global search

Tapping the TravelExplore icon in the Sources tab toolbar pushes
`GlobalMangaSearchScreen`
(`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/globalsearch/GlobalMangaSearchScreen.kt`),
backed by `GlobalMangaSearchScreenModel`. It runs a single query across
**all enabled catalogue sources in parallel**, surfacing results per-source
in a vertically-scrollable list.

`MangaSearchScreenModel` (the abstract base) uses a fixed thread pool:

```kotlin
private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

searchJob = ioCoroutineScope.launch {
    sources.map { source ->
        async {
            if (state.value.items[source] !is MangaSearchItemResult.Loading) return@async
            try {
                val page = withContext(coroutineDispatcher) {
                    source.getSearchManga(1, query, source.getFilterList())   // page 1 only
                }
                val titles = page.mangas.map { networkToLocalManga.await(it.toDomainManga(source.id)) }
                if (isActive) updateItem(source, MangaSearchItemResult.Success(titles))
            } catch (e: Exception) {
                if (isActive) updateItem(source, MangaSearchItemResult.Error(e))
            }
        }
    }.awaitAll()
}
```

Key behaviours:

- **Five-worker thread pool** — at most five sources query in parallel; the
  rest queue. This prevents saturating the OkHttp connection pool.
- **Page 1 only** — global search intentionally fetches only the first
  page per source. Tapping a source's card navigates to
  `BrowseMangaSourceScreen(source.id, query)` for full paging (step 2
  onward, but with the query pre-filled).
- **Cancel-and-replace** — `searchJob?.cancel()` before each new search;
  stale `async`s check `isActive` before posting.
- **Sorting** — results sort by `isEmpty` (non-empty first), then
  pinned-first, then alphabetical by `name (lang)`.

The source filter (`MangaSourceFilter.PinnedOnly` vs `.All`) and the
"only show sources with results" toggle let the user narrow the list
without re-fetching already-loaded sources.

## Sequence diagram

```
USER: tap Browse tab → "Manga Sources"
   │
   ▼
MangaSourcesScreenModel
   └─ GetEnabledMangaSources.subscribe()
        └─ combine(SourcePreferences, AndroidMangaSourceManager.getOnlineSources())
              └─ TreeMap by lang/pinned/last-used
                   └─ UI: Header + Item rows
USER: tap a source row
   │
   ▼
navigator.push(BrowseMangaSourceScreen(sourceId, QUERY_POPULAR))
   │
   ▼
BrowseMangaSourceScreenModel.init
   ├─ source = sourceManager.getOrStub(sourceId)   ← CatalogueSource (extension)
   ├─ listing = Listing.Popular
   └─ mangaPagerFlowFlow emits a fresh Pager
         └─ getRemoteManga.subscribe(sourceId, QUERY_POPULAR, FilterList())
               └─ repository.getPopularManga(sourceId)
                    └─ SourcePopularPagingSource
                         └─ load(page=1) → source.fetchPopularManga(1)
                              └─ OkHttp.get(baseUrl + popularPath)
                                   └─ Jsoup.parse → map to SManga
                                        └─ MangasPage(mangas, hasNextPage)
         └─ PagingData.map { networkToLocalManga.await(it)  ← upsert into mangas table
                            getManga.subscribe(url, source) ← observe local row
                            .filter { !hideInLibraryItems || !favorite } }
              └─ LazyPagingItems → Compose grid renders
USER: scroll → Paging 3 calls load(page=2) → repeat
USER: type "one piece" → search("one piece") → State.listing = Listing.Search("one piece", filters)
   │   └─ distinctUntilChanged(listing) → tear down Pager, build new one
   ▼
SourceSearchPagingSource(source, "one piece", filters)
   └─ load(1) → source.fetchSearchManga(1, "one piece", filters) → MangasPage → render
```

## See also

- [`../03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) — the contract-side deep dive (the paging source hierarchy, the filter UI mapping, the global search dispatcher).
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) — how the `CatalogueSource` instance in step 1 got loaded from an extension APK.
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the source-api contract (`CatalogueSource`, `SManga`, `FilterList`).
- [`read-manga.md`](read-manga.md) — what happens when the user taps a manga card.
- [`add-to-library.md`](add-to-library.md) — the "favorite" toggle the card's long-press menu exposes.
