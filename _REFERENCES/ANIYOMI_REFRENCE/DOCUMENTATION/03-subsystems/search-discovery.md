# 03-subsystems / Browse & Search (Discovery)

> How a user finds new manga or anime to read/watch: browsing a single
> source's catalog (popular / latest / filtered), searching within a source,
> and **global search** — running a query against every enabled source in
> parallel.

This subsystem is the **dual of itself**: every screen, screen-model, and
paging source has a manga twin and an anime twin. The two hierarchies share
no base class but are structurally identical. We use the manga side as the
running example and call out where the anime side diverges.

## Where it lives

```
ui/browse/
├── BrowseTab.kt                              ← the 6-tab Browse root
├── manga/
│   ├── source/
│   │   ├── MangaSourcesTab.kt                ← "Sources" tab entry
│   │   ├── MangaSourcesScreenModel.kt        ← lists enabled sources
│   │   ├── MangaSourcesFilterScreen.kt       ← enable/disable languages & sources
│   │   ├── globalsearch/
│   │   │   ├── GlobalMangaSearchScreen.kt
│   │   │   ├── GlobalMangaSearchScreenModel.kt
│   │   │   └── MangaSearchScreenModel.kt     ← abstract base for both global & single-source
│   │   └── browse/
│   │       ├── BrowseMangaSourceScreen.kt    ← list/grid of a source's catalog
│   │       ├── BrowseMangaSourceScreenModel.kt
│   │       └── SourceFilterMangaDialog.kt    ← the filter sheet
│   └── extension/                            ← see source-system.md / extensions-update.md
│   └── migration/                            ← see source-system.md (migrate manga between sources)
└── anime/
    ├── source/  (same structure, Anime* names)
    └── extension/
    └── migration/
```

The `BrowseTab` is a 6-tab pager: **Anime Sources · Manga Sources · Anime
Extensions · Manga Extensions · Migrate Anime Source · Migrate Manga Source**.
Extensions and migration are documented in
[`source-system.md`](source-system.md) and
[`extensions-update.md`](extensions-update.md); this doc covers the first two
tabs (sources browsing and search).

## The browse flow at a glance

```
BrowseTab (root)
    │
    ├─► MangaSourcesTab ──► MangaSourcesScreenModel.subscribe()
    │       │                  (lists enabled, non-disabled, pinned-first)
    │       │
    │       ├── [TravelExplore icon] ─► GlobalMangaSearchScreen   (global search)
    │       ├── [FilterList icon]   ─► MangaSourcesFilterScreen   (enable/disable sources)
    │       └── [tap a source]     ─► BrowseMangaSourceScreen(sourceId, listingQuery)
    │                                       │
    │                                       ├─ chip: Popular     ─► Listing.Popular
    │                                       ├─ chip: Latest      ─► Listing.Latest   (if supportsLatest)
    │                                       ├─ chip: Filter      ─► SourceFilterMangaDialog
    │                                       └─ search box        ─► Listing.Search(query, filters)
    │
    └─► AnimeSourcesTab  (same shape, Anime* names)
```

## Browsing a single source — `BrowseMangaSourceScreenModel`

The state machine for browsing one source. State is a small immutable `data
class`:

```kotlin
data class State(
    val listing: Listing,
    val filters: FilterList = FilterList(),
    val toolbarQuery: String? = null,
    val dialog: Dialog? = null,
)
```

`Listing` is a sealed class with three variants:

| Listing | Means | Triggers |
|---|---|---|
| `Popular` | The source's "popular" feed | Tapping the Popular chip; initial entry when launched with `listingQuery == GetRemoteManga.QUERY_POPULAR` |
| `Latest` | The source's "latest updates" feed | Tapping the Latest chip; only shown when `CatalogueSource.supportsLatest` is true |
| `Search(query, filters)` | A free-text search with the given filters | Typing in the search box or applying filters |

The three variants are encoded as **special sentinel query strings** by
`GetRemoteManga` (in `:domain`):

```kotlin
fun subscribe(sourceId, query, filterList): SourcePagingSourceType = when (query) {
    QUERY_POPULAR -> repository.getPopularManga(sourceId)
    QUERY_LATEST  -> repository.getLatestManga(sourceId)
    else          -> repository.searchManga(sourceId, query, filterList)
}
```

