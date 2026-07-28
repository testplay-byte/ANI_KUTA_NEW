# 06-ui / Screen catalog

> Every Voyager `Screen`, every Voyager `Tab`, and every legacy `Activity` in
> the Aniyomi UI — grouped by where the user finds them — with the
> `ScreenModel` (or `ViewModel` for the legacy Activities) that holds their
> state and a one-line description.

## How to read this doc

Two navigation patterns coexist (see
[`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md)):

- **Voyager `Screen`** — a full-page Compose screen pushed onto the
  `Navigator` back stack via `navigator.push(SomeScreen(...))`. State lives in
  a `ScreenModel` (typically `StateScreenModel<T>` or `ScreenModel`).
- **Voyager `Tab`** — a selectable tab. Top-level tabs are switched via the
  `TabNavigator` in `HomeScreen`; *sub-tabs* (the dual anime/manga panes
  inside Library / Updates / History / Browse / Downloads / Stats / Storage /
  Categories) are switched via a `HorizontalPager` inside a `TabbedScreen`.
- **Legacy `Activity`** — the reader, the player, the webview, the crash
  screen, the biometric unlock, the OAuth callback, and the deep-link
  routers. Launched via `startActivity(Intent(...))`. State lives in an
  `androidx.lifecycle.ViewModel` (or no state at all).

The Compose content of every legacy Activity (except `ReaderActivity` /
`PlayerActivity`'s core viewers, and `UnlockActivity`) is hosted via the
`setComposeContent { ... }` helper in
`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/view/ViewExtensions.kt`
(see [`compose-migration.md`](compose-migration.md)).

The "**File**" column gives the Voyager `Screen`/`Tab` class location (under
`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/`). The
`ScreenModel`/`ViewModel` column points at the state holder. The
"**Subsys doc**" column links to the relevant
[`03-subsystems/`](../03-subsystems/) deep-dive.

---

## App host & global activities

| Screen / Activity | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| App host | `MainActivity` (legacy `BaseActivity`) | `main/MainActivity.kt` | — | The single Voyager `Navigator` host; installs the splash screen, sets up edge-to-edge + system bars, handles intent actions (deep links, share), shows the app-state banners (incognito / downloaded-only / indexing), and gates app update / onboarding / restore-backup overlays. | [`05-key-flows/app-startup.md`](../05-key-flows/app-startup.md) |
| Home | `HomeScreen` (Voyager `Screen`) | `home/HomeScreen.kt` | — | The root screen. Hosts a `TabNavigator` and renders the `NavigationBar` (phone) or `NavigationRail` (tablet), with `materialFadeThrough` transitions between tabs. Exposes `search` / `openTab` / `showBottomNav` channels used by deep links and notification taps. | [`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) |
| Biometric unlock | `UnlockActivity` (legacy `BaseActivity`) | `security/UnlockActivity.kt` | — | Blank activity that fires a `BiometricPrompt`; on success calls `SecureActivityDelegate.unlock()` and finishes. | — |
| Crash | `CrashActivity` (legacy `BaseActivity`) | `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/crash/CrashActivity.kt` | — | The `GlobalExceptionHandler`'s landing page. Renders `CrashScreen` via `setComposeContent` with a "restart" / "copy stack trace" pair of actions. | [`01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md) |

---

## Library tab

Top-level tab = `MangaLibraryTab` or `AnimeLibraryTab` depending on the
user's `StartScreen` preference (default `ANIME`). The inactive one is
reachable from the "More" tab via the alt-tab mechanism (see `NavStyle`).

| Screen / Tab | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| Manga library | `MangaLibraryTab` (Voyager `Tab`) | `library/manga/MangaLibraryTab.kt` | `MangaLibraryScreenModel` (`library/manga/MangaLibraryScreenModel.kt`) | The "Library" tab for manga. Per-category `LazyColumn`/`LazyGrid` pager; supports selection mode, FAB "resume", drag-to-reorder categories, search-as-you-type from the AppBar, and the long-press bottom action menu. | [`03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Manga library settings sheet | (Composable, no Screen) | `presentation/library/manga/MangaLibrarySettingsDialog.kt` | `MangaLibrarySettingsScreenModel` (`library/manga/MangaLibrarySettingsScreenModel.kt`) | The bottom-sheet filter/sort/display dialog for the manga library. | — |
| Manga library item | (Composable) | `library/manga/MangaLibraryItem.kt` | — | The per-item model used by the pager. | — |
| Anime library | `AnimeLibraryTab` (Voyager `Tab`) | `library/anime/AnimeLibraryTab.kt` | `AnimeLibraryScreenModel` (`library/anime/AnimeLibraryScreenModel.kt`) | The "Library" tab for anime. Mirror of `MangaLibraryTab`. | [`03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Anime library settings sheet | (Composable) | `presentation/library/anime/AnimeLibrarySettingsDialog.kt` | `AnimeLibrarySettingsScreenModel` (`library/anime/AnimeLibrarySettingsScreenModel.kt`) | Filter/sort/display bottom sheet for the anime library. | — |
| Anime library item | (Composable) | `library/anime/AnimeLibraryItem.kt` | — | Per-item model used by the pager. | — |
| Library shared UI | (Composables) | `presentation/library/components/` (`CommonEntryItem.kt`, `LibraryToolbar.kt`, `LibraryBadges.kt`, `LazyLibraryGrid.kt`, `LibraryTabs.kt`, `GlobalSearchItem.kt`, `DeleteLibraryEntryDialog.kt`) | — | Shared library list/grid item shapes (`EntryCompactGridItem`, `EntryComfortableGridItem`, `EntryListItem`), the toolbar, the unread/downloaded badges, the category tabs. | [`components.md`](components.md) |

---

## Updates tab

Top-level tab = `UpdatesTab`. The tab body is a `TabbedScreen` with two
sub-tabs (anime / manga).

| Screen / Tab | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| Updates tab | `UpdatesTab` (Voyager `Tab`) | `updates/UpdatesTab.kt` | — | Wraps `animeUpdatesTab` + `mangaUpdatesTab` in a `TabbedScreen`. Reselect pushes `DownloadsTab`. | [`03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Anime updates sub-tab | `animeUpdatesTab()` builder | `updates/anime/AnimeUpdatesTab.kt` | `AnimeUpdatesScreenModel` (`updates/anime/AnimeUpdatesScreenModel.kt`) | List of new episodes for library anime, grouped by date. Tap → `AnimeScreen`; long-press → multi-select with mark-read/download/cover action menu. | [`03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Manga updates sub-tab | `mangaUpdatesTab()` builder | `updates/manga/MangaUpdatesTab.kt` | `MangaUpdatesScreenModel` (`updates/manga/MangaUpdatesScreenModel.kt`) | List of new chapters for library manga, grouped by date. Tap → `MangaScreen`. | [`03-subsystems/updates.md`](../03-subsystems/updates.md) |
| Updates shared UI | (Composables) | `presentation/updates/manga/MangaUpdatesScreen.kt`, `presentation/updates/anime/AnimeUpdatesScreen.kt`, `presentation/updates/UpdatesDialog.kt` | — | Row layouts (`MangaUpdatesUiItem`, `AnimeUpdatesUiItem`), the "mark all read / update library" confirm dialog. | — |

---

## History tab

Top-level tab = `HistoriesTab`. Body is a `TabbedScreen` with anime/manga
sub-tabs.

| Screen / Tab | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| History tab | `HistoriesTab` (Voyager `Tab`) | `history/HistoriesTab.kt` | — | Wraps `animeHistoryTab` + `mangaHistoryTab`. Reselect resumes the last-watched episode. | [`03-subsystems/history.md`](../03-subsystems/history.md) |
| Anime history sub-tab | `animeHistoryTab()` builder | `history/anime/AnimeHistoryTab.kt` | `AnimeHistoryScreenModel` (`history/anime/AnimeHistoryScreenModel.kt`) | Recently-watched anime with resume points; tap → `PlayerActivity` at the saved seek position. Searchable. | [`03-subsystems/history.md`](../03-subsystems/history.md) |
| Manga history sub-tab | `mangaHistoryTab()` builder | `history/manga/MangaHistoryTab.kt` | `MangaHistoryScreenModel` (`history/manga/MangaHistoryScreenModel.kt`) | Recently-read manga with resume pages; tap → `ReaderActivity` at the saved page. Searchable. | [`03-subsystems/history.md`](../03-subsystems/history.md) |
| History shared UI | (Composables) | `presentation/history/anime/AnimeHistoryScreen.kt`, `presentation/history/manga/MangaHistoryScreen.kt`, `presentation/history/HistoryDialogs.kt` | — | History row items, the "remove" / "remove all" dialogs. | — |

---

## Browse tab

Top-level tab = `BrowseTab`. Body is a scrollable `TabbedScreen` with **six**
sub-tabs: anime sources, manga sources, anime extensions, manga extensions,
migrate-anime-source, migrate-manga-source. The AppBar's search icon opens
`GlobalAnimeSearchScreen` / `GlobalMangaSearchScreen`.

| Screen / Tab | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| Browse tab | `BrowseTab` (Voyager `Tab`) | `browse/BrowseTab.kt` | `MangaExtensionsScreenModel`, `AnimeExtensionsScreenModel` (hoisted for the search bar) | The 6-sub-tab `TabbedScreen`. Reselect pushes `GlobalAnimeSearchScreen`. | [`03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Anime sources sub-tab | `animeSourcesTab()` builder | `browse/anime/source/AnimeSourcesTab.kt` | `AnimeSourcesScreenModel` (`browse/anime/source/AnimeSourcesScreenModel.kt`) | List of installed anime sources; tap → `BrowseAnimeSourceScreen`; long-press → drag-handle to reorder. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Manga sources sub-tab | `mangaSourcesTab()` builder | `browse/manga/source/MangaSourcesTab.kt` | `MangaSourcesScreenModel` (`browse/manga/source/MangaSourcesScreenModel.kt`) | List of installed manga sources; tap → `BrowseMangaSourceScreen`. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Source browse (anime) | `BrowseAnimeSourceScreen` (Voyager `Screen`) | `browse/anime/source/browse/BrowseAnimeSourceScreen.kt` | `BrowseAnimeSourceScreenModel` (`browse/anime/source/browse/BrowseAnimeSourceScreenModel.kt`) | Paged grid/list of anime from one source. Filter dialog (`SourceFilterAnimeDialog`), display-mode toggle, opens `AnimeScreen`. | [`03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Source browse (manga) | `BrowseMangaSourceScreen` (Voyager `Screen`) | `browse/manga/source/browse/BrowseMangaSourceScreen.kt` | `BrowseMangaSourceScreenModel` (`browse/manga/source/browse/BrowseMangaSourceScreenModel.kt`) | Paged grid/list of manga from one source. Filter dialog (`SourceFilterMangaDialog`). | [`03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Sources filter (anime) | `AnimeSourcesFilterScreen` (Voyager `Screen`) | `browse/anime/source/AnimeSourcesFilterScreen.kt` | `AnimeSourcesFilterScreenModel` | Checkbox list to enable/disable anime sources. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Sources filter (manga) | `MangaSourcesFilterScreen` (Voyager `Screen`) | `browse/manga/source/MangaSourcesFilterScreen.kt` | `MangaSourcesFilterScreenModel` | Checkbox list to enable/disable manga sources. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Global search (anime) | `GlobalAnimeSearchScreen` (Voyager `Screen`) | `browse/anime/source/globalsearch/GlobalAnimeSearchScreen.kt` | `GlobalAnimeSearchScreenModel`, `AnimeSearchScreenModel` | Cross-source search across all enabled anime sources, one card row per source. | [`03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Global search (manga) | `GlobalMangaSearchScreen` (Voyager `Screen`) | `browse/manga/source/globalsearch/GlobalMangaSearchScreen.kt` | `GlobalMangaSearchScreenModel`, `MangaSearchScreenModel` | Cross-source manga search. | [`03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Anime extensions sub-tab | `animeExtensionsTab()` builder | `browse/anime/extension/AnimeExtensionsTab.kt` | `AnimeExtensionsScreenModel` (`browse/anime/extension/AnimeExtensionsScreenModel.kt`) | List of available + installed anime extensions, with install/update/uninstall + search. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Manga extensions sub-tab | `mangaExtensionsTab()` builder | `browse/manga/extension/MangaExtensionsTab.kt` | `MangaExtensionsScreenModel` (`browse/manga/extension/MangaExtensionsScreenModel.kt`) | List of available + installed manga extensions. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Anime extension filter | `AnimeExtensionFilterScreen` (Voyager `Screen`) | `browse/anime/extension/AnimeExtensionFilterScreen.kt` | `AnimeExtensionFilterScreenModel` | Checkbox list to show/hide anime extension repos by language. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Manga extension filter | `MangaExtensionFilterScreen` (Voyager `Screen`) | `browse/manga/extension/MangaExtensionFilterScreen.kt` | `MangaExtensionFilterScreenModel` | Checkbox list for manga extension repos. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Anime extension details | `AnimeExtensionDetailsScreen` (Voyager `Screen`) | `browse/anime/extension/details/AnimeExtensionDetailsScreen.kt` | `AnimeExtensionDetailsScreenModel` | Per-extension page: version, package, permissions, uninstall, clear cookies, open source repo. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Manga extension details | `MangaExtensionDetailsScreen` (Voyager `Screen`) | `browse/manga/extension/details/MangaExtensionDetailsScreen.kt` | `MangaExtensionDetailsScreenModel` | Same, for manga extensions. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Anime source preferences | `AnimeSourcePreferencesScreen` (Voyager `Screen`) | `browse/anime/extension/details/AnimeSourcePreferencesScreen.kt` | — | Renders a `ConfigurableSource`'s `SourcePreferences` as Compose preference items. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Manga source preferences | `MangaSourcePreferencesScreen` (Voyager `Screen`) | `browse/manga/extension/details/MangaSourcePreferencesScreen.kt` | — | Same, for a manga source. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migrate anime source sub-tab | `migrateAnimeSourceTab()` builder | `browse/anime/migration/sources/MigrateAnimeSourceTab.kt` | `MigrateAnimeSourceScreenModel` | Per-source list of library anime to migrate to a different source. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migrate manga source sub-tab | `migrateMangaSourceTab()` builder | `browse/manga/migration/sources/MigrateMangaSourceTab.kt` | `MigrateMangaSourceScreenModel` | Per-source list of library manga to migrate. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migrate anime | `MigrateAnimeScreen` (Voyager `Screen`) | `browse/anime/migration/anime/MigrateAnimeScreen.kt` | `MigrateAnimeScreenModel` | "Migrate this anime to …" picker with a list of candidate sources. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migrate manga | `MigrateMangaScreen` (Voyager `Screen`) | `browse/manga/migration/manga/MigrateMangaScreen.kt` | `MigrateMangaScreenModel` | "Migrate this manga to …" picker. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migrate anime search | `MigrateAnimeSearchScreen` (Voyager `Screen`) | `browse/anime/migration/search/MigrateAnimeSearchScreen.kt` | `MigrateAnimeSearchScreenModel` | Search-for-replacement UI used by migrate. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migrate manga search | `MigrateMangaSearchScreen` (Voyager `Screen`) | `browse/manga/migration/search/MigrateMangaSearchScreen.kt` | `MigrateMangaSearchScreenModel` | Search-for-replacement UI used by migrate. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Source-search (manga migrate) | `MangaSourceSearchScreen` (Voyager `Screen`) | `browse/manga/migration/search/MangaSourceSearchScreen.kt` | `MangaMigrateSearchScreenDialogScreenModel` | Single-source search inside the migrate flow. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Source-search (anime migrate) | `AnimeSourceSearchScreen` (Voyager `Screen`) | `browse/anime/migration/search/AnimeSourceSearchScreen.kt` | `AnimeMigrateSearchScreenDialogScreenModel` | Single-source search inside the migrate flow. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migrate season select (anime) | `MigrateSeasonSelectScreen` (Voyager `Screen`) | `browse/anime/migration/anime/season/MigrateSeasonSelectScreen.kt` | `MigrateSeasonSelectScreenModel` | Anime-specific: pick which season of a multi-season show to migrate. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| Migration flags | `AnimeMigrationFlags`, `MangaMigrationFlags` | `browse/{anime,manga}/migration/*MigrationFlags.kt` | — | Bitmask of which fields to copy (chapters/episodes, categories, track, etc.). | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |

---

## More tab & its pushed screens

Top-level tab = `MoreTab`. Renders `MoreScreen` (the list of "Downloads",
"Categories", "Stats", "Storage", "Data and storage", "Settings", "Player
settings", "About", "Help" rows plus the downloaded-only / incognito
switches). Selecting a row pushes a Voyager `Tab` or `Screen`.

| Screen / Tab | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| More tab | `MoreTab` (Voyager `Tab`) | `more/MoreTab.kt` | `MoreScreenModel` (private, same file) | The `MoreScreen` list. `MoreScreenModel` exposes `downloadedOnly`, `incognitoMode`, and a combined `DownloadQueueState` for manga+anime. | — |
| Onboarding | `OnboardingScreen` (Voyager `Screen`) | `more/OnboardingScreen.kt` | — | First-run onboarding (`OnboardingScreen` composable in `presentation/more/onboarding/`). Steps: theme, storage permission, guides. Writes `shownOnboardingFlow`. | [`05-key-flows/app-startup.md`](../05-key-flows/app-startup.md) |
| New update | `NewUpdateScreen` (Voyager `Screen`) | `more/NewUpdateScreen.kt` | — | "New version available" card with changelog + "Update" / "Open in browser". Schedules `AppUpdateDownloadJob`. | [`03-subsystems/updater.md`](../03-subsystems/updater.md) |
| Downloads | `DownloadsTab` (Voyager `Tab`) | `download/DownloadsTab.kt` | `AnimeDownloadQueueScreenModel` (`download/anime/AnimeDownloadQueueScreenModel.kt`), `MangaDownloadQueueScreenModel` (`download/manga/MangaDownloadQueueScreenModel.kt`) | Two-pager (anime/manga) download queue with per-entry progress, pause/resume FAB, sort menu, cancel-all. | [`03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Anime download sub-tab | `animeDownloadTab()` builder | `download/anime/AnimeDownloadQueueTab.kt` | (uses `AnimeDownloadQueueScreenModel`) | The anime queue list (header + child rows via `AnimeDownloadAdapter` / `AnimeDownloadHolder`). | [`03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Manga download sub-tab | `mangaDownloadTab()` builder | `download/manga/MangaDownloadQueueTab.kt` | (uses `MangaDownloadQueueScreenModel`) | The manga queue list. | [`03-subsystems/download-manager.md`](../03-subsystems/download-manager.md) |
| Categories | `CategoriesTab` (Voyager `Tab`) | `category/CategoriesTab.kt` | `AnimeCategoryScreenModel` (`category/anime/AnimeCategoryScreenModel.kt`), `MangaCategoryScreenModel` (`category/manga/MangaCategoryScreenModel.kt`) | Two-pager (anime/manga) category editor: rename, reorder, add, delete. | [`03-subsystems/library-management.md`](../03-subsystems/library-management.md) |
| Stats | `StatsTab` (Voyager `Tab`) | `stats/StatsTab.kt` | `AnimeStatsScreenModel` (`stats/anime/AnimeStatsScreenModel.kt`), `MangaStatsScreenModel` (`stats/manga/MangaStatsScreenModel.kt`) | Two-pager (anime/manga) library statistics: total entries, started, completed, average score, tracked count, etc. | — |
| Storage | `StorageTab` (Voyager `Tab`) | `storage/StorageTab.kt` | `CommonStorageScreenModel` (`storage/CommonStorageScreenModel.kt`), `AnimeStorageScreenModel`, `MangaStorageScreenModel` | Two-pager per-entry disk-usage breakdown (cached chapters/episodes, downloads, cover cache) with delete actions. | [`03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md) |
| About | `AboutScreen` (Voyager `Screen`, `object`) | `presentation/more/settings/screen/about/AboutScreen.kt` | — | App version, links (GitHub, Discord, website), Licenses row. | — |
| OSS licenses | `OpenSourceLicensesScreen`, `OpenSourceLibraryLicenseScreen` (Voyager `Screen`s) | `presentation/more/settings/screen/about/OpenSource{Licenses,LibraryLicense}Screen.kt` | — | Lists every OSS dependency + per-library license text. | — |

---

## Settings (reached from "More → Settings")

`SettingsScreen` (`setting/SettingsScreen.kt`) is a Voyager `Screen` that
hosts a *nested* `Navigator` rooted at `SettingsMainScreen`. On tablet UI it
renders a two-pane `TwoPanelBox` with the main list on the start side and
the current sub-screen on the end side.

| Screen | Class | File | State holder | What it shows |
|---|---|---|---|---|
| Settings root | `SettingsScreen` (Voyager `Screen`) | `setting/SettingsScreen.kt` | — | Nested-Navigator host; `Destination` enum picks the start (`About` / `DataAndStorage` / `Tracking`). |
| Settings main | `SettingsMainScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsMainScreen.kt` | — | The list of sub-setting categories (General, Appearance, Library, Reader, Downloads, Tracking, Browse, Data & storage, Advanced). Search icon → `SettingsSearchScreen`. |
| Settings search | `SettingsSearchScreen` (Voyager `Screen`) | `presentation/more/settings/screen/SettingsSearchScreen.kt` | — | Global search across all setting preferences. |
| Appearance | `SettingsAppearanceScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsAppearanceScreen.kt` | `UiPreferences` | Theme mode, app theme, AMOLED, app language, tablet UI mode, start screen, nav style, date format, relative time. See [`theme-design.md`](theme-design.md). |
| App language | `AppLanguageScreen` (Voyager `Screen`) | `presentation/more/settings/screen/appearance/AppLanguageScreen.kt` | — | Per-app locale picker (Android 13+ per-app language API). |
| Library | `SettingsLibraryScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsLibraryScreen.kt` | — | Library update schedule, categories, update flags, chapter/episode swipe actions, etc. |
| Reader | `SettingsReaderScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsReaderScreen.kt` | — | Default reading mode, orientation, color filter, page transitions. |
| Downloads | `SettingsDownloadScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsDownloadScreen.kt` | — | Download concurrency, save location, auto-download, deletion. |
| Tracking | `SettingsTrackingScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsTrackingScreen.kt` | — | Login state + auto-update toggle per tracker. See [`03-subsystems/trackers.md`](../03-subsystems/trackers.md). |
| Browse | `SettingsBrowseScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsBrowseScreen.kt` | — | Sources / extensions repo lists, local-source config. |
| Manga extension repos | `MangaExtensionReposScreen` (Voyager `Screen`) | `presentation/more/settings/screen/browse/MangaExtensionReposScreen.kt` | `MangaExtensionReposScreenModel` | Add/remove manga extension repo URLs. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Anime extension repos | `AnimeExtensionReposScreen` (Voyager `Screen`) | `presentation/more/settings/screen/browse/AnimeExtensionReposScreen.kt` | `AnimeExtensionReposScreenModel` | Add/remove anime extension repo URLs. | [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) |
| Data & storage | `SettingsDataScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsDataScreen.kt` | — | Create / restore backup, clear DB cache, storage info. |
| Create backup | `CreateBackupScreen` (Voyager `Screen`) | `presentation/more/settings/screen/data/CreateBackupScreen.kt` | — | Backup-content picker + location picker. | [`03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Restore backup | `RestoreBackupScreen` (Voyager `Screen`) | `presentation/more/settings/screen/data/RestoreBackupScreen.kt` | — | Restore flow with per-entity checkboxes. | [`03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) |
| Storage info | `StorageInfo` (composable) | `presentation/more/settings/screen/data/StorageInfo.kt` | — | Disk usage card shown on `SettingsDataScreen`. |
| Security | `SettingsSecurityScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsSecurityScreen.kt` | — | App lock (biometric/PIN), secure screen, hide notification content, incognito on launch. |
| Advanced | `SettingsAdvancedScreen` (`object`, `SearchableSettings`) | `presentation/more/settings/screen/SettingsAdvancedScreen.kt` | — | Crash logs, verbose logging, disable hardware accel, battery optimizations, work manager, etc. |
| Clear manga DB | `ClearDatabaseScreen` (Voyager `Screen`) | `presentation/more/settings/screen/advanced/ClearDatabaseScreen.kt` | — | Preview + clear non-library manga metadata. |
| Clear anime DB | `ClearAnimeDatabaseScreen` (Voyager `Screen`) | `presentation/more/settings/screen/advanced/ClearAnimeDatabaseScreen.kt` | — | Preview + clear non-library anime metadata. |
| Debug info | `DebugInfoScreen` (Voyager `Screen`) | `presentation/more/settings/screen/debug/DebugInfoScreen.kt` | — | Dump of device / app state for bug reports. |
| Backup schema | `BackupSchemaScreen` (Voyager `Screen`) | `presentation/more/settings/screen/debug/BackupSchemaScreen.kt` | — | Browses the JSON schema of backup files. |
| Worker info | `WorkerInfoScreen` (Voyager `Screen`) | `presentation/more/settings/screen/debug/WorkerInfoScreen.kt` | — | Lists running WorkManager jobs. |

---

## Player settings (reached from "More → Player settings")

`PlayerSettingsScreen` (`setting/PlayerSettingsScreen.kt`) is a Voyager
`Screen` that hosts a nested `Navigator` rooted at `PlayerSettingsMainScreen`.
Tablet UI uses the same `TwoPanelBox` two-pane layout as `SettingsScreen`.

| Screen | Class | File | What it shows |
|---|---|---|---|
| Player settings root | `PlayerSettingsScreen` (Voyager `Screen`) | `setting/PlayerSettingsScreen.kt` | Nested-Navigator host. |
| Player settings main | `PlayerSettingsMainScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsMainScreen.kt` | List of player sub-categories. |
| Player | `PlayerSettingsPlayerScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt` | Seek duration, default speed, gesture prefs, PiP, etc. |
| Audio | `PlayerSettingsAudioScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsAudioScreen.kt` | Audio language preference, volume boost, audio prefs. |
| Subtitle | `PlayerSettingsSubtitleScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsSubtitleScreen.kt` | Subtitle language, font, color, outline, position. |
| Decoder | `PlayerSettingsDecoderScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsDecoderScreen.kt` | Hardware decoder selection, deinterlacing, mpv `--hwdec`. |
| Gestures | `PlayerSettingsGesturesScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt` | Double-tap seek, swipe seek, skip-intro length dialog. |
| Advanced | `PlayerSettingsAdvancedScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsAdvancedScreen.kt` | mpv.conf / input.conf editing, GPU rendering, debug logs. |
| Torrent | `PlayerSettingsTorrentScreen` (`object`) | `presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt` | Torrserver host, port, seeding prefs. See [`03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md). |
| Custom buttons | `PlayerSettingsCustomButtonScreen` (Voyager `Screen`) | `presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreen.kt` | `PlayerSettingsCustomButtonScreenModel` — edit per-button mpv commands; opens `CustomButtonScreen` / `CustomButtonListItem` / `CustomButtonDialogs`. |
| Editor (mpv conf) | `PlayerSettingsEditorScreen` (Voyager `Screen`) | `presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreen.kt` | `PlayerSettingsEditorScreenModel` — file picker for mpv.conf / input.conf; opens `CodeEditScreen`. |
| Code editor | `CodeEditScreen` (Voyager `Screen`) | `presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreen.kt` | `CodeEditScreenModel` — simple syntax-highlighted text editor (`SyntaxHighlight.kt`, `Highlight.kt`). |

The underlying preference bags live in `ui/player/settings/`
(`PlayerPreferences.kt`, `SubtitlePreferences.kt`, `AudioPreferences.kt`,
`DecoderPreferences.kt`, `GesturePreferences.kt`,
`AdvancedPlayerPreferences.kt`). See
[`03-subsystems/anime-player.md`](../03-subsystems/anime-player.md).

---

## Entry detail (manga / anime)

The entry detail page is the heart of the per-title UI. It hosts the cover,
info header, chapter/episode list, track info dialog, episode settings, and
the FAB to start reading/watching.

| Screen | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| Manga detail | `MangaScreen` (Voyager `Screen`) | `entries/manga/MangaScreen.kt` | `MangaScreenModel` (`entries/manga/MangaScreenModel.kt`) | The manga entry detail page. Renders `eu.kanade.presentation.entries.manga.MangaScreen`. Hosts the chapter list (`MangaChapterListItem`), `MangaInfoHeader`, `ChapterSettingsDialog`, `DuplicateMangaDialog`, `ScanlatorFilterDialog`, `MangaCoverDialog`, `MangaTrackInfoDialogHomeScreen`, migrate dialog. | [`05-key-flows/read-manga.md`](../05-key-flows/read-manga.md) |
| Manga cover ops | (screen model used by `MangaScreen`) | `entries/manga/MangaCoverScreenModel.kt` | `MangaCoverScreenModel` | Save / share / edit / delete the manga cover. | — |
| Anime detail | `AnimeScreen` (Voyager `Screen`) | `entries/anime/AnimeScreen.kt` | `AnimeScreenModel` (`entries/anime/AnimeScreenModel.kt`) | The anime entry detail page. Renders `eu.kanade.presentation.entries.anime.AnimeScreen`. Hosts the episode list (`AnimeEpisodeListItem`), season list (`AnimeSeasonListItem`), `AnimeInfoHeader`, `EpisodeSettingsDialog`, `SeasonSettingsDialog`, `EpisodeOptionsDialogScreen`, `DuplicateAnimeDialog`, `AnimeImagesDialog`, `AnimeTrackInfoDialogHomeScreen`, migrate dialog. | [`05-key-flows/watch-anime.md`](../05-key-flows/watch-anime.md) |
| Anime image ops | (screen model used by `AnimeScreen`) | `entries/anime/AnimeImageScreenModel.kt` | `AnimeImageScreenModel` | Save / share / edit / delete the anime cover *and* the background image. | — |
| Track info (manga) | `MangaTrackInfoDialogHomeScreen` (Voyager `Screen`, shown as AdaptiveSheet) | `entries/manga/track/MangaTrackInfoDialogHome.kt` (+ `MangaTrackItem.kt`, `MangaTrackInfoDialog.kt`) | — | Per-tracker status card + "search tracker for this entry" (`MangaTrackerSearch`). | [`03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| Track info (anime) | `AnimeTrackInfoDialogHomeScreen` (Voyager `Screen`, shown as AdaptiveSheet) | `entries/anime/track/AnimeTrackInfoDialogHome.kt` (+ `AnimeTrackItem.kt`, `AnimeTrackInfoDialog.kt`) | — | Same, for anime. | [`03-subsystems/trackers.md`](../03-subsystems/trackers.md) |

---

## Reader (legacy Activity — manga)

| Activity | Class | File | ViewModel | What it shows | Subsys doc |
|---|---|---|---|---|---|
| Manga reader | `ReaderActivity` (legacy `BaseActivity`) | `reader/ReaderActivity.kt` | `ReaderViewModel` (`reader/ReaderViewModel.kt`) | The full-screen manga reader. Uses ViewBinding (`ReaderActivityBinding`) — `viewerContainer` hosts the active `Viewer` (a `PagerViewer` or `WebtoonViewer`, both View-based), and `pageNumber` / `dialogRoot` are `ComposeView`s hosting the page indicator + the Compose menus/dialogs (`ReaderAppBars`, `ReaderContentOverlay`, `ReaderSettingsDialog`, `ReadingModeSelectDialog`, `OrientationSelectDialog`, `ReaderPageActionsDialog`, `DisplayRefreshHost`, `PageIndicatorText`). | [`03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) |

Supporting files: `reader/loader/` (`ChapterLoader`, `PageLoader` hierarchy —
`HttpPageLoader`, `DownloadPageLoader`, `DirectoryPageLoader`,
`ArchivePageLoader`, `EpubPageLoader`), `reader/model/` (`ReaderPage`,
ReaderChapter, ChapterTransition, InsertPage, ViewerChapters`),
`reader/viewer/` (`Viewer`, `PagerViewer`, `WebtoonViewer`, the per-mode
`Pager`/`PagerPageHolder`/`PagerTransitionHolder`/`PagerViewerAdapter`,
the webtoon `Webtoon*` family, `ViewerConfig`, `ViewerNavigation` + the
`navigation/` strategies, `ReaderPageImageView`, `ReaderProgressIndicator`,
`ReaderButton`, `GestureDetectorWithLongTap`, `MissingChapters`,
`ReaderTransitionView`), `reader/setting/` (`ReaderPreferences`,
`ReaderSettingsScreenModel`, `ReadingMode`, `ReaderOrientation`),
`reader/SaveImageNotifier.kt`, `reader/ReaderNavigationOverlayView.kt`.

---

## Player (legacy Activity — anime)

| Activity | Class | File | ViewModel | What it shows | Subsys doc |
|---|---|---|---|---|---|
| Anime player | `PlayerActivity` (legacy `BaseActivity`) | `player/PlayerActivity.kt` | `PlayerViewModel` (`player/PlayerViewModel.kt`) | The full-screen MPV-based anime player. Uses ViewBinding (`PlayerLayoutBinding`) — `binding.player` is the `AniyomiMPVView` (a `FrameLayout` wrapping libmpv); `binding.controls` is a `ComposeView` whose `.setContent { TachiyomiTheme { PlayerControls(...) } }` renders all the on-screen controls (`PlayerControls`, `TopLeftPlayerControls`, `TopRightPlayerControls`, `MiddlePlayerControls`, `BottomLeftPlayerControls`, `BottomRightPlayerControls`, `PlayerDialogs`, `GestureHandler`, `PlayerSheets`, `PlayerPanels`). | [`03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) |

Supporting files: `player/AniyomiMPVView.kt`, `player/PlayerObserver.kt`,
`player/PipActions.kt`, `player/PlayerEnums.kt`, `player/PlayerUtils.kt`,
`player/ExternalIntents.kt` (external player hand-off),
`player/loader/` (`EpisodeLoader`, `HosterLoader`),
`player/controls/components/` (the `SeekBar`, `ControlsButton`,
`VerticalSliders`, `ThumbnailPreview`, `BrightnessOverlay`,
`DoubleTapSeekTriangles`, `AutoPlaySwitch`, `PlayerUpdates`,
`CurrentChapter`, and the `sheets/` family: `ChaptersSheet`,
`PlaybackSpeedSheet`, `QualitySheet`, `AudioTracksSheet`,
`SubtitleTracksSheet`, `GenericTracksSheet`, `ScreenshotSheet`, `MoreSheet`;
plus the `dialogs/` family: `EpisodeListDialog`, `IntegerPickerDialog`,
`PlayerDialog`), `player/controls/components/panels/` (the
`SubtitleSettingsPanel`, `SubtitleSettingsColorsCard`,
`SubtitleSettingsTypographyCard`, `SubtitleSettingsMiscellaneousCard`,
`SubtitleDelayPanel`, `AudioDelayPanel`, `VideoFiltersPanel`),
`player/utils/` (`ChapterUtils`, `AniSkipApi`, `TrackSelect`),
`player/settings/` (the `*Preferences` bags listed under Player settings).

---

## Webview, deep link, and OAuth screens

| Screen / Activity | Class | File | State holder | What it shows | Subsys doc |
|---|---|---|---|---|---|
| In-app webview (Voyager) | `WebViewScreen` (Voyager `Screen`) | `webview/WebViewScreen.kt` | `WebViewScreenModel` (`webview/WebViewScreenModel.kt`) | Renders `WebViewScreenContent` (Compose toolbar + embedded Android `WebView`). Used by the entry detail "open in WebView" action and by source-browse captcha flows. | [`03-subsystems/source-system.md`](../03-subsystems/source-system.md) |
| In-app webview (legacy) | `WebViewActivity` (legacy `BaseActivity`) | `webview/WebViewActivity.kt` | — | The standalone-Activity version of the above. Same `WebViewScreenContent`, hosted via `setComposeContent`. Used when a webview must live outside the main Navigator (e.g. from the reader/player). | — |
| Deep-link router (anime) | `DeepLinkAnimeActivity` (legacy `Activity`) | `deeplink/anime/DeepLinkAnimeActivity.kt` | — | Trampoline that rewrites the intent with `INTENT_SEARCH_TYPE = ANIME` and forwards to `MainActivity`. | — |
| Deep-link router (manga) | `DeepLinkMangaActivity` (legacy `Activity`) | `deeplink/manga/DeepLinkMangaActivity.kt` | — | Same, with `DeepLinkScreenType.MANGA`. | — |
| Deep-link UI (anime) | `DeepLinkAnimeScreen` (Voyager `Screen`) | `deeplink/anime/DeepLinkAnimeScreen.kt` | `DeepLinkAnimeScreenModel` | The "did you mean …?" search-result list shown when a deep link needs to resolve a query to a single anime. | [`03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Deep-link UI (manga) | `DeepLinkMangaScreen` (Voyager `Screen`) | `deeplink/manga/DeepLinkMangaScreen.kt` | `DeepLinkMangaScreenModel` | Same, for manga. | [`03-subsystems/search-discovery.md`](../03-subsystems/search-discovery.md) |
| Deep-link type enum | `DeepLinkScreenType` | `deeplink/DeepLinkScreenType.kt` | — | `ANIME` / `MANGA` discriminator passed through `MainActivity.INTENT_SEARCH_TYPE`. | — |
| OAuth callback | `TrackLoginActivity` (legacy `BaseOAuthLoginActivity`) | `setting/track/TrackLoginActivity.kt` | — | OAuth redirect handler for AniList, Bangumi, MAL, Shikimori, Simkl. Parses the redirect `Uri` and calls the matching `trackerManager.<x>.login(...)`; on success returns to settings. | [`03-subsystems/trackers.md`](../03-subsystems/trackers.md) |
| OAuth base | `BaseOAuthLoginActivity` (legacy `BaseActivity`) | `setting/track/BaseOAuthLoginActivity.kt` | — | Holds the `TrackerManager` and the "return to settings" helper. | [`03-subsystems/trackers.md`](../03-subsystems/trackers.md) |

---

## Deep-link routing (recap)

A deep link (`aniyomi://…` or a share intent) lands in one of the
`DeepLinkAnimeActivity` / `DeepLinkMangaActivity` trampolines, which forward
to `MainActivity` with `INTENT_SEARCH_TYPE` set. `MainActivity.handleIntentAction`
inspects the intent and either:

1. Pushes `DeepLinkAnimeScreen` / `DeepLinkMangaScreen` (when a query needs
   disambiguating), which then pushes `AnimeScreen` / `MangaScreen` on tap,
   or
2. Pushes `AnimeScreen(mangaId)` / `MangaScreen(mangaId)` / `ReaderActivity`
   / `PlayerActivity` directly when the deep link carries an explicit ID,
   or
3. Calls `HomeScreen.openTab(Tab.AnimeLib/.../Browse)` to switch the active
   bottom-nav tab (e.g. for the "open extensions" notification action).

See [`05-key-flows/app-startup.md`](../05-key-flows/app-startup.md) for the
launch-time intent-handling flow and
[`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md)
for the general navigator model.

---

## See also

- [`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) — Voyager navigator, tab navigation.
- [`01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md) — `ScreenModel`s, `StateScreenModel`, `ViewModel`s.
- [`components.md`](components.md) — the reusable Compose components these screens are built from.
- [`theme-design.md`](theme-design.md) — the `TachiyomiTheme` that wraps every screen's `Content()`.
- [`compose-migration.md`](compose-migration.md) — why `ReaderActivity` and `PlayerActivity` are still Activities.
- [`03-subsystems/`](../03-subsystems/) — feature-level deep dives that span multiple screens.
- [`05-key-flows/`](../05-key-flows/) — end-to-end user journeys through these screens.
