# 05-key-flows / App launch → home screen

> Trace a cold start of Aniyomi from process creation to the user seeing the
> Library tab, threading through `Application.onCreate()`, the Injekt module
> graph, the dual SQLDelight databases, the Voyager navigator, the default
> tab's `ScreenModel`, and finally the first Compose recomposition.

## Overview

```
Process start (zygote forks)
   │
   ▼
App.onCreate()                              ← App.kt
   ├─ patchInjekt()                         ← rewire Injekt for Android
   ├─ Conscrypt TLS provider (pre-Q)        ← java.security.Security
   ├─ WebView data-dir suffix (per-process) ← avoid crash on P+
   ├─ Injekt.importModule(PreferenceModule)
   ├─ Injekt.importModule(AppModule)        ← builds BOTH SQLDelight drivers
   ├─ Injekt.importModule(DomainModule)
   ├─ Injekt.importModule(SYDomainModule)
   ├─ Notifications.createChannels(this)
   ├─ ProcessLifecycleOwner.addObserver(this)
   ├─ Incognito-mode notification observer
   ├─ setAppCompatDelegateThemeMode(...)
   ├─ MangaWidgetManager + AnimeWidgetManager .init()
   └─ Migrator.initialize(old, new, migrations)
        │  (deferred; runs lazily on first await)
        ▼
MainActivity.onCreate()                     ← MainActivity.kt
   ├─ installSplashScreen()
   ├─ Migrator.awaitAndRelease()            ← blocks until migrations done
   ├─ setComposeContent { … }
   │     └─ Navigator(screen = HomeScreen, …)
   │            └─ HomeScreen.Content()
   │                  └─ TabNavigator(tab = defaultTab)
   │                       └─ <defaultTab>.Content()
   │                            └─ AnimeLibraryTab / MangaLibraryTab
   │                                  ├─ rememberScreenModel { *LibraryScreenModel() }
   │                                  └─ collects state ← LibraryScreenModel.state
   │                                       └─ getLibraryFlow (SQLDelight) → Flow<List<LibraryManga>>
   └─ splashScreen.setKeepOnScreenCondition { !ready && elapsed <= MAX }
                                                 ▲
                                                 │ ready=true set by
                                                 │ MangaLibraryTab.Content()'s
                                                 │ LaunchedEffect(state.isLoading)
```

The whole startup is **synchronous on the main thread** for the Injekt module
import + DB driver construction; the rest is reactive Compose. The splash
screen stays up until the first tab's `ScreenModel` flips `state.isLoading`
to `false` (which sets `MainActivity.ready = true`).

## Step-by-step

### 1. Process start → `App.onCreate()`

`App` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt`) is the
declared `Application` subclass. It also implements
`DefaultLifecycleObserver` (so `onStart`/`onStop` callbacks forward to
`SecureActivityDelegate`) and `SingletonImageLoader.Factory` (so Coil 3 picks
up the app-wide image loader with all the manga/anime cover fetchers wired in
— see [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md)
for the Coil cover pipeline).

`onCreate()` does, in order:

1. **`patchInjekt()`** — Applies the Android-side patch to the Injekt DI
   container (see [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md)).
2. **`GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)`**
   — installs an uncaught-exception handler that routes crashes to
   `CrashActivity` for in-app reporting (see
   [`../01-architecture/06-error-handling.md`](../01-architecture/06-error-handling.md)).
3. **Conscrypt** — On Android < 10 (`SDK_INT < Q`), inserts the bundled
   Conscrypt provider at position 1 so OkHttp has TLS 1.3 support. No-op on
   Q+.
4. **WebView data-dir suffix** — On Android P+ in a multi-process app,
   WebView crashes if two processes share a data dir. If `processName !=
   packageName`, suffix the data dir.
5. **Injekt module imports**:
   ```kotlin
   Injekt.importModule(PreferenceModule(this))
   Injekt.importModule(AppModule(this))
   Injekt.importModule(DomainModule())
   Injekt.importModule(SYDomainModule())
   ```
   - `PreferenceModule` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt`)
     binds the `PreferenceStore` (SharedPreferences-backed).
   - `AppModule` (see step 2 below) constructs the dual SQLDelight drivers
     and ~30 singletons.
   - `DomainModule` / `SYDomainModule` (in `:app`'s `eu.kanade.domain`
     package) bind the pure-domain interactors and the
     `tachiyomi.domain.*` repository interfaces to their `:data`
     implementations.
