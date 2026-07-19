# Dependency Injection

Injekt (`com.github.mihonapp:injekt:91edab2317`) — a Mihon fork of `uy.kohesive.injekt`. Four `InjektModule`s are imported in `App.onCreate`; bindings are registered with `addSingleton`/`addSingletonFactory`/`addFactory` and retrieved with `Injekt.get<T>()` or `by injectLazy<T>()`. No Hilt, no Koin, no JSR-330 annotations.

---

## Where it lives

All paths relative to `REFERENCE/`.

| Concern | Path |
|---------|------|
| App entry — imports the four modules + calls `patchInjekt()` | `app/src/main/java/eu/kanade/tachiyomi/App.kt` |
| Infrastructure module (DBs, caches, network, sources, extensions, downloads, trackers, storage) | `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` |
| Preference module (one factory per `*Preferences` façade) | `app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt` |
| Domain module (repository interface→impl bindings + interactor factories, anime + manga in parallel) | `app/src/main/java/eu/kanade/domain/DomainModule.kt` |
| Tachiyomi-SY extension module (one extra interactor) | `app/src/main/java/eu/kanade/domain/SYDomainModule.kt` |
| Injekt version coordinate | `gradle/libs.versions.toml` (line 42: `injekt = "com.github.mihonapp:injekt:91edab2317"`) |
| App-level dependency declaration | `app/build.gradle.kts` (line 260: `implementation(libs.injekt)`) |

The DI framework is **confirmed** as Injekt by three independent signals: (1) the version-catalog entry, (2) the gradle dependency line, (3) the imports in `App.kt` (`uy.kohesive.injekt.Injekt`, `uy.kohesive.injekt.api.InjektModule`, `uy.kohesive.injekt.api.get`, `uy.kohesive.injekt.injectLazy`) and the same `uy.kohesive.injekt.*` imports across `AppModule.kt`, `PreferenceModule.kt`, `DomainModule.kt`, `SYDomainModule.kt`, `BaseActivity.kt` delegates, `TachiyomiTheme.kt`, and every screen model. A grep across the app for `@Inject`, `@Module`, `@Component`, `@Provides`, `Koin`, `Hilt`, `@AndroidEntryPoint` returns no matches — the project uses **only** Injekt, with no JSR-330 or Koin/Hilt alongside.

---

## What it does

DI wires the entire object graph for the `:app` module at process start:

- Registers **infrastructure singletons** (`Database`, `AnimeDatabase`, `NetworkHelper`, `ChapterCache`, `MangaCoverCache`, `AnimeCoverCache`, `AnimeBackgroundCache`, source managers, extension managers, download managers + caches + providers, `TrackerManager`, `ImageSaver`, `StorageManager`, `Json`, `XML`, `ProtoBuf`, local-source file systems + cover managers, `ExternalIntents`, `Application` itself).
- Registers **all preference façades** (`PreferenceStore`, `NetworkPreferences`, `SourcePreferences`, `SecurityPreferences`, `LibraryPreferences`, `ReaderPreferences`, `PlayerPreferences`, `GesturePreferences`, `DecoderPreferences`, `SubtitlePreferences`, `AudioPreferences`, `AdvancedPlayerPreferences`, `TrackPreferences`, `DownloadPreferences`, `BackupPreferences`, `StoragePreferences`, `UiPreferences`, `BasePreferences`).
- Registers **domain-layer repository bindings** — each `*Repository` interface from `tachiyomi.domain.*` (or `mihon.domain.*`) is bound to its `*Impl` from `tachiyomi.data.*` / `mihon.data.*` as a singleton factory.
- Registers **domain-layer interactor factories** — every use-case/interactor class is registered with `addFactory { Interactor(get(), ...) }` (a fresh instance per `get()`).
- Eagerly initializes the expensive singletons (`NetworkHelper`, both source managers, both databases, both download managers) on the main executor *after* registration to keep cold-start fast — see the bottom of `AppModule.registerInjectables`.

