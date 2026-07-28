# 04 — Decision Records

> **Phase 3 / Implementation.** For each major architectural decision in this plan, the decision, alternatives considered, trade-offs accepted, and conditions for revisiting. These are the ADRs for the restructuring (proposed ADRs 040-049, to be merged into `DOCS/04-design-decisions.md` upon approval).

---

## ADR-040 — Internal content identity: typed `WatchableId` (hybrid provider-ID + source+url+title-hash)

**Status:** Proposed (pending review).
**Driven by:** `analysis/01_content_identification_flow.md`, `analysis/06_data_layer_analysis.md`, `proposals/01_internal_id_system.md`.

### Decision

Introduce a typed `WatchableId` sealed class in `:core:common` with two variants:
- `Linked(provider: MetadataProvider, remoteId: String)` — for content linked to a metadata provider.
- `Unlinked(sourceId: Long, sourceUrl: String, titleHash: String)` — for content with no provider linkage.

`WatchableId.stableKey()` is the in-device key for cross-cutting stores. `WatchableId.crossDeviceKey()` is the backup/restore key (linked: by provider ID; unlinked: by title hash).

All 7 anilistId-keyed cross-cutting stores migrate to `WatchableId` keys. The composite key `"$anilistId:$episodeUrl"` is replaced by `"$watchableId:$episodeNumber"` (source-independent).

### Alternatives considered

1. **Hash of canonical title (Approach A)** — rejected: collision risk for similar-named series; title canonicalization is fragile.
2. **Database UUID (Approach B)** — rejected: not reproducible across devices; breaks backup/restore.
3. **Composite key (title + episode count + year) (Approach C)** — rejected: episode count + year are often unknown or inaccurate; fragile.
4. **Provider-ID-only (Approach D, current)** — rejected: doesn't solve the unlinked-anime problem; hardcodes AniList.

### Trade-offs accepted

- Unlinked cross-device restore is best-effort (title-hash collisions possible but rare; user can disambiguate).
- `WatchableIdMigrator` adds complexity for link/unlink events.
- Two identity columns on `animes` (`anilist_id` + `watchable_id_json`) — redundant for linked anime, kept for backward compat + efficient AniList lookups.
- `episodeNumber` replaces `episodeUrl` in composite keys — rare episode-number mismatches produce duplicate entries (least-bad outcome).

### Conditions for revisiting

- If unlinked title-hash collision rate >0.1% of restores, add a disambiguation field (year, episode count) to the cross-device key.
- If users frequently link the same anime to multiple providers, add a `provider_links` table (many providers per anime).
- If `WatchableIdMigrator` proves fragile, make `WatchableId` immutable + track changes in an `anime_identity_history` table.

---

## ADR-041 — Provider abstraction: `MetadataProvider` umbrella + `MetadataProviderRegistry` with fallback

**Status:** Proposed.
**Driven by:** `analysis/03_provider_coupling_map.md`, `proposals/02_provider_abstraction.md`.

### Decision

Create `:core:provider-api` with a `MetadataProvider` umbrella interface + sub-interfaces (`HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`, `AnimeDetailsProvider`, `EpisodeMetadataSource`, `CoverImageProvider`) + a `MetadataProviderRegistry` that resolves the active provider per capability with a fallback chain.

AniList becomes one provider (the default). Adding MAL/TMDB is one new module + one Koin line.

### Alternatives considered

1. **Keep AniList hardcoded** (current) — rejected: doesn't solve the coupling; can't add providers.
2. **Single `MetadataProvider` interface with all methods** — rejected: not every provider supports every capability (e.g., MAL has no airing schedule). The sub-interfaces let providers declare capabilities.
3. **Compile-time provider selection** (no registry) — rejected: users want to switch providers at runtime; fallback needs runtime resolution.

### Trade-offs accepted

- The registry adds an indirection (every metadata call goes through `forCapability`).
- `UnifiedAnime` is a lowest-common-denominator (provider-specific fields dropped or in `providerExtras`).
- Per-capability active-provider selection adds UI complexity (mitigated by simple mode).

### Conditions for revisiting

- If registry latency is >5ms per call, cache availability with a longer TTL.
- If providers return incompatible data, add a `MetadataResolver` merge layer (ADR-011 generalized).
- If 5+ providers exist, consider dynamic module loading.