`QUERY_POPULAR` / `QUERY_LATEST` are opaque package-qualified strings
(`"eu.kanade.domain.source.manga.interactor.POPULAR"`) so they can never
collide with a real user query.

### Paging: source → UI

The paged data flows through **three** layers:

```
CatalogueSource (extension, in-memory)
    │  suspend fun getPopularManga(page): MangasPage
    │  suspend fun getLatestUpdates(page): MangasPage
    │  suspend fun getSearchManga(page, query, filters): MangasPage
    ▼
SourcePagingSource  (in :data)
    │  subclasses: SourcePopularPagingSource, SourceLatestPagingSource,
    │              SourceSearchPagingSource
    │  load(params) → requestNextPage(page) → LoadResult.Page(...)
    ▼
SourcePagingSourceType  (in :domain, abstract typealias)
    │  (returned by MangaSourceRepository / AnimeSourceRepository)
    ▼
BrowseMangaSourceScreenModel.mangaPagerFlowFlow
    │  Pager(PagingConfig(pageSize = 25)) { getRemoteManga.subscribe(...) }
    │      .flow.map { pagingData → networkToLocal + hide-in-library filter }
    │      .cachedIn(ioCoroutineScope)
    ▼
LazyPagingItems in BrowseSourceContent (Compose)
```

`MangaSourcePagingSource` (and its anime twin `AnimeSourcePagingSource`) in
`:data` is a thin `androidx.paging.PagingSource<Long, SManga>` that delegates
each page-load to the source's suspend API. It throws `NoChaptersException`
when a page comes back empty (used as a signal to display "no results"
rather than endlessly paging).

A subtle but important transform in the screen model:

```kotlin
pagingData.map {
    networkToLocalManga.await(it.toDomainManga(sourceId))   // upsert into DB
        .let { local -> getManga.subscribe(local.url, local.source) }
        .filterNotNull()
        .stateIn(ioCoroutineScope)
}
.filter { !hideInLibraryItems || !it.value.favorite }
```

Every `SManga` returned by the source is **persisted into the local DB**
before it is rendered. The Compose list then observes the local row, so
subsequent favorite/cover/state changes flow back into the UI via Flow
without the source being re-fetched.

### Display modes

`sourcePreferences.sourceDisplayMode()` toggles between compact grid and
list. Column count comes from `libraryPreferences.mangaPortraitColumns()` /
`mangaLandscapeColumns()` (the same preferences the library uses).

## Search within a source

Tapping the search box and submitting triggers:

```kotlin
fun search(query: String? = null, filters: FilterList? = null) {
    val input = state.value.listing as? Listing.Search
        ?: Listing.Search(query = null, filters = source.getFilterList())
    mutableState.update {
        it.copy(listing = input.copy(query = query ?: input.query,
                                      filters = filters ?: input.filters),
                toolbarQuery = query ?: input.query)
    }
}
```

The state change re-keys `mangaPagerFlowFlow` (which `distinctUntilChanged`s
on `listing`), tearing down the old `Pager` and building a new
`SourceSearchPagingSource(source, query, filters)`.

### `searchGenre(genreName)` — the genre shortcut

Long-pressing a genre tag on a manga details screen calls `searchGenre`,
which walks the source's default `FilterList` looking for a matching
`Filter.Group<TriState>` / `Filter.Group<CheckBox>` / `Filter.Select<String>`
and sets it directly. If no matching filter is found, it falls back to a
plain text search for the genre name. This is how Aniyomi offers
"more like this" without each source having to opt in.

## Filter UI — `SourceFilterMangaDialog`

`SourceFilterMangaDialog` (`browse/manga/source/browse/SourceFilterMangaDialog.kt`)
is a bottom sheet that renders an arbitrary `FilterList` into Compose. The
`:source-api` `Filter` sealed class hierarchy is mapped to UI:

| `Filter` subtype | Compose component |
|---|---|
| `Filter.Header` | `HeadingItem` |
| `Filter.Separator` | `HorizontalDivider` |
| `Filter.Text` | `TextItem` |
| `Filter.CheckBox` | `CheckboxItem` |
| `Filter.TriState` | `TriStateItem` |
| `Filter.Group<*>` | `CollapsibleBox` containing the children |
| `Filter.Select<*>` | `SelectItem` (dropdown) |
| `Filter.Sort` | `SortItem` (radio + order toggle) |