Retrieval happens via `Injekt.get<T>()` (synchronous, blocks until the lazy factory runs) or `by injectLazy<T>()` (a `Lazy<T>` delegate that defers the `get()` to first access). Both are used pervasively: activities, screen models, the `App` class, the `ThemableDelegate`, the Coil `ImageLoader` factory, and even composables (`TachiyomiTheme` calls `Injekt.get<UiPreferences>()` at composition time).

---

## Key files & classes

### `App.kt` (`eu.kanade.tachiyomi.App`)

The `Application` subclass. `onCreate()` does (in order):

1. `super<Application>.onCreate()`
2. `patchInjekt()` — applies the Mihon fork's runtime patch (from `dev.mihon.injekt.patchInjekt`).
3. Sets up the global exception handler, Conscrypt TLS, WebView data-dir suffix.
4. **Imports the four modules:**
   ```kotlin
   Injekt.importModule(PreferenceModule(this))
   Injekt.importModule(AppModule(this))
   Injekt.importModule(DomainModule())
   Injekt.importModule(SYDomainModule())
   ```
5. Sets up notification channels, lifecycle observer, incognito-mode receiver, hardware-bitmap threshold, `AppCompatDelegate` theme mode, the two widget managers, logging, and the migrator.
6. Implements `SingletonImageLoader.Factory.newImageLoader(context)` — builds the Coil `ImageLoader`, pulling `NetworkHelper` from Injekt (`Injekt.get<NetworkHelper>().client`) for the OkHttp call factory.

`App` itself is **not** registered as an `InjektModule` host; it just *imports* modules. The four module classes do not extend `Application`.

### `AppModule.kt` (`eu.kanade.tachiyomi.di.AppModule`)

```kotlin
class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() { ... }
}
```

Registers ~40 infrastructure singletons. Notable patterns:

- `addSingleton(app)` — registers the `Application` instance itself.
- Two `AndroidSqliteDriver`s built locally (`tachiyomi.db` for manga, `tachiyomi.animedb` for anime), each with a `Callback` that sets `foreign_keys = ON`, `journal_mode = WAL`, `synchronous = NORMAL`. Factory switches between `FrameworkSQLiteOpenHelperFactory` (debug, API 30+, for the DB inspector) and `RequerySQLiteOpenHelperFactory`.
- `addSingletonFactory { Database(...) }` and `addSingletonFactory { AnimeDatabase(...) }` — SQLDelight database instances with column adapters (`DateColumnAdapter`, `StringListColumnAdapter`, `MangaUpdateStrategyColumnAdapter`, `AnimeUpdateStrategyColumnAdapter`, `FetchTypeColumnAdapter`).
- **Interface-typed bindings** for the two DB handlers:
  ```kotlin
  addSingletonFactory<MangaDatabaseHandler> { AndroidMangaDatabaseHandler(get(), sqlDriverManga) }
  addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get(), sqlDriverAnime) }
  ```
- `addSingletonFactory { Json { ignoreUnknownKeys = true; explicitNulls = false } }`, an `XML { ... }`, and a `ProtoBuf` singleton.
- Caches: `ChapterCache(app, get())`, `MangaCoverCache(app)`, `AnimeCoverCache(app)`, `AnimeBackgroundCache(app)`.
- Network: `NetworkHelper(app, get())`, `JavaScriptEngine(app)`.
- Source managers (interface-typed): `addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get(), get()) }`, `addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }`.
- Extension managers: `MangaExtensionManager(app)`, `AnimeExtensionManager(app)`.
- Download providers/managers/caches for both anime and manga (six factories in parallel).
- `TrackerManager(app)`, `DelayedAnimeTrackingStore(app)`, `DelayedMangaTrackingStore(app)`.
- `ImageSaver(app)`, `AndroidStorageFolderProvider(app)`, local-source file systems + cover managers for both anime and manga, `StorageManager(app, get())`, `ExternalIntents()`.
- At the bottom, an async eager-init block on `ContextCompat.getMainExecutor(app)` that calls `get<NetworkHelper>()`, both source managers, both databases, both download managers — to warm them up off the critical path.

### `PreferenceModule.kt` (`eu.kanade.tachiyomi.di.PreferenceModule`)

```kotlin
class PreferenceModule(val app: Application) : InjektModule
```