6. **`setupNotificationChannels()`** — `Notifications.createChannels(this)`
   registers every `NotificationChannel` (new-chapters, downloads, backup,
   update, incognito, etc.) with the system. See
   [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md).
7. **`ProcessLifecycleOwner.get().lifecycle.addObserver(this)`** — registers
   `App` as a process-lifecycle observer so its `onStart`/`onStop` fire when
   the app foregrounds/backgrounds.
8. **Incognito-mode observer** — A `Flow` on
   `basePreferences.incognitoMode().changes()` that, when incognito turns on,
   registers a `BroadcastReceiver` (`DisableIncognitoReceiver`) and posts an
   ongoing notification whose tap turns it off.
9. **Hardware-bitmap threshold** — Defaults the preference to
   `GLUtil.DEVICE_TEXTURE_LIMIT` if unset, then keeps `ImageUtil` in sync
   with future changes.
10. **Theme mode** — `setAppCompatDelegateThemeMode(UiPreferences.themeMode())`
    applies the system Material `AppCompatDelegate` theme mode (Light/Dark/
    System) so legacy Views pick up the right theme before Compose renders.
11. **Widget managers** — `MangaWidgetManager` and `AnimeWidgetManager`
    (from `:presentation-widget`) are constructed and `.init()`-ed on the
    process scope so home-screen widgets start observing the library.
12. **Verbose logging** — Installs `AndroidLogcatLogger(VERBOSE)` if
    `networkPreferences.verboseLogging()` is on.
13. **`initializeMigrator()`** — Reads the persisted
    `last_version_code` preference, calls `Migrator.initialize(old, new,
    migrations)` with the migration list from
    `mihon.core.migration.migrations.migrations`. The migrations themselves
    are **lazy**: they only run when something awaits the migrator (see
    step 4). On completion, the new version code is persisted.

`App` overrides `getPackageName()` to spoof `WebViewUtil.spoofedPackageName`
when the call stack is from Chromium's `BuildInfo` — this anonymises the
`X-Requested-With` header WebView sends.

### 2. `AppModule` — building the dual databases

