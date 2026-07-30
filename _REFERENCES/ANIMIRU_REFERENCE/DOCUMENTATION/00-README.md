# 00 — README: Extension Details Page Analysis & Plan

> **⚠️ THIS PLAN IS PENDING OWNER APPROVAL. DO NOT START IMPLEMENTATION.**
>
> This folder contains the analysis and implementation plan for the **Unified
> Extension Details Page** — a pluggable data translation layer that lets the
> existing `AnimeDetailScreen` render data from either AniList OR an extension,
> with a three-dot menu to switch sources in-place. `ExtensionDetailScreen.kt`
> will be removed once the unified page handles extension data.

---

## What was analyzed

Three codebases were studied to produce this plan:

1. **ANIKUTA's details page** (`ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/`) — the
   unified page that will receive extension data. Read in full: `AnimeDetailScreen.kt`,
   `DetailBanner.kt`, `DetailContent.kt`, `AnimeDetailViewModel.kt`, `EpisodesSection.kt`,
   `SourceSwitcherDialog.kt`, `ManualSearchSheet.kt`, `ExtensionDetailScreen.kt`, plus
   the `:core:common` / `:core:anilist` / `:core:source-api` data models and the
   Voyager navigation (`Destinations.kt`, `AppController.kt`).

2. **ANIKUTA's extension system** (`:core:source-api`, `:data:extension`, `:data:anime`,
   `:feature:search`) — the `SAnime`/`SEpisode`/`AnimeSource` contract, the
   `SourceMatcher`, the two-way linking stores (`SourceLinkStore` +
   `ExtensionLinkStore`), the `ExtensionLinkingSheet` flow, and the `EpisodeMetadataRepository`.

3. **Animiru** (cloned into `./animiru-src/`, gitignored) — a Tachiyomi/Aniyomi-lineage
   app studied as a reference for how a mature codebase maps `SAnime` → its details
   page, whether it supports in-place source switching (it does NOT — it uses a Migrate
   wizard), and what patterns ANIKUTA should adopt or avoid.

The Animiru source is **local-only** (gitignored). Only this `DOCUMENTATION/` folder is
committed, following the same convention as the sibling `ANIYOMI_REFRENCE/` and
`OLD_ANIKUTA/` reference folders.

---

## Documentation index

| # | File | What it contains |
|---|---|---|
| 01 | `01-anikuta-details-page-analysis.md` | Deep analysis of ANIKUTA's current details page: data model (`AniListAnime`, 26 fields), the 3-stage ViewModel load, UI section breakdown (`DetailBanner`/`GenresRow`/`SynopsisSection`/`EpisodesSection`/`InfoSection`), all interactions, Voyager navigation, the no-op three-dot stub, `ExtensionDetailScreen.kt` shortcomings, and Phase 9 adaptive-theme integration points. |
| 02 | `02-anikuta-extension-system-analysis.md` | Deep analysis of ANIKUTA's extension system: complete `SAnime` (13 fields) + `SEpisode` (8 fields) field tables, the `AnimeHttpSource` API surface (incl. the **never-called** `getAnimeDetails` enrichment), the two linking stores' APIs, the `ExtensionLinkingSheet` flow, and a 30-row data-gap table (AniList has, extensions lack). |
| 03 | `03-animiru-details-page-analysis.md` | Deep analysis of Animiru: ONE unified details screen (`AnimeScreen`) keyed by `animeId`, the `SAnime.toDomainAnime()` mapper (~25 lines), the `og*` + `CustomAnimeInfo` overlay pattern, the Migrate wizard (negative reference — no in-place switching), and lessons for ANIKUTA. |
| 04 | `04-data-model-mapping.md` | The critical synthesis: the proposed `UnifiedAnime` data model, 3 mapping tables (AniList-vs-Extension field availability, per-field translation requirements, UI section visibility matrix), status enum mapping, HTML normalization, the `getAnimeDetails` gap, Phase 9 integration, and 6 open questions. |
| 05 | `05-IMPLEMENTATION_PLAN.md` | The implementation plan: the `AnimeDetailsProvider` translation-layer architecture, 12 files to modify, 11 files to create, 4 files to remove, 10 ordered steps, 10 risks, Phase 9 integration, future-extensibility proof, and 7 open questions for the owner. **Pending approval.** |

---

## Key architectural decisions in the plan

1. **ONE unified details page.** `AnimeDetailScreen` renders a `UnifiedAnime` value
   produced by a pluggable `AnimeDetailsProvider`. The screen is fully source-agnostic.
   `ExtensionDetailScreen.kt` is removed entirely.

