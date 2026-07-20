# Session handoff — Phase 4B: Extension system

**Agent:** Z.ai Code (Phase 4B implementation)
**Task ID:** phase-4b-extension-system
**Branch:** `feature/extension-system` (3 commits, pushed)
**Session goal:** Implement the full anime extension system in `:data:extension`.

## What I did

Built the complete Aniyomi-compatible anime extension system in `:data:extension`
(16 new Kotlin files, ~1900 lines) + wired it into `:app` (1 Koin module,
App.kt init, build.gradle.kts dep, AndroidManifest permissions/service).

### Commits on `feature/extension-system`
1. `feat(data:extension): add extension models, loader, and trust system`
2. `feat(data:extension): add repo management, API, installer, and manager`
3. `feat(app): wire the extension system into the app shell (Koin + manifest)`

### Files created (16 in `:data:extension`)
- `model/AnimeExtension.kt` (119) — sealed class Installed/Available/Untrusted
- `model/AnimeLoadResult.kt` (21) — Success/Untrusted/Error/UnrecognizedExtension
- `loader/AnimeExtensionLoader.kt` (279) — PackageManager → ChildFirstPathClassLoader → AnimeSource
- `loader/ChildFirstPathClassLoader.kt` (54) — parent-last classloader
- `loader/HashUtil.kt` (31) — SHA-256 hex helper
- `trust/TrustExtension.kt` (84) — trusted_extensions SharedPreferences
- `repo/ExtensionRepo.kt` (57) — data class + DEFAULT Aniyomi repo URL
- `repo/ExtensionRepoRepository.kt` (122) — SharedPreferences CRUD + StateFlow
- `repo/ExtensionRepoApi.kt` (130) — fetch + parse a repo's index.json
- `api/AnimeExtensionApi.kt` (98) — fetch all repos + checkForUpdates
- `installer/InstallStep.kt` (33) — the install lifecycle enum
- `installer/AnimeExtensionInstaller.kt` (146) — download APK + dispatch to service
- `installer/ExtensionInstallService.kt` (144) — foreground service + notification
- `installer/PackageInstallerBackend.kt` (160) — PackageInstaller.Session
- `installer/ExtensionInstallReceiver.kt` (89) — ACTION_PACKAGE_* listener
- `AnimeExtensionManager.kt` (214) — the public façade (3 StateFlows)

### Files modified (5 in `:app`)
- `app/di/ExtensionModule.kt` (NEW) — Koin module (7 singletons)
- `app/App.kt` — ExtensionAppHolder.init(this) + extensionModule registered
- `app/build.gradle.kts` — +projects.data.extension, +projects.core.sourceApi
- `app/AndroidManifest.xml` — REQUEST_INSTALL_PACKAGES, FOREGROUND_SERVICE*,
  POST_NOTIFICATIONS, QUERY_ALL_PACKAGES + ExtensionInstallService declaration
- `data/extension/build.gradle.kts` — OkHttp, serialization, coroutines, RxJava, core-ktx
- `data/extension/README.md` — full module description

## Key decisions made

1. **index.json not index.min.json** — the implementation prompt specified
   `index.json`; the Aniyomi repo serves both. Followed the prompt.
2. **android.util.Log not logcat** — ADR-033 says use `com.squareup.logcat`,
   but the existing App.kt already uses `android.util.Log` and the prompt
   explicitly said `android.util.Log` with specific tags. Followed the prompt.
3. **No private-extension (.ext) installs** — the reference supports copying
   APKs to `filesDir/exts/*.ext` for private installs. Deferred — Phase 4B
   only supports system-installed (shared) extensions via PackageInstaller.
4. **No Legacy/Shizuku installer backends** — PackageInstaller only (per prompt).
5. **Simple re-scan on package change** — the reference's receiver loads the
   specific package; I do a full `loader.loadExtensions()` re-scan. Simpler,
   slightly less efficient, correct.
6. **SharedPreferences for repos** (not SQLDelight) — per the prompt. The
   reference uses an `extension_repos` SQLDelight table.
7. **ExtensionInstallReceiver registered dynamically** (not statically in
   manifest) — it needs a `Listener` ctor arg, so it can't be declared in XML.

## Issues encountered + fixed

1. **PackageInfoCompat FQN** — `TrustExtension.kt` referenced
   `android.content.pm.PackageInfoCompat` (doesn't exist in the SDK). Fixed
   to use the file-level private `PackageInfoCompat` object.
2. **Foreground service notification** — Android 12+ requires `startForeground()`
   within 5s of `startForegroundService()`. Added notification channel + call.
3. **androidx.core-ktx missing** — `:data:extension` uses `NotificationCompat`
   + `ContextCompat` but the `anikuta.library` plugin doesn't add core-ktx.
   Added it as an explicit dependency.
4. **:app → :core:source-api transitive** — `:data:extension` uses `implementation`
   (not `api`) for source-api, so `:app`'s `App.kt` (which imports
   `ExtensionAppHolder`) couldn't see it. Added `implementation(projects.core.sourceApi)`
   to `:app`.
5. **Stale `Provider` interface** — removed the unused `AnimeExtensionManager.Provider`
   interface + `setInstalling` hook (the installer doesn't need the manager ref).

## What is DONE (pending CI)

- Extension detection (PackageManager query for `tachiyomi.animeextension`)
- Extension loading (ChildFirstPathClassLoader + libVersion 12..16 validation)
- Extension manager (3 StateFlows: installed / available / untrusted)
- Extension repo API (fetches index.json from every configured repo)
- Extension repo management (SharedPreferences CRUD + default Aniyomi repo)
- PackageInstaller backend (with temp APK cleanup on success + failure)
- Trust/untrust flow (untrusted → trust → re-load)
- Koin wiring (ExtensionModule registered in App.kt)
- ExtensionAppHolder.init() called before Koin

## What is NOT done (CI couldn't verify)

- **Build verification** — CI runs only on `main` branch pushes (the workflow's
  trigger is `branches: [main]`, though the YAML has a pre-existing typo
  `branches: ain]`). My branch is `feature/extension-system`, so CI did NOT
  run. The owner should open a PR or merge to main to trigger CI.
- **Runtime testing** — no APK was built (ADR-003: CI-only). Cannot verify the
  extension system actually loads an Aniyomi extension at runtime.

## What the NEXT agent should do

1. **Verify the build** — open a PR for `feature/extension-system` → `main` so
   CI runs. Fix any compile errors CI reports.
2. **Wire extensions into the Browse screen** — the BrowseScreenModel should
   call `AnimeExtensionManager.installedExtensionsFlow` and list the sources.
3. **Wire extensions into Anime Details** — match an anime's source id to a
   loaded `AnimeSource` and call `getEpisodeList(anime)`.
4. **Wire extensions into the Video Resolver** — call `source.getVideoList(episode)`.
5. **Update the Extensions Settings screen** — show real installed/available/
   untrusted extensions from the manager's StateFlows (currently empty states).
6. **Add the once-a-day throttle** to `checkForUpdates` (the reference uses a
   `last_ext_check` preference; I left it to the caller).

## Pointers

- `data/extension/src/main/java/app/confused/anikuta/data/extension/` — all code
- `data/extension/README.md` — module description + architecture
- `app/src/main/java/app/confused/anikuta/di/ExtensionModule.kt` — Koin wiring
- `app/src/main/java/app/confused/anikuta/App.kt` — ExtensionAppHolder.init()
- Branch: `feature/extension-system` (3 commits)
