# ANIKUTA — Architecture Restructuring Plan: Summary

> **Read this first.** This document ties together the entire analysis + proposal + plan. Someone reading only this should understand the full picture; the other documents provide the details.

**Branch:** `docs/architecture-restructuring-plan` (do NOT merge to `main` — this is for review).
**Task:** Analysis, planning, and documentation only. No code was modified.

---

## 1. What this is

A comprehensive analysis of the ANIKUTA Android app's current architecture, identifying the AniList-coupling constraints that will become blockers as the app expands, and a detailed plan to restructure for provider-agnostic flexibility.

The work is organized into three phases:
- **Phase 1 — Current State Analysis** (6 documents): what the codebase does today, every claim backed by `file:line` references.
- **Phase 2 — Proposed Architecture** (5 documents): the future state, with reasoning + trade-offs.
- **Phase 3 — Implementation Plan** (4 documents): concrete, phased, shippable execution plan.

Plus this summary + an appendix.

---

## 2. The one-sentence headline

**ANIKUTA's database schema is already AniList-optional, but 7 cross-cutting preference stores are AniList-required by type — and fixing this is a medium-effort restructuring centered on a two-tier identity model: a per-source `local_id` (multi-component, with full provenance) + a per-content `content_id` (for grouping + source-switch survival).**

> 📌 **Refined (rev 2):** The identity model was refined from a single `WatchableId` sealed class (ADR-040) to a **two-tier `local_id` + `content_id` model** (ADR-050), per the owner's direction to store full source provenance (system, repo, extension, source-content-id, name) + external provider IDs. See `proposals/01a_refined_id_system.md` for the active design.

---

## 3. The core problem (in 90 seconds)

ANIKUTA has **two parallel identity systems that don't fully interoperate**:

1. **SQLDelight layer** — local-PK-based (`animes._id`). AniList-optional. The schema already supports unlinked anime.
2. **Cross-cutting stores** (`WatchProgressStore`, `PlaybackStateStore`, `DownloadTask`, `EpisodeMetadataCache`, `SourceLinkStore`, legacy `source_pref_<anilistId>`) — anilistId-keyed, non-nullable by type. AniList-required.

This produces **7 concrete failure modes** for unlinked extension anime: downloads blocked, episode metadata skipped, watch progress polluted (saved under degenerate `"0:<url>"` keys), tracker sync skipped, backup partially excluded, updates skipped, library sort broken. Unlinked anime are second-class citizens.

The composite key `"$anilistId:$episodeUrl"` is duplicated across **9+ files** with no central helper, and it breaks when source switching changes the `episodeUrl` — **downloads become invisible after a source switch even though the file is still on disk.**

The download system is the most tightly coupled: `DownloadAnimeInfo.anilistId: Int` is **non-nullable by type**, making it impossible to represent a download without an AniList ID. There's a hard gate (`AppController.kt:509-512`) that Toasts "Cannot download — anime not linked" and returns.

---

## 4. The proposed solution (in 90 seconds)

**Introduce a typed `WatchableId` sealed class** in `:core:common`:
```kotlin
sealed class WatchableId {
    data class Linked(val provider: MetadataProvider, val remoteId: String) : WatchableId()
    data class Unlinked(val sourceId: Long, val sourceUrl: String, val titleHash: String) : WatchableId()
    fun stableKey(): String = ...      // in-device key
    fun crossDeviceKey(): String = ... // backup/restore key
}
```

Migrate all 7 anilistId-keyed stores to `WatchableId` keys. Replace `episodeUrl` with `episodeNumber` in composite keys (fixes source-switching). Remove the anilistId hard gate on downloads. Add a `WatchableIdMigrator` for link/unlink events.

**Why hybrid (provider-ID + source+url+title-hash) over alternatives:**
- Title-hash alone: collision risk (similar-named series).
- UUID: not reproducible across devices (breaks backup/restore).
- Composite (title+epCount+year): ep count + year are often unknown.
- Provider-ID-only (current): doesn't solve the unlinked problem.

The hybrid uses the **most stable available identifier** at any time — provider ID when linked (zero collision, perfect reproducibility), source+url+title-hash when unlinked (in-device stability + best-effort cross-device).

---

## 5. The 6 key proposals

| # | Proposal | What it does | Effort |
|---|---|---|---|
| 01 | [Internal ID system (original)](proposals/01_internal_id_system.md) | ~~Typed `WatchableId`~~ — superseded by 01a | Medium |
| **01a** | **[Refined ID system (ACTIVE)](proposals/01a_refined_id_system.md)** | **Two-tier `local_id` (per-source, multi-component) + `content_id` (per-content grouping) with full provenance + external IDs** | Medium |
| 02 | [Provider abstraction](proposals/02_provider_abstraction.md) | `MetadataProvider` umbrella + registry so MAL/TMDB can be added as one module | Medium |
| 03 | [Download redesign](proposals/03_download_system_redesign.md) | `content_id`-keyed downloads, source-switching fix, user-direct folder | Large |
| 04 | [Extension evolution](proposals/04_extension_evolution.md) | Parallel modules for CloudStream-style extensions (not a unified contract) | Future |
| 05 | [Migration strategy](proposals/05_migration_strategy.md) | Automatic, idempotent, dual-read migration from v1 to v2 | Medium |

