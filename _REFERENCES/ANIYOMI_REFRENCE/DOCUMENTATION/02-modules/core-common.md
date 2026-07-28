# 02-modules / core-common — The `:core:common` module

> Shared utilities used across the whole app — preferences, network, coroutines,
> storage, image decoding, system helpers, logging. Package root `tachiyomi.core`
> with a few legacy `eu.kanade.tachiyomi.*` and `aniyomi.core.common.*` pockets.
> This is the lowest-level "everything can depend on this" module.

## Purpose

`/home/z/.../ANIYOMI/core/common/` is the grab-bag of cross-cutting infrastructure
that every other module needs at some point: the preference system, the OkHttp
client and its interceptors, coroutine/RxJava bridges, file and disk helpers,
image-format detection, the Torrserver client, and a handful of small system
utilities (device info, GL, density, toasts, WebView). It deliberately has **no
Android UI** and no domain logic — just plumbing.

## Build configuration

Source: `../ANIYOMI/core/common/build.gradle.kts`.

```kotlin
plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android { namespace = "eu.kanade.tachiyomi.core.common" }

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)
    api(libs.logcat)
    api(libs.rxjava)
    api(libs.okhttp.core, libs.okhttp.logging, libs.okhttp.brotli, libs.okhttp.dnsoverhttps, libs.okio)
    implementation(libs.image.decoder)
    implementation(libs.unifile)
    implementation(libs.libarchive)
    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json, kotlinx.serialization.json.okio)
    api(libs.preferencektx)
    implementation(libs.jsoup)
    implementation(libs.natural.comparator)
    implementation(libs.bundles.js.engine)
    implementation(aniyomilibs.ffmpeg.kit)
    implementation(aniyomilibs.torrserver)
    testImplementation(libs.bundles.test)
}
```

Key takeaways:

- The module `api`-exposes OkHttp, Okio, RxJava, kotlinx-coroutines,
  kotlinx-serialization-json, `logcat`, and `preferencektx` — anything that
  downstream modules (`:data`, `:domain`, `:source-api`, `:source-local`, …)
  need in their own public APIs.
- It bundles the JavaScript engine (`quickjs`), the libarchive JNI wrapper, and
  the Torrserver client as `implementation` deps (internal use only).
- The legacy namespace `eu.kanade.tachiyomi.core.common` is kept for the
  `Constants.kt` file and a few preference holders; the modern package root is
  `tachiyomi.core.common`.

## Package layout

```
core/common/src/main/java/
├── tachiyomi/core/common/
│   ├── preference/         ← PreferenceStore, Preference<T>, AndroidPreference*, CheckboxState, TriState, InMemoryPreferenceStore
│   ├── storage/            ← FolderProvider, AndroidStorageFolderProvider, UniFileExtensions, UniFileTempFileManager
│   ├── i18n/               ← Localize.kt (Moko stringResource helpers)
│   └── util/
│       ├── lang/           ← CoroutinesExtensions, RxCoroutineBridge, SortUtil, BooleanExtensions
│       └── system/         ← ImageUtil, LogcatExtensions
├── aniyomi/core/common/
│   └── torrent/            ← TorrentServerApi, TorrentServerUtils, TorrentPreferences, DisabledTorrServerException + model/{Torrent, TorrentRequest}
├── eu/kanade/tachiyomi/
│   ├── core/Constants.kt   ← URL constants, shortcut action strings, MANGA_EXTRA/ANIME_EXTRA
│   ├── core/security/SecurityPreferences.kt
│   ├── network/            ← NetworkHelper, NetworkPreferences, Requests, OkHttpExtensions, interceptors, JavaScriptEngine
│   └── util/
│       ├── lang/           ← Hash, StringExtensions
│       ├── storage/        ← DiskUtil, FileExtensions, FFmpegUtils
│       └── system/         ← ToastExtensions, DensityExtensions, WebViewUtil, DeviceUtil, GLUtil
```

## The `preference/` package