---

## ADR-042 — Download identity: `WatchableId + episodeNumber` replaces `anilistId + episodeUrl`

**Status:** Proposed.
**Driven by:** `analysis/02_download_system_analysis.md`, `proposals/03_download_system_redesign.md`.

### Decision

Replace the download composite key `"$anilistId:$episodeUrl"` with `"$watchableId.stableKey():$episodeNumber"`. The on-disk folder name uses `watchableId.stableKey()` instead of `anilistId`. `DownloadManager` methods take `WatchableId` instead of `anilistId`. The hard `anilistId == 0` gate is removed. A filesystem fallback (`findEpisodeDir` by episode number) handles cache misses.

### Alternatives considered

1. **Keep `anilistId + episodeUrl`** (current) — rejected: source-switching break (Doc 02 §10); unlinked anime blocked.
2. **`anilistId + episodeNumber`** — rejected: still requires anilistId (unlinked blocked); doesn't decouple.
3. **`WatchableId + episodeUrl`** — rejected: still breaks on source switch (episodeUrl changes).
4. **UUID per download** (no composite key) — rejected: can't dedup; can't find a download by anime+episode.

### Trade-offs accepted

- Episode-number mismatches across sources (~5% of cases) produce duplicate entries (least-bad).
- Filesystem fallback scan is slower than direct lookup (mitigated by index cache if needed).
- The migration moves files on disk (highest-risk step; mitigated by idempotency + retry).

### Conditions for revisiting

- If episode-number mismatches are frequent (>10%), add a per-anime episode-number mapping table.
- If filesystem fallback is too slow for >500 anime, add an index cache.

---

## ADR-043 — Download folder structure: user-direct (no `ANIKUTA/` subfolder)

**Status:** Proposed.
**Driven by:** `analysis/02_download_system_analysis.md` §6, `proposals/03_download_system_redesign.md` §2.5.

### Decision

Remove the mandatory `ANIKUTA/downloads/anime/` nesting. The user's chosen SAF folder is used directly: `<USER_FOLDER>/<Title [watchableId]>/Episode NNN/...`. A `preserveLegacyStructure` opt-in keeps the old nesting for users who set up their folder expecting it.

### Alternatives considered

1. **Keep the `ANIKUTA/` subfolder** (current) — rejected: contradicts the user's explicit requirement.
2. **Make the subfolder name configurable** — rejected: adds UI complexity for marginal benefit; the user-direct approach is simpler.

### Trade-offs accepted

- Existing downloads must be migrated (moved up one level).
- The folder name embeds `watchableId.stableKey()` (less human-readable than `[12345]`, but source-independent).
- `preserveLegacyStructure` opt-in keeps two code paths (removed in a future release).

### Conditions for revisiting

- If the migration fails for >5% of users, add a manual "re-migrate downloads" button + investigate the failure mode.

---

## ADR-044 — Extension system: parallel modules for multi-format support (not unified contract)

**Status:** Proposed.
**Driven by:** `analysis/05_extension_system_analysis.md`, `proposals/04_extension_evolution.md`.

### Decision

Support multiple extension formats (Aniyomi + CloudStream + future) via **parallel module stacks**, not a unified contract. Each format has its own `:core:*-source-api` + `:data:*-extension` modules. Both map into the existing `UnifiedAnime` + `ResolverServer`/`ResolverVideo`. A generic `ExtensionManager<T>` interface lets the UI iterate all formats.

### Alternatives considered

1. **Unify into one contract** — rejected: breaks Aniyomi compatibility (ADR-029) or creates a lowest-common-denominator contract.
2. **Rewrite `:core:source-api` to be generic** — rejected: same as above.
3. **Don't support other formats** — rejected: the prompt explicitly asks for multi-format flexibility.

### Trade-offs accepted

- Two parallel extension stacks (duplication).
- The extensions-settings UI becomes more complex (3-way toggle).
- `ExtensionManager<out T>` generic adds type complexity.

### Conditions for revisiting

- If a third extension format emerges, evaluate whether parallel modules scale.
- If CloudStream extensions prove more popular, consider making CloudStream the default.

---