`AppModule` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`)
is an `InjektModule` whose `registerInjectables()` block constructs:

- **Two `AndroidSqliteDriver`s**, one per side:
  | Driver | Schema | File |
  |---|---|---|
  | `sqlDriverManga` | `Database.Schema` (`tachiyomi.data.Database`) | `tachiyomi.db` |
  | `sqlDriverAnime` | `AnimeDatabase.Schema` (`tachiyomi.mi.data.AnimeDatabase`) | `tachiyomi.animedb` |

  Each driver's `Callback.onOpen` sets three PRAGMAs: `foreign_keys = ON`,
  `journal_mode = WAL`, `synchronous = NORMAL`. The factory is
  `FrameworkSQLiteOpenHelperFactory()` in debug builds on Android R+ (so the
  Android Studio Database Inspector works) and
  `RequerySQLiteOpenHelperFactory()` otherwise (faster, fewer JNI hops).

- **`Database` and `AnimeDatabase`** SQLDelight instances, each constructed
  with its column-adapters (`DateColumnAdapter`, `StringListColumnAdapter`,
  `MangaUpdateStrategyColumnAdapter` / `AnimeUpdateStrategyColumnAdapter`,
  `FetchTypeColumnAdapter`).
- **Two handlers** — `AndroidMangaDatabaseHandler` /
  `AndroidAnimeDatabaseHandler` — that wrap the SQLDelight instances and are
  bound to the `MangaDatabaseHandler` / `AnimeDatabaseHandler` interfaces
  that the `:domain`/`:data` repositories use.
- **`Json`**, **`XML`**, **`ProtoBuf`** singletons (the latter for backups).
- **Caches**: `ChapterCache`, `MangaCoverCache`, `AnimeCoverCache`,
  `AnimeBackgroundCache`.
- **`NetworkHelper`** (OkHttp + cookies + DNS) and **`JavaScriptEngine`**
  (Rhino, used by sources).
- **Source managers**: `AndroidMangaSourceManager`,
  `AndroidAnimeSourceManager` — bound to the `MangaSourceManager` /
  `AnimeSourceManager` interfaces (see
  [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md)).
- **Extension managers**: `MangaExtensionManager`, `AnimeExtensionManager` —
  their `init {}` blocks immediately scan installed extensions and load
  their sources (this is what populates the source managers).
- **Download subsystems** (manga + anime, each: provider/manager/cache):
  `MangaDownloadProvider`, `MangaDownloadManager`, `MangaDownloadCache` and
  the anime twins.
- **`TrackerManager(app)`** — constructs all 11 tracker singletons (see
  [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md)).
- **`DelayedMangaTrackingStore` / `DelayedAnimeTrackingStore`** — the
  SharedPreferences-backed queues for offline track updates.
- **`ImageSaver`, `AndroidStorageFolderProvider`, `StorageManager`** (SAF),
  the local-source file systems and cover/background/thumbnail managers,
  `ExternalIntents`, `TorrentServerApi`, `TorrentServerUtils`.

The last block is the **async warm-up**:

```kotlin
ContextCompat.getMainExecutor(app).execute {
    get<NetworkHelper>()
    get<MangaSourceManager>()
    get<AnimeSourceManager>()
    get<Database>()
    get<AnimeDatabase>()
    get<MangaDownloadManager>()
    get<AnimeDownloadManager>()
}
```

Posting these `get<>()`s on the main executor forces their
`addSingletonFactory` lambdas to run *after* `onCreate` returns, instead of
chaining their construction inside `importModule`. This shaves the cold-start
latency by deferring the download caches (which wait up to 30 s for the
extension manager) off the critical path.

### 3. `MainActivity.onCreate()`

`MainActivity` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`)
extends `BaseActivity`. It is the **only** launcher activity in the manifest
(except for `CrashActivity`, deep-link activities, and the OAuth login
activity).

1. **`installSplashScreen()`** (only on first launch / `savedInstanceState ==
   null`) — AndroidX core splash screen. Its `setKeepOnScreenCondition`
   (set later) keeps the splash visible until `ready == true`.
2. **`Migrator.awaitAndRelease()`** — suspends until the migrations that
   `App.initializeMigrator()` queued have finished running. Returns `true`
   if a migration actually happened (used to gate the "what's new" dialog).
3. **`isTaskRoot` guard** — if the launcher created a redundant `MainActivity`
   (the well-known Android bug), `finish()` immediately.
4. **`setComposeContent { … }`** — sets the Activity's content view to a
   Compose tree. Inside:
   - Collects `downloadOnly`, `indexing` (manga+anime download cache init
     flags), and `incognito` (manga+anime) into Compose state — used to
     tint the status bar (orange for download-only, red for incognito, blue
     for indexing).
   - **`Navigator(screen = HomeScreen, disposeBehavior = …)`** — creates
     the root Voyager navigator with `HomeScreen` as the initial screen.
   - Inside the `Navigator`'s content lambda:
     - `LaunchedEffect(navigator)` saves a reference to `navigator` on the
       Activity, handles the launch intent (`handleIntentAction`), and
       resets incognito mode on relaunch.
     - `LaunchedEffect(navigator.lastItem)` × 2 — observe the current
       screen so the per-source incognito state can refresh.
     - `Scaffold` with `AppStateBanners` in the top bar and
       `DefaultNavigatorScreenTransition` in the content slot — that's
       what actually renders the current `Screen.Content()`.
     - `HandleOnNewIntent(context, navigator)` — re-routes new intents.
     - `CheckForUpdates()` — fires the app-update check (if
       `updaterEnabled`) and the extension-update check on first launch
       (see [`../03-subsystems/updater.md`](../03-subsystems/updater.md) and
       [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md)).
     - `ShowOnboarding()` — pushes `OnboardingScreen` if
       `preferences.shownOnboardingFlow()` is false.