Registers the `PreferenceStore` (interface-typed → `AndroidPreferenceStore(app)`) as a singleton, then one `addSingletonFactory { XPreferences(get()) }` per preference façade. 17 preference classes total (listed in "What it does" above). `NetworkPreferences` additionally passes `verboseLogging = isDebugBuildType`; `StoragePreferences` takes both `folderProvider = get<AndroidStorageFolderProvider>()` and `preferenceStore = get()`; `BasePreferences` takes `app` and `get()`.

### `DomainModule.kt` (`eu.kanade.domain.DomainModule`)

```kotlin
class DomainModule : InjektModule {
    override fun InjektRegistrar.registerInjectables() { ... }
}
```

The biggest module (~200 bindings). Pattern:

- **Repository bindings** (interface → impl, singleton):
  ```kotlin
  addSingletonFactory<AnimeCategoryRepository> { AnimeCategoryRepositoryImpl(get()) }
  addSingletonFactory<MangaCategoryRepository> { MangaCategoryRepositoryImpl(get()) }
  addSingletonFactory<AnimeRepository> { AnimeRepositoryImpl(get()) }
  addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get()) }
  addSingletonFactory<EpisodeRepository> { EpisodeRepositoryImpl(get()) }
  addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
  addSingletonFactory<AnimeHistoryRepository> { AnimeHistoryRepositoryImpl(get()) }
  addSingletonFactory<MangaHistoryRepository> { MangaHistoryRepositoryImpl(get()) }
  addSingletonFactory<AnimeSourceRepository> { AnimeSourceRepositoryImpl(get(), get()) }
  addSingletonFactory<MangaSourceRepository> { MangaSourceRepositoryImpl(get(), get()) }
  addSingletonFactory<AnimeStubSourceRepository> { AnimeStubSourceRepositoryImpl(get()) }
  addSingletonFactory<MangaStubSourceRepository> { MangaStubSourceRepositoryImpl(get()) }
  addSingletonFactory<AnimeTrackRepository> { AnimeTrackRepositoryImpl(get()) }
  addSingletonFactory<MangaTrackRepository> { MangaTrackRepositoryImpl(get()) }
  addSingletonFactory<AnimeUpdatesRepository> { AnimeUpdatesRepositoryImpl(get()) }
  addSingletonFactory<MangaUpdatesRepository> { MangaUpdatesRepositoryImpl(get()) }
  addSingletonFactory<AnimeExtensionRepoRepository> { AnimeExtensionRepoRepositoryImpl(get()) }
  addSingletonFactory<MangaExtensionRepoRepository> { MangaExtensionRepoRepositoryImpl(get()) }
  addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
  addSingletonFactory<CustomButtonRepository> { CustomButtonRepositoryImpl(get()) }
  ```
- **Interactor factories** (not singleton — a new instance per `get()`):
  ```kotlin
  addFactory { GetAnimeCategories(get()) }
  addFactory { TrackEpisode(get(), get(), get(), get()) }
  addFactory { SyncEpisodesWithSource(get(), get(), get(), get(), get(), get(), get(), get()) }
  ...
  ```
  Anime and manga interactors are registered in parallel blocks (e.g. `GetAnimeCategories` + `GetMangaCategories`, `TrackEpisode` + `TrackChapter`, `SyncEpisodesWithSource` + `SyncChaptersWithSource`).
- `CustomButtonRepository` (player custom buttons) and its 6 interactors — anime/player-specific, no manga counterpart.
- `TrackSelect(get(), get())` — shared track-selection helper used by the player.
- `ExtensionRepoService(get(), get())` — single shared instance (factory) for both anime/manga extension-repo API.

### `SYDomainModule.kt` (`eu.kanade.domain.SYDomainModule`)

Tiny Tachiyomi-SY carry-over module: registers a single factory `addFactory { ToggleExcludeFromMangaDataSaver(get()) }`. Manga-only.

### Retrieval sites (representative, not exhaustive)