## ADR-045 — Migration: automatic, idempotent, dual-read during transition

**Status:** Proposed.
**Driven by:** `proposals/05_migration_strategy.md`.

### Decision

The migration from the anilistId-keyed system to `WatchableId` is **automatic on app update**, **idempotent** (gated by `pref_migration_v2_done`), **non-destructive** (v1 backups retained for one version), with **dual-read paths** during the transition (so the app works if the migration crashes mid-way).

### Alternatives considered

1. **Manual migration (user triggers)** — rejected: most users won't do it; they'd be stuck on the old system.
2. **One-shot migration (no dual-read)** — rejected: if it crashes mid-way, the app is bricked.
3. **Fresh start (wipe + re-fetch)** — rejected: users lose offline downloads + watch progress.
4. **Phased rollout (10% → 50% → 100%)** — considered but rejected for now (the migration is deterministic; phased rollout adds complexity without benefit unless telemetry shows problems).

### Trade-offs accepted

- Dual-read paths add code complexity (removed in a future release).
- v1 backups consume storage (cleaned up after one version).
- The migration is irreversible after Phase 6 + one version (v1 backups are the rollback).

### Conditions for revisiting

- If the migration success rate is <95%, add phased rollout.
- If users report data loss, prioritize the v1 backup restore + investigate.

---

## ADR-046 — Module architecture: extract `:core:video-resolver` + `:core:history` to fix violations

**Status:** Proposed.
**Driven by:** `analysis/04_module_dependencies.md`, `proposals/04_extension_evolution.md` §3, `plan/01_phased_implementation.md` Phase 8.

### Decision

Fix the 4 module violations (Doc 04 §4) by:
1. Extracting `ResolverService` + `VideoTitleParser` + `ResolverStrategyPicker` from `:feature:video-resolver` into a new `:core:video-resolver` (removes 2 feature→feature violations: `:feature:watch`, `:feature:download`).
2. Moving `WatchProgressStore` + `PlaybackStateStore` from `:core:player` to a new `:core:history` (removes the `:core:tracker → :core:player` smell).
3. Defining a `BackupSourceLinkProvider` interface in `:core:backup` (removes the `:core:backup → :data:extension` violation).
4. Extracting `EpisodeRowPreview` to `:core:designsystem` (removes the `:feature:episode-settings → :feature:anime-details` violation).

### Alternatives considered

1. **Leave the violations** — rejected: they're architectural debt; future agents might compound them.
2. **Move everything to `:app`** — rejected: `:app` is already the fattest module (31 deps); this would make it fatter.
3. **Consolidate `:feature:video-resolver` into `:feature:watch`** — rejected: `:feature:download` also needs it; that would create a `:feature:download → :feature:watch` violation.

### Trade-offs accepted

- Two new modules (`:core:video-resolver`, `:core:history`) — but they're extractions, not new code.
- The `:core:video-resolver` split means the UI (`VideoResolverSheet`) stays in `:feature:video-resolver` while the logic moves — a slightly awkward split, but acceptable.

### Conditions for revisiting

- If `:core:video-resolver` + `:feature:video-resolver` prove confusing, merge them back into a single `:core:video-resolver` that includes the UI (but this would require `:core:video-resolver` to depend on `:core:designsystem`, which is fine).

---

## ADR-047 — Consolidate the 3 AniList HTTP clients into one `AniListClient`

**Status:** Proposed.
**Driven by:** `analysis/03_provider_coupling_map.md` §6, `proposals/02_provider_abstraction.md` §6.

### Decision

Consolidate `AniListApi` (browse), `AniListTrackApi` (tracker), and the Aniyomi translator's ad-hoc calls into a single `AniListClient` with authenticated + unauthenticated modes. `AniListMetadataProvider` + `AniListTracker` + `AniyomiBackupTranslator` all wrap `AniListClient`.

### Alternatives considered

1. **Keep 3 clients** (current) — rejected: code duplication, no shared rate limiting, harder to maintain.
2. **2 clients (browse + tracker)** — rejected: the Aniyomi translator's calls duplicate the browse client's logic.

### Trade-offs accepted

- The consolidation is a refactor of working code (some risk of regression — mitigated by testing).
- `AniListClient` has both authenticated + unauthenticated methods (slightly larger surface).