5. **Splash exit** — `splashScreen.setKeepOnScreenCondition { elapsed <=
   SPLASH_MIN_DURATION || (!ready && elapsed <= SPLASH_MAX_DURATION) }`.
   `ready` is set to `true` by the default tab's `Content()` once its
   ScreenModel finishes loading (see step 6).
6. **Optional cache clear** — if
   `libraryPreferences.autoClearItemCache().get()`, clear the
   `ChapterCache` on launch.
7. **External-player result launcher** — registered for the "play in
   external player" flow (used by the anime player, see
   [`watch-anime.md`](watch-anime.md)).

### 4. `HomeScreen` → default tab

`HomeScreen` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`)
is a Voyager `Screen`. Its `Content()` sets up a `TabNavigator`:

```kotlin
private val defaultTab = uiPreferences.startScreen().get().tab
…
TabNavigator(tab = defaultTab, key = TAB_NAVIGATOR_KEY) { tabNavigator ->
    Scaffold(
        startBar = { if (isTabletUi()) NavigationRail { … } },
        bottomBar = { if (!isTabletUi()) NavigationBar { … } },
    ) { …
        AnimatedContent(targetState = tabNavigator.current, …) {
            tabNavigator.saveableState(key = "currentTab", it) {
                it.Content()                  // ← renders the active tab
            }
        }
    }
}
```

`UiPreferences.startScreen()` returns a `StartScreen` enum whose `.tab`
property maps to one of:

| `StartScreen` | Tab |
|---|---|
| `ANIME_LIBRARY` (default on a fresh Aniyomi install) | `AnimeLibraryTab` |
| `MANGA_LIBRARY` | `MangaLibraryTab` |
| `UPDATES` | `UpdatesTab` |
| `HISTORY` | `HistoriesTab` |
| `BROWSE` | `BrowseTab` |

> Note: Tachiyomi/Mihon default to the manga library; **Aniyomi defaults to
> the anime library** (`AnimeLibraryTab`), reflecting its anime-first
> positioning. The bottom-nav order is `Anime Library · Manga Library ·
> Updates · History · Browse · More` (the last collapses into "More" under
> certain `NavStyle`s).

`HomeScreen` also owns three `Channel`s — `librarySearchEvent`,
`openTabEvent`, `showBottomNavEvent` — that other screens use to ask the
home to switch tabs programmatically (e.g. the "More" tab pushing
`DownloadsTab` and then returning).

### 5. The default tab loads its `ScreenModel`

Taking `AnimeLibraryTab` as the example (the manga side is structurally
identical):

`AnimeLibraryTab.Content()` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryTab.kt`)
does:

```kotlin
val screenModel = rememberScreenModel { AnimeLibraryScreenModel() }
val state by screenModel.state.collectAsState()
…
when {
    state.isLoading -> LoadingScreen(…)
    state.isLibraryEmpty -> EmptyScreen(…)
    else -> AnimeLibraryContent(categories = state.categories, …) { state.getLibraryItemsByPage(it) }
}

LaunchedEffect(state.isLoading) {
    if (!state.isLoading) {
        (context as? MainActivity)?.ready = true    // ← releases the splash
    }
}
```

The `AnimeLibraryScreenModel` constructor (see
[`../03-subsystems/library-management.md`](../03-subsystems/library-management.md)
for the full pipeline diagram) wires up a 5-way `combine` over:

- `getLibraryAnime.subscribe()` — a SQLDelight `Flow<List<LibraryAnime>>`
  selecting `animes WHERE favorite = 1`, joined with the
  `animes_categories` and the `animeCategoryFilter`/sort flags.
- The library-item preferences (`downloadedOnly`, `localOnly`,
  `showContinueWatchingButton`, etc.).
- `getTracksPerAnime.subscribe()` — the per-anime tracker bindings (used
  for the tracker-mean sort).
- `libraryPreferences` change flows.
- `downloadCache.changes` — so the download-state badges refresh.

