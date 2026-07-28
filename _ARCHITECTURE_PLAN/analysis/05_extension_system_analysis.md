# 05 — Extension System Analysis

> **Phase 1 / Current State.** How extensions are loaded, registered, managed, and how the core app interfaces with them. Identifies what's extension-specific vs. generic, and what would need to change to support other extension types (e.g., CloudStream-style). Raw evidence: `_evidence/EVID-05-extensions.md`.

---

## 1. Executive summary

- **`:core:source-api` is verbatim Aniyomi-compatible** (ADR-029) — same package `eu.kanade.tachiyomi.animesource.*`, same interface names, same method signatures, same model field names. 36 Kotlin files, ~2,775 lines. Only one ANIKUTA-specific addition: `ExtensionAppHolder` (replaces Injekt's `Application` injection for `ConfigurableAnimeSource`).
- **The contract is Android-only, NOT KMP** — despite the README claim. ANIKUTA deviated from Aniyomi (which IS KMP) by collapsing to a single Android source set. This is a documentation-vs-code drift, not a blocker.
- **`:core:common`, `:core:download`, and `:core:backup` are all decoupled from the extension contract** (verified by grep — 0 `import eu.kanade` statements in each). The unified domain models (`UnifiedAnime`, `AnimeDetailsProvider`, `DetailsRequest`, `DownloadRequest` with resolved URLs) are source-agnostic.
- **Two clean abstraction layers already exist** for multi-format extension support:
  - `UnifiedAnime` + `AnimeDetailsProvider` for the details page (ADR-039).
  - `ResolverServer` + `ResolverVideo` for the watch/download flow.
  Both are source-agnostic and were designed for exactly this.
- **The extension contract leaks into 12 feature/app files** (widest: `AppController.kt` + `AnimeDetailViewModel.kt`, 6 imports each). The contract itself (`:core:source-api`) is 6 of the 30 files that import `eu.kanade.tachiyomi.animesource.*`.
- **Source IDs are deterministic and stable across reinstalls**: `MD5("name/lang/versionId").takeLowest64Bits() and Long.MAX_VALUE`. This is what makes the DB ↔ registry mapping work after an extension is uninstalled+reinstalled.
- **Two source-API generations are supported simultaneously**: ext-lib 1.5 (flat `getVideoList(episode)`) and ext-lib 16+ (two-tier `getHosterList` + `getVideoList(hoster)`). `ResolverService` tries the hoster API first, falls back to the flat API.
- **Source matching = title normalization + Levenshtein** (threshold 0.80). No metadata-based matching (no AniList ID ↔ source ID mapping at the source level).

**The headline for the proposal:** The unified details layer (`AnimeDetailsProvider` + `UnifiedAnime`) is the generic abstraction point. Adding a third data source = one new class + one Koin line. The existing two providers (`AniListDetailsProvider`, `ExtensionDetailsProvider`) are already evidence the pattern works. Supporting CloudStream-style extensions requires a *parallel* module approach (new `:core:media-source-api` + `:data:media-extension`), not a rewrite of the existing Aniyomi-compatible stack.

---

## 2. The `:core:source-api` contract

### 2.1 Build configuration

**File:** `core/source-api/build.gradle.kts:1-51`

```kotlin
plugins {
    id("anikuta.library")              // Android library — NOT KMP
    kotlin("plugin.serialization")
}
android { namespace = "app.confused.anikuta.core.sourceapi" }
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")   // for context(Json) in parseAs
    }
}
```

> ⚠️ **Android-only, NOT KMP.** The README (`core/source-api/README.md:3`) claims KMP, but the build uses `anikuta.library` (Android), not `kotlin("multiplatform")`. The Aniyomi reference IS KMP. ANIKUTA deviated. There is no `commonMain`/`androidMain` split; everything lives in `src/main/kotlin/`.

### 2.2 The contract inventory (key interfaces)

All under package `eu.kanade.tachiyomi.animesource.*`:

| Interface / class | File | Purpose |
|---|---|---|
| `AnimeSource` | `online/AnimeSource.kt` | Base interface. `val name: String`, `val id: Long`, `fetchEpisodeList(anime): Observable<List<SEpisode>>`, `getVideoList(episode): List<Video>` (legacy 1.5) |
| `AnimeCatalogueSource` | `online/AnimeCatalogueSource.kt` | Extends `AnimeSource`. `fetchSearchManga(query): Observable<List<SAnime>>`, `fetchPopularManga(page): Observable<List<SAnime>>`, `fetchLatestUpdate(page)`. The "searchable" source. |
| `AnimeHttpSource` | `online/AnimeHttpSource.kt` | Base class for HTTP sources. Implements `AnimeCatalogueSource`. Source ID generated here (L114-118): `MD5("name/lang/versionId").takeLowest64Bits() and Long.MAX_VALUE`. |
| `SAnime` | `model/SAnime.kt` | `url: String`, `title: String`, `artist: String?`, `author: String?`, `description: String?`, `genre: String?`, `status: Int`, `thumbnail_url: String?`, `initialized: Boolean`. **NO `anilistId` field.** |
| `SEpisode` | `model/SEpisode.kt` | `url: String`, `name: String`, `episode_number: Float`, `scanlator: String?`, `date_upload: Long`. |
| `Video` | `model/Video.kt` | `url: String`, `videoUrl: String?`, `videoTitle: String?`, `quality: String?`, `videoStreams: List<VideoStream>`. |
| `Hoster` | `model/Hoster.kt` | `hosterName: String`, `videoList: List<Video>?` (null = lazy). ext-lib 16+ two-tier API. |
| `ConfigurableAnimeSource` | `ConfigurableAnimeSource.kt` | Sources with settings. Uses `ExtensionAppHolder` (ANIKUTA-specific) for `Application` injection instead of Injekt. |
| `AnimeSourceFactory` | `AnimeSourceFactory.kt` | Multi-source extensions. `createSources(): List<AnimeSource>`. |

**Two API generations:**
- **ext-lib 1.5 (legacy):** `getVideoList(episode): List<Video>` — flat video list.
- **ext-lib 16+ (modern):** `getHosterList(episode): List<Hoster>` + `getVideoList(hoster): List<Video>` — two-tier hierarchy with optional lazy resolution.

`ResolverService` (`feature/video-resolver/.../ResolverService.kt:85-139`) tries the hoster API first, falls back to the flat API. Default method bodies throw `IllegalStateException("Not used")` (`AnimeSource.kt:69, 78`) so the resolver detects non-support via try/catch.

### 2.3 What's Aniyomi-specific vs. ANIKUTA-specific

| Element | Origin | Notes |
|---|---|---|
| All interface names + signatures | Aniyomi (verbatim) | ADR-029 — compatibility requirement |
| Package `eu.kanade.tachiyomi.animesource.*` | Aniyomi (verbatim) | Must stay for extension bytecode compat |
| `ExtensionAppHolder` | ANIKUTA-specific | Replaces Injekt's `Application` injection; ANIKUTA uses Koin for its own DI but needs Injekt for extension compat |
| `App.kt:50-79` Injekt setup | ANIKUTA-specific | Registers `Application` + `_animePreferences` into Injekt so extensions can find them |

---

## 3. Extension loading pipeline

**Module:** `:data:extension` — 23 files, 3,238 lines.

**File:** `data/extension/.../loader/AnimeExtensionLoader.kt:64-195`

### 3.1 The pipeline (step-by-step)

```
1. PackageManager scan
   └─ Query installed APKs with <uses-feature> "tachiyomi.animeextension"
      (AnimeExtensionLoader.kt:64-95)

2. LIB-VERSION VALIDATE
   └─ Read meta-data "aniyomi.animeextension.lib.version" (int)
      Accept range [12, 16] (AnimeExtensionLoader.kt:110-130)
      Reject if outside range

3. SHA-256 SIGNATURE TRUST CHECK
   └─ Hash the APK's signing certificate (AnimeExtensionLoader.kt:140-160)
      Compare against trusted-signatures list (user-approved)
      Untrusted → mark as "untrusted", user must explicitly trust

4. ChildFirstPathClassLoader
   └─ Load the APK with a child-first classloader
      (AnimeExtensionLoader.kt:170-180)
      Child-first = extension's own classes take precedence over app's
      (prevents extension bytecode from clashing with app deps)

5. REFLECTIVE INSTANTIATION
   └─ Read meta-data "aniyomi.animeextension.class" (the factory FQCN)
      Reflectively instantiate AnimeSourceFactory (or AnimeSource)
      (AnimeExtensionLoader.kt:185-195)

6. WRAP as AnimeExtension.Installed
   └─ Wrap the instantiated sources + metadata into AnimeExtension.Installed
      (name, pkgName, versionName, versionCode, libVersion, isNsfw,
       isObsolete, signatures, sources: List<AnimeSource>, icon)
```

### 3.2 Source ID assignment

**File:** `core/source-api/.../online/AnimeHttpSource.kt:114-118`

```kotlin
override val id: Long by lazy {
    val key = "${name.lowercase()}/$lang/$versionId"
    MessageDigest.getInstance("MD5").digest(key.toByteArray())
        .let { ByteBuffer.wrap(it).long }
        .and(Long.MAX_VALUE)
}
```

**Properties:**
- **Deterministic** — same name/lang/version → same ID.
- **Stable across reinstalls** — the ID survives uninstall + reinstall of the extension.
- **Stable across devices** — two devices with the same extension get the same source ID.
- **Stable across users** — global, not per-user.

This is critical: it's what makes `SourceLinkStore` (anilistId → sourceId+url) and `ExtensionLinkStore` (sourceId:url → anilistId) work after an extension is uninstalled+reinstalled. It's also what makes backup/restore of source links feasible across devices.

### 3.3 Extension lifecycle

- **Install:** via `AnimeExtensionInstaller` — downloads the APK from the extension repo, prompts the user to install (Android package installer intent), then `AnimeExtensionLoader` picks it up via a `BroadcastReceiver` for `PACKAGE_INSTALLED` / `PACKAGE_REPLACED` / `PACKAGE_REMOVED`.
- **Update:** same as install (new version replaces old).
- **Uninstall:** Android package installer removes the APK; `AnimeExtensionLoader` fires `PACKAGE_REMOVED`; the `AnimeExtensionManager` removes the sources from the registry.
- **Trust:** first-time extensions are "untrusted" until the user explicitly approves (SHA-256 signature). Stored in a trusted-signatures preference.

> ⚠️ **What happens to source links when an extension is uninstalled?** The `SourceLinkStore` + `ExtensionLinkStore` entries persist (they're keyed by sourceId, which is stable). When the extension is reinstalled, the links reactivate. **But if the extension is uninstalled permanently, the links become orphaned** — no cleanup mechanism exists. This is a minor issue today but could grow.

---

## 4. The interface between core app and extensions

### 4.1 Where the contract leaks (12 files)

Grep for `import eu.kanade.tachiyomi.animesource.*` across `:feature:*` and `:app`:

| File | Import count | What it uses |
|---|---|---|
| `app/.../navigation/AppController.kt` | 6 | `SAnime`, `SEpisode`, `AnimeSource`, `AnimeCatalogueSource` (parameter types) |
| `feature/anime-details/.../AnimeDetailViewModel.kt` | 6 | `SAnime`, `SEpisode`, `AnimeSource` (source switching, episode fetching) |
| `feature/search/.../SearchViewModel.kt` | 4 | `SAnime`, `AnimeCatalogueSource` (extension search results) |
| `feature/video-resolver/.../ResolverService.kt` | 4 | `SEpisode`, `AnimeSource`, `Hoster`, `Video` (video resolution) |
| `feature/watch/.../WatchScreen.kt` | 3 | `SEpisode`, `Video` (episode list, video selection) |
| `feature/download/.../DownloadVideoPickerSheet.kt` | 2 | `Video` (picker UI) |
| `feature/anime-details/.../EpisodesSection.kt` | 2 | `SEpisode` (episode list) |
| `feature/anime-details/.../ManualSearchSheet.kt` | 2 | `SAnime`, `AnimeCatalogueSource` (manual linking) |
| `feature/search/.../ExtensionLinkingSheet.kt` | 2 | `SAnime`, `AnimeCatalogueSource` (linking UI) |
| `app/.../download/DownloadOrchestrator.kt` | 2 | `SEpisode`, `AnimeSource` (download resolution) |
| `feature/anime-details/.../EpisodeDownloadControl.kt` | 1 | `SEpisode` (download button) |
| `feature/anime-details/.../SourceSwitcherMenu.kt` | 1 | `AnimeSource` (source switching) |

**The narrowest extension-aware module:** `:feature:extensions-settings` (zero `eu.kanade` imports — talks only to `AnimeExtensionManager` + `AnimeExtension` data classes). This is the model to aspire to.

### 4.2 Is the interface narrow or wide?

**Wide.** 12 files directly reference the extension contract types. The core domain models (`Anime`, `Episode`) in `:core:common` do NOT import `eu.kanade` (verified), but the feature/viewmodel layer does. This means the feature layer is coupled to the Aniyomi contract — if ANIKUTA wanted to support a different extension format, every one of these 12 files would need to handle both.

### 4.3 Extension-specific assumptions baked into core?

| Module | Imports `eu.kanade`? | Assessment |
|---|---|---|
| `:core:common` | NO | ✅ Clean. `UnifiedAnime`, `AnimeDetailsProvider`, `DetailsRequest` are source-agnostic. Only `HtmlToPlainText.kt:84-92` has a `mapSAnimeStatus(status: Int)` helper that takes a raw `Int` (no source-api import). |
| `:core:download` | NO (only in comments) | ✅ Clean. Declares `:core:source-api` as a dep but only references `SEpisode`/`Track` in **comments** (`DownloadModels.kt:55-60`, `DownloadRequest.kt:9`). The download engine is pure: it accepts an already-resolved `DownloadRequest`. |
| `:core:backup` | NO | ✅ Clean. Persists extension source IDs as opaque `Long` values in `SourceLinkBackupProvider.kt:17-85` — never imports the source-api types. Coupling is by data shape, not by type. |
| `:core:anilist` | NO | ✅ Clean. AniList knows nothing about extensions. |
| `:core:episode-metadata` | NO | ✅ Clean. Metadata sources are independent. |

**Conclusion:** The core layer is clean. The coupling is entirely in the feature + app layers (the 12 files above). This is the correct architectural outcome of ADR-039 (unified details page) + Rule §14 (`DownloadOrchestrator` in `:app`).

---

## 5. Source matching & linking

### 5.1 `SourceMatcher`

**File:** `data/extension/.../matcher/SourceMatcher.kt:346-378`

- **Algorithm:** title normalization (lowercase, strip special characters) + Levenshtein distance.
- **Threshold:** 0.80 (similarity score).
- **Strategy:** sequential priority-ordered search with exact-match short-circuit (`SourceMatcher.kt:233-284`).
- **No metadata-based matching** — there's no AniList ID ↔ source ID mapping at the source level. Matching is purely by title.

**Limitation:** Title-based matching produces false positives for series with similar names (e.g., "Bleach" vs "Bleach: Thousand-Year Blood War"). The linking flow lets the user override, but auto-matching can pick the wrong anime.

### 5.2 `SourceLinkStore` + `ExtensionLinkStore`

**Files:** `data/extension/.../SourceLinkStore.kt` + `ExtensionLinkStore.kt`

**`SourceLinkStore`** — AniList → source mapping:
- Key: `anilistId.toString()`
- Value: `SourceLinkItem(sourceId, sourceName, animeUrl, animeTitle)`
- Purpose: "given an AniList anime, find its extension source."

**`ExtensionLinkStore`** — source → AniList mapping:
- Key: `"${sourceId}:${animeUrl}"`
- Value: `Int(anilistId)`
- Purpose: "given an extension anime, find its AniList ID."

**The bidirectional mapping** is what makes the linking flow work:
1. User opens an AniList anime → `SourceLinkStore.getLink(anilistId)` → finds the extension source.
2. User opens an extension anime → `ExtensionLinkStore.getAniListId(sourceId, url)` → finds the AniList ID.

> ⚠️ Both stores are keyed by `anilistId` (non-nullable `Int`). This is part of the identity-layer coupling described in Doc 01 + Doc 03. The proposed `WatchableId` would generalize these.

### 5.3 The linking flow (`ExtensionLinkingSheet`)

1. User taps an Extension search result → `AppController.startLinking(source, sAnime)`.
2. `ExtensionLinkingViewModel` searches AniList by title (`AniListApi.searchByTitleMultiple`).
3. User picks an AniList match (or "go without linking").
4. If linked: `SourceLinkStore.put(anilistId, sourceLink)` + `ExtensionLinkStore.put(sourceId:url, anilistId)`.
5. If unlinked: nothing is stored; the anime opens via `ExtensionAnimeDetailDestination` with `anilistId = null`.

---

## 6. The unified details layer — the generic abstraction point

**Files (in `:core:common/.../details/`):**
- `AnimeDetailsProvider.kt` — the interface
- `UnifiedAnime.kt` — the unified model
- `DetailsRequest.kt` — the sealed request type

### 6.1 `AnimeDetailsProvider` interface

```kotlin
interface AnimeDetailsProvider {
    val source: DataSource                  // ANILIST, EXTENSION, (future: MAL, TMDB, MEDIA_EXTENSION)
    suspend fun load(request: DetailsRequest): UnifiedAnime?
}
```

### 6.2 `DetailsRequest` (sealed)

```kotlin
sealed class DetailsRequest {
    data class ByAniListId(val anilistId: Int) : DetailsRequest()
    data class ByExtension(
        val sourceId: Long,
        val animeUrl: String,
        val animeTitle: String,
        val anilistId: Int? = null,         // ◄── nullable: unlinked extension anime
    ) : DetailsRequest()
}
```

### 6.3 `UnifiedAnime`

```kotlin
data class UnifiedAnime(
    val source: DataSource,
    val anilistId: Int?,                    // ◄── nullable
    val sourceId: Long?,
    val sourceName: String?,
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val coverColor: String?,
    val status: Int,
    val genres: List<String>,
    val totalEpisodes: Int?,
    val nextAiringEpisode: Int?,
    val score: Double?,
    val studio: String?,
    // ... more fields
)
```

### 6.4 The two existing providers

| Provider | File | DataSource | Handles unlinked? |
|---|---|---|---|
| `AniListDetailsProvider` | `:core:anilist/.../details/` | `ANILIST` | ❌ Returns null if `anilistId == null` |
| `ExtensionDetailsProvider` | `:feature:anime-details/.../` | `EXTENSION` | ✅ DB-first: `getBySourceAndUrl` if `anilistId == null` |

**Adding a third provider** (e.g., `MALDetailsProvider` or `TMDBDetailsProvider`):
1. Create one new class implementing `AnimeDetailsProvider`.
2. Register it in Koin: `app/.../di/DetailsModule.kt:38-62` — `single<List<AnimeDetailsProvider>> { listOf(...) }`.
3. The source-switcher menu (`SourceSwitcherMenu`) automatically picks it up.

**This is the proven pattern.** ADR-039 already demonstrated it with two providers. The provider-abstraction proposal (Phase 2, Doc 02) extends this to home feed, search, and schedule.

### 6.5 The video-resolution abstraction

**Files (in `:feature:video-resolver/`):**
- `ResolverService.kt` — orchestrates resolution
- `VideoTitleParser.kt` — parses titles into Server → Audio → Quality hierarchy
- `ResolverStrategyPicker.kt` — selects structured vs raw strategy
- `ResolverServer` / `ResolverVideo` — the unified types

`ResolverService` (`feature/video-resolver/.../ResolverService.kt:85-139`):
1. Calls `source.getHosterList(episode)` (ext-lib 16+).
2. If that throws (legacy 1.5), falls back to `source.getVideoList(episode)`.
3. For each hoster, checks `hoster.videoList` (non-lazy) first; falls back to `getVideoList(hoster)` (lazy).
4. `VideoTitleParser` parses titles into Server → Audio → Quality.
5. `ResolverStrategyPicker` selects structured (3-tier) vs raw (flat list) UI.

**The `ResolverServer` + `ResolverVideo` types are source-agnostic.** They don't reference `AnimeSource` or `Hoster` directly — they're the unified output. This means a future CloudStream-style resolver could produce `ResolverServer` + `ResolverVideo` from a different source model, and the watch/download flow wouldn't change.

---

## 7. CloudStream support assessment

### 7.1 What CloudStream-style extensions look like (background)

CloudStream extensions (from the CloudStream 3 app) use a different metadata model:
- `MediaContent` (not `SAnime`) — with explicit season + episode numbers, TV-show-style.
- Different `Video` resolution semantics — no `Hoster` concept, no `videoTitle` convention.
- Different package structure (`com.lagradost.cloudstream3.*`).
- Different loading mechanism (also APK-based, but different `<uses-feature>` flag).

### 7.2 What's Aniyomi-specific vs. generic

| Element | Aniyomi-specific? | Generic enough for CloudStream? |
|---|---|---|
| `SAnime` / `SEpisode` / `Video` / `Hoster` types | Yes | ❌ CloudStream uses `MediaContent` |
| Package `eu.kanade.tachiyomi.animesource.*` | Yes | ❌ CloudStream uses `com.lagradost.cloudstream3.*` |
| `<uses-feature> "tachiyomi.animeextension"` | Yes | ❌ CloudStream uses `cloudstream.extension` |
| ext-lib version metadata | Yes | ❌ CloudStream has its own versioning |
| `AnimeExtensionManager` + `AnimeExtensionLoader` | Yes | ❌ CloudStream needs its own loader |
| `AnimeHttpSource` (HTTP base class) | Yes | ⚠️ Partially — CloudStream sources are HTTP too, but the base class differs |
| `UnifiedAnime` + `AnimeDetailsProvider` | **No** | ✅ Source-agnostic — CloudStream can map into it |
| `ResolverServer` + `ResolverVideo` | **No** | ✅ Source-agnostic — CloudStream can produce them |
| `DownloadRequest` (resolved URLs) | **No** | ✅ Source-agnostic |
| `DownloadManager` engine | **No** | ✅ Source-agnostic |

### 7.3 The recommended approach: parallel modules

**Add a parallel stack alongside the existing Aniyomi-compatible stack — don't modify the existing one:**

| New module | Purpose | Parallel to |
|---|---|---|
| `:core:media-source-api` | CloudStream-style contract (`MediaContent`, `MediaEpisode`, `MediaVideo`) | `:core:source-api` |
| `:data:media-extension` | CloudStream extension loader + manager | `:data:extension` |
| `MediaDetailsProvider` (in `:data:media-extension` or `:app`) | Maps `MediaContent` → `UnifiedAnime` | `ExtensionDetailsProvider` |
| `MediaResolverStrategy` (in `:core:video-resolver` or `:feature:video-resolver`) | Maps `MediaVideo` → `ResolverServer`/`ResolverVideo` | existing `ResolverService` |
| `MediaSourceMatcher` (in `:data:media-extension`) | Matches `MediaContent` to AniList/anime | `SourceMatcher` |

**Zero changes to `:core:common`, `:core:download`, `:core:backup`.** The only core change is adding `DataSource.MEDIA_EXTENSION` (one line) to the enum.

**Why parallel, not unified:** The two extension formats have fundamentally different metadata models. Trying to unify them into one contract would either (a) break Aniyomi compatibility (ADR-029 violation) or (b) create a lowest-common-denominator contract that serves neither well. Parallel stacks that both map into `UnifiedAnime` is cleaner and lower-risk.

### 7.4 What needs to be generalized

| Component | Current | Needed |
|---|---|---|
| `AnimeExtensionManager` | Aniyomi-only | A generic `ExtensionManager` interface + Aniyomi impl + CloudStream impl |
| `SourceMatcher` | Aniyomi-only (title + Levenshtein) | Either generalize, or add parallel `MediaSourceMatcher` |
| Extension-settings UI (`:feature:extensions-settings`) | Aniyomi-only | Add a tab/toggle for CloudStream extensions |
| `AppController` extension handling | 6 `eu.kanade` imports | Route through an abstraction (or accept the coupling as the composition root's job) |

---

## 8. Functional gaps vs. Aniyomi

Based on the Aniyomi reference (`_REFERENCES/ANIYOMI_REFRENCE/DOCUMENTATION/`):

| Aniyomi feature | ANIKUTA status |
|---|---|
| `SourceManager` + stub sources | ❌ Missing — uninstalled extensions show "Source no longer installed" instead of graceful degradation |
| WebView / Cloudflare bypass | ❌ Missing |
| Private installer / Shizuku / Legacy installer backends | ❌ Missing (Shizuku dep is in the catalog but unused) |
| Manga extensions | ❌ Missing (deferred per ADR-009) |
| Extension repo browsing UI | ⚠️ Partial — `ExtensionRepoSettingsScreen` exists but is a scaffold |
| Per-source preference screens (`ConfigurableAnimeSource`) | ⚠️ `ConfigurableAnimeSource` interface exists but no per-source settings UI |

---

## 9. Extensions-settings UI — actual state

**Files:** `ExtensionsSettingsScreen.kt` + `ExtensionRepoSettingsScreen.kt` (740 lines total)

The README says "UI scaffold — real data binding lands in a later phase." This is **outdated**. What actually works:
- ✅ Install / uninstall / trust / repo-management all work.
- ✅ 3-category structure (Trusted Sources → Installed → Available).
- ✅ Package-event auto-refresh.

What's missing:
- ❌ Install progress UI.
- ❌ Drag-reorderable trusted sources.
- ❌ Per-source preference screens (`ConfigurableAnimeSource`).
- ❌ ViewModel (logic is inline in the composable).

---

## 10. Conclusion

The extension system is **architecturally well-positioned for multi-format support**:

1. **The core is clean.** `:core:common`, `:core:download`, `:core:backup` have zero `eu.kanade` imports. The unified domain models (`UnifiedAnime`, `AnimeDetailsProvider`, `ResolverServer`, `ResolverVideo`, `DownloadRequest`) are source-agnostic.

2. **The coupling is concentrated in the feature + app layers** (12 files). This is acceptable for a composition root but should be reduced where possible.

3. **Two abstraction layers already exist** (`AnimeDetailsProvider` for details, `ResolverServer`/`ResolverVideo` for watch/download). These are the extension points for new formats.

4. **The parallel-module approach** is the recommended path for CloudStream support — add `:core:media-source-api` + `:data:media-extension` + `MediaDetailsProvider`, mapping into the existing `UnifiedAnime`. Zero changes to core download/backup.

5. **Source IDs are deterministic and stable** — this is a key enabler for cross-device backup/restore and for the proposed `WatchableId.Extension(sourceId, url)`.

6. **The functional gaps vs. Aniyomi** (SourceManager stubs, WebView/Cloudflare, per-source prefs) are independent of the provider-coupling restructuring and can be tackled separately.

These findings drive `proposals/04_extension_evolution.md`.

---

*Evidence source: `_evidence/EVID-05-extensions.md` (1,216 lines).*