### Conditions for revisiting

- If the consolidated client's authenticated + unauthenticated modes prove hard to maintain, split into `AniListBrowseClient` + `AniListTrackClient` sharing a base.

---

## ADR-048 — Schema: add `watchable_id_json` column + ADR-024 columns to `episodes`

**Status:** Proposed.
**Driven by:** `analysis/06_data_layer_analysis.md`, `proposals/01_internal_id_system.md` §4.

### Decision

Add a `watchable_id_json TEXT NOT NULL DEFAULT '{}'` column to `animes` (denormalized JSON, per proposal 01 §4.3 Option A). Add the 4 missing ADR-024 status columns (`release_date`, `last_refresh`, `last_metadata_fetch`, `next_episode_check`) to `episodes`. Add unique indexes on `episodes(anime_id, episode_number)` and `animes(source_id, url)`.

### Alternatives considered

1. **Normalized columns (6 columns for `watchable_id`)** — rejected: more migration boilerplate; rarely queried by internals.
2. **Don't add ADR-024 columns to `episodes`** — rejected: ARCHITECTURE.md §5 requires them; they're needed for future notifications (ADR-014).
3. **Remove `anilist_id` column** — rejected: needed for backward compat + efficient AniList lookups.

### Trade-offs accepted

- JSON in SQLite is less queryable (mitigated: we rarely query by `watchable_id` internals).
- The `episodes` ADR-024 columns are added but may go unused until notifications are implemented.

### Conditions for revisiting

- If `watchable_id_json` needs to be queried often, extract `watchable_id_type` + `watchable_id_stable_key` as indexed columns.

---

## ADR-049 — Empty stub modules: remove or document

**Status:** Proposed.
**Driven by:** `analysis/04_module_dependencies.md` §6, `plan/01_phased_implementation.md` Phase 9.

### Decision

Remove the 8 immediately-removable empty stub modules (`:core:network`, `:core:notification`, `:core:source-local`, `:data:manga`, `:data:tracker`, `:feature:home`, `:feature:more`, `:feature:episode-list`) from `settings.gradle.kts`. For `:feature:player` (depended on by `:app`), remove the `:app` dep first, then the module. For `:i18n`, either create the directory + minimal build script OR remove the declaration.

**Alternative for stubs tied to future work** (`:core:notification` for ADR-014, `:core:source-local` for local-files-as-source, `:data:manga` for ADR-009): keep them but add a one-line README explaining they're intentional placeholders, so future agents don't "fix" them.

### Alternatives considered

1. **Keep all stubs** — rejected: clutters `settings.gradle.kts`; confusing for new agents.
2. **Remove all stubs including future-work ones** — rejected: loses the architectural placeholders for planned features.

### Trade-offs accepted

- Removing the stubs means re-adding them when the features land (low cost — `include(":...")` is one line).
- Keeping the future-work stubs with READMEs is a small documentation burden.

### Conditions for revisiting

- If a stubmed feature is implemented, re-add the module.

---

## Summary of proposed ADRs

| ADR | Title | Status |
|---|---|---|
| 040 | Internal content identity: typed `WatchableId` (hybrid) | Proposed |
| 041 | Provider abstraction: `MetadataProvider` umbrella + registry | Proposed |
| 042 | Download identity: `WatchableId + episodeNumber` | Proposed |
| 043 | Download folder: user-direct (no `ANIKUTA/` subfolder) | Proposed |
| 044 | Extension system: parallel modules for multi-format | Proposed |
| 045 | Migration: automatic, idempotent, dual-read | Proposed |
| 046 | Module architecture: extract `:core:video-resolver` + `:core:history` | Proposed |
| 047 | Consolidate 3 AniList HTTP clients into one | Proposed |
| 048 | Schema: `watchable_id_json` + ADR-024 columns on `episodes` | Proposed |
| 049 | Empty stub modules: remove or document | Proposed |

**Upon approval, these ADRs are merged into `DOCS/04-design-decisions.md`** (per the existing ADR process: superseded entries are struck-through, never deleted; new entries appended).

---

*Related: all `proposals/01-05` + all `plan/01-03`. These ADRs formalize the decisions in those documents.*