This is the heart of the Aniyomi preference system (see
[`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
for the architectural view).

| File | Role |
|---|---|
| `PreferenceStore.kt` | `interface PreferenceStore` — factory for typed `Preference<T>`s. Methods: `getString`, `getLong`, `getInt`, `getFloat`, `getBoolean`, `getStringSet`, `getObject(key, default, serializer, deserializer)`, `getAll()`. Plus the `inline fun <reified T : Enum<T>> getEnum(key, default)` helper. |
| `Preference.kt` | `interface Preference<T>` — `key()`, `get()`, `set(value)`, `isSet()`, `delete()`, `defaultValue()`, `changes(): Flow<T>`, `stateIn(scope): StateFlow<T>`. Companion holds `isPrivate(key)` / `privateKey(key)` (prefixed `__PRIVATE_`) and `isAppState(key)` / `appStateKey(key)` (prefixed `__APP_STATE_`) for keys that should be excluded from backups. Top-level extensions: `getAndSet`, `deleteAndGet`, `plusAssign`/`minusAssign` on set preferences, `Preference<Boolean>.toggle()`. |
| `AndroidPreference.kt` | `sealed class AndroidPreference<T>` — the production implementation, backed by `SharedPreferences`. One nested `*Primitive` class per primitive type (String/Long/Int/Float/Boolean/StringSet) plus an `Object<T>` for serialised values. `changes()` is built by filtering the store-wide `keyFlow` for the matching key (or `null` for "all keys changed"). |
| `AndroidPreferenceStore.kt` | `class AndroidPreferenceStore(context, sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context))` — implements `PreferenceStore` by delegating to the appropriate `AndroidPreference.*` subclass. The `SharedPreferences.keyFlow` extension turns the `OnSharedPreferenceChangeListener` callback into a `Flow<String?>`. |
| `InMemoryPreferenceStore.kt` | Test/preview implementation of `PreferenceStore`. Holds values in a map. (Note: `getStringSet` is `TODO("Not yet implemented")`.) |
| `CheckboxState.kt` | `sealed class CheckboxState<T>` with two flavours: `State<T>` (Checked / None) for plain checkboxes, `TriState<T>` (Include / Exclude / None) for tri-state filters. `asCheckboxState(condition)` and `List<T>.mapAsCheckboxState(condition)` builders. Used by library/category filter UI. |
| `TriState.kt` | `enum class TriState { DISABLED, ENABLED_IS, ENABLED_NOT }` with a `next()` cycle. Used by simpler preference toggles (e.g. NSFW filter). |

## The `network/` package

| File | Role |
|---|---|
| `NetworkHelper.kt` | `class NetworkHelper(context, preferences: NetworkPreferences)` — builds the shared `OkHttpClient`. Configures 30s connect/read timeout, 2-minute call timeout, 5 MiB on-disk cache, `AndroidCookieJar`, and an interceptor chain: `UncaughtExceptionInterceptor` → `UserAgentInterceptor` → `IgnoreGzipInterceptor` → `BrotliInterceptor`. Adds `CloudflareInterceptor` only to `client` (not `nonCloudflareClient`). Selects a DoH provider (Cloudflare, Google, AdGuard, Quad9, AliDNS, DNSPod, 360, Quad101, Mullvad, ControlD, Njalla, Shecan, LibreDNS) based on `dohProvider()` preference. `defaultUserAgentProvider()` returns the configured UA string. |
| `NetworkPreferences.kt` | `verboseLogging()`, `dohProvider()`, `defaultUserAgent()` (default: Firefox 136 on Windows). |
| `Requests.kt` | Top-level `GET(url, headers, cache)`, `POST`, `PUT`, `DELETE` request builders; suspend `OkHttpClient.get(url)` / `post(url)` extension functions. |
| `OkHttpExtensions.kt` | `Call.asObservable()` / `asObservableSuccess()` (RxJava bridge for legacy `fetch*` API), `Call.await()` / `awaitSuccess()` (coroutines suspend), `OkHttpClient.newCachelessCallWithProgress(request, listener)` (downloads with progress, 30-hour timeout), `Response.parseAs<T>()` (with `context(Json)` receiver), `HttpException(code)`. |
| `AndroidCookieJar.kt` | OkHttp `CookieJar` backed by Android `WebView.CookieManager` so extensions share cookies with the in-app WebView. |
| `DohProviders.kt` | `OkHttpClient.Builder.dohCloudflare()` / `dohGoogle()` / etc. — one extension function per DoH provider. |
| `JavaScriptEngine.kt` | `class JavaScriptEngine(context)` — wraps `QuickJs` so sources can evaluate JS (e.g. to bypass Cloudflare or extract URLs). `suspend fun <T> evaluate(script): T` runs on `Dispatchers.IO`. |
| `ProgressListener.kt` + `ProgressResponseBody.kt` | Callback + OkHttp `ResponseBody` wrapper that reports bytes-read progress; used by image/video downloads. |
| `interceptor/CloudflareInterceptor.kt` | Detects Cloudflare challenges and solves them via WebView or JS engine. |
| `interceptor/UncaughtExceptionInterceptor.kt` | Wraps interceptor exceptions so they propagate to the caller rather than crashing OkHttp's worker. |
| `interceptor/UserAgentInterceptor.kt` | Injects the default user agent when the request doesn't already have one. |
| `interceptor/IgnoreGzipInterceptor.kt` | Strips `Accept-Encoding: gzip` for sites that misbehave. |
| `interceptor/RateLimitInterceptor.kt` + `SpecificHostRateLimitInterceptor.kt` | Token-bucket per-host rate limiting. |
| `interceptor/WebViewInterceptor.kt` | Base class for interceptors that fallback to WebView to fetch a response. |

## The `util/lang/` and `util/system/` packages

| File | Role |
|---|---|
| `util/lang/CoroutinesExtensions.kt` | `launchUI`/`launchIO`/`launchNow` (GlobalScope — `@DelicateCoroutinesApi`), receiver-style `CoroutineScope.launchUI/IO/NonCancellable`, `withUIContext`/`withIOContext`/`withNonCancellableContext`. |
| `util/lang/RxCoroutineBridge.kt` | `suspend fun <T> Observable<T>.awaitSingle(): T` — the bridge used by `:source-api`'s modern suspend API to delegate to legacy `fetch*` methods. |
| `util/lang/SortUtil.kt` | `String.compareToWithCollator(other)` — locale-aware `Collator.PRIMARY` comparison. |
| `util/lang/BooleanExtensions.kt` | (small) `Boolean.toInt()`-style helpers. |
| `util/system/ImageUtil.kt` | `object ImageUtil` — image-format detection (`isImage(name, openStream)`, `findImageType(stream)` → `ImageType` enum covering AVIF/GIF/HEIF/JPEG/JXL/PNG/WEBP), plus a large API for decoding / scaling / cropping / splitting / merging bitmaps used by the manga reader. |
| `util/system/LogcatExtensions.kt` | `inline fun Any.logcat(priority, throwable, message)` — wraps the `logcat` library, appending the throwable's `asLog()` to the message. |
| `util/lang/Hash.kt` | `Hash.md5(input)` — used by `DiskUtil.hashKeyForDisk` and (in `:source-api`) by source-id generation. |
| `util/lang/StringExtensions.kt` | `String.chop(count, replacement)`, `truncateCenter`, `compareToCaseInsensitiveNaturalOrder` (uses `CaseInsensitiveSimpleNaturalComparator`), `byteSize`, `takeBytes(n)`, `htmlDecode`. |

## The `util/storage/` and `util/system/` Android helpers

| File | Role |
|---|---|
| `util/storage/DiskUtil.kt` | `object DiskUtil` — `getExternalStorages(context)`, `hashKeyForDisk(key)` (MD5), `getDirectorySize`, `getTotalStorageSpace` / `getAvailableStorageSpace` (via `StatFs`), `createNoMediaFile(dir, context)` (writes `.nomedia` so gallery apps ignore downloaded chapters), `scanMedia(context, uri)`, `buildValidFilename(name)` (FAT-safe), `NOMEDIA_FILE`, `MAX_FILE_NAME_BYTES` constants. |
| `util/storage/FileExtensions.kt` | (small) `File`/`Uri` helpers including `toFFmpegString(context)` used by `:source-local` to pass SAF URIs to ffmpeg-kit. |
| `util/storage/FFmpegUtils.kt` | Helpers around `FFmpegKit` / `FFprobeKit`. |
| `util/system/DeviceUtil.kt` | Device info: manufacturer, SDK, whether it's a tablet/foldable, bubble support, etc. |
| `util/system/GLUtil.kt` | `maxTextureSize` — used by the reader to cap decoded image dimensions. |
| `util/system/DensityExtensions.kt` | dp/sp → px conversions. |
| `util/system/ToastExtensions.kt` | `Context.toast(message, length)` shortcut. |
| `util/system/WebViewUtil.kt` | WebView version detection and "is WebView available" checks. |

## The `storage/` package (top-level under `tachiyomi.core.common`)

| File | Role |
|---|---|
| `FolderProvider.kt` | `interface FolderProvider { fun directory(): File; fun path(): String }` — abstracts the app's primary storage folder. |
| `AndroidStorageFolderProvider.kt` | `actual` implementation returning `Environment.getExternalStorageDirectory()/Aniyomi`. |
| `UniFileExtensions.kt` | `val UniFile.extension`, `nameWithoutExtension`, `displayablePath` — essential helpers for working with SAF `UniFile`s. |
| `UniFileTempFileManager.kt` | Creates and tracks temp files inside the app's cache dir backed by `UniFile`. |

## The `i18n/` package

`tachiyomi/core/common/i18n/Localize.kt` provides `Context.stringResource(StringResource)`
and `Context.pluralStringResource(PluralsResource, count, vararg args)` — thin
wrappers around Moko Resources' `StringDesc` that handle the platform-specific
`toString(context)` and the workaround for
[icerockdev/moko-resources#337](https://github.com/icerockdev/moko-resources/issues/337)
(em-dash quoting bug). Every module that needs localised strings depends on
`:core:common` (or transitively gets it via `:i18n`) and uses these helpers
instead of touching `Context.getString(R.string.…)` directly.

## The `torrent/` package (`aniyomi.core.common.torrent`)

The Torrserver client used by anime sources that stream over BitTorrent (see
[`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md)).

