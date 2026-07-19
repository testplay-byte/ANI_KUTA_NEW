# 03-subsystems / source-system — Sources & extensions

> The runtime machinery that turns external APKs into live, callable sources.
> This is the "loader + registry" half of the story; the **contract** half is
> documented in [`../02-modules/source-api.md`](../02-modules/source-api.md).
> Read that doc first — this one assumes you know what a `MangaSource`,
> `AnimeSource`, `HttpSource`, `AnimeHttpSource`, `CatalogueSource`, and
> `SourceFactory` are.

## Purpose

Aniyomi ships with **zero** online sources baked into the app. Every public
catalogue a user can browse (MangaDex, Nyaa SI, Crunchyroll scrapers, etc.)
lives in a *separate* APK that the user installs on demand. This subsystem:

1. Discovers those APKs on disk and on the network.
2. Loads each APK's classes at runtime via a `PathClassLoader`.
3. Validates the APK's signature against a user-trusted list.
4. Instantiates the source classes declared in the APK's manifest metadata.
5. Registers them, keyed by source `id`, in two in-memory source managers
   (one for manga, one for anime).
6. Mirrors the same information as **stub sources** in SQLDelight, so a
   favourite entry whose extension is currently uninstalled still knows the
   name and lang of its original source.
7. Drives the install / update / uninstall lifecycle, including a
   foreground service, a package-installer bridge, a Shizuku bridge, and a
   private-APK bridge.
8. Provides a `WebViewActivity` for in-app login and Cloudflare-bypass
   sessions per source.

Everything here is **dual**: there is a manga pipeline and an anime
pipeline, structurally identical. Where they diverge, this doc calls it
out.

## Two-layer architecture

```
   ┌──────────────────────────────────┐
   │ :source-api  (KMP contract)      │   MangaSource / AnimeSource
   │   HttpSource / AnimeHttpSource    │   CatalogueSource / AnimeCatalog.
   │   SourceFactory / AnimeSourceFact.│
   └──────────────────────────────────┘
                  ▲ implements
   ┌──────────────┴───────────────────────────────────────────┐
   │ :app — extension loader                                   │
   │                                                           │
   │  MangaExtensionManager  ──┐    ┌──  AnimeExtensionManager │
   │   + Loader + Installer     │    │     + Loader + Installer │
   │   + Api (repo JSON)        │    │     + Api (repo JSON)    │
   │                            ▼    ▼                         │
   │  AndroidMangaSourceManager      AndroidAnimeSourceManager │
   │   (id → MangaSource)             (id → AnimeSource)       │
   │   + LocalMangaSource             + LocalAnimeSource       │
   │   + StubSourceRepo (DB)          + StubSourceRepo (DB)    │
   └───────────────────────────────────────────────────────────┘
                  ▲ calls (fetchMangaList / etc.)
              ┌───┴──────────────────────────┐
              │ UI: Browse / Entry / Reader / │
              │     Player                    │
              └──────────────────────────────┘
```

The contract module only declares interfaces; nothing there knows about
APKs, package managers, or repositories. The `:app` module owns all the
runtime machinery in the `eu.kanade.tachiyomi.extension.*` and
`eu.kanade.tachiyomi.source.*` packages.

## Extension APKs — what they look like

An extension is an ordinary Android APK with extra metadata in its
`<application>` manifest block:

| Manifest meta-data key                | Meaning                                                      |
|---------------------------------------|--------------------------------------------------------------|
| `tachiyomi.extension` (uses-feature)  | Required feature flag — used to *recognise* the APK as an extension. |
| `tachiyomi.extension.class`           | Semicolon-separated FQCNs of `MangaSource`/`AnimeSource` subclasses or `SourceFactory`/`AnimeSourceFactory` subclasses. |
| `tachiyomi.extension.factory`         | Optional FQCN of the source factory (version-pinning key). |
| `tachiyomi.extension.nsfw`            | `0` (SFW) or `1` (NSFW). If `1` and the user has disabled NSFW sources, the extension is silently dropped. |