- `App.kt`: `private val basePreferences: BasePreferences by injectLazy()`, `private val networkPreferences: NetworkPreferences by injectLazy()`, plus `Injekt.get<UiPreferences>()`, `Injekt.get<PreferenceStore>()`, `Injekt.get<NetworkHelper>()`, `Injekt.get<MangaDownloadManager>()`, `Injekt.get<AnimeDownloadManager>()` etc.
- `BaseActivity` delegates: `SecureActivityDelegateImpl` uses `injectLazy<BasePreferences>()` + `injectLazy<SecurityPreferences>()`; `ThemableDelegateImpl` uses `Injekt.get<UiPreferences>()`.
- `MainActivity`: `private val libraryPreferences: LibraryPreferences by injectLazy()`, `private val preferences: BasePreferences by injectLazy()`, plus caches + interactors via `injectLazy()`.
- Screen models: `AnimeScreenModel` constructor — ~25 params each with `= Injekt.get()` default (see "How it works → Constructor injection").
- `TachiyomiTheme` composable: `Injekt.get<UiPreferences>()` at composition time.
- `App.newImageLoader`: `Injekt.get<NetworkHelper>().client`.

---

## How it works

### The DI framework — Injekt (confirmed)

Injekt is a Kotlin DI library originally from `uy.kohesive.injekt` (Kohesive); aniyomi uses the Mihon fork published as a snapshot at `com.github.mihonapp:injekt:91edab2317` (a JitPack coordinate). It is **not** JSR-330 and has no annotation processor — bindings are declared programmatically in `InjektModule` classes, and retrieval is by explicit `Injekt.get<T>()` calls.

At process start:

1. The `Application` (`App`) calls `patchInjekt()` (a small Mihon patch applied to the Injekt runtime — likely disabling its logging / enabling some optimization; the patch source lives in the `dev.mihon.injekt` package).
2. The four `InjektModule` instances are imported with `Injekt.importModule(module)`. Each module's `InjektRegistrar.registerInjectables()` block runs and registers its bindings into the global `Injekt` registry.
3. From then on, any `Injekt.get<T>()` anywhere in the `:app` process resolves `T` by looking up its registered factory and invoking it. `injectLazy<T>()` returns a `Lazy<T>` whose `get()` calls `Injekt.get<T>()` on first access.

Injekt has no concept of scopes, components, or subcomponents. Everything is registered globally. "Singleton" means the factory is invoked **once** and the result is cached; "factory" means the factory is invoked **every time** `get()` is called.

### Binding API

| Function | Behavior |
|----------|----------|
| `addSingleton(instance)` | Registers a pre-built instance. Used once in `AppModule` for the `Application` itself. |
| `addSingletonFactory { ... }` | Registers a **lazy singleton** factory. The lambda runs on first `get<T>()` and the result is cached. The inferred type parameter is the lambda's return type. |
| `addSingletonFactory<Interface> { Impl(get()) }` | Same as above but binds under the `Interface` type, so `Injekt.get<Interface>()` returns the `Impl` instance. This is how **interface → impl** bindings are expressed (Injekt has no separate `bind` API). |
| `addFactory { ... }` | Registers a **non-singleton** factory. A fresh instance is returned on every `get<T>()`. Used for interactors (which are lightweight, often stateful for the duration of one use-case, and may hold a `CoroutineScope`). |
| `get<T>()` (inside a factory lambda) | Resolves a dependency from the same registry. This is how factories compose — e.g. `AnimeCategoryRepositoryImpl(get())` resolves `AnimeDatabaseHandler` at construction time. |

### Constructor injection pattern

Injekt does not do auto-injected constructors. Instead, classes declare constructor parameters with `= Injekt.get()` defaults:

```kotlin
class AnimeScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val animeId: Long,
    private val isFromSource: Boolean,
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    // ... ~20 more = Injekt.get() defaults
) : StateScreenModel<AnimeScreenModel.State>(State.Loading)
```

