# 05 — Implementation Plan: Unified Extension Details Page

> **STATUS: PENDING OWNER APPROVAL. Do NOT start implementation.**
>
> This plan is the synthesis of docs 01–04. It defines the architecture, file-level
> impact, ordered steps, risks, and Phase 9 integration for the **pluggable data
> translation layer** that lets the unified `AnimeDetailScreen` render data from
> either AniList OR an extension, with a three-dot menu to switch sources in-place.
>
> `ExtensionDetailScreen.kt` will be **removed** once the unified page handles
> extension data.

---

## 0. Executive summary

**The vision:** ONE details page (`AnimeDetailScreen`) serves all data sources. A new
**`AnimeDetailsProvider`** abstraction (the "translation layer") produces a
`UnifiedAnime` value + `List<Episode>` from either AniList or an extension. The screen
reads `UnifiedAnime` and renders sections conditionally based on which fields are
non-null (doc 04 Table 3). A three-dot menu toggles `dataSource` and re-queries the
provider — in-place, no navigation, no new DB row.

**Why it works:** Animiru (doc 03) already proves the unified-page pattern works with
a ~25-line `SAnime→Anime` mapper and hide-if-empty rendering. ANIKUTA's novelty is
making AniList a **first-class data source** in the same abstraction (Animiru treats
AniList/MAL as secondary `Track` bindings) and adding **in-place source switching**
(Animiru cannot — its `source: Long` is baked into the anime row).

**Scope of THIS plan:** the translation layer + unified page wiring + three-dot menu +
extension switching + `ExtensionDetailScreen` removal. **Out of scope:**
recommendations/relations sections, notifications, manga reader, new Gradle modules
beyond what's strictly needed.

**Estimated effort:** ~8–12 implementation steps (§5), each independently reviewable.

---

## 1. Architecture design

### 1.1 The translation layer — `AnimeDetailsProvider`

A new **interface** in `:core:common` (so both `:feature:anime-details` and any future
consumer can depend on it without circular deps):

```kotlin
// core/common/src/main/java/app/confused/anikuta/core/common/details/
//   AnimeDetailsProvider.kt  (NEW)
package app.confused.anikuta.core.common.details

interface AnimeDetailsProvider {
    /** Which data source this provider serves. */
    val dataSource: DataSource

    /**
     * Load the unified anime + episodes for the given [request].
     * Implementations MUST dispatch to Dispatchers.IO internally.
     * Returns null only if the anime cannot be resolved at all.
     */
    suspend fun load(request: DetailsRequest): DetailsResult?
}

sealed interface DetailsRequest {
    /** AniList-keyed lookup. */
    data class ByAniListId(val anilistId: Int) : DetailsRequest
    /** Extension-keyed lookup (the SAnime came from search or a saved link). */
    data class ByExtension(val sourceId: Long, val sAnime: SAnime, val anilistId: Int? = null) : DetailsRequest
}

data class DetailsResult(
    val anime: UnifiedAnime,
    val episodes: List<Episode>,        // existing domain model
    val episodeMetadata: Map<Int, EpisodeMetadata>,  // optional enrichment
)

enum class DataSource { ANILIST, EXTENSION }
```

Two implementations:

#### `AniListDetailsProvider` (in `:core:anilist` or `:data:anime` — TBD in step 1)
- `dataSource = ANILIST`
- `load(ByAniListId(id))` → `AniListApi.fetchById(id)` → map `AniListAnime` → `UnifiedAnime` (mapper in §1.2).
- Then runs the **existing** Stage-3 episode flow (`SourceMatcher.matchAll(title)` → `source.getEpisodeList(sAnime)`) — this is just refactoring the current `AnimeDetailViewModel` logic into the provider.
- `load(ByExtension(sourceId, sAnime, anilistId))` → if `anilistId != null`, behave as `ByAniListId`; else return null (AniList provider can't serve pure-extension).

#### `ExtensionDetailsProvider` (in `:data:extension` or a new `:data:details` — TBD in step 1)
- `dataSource = EXTENSION`
- `load(ByExtension(sourceId, sAnime, anilistId))`:
  1. If `sAnime.initialized == false` → call `source.getAnimeDetails(sAnime)` to enrich (doc 04 §4 — the gap ANIKUTA has today).
  2. Map enriched `SAnime` → `UnifiedAnime` (mapper in §1.2).
  3. If `anilistId != null` → also fetch `AniListApi.fetchById(anilistId)` and merge AniList-only fields (score, format, season, studios, nextAiring, recommendations) into `UnifiedAnime`. This gives linked extension anime the BEST of both sources.
  4. Fetch episodes via `source.getEpisodeList(sAnime)`.
  5. If `anilistId != null` → run `EpisodeMetadataRepository.fetchAll(...)` for per-episode enrichment.
- `load(ByAniListId(id))` → reverse-lookup via `ExtensionLinkStore.getPreferredSourceForAnilist(id)` + `SourceLinkStore.getLink(id)` → reconstruct `SAnime` → call the ByExtension path.

### 1.2 The mappers (small, focused — Animiru-style)

```kotlin
// core/common/src/main/java/app/confused/anikuta/core/common/details/
//   AniListAnimeMapper.kt  (NEW)  — ~40 lines
fun AniListAnime.toUnifiedAnime(): UnifiedAnime  // direct field copy + status enum map

//   SAnimeMapper.kt  (NEW)  — ~35 lines
fun SAnime.toUnifiedAnime(sourceId: Long, sourceName: String, anilistId: Int?, malId: Int?): UnifiedAnime
// status Int → UnifiedStatus (doc 04 §3); genres via getGenres(); coverColorHex via Palette (caller-supplied)
```

Plus the existing `EpisodeMapper` (`data/anime/.../EpisodeMapper.kt`) for `SEpisode → Episode` — **no change**.

### 1.3 The `AnimeDetailsProviderRegistry` (Koin multi-binding, like BackupProvider)

Pattern copied from `BackupModule`'s `single<List<BackupProvider>>` (doc: root cause
fix for the "only 1 category saved" Koin collision — ANALYSIS-B in worklog):

```kotlin
// core/common/src/main/java/app/confused/anikuta/core/common/details/
//   AnimeDetailsProviderRegistry.kt  (NEW)
class AnimeDetailsProviderRegistry(private val providers: List<AnimeDetailsProvider>) {
    fun forSource(dataSource: DataSource): AnimeDetailsProvider =
        providers.first { it.dataSource == dataSource }
}
```

Koin binding (`app/.../di/DetailsModule.kt` NEW):
```kotlin
single<List<AnimeDetailsProvider>> { listOf(get<AniListDetailsProvider>(), get<ExtensionDetailsProvider>()) }
single { AnimeDetailsProviderRegistry(get()) }
single { AniListDetailsProvider(get(), get(), get(), get()) }   // deps TBD
single { ExtensionDetailsProvider(get(), get(), get(), get(), get()) }
```

### 1.4 How `AnimeDetailViewModel` changes

Today the VM has a 3-stage load inline (doc 01 §2). The refactor:

- Replace the inline 3 stages with: `val provider = registry.forSource(currentDataSource); provider.load(request)`.
- Add a `currentDataSource: StateFlow<DataSource>` (default `ANILIST` if `anilistId != null`, else `EXTENSION`).
- Add `switchDataSource(target: DataSource)` — sets the state, re-queries the provider, preserves scroll position.
- The `UnifiedAnime` becomes the VM's primary state (replaces the current `AniListAnime` + ad-hoc SAnime merging).
- Episodes + episode metadata flow unchanged (they already come from the provider's `DetailsResult`).

**Key invariant:** the VM never calls `AniListApi` or `source.getEpisodeList` directly anymore — only through the provider. This is the architectural boundary that makes the page truly source-agnostic.

### 1.5 How the three-dot menu switching works

```
User taps three-dot (DetailBanner.kt:116 — currently a no-op stub)
  → DropdownMenu shows:
      "View from AniList"     (enabled if anilistId != null && currentDataSource == EXTENSION)
      "View from Extension"   (enabled if sourceId != null && currentDataSource == ANILIST)
      "Switch extension..."   (enabled if currentDataSource == EXTENSION — opens ManualSearchSheet)
      "Refresh"               (existing pull-to-refresh logic)
      "Share" / "Web search"  (existing actions, moved into the menu)
  → User picks "View from Extension"
  → VM.switchDataSource(EXTENSION)
  → VM sets loading state, calls registry.forSource(EXTENSION).load(request)
  → New UnifiedAnime emitted → screen re-renders with extension data
  → Sections that lack data (score, format, season, ...) collapse (doc 04 Table 3)
```

**State preservation:** scroll position, library/favorite state, and watched-episode
flags are all keyed by `anilistId` (or `sourceId+url` for unlinked) — none of these
change when switching the *displayed* data source. Only the *rendered metadata*
changes. This is the core UX win over Animiru's Migrate wizard.

### 1.6 How extension switching from the details page works

"Switch extension" opens the **existing** `ManualSearchSheet` (doc 01 §6 — already
implemented for AniList mode). The sheet shows `SourceMatcher.matchAll(title)` results
across installed extensions. User picks a different source → `SourceLinkStore.saveLink`
updates the preferred source → VM re-queries `ExtensionDetailsProvider` with the new
`sourceId + sAnime` → episodes refresh.

> This is mostly already built. The work is routing the ManualSearchSheet result back
> through the provider instead of the current direct `source.getEpisodeList` call.

---

## 2. Files to MODIFY (with WHY)

| # | File | Why | Change |
|---|---|---|---|
| M1 | `feature/anime-details/.../AnimeDetailScreen.kt` | Consume `UnifiedAnime` instead of `AniListAnime`; add three-dot menu | Replace `AniListAnime` param with `UnifiedAnime`; wire `DropdownMenu` at the existing three-dot stub (line 116); pass `currentDataSource` + `onSwitchDataSource` callback down. Preserve Phase 9 adaptive-theme block (lines 88-94, 138-160, 207-212). |
| M2 | `feature/anime-details/.../DetailBanner.kt` | Render from `UnifiedAnime`; show/hide status/score/format/season chips per doc 04 Table 3 | Replace field reads; add conditional `if (anime.averageScore != null)` etc. Wire the three-dot `IconButton` (line 116) to the new menu. |
| M3 | `feature/anime-details/.../DetailContent.kt` | Render `SynopsisSection`/`GenresRow`/`InfoSection` from `UnifiedAnime` | Field source swaps; add author/artist row (extension-only bonus). |
| M4 | `feature/anime-details/.../AnimeDetailViewModel.kt` | Replace inline 3-stage load with provider call; add `currentDataSource` + `switchDataSource` | Largest change. Keep the DB-persistence + offline-short-circuit logic (move into `ExtensionDetailsProvider`). Remove the now-unused `extensionManager` injection (doc 01 — dead weight). |
| M5 | `feature/anime-details/.../EpisodesSection.kt` | Minimal — episodes already come from `DetailsResult.episodes` | Verify no `AniListAnime` references remain. |
| M6 | `feature/anime-details/.../SourceSwitcherDialog.kt` | **Either wire it in or delete it** (doc 01: dead code, `internal`, never invoked) | Decision in step 5. If the three-dot menu subsumes it, delete. |
| M7 | `feature/anime-details/.../ManualSearchSheet.kt` | Route result through provider instead of direct `source.getEpisodeList` | Callback signature change. |
| M8 | `app/.../navigation/Destinations.kt` | `ExtensionDetailDestination` becomes unreachable after `ExtensionDetailScreen` removal; `AnimeDetailDestination` may need a `dataSource` param | Add optional `initialDataSource: DataSource = ANILIST` to `AnimeDetailDestination`. Mark `ExtensionDetailDestination` deprecated (remove in step 8). |
| M9 | `app/.../navigation/AppController.kt` | The "go without linking" path (doc 01 §5) currently pushes `ExtensionDetailDestination`; repoint to `AnimeDetailDestination` with `initialDataSource = EXTENSION` | One callback change. |
| M10 | `app/.../di/` (new `DetailsModule.kt` or extend `RepositoryModule.kt`) | Register the provider registry + 2 impls | Koin multi-binding (§1.3). |
| M11 | `feature/search/.../ExtensionLinkingSheet.kt` + `ExtensionLinkingViewModel.kt` | The "go without linking" callback (doc 01 §5) changes target | Update `onGoWithoutLinking` to push `AnimeDetailDestination(source, sAnime, initialDataSource = EXTENSION)`. |
| M12 | `feature/anime-details/build.gradle.kts` | Add dep on `:core:common` (for `AnimeDetailsProvider`) if not already present; confirm `:core:anilist` / `:data:extension` deps | Build config. |

> **No Phase 9 files are modified.** `PaletteExtraction.kt`, `ThemePreferences`,
> `AppearanceScreen`, and the adaptive-theme block in `AnimeDetailScreen.kt` are
> preserved as-is. The only touch to the theme block is ensuring `coverColorHex`
> flows from `UnifiedAnime` (which the `ExtensionDetailsProvider` populates via
> Palette) instead of `AniListAnime.coverColorHex` directly.

---

## 3. Files to CREATE (with PURPOSE)

| # | File | Purpose | Approx LOC |
|---|---|---|---|
| C1 | `core/common/.../details/UnifiedAnime.kt` | The unified data model (doc 04 §0) | ~50 |
| C2 | `core/common/.../details/DataSource.kt` | `DataSource` enum + `DetailsRequest` sealed interface + `DetailsResult` | ~30 |
| C3 | `core/common/.../details/AnimeDetailsProvider.kt` | The provider interface | ~25 |
| C4 | `core/common/.../details/AnimeDetailsProviderRegistry.kt` | Koin multi-binding registry | ~20 |
| C5 | `core/common/.../details/AniListAnimeMapper.kt` | `AniListAnime.toUnifiedAnime()` | ~40 |
| C6 | `core/common/.../details/SAnimeMapper.kt` | `SAnime.toUnifiedAnime(...)` + status Int→enum (doc 04 §3) | ~45 |
| C7 | `core/common/.../details/HtmlToPlainText.kt` | HTML→plain-text normalizer (doc 04 §2) — IF not already present | ~30 |
| C8 | `:core:anilist` or `:data:anime` — `AniListDetailsProvider.kt` | ANILIST provider impl (§1.1) | ~120 |
| C9 | `:data:extension` or new `:data:details` — `ExtensionDetailsProvider.kt` | EXTENSION provider impl (§1.1) incl. `getAnimeDetails` enrichment + optional AniList merge | ~180 |
| C10 | `app/.../di/DetailsModule.kt` | Koin wiring (§1.3) | ~25 |
| C11 | `feature/anime-details/.../SourceSwitcherMenu.kt` | The three-dot `DropdownMenu` composable (§1.5) | ~80 |

> **Module placement decision (step 1 of §5):** `AniListDetailsProvider` needs
> `AniListApi` (`:core:anilist`) + `SourceMatcher`/`AnimeExtensionManager`
> (`:data:extension`) + `EpisodeRepository` (`:data:anime`) + `EpisodeMetadataRepository`
> (`:core:episode-metadata`). To avoid a new module, place it in `:data:anime` (which
> already depends on `:core:anilist` transitively). `ExtensionDetailsProvider` similarly
> in `:data:extension`. **Confirm dependency graph in step 1 before writing code.**
> If circular deps arise, create a small `:data:details` module — but try to avoid a
> new module first (per the task constraint "document the need, don't create").

---

## 4. Files to REMOVE

| # | File | When | Why |
|---|---|---|---|
| R1 | `feature/anime-details/.../ExtensionDetailScreen.kt` | Step 8 (final), after the unified page handles extension data end-to-end | Replaced by unified page. |
| R2 | `feature/anime-details/.../ExtensionDetailViewModel.kt` (if exists) | Step 8 | Same. |
| R3 | `feature/anime-details/.../SourceSwitcherDialog.kt` | Step 5, IF the three-dot menu subsumes it | Dead code today (doc 01 §6). |
| R4 | `app/.../navigation/Destinations.kt` — `ExtensionDetailDestination` | Step 8 | Unreachable after R1. |

> **Do NOT remove in this planning phase.** Removals happen in the implementation
> steps, each gated on the unified page proving it covers the removed functionality.

---

## 5. Implementation steps (ordered, with dependencies)

> Each step is independently reviewable. Steps must be done in order. CI must pass
> between steps (per project rules — CI-only builds, ADR-003).

### Step 1 — Module placement + dependency verification (NO code)
- Confirm whether `AniListDetailsProvider` can live in `:data:anime` without circular
  deps (check `:data:anime/build.gradle.kts` deps on `:core:anilist`,
  `:data:extension`, `:core:episode-metadata`).
- If circular → decide: add deps to `:data:anime`, OR create `:data:details`.
- **Deliverable:** a one-paragraph decision recorded in the worklog. No code yet.
- **Prerequisite for:** C8, C9.

### Step 2 — The unified data model (C1, C2, C3, C4, C5, C6, C7)
- Create the `UnifiedAnime`, `DataSource`, `DetailsRequest`, `DetailsResult`,
  `AnimeDetailsProvider`, `AnimeDetailsProviderRegistry`, the two mappers, and
  `HtmlToPlainText` (if needed).
- All in `:core:common` (mappers may need to import `SAnime` from `:core:source-api`
  and `AniListAnime` from `:core:anilist` — confirm `:core:common` can depend on both,
  else move mappers to the provider impls).
- **Unit-test the mappers** (status Int→enum, genre splitting, HTML stripping) — these
  are pure functions, easy to test.
- **Prerequisite for:** Step 3.

### Step 3 — The two provider implementations (C8, C9)
- `AniListDetailsProvider`: refactor the existing `AnimeDetailViewModel` Stage-1
  (AniList fetch) + Stage-3 (source match + episode fetch) into `load()`.
- `ExtensionDetailsProvider`: implement §1.1 — incl. the NEW `getAnimeDetails`
  enrichment call (doc 04 §4) and the optional AniList merge for linked anime.
- **No UI changes yet** — providers are standalone, testable via unit tests with mock
  sources.
- **Prerequisite for:** Step 4.

### Step 4 — Koin wiring (C10, M10)
- Create `DetailsModule.kt` with the `List<AnimeDetailsProvider>` multi-binding
  (§1.3). Add to `App.kt` `startKoin { modules(...) }`.
- **Prerequisite for:** Step 5.

### Step 5 — `AnimeDetailViewModel` refactor (M4) + three-dot menu (C11, M1, M2)
- Replace the VM's inline 3-stage load with `registry.forSource(currentDataSource).load(request)`.
- Add `currentDataSource: StateFlow<DataSource>` + `switchDataSource(target)`.
- Wire the three-dot `DropdownMenu` (C11) at `DetailBanner.kt:116`.
- Replace `AniListAnime` reads in `DetailBanner`/`DetailContent` with `UnifiedAnime`
  reads + conditional rendering per doc 04 Table 3.
- **Decision point:** wire or delete `SourceSwitcherDialog.kt` (M6/R3).
- **At this point the unified page works for BOTH data sources.** `ExtensionDetailScreen`
  still exists but is now redundant.
- **Prerequisite for:** Step 6.

### Step 6 — Extension switching from the details page (M7)
- Route `ManualSearchSheet` results through the provider (§1.6).
- Verify switching extensions refreshes episodes in-place.
- **Prerequisite for:** Step 7.

### Step 7 — Navigation repoint (M8, M9, M11)
- Add `initialDataSource` param to `AnimeDetailDestination`.
- Repoint `ExtensionLinkingSheet` "go without linking" + any other
  `ExtensionDetailDestination` push sites to `AnimeDetailDestination(initialDataSource = EXTENSION)`.
- **Prerequisite for:** Step 8.

### Step 8 — Remove `ExtensionDetailScreen` (R1, R2, R4)
- Delete `ExtensionDetailScreen.kt`, its VM (if any), and `ExtensionDetailDestination`.
- Verify no remaining references (grep).
- **Final verification:** open an unlinked extension anime → it renders in the unified
  page with `dataSource = EXTENSION`; open a linked one → defaults to AniList, switch
  to Extension via menu; switch back.

### Step 9 — Phase 9 integration verification (no code, just testing)
- Confirm `coverColorHex` flows into `UnifiedAnime` for BOTH modes:
  - AniList: from `AniListAnime.coverColorHex` (existing).
  - Extension: from `PaletteExtraction.extractCoverColor(coverUrl)` (Phase 9).
- Confirm the `MaterialTheme(dynamicScheme)` block in `AnimeDetailScreen.kt:207-212`
  still wraps the screen when `coverColorHex != null`.
- **If `PaletteExtraction` API doesn't match what the provider needs** (doc 04 §7 Q4),
  coordinate with the Phase 9 agent — do NOT modify Phase 9 files unilaterally.

### Step 10 — Documentation update
- Update `START_HERE.md` "What's done" to note `ExtensionDetailScreen` removal + unified page.
- Update `ARCHITECTURE.md` §4 (data flow) to mention `AnimeDetailsProvider`.
- Add an ADR for the translation-layer decision (per `RULES/project-conventions.md` §6).
- Write a session handoff note in `RULES/sessions/` (per §5 — the project has a
  handoff-note deficit; don't perpetuate it).

---

## 6. Risk assessment

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|:---:|:---:|---|
| R1 | **`getAnimeDetails` enrichment breaks extensions** (some extensions may not implement it correctly, return partial data, or hang) | Medium | High | Wrap in try/catch + 30s timeout (matching `ResolverService` pattern); fall back to the un-enriched SAnime. Log via `DownloadLogger`-style tag. |
| R2 | **Circular Gradle deps** prevent placing providers in `:data:anime` / `:data:extension` | Medium | Medium | Step 1 verifies first; fallback is a new `:data:details` module (documented, not created in this plan). |
| R3 | **Library/history invisibility for unlinked extension anime** persists (doc 01 §8 shortcoming #3) | High | Medium | Address in Step 5 by ensuring `UnifiedAnime` carries a stable library key (anilistId OR sourceId+url) and the library query handles both. May need a `SourceLinkStore` index change (doc 04 §6 Q2). |
| R4 | **Phase 9 `PaletteExtraction` API mismatch** — provider can't call it cleanly | Medium | Medium | Step 9 flags it; coordinate with Phase 9 agent. Do NOT modify Phase 9 files. |
| R5 | **Performance regression** — provider indirection adds a layer; `getAnimeDetails` adds a network call | Low | Medium | Provider calls are on `Dispatchers.IO`; `getAnimeDetails` result cached via existing DB persistence. Measure cold-open time before/after. |
| R6 | **Three-dot menu UX confusion** — users may not understand "View from AniList" vs "View from Extension" | Low | Low | Add a one-time tooltip or a subtitle in the menu ("Switch data source"). UX detail, not a blocker. |
| R7 | **Regression in existing AniList-mode behavior** — the refactor could break the working 3-stage load | Medium | High | Step 5 must be carefully tested: open a linked anime → verify AniList data renders identically to pre-refactor. Keep the old VM code in git history for diffing. |
| R8 | **`EpisodeMetadataRepository` called with null anilistId/malId** in pure-extension mode | Low | Low | Already handled by `supports()` (doc 02). Verify no crash. |
| R9 | **Status enum mismatch** — extension `ON_HIATUS`/`LICENSED` collapse to `UNKNOWN` may surprise users | Low | Low | Doc 04 §3 Q1 — acceptable for v1; revisit if reported. |
| R10 | **Removal of `ExtensionDetailScreen` breaks deep links or bookmarks** | Low | Medium | Grep for any `ExtensionDetailDestination` references beyond the known push sites (doc 01 §5). Step 7 covers known sites; verify no others. |

### Trickiest parts (need careful testing)
1. **The `AnimeDetailViewModel` refactor (Step 5)** — it's the largest change and
   touches the working 3-stage load. Risk R7. Test: open 5 linked + 2 unlinked anime,
   verify no regression.
2. **The `getAnimeDetails` enrichment (Step 3)** — first time ANIKUTA calls this
   extension method. Risk R1. Test: try 3 different extensions; verify enrichment
   doesn't hang or crash.
3. **In-place source switching (Step 5)** — the UX centerpiece. Verify scroll
   position, library state, and watched flags survive a switch.
4. **Library keying for unlinked anime (Risk R3)** — if not solved, unlinked
   extension anime saved from the unified page still won't appear in the library.
   This is the one pre-existing shortcoming that the refactor SHOULD fix but might
   defer.

---

## 7. Integration with Phase 9 (adaptive colors)

Phase 9 lives on `feature/voyager-navigation` (our branch base). It adds:

| Phase 9 artifact | Location | How this plan interacts |
|---|---|---|
| `PaletteExtraction.kt` | `core/designsystem/.../PaletteExtraction.kt` | `ExtensionDetailsProvider` calls `extractCoverColor(coverUrl)` to populate `UnifiedAnime.coverColorHex` when AniList's `coverColorHex` is absent. **No modification to PaletteExtraction.** |
| `ThemePreferences` + `AppearanceScreen` | `:core:preferences` + `:feature:settings` | Unaffected. The unified page reads theme prefs the same way `AnimeDetailScreen` does today. |
| Adaptive-theme block | `AnimeDetailScreen.kt:88-94, 138-160, 207-212` | **Preserved verbatim.** The only change is the source of `coverColorHex`: from `AniListAnime.coverColorHex` to `UnifiedAnime.coverColorHex` (which the provider populates from either AniList or Palette). |
| `generateDynamicScheme(coverColorArgb, darkTheme, amoled)` | `core/designsystem/.../CoverColor.kt` (existing) | Unaffected — called with the same arg type. |

**Open question to confirm with the Phase 9 agent (doc 04 §7 Q4):**
Does `PaletteExtraction.extractCoverColor(url)` accept a URL (and load the bitmap
internally) or a pre-loaded `Bitmap`? The `ExtensionDetailsProvider` has Coil access
(`:data:extension` depends on Coil); `:core:designsystem` does NOT (commit `4cd3e66`
removed it). If `PaletteExtraction` takes a `Bitmap`, the provider loads the cover via
Coil and passes the bitmap. If it takes a URL, the provider just passes the URL. **Do
NOT assume — ask the Phase 9 agent before Step 9.**

**Non-conflict guarantee:** This plan touches `AnimeDetailScreen.kt`,
`AnimeDetailViewModel.kt`, `DetailBanner.kt`, `DetailContent.kt`, and the navigation
files. Phase 9 touches `PaletteExtraction.kt`, `ThemePreferences`, `AppearanceScreen`,
and the adaptive-theme *block* within `AnimeDetailScreen.kt`. The overlap is
`AnimeDetailScreen.kt` — both plans edit it. **Coordinate sequencing:** either this
plan's Step 5 lands before Phase 9's theme-block changes, or after. Recommend: this
plan rebases onto the latest `feature/voyager-navigation` before Step 5 and preserves
the theme block as-is.

---

## 8. Future extensibility (proving the architecture is modular)

**Adding a third data source** (e.g., a hypothetical `LocalMetadataApi` or a `KitsuSource`):

1. Create `KitsuDetailsProvider : AnimeDetailsProvider` with `dataSource = KITSU` (add to the `DataSource` enum).
2. Add a mapper `KitsuAnime.toUnifiedAnime()`.
3. Register in `DetailsModule`'s `listOf(...)` — one line.
4. Add a menu entry "View from Kitsu" in `SourceSwitcherMenu` (conditional on availability).

**Zero changes** to `AnimeDetailScreen`, `DetailBanner`, `DetailContent`,
`AnimeDetailViewModel` (beyond the enum addition). The screen stays source-agnostic.
This is the core modularity proof.

**Other extensibility wins:**
- A "debug" provider that synthesizes test data — useful for UI development without network.
- A "cached/offline" provider wrapper that serves from SQLDelight first, network second — could replace the current DB-first short-circuit in `AnimeDetailViewModel`.
- Per-source preferences (e.g., "prefer extension X for this anime") become a property of the registry, not the VM.

---

## 9. Open questions for the owner (decide before implementation)

1. **Module placement (Step 1):** OK to add `AniListDetailsProvider` to `:data:anime`
   and `ExtensionDetailsProvider` to `:data:extension`, or do you prefer a dedicated
   `:data:details` module? (§3 note, §5 Step 1)
2. **Unlinked extension anime in library (Risk R3):** should this plan ALSO fix the
   "unlinked anime invisible in library" shortcoming (doc 01 §8 #3), or defer it to a
   follow-up? Fixing it likely needs a `SourceLinkStore` index change (doc 04 §6 Q2).
3. **`SourceSwitcherDialog.kt` (M6/R3):** delete it (subsume into the three-dot menu),
   or keep it as a separate full-screen picker? (§5 Step 5)
4. **`UnifiedStatus` granularity (doc 04 §3 Q1):** collapse `ON_HIATUS`/`LICENSED` to
   `UNKNOWN` (recommended), or add them?
5. **Recommendations/Relations sections (doc 04 §8 Q6):** in scope for the unified
   page, or deferred? (Current `AnimeDetailScreen` doesn't render them — this plan
   matches current behavior.)
6. **Phase 9 `PaletteExtraction` API (§7):** can you confirm whether it takes a URL or
   a Bitmap? (Or should I ask the Phase 9 agent directly?)
7. **ADR:** should I write the ADR for the translation-layer decision as part of Step
   10, or do you want it drafted now (before implementation) for review?

---

## 10. Summary — what to expect after implementation

**Before:** `AnimeDetailScreen` (AniList only) + `ExtensionDetailScreen` (extension
only, separate, imperfect, ~120-line copy-paste, no metadata enrichment, no source
switching, no adaptive theming, unlinked anime invisible in library).

**After:** ONE `AnimeDetailScreen` that:
- Renders AniList OR extension data via `UnifiedAnime`.
- Switches sources in-place via the three-dot menu (no navigation, no new DB row).
- Switches extensions in-place via `ManualSearchSheet`.
- Calls `getAnimeDetails` to enrich extension data (NEW capability).
- Merges AniList metadata into linked extension anime (best of both).
- Preserves Phase 9 adaptive theming for both sources (via Palette for extension covers).
- Removes `ExtensionDetailScreen.kt` entirely.

**The architecture is modular:** a third data source = one new provider class + one
mapper + one Koin line + one menu entry. Zero UI changes.

---

*End of plan. **Awaiting owner approval before any implementation.***