The sheet has **Reset** and **Filter** buttons: Reset restores
`source.getFilterList()`; Filter calls `screenModel.setFilters(filters)` then
`search()`.

The anime side is `SourceFilterAnimeDialog`.

## Global search

Global search runs a single query across **all enabled catalogue sources at
once**, surfacing results per-source in a vertically-scrollable list. Entry
point: the TravelExplore icon in the Sources tab toolbar.

### `MangaSearchScreenModel` (the shared base)

`globalsearch/MangaSearchScreenModel.kt` is an abstract `StateScreenModel`
that powers both global search (`GlobalMangaSearchScreenModel` subclass) and
single-source search (`SourceSearchScreenModel` would subclass; in practice
the manga side has `GlobalMangaSearchScreenModel` as the only concrete
subclass, and the anime side mirrors it).

State:

```kotlin
data class State(
    val fromSourceId: Long? = null,
    val searchQuery: String? = null,
    val sourceFilter: MangaSourceFilter = MangaSourceFilter.PinnedOnly, // or All
    val onlyShowHasResults: Boolean = false,                            // hide sources with no hits
    val items: PersistentMap<CatalogueSource, MangaSearchItemResult> = persistentMapOf(),
)
```

`MangaSearchItemResult` is a sealed interface with `Loading`, `Error(throwable)`,
and `Success(List<Manga>)` variants.

### How the parallel search is dispatched

