# ADR-039 — Unified Anime Details Page via Pluggable Data Translation Layer

**Date:** 2026-07-27
**Status:** ACCEPTED
**Supersedes:** None (refines ADR-012's navigation scope; complements ADR-029's extension compat)
**Branch:** `feature/extension-details-page`

---

## Context

ANIKUTA previously had **two separate details screens**:

1. `AnimeDetailScreen` — rendered AniList data (`AniListAnime`). The primary
   path: user taps an anime from browse/search → AniList fetch → source match →
   episodes.
2. `ExtensionDetailScreen` — rendered extension data (`SAnime`) directly. A
   ~120-line copy-paste of `DetailBanner`/`GenresRow`/`ActionButton`. Used when
   a user opened an extension anime that wasn't linked to AniList ("go without
   linking").

**Problems with the two-screen approach:**

- **Inconsistent UX** — extension-sourced anime looked different from AniList-sourced anime.
- **Code duplication** — `ExtensionDetailScreen` copied `DetailBanner`/`GenresRow`/`ActionButton` with slight modifications.
- **Missing features on the extension screen** — no metadata enrichment, no source switching, no download buttons, no watched toggle, no category picker, no adaptive theming (hardcoded `coverColor = surfaceVariant`).
- **Library invisibility** — unlinked extension anime saved with `anilistId = null` were keyed by `sourceId + url`, but the library query only favored `anilistId`, making them invisible.
- **`getAnimeDetails` never called** — extension data was rendered from partial search results (the enrichment gap, doc 02 §4).
- **No in-place source switching** — Animiru's Migrate wizard (doc 03) was the only precedent, and it creates a new DB row (bad UX).

## Decision

**Adopt a pluggable data translation layer (the "provider" pattern) feeding ONE unified details page.**

### Architecture

```
AnimeDetailScreen (unified, source-agnostic)
        ↓ consumes
UnifiedAnime (nullable fields; UI conditionally hides null sections)
        ↑ produced by
AnimeDetailsProviderRegistry
        ↑ resolves by DataSource
   ┌────┴────┐
AniListDetailsProvider   ExtensionDetailsProvider
(:data:anime)            (:data:extension)
```

- **`AnimeDetailsProvider`** interface (in `:core:common`) — `suspend fun load(request: DetailsRequest): DetailsResult?`
- **`UnifiedAnime`** data class (in `:core:common`) — nullable fields for every datum; the UI hides sections whose fields are null (doc 04 Table 3).
- **Two implementations:**
  - `AniListDetailsProvider` (`:data:anime`) — wraps the existing 3-stage load (AniList fetch → source match → episode fetch + DB persist), maps `AniListAnime` → `UnifiedAnime`.
  - `ExtensionDetailsProvider` (`:data:extension`) — maps `SAnime` → `UnifiedAnime`, calls `getAnimeDetails` to enrich (closes the gap), Palette-extracts the cover color via OkHttp + `PaletteExtraction.extractFromBitmap` (Phase 9), optionally merges AniList metadata for linked anime.
- **Koin multi-binding** — `single<List<AnimeDetailsProvider>>` (the same pattern that fixed the "only 1 category saved" backup bug). A third data source = one new class + one Koin line, zero UI changes.
- **Three-dot menu** (`SourceSwitcherMenu`) — replaces the no-op stub at `DetailBanner.kt:116`. Switches `DataSource` in-place (AniList ↔ Extension) without navigation or a new DB row. Scroll position, library state, and watched flags survive (keyed on `anilistId` / `sourceId+url`, not on the displayed source).

### Library keying (owner requirement Q2)

- **Linked anime** (`anilistId != null`): library row keyed by `anilistId` (unchanged).
- **Unlinked extension anime** (`anilistId == null`): keyed by `sourceId + url` via `AnimeRepository.getBySourceAndUrl` (the columns already existed on the `animes` table — no schema change). This makes unlinked extension anime **visible in the library** (fixes the old `ExtensionDetailScreen` shortcoming).

### `ExtensionDetailScreen.kt` — REMOVED

The separate extension details screen is deleted entirely. Its useful logic (the "re-link to AniList" affordance) is preserved via:
- The three-dot menu's "View from AniList" option (when the anime is linked).
- The `ExtensionLinkingSheet` flow (when unlinked — accessible from search).