The combined pipeline applies filters, applies sort, groups by category,
applies the search query, and emits into `MutableStateFlow<State>`. The
first emission is what flips `isLoading` to `false` and lets the splash
screen dismiss.

### 6. First Compose render

Once `state.isLoading == false`:

1. `AnimeLibraryContent` (in `:app`'s `eu.kanade.presentation.library.anime`
   package) renders a paged category grid/list, querying
   `state.getLibraryItemsByPage(categoryIndex)` per page.
2. Each cell is a `LibraryItem` that loads its cover via Coil 3, hitting
   `AnimeCoverFetcher` → `AnimeCoverCache` (see
   [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md)
   for the cover pipeline).
3. The `LaunchedEffect(state.isLoading)` block runs, setting
   `MainActivity.ready = true`. The splash screen's
   `setKeepOnScreenCondition` lambda then returns `false`, and AndroidX
   removes the splash — revealing the rendered library.

### 7. Background side-loads

Once the UI is interactive, two more things fire (from
`MainActivity.CheckForUpdates()`):

- **App update check** — `AppUpdateChecker().checkForUpdate(context)` on a
  `LaunchedEffect(Unit)`, only if `updaterEnabled` (a build-config flag,
  off in local builds — see
  [`../03-subsystems/updater.md`](../03-subsystems/updater.md)). If a new
  version exists, pushes `NewUpdateScreen`.
- **Extension update check** — `AnimeExtensionApi().checkForUpdates(context)`
  and `MangaExtensionApi().checkForUpdates(context)`. Unlike library
  updates, extension updates run **once per app launch**, not on a periodic
  WorkManager job (see
  [`../03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md)).

The periodic **library update** WorkManager job (`*LibraryUpdateJob`) is
**not** triggered at startup; it is re-armed by `Migrator` migrations and
by `PreferenceRestorer` after a restore, both of which call
`*LibraryUpdateJob.setupTask(context)` with the restored interval. See
[`../03-subsystems/updates.md`](../03-subsystems/updates.md).

## Sequence diagram

```
USER taps icon
   │
   ▼
Zygote forks process ─► App.onCreate()
   │   ├─ Injekt: PreferenceModule, AppModule, DomainModule, SYDomainModule
   │   │      └─ AppModule builds 2× AndroidSqliteDriver → Database, AnimeDatabase
   │   ├─ Notifications.createChannels()
   │   └─ Migrator.initialize(old, new, migrations)   ← deferred
   ▼
MainActivity.onCreate()
   │   ├─ Migrator.awaitAndRelease()   ← blocks until migrations run
   │   └─ setComposeContent {
   │         Navigator(HomeScreen) {
   │            HomeScreen.Content()
   │              └─ TabNavigator(defaultTab = AnimeLibraryTab)
   │                    └─ AnimeLibraryTab.Content()
   │                          ├─ rememberScreenModel { AnimeLibraryScreenModel() }
   │                          │      └─ getLibraryAnime.subscribe()  ── SQLDelight Flow
   │                          │             └─ SELECT * FROM animes WHERE favorite=1
   │                          ├─ collectAsState(state)
   │                          └─ when (!state.isLoading) { MainActivity.ready = true }
   │       }
   │       splashScreen.setKeepOnScreenCondition { !ready && elapsed <= MAX }
   ▼
Compose renders AnimeLibraryContent → Coil loads covers
   │
   ▼
USER sees anime library
```

## See also

- [`01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md) — Injekt wiring (the modules imported in step 1).
- [`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) — Voyager navigator and tab navigation.
- [`03-subsystems/library-management.md`](../03-subsystems/library-management.md) — the Library ScreenModel pipeline that this flow hands off to.
- [`03-subsystems/notifications.md`](../03-subsystems/notifications.md) — `Notifications.createChannels`.
- [`03-subsystems/updater.md`](../03-subsystems/updater.md) and [`03-subsystems/extensions-update.md`](../03-subsystems/extensions-update.md) — the two startup-triggered update checks.
- [`02-modules/app.md`](../02-modules/app.md) — the `:app` module overview.
