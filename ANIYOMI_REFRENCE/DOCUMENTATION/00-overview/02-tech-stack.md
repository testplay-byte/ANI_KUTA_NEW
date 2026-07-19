# 00-overview / 02 — Tech Stack

> Distilled from the version catalogs at
> `../ANIYOMI/gradle/libs.versions.toml`, `kotlinx.versions.toml`,
> `androidx.versions.toml`, `compose.versions.toml`, `aniyomi.versions.toml`.

## Languages & runtime

| Layer | Technology | Notes |
|---|---|---|
| App language | **Kotlin** | Code style `official`; Android source set layout v2. |
| Multiplatform | Kotlin Multiplatform (KMP) | `source-api` and `i18n` use `commonMain`/`androidMain`. |
| JVM target | (set by buildSrc `AndroidConfig`) | Desugared via `desugar_jdk_libs`. |
| Scripting (sources) | **QuickJS** (`app.cash.quickjs`) | Extensions can run JS to bypass Cloudflare/parse pages. |
| Build scripting | Kotlin DSL (`*.gradle.kts`) + custom `buildSrc` plugins. | |

## UI

| Technology | Role |
|---|---|
| **Jetpack Compose** (via `compose.versions.toml`) | Modern UI; the app is mid-migration from Views. |
| **Material 3** components | Design system. |
| **Voyager** (`cafe.adriel.voyager` 1.0.1) | Navigation & ScreenModel (Compose-friendly MVVM-ish). |
| `compose-materialmotion` | Shared-element / material transitions. |
| `compose-webview` | In-app WebView (for sources that need login via web). |
| `compose-grid` | Custom grid layout. |
| `reorderable` | Drag-to-reorder (categories, queue). |
| `swipe` (saket) | Swipe-to-dismiss/action rows. |
| Legacy Views: `PhotoView`, `DirectionalViewPager`, `FlexibleAdapter`, `subsampling-scale-image-view`, `image-decoder` | Still used by the manga reader (not yet Compose-migrated). |
| **Coil 3** (`io.coil-kt.coil3`) | Image loading (Compose + GIF + OkHttp network). |

## Data & persistence

| Technology | Role |
|---|---|
| **SQLDelight** 2.0.2 | Database ORM. Schema in `.sq` files under `data/src/main/sqldelight/` (manga) and `.../sqldelightanime/` (anime). |
| SQLite (via `androidx.sqlite` 2.4.0 + `requery:sqlite-android` 3.45.0) | The underlying SQLite, newer than the Android-default for older devices. |
| `androidx.preference:preference-ktx` | Low-level preference storage. |
| **Injekt** (`com.github.mihonapp:injekt`) | DI framework (Tachiyomi/Mihon lineage). |

## Networking

| Technology | Role |
|---|---|
| **OkHttp** 5.0.0-alpha.14 | HTTP client. Includes `logging-interceptor`, `okhttp-brotli`, `okhttp-dnsoverhttps`. |
| **Okio** 3.10.2 | I/O abstraction (OkHttp dep). |
| **Conscrypt** 2.5.3 | TLS provider (newer crypto on old Android). |
| **jsoup** 1.19.1 | HTML parsing in source extensions. |

## Media — anime player

| Technology | Role |
|---|---|
| **aniyomi-mpv-lib** (`com.github.aniyomiorg:aniyomi-mpv-lib` 1.18.n) | The MPV video player, JNI-bound. |
| **ffmpeg-kit** (`com.github.jmir1:ffmpeg-kit` 1.18) | FFmpeg for transcoding/extracting. |
| **NanoHTTPD** 2.3.1 | Embedded HTTP server (serves media to the player / handles local streaming). |
| **Torrserver** (`io.github.secozzi:torrserver` 0.1.0) | Torrent streaming for anime episodes. |
| `seeker` | Seek bar UI component. |
| `mediasession` (`androidx.media:media` 1.7.0) | Media session for background/PiP controls. |
| `truetypeparser` | Font parsing (subtitle fonts). |

## Media — manga reader

| Technology | Role |
|---|---|
| `subsampling-scale-image-view` (tachiyomiorg fork) | Pan/zoom large images efficiently. |
| `image-decoder` (tachiyomiorg fork) | Decodes WebP/AVIF/etc on old Android. |
| `libarchive` (`me.zhanghai.android.libarchive`) | Reads CBZ/CBR/ZIP/RAR archives. |
| `unifile` (tachiyomiorg fork) | SAF-aware file abstraction. |
| `disklrucache` | Disk cache for downloaded pages. |

## Localization

| Technology | Role |
|---|---|
| **Moko Resources** (`dev.icerock.moko:resources` 0.24.5) | Multiplatform string resources. Two catalogs: `:i18n` (shared, from Mihon) and `:i18n-aniyomi` (Aniyomi-specific). |

## Diagnostics & quality

| Technology | Role |
|---|---|
| `logcat` (`com.squareup.logcat`) | Lightweight logging. |
| **LeakCanary** 2.14 | Memory leak detection (debug builds). |
| **AboutLibraries** 11.6.3 | OSS license listing in-app. |
| **Spotless** 7.0.2 + **ktlint** 1.5.0 | Code formatting. |
| **Shizuku** 13.1.0 | Allows operations (e.g. extension install) with elevated privileges without root. |
| ACRA (commented out in `app/build.gradle.kts`) | Crash reporting scaffold (currently disabled). |

## Testing

| Technology | Role |
|---|---|
| JUnit Jupiter 5.11.4 | Unit tests. |
| Kotest assertions 5.9.1 | AssertJ-style assertions. |
| MockK 1.13.17 | Kotlin mocking. |
| `:macrobenchmark` module | Macrobenchmarks + baseline profile generation. |

## Build tooling

| Technology | Role |
|---|---|
| Gradle (wrapper) | Build system. |
| `buildSrc/` | Custom convention plugins: `mihon.android.application`, `mihon.android.application.compose`, `mihon.library`, `mihon.library.compose`, `mihon.code.lint`, `mihon.benchmark`. |
| `foojay-resolver-convention` | Auto-provision JDK toolchains. |
| `android-shortcut-gradle-plugin` | Generates launcher shortcuts (`shortcuts.xml`). |

## Version catalogs (5 files under `gradle/`)

| Catalog | Contents |
|---|---|
| `libs.versions.toml` | The big one: OkHttp, SQLDelight, Coil, Voyager, Injekt, Shizuku, reader libs, player deps, testing. |
| `kotlinx.versions.toml` | Kotlin plugin, Coroutines, Serialization, DateTime, Immutable collections. |
| `androidx.versions.toml` | AndroidX libraries (lifecycle, navigation, work, etc.). |
| `compose.versions.toml` | Compose BOM + compiler + activity/lifecycle integration. |
| `aniyomi.versions.toml` | Aniyomi-specific: MPV, ffmpeg, media, NanoHTTPD, torrserver, seeker. |

## See also

- [`03-module-map.md`](03-module-map.md) — how these libraries are distributed across modules.
- [`04-build-system.md`](04-build-system.md) — Gradle config, build types, ABI splits.
- `../../02-modules/` — per-module dependency details.