```kotlin
// Executors.newFixedThreadPool(5) — caps concurrent source calls at 5
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

Notable behaviours:

- **Five-worker thread pool** — at most five sources are queried in parallel;
  the rest queue. This prevents the OkHttp connection pool from being
  saturated by 30 simultaneous source calls.
- **Page 1 only** — global search intentionally fetches only the first page
  per source. Tapping a source's card navigates to
  `BrowseMangaSourceScreen(source.id, query)` for full paging.
- **Re-uses prior results** — when the user changes only the *source filter*
  (e.g. from PinnedOnly to All) with the same query, results from the
  previous run are kept and only newly-visible sources are fetched.
- **Cancel-and-replace** — `searchJob?.cancel()` before each new search;
  stale `async`s check `isActive` before posting.
- **Sorting** — results are sorted by `isEmpty` (non-empty first), then
  pinned-first, then alphabetical by `name (lang)`.

### Source filtering

`getEnabledSources()` filters by:
1. `sourcePreferences.enabledLanguages()` — language allow-list.
2. `sourcePreferences.disabledMangaSources()` — explicit disable-list.
3. (Global search subclass only) `sourceFilter` — `PinnedOnly` (only
   sources in `pinnedMangaSources`) or `All`.

`onlyShowHasResults` is a UI toggle backed by
`sourcePreferences.globalSearchFilterState()`. When on, sources that returned
`Success` with an empty list are hidden.

### `extensionFilter` — search within one extension's sources

`GlobalMangaSearchScreen` takes an optional `extensionFilter: String?`
(pkgName). When set, the search is restricted to the sources belonging to
that one installed extension. This is used by the migration flow
(`MigrateMangaSearchScreen`) and by "search in this extension" shortcuts.

### Single-result shortcut

When the query is non-empty, an `extensionFilter` is set, and exactly one
source is enabled, `GlobalMangaSearchScreen` shows a full-screen
`LoadingScreen` and — if the search returns exactly one manga —
**auto-navigates** straight to that manga's details screen (`navigator.replace(MangaScreen(...))`).
This is the "I know what I'm looking for and which source it's in" fast path.

## Incognito mode

Incognito mode suppresses the "last used source" recording and (per-source)
history tracking. It is checked in `BrowseMangaSourceScreenModel.init`:

```kotlin
if (!getIncognitoState.await(source.id)) {
    sourcePreferences.lastUsedMangaSource().set(source.id)
}
```

`GetMangaIncognitoState` (`app/src/main/java/eu/kanade/domain/source/manga/interactor/GetMangaIncognitoState.kt`)
computes incognito as a **per-source** OR of:

1. `basePreferences.incognitoMode().get()` — the global incognito toggle.
2. `sourcePreferences.incognitoMangaExtensions().get()` — a set of extension
   package names that are individually marked incognito.

So incognito can be turned on globally (affects every source) or per
extension (the global toggle stays off but, say, a specific adult-content
extension is always incognito). The anime side has the parallel
`GetAnimeIncognitoState` / `ToggleAnimeIncognito`.

When incognito is on, the `lastUsedMangaSource` preference is **not**
written, so the "Last used" header on the Sources tab doesn't change.

## The anime side

Every file in `ui/browse/manga/...` has a twin at `ui/browse/anime/...` with
the same structure: `BrowseAnimeSourceScreenModel`, `GlobalAnimeSearchScreenModel`,
`AnimeSearchScreenModel`, `SourceFilterAnimeDialog`, `AnimeSourcesScreenModel`,
`AnimeSourcesTab`, etc. The flow is identical; the only differences are:

- The `Listing` chips route to `GetRemoteAnime.QUERY_POPULAR` /
  `QUERY_LATEST` and to `AnimeSourceRepository.{getPopularAnime,
  getLatestAnime, searchAnime}`.
- The paging source returns `SAnime` not `SManga`.
- Global search calls `source.getSearchAnime(1, query, filters)` and
  upserts via `NetworkToLocalAnime`.
- The Browse tab's `onReselect` opens `GlobalAnimeSearchScreen` (anime
  side) by default — there's a TODO in `BrowseTab` to make this
  tab-aware.
- Anime sources also have a **seasons** workflow (parent/child anime with
  `parentId`/`seasonNumber`) and a **hosters** workflow — both are
  source-API features documented in [`source-system.md`](source-system.md)
  and [`../02-modules/source-api.md`](../02-modules/source-api.md), and
  surface in the browse UI as additional fetch types, not as separate
  screens.

## Key files

| File | Role |
|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt` | 6-tab Browse root. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/MangaSourcesTab.kt` | Manga Sources tab + toolbar (global search / filter entry points). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/MangaSourcesScreenModel.kt` | Lists enabled sources, grouped by lang/pinned/last-used. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/MangaSourcesFilterScreen.kt` / `MangaSourcesFilterScreenModel.kt` | Enable/disable languages and individual sources. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/browse/BrowseMangaSourceScreen.kt` | The list/grid screen + Popular/Latest/Filter chips. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/browse/BrowseMangaSourceScreenModel.kt` | State machine: `Listing`, filters, pager flow, favorite toggling, genre shortcut. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/browse/SourceFilterMangaDialog.kt` | Filter bottom sheet rendering `FilterList` → Compose. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/globalsearch/GlobalMangaSearchScreen.kt` | Global search screen; single-result auto-navigate shortcut. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/globalsearch/GlobalMangaSearchScreenModel.kt` | Concrete subclass for global scope. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/globalsearch/MangaSearchScreenModel.kt` | Abstract base: 5-thread dispatcher, parallel-search loop, sort, source filter. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/**` | Anime twins of all of the above. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/manga/interactor/GetRemoteManga.kt` | Sentinel-query dispatch to repository. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/source/anime/interactor/GetRemoteAnime.kt` | Anime twin. |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/source/manga/MangaSourcePagingSource.kt` | `SourcePagingSource` + Popular/Latest/Search subclasses (manga). |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/source/anime/AnimeSourcePagingSource.kt` | Anime twin. |
| `../ANIYOMI/app/src/main/java/eu/kanade/domain/source/manga/interactor/GetMangaIncognitoState.kt` / `ToggleMangaIncognito.kt` | Per-source + global incognito state. |
| `../ANIYOMI/app/src/main/java/eu/kanade/domain/source/anime/interactor/GetAnimeIncognitoState.kt` / `ToggleAnimeIncognito.kt` | Anime twins. |
| `../ANIYOMI/app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` | `enabledLanguages`, `disabled{Manga,Anime}Sources`, `pinned{Manga,Anime}Sources`, `incognito{Manga,Anime}Extensions`, `sourceDisplayMode`, `globalSearchFilterState`, `dataSaver*`, etc. |

## See also

- [`../05-key-flows/browse-catalog.md`](../05-key-flows/browse-catalog.md) — end-to-end browse journey.
- [`source-system.md`](source-system.md) — how sources/extensions are loaded; the `CatalogueSource` contract; migration.
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the `Filter`/`FilterList` schema, `SManga`/`SAnime` models.
- [`storage-and-cache.md`](storage-and-cache.md) — the **data saver** (image-compression proxy) that hooks into browse.
- [`../01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md) — the `StateScreenModel` + Flow + paging patterns used throughout.