The APK's **`versionName`** must encode the source-API lib version it was
compiled against as `<libversion>.<patch>`. The loader accepts `libVersion`
in `[LIB_VERSION_MIN, LIB_VERSION_MAX]` — currently `1.4 .. 1.5`. Anything
outside is rejected as obsolete (see [`extensions-update.md`](extensions-update.md)).

The APK's **application label** must start with `Tachiyomi: ` (manga) or
`Aniyomi: ` (anime); the loader strips that prefix to produce the
user-facing extension name. Anime extensions additionally carry an
`isTorrent` flag in the repo JSON.

## ExtensionManager — discovery, loading, registration

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/`.

Two parallel managers (`MangaExtensionManager`, `AnimeExtensionManager`)
have the same shape — only the types differ.

```
class MangaExtensionManager(context, preferences, trustExtension) {
    init {
        initExtensions()                                // synchronous scan at construction
        MangaExtensionInstallReceiver(InstallationListener()).register(context)
    }

    val installedExtensionsFlow:  StateFlow<List<MangaExtension.Installed>>
    val availableExtensionsFlow:  StateFlow<List<MangaExtension.Available>>
    val untrustedExtensionsFlow:  StateFlow<List<MangaExtension.Untrusted>>
    val isInitialized:            StateFlow<Boolean>

