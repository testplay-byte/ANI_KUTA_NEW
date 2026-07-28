# 04 — Extension System Evolution

> **Phase 2 / Proposed Architecture.** How the extension system should evolve to support multiple extension types (Aniyomi-style + CloudStream-style + future) without changes to core modules. Grounded in `analysis/05_extension_system_analysis.md`.

---

## 1. The problem

The current extension system is **Aniyomi-compatible only** (ADR-029). The `:core:source-api` contract uses Aniyomi's package (`eu.kanade.tachiyomi.animesource.*`), interface names, and method signatures. This is correct for Aniyomi compatibility — but it means the core app can only consume Aniyomi-style extensions.

The prompt asks for an architecture flexible enough to support **additional extension types** (different APIs, content formats, metadata structures, potentially CloudStream-style) without changes to core modules.

---

## 2. The key finding (from Doc 05)

**The core is already clean.** `:core:common`, `:core:download`, `:core:backup` have zero `import eu.kanade` statements. The unified domain models (`UnifiedAnime`, `AnimeDetailsProvider`, `DetailsRequest`, `ResolverServer`, `ResolverVideo`, `DownloadRequest`) are source-agnostic.

**Two abstraction layers already exist** that are the extension points for new formats:
1. `AnimeDetailsProvider` + `UnifiedAnime` — for the details page (ADR-039).
2. `ResolverServer` + `ResolverVideo` — for the watch/download flow.

**The extension contract leaks into 12 feature/app files** (Doc 05 §4.1), but this is in the feature/app layer (the composition root), not the core layer. It's acceptable for the composition root to know about specific extension formats — that's its job.

---

## 3. The recommended approach: parallel modules

**Add a parallel stack alongside the existing Aniyomi-compatible stack — don't modify the existing one.**

### 3.1 Why parallel, not unified

The two extension formats (Aniyomi + CloudStream) have fundamentally different metadata models:
- Aniyomi: `SAnime` (url, title, status) + `SEpisode` (url, name, episode_number) + `Video` (url, quality).
- CloudStream: `MediaContent` (seasons, episodes with explicit S/E numbers) + `Video` (different resolution semantics).

Trying to unify them into one contract would either:
- (a) Break Aniyomi compatibility (ADR-029 violation — existing extensions stop working), or
- (b) Create a lowest-common-denominator contract that serves neither well.

**Parallel stacks that both map into `UnifiedAnime` + `ResolverServer`/`ResolverVideo`** is cleaner and lower-risk. The core consumes the unified types; each extension format has its own loader + mapper.

### 3.2 The proposed module structure

```
EXISTING (Aniyomi-compatible — unchanged):
  :core:source-api          ← Aniyomi contract (eu.kanade.tachiyomi.animesource.*)
  :data:extension           ← Aniyomi loader + manager + matcher
  ExtensionDetailsProvider  ← maps SAnime → UnifiedAnime (in :feature:anime-details or :app)
  ResolverService           ← maps AnimeSource.getVideoList → ResolverServer/ResolverVideo

NEW (CloudStream-compatible — future):
  :core:media-source-api    ← CloudStream contract (com.lagradost.cloudstream3.*)
  :data:media-extension     ← CloudStream loader + manager
  MediaDetailsProvider      ← maps MediaContent → UnifiedAnime
  MediaResolverStrategy     ← maps MediaVideo → ResolverServer/ResolverVideo

SHARED (already exist):
  :core:common              ← UnifiedAnime, AnimeDetailsProvider, DetailsRequest, WatchableId
  :core:download            ← DownloadManager (accepts resolved DownloadRequest)
  :core:backup              ← BackupManager (persists opaque sourceIds)
  :core:video-resolver (NEW, extracted from :feature:video-resolver) ← ResolverService logic
```

**Zero changes to `:core:common`, `:core:download`, `:core:backup`.** The only core change is adding `DataSource.MEDIA_EXTENSION` (one line) to the enum.

---

## 4. The generic `ExtensionManager` interface

Today, `AnimeExtensionManager` is Aniyomi-specific. To support multiple formats, introduce a generic interface:

```kotlin
// :core:common (or :core:provider-api)
interface ExtensionManager<out T : InstalledExtension> {
    val installedExtensions: StateFlow<List<T>>
    val changes: Flow<ExtensionChange>       // Installed / Updated / Removed

    fun getExtension(pkgName: String): T?
    fun getSources(): List<ExtensionSource>
    fun getSource(sourceId: Long): ExtensionSource?
    fun trust(pkgName: String)
    fun untrust(pkgName: String)
}

interface InstalledExtension {
    val pkgName: String
    val name: String
    val versionName: String
    val versionCode: Int
    val isNsfw: Boolean
    val isTrusted: Boolean
    val sources: List<ExtensionSource>
}

interface ExtensionSource {
    val id: Long                 // deterministic (MD5 of name/lang/versionId)
    val name: String
    val lang: String
    val category: ExtensionCategory   // ANIME, MANGA, MEDIA (CloudStream)
}

enum class ExtensionCategory { ANIME, MANGA, MEDIA }
```

