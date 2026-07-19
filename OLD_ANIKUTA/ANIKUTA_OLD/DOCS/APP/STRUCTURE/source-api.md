# :source-api module — what we copied from aniyomi

> Step 1.4 — Copy `:source-api` from aniyomi (anime side only).
> This is the **anime source/extension contract** — the plugin boundary that
> 3rd-party extensions compile against. The most important module for our app.

---

## Source
- aniyomi module: `:source-api` (Kotlin Multiplatform: commonMain + androidMain)
- aniyomi path: `REFERENCE/source-api/src/`
- aniyomi commit: `2f5cf77` (2025-11-05)
- Copied on: Session 15 (Step 1.4)

## What we copied (25 files — anime side only)

### Core source interfaces (5 files)
- `AnimeSource.kt` — the base anime source interface
- `AnimeCatalogueSource.kt` — a source with a browseable catalog
- `AnimeSourceFactory.kt` — factory for multi-source extensions
- `ConfigurableAnimeSource.kt` — source with a settings screen
- `UnmeteredSource.kt` — marker for sources that don't count against rate limits

### Model (11 files)
- `model/SAnime.kt` + `model/SAnimeImpl.kt` — anime metadata (title, author, status, etc.)
- `model/SEpisode.kt` + `model/SEpisodeImpl.kt` — episode metadata
- `model/Video.kt` — video stream + `SerializableVideo` (for serialization)
- `model/Hoster.kt` — hoster info (extension-lib 16 hoster system)
- `model/AnimeFilter.kt` + `model/AnimeFilterList.kt` — browse filters
- `model/AnimesPage.kt` — a page of search/browse results
- `model/AnimeUpdateStrategy.kt` — how the source updates
- `model/FetchType.kt` — Seasons vs Episodes fetch mode

### Online sources (3 files)
- `online/AnimeHttpSource.kt` — HTTP-based anime source (the main base class)
- `online/ParsedAnimeHttpSource.kt` — parsed HTML anime source (JSoup helpers)
- `online/ResolvableAnimeSource.kt` — source that can resolve deep links

### Utils (1 file)
- `utils/Preferences.kt` — source preference helpers

### Shared util (4 files)
- `util/JsoupExtensions.kt` — HTML parsing helpers (asJsoup, selectText, etc.)
- `util/JsonExtensions.kt` — JSON instance + helpers (uses Injekt)
- `util/RxExtension.kt` — Observable.awaitSingle() (merged from KMP expect/actual)
- `util/VideoInfo.kt` — sealed class for video info

### PreferenceScreen (1 file)
- `PreferenceScreen.kt` — typealias to androidx.preference.PreferenceScreen

## What we did NOT copy (manga side — D2: anime-only)

The entire `eu.kanade.tachiyomi.source` package (manga):
- `MangaSource`, `CatalogueSource`, `HttpSource`, `ParsedHttpSource`, etc.
- `model/Filter`, `FilterList`, `MangasPage`, `Page`, `SChapter`, `SManga`, etc.

> Confirmed: the anime side has **zero imports** from the manga side. They are
> fully parallel, independent stacks (per `REFERENCE-DOCS/ANALYSIS-DUAL-MODEL.md`).

## KMP → Android-only simplification

aniyomi's `:source-api` is Kotlin Multiplatform (commonMain + androidMain). We
simplified to **Android-only** (single `src/main/java/` source set):
- `PreferenceScreen`: was `expect class` (commonMain) + `actual typealias` (androidMain) → now just `typealias`
- `RxExtension`: was `expect fun` (commonMain) + `actual fun` (androidMain) → now just `fun`

## Changes made to copied files

1. **Package rename:** `eu.kanade.tachiyomi.animesource.*` → `app.anikuta.source.api.*`, `eu.kanade.tachiyomi.util.*` → `app.anikuta.source.api.util.*`
2. **Import rename:** `eu.kanade.tachiyomi.network.*` → `app.anikuta.core.network.*`, `tachiyomi.core.common.*` → `app.anikuta.core.*`
3. **KMP fix:** removed `actual` keywords (Android-only, no expect/actual needed)

## Dependencies added (to `:source-api` build.gradle.kts)

- `injekt` (`com.github.mihonapp:injekt:91edab2317` from JitPack) — DI framework used by `injectLazy`
- `compose-runtime` (BOM-managed) — for `@Stable` annotation in `AnimeFilterList`
- `jsoup` — for HTML parsing in `JsoupExtensions` + `ParsedAnimeHttpSource`
- JitPack repository added to `settings.gradle.kts`

## Key types (quick reference)

| Type | Purpose |
|------|---------|
| `AnimeSource` | Base interface all anime sources implement |
| `AnimeHttpSource` | HTTP-based source base class (extensions extend this) |
| `SAnime` / `SEpisode` | Anime + episode metadata models |
| `Video` | A video stream (URL + quality + headers) |
| `Hoster` | Hoster info (extension-lib 16 hoster system) |
| `AnimeFilterList` | Browse/search filters |
| `AnimeSourceFactory` | Factory for multi-source extensions |