    suspend fun findAvailableExtensions()                // hit repo index.min.json
    fun installExtension(ext: Available): Flow<InstallStep>
    fun updateExtension(ext: Installed): Flow<InstallStep>
    fun uninstallExtension(ext: MangaExtension)
    suspend fun trust(ext: MangaExtension.Untrusted)
}
```

The `MangaExtension` sealed class has three states — `Installed`,
`Available` (listed in the repo but not installed), `Untrusted` (installed
but signature not yet trusted). Internally the manager holds three
`MutableStateFlow<Map<String, T>>` keyed by package name; public flows
expose them as lists via `mapExtensions(scope)`.

### Construction-time scan

`initExtensions()` calls `MangaExtensionLoader.loadMangaExtensions(context)`:

1. `PackageManager.getInstalledPackages(...)` for every installed package
   on the device, filtered by the `tachiyomi.extension` uses-feature →
   "shared" extensions.
2. Lists `context.filesDir/exts/*.ext` → "private" extensions (more on
   these under install below).
3. When a package exists in both lists, picks the higher version code
   (`selectExtensionPackage`).
4. Loads each extension **concurrently** in a `runBlocking { awaitAll }`
   block. Returns `List<MangaLoadResult>` of `Success | Untrusted | Error`.

Successes go into `installedExtensionsMapFlow`; Untrusted go into
`untrustedExtensionsMapFlow`; Errors are dropped. Then `_isInitialized.value = true`.

### The loader

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionLoader.kt`
(and its anime twin `AnimeExtensionLoader.kt`).

The loader is an `internal object`. The core method
`loadMangaExtension(context, extensionInfo)`:

```
 1. Pull versionName from PackageInfo.
 2. Parse libVersion = versionName.substringBeforeLast('.').toDouble().
    Reject if null or outside [LIB_VERSION_MIN, LIB_VERSION_MAX].
 3. Get the APK's signing certificates and SHA-256 them.
 4. Ask TrustMangaExtension.isTrusted(pkgInfo, signatures):
       - Trusted → continue.
       - Not trusted → return MangaLoadResult.Untrusted(extension).
 5. Read tachiyomi.extension.nsfw metadata; skip if NSFW & disabled.
 6. Build a ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader).
       (Falls back to dalvik.system.PathClassLoader on LinkageError.)
    "Child-first" means: when resolving a class, the extension's own DEX
    is consulted BEFORE the app's classpath. This is what lets an
    extension ship its own Jsoup / OkHttp / etc. without clashing with
    the versions bundled in the app — they only need to be binary-
    compatible at the source-api boundary.
 7. For each class name in tachiyomi.extension.class metadata:
       Class.forName(name, false, classLoader).getDeclaredConstructor().newInstance()
       → if it is a MangaSource, register directly;
         if it is a SourceFactory, call createSources() and register each.
 8. Wrap the sources in MangaExtension.Installed(...).
```

`ChildFirstPathClassLoader` lives in `eu.kanade.tachiyomi.util.system` —
it overrides `findClass` to consult the extension's DEX first. Note that
Tachiyomi/Aniyomi use `PathClassLoader` (not `DexClassLoader`); the APKs
are real signed packages so `PathClassLoader` is sufficient.

### Trust

`TrustMangaExtension` (`eu.kanade.domain.extension.manga.interactor`)
reads the `trusted_extensions` app-state preference (a `Set<String>` of
`"pkgName:versionCode:signatureHash"` entries) and returns `true` if the
package's signature is in the set. When the user taps "Trust" on an
Untrusted extension, the manager calls `trustExtension.trust(...)`,
removes the extension from the untrusted flow, and re-runs the loader —
which this time succeeds and emits a Success.

## SourceManager — the live registry

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/source/{manga,anime}/Android*SourceManager.kt`.

`AndroidMangaSourceManager` and `AndroidAnimeSourceManager` are the
in-memory maps the rest of the app uses to look up sources by id. They
implement `MangaSourceManager` / `AnimeSourceManager` (interfaces declared
in `:domain`).

```
class AndroidMangaSourceManager(context, extensionManager, sourceRepository) {
    private val sourcesMapFlow = MutableStateFlow<ConcurrentHashMap<Long, MangaSource>>(...)
    private val stubSourcesMap = ConcurrentHashMap<Long, StubMangaSource>()

    init {
        // Rebuild the map from scratch whenever the extension list changes.
        scope.launch {
            extensionManager.installedExtensionsFlow.collectLatest { exts ->
                val map = ConcurrentHashMap<Long, MangaSource>(mapOf(
                    LocalMangaSource.ID to LocalMangaSource(...),
                ))
                exts.forEach { ext -> ext.sources.forEach {
                    map[it.id] = it
                    registerStubSource(StubMangaSource.from(it))
                }}
                sourcesMapFlow.value = map
                _isInitialized.value = true
            }
        }
        // Separately, mirror all stub-source rows from the DB into
        // stubSourcesMap so they're available even before extensions load.
        scope.launch { sourceRepository.subscribeAllManga().collectLatest { ... } }
    }

    fun get(sourceKey: Long): MangaSource?            // may return null
    fun getOrStub(sourceKey: Long): MangaSource       // never null — stub if missing
    fun getOnlineSources(): List<HttpSource>
    fun getCatalogueSources(): List<CatalogueSource>
    fun getStubSources(): List<StubMangaSource>        // stubs whose source isn't loaded
}
```

Key behaviours:

- **Local source is hard-coded.** `LocalMangaSource.ID` (= `0L`) and
  `LocalAnimeSource.ID` are registered by hand in the map's initial value,
  so they're always available regardless of installed extensions.
- **Stubs survive uninstalls.** When a user favourites a manga from an
  extension, then later uninstalls that extension, the manga row in the
  DB still references `source = <some-id>`. The manager returns a
  `StubMangaSource(id, name, lang)` for that id so the UI can display
  *something* and offer to reinstall. Stub rows persist in the
  `mangasources` / `animesources` SQLDelight tables — see
  [`../02-modules/data.md`](../02-modules/data.md).
- **`registerStubSource` keeps DB and disk in sync.** Whenever a real
  source loads, its name/lang are upserted into the stub table. If the
  name has changed (e.g. an extension was updated), the matching download
  folder is also renamed via `downloadManager.renameSource(old, new)` so
  existing downloads aren't orphaned.

## The source ID

Source IDs are **deterministic** (see `source-api.md` for the full
algorithm). The short version:

```
id = MD5("${name.lowercase()}/$lang/$versionId").takeLowest64Bits() and signBitCleared
```

This is what makes the DB ↔ in-memory registry mapping work: a manga row
in the DB references a source by id; the manager's
`sourcesMapFlow.value[id]` resolves that to the live source object. The id
is stable across reinstalls as long as the extension's name, language,
and `versionId` constant don't change. `LocalMangaSource` and
`LocalAnimeSource` deliberately use `id = 0L` so the app can recognise
them.

## Extension installation flow

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/util/*Installer*.kt`,
`*InstallService.kt`, `*InstallActivity.kt`, `*InstallReceiver.kt`.

### InstallStep

```
enum class InstallStep {
    Idle, Pending, Downloading, Installing, Installed, Error;
    fun isCompleted() = this in [Installed, Error, Idle]
}
```

`installExtension(available)` returns a `Flow<InstallStep>` that emits the
steps and completes when the install finishes (one way or the other).

### Three installers — selected at runtime by preference

`BasePreferences.extensionInstaller()` is an enum with four values:

| Preference value  | What happens                                                                                  |
|-------------------|-----------------------------------------------------------------------------------------------|
| `LEGACY`          | Launches `MangaExtensionInstallActivity`, which fires the system `ACTION_INSTALL_PACKAGE` intent and waits for `onActivityResult`. Includes the MIUI-package-installer-bug workaround (`hasMiuiPackageInstaller` + 1-second ignore window). |
| `PRIVATE`         | Copies the downloaded APK into `context.filesDir/exts/<pkgName>.ext` (read-only on Android 14+) and uses `MangaExtensionLoader.installPrivateExtensionFile` to "install" it privately. No system permission prompt. The extension is only visible to this app. |
| `PACKAGEINSTALLER`| Uses Android's `PackageInstaller.Session` API inside `MangaExtensionInstallService` (a foreground service). Streams the APK into a session, commits, and listens on a broadcast receiver for `STATUS_PENDING_USER_ACTION` / `STATUS_SUCCESS` / `STATUS_FAILURE`. Can install silently on Android S+ with `USER_ACTION_NOT_REQUIRED`. |
| `SHIZUKU`         | Uses Shizuku to run `pm install-create / pm install-write / pm install-commit` as a privileged shell user. Requires the Shizuku app to be running and the user to have granted permission. No user prompt. Useful for batch updates. |

### The full sequence

```
UI tap "Install"
   │
   ▼
MangaExtensionManager.installExtension(available)
   │  returns Flow<InstallStep>
   ▼
MangaExtensionInstaller.downloadAndInstall(url, extension)
   ├── enqueues an Android DownloadManager.Request
   │   (downloads to externalFilesDir/Downloads/<apk-name>)
   ├── registers a DownloadCompletionReceiver for ACTION_DOWNLOAD_COMPLETE
   └── polls DownloadManager status every 1s and emits
       InstallStep.Pending / Downloading
              │
              ▼  (DownloadManager fires ACTION_DOWNLOAD_COMPLETE)
       onReceive: getUriForDownloadedFile(id)
              │
              ▼
       installApk(downloadId, uri)
         ├── LEGACY   → startActivity(MangaExtensionInstallActivity)
         ├── PRIVATE  → MangaExtensionLoader.installPrivateExtensionFile
         │               → copyAndSetReadOnlyTo(/filesDir/exts/<pkg>.ext)
         │               → MangaExtensionInstallReceiver.notifyAdded
         └── PACKAGEINSTALLER / SHIZUKU
               → startForegroundService(MangaExtensionInstallService)
                 → installer.addToQueue(id, uri) → processEntry(entry)
                   → PackageInstaller session commit / pm install-commit
                 → continueQueue(Installed | Error)
              │
              ▼
       MangaExtensionInstallReceiver (BroadcastReceiver) gets
       Intent.ACTION_PACKAGE_ADDED / REPLACED / REMOVED for the new pkg
         ├── ADDED    → onExtensionInstalled(load(pkg))
         ├── REPLACED → onExtensionUpdated(load(pkg))
         └── REMOVED  → onPackageUninstalled(pkg)
              │
              ▼
       MangaExtensionManager updates its StateFlows
              │
              ▼
       AndroidMangaSourceManager picks up the change via
       installedExtensionsFlow.collectLatest → registers sources by id
              │
              ▼
       UI re-renders with the new sources available
```

The receiver listens for *both* the system `ACTION_PACKAGE_*` intents
(so normal system installs are caught) and the app's own private
`ACTION_EXTENSION_*` broadcasts (so private installs that never go
through the system package manager are also caught). `isReplacing(intent)`
suppresses the duplicate ADDED+REMOVED pair that fires during a package
replace.

### Trust-on-first-install

If the APK's signature is not in the trusted set, `loadMangaExtension`
returns `Untrusted` and the manager moves the extension from
`installedExtensionsFlow` to `untrustedExtensionsFlow`. The UI shows a
"Trust extension?" dialog. On confirm, `trust(extension)` writes the
signature to the trusted-extensions preference and re-runs the loader.

### Uninstall

`uninstallExtension(extension)` either starts the system
`ACTION_UNINSTALL_PACKAGE` intent (for shared extensions) or directly
deletes the `.ext` file in private storage (for private extensions) and
broadcasts `ACTION_EXTENSION_REMOVED`. The `InstallationListener` then
removes the extension from the manager's flows, and the source manager
removes its sources from the live map. The manga rows in the DB keep
their `source` id — they will now resolve to a `StubMangaSource`.

## Extension updates

The full update-check / batch-update story is in
[`extensions-update.md`](extensions-update.md). The pieces that live
inside the source system:

- `MangaExtensionApi.checkForUpdates(context, fromAvailableExtensionList)`
  hits every repo's `index.min.json`, compares each `Available` against
  every installed `Installed` (by `pkgName`), and returns the list of
  installed extensions that have a newer `versionCode` or `libVersion`.
  Notifies via `ExtensionUpdateNotifier.promptUpdates(names)`.
- `MangaExtensionManager.updateExtension(installed)` resolves the
  `Available` twin and calls `installExtension(available)` — an update is
  just an install that triggers `ACTION_PACKAGE_REPLACED` instead of
  `ACTION_PACKAGE_ADDED`.
- The `hasUpdate` flag on each `MangaExtension.Installed` is recomputed
  whenever `findAvailableExtensions()` returns; it powers the badge
  counters in the Extensions screen.
- **Obsolete extensions.** If an installed extension's `pkgName` is *not*
  present in any repo's index (the extension was delisted or the repo
  removed), the manager flips `isObsolete = true`. The UI shows an
  "Obsolete" badge and the user is encouraged to uninstall.

## How a source is invoked from the UI

The UI never holds a direct reference to a source — it always goes
through the manager. A typical browse flow:

```
BrowseScreen (Voyager Screen)
   │
   ▼
BrowseScreenModel (StateScreenModel)
   │   sourceManager.getOrStub(sourceId) as? CatalogueSource
   ▼
RemoteMangaSource (PagingSource wrapper in :data)
   │
   ▼
source.fetchMangaList(page, query, filters)  : Observable<MangasPage>
   │  (HttpSource implements fetch* by delegating to *Request/*Parse)
   ▼
HttpSource.searchMangaRequest(page, query, filters)  : Request
HttpSource.searchMangaParse(page, query, filters)    : MangasPage
   │
   ▼
OkHttp → response.body → Jsoup.parse → SManga list → MangasPage
```

For the anime side, replace `Manga` with `Anime`, `CatalogueSource` with
`AnimeCatalogueSource`, `fetchMangaList` with `fetchEpisodeList`, etc.
The full call chain is in
[`../05-key-flows/browse-catalog.md`](../05-key-flows/browse-catalog.md)
and the source-side contract in
[`../02-modules/source-api.md`](../02-modules/source-api.md).

## Per-source preferences

A source that needs configuration (login token, quality setting, server
mirror) implements `ConfigurableSource` (manga) or
`ConfigurableAnimeSource` (anime). The app gives each source its own
`SharedPreferences`, keyed by `source_<id>`:

```kotlin
// source-api/src/commonMain/.../animesource/utils/Preferences.kt
fun preferencesKey(id: Long) = "source_$id"
fun AnimeSource.preferencesKey(): String = preferencesKey(id)
```

When the user opens "Source settings" from the browse screen, the app
calls `source.setupPreferenceScreen(screen)` and the source adds its own
`SwitchPreference`, `EditTextPreference`, `ListPreference`, etc. to the
provided `PreferenceScreen` (a thin KMP-compatible wrapper around the
AndroidX preference types — see `source-api.md`).

Per-source preferences are **not** versioned or migrated — when a source
is uninstalled, the preferences file is orphaned. Reinstalling the same
source picks the file back up because the id is deterministic. See
[`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
and [`../04-data-models/preferences-catalog.md`](../04-data-models/preferences-catalog.md).

## The stub-source registry in the DB

The two SQLDelight databases each have a tiny table that mirrors the
currently-known sources:

| Manga (`sqldelight/`)   | Anime (`sqldelightanime/`)   | Columns                            |
|-------------------------|------------------------------|------------------------------------|
| `mangasources.sq`       | `animesources.sq`            | `id INTEGER PK`, `lang TEXT`, `name TEXT` |

Written by `AndroidMangaSourceManager.registerStubSource` /
`AndroidAnimeSourceManager.registerStubSource` (upsert on every extension
load) and read by `MangaStubSourceRepository` /
`AnimeStubSourceRepository` (in `:domain`). They back `getOrStub()` so UI
rendering of a favourite doesn't require the extension to be loaded yet
— and survives the extension being uninstalled entirely. See
[`../02-modules/data.md`](../02-modules/data.md) for the schema and
[`../02-modules/domain.md`](../02-modules/domain.md) for the repo
interfaces.

## WebView integration

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/webview/`.

Some sources need a real WebView to:

1. **Bypass Cloudflare** — the Cloudflare challenge runs JS in a browser
   to compute a `cf_clearance` cookie. There is no way to do this without
   a real WebView.
2. **Log in** — the user types credentials into the source's real
   website; cookies set during the session are reused by OkHttp
   (`AndroidCookieJar` is shared between the WebView and OkHttp).
3. **Solve captchas** — same idea.
4. **Browse the source site** as a fallback when the source's parsed
   layout is broken.

### The activity

`WebViewActivity.newIntent(context, url, sourceId, title, isAnime)` is
the entry point. It:

- Looks up the source by id (`sourceManager.get(sourceId) as? HttpSource`
  for manga, the equivalent for anime).
- Pulls the source's custom OkHttp `headers` and passes them as the
  WebView's `additionalHttpHeaders` so the initial request looks
  identical to the one OkHttp would have made.
- Renders `WebViewScreenContent` (a Compose wrapper around the
  Accompanist WebView). That wrapper:
  - Detects Cloudflare challenge pages by sniffing for
    `window._cf_chl_opt` or `"Ray ID is"` in the page HTML.
  - Shows a help dialog linking to
    `https://aniyomi.org/docs/guides/troubleshooting/#cloudflare`.
  - Intercepts internal sub-resource requests and routes them through
    `network.nonCloudflareClient` so the bypassed Cloudflare cookies are
    reused but the WebView itself doesn't get stuck in a recursive
    challenge loop.
- Provides menu actions: **Share URL**, **Open in browser**, **Clear
  cookies** (calls `network.cookieJar.remove(url)` for the current host).

### CloudflareInterceptor (OkHttp side)

Source: `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`.

This is the *automatic* Cloudflare bypass — it runs on every OkHttp
request, not just user-initiated ones:

```
OkHttp → chain.proceed(request) → response
   │
   ▼
CloudflareInterceptor.shouldIntercept(response)
   = (response.code in [403, 503]) && (response.header("Server") in
       ["cloudflare-nginx", "cloudflare"])
   │  true
   ▼
intercept():
   - remove any old cf_clearance cookie for this URL
   - resolveWithWebView(request, oldCookie):
       * create a fresh WebView on the main thread
       * load the request URL with the source's headers
       * WebViewClient.onPageFinished checks whether a new cf_clearance
         cookie has been set → if yes, countDown the latch
       * wait up to 30 seconds
       * destroy the WebView
       * if no cf_clearance was obtained → throw CloudflareBypassException
         (rewrapped as an IOException for OkHttp)
   - re-issue chain.proceed(request); the cookie jar now has the
     cf_clearance cookie so the second request goes through.
```

The interceptor is registered on `NetworkHelper.client` (the general
client). The `nonCloudflareClient` used by the in-WebView sub-resource
interceptor skips it, to avoid the recursion mentioned above. See
[`../02-modules/core-common.md`](../02-modules/core-common.md).

## Key files

The manga and anime pipelines are structurally identical; for every manga
file there is an anime twin with `Manga` ↔ `Anime` in the name and types.
Only the manga path is listed; substitute to find the anime equivalent.

| File (relative to `../ANIYOMI/`) | Role |
|---|---|
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/MangaExtensionManager.kt` | Public façade. Three StateFlows; drives install/update/uninstall; listens to install events. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionLoader.kt` | Disk → `MangaLoadResult` list. PathClassLoader, signature check, NSFW filter, lib-version validation, private-extension file install. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionInstaller.kt` | Downloads APK via `DownloadManager`, polls status, dispatches to the chosen installer backend. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionInstallService.kt` | Foreground service hosting the `PackageInstaller` or `Shizuku` installer queue. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionInstallActivity.kt` | Transparent activity for the LEGACY system-installer flow (handles `onActivityResult`). |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionInstallReceiver.kt` | BroadcastReceiver for `ACTION_PACKAGE_ADDED/REPLACED/REMOVED` plus the app's private `ACTION_EXTENSION_*` broadcasts. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/installer/InstallerManga.kt` | Base class for the service-side installer queue. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/installer/PackageInstallerInstallerManga.kt` | `PackageInstaller.Session`-based installer. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/installer/ShizukuInstallerManga.kt` | Shizuku-based installer (`pm install-create/write/commit`). |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/api/MangaExtensionApi.kt` | Fetches `index.min.json` from each repo, maps JSON → `MangaExtension.Available`, runs `checkForUpdates`. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/manga/model/MangaExtension.kt` | `sealed class { Installed, Available, Untrusted }`. |
| `app/src/main/java/eu/kanade/tachiyomi/source/manga/AndroidMangaSourceManager.kt` | In-memory `id → MangaSource` map; mirrors into the DB stub-source table. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/InstallStep.kt` | The `Idle/Pending/Downloading/Installing/Installed/Error` enum. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionUpdateNotifier.kt` | Posts the "N extensions have updates" notification. |
| `app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewActivity.kt` | In-app browser / login / Cloudflare-helper activity. |
| `app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewScreen.kt` | Voyager `Screen` wrapper. |
| `app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewScreenModel.kt` | Pulls source headers, provides share/open-in-browser/clear-cookies actions. |
| `core/common/src/main/java/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` | OkHttp interceptor that bypasses Cloudflare via a headless WebView. |
| `domain/src/main/java/mihon/domain/extensionrepo/manga/` | Repo CRUD interactors: `Create/Update/Get/Replace/Delete/GetCount` `MangaExtensionRepo`. |
| `domain/src/main/java/mihon/domain/extensionrepo/service/ExtensionRepoService.kt` | HTTP fetch of `<repo>/repo.json` → `ExtensionRepo` meta. |

Anime-only additions:
- `AnimeExtension.kt` adds `isTorrent: Boolean` to every state.
- `AnimeExtensionApi.kt` parses the `torrent` field from the repo JSON.
- `AnimeExtensionLoader.kt` strips the `Aniyomi: ` prefix (vs `Tachiyomi: `).

## See also

- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the
  contract every source implements (the "what" to this doc's "how").
- [`extensions-update.md`](extensions-update.md) — extension repo
  management and the periodic update check.
- [`download-manager.md`](download-manager.md) — how a downloaded chapter
  / episode pulls pages / videos through a source.
- [`torrent-streaming.md`](torrent-streaming.md) — torrent sources,
  Torrserver, and how the player consumes torrent video.
- [`../02-modules/source-local.md`](../02-modules/source-local.md) — the
  built-in local-file source (always id `0L`).
- [`../02-modules/data.md`](../02-modules/data.md) — the `mangasources` /
  `animesources` stub-source tables.
- [`../02-modules/domain.md`](../02-modules/domain.md) — the
  `MangaSourceManager` / `AnimeSourceManager` interfaces and stub-source
  repositories.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
  — the per-source preference mechanism.
- [`../04-data-models/preferences-catalog.md`](../04-data-models/preferences-catalog.md)
  — full preference-key catalog.
- [`../05-key-flows/browse-catalog.md`](../05-key-flows/browse-catalog.md)
  — end-to-end browse flow.
- [`../05-key-flows/add-to-library.md`](../05-key-flows/add-to-library.md)
  — what happens to the source id when a manga/anime is favourited.