## Alternatives considered

1. **Keep two screens, fix `ExtensionDetailScreen`'s shortcomings individually.**
   - Rejected: perpetuates code duplication; doesn't solve the "no in-place source switching" UX problem; the owner explicitly wanted ONE unified page.

2. **Hardcoded adapters (no provider interface).**
   - Rejected: tightly couples the screen to AniList + extension specifics; adding a third data source (Kitsu, local API) would require screen surgery. The provider interface makes the architecture modular + future-proof (doc 05 §8).

3. **Voyager ScreenModel per data source.**
   - Rejected: would require separate Voyager screens per source → re-introduces the two-screen problem. The provider keeps ONE screen + ONE ViewModel.

4. **Bake `source: Long` into the anime row (Animiru's approach).**
   - Rejected (doc 03 §4): forces a Migrate-wizard flow to switch sources (creates a new DB row, orphans episodes/history). ANIKUTA keys the library on `anilistId` / `sourceId+url` instead, enabling in-place switching.

5. **New `:data:details` Gradle module for the providers.**
   - Rejected (owner direction Q1): providers placed in existing modules (`:data:anime` + `:data:extension`) with acyclic dep additions. No new Gradle module.

## Consequences

**Positive:**
- ONE details page serves all data sources — consistent UX.
- In-place source switching via the three-dot menu (no Migrate wizard).
- `getAnimeDetails` enrichment closes the partial-data gap.
- Linked extension anime get the best of both sources (extension episodes + AniList metadata).
- Unlinked extension anime are visible in the library.
- Phase 9 adaptive theming works for both sources (Palette extraction for extension covers).
- Future-extensible: a third data source = one provider class + one Koin line.

**Negative:**
- `:data:anime` + `:data:extension` gained new module deps (`:core:anilist`, `:core:episode-metadata`, `:core:source-api`, `:core:designsystem`). Acyclic but heavier.
- `AnimeDetailViewModel` was substantially rewritten (Risk R7) — requires careful regression testing on AniList-linked anime.
- `getAnimeDetails` is now called on every extension-anime open (Risk R1) — mitigated with try/catch + fallback to the un-enriched SAnime.
- Two AniList API instances existed before (NavModule + UpdateCheckerModule); this ADR's providers resolve the single NavModule instance via Koin. Consolidation is correct but means all consumers now share one cache + rate limiter (already the case post-Phase-9 nav migration).

## Compliance

- **ADR-029 (Aniyomi extension compat)** — preserved. The `ExtensionDetailsProvider` uses the standard `AnimeCatalogueSource.getAnimeDetails` / `getEpisodeList` contract.
- **ADR-024 (SQLDelight + status columns)** — preserved. Episode persistence unchanged; the `animes` table's existing `source_id` + `url` columns are used for unlinked keying (no schema change).
- **ADR-023 (Koin DI)** — the provider registry uses the established `List<T>` multi-binding pattern.
- **Phase 9 (adaptive theming)** — preserved. The `MaterialTheme(dynamicScheme)` block in `AnimeDetailScreen` is untouched; `UnifiedAnime.coverColorHex` flows from AniList's `coverImage.color` (AniList mode) or Palette extraction (extension mode).
- **`RULES/ai-agent-rules.md` §3 (data flow)** — the provider is a repository-layer component (interface in `:core`, impls in `:data`); the ViewModel consumes it; the UI consumes `UnifiedAnime`. No layer violations.

## References

- `05-IMPLEMENTATION_PLAN.md` — the full plan (approved by owner 2026-07-27).
- `04-data-model-mapping.md` — the `UnifiedAnime` field contract + visibility matrix.
- `03-animiru-details-page-analysis.md` — Animiru reference (validated unified page, negative reference for source switching).
- `02-anikuta-extension-system-analysis.md` — the `getAnimeDetails` gap + `SAnime` field table.
- `01-anikuta-details-page-analysis.md` — the pre-refactor architecture.

---

*Implementation: `feature/extension-details-page` branch. See worklog `EXT-DETAILS-IMPL` entry for the step-by-step commit log.*