2. **A `AnimeDetailsProvider` interface + 2 implementations** (`AniListDetailsProvider`
   + `ExtensionDetailsProvider`), registered via Koin multi-binding
   (`single<List<AnimeDetailsProvider>>` — the same pattern that fixed the
   "only 1 category saved" backup bug). A third data source = one new class + one mapper
   + one Koin line. Zero UI changes.

3. **In-place source switching via the three-dot menu** — "View from AniList" /
   "View from Extension" / "Switch extension". No navigation, no new DB row, scroll
   position + library state + watched flags preserved. This is the UX Animiru CANNOT
   do (its `source: Long` is baked into the anime row) — ANIKUTA avoids that by keying
   the library on `anilistId` (linked) or `sourceId+url` (unlinked), NOT on the source.

4. **Two small mappers** (Animiru-style, ~40 lines each): `AniListAnime.toUnifiedAnime()`
   + `SAnime.toUnifiedAnime()`. Plus the existing `EpisodeMapper` (unchanged).

5. **Close the `getAnimeDetails` gap.** ANIKUTA currently NEVER calls
   `AnimeHttpSource.getAnimeDetails(sAnime)` — extension data is rendered from partial
   search results. The `ExtensionDetailsProvider` will call it (with timeout + fallback)
   to enrich extension data on first open. Cached afterward via existing DB persistence.

6. **Merge AniList metadata into linked extension anime.** When an extension anime is
   linked to AniList, `ExtensionDetailsProvider` fetches BOTH and merges — extension
   provides episodes + author/artist + cover; AniList provides score/format/season/
   studios/next-airing. Best of both sources.

7. **Preserve Phase 9 adaptive theming.** The `MaterialTheme(dynamicScheme)` block in
   `AnimeDetailScreen.kt` is untouched. `UnifiedAnime.coverColorHex` flows from AniList
   (`coverImage.color`) in AniList mode, or from `PaletteExtraction.extractCoverColor()`
   in extension mode. One open question: confirm `PaletteExtraction`'s API (URL vs
   Bitmap) with the Phase 9 agent.

---

## Concerns / open questions (need owner input before implementation)

These are documented in detail in doc 05 §9 and doc 04 §8. Summary:

1. **Module placement** — `:data:anime` + `:data:extension`, or a new `:data:details`?
2. **Unlinked extension anime in library** — fix the existing invisibility shortcoming
   now, or defer? (Likely needs a `SourceLinkStore` index change.)
3. **`SourceSwitcherDialog.kt`** — delete (subsume into three-dot menu) or keep?
4. **`UnifiedStatus` granularity** — collapse `ON_HIATUS`/`LICENSED` to `UNKNOWN`?
5. **Recommendations/Relations sections** — in scope, or deferred?
6. **Phase 9 `PaletteExtraction` API** — URL or Bitmap? (Coordinate with Phase 9 agent.)
7. **ADR** — draft now (pre-implementation review) or in Step 10?

---

## Top risks (full table in doc 05 §6)

- **R7 (High impact):** The `AnimeDetailViewModel` refactor (Step 5) touches the
  working 3-stage load. Must carefully verify no regression on linked anime.
- **R1 (High impact):** `getAnimeDetails` enrichment may break on extensions that
  implement it poorly. Mitigate with 30s timeout + fallback to un-enriched SAnime.
- **R3 (Medium impact):** Unlinked extension anime may remain invisible in the library
  unless the plan also fixes the keying (open question #2).
- **R4 (Medium impact):** Phase 9 `PaletteExtraction` API mismatch — coordinate, don't
  modify Phase 9 files.

---

## How to review this plan

1. Read this `00-README.md` for the overview.
2. Skim `04-data-model-mapping.md` Tables 1 + 3 — these define the contract.
3. Read `05-IMPLEMENTATION_PLAN.md` §1 (architecture), §5 (steps), §9 (questions).
4. Dive into `01`/`02`/`03` for the evidence behind any claim you want to verify.

---

## ⚠️ Approval gate

**This plan is pending owner approval. Do NOT start implementation.**

Once approved, implementation will proceed step-by-step per doc 05 §5, with CI passing
between each step, ntfy.sh notifications on each task completion (ADR-008), and a
session handoff note at the end (per `RULES/project-conventions.md` §5).

Branch: `feature/extension-details-page` (branched from `feature/voyager-navigation`).
Base commit: `fa9ba8a` (latest on `feature/voyager-navigation` at branch creation).
Setup commit: `6cf3526` (Animiru reference folder structure).
Docs commit: see git log (this commit adds `00`–`05`).

---

*Prepared by: Z.ai orchestrator. Analysis-only task — no Kotlin source files were
modified in the production of this plan.*
