---
Task ID: PAGE-ANALYSIS-1
Agent: Explore
Task: Analyze current Library, History, Search pages (Screen + VM + data flow + related settings)

Work Log:
- Read /home/z/work/anikuta/worklog.md (2,388 lines, recent context = Phase 5 download QoL work)
- Read Library trio: LibraryScreen.kt (387L), LibraryViewModel.kt (147L), LibraryStore.kt (117L)
- Read History trio: HistoryScreen.kt (521L), HistoryViewModel.kt (222L), WatchProgressStore.kt (102L)
- Read Search duo: SearchScreen.kt (477L), SearchViewModel.kt (251L)
- Read data layer: AniListRepository.kt, AniyomiSourceBridge.kt
- Read domain (mostly DEAD code): LibraryPreferences.kt (381L), LibraryAnime.kt, AnimeLibrarySortMode.kt, LibraryDisplayMode.kt, AnimeHistoryRepository.kt, AnimeHistoryRepositoryImpl.kt, AnimeHistory.kt, AnimeHistoryWithRelations.kt, GetAnimeHistory.kt
- Read DI: AppModule.kt, PreferenceModule.kt — confirmed LibraryPreferences + AnimeHistoryRepository + all history interactors are NOT registered (dead code)
- Read settings: GeneralSettingsScreen, DisplaySettingsScreen, LayoutSettingsScreen, SettingsHomeScreen, MoreScreen — confirmed NO library/history/search settings screens exist
- Read AnikutaNavGraph.kt (319L) for navigation
- Verified PlayerActivity reads WatchProgressStore for resume (L661) and saves on pause (L790-806)
- Verified DetailViewModel does NOT read WatchProgressStore (resume only works once user reaches player)