| File | Role |
|---|---|
| `TorrentServerApi.kt` | `class TorrentServerApi(network, json)` — HTTP client for the local Torrserver. `echo()`, `addTorrent(link, title, poster, data, save): Torrent`, `uploadTorrent(file, title, save): Torrent`. Uses the shared `network.client` to talk to `http://127.0.0.1:$port`. |
| `TorrentServerUtils.kt` | Higher-level helpers built on `TorrentServerApi`. |
| `TorrentPreferences.kt` | `torrserverPort()`, etc. preferences. |
| `DisabledTorrServerException.kt` | Thrown when Torrserver isn't running / can't be reached. |
| `model/Torrent.kt` + `model/TorrentRequest.kt` | `@Serializable` DTOs that mirror Torrserver's JSON. |

## The `core/` and `core/security/` packages

| File | Role |
|---|---|
| `eu/kanade/tachiyomi/core/Constants.kt` | `URL_HELP`, `URL_HELP_UPCOMING`, `MANGA_EXTRA = "manga"` / `ANIME_EXTRA = "anime"` (intent extras), `MAIN_ACTIVITY` class name, and `SHORTCUT_*` action strings for launcher shortcuts (library, anime, manga, updates, history, sources, downloads, extensions). |
| `eu/kanade/tachiyomi/core/security/SecurityPreferences.kt` | `incognitoMode()`, `authenticatorAppLock()`, `lockAppAfter()` etc. — preference keys for the security subsystem. |

