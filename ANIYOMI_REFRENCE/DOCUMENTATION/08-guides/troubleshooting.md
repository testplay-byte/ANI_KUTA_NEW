# 08-guides / troubleshooting.md

> Common issues when building/running the Aniyomi reference, with **verified**
> causes and fixes. Values cited here were checked against the actual snapshot
> (not assumed).

> ⚠️ Reminder: per `../../../docs/06-build-and-ci.md`, ANIKUTA is built via
> GitHub Actions only — never locally. The issues below are relevant when
> studying the reference or when CI fails.

## Build issues

### `OutOfMemoryError` during Gradle build
- **Cause:** Gradle daemon heap too small for this large multi-module project.
- **Fix:** `gradle.properties` sets `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`.
  If CI OOMs, ensure this isn't overridden. The project also enables
  `org.gradle.parallel=true`, `org.gradle.caching=true`,
  `org.gradle.configureondemand=true`.

### Compose compiler / Kotlin version mismatch
- **Cause:** Kotlin upgraded in `kotlinx.versions.toml` but the Compose compiler
  (in `compose.versions.toml`) wasn't updated to match.
- **Fix:** The Kotlin/Compose versions must satisfy Compose's compiler
  compatibility map. Keep both catalogs in sync. (The project uses the Kotlin
  2.x Compose compiler plugin, so the old `composeCompiler` version flag no
  longer applies.)

### SQLDelight code generation fails
- **Cause:** A `.sq` file changed without a matching `.sqm` migration, or the
  schema version wasn't bumped.
- **Fix:** For every schema change:
  1. Add a `.sqm` file in the right `migrations/` folder:
     - Manga: `data/src/main/sqldelight/migrations/` (next number after `32.sqm`).
     - Anime: `data/src/main/sqldelightanime/migrations/` (next after `135.sqm`).
  2. The two schemas version **independently** — bumping one does NOT require a
     migration on the other.
- **Verify:** `./gradlew :data:generateSqlDelightInterface` (on CI).

### Moko Resources not generated / `MR`/`AYMR` unresolved
- **Cause:** The Moko Resources plugin didn't run, or a new locale folder has an
  empty `strings.xml`.
- **Fix:** The `mihon.library` + `kotlin("multiplatform")` + `alias(libs.plugins.moko)`
  combo must be applied (it is, in `i18n`/`i18n-aniyomi` `build.gradle.kts`).
  `buildSrc` has a `LocalesConfigTask` that scans `moko-resources/**/strings.xml`
  and filters out empty `<resources/>` — an empty locale file is silently dropped.
  Generated accessors: `tachiyomi.i18n.MR` (`:i18n`) and
  `tachiyomi.i18n.aniyomi.AYMR` (`:i18n-aniyomi`, via `resourcesClassName = "AYMR"`).

### JDK / toolchain errors
- **Cause:** Wrong JDK version.
- **Fix:** The project requires **JDK 17** (set in
  `buildSrc/.../AndroidConfig.kt`: `JavaVersion.VERSION_17`,
  `JvmTarget.JVM_17`). The `foojay-resolver-convention` plugin (in
  `settings.gradle.kts`) auto-provisions the toolchain. `gradle-daemon-jvm.properties`
  pins the daemon JVM requirement.

## Extension issues

### Extension not showing up / marked obsolete
- **Cause:** The extension's `versionName` lib-version is outside the allowed range.
- **Verify:** `MangaExtensionLoader.LIB_VERSION_MIN = 1.4`, `LIB_VERSION_MAX = 1.5`;
  `AnimeExtensionLoader.LIB_VERSION_MIN = 12`, `LIB_VERSION_MAX = 16`.
- **Fix:** Set the extension's `versionName` so the parsed `libVersion` falls in
  range. (Manga uses semver 1.4.x/1.5.x; anime uses integers 12–16.)

### Extension signature not trusted
- **Cause:** Aniyomi verifies extension signatures (the loader uses
  `PackageManager.GET_SIGNATURES` / `GET_SIGNING_CERTIFICATES`).
- **Fix:** Trust the extension's signing cert in the app's extension trust store,
  or use one of the 4 installer backends (Legacy / Private / PackageInstaller /
  Shizuku) that handles install appropriately. See
  `../03-subsystems/source-system.md` and `../03-subsystems/extensions-update.md`.

