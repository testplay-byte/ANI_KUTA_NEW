# 03 — Reference Module Map (Aniyomi)

> A quick-reference map of the Aniyomi source under
> `../ANIYOMI_REFRENCE/ANIYOMI/`. Use this when porting a concept: find the
> module here, read it in the reference, then re-implement it the ANIKUTA way
> under `../ANIKUTA_PROJECT/ANIKUTA/`.

The modules below are defined in
`ANIYOMI_REFRENCE/ANIYOMI/settings.gradle.kts`.

## Modules

### `:app`
The main application module. Contains:
- `Application` subclass, DI setup, startup logic.
- Activities / navigation host.
- The manga reader and anime player UI (note: a lot of UI still lives here rather
  than in `presentation-*`).
- `PlayerViewModel` and reader controllers.
- App-level Gradle config (signing, flavors, dependencies).

**Key entry points:** `ANIYOMI_REFRENCE/ANIYOMI/app/src/main/java/eu/kanade/...`

### `:core:common`
Shared utilities used across the whole app: logging, file helpers, system
extensions, date/time helpers, preference primitives.

### `:core:archive`
Archive (CBZ / CBR / ZIP / RAR) reading support for local manga.

### `:core-metadata`
Manga & anime metadata models and parsing (e.g. MAL/AniList-style metadata,
chapter/info parsing).

### `:data`
The data layer:
- Database schema (SQLDelight) and DAOs.
- Repository implementations.
- Preference stores.
- Sync/backup data models.

### `:domain`
The domain layer (pure Kotlin, no Android dependencies):
- Domain models (Manga, Anime, Chapter, Episode, Track, …).
- Use cases / interactors.
- Repository interfaces (implemented by `:data`).

### `:i18n`
Shared localization using [Moko Resources](https://github.com/icerockdev/moko-resources).
Contains the common string catalog consumed by multiple modules.

### `:i18n-aniyomi`
Aniyomi-specific strings layered on top of `:i18n`.

### `:macrobenchmark`
Macrobenchmark + baseline profile generation. Produces `baseline-prof.txt`
(consumed by the app for startup/profile-guided optimization).

### `:presentation-core`
Shared Jetpack Compose building blocks: theme, reusable components, design tokens.

### `:presentation-widget`
Android home-screen widgets (e.g. updates grid widget).

### `:source-api`
The contract that every "source" (manga/anime site extension) implements.
This is the boundary between the app and the extension ecosystem.

### `:source-local`
A built-in source that treats files on the device (or in archives) as a library,
so users can read local manga/anime.

## Notable top-level files (not modules)

| Path | What |
|---|---|
| `build.gradle.kts` (root) | Root build config, plugin versions. |
| `settings.gradle.kts` | Module includes + version catalogs. |
| `gradle/*.versions.toml` | Version catalogs: `kotlinx`, `androidx`, `compose`, `aniyomilibs`. |
| `gradle/libs.versions.toml` | (If present) classic version catalog. |
| `buildSrc/` | Custom Gradle plugins / convention helpers. |
| `gradle/wrapper/` | Gradle wrapper. |
| `fastlane/` | Store listing metadata for releases. |
| `.github/workflows/` | Upstream CI (for reference only; we run our own). |

## How to use this map

1. Identify the Aniyomi feature you want to port (e.g. "downloads queue").
2. Find the module above that owns it (downloads touches `:app`, `:data`, `:domain`).
3. Read the relevant files in `ANIYOMI_REFRENCE/ANIYOMI/<module>/`.
4. Decide how ANIKUTA will implement it (record in `04-design-decisions.md`).
5. Implement under `ANIKUTA_PROJECT/ANIKUTA/` — do not edit the reference.