## How it fits into the dependency graph

`:core:common` is depended on by **almost everyone**: `:app`, `:data`,
`:domain`, `:source-api` (androidMain only), `:source-local` (androidMain only),
`:core-metadata` (transitively), `:presentation-core`, `:presentation-widget`.
It itself depends only on `:i18n` (for the localise helpers) plus third-party
libraries. This makes it the de-facto "leaf" of the dependency tree above the
external libs — code added here is instantly available everywhere.

## Key files table

| File | Purpose |
|---|---|
| `../ANIYOMI/core/common/build.gradle.kts` | Android library, namespace `eu.kanade.tachiyomi.core.common`. `api`-exposes OkHttp/RxJava/coroutines/serialization/logcat/preferencektx. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/PreferenceStore.kt` | Factory interface for typed `Preference<T>`s. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/Preference.kt` | `Preference<T>` interface + key-prefix helpers + set/toggle extensions. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/AndroidPreference.kt` | Sealed `SharedPreferences`-backed implementation (one subclass per primitive + `Object<T>`). |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/AndroidPreferenceStore.kt` | Production `PreferenceStore` over `PreferenceManager.getDefaultSharedPreferences(context)`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/InMemoryPreferenceStore.kt` | Test/preview `PreferenceStore`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/CheckboxState.kt` | Sealed `CheckboxState<T>` (State + TriState) for filter UIs. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/preference/TriState.kt` | `enum class TriState { DISABLED, ENABLED_IS, ENABLED_NOT }`. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt` | Builds the shared `OkHttpClient` with cookie jar, cache, DoH, and interceptor chain. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkPreferences.kt` | `verboseLogging`, `dohProvider`, `defaultUserAgent` preferences. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/Requests.kt` | `GET/POST/PUT/DELETE` request builders + suspend `OkHttpClient.get/post`. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/OkHttpExtensions.kt` | `Call.asObservable`/`asObservableSuccess`, `Call.await`/`awaitSuccess`, `newCachelessCallWithProgress`, `Response.parseAs<T>`, `HttpException`. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/JavaScriptEngine.kt` | QuickJs wrapper for sources. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/interceptor/*.kt` | Cloudflare, UncaughtException, UserAgent, IgnoreGzip, RateLimit, SpecificHostRateLimit, WebView interceptors. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/util/lang/CoroutinesExtensions.kt` | `launchUI/IO/Now`, `withUIContext`/`withIOContext`/`withNonCancellableContext`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/util/lang/RxCoroutineBridge.kt` | `Observable<T>.awaitSingle()` bridge. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/util/lang/SortUtil.kt` | Locale-aware `compareToWithCollator`. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/lang/StringExtensions.kt` | `chop`, `truncateCenter`, `compareToCaseInsensitiveNaturalOrder`, `byteSize`, `takeBytes`, `htmlDecode`. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/lang/Hash.kt` | `Hash.md5`. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/storage/DiskUtil.kt` | Filename / storage / `.nomedia` helpers. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/storage/FFmpegUtils.kt` | ffmpeg-kit helpers. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/system/DeviceUtil.kt` | Device / form-factor info. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/system/GLUtil.kt` | `maxTextureSize`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/util/system/ImageUtil.kt` | Image-format detection + bitmap manipulation. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/util/system/LogcatExtensions.kt` | `logcat` wrapper that appends throwable stack trace. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/storage/FolderProvider.kt` | Interface for the app's primary storage folder. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` | `Environment.getExternalStorageDirectory()/Aniyomi`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/storage/UniFileExtensions.kt` | `UniFile.extension`, `nameWithoutExtension`, `displayablePath`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/storage/UniFileTempFileManager.kt` | Temp-file manager over `UniFile`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/i18n/Localize.kt` | `Context.stringResource` / `pluralStringResource` Moko wrappers. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/core/Constants.kt` | URLs, intent extras, shortcut actions. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/core/security/SecurityPreferences.kt` | Incognito / app-lock preferences. |
| `../ANIYOMI/core/common/src/main/java/aniyomi/core/common/torrent/TorrentServerApi.kt` | Torrserver HTTP client. |
| `../ANIYOMI/core/common/src/main/java/aniyomi/core/common/torrent/TorrentPreferences.kt` | Torrserver preferences. |
| `../ANIYOMI/core/common/src/main/java/aniyomi/core/common/torrent/model/Torrent.kt` | `@Serializable` torrent DTO. |

## See also

- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
  — the architectural view of the `PreferenceStore` / `Preference<T>` system.
- [`../01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md)
  — coroutine patterns and the RxJava bridge.
- [`source-api.md`](source-api.md) — `:source-api` uses `awaitSingle`, the
  `Json` from Injekt, and the `NetworkHelper` exposed here.
- [`source-local.md`](source-local.md) — uses `withIOContext`, `ImageUtil`,
  `DiskUtil`, `UniFileExtensions`.
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) —
  how `NetworkHelper` and the interceptors ride along with source HTTP calls.
- [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md)
  — `FolderProvider`, `UniFile`, `.nomedia`.
- [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md)
  — `TorrentServerApi` integration.
- [`../04-data-models/preferences-catalog.md`](../04-data-models/preferences-catalog.md)
  — every preference key used across the app.