## Database issues

### "no such table" / schema version mismatch
- **Cause:** A migration `.sqm` is missing or the version counter is wrong.
- **Verify:** Manga DB is at **v32** (32 `.sqm` files); anime DB at **v135**
  (23 `.sqm` files, `113.sqm`–`135.sqm`; bootstrapped at v113 when manga was v112).
- **Fix:** Add the missing `.sqm` in the right `migrations/` folder. The two DBs
  are separate files (`tachiyomi.db` + `tachiyomi.animedb`) with **independent**
  version counters.

### Migration runs but data shape is wrong
- **Cause:** The manga and anime schemas diverged. Anime has extra columns
  (`parent_id`, `fetch_type`, `season_*` on `animes`; `seen`, `fillermark`,
  `last_second_seen`, `total_seconds`, `summary`, `preview_url` on `episodes`)
  and extra tables (`custom_buttons`) that manga doesn't have. Manga has
  `excluded_scanlators` that anime doesn't.
- **Fix:** Don't assume symmetry. See `../00-overview/06-dual-manga-anime-pattern.md`.

## Reader / Player runtime issues

### `NetworkOnMainThreadException`
- **Cause:** A source call wasn't dispatched off the main thread.
- **Fix:** Wrap source/DB calls in `withContext(Dispatchers.IO)`. Interactors
  should do this; UI should never call sources directly. See
  `../01-architecture/03-state-and-async.md`.

### Reader/Player memory leaks (LeakCanary)
- **Cause:** The legacy `ReaderActivity`/`PlayerActivity` (View-based) didn't
  release their View resources. These two are the only non-Compose Activities.
- **Fix:** Ensure `viewer.destroy()` / MPV teardown runs in `onDestroy()`.
  LeakCanary (`com.squareup.leakcanary`) is included in debug builds.

### MPV player fails to load video
- **Cause:** The video URL resolution pipeline failed, or a torrent source
  couldn't reach Torrserver.
- **Verify:** The player resolves videos in 3 lazy steps —
  `EpisodeLoader.getHosters` → `loadHosterVideos` → `HosterLoader.selectBestVideo`.
  Torrents go through Torrserver (app-global foreground service on port 8090).
- **Fix:** Check the hoster/video status; for torrents, confirm Torrserver is
  enabled and reachable. See `../03-subsystems/anime-player.md` and
  `../03-subsystems/torrent-streaming.md`.

## Localization issues

### String shows as a key instead of translated text
- **Cause:** The string was added to `base/strings.xml` but the Moko accessor
  wasn't regenerated, or the wrong catalog was used (`MR` vs `AYMR`).
- **Fix:** Rebuild (CI regenerates accessors). Use `MR.strings.x` for `:i18n`
  strings, `AYMR.strings.x` for `:i18n-aniyomi` strings.

## CI issues (for ANIKUTA's own workflow)

### CI sanity job fails with "MISSING: <file>"
- **Cause:** The `ci-placeholder.yml` workflow verifies required files exist
  (both reference snapshots, the ANIKUTA skeleton, and a list of docs/rules files).
- **Fix:** Don't delete required files, or update the workflow's file list if a
  doc was intentionally renamed. See `../../../docs/06-build-and-ci.md`.

## Things that are NOT bugs (common false alarms)

- **`android.nonTransitiveRClass=false`** in `gradle.properties` — this is
  intentional (the project needs non-transitive R classes off for legacy reasons).
- **No `enableJetifier`** in `gradle.properties` — the project doesn't use
  Jetifier; it has no legacy support-library deps.
- **Two `.db` files** — `tachiyomi.db` (manga) and `tachiyomi.animedb` (anime)
  are intentionally separate. See `../02-modules/data.md`.
- **`universalApk = true`** — ABI splits produce per-arch APKs **and** a universal
  one (by design, for devices that need it).
- **No backup encryption** — `.tachibk` files are gzipped protobuf, **not**
  encrypted. See `../03-subsystems/backup-restore.md`.

## See also

- [`how-to-add-features.md`](how-to-add-features.md)
- `../01-architecture/06-error-handling.md` — the app's own error handling.
- `../../../docs/06-build-and-ci.md` — ANIKUTA's build policy (CI-only).