Stage Summary:
- All 3 pages use a SIMPLE SharedPreferences JSON-map storage pattern (LibraryStore, WatchProgressStore) that deliberately bypasses the full aniyomi SQLDelight DB layer. The domain layer has full aniyomi-style LibraryPreferences / AnimeHistoryRepository / interactors that exist but are NOT wired into DI — dead code that could mislead an agent.
- LIBRARY (387+147+117L): 2-col grid, 3 sort modes (Title/Last watched/Unread), pull-to-refresh, reactive via LibraryStore.changes Flow. "Unread" sort is a STUB (sorts by episode count, not seen count). No categories, no filters, no display-mode toggle, no badges, no long-press menu. LibraryPreferences (381L, domain) supports all of these but is unused.
- HISTORY (521+222L): "Continue Watching" carousel + Today/Yesterday/This Week/Earlier groups, clear-all + per-entry remove. Data source = WatchProgressStore. NON-REACTIVE (loads once in init, no Flow collection). NAVIGATION BUG: HistoryScreen.onResume(anilistId, episodeUrl, title) → NavGraph DROPS episodeUrl+title, only navigates to detail/{anilistId}; user must re-find the episode. Cover images are placeholder colors (WatchProgressStore doesn't store cover URLs). No search within history.
- SEARCH (477+251L): AniList-only (Q5 decision; extension search deferred). 400ms debounce, 5min cache, recent searches (max 10, persisted). No pagination (only first 25 results), no filters (genre/year/format), no infinite scroll. onSubmit() exists but is never called from UI. AniyomiSourceBridge (which does extension search) is used by DetailScreen, NOT by Search.
- NAVIGATION: All 3 are top-level bottom-nav destinations. Only `anilistId: Int` is passed (to Detail). HistoryScreen's episodeUrl/title args are silently discarded.
- SETTINGS: No library/history/search-specific settings screens. The 8 settings categories are General/Data/Player/Details/Extensions/Downloads/Tracking/About. DisplaySettingsScreen + LayoutSettingsScreen control DETAIL-page episode row layout (PlayerPreferences), not Library.
- CROSS-CUTTING: (1) LibraryCard and SearchAnimeCard are near-duplicate composables (could share). (2) Three different top-bar patterns (Library=sort dropdown, History=overflow menu, Search=text field). (3) Domain layer is ~50% dead code (LibraryPreferences, LibraryAnime, AnimeLibrarySort, LibraryDisplayMode, AnimeHistoryRepository + Impl, 4 history interactors) — exists but not in DI. (4) Resume works in PlayerActivity but DetailScreen doesn't show resume progress on episode rows. (5) History is non-reactive; Library + Search are reactive.
- TOP IMPROVEMENTS (ranked, see full report for risk levels): (1) Wire History resume to launch player directly w/ episodeUrl [HIGH IMPACT, LOW RISK]; (2) Make History reactive via WatchProgressStore.changes Flow [MED IMPACT, LOW RISK]; (3) Add Library display-mode toggle (grid/list) + grid-size setting [MED IMPACT, LOW RISK]; (4) Add Search pagination + filters [HIGH IMPACT, MED RISK]; (5) Add extension/source search to Search via AniyomiSourceBridge [HIGH IMPACT, HIGH RISK — needs UI for source selection]; (6) Show resume progress on DetailScreen episode rows [MED IMPACT, LOW RISK]; (7) Either delete dead domain code OR wire it up [LOW IMPACT, LOW RISK to delete]; (8) Extract shared AnimeCard composable + shared top-bar pattern [LOW IMPACT, LOW RISK].

---
Task ID: REFERENCE-RESEARCH-1
Agent: Explore
Task: Research how aniyomi implements watched/unwatched tracking, library categories, history UI, resume+server-fallback, last-watched sort, search, sub/dub counts, and overall UI design language

Work Log:
- Read previous worklog entry (PAGE-ANALYSIS-1, ~40 lines) — established that ANI-KUTA has dead aniyomi-style domain code (LibraryPreferences, AnimeHistoryRepository, interactors) and a simple SharedPreferences-based LibraryStore/WatchProgressStore; current Library/History/Search trio analyzed.
- Explored REFERENCE/data/src/main/sqldelightanime/ — found animehistory.sq, episodes.sq, animes.sq, categories.sq, animes_categories.sq + 6 view files (animelibView, episodestatsView, animehistorystatsView, animehistoryView, etc.).
- Read animehistory.sq (65 lines): schema = (_id, episode_id UNIQUE FK, last_seen AS Date); queries for upsert, getHistoryByAnimeId, resetById, resetByAnimeId, removeAll, removeResetted.
- Read episodes.sq (119 lines): table has `seen INTEGER AS Boolean`, `last_second_seen INTEGER`, `total_seconds INTEGER`, `bookmark`, `fillermark`, `preview_url`, `summary`, plus a trigger that bumps version on seen/bookmark/last_second_seen changes.
- Read animehistoryView.sq — defines `animehistoryView` SQL view that JOINs animes+episodes+animehistory+max_last_seen subquery; `animehistory` query returns ONE row per anime (the most-recently-watched episode) ordered by seenAt DESC. This is what the History screen consumes.
- Read episodestatsView.sq — aggregated per-anime counts: total, seenCount, latestUpload, fetchedAt, bookmarkCount, fillermarkCount (GROUP BY anime_id).
- Read animehistorystatsView.sq — `max(animehistory.last_seen) AS lastSeen` per anime_id. This is the cached "last watched" timestamp.
- Read animelibView.sq — the Library view: JOINs animes+episodestatsView+animehistorystatsView+animes_categories; produces totalCount, seenCount, unseenCount (derived), latestUpload, episodeFetchedAt, lastSeen, bookmarkCount, fillermarkCount, category — per anime.
- Read AnimeHistoryRepositoryImpl.kt (75 lines) — wraps animehistoryViewQueries.animehistory(query) Flow, getLatestAnimeHistory, upsertAnimeHistory(seenAt=Date), resetById, resetByAnimeId, deleteAll.
- Read EpisodeRepositoryImpl.kt (167 lines) — addAllEpisodes, updateEpisode (via EpisodeUpdate), getEpisodeByAnimeId (Flow + suspend), getEpisodeByUrlAndAnimeId.
- Read SetSeenStatus.kt (81 lines, app/domain) — interactor for marking episodes seen/unseen; when marking unseen, also resets lastSecondSeen=0; when marking seen + removeAfterMarkedAsRead pref → deletes download.
- Read PlayerViewModel.kt lines 1541-1700 — `onSecondReached(position, duration)`: saves `currentEp.last_second_seen = position*1000`; if `seconds >= totalSeconds * progress` AND not incognito (or has trackers) → calls `updateEpisodeProgressOnComplete` which marks `currentEp.seen = true`. `saveWatchingProgress` = `saveEpisodeProgress + saveEpisodeHistory` — saves EpisodeUpdate(seen, lastSecondSeen, totalSeconds) via updateEpisode interactor AND upserts AnimeHistoryUpdate(episodeId, Date()) via upsertHistory interactor.
- Read PlayerPreferences.kt line 15 — `progressPreference() = getFloat("pref_progress_preference", 0.85F)`. Default watched threshold = 85%.
- Read LibraryAnime.kt (26 lines) — domain model: totalCount, seenCount, unseenCount = totalCount - seenCount, hasBookmarks, hasStarted (seenCount > 0), lastSeen, latestUpload, episodeFetchedAt, bookmarkCount, fillermarkCount.
- Read categories.sq (92 lines) — table (_id, name, sort, flags, hidden); row _id=0 = system "default" category; trigger prevents deleting _id<=0; queries getCategory, getCategories, getVisibleCategories, getCategoriesByAnimeId, getVisibleCategoriesByAnimeId, insert, delete, update, updateAllFlags.
- Read animes_categories.sq (24 lines) — many-to-many join table (_id, anime_id, category_id); trigger bumps anime version on insert; only 2 queries: insert(animeId, categoryId) and deleteAnimeCategoryByAnimeId(animeId).
- Read AnimeRepositoryImpl.kt lines 94-101 — `setAnimeCategories(animeId, categoryIds)`: in transaction DELETE all rows for animeId, then INSERT each new categoryId. (Functionally many-to-many; semantically "set/replace".)
- Read Category.kt (18 lines) — domain model: id, name, order, flags, hidden, isSystemCategory (id == 0). No "Watching/Watched/Planning" presets — entirely user-created (with implicit "Default" = id 0).
- Read SetAnimeCategories.kt (19 lines) — interactor wraps repository.setAnimeCategories.
- Read AnimeCategoryScreenModel.kt (162 lines) — CRUD for categories: create, rename, hide, delete, reorder (drag-drop); uses CreateAnimeCategoryWithName, RenameAnimeCategory, HideAnimeCategory, DeleteAnimeCategory, ReorderAnimeCategory interactors.
- Read AnimeCategoryTab.kt + AnimeCategoryScreen.kt — UI is a LazyColumn of CategoryListItem with ReorderableItem drag handles + FAB for create; dialogs for create/rename/delete. NO preset categories.
- Read AnimeLibraryTab.kt (308 lines) — entry point: Scaffold with LibraryToolbar (search+filter+overflow), AnimeLibraryContent (Pager of category pages), bottom action menu (mark seen/unseen, change category, download, delete) shown only in selection mode; opens GlobalAnimeSearchScreen when "global search" tapped from empty library.
- Read AnimeLibraryScreenModel.kt (811 lines) — combines getLibraryAnime.subscribe() (Flow) + getCategories.subscribe() + tracksFlow + downloadCache.changes → maps to AnimeLibraryMap (Map<Category, List<AnimeLibraryItem>>); applyFilters + applySort per category; sort comparator dispatches on AnimeLibrarySort.Type (LastSeen compares libraryAnime.lastSeen).
- Read AnimeLibrarySortMode.kt (143 lines) — 11 sort types: Alphabetical, LastSeen, LastUpdate, UnseenCount, TotalEpisodes, LatestEpisode, EpisodeFetchDate, DateAdded, TrackerMean, AiringTime, Random; direction Ascending/Descending; serialized as "TYPE,DIRECTION" string in pref "animelib_sorting_mode".
- Read AnimeLibraryContent.kt (119 lines) — Column with LibraryTabs (PrimaryScrollableTabRow) on top + PullRefresh + AnimeLibraryPager (HorizontalPager of category pages).
- Read LibraryTabs.kt (52 lines) — uses M3 `PrimaryScrollableTabRow` (NOT pills); each Tab shows TabText(categoryName, badgeCount=numberOfAnimeInCategory) where badgeCount comes from `getNumberOfItemsForCategory`.
- Read LibraryBadges.kt (61 lines) — DownloadsBadge (tertiary color), UnviewedBadge (secondary color, count of unseen), LanguageBadge (folder icon if local, else source lang uppercase).
- Read CommonEntryItem.kt (416 lines) — `EntryCompactGridItem` (cover+title-overlay), `EntryComfortableGridItem` (cover+title-below), `EntryListItem` (row: cover+title+badge+continueButton); ContinueViewingButton = FilledIconButton(PlayArrow) shown only if unseenCount > 0 AND showContinueViewingButton pref.
- Read AnimeLibraryComfortableGrid.kt + AnimeLibraryList.kt — wires badges into EntryComfortableGridItem/EntryListItem via coverBadgeStart = { DownloadsBadge; UnviewedBadge }, coverBadgeEnd = { LanguageBadge }.
- Read LibraryPreferences.kt (429 lines) — full preferences: displayMode, animeSortingMode, categoryTabs (default true), categoryNumberOfItems (default false), showContinueViewingButton (default false), downloadBadge (default false), unreadBadge (default TRUE), localBadge (default true), languageBadge (default false), animePortraitColumns/animeLandscapeColumns (0=auto), filterDownloadedAnime/filterUnseen/filterStartedAnime/filterBookmarkedAnime/filterCompletedAnime (TriState.DISABLED), defaultAnimeCategory (-1 = always ask), lastUsedAnimeCategory (appState, 0).
- Read AnimeLibrarySettingsDialog.kt (319 lines) — TabbedDialog with 3 tabs: Filter (TriState filters), Sort (radio list of 11 sort types + direction toggle), Display (displayMode chips, columns slider, badge toggles, category-tabs toggle).
- Read AnimeHistoryTab.kt (188 lines) — entry point: Scaffold(no topBar here; topBar comes from HistoriesTab wrapper), AnimeHistoryScreen; onClickResume = screenModel::getNextEpisodeForAnime(animeId, episodeId) → emits Event.OpenEpisode(episode) → handler calls MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer). DELETE_ALL action via overflow. Search-enabled tab.
- Read AnimeHistoryScreenModel.kt (268 lines) — init collects `_query` (search) Flow → `getHistory.subscribe(query)` Flow → maps to AnimeHistoryUiModel list with `insertSeparators` adding `Header(localDate)` between items on different days. getNextEpisodeForAnime calls `getNextEpisodes.await(animeId, episodeId, onlyUnseen=false)` and takes .first() — so tapping history opens the NEXT episode after the watched one (or same if not fully seen).
- Read AnimeHistoryScreen.kt (135 lines) — FastScrollLazyColumn of Header+Item pairs. NO "continue watching" carousel. NO progress bar. Just a flat date-grouped list.
- Read AnimeHistoryItem.kt (129 lines) — Row: ItemCover.Book (anime cover, NOT episode thumbnail) + Column(title, "Ep X • timestamp") + Favorite icon (if not in library) + Delete icon. Fixed height 96.dp. Click on row = onClickResume (resume/next episode).
- Read AnimeHistoryWithRelations.kt (14 lines) — fields: id, episodeId, animeId, title, episodeNumber, seenAt: Date?, coverData: AnimeCover. NO progress, NO duration, NO episode thumbnail URL.
- Read AnimeHistoryMapper.kt (45 lines) — maps SQL view row → AnimeHistoryWithRelations; coverData.url = animes.thumbnail_url (anime cover, not episode preview_url).
- Read GetNextEpisodes.kt (52 lines) — 3 overloads: await(onlyUnseen), await(animeId, onlyUnseen), await(animeId, fromEpisodeId, onlyUnseen). Logic for the fromEpisodeId variant: if current episode NOT seen → returns list starting at current; if seen → drops current and returns from next.
- Read PlayerActivity.kt lines 147-220, 1045-1107 — `newIntent(context, animeId, episodeId, hostList?, hostIndex?, vidIndex?)` puts extras "animeId","episodeId","hostList"(serialized),"hostIndex","vidIndex". `setVideo(video, position)` reads `episode.last_second_seen` from DB; if episode.seen && !preserveWatchingPosition → resume at 0, else resume at last_second_seen; seeks via MPV `start` property. `setInitialEpisodeError` → toast + finish().
- Read MainActivity.kt lines 591-622 — `startPlayerActivity(context, animeId, episodeId, extPlayer, video?, hosterIndex?, videoIndex?, hosterList?)`: if extPlayer → ExternalIntents; else → `PlayerActivity.newIntent(context, animeId, episodeId, hosterList, hosterIndex, videoIndex)`. History tab calls with only animeId+episodeId (no hoster info) → re-resolves.
- Read PlayerViewModel.kt lines 1175-1251 (`init`) — if `hostList` extra is blank → `EpisodeLoader.getHosters(currentEp, anime, source)` re-fetches hosters from source; else parses hostList from extra. So history tap = full re-resolve.
- Read PlayerViewModel.kt lines 1289-1440 (`loadHosters` + `loadVideo`) — iterates all hosters concurrently via `EpisodeLoader.loadHosterVideos`; picks preferred video first (Video.preferred flag), else `HosterLoader.selectBestVideo`. If `loadVideo` returns false (resolvedVideo.videoUrl empty / dead) → falls back to `HosterLoader.selectBestVideo(hosterState.value)` to pick another. This is the AUTO-FALLBACK when a saved/dead video URL fails. If no hoster works at all → throws ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos) → activity finishes with toast.
- Read EpisodeLoader.kt lines 1-100 — `getHosters(episode, anime, source)`: if downloaded → local file hoster; if HTTP source → `source.getHosterList(episode)` (newer ext) OR `source.getVideoList(episode).toHosterList()` (legacy); if LocalAnimeSource → local.
- Read HosterLoader.kt (170 lines) — `selectBestVideo`: prefers Video.preferred==true; else first video with non-empty url; returns (-1,-1) if none. `getResolvedVideo`: if source is AnimeHttpSource && !video.initialized → calls `source.resolveVideo(video)`; catches Exception → null. So resolution is per-Video and lazy.
- Read GlobalAnimeSearchScreenModel.kt + AnimeSearchScreenModel.kt (236 lines) — global search across ALL enabled catalogue sources concurrently (Executors.newFixedThreadPool(5)); per-source `source.getSearchAnime(1, query, source.getFilterList())` returns first page only (NO pagination in global search). Results grouped by source, sorted by (has-results, pinned, alpha). Source filter: All / PinnedOnly. Toggle "only show sources with results".
- Read GlobalAnimeSearchScreen.kt + GlobalAnimeSearchCardRow.kt — LazyColumn of source-grouped results; each group is a LazyRow of 96.dp-wide EntryComfortableGridItem cards with InLibraryBadge. NO AniList anywhere — aniyomi uses AniList ONLY as a tracker (data/track/anilist/), NOT for discovery/search.
- Read BrowseAnimeSourceScreenModel.kt (378 lines) — per-source browse uses Android Paging 3: `Pager(PagingConfig(pageSize=25))` with AnimeSourceSearchPagingSource → infinite scroll. Supports source.getFilterList() (AnimeFilterList — source-specific filters: genre, year, format, status, etc. defined by extension). Listings: Popular / Latest / Search(query, filters).
- Read AnimeSourcePagingSource.kt (66 lines) — PagingSource<Long, SAnime>: load() calls requestNextPage(page) → source.getSearchAnime(page, query, filters); nextKey = page+1 if hasNextPage.
- Searched for "anilist" across app/src/main/java — found 22 files, ALL under data/track/anilist/ (Anilist.kt, AnilistApi.kt, dto/*) or tracker UI. Confirmed: AniList = tracker only, NOT a search source.
- Searched for "isSub|isDub" + "(sub|dub).*(count|episode|badge)" — only baseline-prof.txt hit was `isSubpackageOf` (Kotlin reflection, unrelated). Confirmed: aniyomi does NOT track sub/dub per episode and does NOT show sub/dub counts on library cards.
- Read Video.kt (169 lines, source-api) — `Video` has `audioTracks: List<Track>` and `subtitleTracks: List<Track>` where `Track(url, lang)`. This is PER-VIDEO, exposed at player time, NOT persisted to DB per episode.
- Read TrackSelect.kt (66 lines) — player-time selection of preferred audio/subtitle language based on SubtitlePreferences.preferredSubLanguages / AudioPreferences.preferredAudioLanguages (comma-separated locale list). Selection is by matching track.name or track.language against preferred Locale; NOT a count.
- Read TachiyomiTheme.kt (124 lines) — Material 3 (`MaterialTheme` from androidx.compose.material3); 18 built-in color schemes (Tachiyomi default, Monet, Cloudflare, Cottoncandy, Doom, GreenApple, Lavender, Matrix, MidnightDusk, Mocha, Sapphire, Nord, Strawberry, Tako, TealTurqoise, TidalWave, YinYang, Yotsuba, Monochrome). AMOLED mode toggle.
- Read ItemCover.kt (55 lines) — `enum ItemCover(val ratio: Float) { Square(1/1), Book(2/3), Thumb(16/9) }`. Library + browse + history all use `ItemCover.Book` (2:3 portrait). Shape = `MaterialTheme.shapes.extraSmall` (rounded ~4dp). AsyncImage via coil3.
- Read AppBar.kt (457 lines) — uses M3 `TopAppBar` (small, NOT LargeTopAppBar/MediumTopAppBar); `TopAppBarScrollBehavior` passed in for collapse-on-scroll; SearchToolbar variant embeds a BasicTextField in the title slot.
- Read Scaffold.kt (362 lines) — custom Tachiyomi-forked M3 Scaffold with `startBar` slot (NavigationRail for tablets) + `bottomBar` + `topBar(scrollBehavior)`.
- Read NavigationBar.kt (48 lines) — M3 NavigationBar (height 80dp, no horizontal spacer).
- Read HomeScreen.kt (343 lines) — TabNavigator with 6 tabs: AnimeLibraryTab, MangaLibraryTab, UpdatesTab, HistoriesTab, BrowseTab, MoreTab; NavStyle pref moves ONE of {Manga, Updates, History, Browse} into MoreTab. Bottom nav = NavigationBar (phone) or NavigationRail (tablet). UpdatesTab + BrowseTab show badge counts.
- Read NavStyle.kt (52 lines) — 4 nav style options: MOVE_MANGA_TO_MORE, MOVE_UPDATES_TO_MORE, MOVE_HISTORY_TO_MORE, MOVE_BROWSE_TO_MORE. Default tab = uiPreferences.startScreen().get().tab.
- Read Badges.kt + Pill.kt + Tabs.kt (presentation-core) — Badge = RectangleShape text chip with secondary-color background; BadgeGroup = Row clipped to shapes.extraSmall; Pill = Surface with shapes.extraLarge (used in tab counts and toolbar counts).
- Read AnimeLibraryPager.kt (160 lines) — HorizontalPager over categories; only ±1 offscreen pages composed; per-page display mode dispatch (List/CompactGrid/CoverOnlyGrid/ComfortableGrid); empty page → GlobalSearchItem + EmptyScreen.
- Read LibraryDisplayMode.kt (43 lines) — sealed interface: CompactGrid (default), ComfortableGrid, List, CoverOnlyGrid.

Stage Summary:

### Topic 1: Watched/Unwatched tracking
- **Storage**: TWO SQLDelight tables. `episodes` (REFERENCE/data/src/main/sqldelightanime/dataanime/episodes.sq:3-25) has `seen INTEGER AS Boolean`, `last_second_seen INTEGER`, `total_seconds INTEGER` columns — this is the per-episode progress + seen flag. `animehistory` (REFERENCE/data/src/main/sqldelightanime/dataanime/animehistory.sq:3-9) has `(episode_id UNIQUE FK, last_seen AS Date)` — this is the "I watched this episode at this time" log used for the History screen and "last watched" sort.
- **Repository**: `AnimeHistoryRepository` / `AnimeHistoryRepositoryImpl` (REFERENCE/data/src/main/java/tachiyomi/data/history/anime/AnimeHistoryRepositoryImpl.kt:12-75). `EpisodeRepository` / `EpisodeRepositoryImpl` (REFERENCE/data/src/main/java/tachiyomi/data/items/episode/EpisodeRepositoryImpl.kt:11-167). Marking watched uses `SetSeenStatus` interactor (REFERENCE/app/src/main/java/eu/kanade/domain/items/episode/interactor/SetSeenStatus.kt:14-80) which writes `EpisodeUpdate(seen=..., lastSecondSeen=...)`.
- **When marked watched**: In `PlayerViewModel.onSecondReached` (REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1541-1569): `if (seconds >= totalSeconds * progress && shouldTrack) { updateEpisodeProgressOnComplete(...) }` where `currentEp.seen = true`. Threshold = `playerPreferences.progressPreference().get()` — DEFAULT 0.85F (85%) — configurable via PlayerSettingsPlayerScreen (REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt:15). `shouldTrack = !incognitoMode || hasTrackers`. So: **NOT 100%, NOT 50% — configurable, default 85%**.
- **Unwatched count display**: Cached in `animelibView` SQL view (REFERENCE/data/src/main/sqldelightanime/view/animelibView.sq:1-43) as `seenCount` (sum of `episodes.seen`) per anime; `unseenCount = totalCount - seenCount` (computed in domain model `LibraryAnime.unseenCount`, REFERENCE/domain/src/main/java/tachiyomi/domain/library/anime/LibraryAnime.kt:18-19). Displayed on library cards via `UnviewedBadge(count)` (REFERENCE/app/src/main/java/eu/kanade/presentation/library/components/LibraryBadges.kt:23-28) — a small rectangle chip in the top-start corner of the cover, only shown if `count > 0` AND `libraryPreferences.unreadBadge().get()` is true (default TRUE). Per-anime, NOT per-episode.
- **ANI-KUTA gap**: ANI-KUTA stores progress in `WatchProgressStore` (a SharedPreferences JSON map of anilistId → episode progress), bypassing SQLDelight entirely. No `episodes` table, no `seen` boolean, no `last_second_seen`, no `animehistory` table. The "Unread" sort in ANI-KUTA's LibraryStore is a STUB (sorts by episode count). To match aniyomi: wire the existing (dead) `AnimeHistoryRepository` + `EpisodeRepository` into DI, migrate WatchProgressStore data into SQLDelight, surface `unseenCount` on library cards.

### Topic 2: Library categories
- **Model**: `Category(id, name, order, flags, hidden, isSystemCategory)` (REFERENCE/domain/src/main/java/tachiyomi/domain/category/model/Category.kt:5-18). `UNCATEGORIZED_ID = 0L` is the implicit "Default" system category — NOT deletable (SQL trigger in categories.sq:12-18 prevents it).
- **Preset vs custom**: ENTIRELY user-created. There is NO "Watching / Watched / Planning" preset. The only system category is the implicit "Default" (id=0, name=""). Users create categories via the AnimeCategoryScreen (REFERENCE/app/src/main/java/eu/kanade/presentation/category/AnimeCategoryScreen.kt:28-64) with a FAB + create/rename/hide/delete/reorder dialogs.
- **Schema**: `categories` table (REFERENCE/data/src/main/sqldelightanime/dataanime/categories.sq:1-92). `animes_categories` join table (REFERENCE/data/src/main/sqldelightanime/dataanime/animes_categories.sq:1-24) — many-to-many. `setAnimeCategories(animeId, categoryIds)` (REFERENCE/data/src/main/java/tachiyomi/data/entries/anime/AnimeRepositoryImpl.kt:94-101) = DELETE all rows for animeId, then INSERT each new categoryId (transactional set/replace semantic; one anime can be in multiple categories).
- **Interactors**: GetAnimeCategories, GetVisibleAnimeCategories, SetAnimeCategories, CreateAnimeCategoryWithName, RenameAnimeCategory, DeleteAnimeCategory, ReorderAnimeCategory, HideAnimeCategory, SetSortModeForAnimeCategory, ResetAnimeCategoryFlags, SetAnimeDisplayMode — all in REFERENCE/domain/src/main/java/tachiyomi/domain/category/anime/interactor/.
- **Library tab UI**: `LibraryTabs` (REFERENCE/app/src/main/java/eu/kanade/presentation/library/components/LibraryTabs.kt:17-52) uses M3 **`PrimaryScrollableTabRow`** (NOT pills) — horizontally scrollable tabs, one per category. Each Tab shows `TabText(category.visualName, badgeCount)` where badgeCount is the number of anime in that category (only shown if `libraryPreferences.categoryNumberOfItems().get()` is true, default FALSE). Tabs hidden if only 1 category OR if `categoryTabs()` pref is false. Below tabs: `HorizontalPager` (AnimeLibraryPager) — one page per category, swipeable.
- **Category management UI**: AnimeCategoryTab → AnimeCategoryScreen (REFERENCE/app/src/main/java/eu/kanade/presentation/category/AnimeCategoryScreen.kt) — LazyColumn with drag-to-reorder (sh.calvin.reorderable), CategoryListItem with rename/hide/delete overflow, CategoryFloatingActionButton for create. Dialogs: CategoryCreateDialog, CategoryRenameDialog, CategoryDeleteDialog.
- **Per-category customization**: Each category has `flags` (Long) which encodes per-category sort mode + display mode (see `Category.sort: AnimeLibrarySort` extension, AnimeLibrarySortMode.kt:144-145). The `categorizedDisplaySettings()` pref (default false) enables per-category display modes.
- **ANI-KUTA gap**: ANI-KUTA has NO categories at all — LibraryStore is a flat list. The dead domain code (LibraryPreferences has `defaultAnimeCategory()`, `lastUsedAnimeCategory()`, etc.) exists but is unused. To match aniyomi: introduce `categories` + `animes_categories` SQLDelight tables, port the 11 category interactors, add AnimeCategoryScreen, add LibraryTabs + HorizontalPager to LibraryScreen.

### Topic 3: History UI
- **Layout**: SINGLE flat `FastScrollLazyColumn` (REFERENCE/app/src/main/java/eu/kanade/presentation/history/anime/AnimeHistoryScreen.kt:67-110) — NO "continue watching" carousel at the top, NO grouped sections by Today/Yesterday/This Week. Instead, items are date-grouped via `insertSeparators` (REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/history/anime/AnimeHistoryScreenModel.kt:93-104) which inserts `AnimeHistoryUiModel.Header(localDate)` between items on different days. Header text = `relativeDateText(date)` (e.g. "Today", "Yesterday", "Oct 15"). Items ordered by `seenAt DESC`.
- **Per-entry layout** (AnimeHistoryItem.kt:36-109): Fixed 96.dp Row: [ItemCover.Book cover | Column(title, "Ep X • seenAt timestamp") | Favorite icon (if not in library) | Delete icon]. NO progress bar, NO duration, NO episode thumbnail.
- **Cover images**: Real anime covers via `ItemCover.Book` (2:3 portrait aspect) reading `AnimeCover.url = animes.thumbnail_url` (REFERENCE/data/src/main/java/tachiyomi/data/history/anime/AnimeHistoryMapper.kt:37-43). Uses coil3 AsyncImage. NO episode thumbnails on history screen — aniyomi HAS `episodes.preview_url` in the DB (episodes.sq:21) but it's shown only on the episode list (detail screen), NOT history.
- **Reactive**: YES — `getHistory.subscribe(query)` returns a Flow that re-emits on DB changes (REFRESH after delete/reset). ANI-KUTA's history is NON-reactive (loads once).
- **Search**: History tab has search-enabled top bar (filters by anime title via the `LIKE %query%` in `animehistory` SQL query, animehistoryView.sq:43).
- **ANI-KUTA gap**: ANI-KUTA has a "Continue Watching" carousel + Today/Yesterday/This Week/Earlier groups (more elaborate than aniyomi's date-header list), but uses placeholder color covers (WatchProgressStore doesn't store cover URLs), is non-reactive, and discards episodeUrl/title in navigation. To match aniyomi: keep the grouped list (it's arguably better), but switch to date-separator headers like aniyomi, use real anime covers from AniList thumbnail URL (already available in ANI-KUTA's AniListRepository), make it reactive via WatchProgressStore.changes Flow, and pass episodeUrl through navigation.

### Topic 4: Resume behavior + server/source fallback
- **On history tap**: `onClickResume = screenModel::getNextEpisodeForAnime(animeId, episodeId)` (REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/history/anime/AnimeHistoryTab.kt:89). This calls `getNextEpisodes.await(animeId, episodeId, onlyUnseen=false).first()` (GetNextEpisodes.kt:33-51) — returns the SAME episode if not fully seen, OR the NEXT episode if the watched one is seen. Then `MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer)` (AnimeHistoryTab.kt:57-65) — launches PlayerActivity directly (NOT detail page).
- **Intent extras**: `PlayerActivity.newIntent(context, animeId, episodeId, hostList?, hostIndex?, vidIndex?)` puts "animeId", "episodeId", "hostList" (serialized Hoster list), "hostIndex", "vidIndex" (REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:147-164). History tap passes ONLY animeId+episodeId (no hoster info) → triggers full re-resolve.
- **Resume position**: `PlayerActivity.setVideo(video, position)` (PlayerActivity.kt:1051-1087) reads `episode.last_second_seen` from the DB-loaded episode; if `episode.seen && !preserveWatchingPosition` → resume at 0, else resume at `last_second_seen`. Seeks via MPV `start` property: `MPVLib.command(arrayOf("set", "start", "${resumePosition / 1000F}"))`. `preserveWatchingPosition` pref defaults to FALSE (PlayerPreferences.kt:11-14).
- **Server/source memory**: aniyomi does NOT remember which server/video was being played across launches. On each player open with only animeId+episodeId, `PlayerViewModel.init` (PlayerViewModel.kt:1175-1251) calls `EpisodeLoader.getHosters(currentEp, anime, source)` to fetch fresh hosters from the extension. The `hostList` extra is ONLY used when the player is re-launched from itself (e.g. switching episodes within the player).
- **Dead video URL fallback**: In `PlayerViewModel.loadVideo` (PlayerViewModel.kt:1377-1440): if `resolvedVideo.videoUrl.isEmpty()` (dead link) AND no current video → marks hoster state ERROR, then `HosterLoader.selectBestVideo(hosterState.value)` picks another hoster+video, recursively calls `loadVideo` on it. If ALL hosters fail → throws `ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos)` → `setInitialEpisodeError` (PlayerActivity.kt:1093-1101) shows toast + finishes activity. NO user prompt, NO retry dialog — silent auto-fallback then error toast.
- **ANI-KUTA gap**: ANI-KUTA's PlayerActivity already reads WatchProgressStore for resume (prior agent confirmed line 661) and saves on pause (lines 790-806). ANI-KUTA does NOT have multi-hoster fallback — it likely uses a single resolved video URL. The history navigation BUG (episodeUrl+title dropped) means resume from history goes to detail page, not player. To match aniyomi: (a) make history tap launch PlayerActivity directly with anilistId+episodeId (or equivalent), (b) implement hoster list + loadHosters + loadVideo-with-fallback pattern, (c) rely on DB-stored last_second_seen rather than passing position via Intent.

### Topic 5: "Last watched" library sort
- **Enum**: `AnimeLibrarySort.Type.LastSeen` (REFERENCE/domain/src/main/java/tachiyomi/domain/library/anime/model/AnimeLibrarySortMode.kt:28). One of 11 types. User-facing label = `AYMR.strings.action_sort_last_seen` ("Last seen").
- **Sort implementation**: In `AnimeLibraryScreenModel.applySort` (AnimeLibraryScreenModel.kt:277-326):
  ```kotlin
  AnimeLibrarySort.Type.LastSeen -> {
      i1.libraryAnime.lastSeen.compareTo(i2.libraryAnime.lastSeen)
  }
  ```
  Then `.let { if (category.sort.isAscending) it else it.reversed() }.thenComparator(sortAlphabetically)`.
- **Caching**: YES — `libraryAnime.lastSeen` is precomputed by the `animehistorystatsView` SQL view (REFERENCE/data/src/main/sqldelightanime/view/animehistorystatsView.sq:1-7) as `max(animehistory.last_seen) AS lastSeen` per anime, joined into `animelibView` (animelibView.sq:21-23). So the sort itself just compares two Longs — fast. The MAX is computed once per library query (via SQL view), NOT on every sort comparison. The `animehistory.last_seen` is updated by `upsertAnimeHistory(seenAt=Date())` every time the user watches (PlayerViewModel.kt:1675-1683).
- **ANI-KUTA gap**: ANI-KUTA's "Last watched" sort is a STUB — LibraryStore sorts by `episodeCount` (per prior agent analysis), NOT by actual last-watched timestamp. ANI-KUTA's WatchProgressStore has `lastWatchedAt` per anime (could be used), but the sort doesn't use it. To match aniyomi: store `lastWatchedAt` in WatchProgressStore (already does) and sort LibraryStore items by it; OR migrate to SQLDelight `animehistory` table + `animehistorystatsView`.

### Topic 6: Search (AniList vs sources, filters, pagination)
- **AniList in aniyomi**: Used ONLY as a TRACKER (REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/track/anilist/) — syncs watch progress to AniList. NOT used for search/discovery. Confirmed: 0 references to AniList in any search/browse UI file.
- **Two search modes**:
  1. **Global search** (GlobalAnimeSearchScreen, REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/globalsearch/): Searches ALL enabled catalogue sources CONCURRENTLY via `Executors.newFixedThreadPool(5)` (AnimeSearchScreenModel.kt:46). Each source: `source.getSearchAnime(1, query, source.getFilterList())` — returns FIRST PAGE ONLY (no pagination, no infinite scroll). Results grouped by source in a LazyColumn; each source row is a LazyRow of 96.dp cover cards. Source filter: All / PinnedOnly. Toggle "only show sources with results". Accessed from LibraryScreen's "Global search" button when search query is non-empty.
  2. **Per-source browse** (BrowseAnimeSourceScreen, REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/browse/): Uses Android **Paging 3** with `Pager(PagingConfig(pageSize=25))` (BrowseAnimeSourceScreenModel.kt:115) → infinite scroll. Supports `source.getFilterList()` (AnimeFilterList — source-specific filters: genre, year, format, status, etc. defined by the extension). Three listing modes: Popular / Latest / Search(query, filters).
- **Filters**: Per-source filters come from the extension's `getFilterList()` method (AnimeFilterList — REFERENCE/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/AnimeFilter.kt). Source-specific (each extension defines its own: genre checkboxes, year dropdown, format select, etc.). NO global filter UI for AniList-style filters (because aniyomi doesn't search AniList).
- **Pagination**: Per-source browse = infinite scroll via Paging 3. Global search = NO pagination (first page only).
- **Library search**: In-library search is a simple in-memory filter on library items (`it.matches(searchQuery)` in AnimeLibraryScreenModel.kt:120-124); matches title/author/artist/description/genre/source-name. When query is non-empty AND no in-library matches, a "Global search" button appears at the top of the empty library page → launches GlobalAnimeSearchScreen.
- **ANI-KUTA gap**: ANI-KUTA's SearchScreen is AniList-only (per prior agent + Q5 decision). NO source/extension search in SearchScreen (AniyomiSourceBridge is used by DetailScreen, not Search). NO pagination (only first 25 results). NO filters (no genre/year/format). To match aniyomi: (a) add a tab toggle or unified search that includes source search via AniyomiSourceBridge, (b) add Paging 3 infinite scroll, (c) surface source-defined filters. NOTE: aniyomi has NO AniList search at all — ANI-KUTA's AniList search is a CUSTOM feature not in upstream.

### Topic 7: Sub vs Dub episode counts
- **NOT an aniyomi feature.** Confirmed via:
  - Searched all of REFERENCE/ for `isSub|isDub` → only hit was `isSubpackageOf` (Kotlin reflection in baseline-prof.txt).
  - Searched for `(sub|dub).*(count|episode|badge)` → 0 hits in app code.
  - `Video` model (REFERENCE/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Video.kt:29-44) has `audioTracks: List<Track>` and `subtitleTracks: List<Track>` where `Track(url, lang)`. This is PER-VIDEO, exposed at player time, NOT persisted to DB per episode.
  - `TrackSelect` (REFERENCE/app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/TrackSelect.kt:16-54) picks a preferred audio/subtitle track by matching against `preferredAudioLanguages` / `preferredSubLanguages` prefs (comma-separated locale list). It's a player-time selector, NOT a count.
  - `episodes` table has NO `is_dub` / `audio_lang` column. `episodestatsView` has NO sub/dub count.
  - Library badges are: DownloadsBadge, UnviewedBadge (unseen count), LanguageBadge (source language, e.g. "EN"), LocalBadge. NO sub/dub badge.
- **What aniyomi DOES have**: per-Video audioTracks (list of {url, lang}) at player time; preferred audio language preference; player UI sheet to switch audio track. The "dub" concept exists only as "the user picked a non-subtitle audio track in the player".
- **ANI-KUTA gap**: This is a CUSTOM feature the user wants that aniyomi does NOT have. To implement in ANI-KUTA: would need to either (a) inspect `Video.audioTracks` on first play and cache a boolean "has dub" / "has sub" per episode in a new DB column or SharedPreferences, then aggregate per anime for the library card badge; OR (b) have the extension's `getEpisodeList` return audio-language metadata. Either way, this is net-new work with no aniyomi reference to copy.

### Topic 8: Overall UI design language
- **Theme**: Material 3 (M3) — `androidx.compose.material3.MaterialTheme` (REFERENCE/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt:64-67). Custom Tachiyomi-forked `Scaffold` (presentation-core/.../material/Scaffold.kt) adds a `startBar` slot for NavigationRail. 18 built-in color schemes + Monet (dynamic). AMOLED toggle. No Material 2 components used in library/history/search.
- **Library cards**: 4 display modes (LibraryDisplayMode.kt): CompactGrid (cover + title overlay at bottom with gradient), ComfortableGrid (cover + title below, default for browse), CoverOnlyGrid (cover only), List (row). All use `ItemCover.Book` = **2:3 portrait aspect ratio** (REFERENCE/app/src/main/java/eu/kanade/presentation/entries/components/ItemCover.kt:18-22). Cover shape = `MaterialTheme.shapes.extraSmall` (~4dp rounded). Grid item selected state = `selectedOutline` drawBehind (secondary color rect). No elevation on cards (flat). Badges in top-start/top-end corners via `BadgeGroup` (RectangleShape, secondary/tertiary colors). ContinueViewingButton = FilledIconButton(PlayArrow, primaryContainer color, ~28dp) in bottom-end of cover.
- **History cards**: Fixed 96.dp Row, `ItemCover.Book` (2:3) on the left, text column in middle, icon buttons on right. No elevation. No card background — flat on scaffold background.
- **Search cards (global)**: 96.dp-wide `EntryComfortableGridItem` in a horizontal LazyRow per source. `InLibraryBadge` (heart) if favorited. Cover alpha = 0.34 if favorited.
- **Top-bar pattern**: M3 `TopAppBar` (small, NOT Large/Medium). `SearchToolbar` variant embeds a `BasicTextField` in the title slot for search. `TopAppBarScrollBehavior` (pinnedScrollBehavior) passed for collapse-on-scroll — BUT disabled when category tabs are visible (AnimeLibraryTab.kt:162: `scrollBehavior = scrollBehavior.takeIf { !tabVisible }`). Action mode (selection) swaps to a separate toolbar with X cancel + count + select-all/invert. Overflow menu uses M3 DropdownMenu.
- **Bottom-nav**: M3 `NavigationBar` (height 80dp, no horizontal spacer, REFERENCE/presentation-core/.../material/NavigationBar.kt). 5 visible tabs + 1 hidden in "More" (configurable via NavStyle: MOVE_MANGA_TO_MORE / MOVE_UPDATES_TO_MORE / MOVE_HISTORY_TO_MORE / MOVE_BROWSE_TO_MORE). Tab labels always shown. Badges on Updates (new-episode count) and Browse (extension-update count). Tablet UI uses `NavigationRail` instead (startBar slot). Bottom nav animates (expandVertically/shrinkVertically) when hidden.
- **Tab pattern (within library)**: M3 `PrimaryScrollableTabRow` (horizontally scrollable, edgePadding=0, no divider). Each tab = `Tab(text = TabText(name, badgeCount))`. Pill-shaped count badge via `Pill` (shapes.extraLarge, surfaceContainerHigh color).
- **Animation**: `materialFadeThroughIn/Out` (200ms) for tab switching; `animateItemFastScroll` for list item placement; `AnimatedVisibility` for bottom nav.
- **ANI-KUTA gap**: ANI-KUTA uses Material 3 (per prior agent). Three different top-bar patterns across Library/History/Search (sort dropdown / overflow menu / text field) — aniyomi uses SearchToolbar consistently. ANI-KUTA has 2-col grid only (no list/compact/cover-only modes). ANI-KUTA has no category tabs. ANI-KUTA has no badge system. To match aniyomi: (a) standardize on SearchToolbar with filter icon + overflow, (b) add display-mode toggle (4 modes), (c) add badge system (UnviewedBadge, DownloadsBadge, LanguageBadge), (d) add PrimaryScrollableTabRow for categories, (e) add ContinueViewingButton on cards when unseenCount > 0.

### Cross-cutting notes
- **SQLDelight is the single source of truth**: aniyomi uses SQLDelight with 6 materialized views (animelibView, episodestatsView, animehistorystatsView, animehistoryView, animeupdatesView, animeseasonstatsView) to pre-aggregate per-anime counts/timestamps. The Library screen reads `animelibView` (one row per favorited anime with all stats joined in); the History screen reads `animehistoryView` (one row per most-recently-watched episode per anime). All reactive via SQLDelight's `.asFlow()`.
- **Domain layer is fully wired**: every aniyomi domain interactor (GetAnimeHistory, GetNextEpisodes, SetSeenStatus, SetAnimeCategories, etc.) is registered in DI and used by ScreenModels. ANI-KUTA has the same domain code (~50% of it) but it's DEAD — not registered in DI, not called by any ScreenModel.
- **Tracker integration**: aniyomi syncs seen-status to AniList/MAL/Shikimori via `TrackEpisode` interactor (called from `updateEpisodeProgressOnComplete`). ANI-KUTA has a Supabase tracker (data/tracker/) — separate concern.
- **Coil for covers**: aniyomi uses coil3 (`AsyncImage`) with `AnimeCover` data class carrying (animeId, sourceId, isAnimeFavorite, url, lastModified). `AnimeCoverCache` handles disk caching + invalidation on cover change.

### Implementation priority for ANI-KUTA revamp (ranked by impact vs effort)
1. **HIGH impact, LOW effort**: Make History reactive + use real anime covers (AniList thumbnail URL already available) + fix navigation to pass episodeUrl. No SQLDelight migration needed — just collect WatchProgressStore.changes Flow + store cover URL in WatchProgressStore entry.
2. **HIGH impact, MED effort**: Add Library display-mode toggle (grid/list/compact/cover-only) + grid-size slider. Reuse aniyomi's CommonEntryItem.kt patterns.
3. **HIGH impact, MED effort**: Add Library badges (UnviewedBadge with actual unseenCount from WatchProgressStore, DownloadsBadge). Fix "Last watched" sort to use actual lastWatchedAt.
4. **HIGH impact, HIGH effort**: Add Library categories (categories table + animes_categories + AnimeCategoryScreen + LibraryTabs + HorizontalPager). Requires SQLDelight migration OR a CategoryStore (SharedPreferences) for simplicity.
5. **HIGH impact, HIGH effort**: Add source search to SearchScreen via AniyomiSourceBridge + Paging 3 infinite scroll + source filters.
6. **MED impact, HIGH effort**: Migrate from WatchProgressStore (SharedPreferences) to SQLDelight `episodes` + `animehistory` tables. Unlocks all of aniyomi's reactive flows + views for free, but is a large data migration.
7. **CUSTOM (no aniyomi reference)**: Sub/dub episode counts. Requires net-new DB column or SharedPreferences cache, populated by inspecting Video.audioTracks on first play.
8. **LOW effort**: Standardize top-bar pattern (SearchToolbar everywhere), extract shared AnimeCard composable.

---
Task ID: UI-DESIGN-ANALYSIS-1
Agent: Explore
Task: Analyze ANI-KUTA's existing UI design language (Material 3 Expressive) from the Detail page, episodes list, settings→details, episode layout settings, and player page

Work Log:
- Read /home/z/my-project/worklog.md tail (prior agents analyzed aniyomi reference for data/sort/search; noted ANI-KUTA uses M3 Expressive but didn't extract its concrete design tokens).
- Read the three theme files: Theme.kt, Expressive.kt (Typography + Shapes + AnikutaSprings), Color.kt (brand seed emerald + dark variants).
- Read DetailScreen.kt fully (1676 lines, not 1293 as task said): DetailHeader (blurred banner + cover + title), EpisodeRow / EpisodeRowSimple / EpisodeRowRich, DownloadButtonTall, InfoCard, InfoRow, AudioPills, long-press download ModalBottomSheet, ThreeStagePullRefresh integration, dynamic theming via MaterialTheme override.
- Read DetailViewModel.kt head (state shape: DetailState, EpisodeState, VideoPickerState, RefreshStage).
- Read all settings subpages: DetailsSettingsScreen (hub), DisplaySettingsScreen (toggles), LayoutSettingsScreen (segmented positions), MetadataSettingsScreen (master + conditional), plus shared SettingsComponents.kt, SelectableOptionCard.kt, SettingsSubpageScaffold.kt, EpisodeRowPreview.kt, SettingsHomeScreen.kt (CategoryCard).
- Read PlayerScreen.kt fully (1049 lines): MINIMIZED + FULLSCREEN modes, floating pill top bar, 16:9 rounded video container, below-video LazyColumn with fade gradient, ModalBottomSheet sheets (Quality/Subtitle/Audio/Server/Speed/More), EpisodeSwitchingOverlay, SubtitleStatusPill.
- Read ThreeStagePullRefresh.kt, DynamicTheming.kt (HSL-based cover color → full M3 ColorScheme), VideoPickerSheet.kt (ModalBottomSheet + accordion servers), PlayerSheet.kt (reusable sheet shell + SheetOption).
- Read HomeScreen.kt (the canonical M3 Expressive reference: FloatingTopBar, HeroSection, HomeSection accent bar, ExpressiveAnimeCard with spring scale + corner morph, SkeletonRow, AssistChip) — this is the design target the History screen explicitly says it follows.
- Read SkeletonBox.kt (shimmer placeholder) and skimmed the current Library/History/Search screens to know what's already there.
- Synthesized findings into the A–F report + cheat sheet below.

Stage Summary:

# ANI-KUTA UI Design Language — Full Extraction Report

The app's design language is **Material 3 Expressive** built on top of `androidx.compose.material3`. The signature expressive traits are: (1) **spring-based press feedback** with scale + corner-radius morph, (2) **"deliberate surface containment"** — tonal elevation + shadow elevation stacked on floating pill-shaped surfaces, (3) **per-content dynamic theming** where each anime's cover color generates a full M3 ColorScheme that re-themes the Detail page and Player, (4) **alternating surface-container levels** for list rows, (5) **uppercase primary-colored section headers** with a small tonal accent bar.

---

## A. Design Tokens

### A.1 Color scheme (`ui/theme/Theme.kt` + `Color.kt`)
- `AnikutaTheme` (`Theme.kt:71-92`): **dynamic color (Monet) on API 31+** via `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`; falls back to a static **emerald seed** on lower APIs. `dynamicColor = true` by default. Dark theme follows system. No AMOLED toggle.
- Static fallback brand seed (`Color.kt`): emerald.
  - Light: `primary #006B3C`, `primaryContainer #8FF8B7`, `secondary #4F6354`, `secondaryContainer #D2E8D4`, `tertiary #3A656F`, `tertiaryContainer #BEEAF6`, `error #BA1A1A`, `background/surface #FBFDFA`, `surfaceVariant #DCE5DB`, `outline #707973`.
  - Dark: `primary #74DB9C`, `primaryContainer #00522D`, `secondaryContainer #384B3D`, `background/surface #191C1A`, `surfaceVariant #404943`, `outline #8A9389`.
- **IMPORTANT GAP**: `Theme.kt` only assigns up through `surfaceVariant`/`outline`. It does **NOT** set the newer M3 `surfaceContainerLow/High/Container/ContainerHighest/outlineVariant` roles. Those are used *everywhere* in the actual UI code (`surfaceContainerLow`, `surfaceContainerHigh`, `surfaceContainer`, `outlineVariant`). They resolve correctly **only because Monet dynamic color schemes on API 31+ populate them automatically**. On API < 31 (static fallback) they fall back to M3 defaults — likely a latent visual bug but not blocking since most devices are 31+.
- Color roles actually used in the UI:
  - **Backgrounds**: `background` (page bg), `surface` (neutral), `surfaceContainerLow` (cards/even rows), `surfaceContainerHigh` (odd rows / floating top bars), `surfaceContainer` (inner title/synopsis/thumbnail panels), `surfaceVariant` (ep-number circle / icon backgrounds / chip containers).
  - **Accents**: `primary` (selected states, accent bars, brand text, badges), `primaryContainer`/`onPrimaryContainer` (ep-number "EP" badge, download-in-progress bg, episode-number prominent badge on player), `secondaryContainer`/`onSecondaryContainer` (icon chip backgrounds in top bars and settings LeadingIcons, genre chips).
  - **Pills/badges**: `outlineVariant` (date pill bg, audio pill bg) + `onSurfaceVariant` text.
  - **Destructive**: `error`/`errorContainer` (delete menu options, download error state).
  - **Borders**: `outlineVariant` (unselected SelectableOptionCard border), `primary` (selected border, 2.dp).
  - **Status overlays**: `Color.Black.copy(alpha=0.6f..0.85f)` for player scrims; `Color.White` for icons/text on banners.

### A.2 Dynamic per-anime theming (`ui/detail/DynamicTheming.kt`)
- `generateDynamicScheme(coverColor: Color): DynamicColorScheme` — takes the AniList `coverImage.color` hex and generates 7 HSL-derived variants (accent, surfaceLow, surfaceHigh, surfaceContainer, background, onSurface, onSurfaceVariant). Hue preserved; saturation scaled 0.20–0.35; lightness 0.08–0.24 for surfaces.
- `DynamicColorScheme.toM3ColorScheme()` — maps to a full `darkColorScheme()` (always dark — surfaces are very dark with cover-hue tint). Used by Detail page (`DetailScreen.kt:196-202`) and Player (`PlayerScreen.kt:207-213`) to wrap their content in `MaterialTheme(colorScheme = themedColorScheme)`. **This is the signature "depth" of the Detail/Player experience** — every color on the page shifts to match the anime.
- Guarded by `prefs.dynamicDetailTheming()` (toggle in DetailsSettingsScreen → "Appearance" → "Dynamic theming"). Default ON.

### A.3 Shape system (`Expressive.kt:140-146`)
```kotlin
val AnikutaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
```
**But the code uses these explicit radii directly** (not via `MaterialTheme.shapes.*`), so the de-facto shape vocabulary is:
| Radius | Used for |
|---|---|
| `2.dp` | tonal accent bars (3×16dp / 4×20dp) |
| `4.dp` | skeleton text-line corners |
| `6.dp` | pills (date, audio), episode-number overlay badge, EP-number pill, SelectableOptionCard selected-border inner |
| `8.dp` | AssistChip (genres), title/synopsis/thumbnail-inner Surface, segmented-row pills, InfoRow separators, SelectableOptionCard unselected-border |
| `10.dp` | episode thumbnails |
| `12.dp` | episode row card, download button tall, cover image on detail header, episode thumbnail wrapper, InfoCard, SelectableOptionCard container, StyledSegmentedRow container, LeadingIcon, metadata note Surface, modal bottom-sheet long-press download menu Surface |
| `14.dp` | player 16:9 video container |
| `16.dp` | SettingsGroupCard, ExpressiveAnimeCard (resting), SkeletonCard, pull-refresh indicator, video-picker resolving spinner Surface, SubtitleStatusPill |
| `20.dp` | floating pill top bars (HomeScreen, LibraryTopBar, player MINIMIZED top bar), SubtitleStatusPill, resume "start over" pill |
| `24.dp` | HeroSection (HomeScreen) |
| `CircleShape` | 36dp icon circles in top bars, 40dp ep-number circle, 44dp player lock button |
| `RoundedCornerShape(percent = 50)` | alias for circle on 36dp icon buttons |

### A.4 Typography (`Expressive.kt:21-132`)
Standard M3 type scale, **FontFamily.Default** throughout, weights lean bold:
- `displayLarge` 57/Bold/64, `displayMedium` 45/Bold/52, `displaySmall` 36/SemiBold/44
- `headlineLarge` 32/SemiBold/40, `headlineMedium` 28/SemiBold/36, `headlineSmall` 24/SemiBold/32
- `titleLarge` 22/SemiBold/28 — used for screen titles (SettingsSubpageScaffold, HomeSection headers, player episode title, detail header anime title with `Bold` override)
- `titleMedium` 16/Medium/24 — **the workhorse**: card titles, row titles, section titles, top-bar brand text, sheet titles, episode-section headers. Almost always rendered with `fontWeight = FontWeight.Bold` or `SemiBold` override.
- `titleSmall` 14/Medium/20 — episode-row titles (Bold override)
- `bodyLarge` 16/Normal/24 — dialog body, long-press menu options
- `bodyMedium` 14/Normal/20 — descriptions, synopsis, settings row subtitles, InfoRow values, SelectableOptionCard titles
- `bodySmall` 12/Normal/16 — secondary/muted text everywhere (counts, source badge subtitle, episode date text, info-card body, error messages)
- `labelLarge` 14/Medium/20 — SubtitleStatusPill icon
- `labelMedium` 12/Medium/16 — **section-header uppercase labels** ("LIVE PREVIEW", "CUSTOMIZE", SettingsGroupCard titles, `letterSpacing = 1.sp`), ep-number circle text, EP-badge prominent player label, segmented-row pill text
- `labelSmall` 11/Medium/16 — date pill, audio pills, EP-number overlay, source badge, "Fetching metadata…" text, hero badge, skeleton genre chip

Pattern: **almost every Text overrides fontWeight to Bold or SemiBold** regardless of the style's default. Body text is the exception.

### A.5 Elevation strategy — "deliberate surface containment"
Almost no flat surfaces; depth comes from **tonal elevation + shadow elevation stacked**:
| Surface | tonalElevation | shadowElevation |
|---|---|---|
| Episode row card (Detail) | — (color is explicit alt surfaceContainerLow/High) | — |
| DownloadButtonTall | `1.dp` | — |
| SettingsGroupCard | `1.dp` | — |
| CategoryCard (settings home) | `1.dp` | — |
| ExpressiveAnimeCard (Home) | `CardDefaults.cardElevation(default=2.dp, pressed=4.dp)` | — |
| SkeletonCard (Home) | (Card default) | — |
| FloatingTopBar (Home/Library/Player) | `3.dp` | `6.dp` |
| HeroSection (Home) | `4.dp` | `8.dp` |
| Three-stage pull-refresh indicator | `3.dp` | — |
| Player video container | — | — (rounded clip on Black bg) |
| ModalBottomSheet (player sheets) | (M3 default) | (M3 default) |

So the elevation hierarchy is: **list rows/cards = tonal-only 1–2dp** (subtle), **floating top bars = tonal 3 + shadow 6**, **hero/banner = tonal 4 + shadow 8**. This layering is the "depth" the user loves.

### A.6 Springs — `AnikutaSprings` (`Expressive.kt:155-185`)
```kotlin
object AnikutaSprings {
    val spatial    = spring<Float>(dampingRatio = MediumBouncy, stiffness = MediumLow)   // position/size
    val effects    = spring<Float>(dampingRatio = NoBouncy,     stiffness = Medium)      // color/opacity/scale (corners)
    val press      = spring<Float>(dampingRatio = MediumBouncy, stiffness = High)        // card/button press (snappy)
    val expansion  = spring<Float>(dampingRatio = LowBouncy,    stiffness = MediumLow)   // hero/fab expansion
    val color      = spring<Color>(dampingRatio = NoBouncy,     stiffness = Medium)      // color transitions
}
```
- `press` is the **only one used for press feedback** (scale + corner morph). Snappy thanks to `StiffnessHigh`.
- `effects` is used for the corner-radius morph on `ExpressiveAnimeCard` (no overshoot → corners grow smoothly).
- `color` is declared but I did not see it used in the files I read (available for future color transitions).
- `spatial`/`expansion` declared but not used in the analyzed files.
- **Note**: springs are *available* but currently only `ExpressiveAnimeCard` (Home) + `CategoryCard`/`ClickableSettingsRow` (settings) actually apply them. The Detail page's `EpisodeRow` and the player's episode rows do **NOT** have press-scale feedback today — tapping a row fires `Surface(onClick=…)` with default ripple only. **This is a gap to fix when applying the language to Library/History/Search.**

---

## B. Component Patterns

### B.1 Cards

**(a) ExpressiveAnimeCard** (`HomeScreen.kt:311-401`) — the canonical M3 Expressive card:
```kotlin
Card(
    modifier = Modifier.width(140.dp).height(300.dp).scale(scale),
    shape = RoundedCornerShape(cornerRadius.dp),       // 16dp → 20dp on press
    colors = CardDefaults.cardColors(containerColor = surfaceContainerLow),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    interactionSource = interactionSource,
    onClick = { onAnimeClick(anime.id) },
) {
    Column {
        AsyncImage(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(topStart=cornerRadius.dp, topEnd=cornerRadius.dp)), contentScale = Crop)
        Column(Modifier.fillMaxWidth().height(100.dp).padding(horizontal=10.dp, vertical=8.dp), spacedBy=2.dp) {
            Text(title, labelMedium Medium, maxLines=2 minLines=2)
            Row(spacedBy=6.dp) { Text("★ $score", labelSmall onSurfaceVariant); Text("· $eps eps", labelSmall onSurfaceVariant) }
            Text(genres.take(2).joinToString(), labelSmall primary, maxLines=1)
        }
    }
}
```
- **Cover aspect ratio ~2:3** (140w × 200h = 0.7, close to 2:3 portrait).
- Press: `scale 1 → 0.96` via `AnikutaSprings.press`; `cornerRadius 16 → 20` via `AnikutaSprings.effects`. Image top corners morph with the card.
- **How to apply to Library/History/Search**: this is *already* the model — Library's `LibraryCard` should mirror it exactly (it currently lives in `LibraryScreen.kt` but the Home version is the reference). For History rows use a horizontal variant (cover left, text middle, actions right — see aniyomi pattern from prior agents).

**(b) EpisodeRow card** (`DetailScreen.kt:996-1061`) — alternating-bg Surface, no shadow:
```kotlin
val cardColor = if (index % 2 == 0) surfaceContainerLow else surfaceContainerHigh
Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = cardColor,
    onClick = onClick,            // default ripple — NO spring scale today
) { if (isRich) EpisodeRowRich(...) else EpisodeRowSimple(...) }
```
- Inner padding: `horizontal = 12.dp, vertical = 12.dp`.
- Thumbnail: `RoundedCornerShape(10.dp)` Surface wrapper + AsyncImage clipped to 10.dp. Sizes: small `100×56`, medium `120×68`, large `160×90`.
- Episode number overlay on thumbnail: `Surface(RoundedCornerShape(6.dp), color = Black.copy(alpha=0.7f))` + `Text("EP $n", labelSmall Bold White, padding horizontal=6.dp vertical=2.dp)`, aligned `TopStart` with `padding(4.dp)`.
- Title: nested `Surface(RoundedCornerShape(8.dp), color = surfaceContainer, padding horizontal=10.dp vertical=6.dp)` + `Text(title, titleSmall Bold, maxLines=1, Ellipsis)`.
- "Badge" ep-number variant (when `episodeNumberPosition == "badge"`): `Surface(RoundedCornerShape(6.dp), primaryContainer)` + `Text("EP $n", labelSmall Bold onPrimaryContainer, padding 6/2)` placed *inside* the title Surface, before the title text, with `Spacer(width=8.dp)`.
- Synopsis: `Surface(RoundedCornerShape(8.dp), surfaceContainer)` + `Text(bodySmall onSurfaceVariant, maxLines = if(expanded) MAX else 3, Ellipsis, padding horizontal=10.dp vertical=6.dp, clickable toggles expanded)`.
- Date pill + Audio pills row: `Row(spacedBy=6.dp)`. Date: `Surface(RoundedCornerShape(6.dp), outlineVariant)` + `Text(date, labelSmall Medium onSurfaceVariant, padding 8/3, maxLines=1, softWrap=false)`.
- **How to apply**: This card is the visual signature of the episodes list. For Library grid cards use ExpressiveAnimeCard; for History *list rows* adopt this Surface-with-alternating-bg + nested-pill-title pattern but with the cover as the leading thumbnail.

**(c) DownloadButtonTall** (`DetailScreen.kt:859-962`) — 48dp-wide tall button beside episode card:
```kotlin
Surface(
    modifier = Modifier.width(48.dp).fillMaxHeight().combinedClickable(onClick, onLongClick),
    shape = RoundedCornerShape(12.dp),
    color = backgroundColor,              // state-dependent, see below
    tonalElevation = 1.dp,
) { Box(Center) { /* icon or CircularProgressIndicator(progress) 24dp strokeWidth 2dp */ } }
```
- Background by state: `DOWNLOADING → primaryContainer`, `ERROR/RECONNECTING → errorContainer`, `DOWNLOADED/onDisk → primaryContainer.copy(alpha=0.5f)`, `PAUSED/other → alternating default (opposite level of card)`.
- Icon tint by state: `ERROR/RECONNECTING → error`, `DOWNLOADED/onDisk → primary`, `other → onSurfaceVariant`.
- Icon set: `Download` (idle/paused), `DownloadDone` (done), `Error` (failed), indeterminate `CircularProgressIndicator` (queued/resolving/muxing), determinate `CircularProgressIndicator(progress = {pct/100f})` (downloading), color-cycling `CircularProgressIndicator` for RECONNECTING (tween 500ms Reverse between `error` and `0xFFFFA000`).

**(d) SkeletonCard / SkeletonAnimeCard** (`SkeletonBox.kt` + `HomeScreen.kt:452-486`): `Card(RoundedCornerShape(16.dp), surfaceContainerLow)` with `Surface(surfaceVariant, RoundedCornerShape(topStart=16, topEnd=16))` 200dp image area + three `Surface(surfaceVariant, RoundedCornerShape(4.dp))` text-line placeholders. `SkeletonBox` itself pulses alpha 0.3↔0.7 over 800ms `infiniteRepeatable(tween(800), Reverse)`.

### B.2 Episode rows — see B.1(b) above. Additional notes:
- Two variants: `EpisodeRowSimple` (no thumbnail, no summary — ep-number circle + title pill + date/audio pills below) and `EpisodeRowRich` (thumbnail + configurable title/synopsis/date positions).
- No explicit play button — **tapping anywhere on the row plays** (`Surface(onClick = onClick)`).
- Download button is *beside* the row (`episode_row` placement) or *inside* the synopsis area (`synopsis` placement) — both use `Row(IntrinsicSize.Min)` + `fillMaxHeight` so the button matches the card's height.
- Long-press → `ModalBottomSheet` with state-dependent options (Play downloaded / Delete / Cancel / Resume / Retry / Download).

### B.3 Top bars
**No standard `TopAppBar` is used anywhere.** The app replaces it with a custom **floating pill Surface**:
```kotlin
// HomeScreen.FloatingTopBar / LibraryTopBar / PlayerScreen MINIMIZED top bar — identical pattern
Surface(
    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
    shape = RoundedCornerShape(20.dp),
    color = surfaceContainerHigh,
    tonalElevation = 3.dp,
    shadowElevation = 6.dp,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), SpaceBetween, CenterVertically) {
        Text(brand, titleMedium Bold primary)         // "ANI-KUTA" / "Library" / "AniKuta"
        // trailing: 36dp circle (clip 50%) with secondaryContainer bg + icon 18dp onSecondaryContainer
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(percent=50)).background(secondaryContainer).clickable {...}) { Icon(..., size=18.dp) }
    }
}
```
- **Detail page is the exception**: it has NO top bar — instead a blurred cover banner (360dp) with a Row of plain IconButtons (back/save/share) overlaid `statusBarsPadding().padding(horizontal=8.dp, vertical=4.dp)`, icons tinted White (or `primary` when saved).
- **Settings subpages** use `SettingsSubpageScaffold` (`SettingsSubpageScaffold.kt:28-61`): a `Column(statusBarsPadding)` with a header `Row(padding horizontal=8.dp vertical=4.dp)` containing `IconButton(onBack)` (AutoMirrored ArrowBack) + `Spacer(4.dp)` + `Text(title, titleLarge Bold, weight=1f)` + `actions()` slot. NO Surface, NO elevation — flat on background. This is a *different* top-bar pattern from the floating pill.
- **How to apply**: Library already uses the floating pill (`LibraryTopBar`). History and Search should match — Search should embed a `BasicTextField` in the trailing slot or replace the title Text with a search field (aniyomi `SearchToolbar` style). Don't introduce `TopAppBar` — keep the floating pill.

### B.4 Bottom sheets
- `ModalBottomSheet` (M3) with `rememberModalBottomSheetState()`.
- **Player sheets** (`PlayerSheet.kt:24-53`) — the reusable shell:
  ```kotlin
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
      containerColor = surfaceContainerLow,
  ) {
      Column(padding horizontal=20.dp vertical=8.dp) {
          Text(title, titleMedium Bold onSurface, padding bottom=12.dp)
          content()
      }
  }
  ```
- **VideoPickerBottomSheet** (`VideoPickerSheet.kt:139-142`) — uses default ModalBottomSheet (no custom shape), `Column(padding bottom=24.dp, animateContentSize())`, header `Row(padding horizontal=24.dp vertical=8.dp)` with title + optional refreshing badge, then `LazyColumn(heightIn(max=420.dp))` with `HorizontalDivider(outlineVariant 0.3 alpha)` between rows.
- **Detail long-press download menu** (`DetailScreen.kt:621-693`) — plain `ModalBottomSheet`, `Column(padding=16.dp)`, episode title `titleSmall Bold`, `Spacer(12.dp)`, then `DownloadMenuOption` rows (icon 22dp + `Spacer(16.dp)` + text `bodyLarge`; `tint = error` if destructive).
- `SheetOption` (`PlayerSheet.kt:55-94`) — selection row: `Row clickable padding vertical=6.dp SpaceBetween`, `Column(weight=1)` with title (`bodyMedium` Bold if selected else Medium; `primary` if selected else `onSurface`) + optional subtitle (`bodySmall onSurfaceVariant`); trailing `"✓"` (`bodyLarge Bold primary`) when selected.
- **Handle**: default M3 drag handle (no custom). Sheets rely on M3 defaults for scrim + handle.

### B.5 Selectors
- **`SelectableOptionCard`** (`SelectableOptionCard.kt:42-105`) — card-style radio group for 2–4 options. Each option is a `Surface(RoundedCornerShape(12.dp), color=surface, border=BorderStroke(if selected 2.dp else 1.dp, if selected primary else outlineVariant), clickable)`. Inner `Row(padding 14/12, SpaceBetween)`: `Column(weight=1)` with label (`bodyMedium` Bold if selected else Medium, `primary` if selected else `onSurface`) + optional desc (`bodySmall onSurfaceVariant`); trailing `Icon(Check, primary)` when selected. Group wrapped in `Column(padding horizontal=16.dp vertical=12.dp)` with title (`bodyMedium Medium`) + optional subtitle (`bodySmall onSurfaceVariant`) + `Spacer(10.dp)` + options `Column(spacedBy=8.dp)`.
- **`StyledSegmentedRow`** (`SelectableOptionCard.kt:117-152`) — pill-style segmented control for 2–3 short labels. Container `Surface(RoundedCornerShape(12.dp), surfaceVariant.copy(alpha=0.5f), padding=4.dp, spacedBy=4.dp)`; each segment `Surface(weight=1, RoundedCornerShape(8.dp), color=primary if selected else Transparent, onClick)` + `Text(labelMedium, Bold if selected else Medium, onPrimary if selected else onSurfaceVariant, padding vertical=8.dp, Center)`.
- **`Switch`** (M3) — used in `SwitchSettingsRow`.
- **`DropdownMenu`** (M3) — used in `LibraryTopBar` sort menu.
- No `SegmentedButton` (M3) is used — the custom `StyledSegmentedRow` is preferred.

### B.6 Buttons
- **Filled `Button`** (M3) — only for critical actions (player error "Go Back", dialog confirm).
- **`TextButton`** — "Retry", "Show more/less", dialog dismiss/confirm.
- **`IconButton`** (M3) — back, save, share, sort — bare (no background).
- **Trailing icon circles** — `Box(36.dp, clip 50%, secondaryContainer, clickable)` + `Icon(18.dp, onSecondaryContainer)`. Used in every floating top bar.
- **No FABs** anywhere in the analyzed screens.
- **No `FilledTonalButton`/`OutlinedButton`** in the analyzed files (could be introduced for Library/History actions if needed, but the design language leans on `Surface(onClick=…)` + custom pills rather than M3 button variants).

### B.7 Lists / dividers
- `LazyColumn` with `verticalArrangement = Arrangement.spacedBy(8.dp)` or `12.dp`.
- `LazyVerticalGrid(GridCells.Fixed(2))` with `horizontalArrangement = Arrangement.spacedBy(12.dp)` and `verticalArrangement = Arrangement.spacedBy(12.dp)` (Library).
- `LazyRow` with `contentPadding = PaddingValues(horizontal = 16.dp)`, `horizontalArrangement = Arrangement.spacedBy(8.dp)` (chips) or `10.dp` (cards).
- **Dividers**: `HorizontalDivider(color = outlineVariant)` between settings rows; `HorizontalDivider(color = outlineVariant.copy(alpha = 0.3f))` between video-picker rows; `HorizontalDivider(thickness = 1.dp, color = outlineVariant)` between player details section and episodes list. Settings GroupCards use `HorizontalDivider()` (default) between LabeledSections and rows.
- **No item dividers in grids/episode-lists** — alternating row colors provide visual separation instead.

### B.8 Empty / error / loading states
- **Loading**:
  - Detail: full-screen centered `CircularProgressIndicator()`.
  - Episode list (Detail): inline `Row` with `CircularProgressIndicator(size=16.dp, strokeWidth=2.dp)` + `Spacer(8.dp)` + `Text("Searching extensions…" / "Loading episodes…", bodySmall onSurfaceVariant)`.
  - Home sections: `SkeletonRow` (5 skeleton cards).
  - Library: `Text("Loading your library…", bodyMedium onSurfaceVariant)` + 4× `SkeletonCard()`.
  - Metadata enrichment: `Surface(RoundedCornerShape(12.dp), surfaceVariant)` + `Row(padding 8/3)` with `CircularProgressIndicator(12dp, 1.5dp)` + `Text("Fetching metadata…", labelSmall onSurfaceVariant)`.
- **Error**:
  - Detail: `Column(fillMaxSize, padding 24.dp, statusBarsPadding, CenterHorizontally, CenterVertically)` + `Text("Couldn't load: $msg", bodyMedium, error)` + `TextButton("Retry")` + `TextButton("Go back")`.
  - Episode error: `InfoCard(title, body)` = `Surface(RoundedCornerShape(12.dp), surfaceContainerLow, padding 16.dp)` + title `bodyMedium SemiBold` + body `bodySmall onSurfaceVariant`.
  - Player error: full-screen `Box(background Black.copy(alpha=0.85f), Center)` + `Text("⚠️", headlineLarge White)` + message + `Button("Go Back")`.
- **Empty**:
  - Detail "Not yet aired" / "No streaming source available" → `InfoCard`.
  - Library `EmptyState()` (custom composable).
  - History `HistoryEmptyState()`.

---

## C. Layout Patterns

### C.1 Detail page (`DetailScreen.kt`)
- `Box(fillMaxSize, background = themedBg)` → `ThreeStagePullRefresh` → `LazyColumn(contentPadding = PaddingValues(bottom=24.dp))`.
- **Header** (`DetailHeader`, `DetailScreen.kt:701-820`): edge-to-edge `Box(fillMaxWidth, height=360.dp)` with three stacked layers:
  1. `AsyncImage(fillMaxSize, blur(8.dp), ContentScale.Crop)` — blurred cover.
  2. `Box(fillMaxSize, background = coverColor.copy(alpha = 0.2f))` — theme tint.
  3. `Box(fillMaxSize, background = Brush.verticalGradient(Black 0.2 → Transparent → background))` — readability fade.
  - Overlaid top-bar Row: `statusBarsPadding().padding(horizontal=8.dp, vertical=4.dp)`, `SpaceBetween`, bare IconButtons (back/save/share), icons White (or `primary` when saved).
  - Cover + title at `align(BottomStart).padding(horizontal=16.dp)`: `Row(Bottom, spacedBy=16.dp)` with `AsyncImage(100×150.dp, clip RoundedCornerShape(12.dp), Crop)` + `Column(padding bottom=8.dp)` with title (`titleLarge Bold onBackground, maxLines=3, Ellipsis`) + meta row (`bodySmall onSurfaceVariant`, "★ score · status · N eps").
- **Body sections** (LazyColumn items, 16.dp horizontal padding, 8.dp vertical):
  - Genres: `LazyRow(contentPadding horizontal=16.dp, spacedBy=8.dp, padding vertical=8.dp)` of `AssistChip(shape=RoundedCornerShape(8.dp))`.
  - Synopsis: `Column(padding 16/8)` with "Synopsis" `titleMedium Bold` + `Spacer(8.dp)` + `Text(bodyMedium onSurfaceVariant, maxLines = 4 or MAX, Ellipsis)` + `TextButton("Show more/less")`.
  - Information: `Column(padding 16/8)` with "Information" `titleMedium Bold` + `InfoRow`s (label/value `bodyMedium`, label `onSurfaceVariant`, value `Medium`).
  - Episodes header: `Column(padding 16/8)` with `Row` ("Episodes" `titleMedium Bold` + optional fetching-metadata pill) + `Row` ("N episodes" `bodySmall onSurfaceVariant weight=1` + source badge `Surface(RoundedCornerShape(8.dp), secondaryContainer, padding 8/4)` + `Text(sourceName, labelSmall onSecondaryContainer)`).
- **Episodes layout is configurable** (`animeInfoPosition`):
  - `"above"` (full-page scroll): episodes render directly as LazyColumn items with `Box(padding horizontal=6.dp vertical=4.dp)` wrapper; download button beside each row.
  - `"below"` (default): episodes in an inner `LazyColumn(fillMaxWidth, heightIn(max=600.dp), spacedBy=8.dp)`.
- **Three-stage pull-to-refresh** wraps the whole LazyColumn (see D.3).

### C.2 Settings subpages (`SettingsSubpageScaffold` + per-screen)
- `SettingsSubpageScaffold(title, onBack, actions?, content)`: `Column(fillMaxSize, statusBarsPadding)` → header `Row(padding 8/4)` → `content()`.
- **Hub pages** (DetailsSettingsScreen): `LazyColumn(fillMaxWidth, contentPadding bottom=24.dp, spacedBy=12.dp)` of items. Each item is `Column(padding horizontal=16.dp)` containing either a `Text("LIVE PREVIEW"/"CUSTOMIZE", labelMedium Bold primary letterSpacing=1.sp, padding 4)` label or a `SettingsGroupCard(title) { rows separated by HorizontalDivider() }`.
- **Subpages with live preview** (LayoutSettingsScreen, DisplaySettingsScreen, MetadataSettingsScreen): `Column(fillMaxSize)` → sticky preview block at top (`Column(padding bottom=20.dp)` with `Text("LIVE PREVIEW", labelMedium Bold primary, padding 16/4)` + `Column(padding horizontal=16.dp) { EpisodeRowPreview(...) }`) → `LazyColumn(fillMaxSize, contentPadding bottom=24.dp, spacedBy=12.dp)` of `Column(padding horizontal=16.dp) { SettingsGroupCard { LabeledSection(...) { StyledSegmentedRow(...) } } }`.
- `LabeledSection(title, description, content)` (`LayoutSettingsScreen.kt:88-100`): `Column(padding 16/12)` + `Text(title, bodyMedium SemiBold)` + `Text(description, bodySmall onSurfaceVariant)` + `Spacer(10.dp)` + content.
- `SettingsGroupCard` (`SettingsComponents.kt:25-55`): `Surface(fillMaxWidth, RoundedCornerShape(16.dp), surfaceContainerLow, tonalElevation=1.dp)` → `Column` → header `Row(padding start=16 end=16 top=14 bottom=6, CenterVertically)` with `Surface(width=3.dp height=16.dp, RoundedCornerShape(2.dp), primary)` accent bar + `Spacer(8.dp)` + `Text(title.uppercase(), labelMedium Bold primary letterSpacing=1.sp)` → `content()`.

### C.3 Player page (`PlayerScreen.kt`)
- Wrapped in `MaterialTheme(colorScheme = themedColorScheme)` (per-anime dynamic theming, same as Detail).
- **MINIMIZED mode** (`PlayerScreen.kt:246-606`): `Column(fillMaxSize, background)`:
  1. **Floating pill top bar** (conditional on `showTopBar` pref) — same Surface pattern as Home/Library but with two 36dp circle icon buttons (back + settings) flanking the "AniKuta" title.
  2. **Video area**: `Box(fillMaxWidth, then(if !showTopBar statusBarsPadding else padding top=8.dp), background)` → inner `Box(fillMaxWidth, padding horizontal=6.dp, aspectRatio(16f/9f), clip RoundedCornerShape(14.dp), background Black)` → `AndroidView(MPV)` + overlay (loading `EpisodeSwitchingOverlay` or `MinimizedControls`).
  3. **Below-video content** (`Box(fillMaxSize)`): `LazyColumn(state, fillMaxSize, contentPadding = PaddingValues(start=8, end=8, top=13, bottom=24), spacedBy=8.dp)` with items: episode details (EPISODE N pill + titleLarge Bold + date + summary) → `ServerVersionDropdowns` → `HorizontalDivider(outlineVariant, 1.dp, padding vertical=4.dp)` → "Episodes" header (`titleMedium Bold onSurface, padding 4/2`) → `PlayerEpisodeRowInline` items. A 35dp `Box(TopCenter, verticalGradient background→transparent)` fade overlay sits on top so episodes visually dissolve before touching the video.
- **FULLSCREEN mode** (`PlayerScreen.kt:607-728`): `Box(fillMaxSize, background Black)` → `AndroidView(MPV fillMaxSize)` → `PlayerGestureHandler` (when not locked) + `FullscreenControls` OR a `Box(pointerInput detectTapGestures { showLockButton })` (when locked). Lock button: `Surface(CircleShape, Black 0.6 alpha, TopStart padding 16/16, size 44.dp, clickable unlock)` + `Icon(Lock, White, 22.dp)`.
- **Auto-hide**: controls auto-hide after 4s (fullscreen) / 5s (minimized) via `LaunchedEffect(controlsVisible, playerMode, controlsLocked) { delay(...); setControlsVisible(false) }`. Lock button auto-hides after 3s.
- **Sheets** (6 of them): `QualitySheet`, `SubtitleTracksSheet`, `AudioTracksSheet`, `ServerSheet`, `SpeedSheet`, `MoreOptionsSheet` — all use `PlayerSheet` shell.
- **Overlays**: `EpisodeSwitchingOverlay` (episode switch), `SubtitleStatusPill` (auto-fade 4s, `Surface(RoundedCornerShape(20.dp), Black 0.75 alpha, padding 14/8)` + icon + label), "start over" pill (`Surface(RoundedCornerShape(20.dp), Black 0.8 alpha, BottomCenter padding bottom=100.dp, clickable)`), error overlay (`Box(Black 0.85, Center)` + ⚠️ + message + Button), first-time prompt.

---

## D. Interaction Patterns

### D.1 Press feedback
- **`ExpressiveAnimeCard`** (Home, `HomeScreen.kt:316-327`): the signature. `scale 1 → 0.96` with `AnikutaSprings.press`; `cornerRadius 16 → 20` with `AnikutaSprings.effects`. Both via `animateFloatAsState(targetValue = if (isPressed) … else …)`. `interactionSource = remember { MutableInteractionSource() }` + `collectIsPressedAsState()`.
- **`CategoryCard`** (settings home, `SettingsHomeScreen.kt:128-135`): `scale 1 → 0.97` with `AnikutaSprings.press`. Uses `combinedClickable` so it can also support long-press if needed.
- **`ClickableSettingsRow`** (`SettingsComponents.kt:78-84`): `scale 1 → 0.98` with `AnikutaSprings.press`.
- **Episode rows / Library cards / History rows / Search cards**: currently use plain `Surface(onClick=…)` or `Card(onClick=…)` — **NO spring scale today**. This is the gap to close when applying the language.

### D.2 Transitions
- `AnimatedVisibility` (`expandVertically`/`shrinkVertically`) for conditional settings sections (MetadataSettingsScreen per-field toggles).
- `animateContentSize()` on `VideoPickerBottomSheet` Column (smooth height changes when servers expand/collapse).
- `AnimatedVisibility` with `fadeIn()`/`fadeOut()` for the Three-stage pull-refresh indicator.
- `animateColor` (infiniteRepeatable tween 500ms Reverse) for the RECONNECTING download spinner color.
- `infiniteRepeatable(tween(800), RepeatMode.Reverse)` for skeleton shimmer.
- **No shared-element transitions** in the analyzed screens.
- **No `materialFadeThrough`** for tab switches (the prior agent's note about aniyomi using it doesn't apply here — ANI-KUTA has no tabs yet).

### D.3 Gestures
- **Three-stage pull-to-refresh** (Detail only, `ThreeStagePullRefresh.kt`): novel custom `NestedScrollConnection`. Stages at 100/200/300dp pull (max 360dp). Damping 0.5× on `onPostScroll`. Fires `onRefresh(stage)` on `onPreFling` if past stage 1. Indicator: `Surface(RoundedCornerShape(16.dp), surfaceVariant.copy(alpha=0.95f), tonalElevation=3.dp, padding 20/10)` with `CircularProgressIndicator(18dp, 2dp)` or `KeyboardArrowDown(20dp)` + label `bodySmall Medium onSurfaceVariant`. Aligned `TopCenter padding top=56.dp`.
- **Standard M3 `PullToRefreshBox`** (Home, Library): `androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing, onRefresh)`.
- **Long-press**:
  - Episode rows (Detail) — `combinedClickable(onClick, onLongClick)` on `DownloadButtonTall`; long-press opens the state-dependent download `ModalBottomSheet`.
  - History rows — long-press triggers haptic (`LocalHapticFeedback`) + delete confirmation `AlertDialog`.
- **Player gestures** (`PlayerGestureHandler`, fullscreen only): tap to toggle controls, double-tap to seek ±10s (per `FullscreenControls`), horizontal/vertical drag for seek/brightness/volume.
- **Tap-to-expand synopsis**: episode-row synopsis `Text` is `.clickable { summaryExpanded = !summaryExpanded }`.
- **Tap-to-expand description**: Detail page "Show more/less" `TextButton`.

---

## E. The "Expressive" Elements

What makes this "Material 3 Expressive" vs vanilla M3:

1. **`AnikutaSprings` object** (`Expressive.kt:155-185`) — five named spring specs (`spatial`, `effects`, `press`, `expansion`, `color`) with tuned damping/stiffness. Vanilla M3 uses tween/easing; this app uses springs for all motion.

2. **Spring-based press feedback with shape morphing** — the signature expressive interaction. `ExpressiveAnimeCard` simultaneously scales (1→0.96, `press` spring) AND morphs its corner radius (16→20dp, `effects` spring) on press. The image's top corners morph with the card via `clip(RoundedCornerShape(topStart=cornerRadius.dp, topEnd=cornerRadius.dp))`. This is the M3 Expressive "shape responds to touch" pattern.

3. **"Deliberate surface containment"** — floating pill-shaped Surfaces with stacked tonal + shadow elevation (top bars tonal 3 + shadow 6, hero tonal 4 + shadow 8). The pill shape (`RoundedCornerShape(20.dp)` on a full-width bar) is itself expressive — vanilla M3 uses rectangular `TopAppBar`s.

4. **Per-content dynamic theming** — `generateDynamicScheme(coverColor).toM3ColorScheme()` re-themes the entire Detail page and Player per-anime, using HSL manipulation of the AniList cover color. Every `MaterialTheme.colorScheme.*` reference inside the wrapped content shifts. This creates a sense of "the UI is made of the anime's own colors."

5. **Alternating surface-container levels** for list rows (`surfaceContainerLow` even / `surfaceContainerHigh` odd) — gives the episodes list a built-in zebra-stripe rhythm without dividers. Vanilla M3 doesn't prescribe this.

6. **Tonal accent bars + uppercase primary labels** for section headers — `Surface(3×16dp or 4×20dp, RoundedCornerShape(2.dp), primary)` + `Text(title.uppercase(), labelMedium Bold primary, letterSpacing=1.sp)`. Used in `SettingsGroupCard`, `HomeSection`, and the "LIVE PREVIEW"/"CUSTOMIZE" labels. A distinctive expressive header treatment.

7. **`AssistChip` with `RoundedCornerShape(8.dp)` + `secondaryContainer`** (genre chips) rather than vanilla M3's default `AssistChip` shape.

8. **Custom selectors** (`SelectableOptionCard`, `StyledSegmentedRow`) rather than M3 `SegmentedButton`/`RadioButton` — border-based selection with `primary` accent, pill-style segmented control with `surfaceVariant` 50% alpha container.

Where the "depth" comes from: **a combination of (a) stacked tonal + shadow elevation on floating surfaces, (b) alternating surface-container levels on list rows, (c) per-content color theming, and (d) nested Surface-within-Surface construction** (episode rows have a `surfaceContainerLow/High` outer card containing `surfaceContainer` inner title/synopsis panels — two levels of tonal depth within a single row).

---

## F. Reusable Components Inventory

### `ui/theme/` (design tokens — usable everywhere)
| Composable / value | Signature | Used by |
|---|---|---|
| `AnikutaTheme` | `(darkTheme, dynamicColor, content) -> Unit` | App root |
| `AnikutaTypography` | `Typography` value | AnikutaTheme |
| `AnikutaShapes` | `Shapes` value | AnikutaTheme (but most code uses explicit radii) |
| `AnikutaSprings` | object with `spatial`, `effects`, `press`, `expansion`, `color` | ExpressiveAnimeCard, CategoryCard, ClickableSettingsRow |

### `ui/components/`
| Composable | Signature | Used by |
|---|---|---|
| `SkeletonBox` | `(modifier, cornerRadius=8)` | (available; Home uses its own SkeletonRow) |
| `SkeletonAnimeCard` | `(modifier)` | (available for Library/Search loading states) |

### `ui/settings/` (shared settings UI)
| Composable | Signature | Used by |
|---|---|---|
| `SettingsSubpageScaffold` | `(title, onBack, actions={}, content)` | Every settings subpage |
| `SettingsGroupCard` | `(title, content)` | Every settings subpage |
| `LeadingIcon` | `(icon: ImageVector)` | ClickableSettingsRow, SwitchSettingsRow |
| `ClickableSettingsRow` | `(icon, title, subtitle, onClick, trailing?)` | DetailsSettingsScreen, all settings hubs |
| `SwitchSettingsRow` | `(icon, title, subtitle, checked, onCheckedChange)` | DisplaySettingsScreen, MetadataSettingsScreen, DetailsSettingsScreen |
| `SelectableOptionCard` | `(title, subtitle?, options: List<Triple<value,label,desc?>>, selectedValue, onSelect)` | (available — defined for card-style selectors; currently LayoutSettings uses StyledSegmentedRow instead) |
| `StyledSegmentedRow` | `(options: List<Pair<label, selected>>, onSelect: (Int)->Unit)` | LayoutSettingsScreen |
| `EpisodeRowPreview` | `(showThumbnails, showSummaries, showTitles, showDates, showEpisodeNumber, showAudioPills, synopsisPosition, datePosition, thumbnailSize, titlePosition, episodeNumberPosition="overlay", thumbnailPosition="left", downloadButtonPlacement="episode_row", showDownloadButton=true)` | DetailsSettingsScreen, DisplaySettingsScreen, LayoutSettingsScreen, MetadataSettingsScreen |

### `ui/detail/` (Detail-specific but extractable)
| Composable | Visibility | Notes |
|---|---|---|
| `ThreeStagePullRefresh` | public | `(isRefreshing, onRefresh: (RefreshStage)->Unit, modifier, content)`. Could be reused on Library/History if 3-stage refresh is wanted; otherwise use M3 `PullToRefreshBox`. |
| `generateDynamicScheme` / `DynamicColorScheme.toM3ColorScheme` | public | Per-content theming. Could be applied to Library/History cards or a per-anime "now watching" hero. |
| `EpisodeRow` / `EpisodeRowSimple` / `EpisodeRowRich` | private | The episode-row pattern. Extract to a shared `ui/components/EpisodeRow.kt` if History/Player-list reuse is wanted. |
| `DownloadButtonTall` | private | 48dp tall state-colored download button. |
| `InfoCard` | private | `Surface(RoundedCornerShape(12.dp), surfaceContainerLow, padding 16)` with title+body. Useful as a shared "notice" card. |
| `AudioPills` | private | Adaptive SUB/DUB/HSUB pills with dot separators. |
| `formatDate(epochMillis)` | private file-fn | "MMM d, yyyy" — duplicated in PlayerScreen; should be extracted. |

### `player/controls/sheets/`
| Composable | Signature | Used by |
|---|---|---|
| `PlayerSheet` | `(title, onDismiss, content)` | Quality/Subtitle/Audio/Server/Speed/More sheets |
| `SheetOption` | `(title, subtitle?, selected, onClick)` | Sheets with selectable rows |

---

# Design Language Cheat Sheet (for Library/History/Search revamp)

## Tokens
```
Color:      Monet dynamic on API 31+; emerald seed fallback.
            Key roles actually used: background, surface, surfaceContainerLow,
              surfaceContainer, surfaceContainerHigh, surfaceVariant,
              primary/onPrimary, primaryContainer/onPrimaryContainer,
              secondaryContainer/onSecondaryContainer, outlineVariant,
              error/errorContainer.
