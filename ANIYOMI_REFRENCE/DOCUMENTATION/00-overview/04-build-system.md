# 00-overview / 04 — Build System

> How Aniyomi is built: Gradle config, version catalogs, build types, flavors,
> ABI splits, and the custom convention plugins under `buildSrc/`.

## Gradle layout

```
../ANIYOMI/
├── settings.gradle.kts              ← module includes + version catalog refs
├── build.gradle.kts                 ← root buildscript (plugins apply false)
├── gradle.properties                ← JVM args, AndroidX, Kotlin MPP layout
├── gradle/
│   ├── libs.versions.toml           ← main catalog
│   ├── kotlinx.versions.toml        ← Kotlin/Coroutines/Serialization
│   ├── androidx.versions.toml       ← AndroidX libs
│   ├── compose.versions.toml        ← Compose
│   ├── aniyomi.versions.toml        ← Aniyomi-specific (MPV, ffmpeg, torrserver)
│   ├── gradle-daemon-jvm.properties ← JDK toolchain requirement
│   └── wrapper/                     ← Gradle wrapper
├── gradlew / gradlew.bat
├── buildSrc/                        ← custom convention plugins (see below)
└── <module>/build.gradle.kts        ← per-module config
```

## `gradle.properties` highlights

```properties
android.nonTransitiveRClass=false
android.useAndroidX=true
kotlin.code.style=official
kotlin.mpp.androidSourceSetLayoutVersion=2
org.gradle.caching=true
org.gradle.configureondemand=true
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
```

- `-Xmx4g` — Gradle needs a hefty heap (large multi-module project).
- `parallel` + `caching` + `configureondemand` — build speed.
- `mpp.androidSourceSetLayoutVersion=2` — required for the KMP modules
  (`source-api`, `i18n`).

## Version catalogs

Five catalogs (declared in `settings.gradle.kts`):

```kotlin
versionCatalogs {
    create("kotlinx")    { from(files("gradle/kotlinx.versions.toml")) }
    create("androidx")   { from(files("gradle/androidx.versions.toml")) }
    create("compose")    { from(files("gradle/compose.versions.toml")) }
    create("aniyomilibs"){ from(files("gradle/aniyomi.versions.toml")) }
    create("libs")       { from(files("gradle/libs.versions.toml")) }   // implicit default
}
```

Modules reference them as `libs.xxx`, `kotlinx.xxx`, `androidx.xxx`,
`compose.xxx`, `aniyomilibs.xxx`. `TYPESAFE_PROJECT_ACCESSORS` is enabled, so
module dependencies are written `projects.domain`, `projects.core.common`, etc.

## `buildSrc/` — convention plugins

Custom Gradle plugins that centralize Android/Kotlin/Compose config so each
module's `build.gradle.kts` stays tiny.

| Plugin file | ID | What it does |
|---|---|---|
| `mihon.android.application.gradle.kts` | `mihon.android.application` | Base config for an Android **app** module (SDK levels, Java/Kotlin targets, packaging, test runner). |
| `mihon.android.application.compose.gradle.kts` | `mihon.android.application.compose` | Adds Compose on top of the app plugin. |
| `mihon.library.gradle.kts` | `mihon.library` | Base config for an Android **library** module. |
| `mihon.library.compose.gradle.kts` | `mihon.library.compose` | Adds Compose to a library. |
| `mihon.code.lint.gradle.kts` | `mihon.code.lint` | Lint/Spotless/ktlint wiring. |
| `mihon.benchmark.gradle.kts` | `mihon.benchmark` | Configures the `:macrobenchmark` module. |

Supporting code under `buildSrc/src/main/kotlin/mihon/buildlogic/`:

- `AndroidConfig.kt` — central SDK / version constants.
- `BuildConfig.kt` — `Config` object (e.g. `enableUpdater`, `enableCodeShrink`).
- `ProjectExtensions.kt` — helpers (e.g. `getBuildTime`, `getCommitCount`, `getGitSha`).
- `Commands.kt` — shell command helpers.
- `tasks/LocalesConfigTask.kt` — generates the locales config at build time.

## App build types & flavors (`app/build.gradle.kts`)

The `:app` module defines **four build types**:

| Build type | Application ID suffix | Purpose |
|---|---|---|
| `debug` | `.dev` | Day-to-day development. Debuggable. |
| `release` | — | Production release. Minified + resource-shrunk. |
| `preview` | `.debug` | Release-optimized but debug-signed, for preview builds. |
| `benchmark` | `.benchmark` | Non-debuggable, profileable — used by `:macrobenchmark` to generate baseline profiles. |

ABI splits (per-architecture APKs):

```kotlin
splits {
    abi {
        isEnable = true
        isUniversalApk = true
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }
}
```

Native libraries kept (from `packaging.jniLibs.keepDebugSymbols`): libmpv, libav*
(ffmpeg), libarchive-jni, libconscrypt_jni, libimagedecoder, etc. — i.e. the
player, archive, TLS, and image-decoder native components.

## Build config fields injected into the app

```kotlin
buildConfigField("String", "COMMIT_COUNT", ...)
buildConfigField("String", "COMMIT_SHA", ...)
buildConfigField("String", "BUILD_TIME", ...)
buildConfigField("boolean", "UPDATER_ENABLED", ...)
```

These are consumed at runtime for the "About" screen and the self-updater.

## Shortcut helper

```kotlin
id("com.github.zellius.shortcut-helper")
shortcutHelper.setFilePath("./shortcuts.xml")
```

Generates Android launcher shortcuts (e.g. "Recently read", "Library") from
`shortcuts.xml`.

## Updater

`Config.enableUpdater` toggles the in-app self-updater. The updater checks a
release JSON for new versions; see `../../03-subsystems/updater.md`.

## CI (upstream — for reference only)

Upstream Aniyomi's own workflows live at `../ANIYOMI/.github/workflows/`:
- `build_pull_request.yml`
- `build_push.yml`

> **Note:** We do **not** use upstream's CI. ANIKUTA has its own CI at the repo
> root `.github/workflows/`. The upstream workflows are kept only because they
> came with the reference snapshot. See `../../../../docs/06-build-and-ci.md`.

## See also

- [`03-module-map.md`](03-module-map.md) — what gets built.
- `../../02-modules/` — per-module `build.gradle.kts` details.
- `../../../../docs/06-build-and-ci.md` — OUR build policy (not Aniyomi's).
