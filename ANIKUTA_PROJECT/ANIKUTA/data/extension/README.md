# `:data:extension`

> The Aniyomi-compatible anime extension system for ANIKUTA.
>
> **Module path:** `data/extension`
> **Package:** `app.confused.anikuta.data.extension`
> **Phase:** 4B (extension loader + manager + installer + repo management)

## Purpose

Loads, manages, and installs **Aniyomi-compatible anime extensions** — external
APKs that implement the `:core:source-api` contract (ADR-029). Each extension
ships one or more [`AnimeSource`](../../core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/AnimeSource.kt)
implementations that the app calls to browse catalogs, fetch episode lists, and
resolve videos.

This module is the runtime half of the source system (the contract half lives in
`:core:source-api`). It is **dual-ready** (a manga twin can be added later by
mirroring the package structure) but only the anime side is implemented in
Phase 4B.

## Architecture (ports the Aniyomi reference)

```
AnimeExtensionManager  ← public façade, owns 3 StateFlows
 ├─ AnimeExtensionLoader      ← PackageManager → ChildFirstPathClassLoader → AnimeSource
 │    ├─ ChildFirstPathClassLoader  (child-first class resolution)
 │    ├─ HashUtil                   (SHA-256 for signing certs)
 │    └─ TrustExtension             (trusted_extensions SharedPreferences)
 ├─ AnimeExtensionApi          ← fetches index.json from every repo, checks for updates
 │    └─ ExtensionRepoApi           (single-repo HTTP fetch + JSON parse)
 ├─ AnimeExtensionInstaller    ← downloads APK + dispatches to the service
 │    ├─ ExtensionInstallService    (foreground service, one install at a time)
 │    ├─ PackageInstallerBackend    (Android PackageInstaller.Session)
 │    └─ ExtensionInstallReceiver   (ACTION_PACKAGE_* broadcasts → manager)
 └─ ExtensionRepoRepository    ← repo CRUD (SharedPreferences-backed)
      └─ ExtensionRepo              (data class + default Aniyomi repo URL)
```

## Key files

| File | Role |
|---|---|
| `model/AnimeExtension.kt` | Sealed class: `Installed` / `Available` / `Untrusted`. |
| `model/AnimeLoadResult.kt` | Sealed result: `Success` / `Untrusted` / `Error` / `UnrecognizedExtension`. |
| `loader/AnimeExtensionLoader.kt` | Scans PackageManager, validates libVersion, hashes signature, instantiates sources. |
| `loader/ChildFirstPathClassLoader.kt` | Parent-last classloader so extensions can bundle their own deps. |
| `loader/HashUtil.kt` | SHA-256 hex helper. |
| `trust/TrustExtension.kt` | Reads/writes the `trusted_extensions` SharedPreferences set. |
| `repo/ExtensionRepo.kt` | Data class + `DEFAULT` (the Aniyomi repo URL). |
| `repo/ExtensionRepoRepository.kt` | CRUD over SharedPreferences, exposes `StateFlow<List<ExtensionRepo>>`. |
| `repo/ExtensionRepoApi.kt` | Fetches + parses a single repo's `index.json`. |
| `api/AnimeExtensionApi.kt` | Fetches all repos concurrently, merges by `pkgName`, checks for updates. |
| `installer/InstallStep.kt` | The `Idle/Pending/Downloading/Installing/Installed/Error` enum. |
| `installer/AnimeExtensionInstaller.kt` | Downloads APK via OkHttp + starts the install service. |
| `installer/ExtensionInstallService.kt` | Foreground service hosting the PackageInstaller backend. |
| `installer/PackageInstallerBackend.kt` | `PackageInstaller.Session` + `PendingIntent` result handling. |
| `installer/ExtensionInstallReceiver.kt` | Listens for `ACTION_PACKAGE_*` system broadcasts. |
| `AnimeExtensionManager.kt` | The public façade — 3 StateFlows, install/uninstall/trust. |

## Dependencies

- `:core:common` (interfaces)
- `:core:source-api` (the Aniyomi-compatible contract — ADR-029)
- OkHttp 5.0.0-alpha.14 (repo index + APK download)
- kotlinx-serialization-json 1.9.0 (parse index.json)
- kotlinx-coroutines-core 1.10.1 (async / Flow)
- RxJava 1.3.8 (source-api compat — the deprecated `fetch*` API)

## Phase 4B scope (what's done)

- ✅ Extension detection (PackageManager query for `tachiyomi.animeextension` feature)
- ✅ Extension loading (ChildFirstPathClassLoader + libVersion 12..16 validation)
- ✅ Extension manager (3 StateFlows: installed / available / untrusted)
- ✅ Extension repo API (fetches `index.json` from every configured repo)
- ✅ Extension repo management (SharedPreferences CRUD + default Aniyomi repo)
- ✅ PackageInstaller backend (with temp APK cleanup on success + failure)
- ✅ Trust/untrust flow (untrusted → trust → re-load)
- ✅ Koin wiring (`ExtensionModule` registered in `App.kt`)

## Phase 4B NOT done (deferred)

- ❌ Wiring into the Browse screen (source list) — next agent
- ❌ Wiring into Anime Details (episode list from matched source) — next agent
- ❌ Wiring into Video Resolver (real server/audio/quality) — next agent
- ❌ Extensions Settings screen showing real installed/available — next agent
- ❌ Private extension installs (`.ext` files) — reference supports it, deferred
- ❌ Legacy / Shizuku installer backends — PackageInstaller only for now
- ❌ Once-a-day throttle on `checkForUpdates` — caller's responsibility

## ADRs referenced

- ADR-003: CI-only builds (no local `./gradlew assemble*`)
- ADR-023: Koin DI (NOT Injekt)
- ADR-029: Aniyomi extension compatibility (same package names, same source-api)
- ADR-033: Logging (`android.util.Log`, tag-based, filterable)

## Extension compat notes

- Manifest metadata uses `tachiyomi.animeextension.*` (anime-specific, NOT the
  generic `tachiyomi.extension` the manga side uses).
- The APK's `versionName` must be `<libversion>.<patch>` where `libversion` ∈ [12, 16].
- The APK's application label must start with `Aniyomi: ` (stripped for display).
- Default repo: `https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo`.