Shapes:     2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 32 dp + CircleShape.
            Cards=12dp, TopBars=20dp, Hero=24dp, Pills=6dp, Inner-panels=8dp,
            Thumbnails=10dp, Accent-bars=2dp.
Typography: titleLarge (screen titles, Bold), titleMedium (row/card titles, Bold),
            titleSmall (ep titles, Bold), bodyMedium (descriptions, Medium),
            bodySmall (muted/meta, Normal), labelMedium (uppercase section
            headers, Bold, letterSpacing=1.sp), labelSmall (pills/badges, Medium).
            Default weights almost always overridden to Bold/SemiBold.
Elevation:  Cards/list-rows = tonal 1-2dp (no shadow).
            Floating top bars = tonal 3dp + shadow 6dp.
            Hero/banner = tonal 4dp + shadow 8dp.
            ModalBottomSheet = M3 default.
Springs:    AnikutaSprings.press   (MediumBouncy/High)    — press scale.
            AnikutaSprings.effects (NoBouncy/Medium)      — corner morph / color.
```

## Components to reuse directly
- `ExpressiveAnimeCard` pattern (from `HomeScreen.kt`) → Library grid card. **Extract to `ui/components/ExpressiveAnimeCard.kt`** so Library/Search share it.
- `FloatingTopBar` pattern (from `HomeScreen.kt` / `LibraryScreen.kt`) → History & Search top bars. **Extract to `ui/components/FloatingTopBar.kt`** with `title`, `onSortClick?`, `onSearchClick?`, `searchField?` slots.
- `SettingsGroupCard` (already shared) → use for any Library/History "filter group" or "sort options" surface.
- `SelectableOptionCard` + `StyledSegmentedRow` (already shared) → use for sort-mode pickers, display-mode toggles.
- `SkeletonBox` / `SkeletonAnimeCard` (already shared) → loading states for Library grid and Search grid.
- `PlayerSheet` + `SheetOption` (already shared) → bottom-sheet sort/filter pickers on Library/History.
- `AnikutaSprings.press` + `animateFloatAsState` scale (1→0.96) → **apply to Library/History/Search cards** (they currently lack press feedback).
- `generateDynamicScheme(coverColor).toM3ColorScheme()` → optional per-card theming on History "continue watching" hero.

## Layout recipes
```
Library (grid):
  PullToRefreshBox {
    LazyVerticalGrid(Fixed(2), spacedBy 12.dp, contentPadding 0/24/12/12) {
      item(span=maxLineSpan) { FloatingTopBar("Library", trailing=sortDropdown) }
      item(span=maxLineSpan) { Text("$n saved", labelMedium onSurfaceVariant) }
      items(anime) { ExpressiveAnimeCard(it, onClick) }   // 140×300dp
      // loading: SkeletonAnimeCard x4 ; empty: EmptyState ; error: ErrorState
    }
  }