**`AnimeExtensionManager` implements `ExtensionManager<AnimeExtension>`** (Aniyomi-specific).
**`MediaExtensionManager` (new) implements `ExtensionManager<MediaExtension>`** (CloudStream-specific).

The extensions-settings UI can then iterate `List<ExtensionManager<*>>` (Koin multi-binding) to show all installed extensions across formats, grouped by category.

---

## 5. The `MediaDetailsProvider` (CloudStream → UnifiedAnime)

```kotlin
// :data:media-extension (or :app)
class MediaDetailsProvider(
    private val manager: MediaExtensionManager,
) : AnimeDetailsProvider {

    override val source = DataSource.MEDIA_EXTENSION

    override suspend fun load(request: DetailsRequest): UnifiedAnime? {
        return when (request) {
            is DetailsRequest.ByMediaExtension -> {       // NEW request variant
                val source = manager.getSource(request.sourceId) ?: return null
                val mediaContent = source.fetchDetails(request.mediaUrl) ?: return null
                mediaContent.toUnifiedAnime()
            }
            else -> null   // Aniyomi + AniList requests are handled by other providers
        }
    }
}

private fun MediaContent.toUnifiedAnime(): UnifiedAnime = UnifiedAnime(
    source = DataSource.MEDIA_EXTENSION,
    watchableId = WatchableId.Unlinked(sourceId, mediaUrl, titleHash(title)),
    title = name,
    // ... map fields
)
```

**The key:** `MediaContent` maps into the existing `UnifiedAnime`. The details page, watch page, and download flow consume `UnifiedAnime` — they don't know or care that it came from a CloudStream extension.

---

## 6. The `MediaResolverStrategy` (CloudStream → ResolverServer/ResolverVideo)

```kotlin
// :core:video-resolver (or :feature:video-resolver)
class MediaResolverStrategy {
    fun resolve(mediaSource: MediaExtensionSource, episode: MediaEpisode): List<ResolverServer> {
        val videos = mediaSource.getVideos(episode)
        return videos.map { video ->
            ResolverServer(
                name = video.serverName,
                videos = listOf(ResolverVideo(
                    url = video.url,
                    quality = video.quality,
                    // ...
                ))
            )
        }
    }
}
```

**The key:** `ResolverServer` + `ResolverVideo` are the unified types that `WatchScreen` + `DownloadOrchestrator` consume. A CloudStream video maps into them just like an Aniyomi video does.

---

## 7. `SourceMatcher` generalization

Today, `SourceMatcher` (Doc 05 §5.1) matches Aniyomi `SAnime` to AniList anime via title normalization + Levenshtein.

**Two options:**
1. **Generalize:** Make `SourceMatcher` work on `UnifiedAnime` (or just title strings) instead of `SAnime`. Both Aniyomi and CloudStream matchers use it.
2. **Parallel:** Keep `SourceMatcher` Aniyomi-only; add `MediaSourceMatcher` for CloudStream.

**Recommendation: Option 1 (generalize).** The matching logic (normalize title, Levenshtein, threshold) is format-agnostic. Extract it into a `TitleMatcher` utility that takes two title strings and returns a similarity score. Both `SourceMatcher` and `MediaSourceMatcher` use it.

---

## 8. The extensions-settings UI

Today, `ExtensionsSettingsScreen` (`:feature:extensions-settings`, 740 lines) is Aniyomi-only. To support multiple formats:

1. **Add a category toggle** (Anime / Manga / Media — per ADR-016's two-category pattern, extended).
2. **Iterate `List<ExtensionManager<*>>`** (Koin multi-binding) to show all installed extensions across formats, grouped by category.
3. **Each format has its own install/trust/repo flow** — but the UI is shared (3-category structure: Trusted Sources → Installed → Available).

**The 2-way Anime/Manga toggle** (per DESIGN_LANGUAGE §3) already exists for Aniyomi. Extend it to a 3-way Anime/Manga/Media toggle when CloudStream support lands.

---

## 9. The `DataSource` enum

```kotlin
// :core:common
enum class DataSource(val key: String) {
    ANILIST("al"),
    EXTENSION("ext"),           // Aniyomi-compatible
    MEDIA_EXTENSION("media"),   // CloudStream-compatible (NEW)
}
```

`UnifiedAnime.source` uses this. The details page source-switcher menu shows providers grouped by `DataSource`. `WatchableId.Unlinked` carries the `sourceId`, which is globally unique across formats (because it's `MD5(name/lang/versionId)` — the name includes the format implicitly).

---

## 10. Functional gaps to close (independent of multi-format support)

Doc 05 §8 identified gaps vs. Aniyomi. These should be closed regardless of the multi-format work:

| Gap | Priority | Notes |
|---|---|---|
| `SourceManager` + stub sources | Medium | Uninstalled extensions should show "Source no longer installed" gracefully |
| WebView / Cloudflare bypass | Medium | Some sources require it |
| Per-source preference screens (`ConfigurableAnimeSource`) | Medium | The interface exists; no UI |
| Install progress UI | Low | Currently silent |
| Drag-reorderable trusted sources | Low | Polish |
| Extensions-settings ViewModel | Low | Logic is inline in the composable |

These are orthogonal to the multi-format evolution and can be tackled in any order.

---

## 11. Migration / backward compatibility

**For existing Aniyomi extensions:** Zero impact. The `:core:source-api` contract is unchanged. Existing extensions load + work exactly as before. `AnimeExtensionManager` is unchanged (just implements the new generic interface).

**For existing source links:** `SourceLinkStore` + `ExtensionLinkStore` migrate to `WatchableId` keys (proposal 01). Aniyomi source IDs are stable (`MD5(name/lang/versionId)`), so the migration is a pure re-keying.

**For CloudStream extensions (when added):** They're a new category. Users install them via the extensions-settings screen (3-way toggle). They appear in search (as a third tab or merged). They produce `UnifiedAnime` with `DataSource.MEDIA_EXTENSION`. Everything downstream (details, watch, download, library, history) works without changes.

---

## 12. Phased rollout

**Phase 1 (foundational, no user-visible change):**
- Extract `ResolverService` logic from `:feature:video-resolver` into `:core:video-resolver` (removes 2 feature→feature violations from Doc 04).
- Introduce the generic `ExtensionManager<T>` interface. `AnimeExtensionManager` implements it.
- Generalize `SourceMatcher` into `TitleMatcher` + `SourceMatcher`.

**Phase 2 (CloudStream support — future, opt-in):**
- Create `:core:media-source-api` (CloudStream contract).
- Create `:data:media-extension` (loader + manager).
- Create `MediaDetailsProvider` + `MediaResolverStrategy`.
- Add the 3-way toggle to extensions-settings.

**Phase 3 (polish):**
- Close the functional gaps (§10).
- Add `SourceManager` stub sources.
- Add per-source preference screens.

---

## 13. Trade-offs accepted

1. **Two parallel extension stacks** instead of one unified contract. We accept the duplication because unification would break Aniyomi compatibility (ADR-029) or produce a lowest-common-denominator contract.

2. **The extensions-settings UI becomes more complex** (3-way toggle, multi-format list). We accept this because users who only use Aniyomi extensions see no change (the toggle defaults to Anime).

3. **`ExtensionManager<out T>` is generic**, which adds a small amount of type complexity. We accept this because it enables `List<ExtensionManager<*>>` multi-binding, which is what makes the UI multi-format.

4. **CloudStream support is deferred to Phase 2**, not part of the initial restructuring. We accept this because the AniList-coupling work (proposals 01-03) is higher-priority and the parallel-module approach means CloudStream can be added later without rework.

---

## 14. Conditions for revisiting

- If a third extension format emerges (beyond Aniyomi + CloudStream), evaluate whether the parallel-module approach scales or whether a unified contract becomes worth the compatibility risk.
- If CloudStream extensions prove more popular than Aniyomi ones, consider making CloudStream the default and Aniyomi the opt-in.
- If the `ExtensionManager<out T>` generic proves too cumbersome, replace with a non-generic interface + type checks.

---

## 15. Summary

**Recommendation:**
1. **Do NOT unify the extension contract.** Keep `:core:source-api` verbatim Aniyomi-compatible (ADR-029).
2. **Add a parallel `:core:media-source-api` + `:data:media-extension`** for CloudStream-style extensions (future, Phase 2).
3. **Introduce a generic `ExtensionManager<T>` interface** that both `AnimeExtensionManager` and `MediaExtensionManager` implement.
4. **Both formats map into the existing `UnifiedAnime` + `ResolverServer`/`ResolverVideo`** — no changes to `:core:common`, `:core:download`, `:core:backup`.
5. **Extract `ResolverService` logic into `:core:video-resolver`** (removes 2 module violations + enables reuse).
6. **Generalize `SourceMatcher` into `TitleMatcher`** (format-agnostic title matching).
7. **Close the functional gaps vs. Aniyomi** (SourceManager stubs, WebView, per-source prefs) independently.

**Why this approach:**
- The core is already clean (Doc 05 §4.3) — the parallel approach leverages that.
- Adding a format is additive (one new module pair + one provider class + one Koin line).
- Aniyomi compatibility is preserved (ADR-029).
- The unified types (`UnifiedAnime`, `ResolverServer`, `ResolverVideo`) are the integration point — proven by ADR-039.

**Driven by evidence:** Doc 05 (the core is clean; two abstraction layers exist; the contract leaks into 12 files but only in feature/app layers), Doc 04 (the 3 feature→feature violations are fixable by extracting `:core:video-resolver`).

---

*Related: `proposals/02_provider_abstraction.md` (providers are a parallel concept), `proposals/01_internal_id_system.md` (`WatchableId.Unlinked` handles unlinked extensions of any format).*