The caller (`rememberScreenModel { AnimeScreenModel(context, lifecycle, animeId, fromSource) }` in the `Screen.Content()` composable) supplies only the contextual args; all DI-managed deps fall back to their `Injekt.get()` defaults. This makes the class fully testable (tests can pass mocks for every dep) and fully DI-managed in production (the caller doesn't know about the deps).

Activities use the `by injectLazy<T>()` delegate for fields instead, because their constructors are Android-controlled:

```kotlin
class MainActivity : BaseActivity() {
    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val preferences: BasePreferences by injectLazy()
    private val animeDownloadCache: AnimeDownloadCache by injectLazy()
    ...
}
```

### Singleton scoping

There are exactly two effective scopes:

1. **Process-wide singleton** — anything registered with `addSingleton` or `addSingletonFactory`. Lives for the entire `:app` process. Includes all repositories, all `*Preferences`, all caches, both databases, both source managers, both download managers, the `Application`, `Json`/`XML`/`ProtoBuf`, `NetworkHelper`, `TrackerManager`, etc.
2. **Transient / non-singleton** — anything registered with `addFactory`. A new instance per `get()`. Includes all interactors (`GetAnimeCategories`, `TrackEpisode`, `SyncEpisodesWithSource`, etc.) and `ExtensionRepoService`, `TrackSelect`.

There are no activity-scoped, fragment-scoped, or request-scoped bindings. Screen-model-scoped state lives in the `ScreenModel` instance (owned by Voyager's `rememberScreenModel`), not in the DI graph.

### How to add a new binding

**Adding a new infrastructure singleton:**

1. In `AppModule.registerInjectables()` add:
   ```kotlin
   addSingletonFactory { MyNewThing(app, get()) }
   ```
   or, if you want it bound under an interface:
   ```kotlin
   addSingletonFactory<MyNewThingInterface> { MyNewThingImpl(app, get()) }
   ```
2. If `MyNewThing` should be warmed up eagerly, add a `get<MyNewThing>()` call to the async-init block at the bottom of `AppModule`.

**Adding a new repository (interface → impl):**

1. Define the interface in `:domain` (e.g. `tachiyomi.domain.foo.repository.FooRepository`).
2. Implement it in `:data` (e.g. `tachiyomi.data.foo.FooRepositoryImpl(get())`), taking its `AnimeDatabaseHandler`/`MangaDatabaseHandler` via constructor param.
3. In `DomainModule.registerInjectables()` add:
   ```kotlin
   addSingletonFactory<FooRepository> { FooRepositoryImpl(get()) }
   ```

**Adding a new interactor:**

1. Define `class GetFooUseCase(private val repo: FooRepository) { suspend operator fun invoke(): Foo = repo.getFoo() }` in `:domain`.
2. In `DomainModule.registerInjectables()` add:
   ```kotlin
   addFactory { GetFooUseCase(get()) }
   ```
3. Inject into the consuming screen model with `private val getFoo: GetFooUseCase = Injekt.get()`.

**Adding a new preference façade:**

1. Define `class FooPreferences(private val preferenceStore: PreferenceStore)` in `:domain` (or `:core:common`).
2. In `PreferenceModule.registerInjectables()` add `addSingletonFactory { FooPreferences(get()) }`.

No annotation processing, no Hilt `@AndroidEntryPoint`, no Koin `by inject()` — every new binding is one explicit line in one of the four module classes.

---

## Dependencies

### The DI library

- `com.github.mihonapp:injekt:91edab2317` (JitPack) — declared in `gradle/libs.versions.toml` line 42, consumed by `app/build.gradle.kts` line 260 (`implementation(libs.injekt)`). The Mihon fork's `patchInjekt()` helper is brought in transitively (used in `App.kt` via `dev.mihon.injekt.patchInjekt`).
- JitPack repo must be enabled in `settings.gradle.kts` for this coordinate to resolve.

### What depends on DI

Essentially **everything in `:app`** that holds state or calls the domain/data layer:

- `App` (the `Application`) — imports the four modules.
- Every `BaseActivity` subclass (`MainActivity`, `PlayerActivity`, `ReaderActivity`, `CrashActivity`, `UnlockActivity`) — `injectLazy` for preferences/caches/interactors.
- Every Voyager `ScreenModel` — constructor injection of repositories + interactors + preferences.
- Some composables — `TachiyomiTheme` calls `Injekt.get<UiPreferences>()` at composition time (this is the only direct Injekt use in the Compose tree; composables are otherwise pure).
- Background services / workers (in `:app`'s `data/` package) — they reach into `Injekt` for `Database`, `NetworkHelper`, etc.
- The Coil `ImageLoader` factory (`App.newImageLoader`) — pulls `NetworkHelper` for the OkHttp client.

`:presentation-core`, `:presentation-widget`, `:data`, `:domain`, `:core:common`, `:source-api`, etc. **do not** depend on Injekt directly — they receive their dependencies via constructor parameters. Only `:app` wires the graph; the lower layers are DI-agnostic and could in principle be reused with a different DI framework.

---

## Anime vs manga

DI binds anime + manga side by side in **parallel** — there is no shared `EntryRepository` or shared `SourceManager`. Each domain concept has two parallel interfaces and two parallel impls, both registered:

| Concept | Anime binding | Manga binding |
|---------|---------------|---------------|
| Database | `AnimeDatabase` (schema from `tachiyomi.mi.data.AnimeDatabase`, db file `tachiyomi.animedb`) | `Database` (schema from `tachiyomi.data.Database`, db file `tachiyomi.db`) |
| DB handler | `AnimeDatabaseHandler` → `AndroidAnimeDatabaseHandler` | `MangaDatabaseHandler` → `AndroidMangaDatabaseHandler` |
| Source manager | `AnimeSourceManager` → `AndroidAnimeSourceManager` | `MangaSourceManager` → `AndroidMangaSourceManager` |
| Extension manager | `AnimeExtensionManager` (concrete) | `MangaExtensionManager` (concrete) |
| Download provider/manager/cache | `AnimeDownloadProvider`/`AnimeDownloadManager`/`AnimeDownloadCache` | `MangaDownloadProvider`/`MangaDownloadManager`/`MangaDownloadCache` |
| Cover / background caches | `AnimeCoverCache`, `AnimeBackgroundCache` | `MangaCoverCache` (no manga background cache) |
| Local-source file system / cover mgr | `LocalAnimeSourceFileSystem`, `LocalAnimeCoverManager`, `LocalAnimeBackgroundManager`, `LocalEpisodeThumbnailManager`, `LocalAnimeFetchTypeManager` | `LocalMangaSourceFileSystem`, `LocalMangaCoverManager` |
| Track delayed store | `DelayedAnimeTrackingStore` | `DelayedMangaTrackingStore` |
| Repositories (in `DomainModule`) | `AnimeRepository`, `EpisodeRepository`, `AnimeHistoryRepository`, `AnimeSourceRepository`, `AnimeStubSourceRepository`, `AnimeTrackRepository`, `AnimeUpdatesRepository`, `AnimeCategoryRepository`, `AnimeExtensionRepoRepository` | `MangaRepository`, `ChapterRepository`, `MangaHistoryRepository`, `MangaSourceRepository`, `MangaStubSourceRepository`, `MangaTrackRepository`, `MangaUpdatesRepository`, `MangaCategoryRepository`, `MangaExtensionRepoRepository` |
| Sync interactor | `SyncEpisodesWithSource` (8 deps) | `SyncChaptersWithSource` (9 deps) |
| Track interactor | `TrackEpisode`, `AddAnimeTracks`, `RefreshAnimeTracks`, `DeleteAnimeTrack`, `SyncEpisodeProgressWithTrack` | `TrackChapter`, `AddMangaTracks`, `RefreshMangaTracks`, `DeleteMangaTrack`, `SyncChapterProgressWithTrack` |

A few bindings are **shared** (not split):

- `TrackerManager(app)` — single instance; internally dispatches to per-tracker anime-vs-manga logic.
- `NetworkHelper(app, get())` — single OkHttp client for everything.
- `ChapterCache(app, get())` — manga chapter cache; anime has no parallel (episodes are downloaded files, not cached pages).
- `StorageManager(app, get())`, `AndroidStorageFolderProvider(app)`, `ImageSaver(app)`, `Json`, `XML`, `ProtoBuf` — shared.
- `CustomButtonRepository` + 6 custom-button interactors — anime/player-specific, no manga counterpart.
- `ExtensionRepoService(get(), get())` — shared HTTP service for both anime and manga extension-repo APIs.
- `ReleaseService` → `ReleaseServiceImpl` — app-update release checking, shared.

**Coupling:** the parallel structure means anime-first pruning is mechanical: delete the manga `addSingletonFactory`/`addFactory` lines from `AppModule` and `DomainModule`, delete the `SYDomainModule` import (it's manga-only), and the remaining graph stays consistent (no anime binding depends on a manga binding). The converse is also true — the manga side does not depend on anime bindings. The only place both sides meet is `MainActivity`, `App.kt` (widget managers, eager-init block), and a handful of shared singletons (`TrackerManager`, `NetworkHelper`).

---

## Relationships

- **DATA-LAYER** (TODO subsystem doc) — `DomainModule` is the central registry that binds `tachiyomi.domain.*.repository.*Repository` interfaces to `tachiyomi.data.*.*RepositoryImpl` / `mihon.data.*.*RepositoryImpl` implementations. Every repo binding line in `DomainModule` is a cross-reference to a DATA-LAYER interface+impl pair.
- **UI-THEME** — see `SUBSYSTEMS/UI-THEME.md`. `TachiyomiTheme` and `ThemableDelegate` are direct Injekt consumers (`Injekt.get<UiPreferences>()`). `BaseActivity`'s `SecureActivityDelegateImpl` uses `injectLazy<BasePreferences>()` + `injectLazy<SecurityPreferences>()`.
- **PLAYER** (TODO) — `PlayerActivity` extends `BaseActivity` and pulls `AudioPreferences`, `AdvancedPlayerPreferences`, `NetworkPreferences`, `StorageManager` via `Injekt.get()`. `PlayerPreferences` and friends are all registered in `PreferenceModule`. `CustomButtonRepository` + interactors serve the player's custom-button feature.
- **READER** (TODO) — `ReaderActivity` extends `BaseActivity`; `ReaderViewModel` uses constructor injection. `ReaderPreferences` registered in `PreferenceModule`; `ChapterRepository` + `Chapter`/`GetChapter`/`SetReadStatus`/`SyncChaptersWithSource` interactors registered in `DomainModule`.
- **DOWNLOAD** (TODO) — `AnimeDownloadManager`/`AnimeDownloadProvider`/`AnimeDownloadCache` and the manga trio are all in `AppModule`. `DownloadPreferences` in `PreferenceModule`. `DeleteEpisodeDownload`/`DeleteChapterDownload` + `FilterEpisodesForDownload`/`FilterChaptersForDownload` interactors in `DomainModule`.
- **TRACKER** (TODO) — `TrackerManager` (shared singleton) + `DelayedAnimeTrackingStore`/`DelayedMangaTrackingStore` in `AppModule`; `AnimeTrackRepository`/`MangaTrackRepository` + `TrackEpisode`/`TrackChapter` etc. in `DomainModule`; `TrackPreferences` in `PreferenceModule`.
- **SOURCE/EXTENSION** (TODO) — `AnimeSourceManager`/`MangaSourceManager` (interface-typed) + `AnimeExtensionManager`/`MangaExtensionManager` + `AnimeExtensionRepoRepository`/`MangaExtensionRepoRepository` + `ExtensionRepoService` (shared) + `SourcePreferences`.
- **PERSISTENCE / DATABASE** (TODO) — both `Database` and `AnimeDatabase` SQLDelight instances + their handlers are constructed in `AppModule`. This is the only module that touches SQLDelight directly.
- **PREFERENCES** — `PreferenceModule` owns the `PreferenceStore` → `AndroidPreferenceStore` binding and all 17 `*Preferences` façades.
- **MIGRATION** — `App.initializeMigrator()` reads `PreferenceStore` from Injekt (`Injekt.get<PreferenceStore>()`) to determine the last-version-code. Migration classes themselves (under `mihon.core.migration`) are not Injekt-managed; they're constructed ad-hoc by `Migrator`.
- **APP-STRUCTURE.md** — the `di/` package is listed there as part of `eu/kanade/tachiyomi/`.
- **ARCHITECTURE.md** — calls out Injekt as the DI framework and flags "Injekt vs. Hilt/Koin" as a future decision (Decision 4).

---

## Notes for our build (anime-first)

1. **Injekt is adequate but unusual.** It works, it's tiny, it has no annotation processor (faster builds), and it has served the Tachiyomi/Mihon/Aniyomi lineage for years. Its downsides: no compile-time graph validation (a missing binding is a runtime crash), no scopes (everything is global), no `@AssistedInject` / `@Binds` DSL sugar, and the Mihon fork is a JitPack snapshot (not on Maven Central) which complicates reproducible builds.
2. **Keep vs. switch — trade-offs (no decision here, just framing):**
   - **Keep Injekt.** Lowest effort. Existing module classes work as-is. We inherit the runtime-crash risk and the JitPack dependency. Best if our priority is "match upstream to make rebasing easy."
   - **Switch to Hilt.** Compile-time validation, scoped bindings (`@ActivityRetainedScoped`, `@ViewModelScoped`), first-class `@HiltViewModel` integration with Compose (`hiltViewModel()`), official Android docs. Cost: rewrite all four module classes as `@Module @InstallIn(SingletonComponent::class)` objects with `@Provides`/`@Binds`, add the Hilt Gradle plugin + ksp/kapt, and rewrite every `Injekt.get()`/`injectLazy` call site (hundreds). Loses upstream-merge-friendliness for the DI slice.
   - **Switch to Koin.** Mid-ground: programmatic modules like Injekt (no annotation processor), but cleaner DSL and active development. Cost: rewrite module classes in Koin's `module { single { ... } }` DSL, change call sites from `Injekt.get<T>()` to `koinInject<T>()` / `by inject()`. Smaller diff than Hilt, still a real diff. Koin also has no compile-time validation.
3. **Anime-first pruning is clean either way.** Because anime + manga bindings are strictly parallel in `AppModule` and `DomainModule` (no anime binding depends on a manga binding), removing the manga lines is a mechanical deletion. Whichever DI framework we keep, the anime-only graph is a subset.
4. **Screen-model constructor injection is the pattern to preserve.** `class FooScreenModel(..., deps = Injekt.get())` + `rememberScreenModel { FooScreenModel(context, id) }` is exactly the testable, DI-agnostic pattern our UI/logic separation rule wants. If we switch to Hilt, this becomes `@HiltViewModel class FooViewModel @Inject constructor(...) : ViewModel()` + `hiltViewModel()` in the composable — same shape, different framework.
5. **The async eager-init block** at the bottom of `AppModule` is a useful cold-start trick (warm `NetworkHelper`, source managers, databases, download managers off the critical path) that we should keep regardless of DI framework.
6. **No scopes → no per-screen DI.** If we later want per-screen-scoped objects (e.g. a session-scoped video pipeline), we'll have to manage them manually on the `ScreenModel` (Voyager) or via a custom scope — Injekt won't help here. Hilt would.

---

## TODOs / open questions

- TODO: read the source of `dev.mihon.injekt.patchInjekt()` to document exactly what the Mihon fork patches (likely logging/verbosity or a perf tweak). The fork source is in the `com.github.mihonapp:injekt` JitPack artifact, not in this repo.
- TODO: confirm the JitPack repository is declared in `settings.gradle.kts` `dependencyResolutionManagement.repositories` (needed for the `com.github.mihonapp:injekt` coordinate to resolve). Not read for this doc.
- TODO: enumerate every `Injekt.get` / `injectLazy` call site across `:app` to quantify the migration cost if we switch frameworks (a grep gives a count but not a per-class breakdown).
- TODO: decide whether the `CustomButtonRepository` (player custom buttons) is anime-only or shared with manga — it has no manga counterpart in `DomainModule` but the player itself is anime-only, so it's effectively anime-only.
- TODO: confirm whether `:data` / `:domain` truly have zero Injekt imports (a grep suggests yes; they only declare constructor params). If true, they're DI-agnostic and a framework switch only touches `:app`.
- TODO: record Decision 4 ("Injekt vs. Hilt/Koin") in `MEMORY/decisions/` — left open here, not made.