History (list + hero):
  LazyColumn(spacedBy 12.dp) {
    item { FloatingTopBar("History", trailing=overflowMenu) }
    item { ContinueWatchingHero(episode) }   // Surface(RoundedCornerShape(24.dp), tonal 4 + shadow 8)
    item { HomeSection-style header ("Continue Watching" / "Recently Watched") }
    items(history) { HistoryRow(it) }        // Surface(RoundedCornerShape(12.dp), alt surfaceContainerLow/High)
  }                                          //   cover 100×150 clip 12.dp + text col + play/delete icon buttons

Search:
  Column {
    FloatingTopBar with embedded BasicTextField (search field replaces title Text)
    when (state) {
      Loading -> LazyVerticalGrid of SkeletonAnimeCard x4
      Empty   -> EmptyState("No results for '$query'")
      Error   -> ErrorState(message, onRetry)
      Success -> LazyVerticalGrid(Fixed(2)) of ExpressiveAnimeCard
    }
  }
```

## Interaction recipe (apply to all three screens)
```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, AnikutaSprings.press, "scale")
val corner by animateFloatAsState(if (isPressed) 20f else 16f, AnikutaSprings.effects, "corner")
Card(
    modifier = Modifier.scale(scale),
    shape = RoundedCornerShape(corner.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    interactionSource = interactionSource,
    onClick = { ... },
) { /* cover clipped to topStart/topEnd = corner.dp */ }
```

## Section header recipe (M3 Expressive accent bar)
```kotlin
Row(verticalAlignment = CenterVertically, padding horizontal=16.dp vertical=4.dp) {
    Surface(Modifier.width(4.dp).height(20.dp), RoundedCornerShape(2.dp), primary) {}
    Spacer(8.dp)
    Text(title, titleLarge Bold)             // or labelMedium Bold primary letterSpacing=1.sp for uppercase
}
```

## Top bar recipe (floating pill — already in Library, copy to History/Search)
```kotlin
Surface(
    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=12.dp, vertical=4.dp),
    shape = RoundedCornerShape(20.dp),
    color = surfaceContainerHigh, tonalElevation = 3.dp, shadowElevation = 6.dp,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=10.dp), SpaceBetween, CenterVertically) {
        Text(title, titleMedium Bold primary)
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(percent=50)).background(secondaryContainer).clickable {...}) {
            Icon(icon, onSecondaryContainer, size=18.dp)
        }
    }
}
```

## What to AVOID (not part of this design language)
- `TopAppBar` / `LargeTopAppBar` / `MediumTopAppBar` (M3) — the app uses floating pills instead.
- `SegmentedButton` (M3) — use `StyledSegmentedRow`.
- `RadioButton` — use `SelectableOptionCard` or `SheetOption`.
- `FilledTonalButton` / `OutlinedButton` — use `Surface(onClick)` or `TextButton` / `Button`.
- Flat list rows without alternating bg — every list uses `surfaceContainerLow`/`High` alternation.
- Tween-based press animations — always `AnikutaSprings.press`.
- `MaterialTheme.shapes.*` — the code uses explicit `RoundedCornerShape(N.dp)` values directly.
- Hard-coded non-theme colors (except black/white scrims on video/banner overlays, and the fixed yellow→orange→red preview gradient in `EpisodeRowPreview`).

## Quick gap-check vs current Library/History/Search
- Library `LibraryCard` (in `LibraryScreen.kt`) — needs to match `ExpressiveAnimeCard` (spring scale + corner morph + 140×300dp + cover 200dp + meta row + genre line). Library already has the FloatingTopBar ✓ and PullToRefreshBox ✓.
- History — already references "FloatingTopBar / HomeSection / Surface cards" in its docstring but the screen uses `AlertDialog` for delete confirmation (fine) and `CircularProgressIndicator` for loading (should switch to skeleton). Should adopt the alternating-bg row pattern from `EpisodeRow` and a `HeroSection`-style "continue watching" card at top.
- Search — docstring says it uses `AnikutaSprings.press + effects` already (so it may already match `ExpressiveAnimeCard`); needs verification. Should use the FloatingTopBar with an embedded `BasicTextField` (aniyomi `SearchToolbar` pattern) rather than a bare `OutlinedTextField`.

## Implementation priority for the revamp agent
1. **Extract `ExpressiveAnimeCard` to `ui/components/ExpressiveAnimeCard.kt`** (currently private in `HomeScreen.kt`) → share across Home, Library, Search.
2. **Extract `FloatingTopBar` to `ui/components/FloatingTopBar.kt`** with `title`, `trailing` slot → share across Home, Library, History, Search.
3. **Add `AnikutaSprings.press` + corner-morph to Library/History cards** if not already present.
4. **Standardize History rows on the `EpisodeRow` alternating-bg + nested-pill pattern** (cover 100×150 clip 12.dp + title/meta col + action icon buttons).
5. **Replace History's bare `CircularProgressIndicator` loading with `SkeletonAnimeCard`/skeleton rows.**
6. **Add a per-content-themed "Continue Watching" hero** on History using `generateDynamicScheme(coverColor)` for the signature Detail/Player depth.
7. **Consider adding `surfaceContainerLow/High/Container/outlineVariant` to the static `LightColors`/`DarkColors`** in `Theme.kt` so the design language holds on API < 31 (currently relies on Monet).