> 📌 **The refined identity model (proposal 01a)** is the active design. It incorporates the owner's direction: a multi-component `local_id` derived from (system + repo + extension + source-content-id), with full provenance stored + external provider IDs (AniList, MAL, TMDB) alongside. The two-tier structure (local_id + content_id) solves source-switching while preserving maximum flexibility for future core systems.

---

## 6. The 10 implementation phases

| Phase | Goal | Risk | Effort |
|---|---|---|---|
| 0 | Documentation hygiene | None | 1 day |
| 1 | `WatchableId` type + schema migration | Low | 3-5 days |
| 2 | Provider abstraction interfaces | Low | 3-5 days |
| 3 | Migrate watch progress + playback state | Medium | 3-5 days |
| 4 | Migrate episode metadata + source links | Medium | 3-5 days |
| 5 | `WatchableIdMigrator` for link/unlink | Low | 2-3 days |
| 6 | **Download system redesign** ★ | **High** | 7-10 days |
| 7 | Migrate browse + search + schedule to registry | Medium | 5-7 days |
| 8 | Module-architecture fixes (4 violations) | Low | 3-5 days |
| 9 | Stub cleanup + `:i18n` | None | 1 day |
| 10 | Extension evolution (future, opt-in) | Medium | 10+ days |

**Critical path:** Phase 0 → 1 → 3 → 4 → 5 → 6 (the AniList-decoupling spine).
**Total (Phases 0-9): ~4-6 weeks** of focused work.

See [plan/01_phased_implementation.md](plan/01_phased_implementation.md) for the full plan with dependencies + parallelism.

---

## 7. The 11 proposed ADRs

| ADR | Decision |
|---|---|
| 040 | ~~Internal content identity: typed `WatchableId`~~ — **superseded by ADR-050** |
| **050** | **Refined identity: two-tier `local_id` (per-source, multi-component) + `content_id` (per-content) with full provenance** ← ACTIVE |
| 041 | Provider abstraction: `MetadataProvider` umbrella + registry |
| 042 | Download identity: `content_id + episodeNumber` |
| 043 | Download folder: user-direct (no `ANIKUTA/` subfolder) |
| 044 | Extension system: parallel modules for multi-format |
| 045 | Migration: automatic, idempotent, dual-read |
| 046 | Module architecture: extract `:core:video-resolver` + `:core:history` |
| 047 | Consolidate 3 AniList HTTP clients into one |
| 048 | Schema: `watchable_id_json` + ADR-024 columns on `episodes` |
| 049 | Empty stub modules: remove or document |

See [plan/04_decision_records.md](plan/04_decision_records.md) for alternatives considered + trade-offs.

---

## 8. The highest-leverage finding

**One store already does it right.** `DetailsViewPreferenceStore` uses a hybrid key:
```kotlin
val key = anilistId?.toString() ?: "ext:$sourceId:$url"
```

This is the **only cross-cutting store that correctly handles unlinked extension anime**. It proves the pattern works. The entire restructuring generalizes this one pattern to a typed `WatchableId` across all 7 stores.

---

## 9. The highest-risk step

**Phase 6 (download system redesign)** is the riskiest single change:
- Re-keys the download queue (every `DownloadTask.key` changes).
- Moves files on disk (from `<root>/ANIKUTA/downloads/anime/<Title [id]>/` to `<root>/<Title [watchableId]>/`).
- Removes the hard gate (unlinked anime can now download).

**Mitigations:** idempotent migration, background + progress notification, dual-read paths during transition, v1 backups retained for rollback, extensive testing with synthetic large libraries.

---

## 10. What stays the same

- **The download engine** (`HttpDownloader`, `AdvancedHttpDownloader`, `HlsDownloader`) — unchanged. The coupling was upstream, in the identity layer.
- **The Aniyomi-compatible extension contract** (`:core:source-api`) — unchanged. ADR-029 preserved.
- **The SQLDelight schema PKs** — all stay `_id` (AUTOINCREMENT). The restructuring adds columns, doesn't restructure PKs.
- **The Voyager navigation** — unchanged.
- **The theme system** — unchanged.
- **The `AnimeDetailsProvider` pattern (ADR-039)** — extended, not replaced. It's the template for the provider abstraction.
- **AniList as the default provider** — stays. The abstraction enables adding others; it doesn't remove AniList.

---

## 11. The evidence base

This plan is grounded in **7 evidence files** (`_evidence/`, ~590KB total) produced by parallel deep-research agents, each citing `file:line` for every claim:

| Evidence file | Lines | Focus |
|---|---|---|
| EVID-01-content-identification.md | 2,326 | Where anilistId is (and isn't) the primary key |
| EVID-02A-downloads-data.md | 805 | Download data models, composite keys, persistence, storage |
| EVID-02B-downloads-pipeline.md | 770 | Download pipeline, orchestration, offline playback, source-switching break |
| EVID-03-provider-coupling.md | 907 | Per-system AniList coupling map |
| EVID-04-module-deps.md | 776 | All 41 module dependencies + violations |
| EVID-05-extensions.md | 1,216 | Extension loading, contract, abstraction layers |
| EVID-06-data-layer.md | 1,520 | Every `.sq` file, every table, every preference key |

---

## 12. Document index

### Phase 1 — Current State Analysis
1. [01_content_identification_flow.md](analysis/01_content_identification_flow.md) — the complete lifecycle of an anime entry + the 7 failure modes for unlinked anime.
2. [02_download_system_analysis.md](analysis/02_download_system_analysis.md) — the download pipeline, the composite key, the source-switching break.
3. [03_provider_coupling_map.md](analysis/03_provider_coupling_map.md) — per-system AniList coupling (tight/moderate/loose/none) + the 10 systems with no fallback.
4. [04_module_dependencies.md](analysis/04_module_dependencies.md) — all 41 modules, the 4 violations, the fat modules, the 9 empty stubs.
5. [05_extension_system_analysis.md](analysis/05_extension_system_analysis.md) — the Aniyomi-compatible contract, loading pipeline, the 2 abstraction layers that already exist.
6. [06_data_layer_analysis.md](analysis/06_data_layer_analysis.md) — every `.sq` file, every table, every preference key, the backup format.

### Phase 2 — Proposed Architecture
1. [01_internal_id_system.md](proposals/01_internal_id_system.md) — the typed `WatchableId` (hybrid approach).
2. [02_provider_abstraction.md](proposals/02_provider_abstraction.md) — the `MetadataProvider` umbrella + registry.
3. [03_download_system_redesign.md](proposals/03_download_system_redesign.md) — `WatchableId`-keyed downloads + source-switching fix + user-direct folder.
4. [04_extension_evolution.md](proposals/04_extension_evolution.md) — parallel modules for multi-format extensions.
5. [05_migration_strategy.md](proposals/05_migration_strategy.md) — automatic, idempotent, dual-read migration.

### Phase 3 — Implementation Plan
1. [01_phased_implementation.md](plan/01_phased_implementation.md) — 10 phases with dependencies + parallelism.
2. [02_risk_assessment.md](plan/02_risk_assessment.md) — 12 risks with likelihood, impact, mitigation.
3. [03_testing_strategy.md](plan/03_testing_strategy.md) — unit, integration, migration, regression tests.
4. [04_decision_records.md](plan/04_decision_records.md) — 10 proposed ADRs (040-049).

### Reference
- [APPENDIX.md](APPENDIX.md) — code reference index, schema dumps, file lists.

---

## 13. What I need from you (the reviewer)

This is a **planning and documentation task** — no code was modified, nothing was merged to `main`. The branch `docs/architecture-restructuring-plan` is pushed for your review.

**Decisions to make:**

1. **Approve the `WatchableId` hybrid approach (ADR-040)?** Or prefer one of the alternatives (title-hash, UUID, composite)?
2. **Approve the provider abstraction (ADR-041)?** Or keep AniList hardcoded for now?
3. **Approve the download redesign (ADR-042, ADR-043)?** Specifically: (a) `WatchableId + episodeNumber` keying, (b) user-direct folder (no `ANIKUTA/` subfolder).
4. **Approve the parallel-module extension approach (ADR-044)?** Or prefer a unified contract (accepting Aniyomi compat risk)?
5. **Approve the migration strategy (ADR-045)?** Automatic on update + dual-read + v1 backups?
6. **Approve the phase ordering?** Or reorder (e.g., do the module fixes first, or the provider abstraction first)?
7. **Which phases to do first?** The critical path (0→1→3→4→5→6) is the AniList-decoupling spine. Or start with the low-risk documentation + module fixes (Phases 0, 8, 9) to build momentum?
8. **CloudStream support (Phase 10) — now or later?** The plan defers it; confirm or reprioritize.

**Once approved**, the implementation proceeds per [plan/01_phased_implementation.md](plan/01_phased_implementation.md), one feature branch per phase, CI-gated, merged to `main` only when green.

---

## 14. The 30-second version

The app works, but AniList is baked into the identity layer in a way that makes unlinked extension anime second-class and breaks downloads on source switches. The fix is a typed `WatchableId` (hybrid: provider-ID when linked, source+url+title-hash when unlinked) that replaces the `"$anilistId:$episodeUrl"` composite key everywhere. The schema barely changes; the work is in the 7 cross-cutting stores. The download system gets a redesign (keyed by `WatchableId + episodeNumber`, user-direct folder, no hard gate). Migration is automatic. Total effort: ~4-6 weeks across 10 phases. The highest-risk phase is the download redesign (Phase 6). One store (`DetailsViewPreferenceStore`) already demonstrates the correct hybrid-key pattern — the whole restructuring generalizes it.

---

*This summary is the entry point. For any claim above, the supporting evidence + detailed reasoning is in the linked documents.*
