# :app — Extension + Source Managers + DI (Steps 1.7 + 1.8)

> Steps 1.7 + 1.8 — Copy extension/source managers + Injekt DI wiring from aniyomi.

## Step 1.7 — Extension + Source Managers

### What we copied (8 files)

- `extension/anime/AnimeExtensionManager.kt` — manages installed anime extensions
- `extension/anime/api/AnimeExtensionApi.kt` — fetches extensions from repos
- `extension/anime/model/AnimeExtension.kt` — extension data model
- `extension/anime/model/AnimeLoadResult.kt` — load result enum
- `extension/anime/util/AnimeExtensionLoader.kt` — loads extension APKs at runtime (DexClassLoader)
- `extension/InstallStep.kt` — install step enum
- `source/AndroidAnimeSourceManager.kt` — manages anime sources at runtime
- `source/AnimeSourceExtensions.kt` — source extension helpers

### What we did NOT copy (added later)
- `installer/` — PackageInstaller + Shizuku installer (needs Android installer system)
- `util/AnimeExtensionInstallActivity.kt` — install UI (needs R class + notifications)
- `util/AnimeExtensionInstallReceiver.kt` — install receiver
- `util/AnimeExtensionInstallService.kt` — install service
- `util/AnimeExtensionInstaller.kt` — installer orchestration
- `ExtensionUpdateNotifier.kt` — update notifications (needs notification system)

### Stubs (TODO — fill in later steps)
- `toast()` calls in AnimeExtensionManager → commented out
- `Hash` util in AnimeExtensionLoader → commented out
- `copyAndSetReadOnlyTo` in AnimeExtensionLoader → commented out
- `ChildFirstPathClassLoader` → replaced with `PathClassLoader`
- `AnimeDownloadManager` in AndroidAnimeSourceManager → import removed
- `LocalAnimeSource` in AndroidAnimeSourceManager → import removed

## Step 1.8 — DI Wiring (Injekt)

### What we created (minimal, not copied verbatim)

- `di/AppModule.kt` — minimal Injekt module wiring:
  - Application, PreferenceStore, NetworkHelper, NetworkPreferences
  - AnimeDatabase (SQLDelight) + AnimeDatabaseHandler
  - AnimeExtensionManager + AndroidAnimeSourceManager
- `di/PreferenceModule.kt` — just registers PreferenceStore
- `App.kt` — Application class that imports AppModule + PreferenceModule

### Why minimal (not copied verbatim)
aniyomi's AppModule wires ~40 singletons (both anime + manga: DBs, caches, download
managers, trackers, notifiers, etc.). We only have a fraction of those. The minimal
version wires what we have; more bindings added as we copy more subsystems.

## Dependencies added to `:app`
- Injekt (already in :source-api, propagated via api)
- SQLDelight android-driver (from :data, propagated via api)
