# 01 — Phased Implementation Plan

> **Phase 3 / Implementation.** Concrete, actionable plan for executing the restructuring. Each phase has clear goals, files to change/create, dependencies, complexity, and is independently shippable. Driven by `proposals/01-05`.

---

## Guiding principles

1. **Each phase leaves the app in a working state.** No half-migrations. If a phase ships, the app is fully functional.
2. **Phases are ordered by dependency + risk.** Foundational types (`WatchableId`) before store migrations; store migrations before download redesign; download redesign before folder-structure migration.
3. **Parallelizable work is called out.** Some phases can proceed in parallel (e.g., provider-abstraction interfaces can be drafted while `WatchableId` is implemented).
4. **CI gates every phase.** Each phase pushes to a feature branch, triggers CI, and is merged only when the APK builds + unit tests pass.

---

## Phase 0 — Documentation hygiene (prerequisite, low risk)

**Goal:** Fix the documentation drift identified in Doc 01-06 before touching code, so future agents have an accurate baseline.

**Files to change:**
- `AI-AGENT-PROMPTS/MASTER_STARTUP_PROMPT.md` — update navigation (Voyager) + theme (done) + ADR-039.
- `README.md` — update done/not-done lists.
- `ARCHITECTURE.md` §3 — add `core:tracker`, `core:update-checker`, `feature:episode-settings` to module tree.
- `PROJECT_STARTUP.md` Step 4 — add the 4th convention plugin.
- All module READMEs — remove false "Skeleton (Phase 1)" / "Empty stub" claims.
- `:feature:settings` README — fix "Empty stub" (it has 1,441 lines).

**Complexity:** Low. **Risk:** None. **Dependencies:** None. **Shippable:** Yes (docs-only commit).

---

## Phase 1 — `WatchableId` type + schema migration (foundational, low risk)

**Goal:** Introduce the `WatchableId` sealed class in `:core:common`. Add the `watchable_id` column to `animes`. Backfill existing rows. No behavioral change yet — the new type exists but isn't used by stores.

**Files to create:**
- `:core:common/.../model/WatchableId.kt` — the sealed class (`Linked` + `Unlinked` + `stableKey()` + `crossDeviceKey()`).
- `:core:common/.../model/MetadataProvider.kt` — the `MetadataProvider` enum.
- `:core:database/src/main/sqldelight/.../2.sqm` — schema migration (add `watchable_id_json`, add ADR-024 columns to `episodes`, add unique indexes).

**Files to change:**
- `:core:common/.../model/Anime.kt` — add `watchableId: WatchableId` field (derived from existing `anilistId`/`sourceId`/`url` for now).
- `:core:common/.../model/details/UnifiedAnime.kt` — add `watchableId` field.
- `:data:anime/.../AnimeRepositoryImpl.kt` — populate `watchableId` on upsert; add `updateWatchableId(id, watchableId)`.
- `animes.sq` — add `selectByWatchableId` query.

**Complexity:** Low-Medium. **Risk:** Low (additive — the schema migration adds columns, doesn't remove). **Dependencies:** Phase 0 (so docs match). **Shippable:** Yes — the app works exactly as before; the new type is dormant.

**Completion criteria:**
- `WatchableId` compiles + is unit-tested (serialization, stableKey, crossDeviceKey).
- Schema migrates cleanly (v2 → v3) on a v0.1 database.
- Every existing `animes` row has a non-null `watchable_id_json` after backfill.
- CI builds + `:data:anime` tests pass.

---

## Phase 2 — Provider abstraction interfaces (parallelizable with Phase 1, low risk)

**Goal:** Create `:core:provider-api` with the `MetadataProvider` umbrella + sub-interfaces + `MetadataProviderRegistry`. The existing `AnimeDetailsProvider` moves here. No behavioral change — the interfaces exist but the registry always returns AniList.

**Files to create:**
- `:core:provider-api/` — new module.
- `:core:provider-api/.../MetadataProvider.kt` — umbrella interface.
- `:core:provider-api/.../HomeFeedProvider.kt`, `SearchProvider.kt`, `AiringScheduleProvider.kt`, `CoverImageProvider.kt`.
- `:core:provider-api/.../MetadataProviderRegistry.kt`.
- `:core:provider-api/.../ProviderPreferences.kt` (interface; impl in `:core:preferences`).

**Files to change:**
- `:core:common/.../details/AnimeDetailsProvider.kt` → move to `:core:provider-api`.
- `:core:common/.../details/UnifiedAnime.kt` → move (or keep + depend on `:core:provider-api`).
- `:core:anilist/.../details/AniListDetailsProvider.kt` → implement the new `MetadataProvider` umbrella (wrap, don't rewrite).
- `settings.gradle.kts` — add `:core:provider-api`.
- `app/.../di/DetailsModule.kt` → register `AniListMetadataProvider` as a `MetadataProvider`.

**Complexity:** Medium. **Risk:** Low (additive — the registry is wired but always returns AniList). **Dependencies:** Phase 1 (for `WatchableId` on `UnifiedAnime`). **Parallelizable:** Yes, with Phase 1. **Shippable:** Yes.

**Completion criteria:**
- `:core:provider-api` compiles + is unit-tested (registry fallback logic).
- `AniListMetadataProvider` implements the new interfaces (delegates to existing `AniListApi`).
- The details page still works (now via the registry, but AniList is the only provider).
- CI builds + tests pass.

---

## Phase 3 — Migrate watch progress + playback state to `WatchableId` (medium risk)

**Goal:** Re-key `WatchProgressStore` + `PlaybackStateStore` from `"$anilistId:$episodeUrl"` to `"$watchableId:$episodeNumber"`. Includes the migration of existing entries.

**Files to change:**
- `:core:player/.../WatchProgressStore.kt` — change key type; add `get(watchableId, episodeNumber)`; add `rekey(oldId, newId)`.
- `:core:player/.../PlaybackStateStore.kt` — same.
- `:feature:watch/.../WatchScreen.kt` — use `WatchableId` + `episodeNumber` for progress saves.
- `:feature:history/.../HistoryViewModel.kt` — use `WatchableId` for history lookups.
- `:app/.../navigation/AppController.kt` — pass `WatchableId` to `resolveEpisode`.

**Migration code:**
- `:core:player/.../migration/WatchProgressMigrator.kt` (new) — re-keys existing entries.
- `:core:player/.../migration/PlaybackStateMigrator.kt` (new).

**Complexity:** Medium. **Risk:** Medium (re-keying existing data; potential data loss for `anilistId=0` entries — which were already broken). **Dependencies:** Phase 1 (`WatchableId` exists + is backfilled on `animes`). **Shippable:** Yes — the app works; watch progress + history now use the new keys.

**Completion criteria:**
- Watch progress saves + loads correctly for both linked + unlinked anime.
- History rows for unlinked anime are now openable (the `anilistId=0` bug is fixed because `WatchableId.Unlinked` is a valid key).
- Migration runs automatically on first launch; <5% data loss (only the already-unusable `0:<url>` entries).
- CI builds + tests pass.

---

## Phase 4 — Migrate episode metadata + source links to `WatchableId` (medium risk)

**Goal:** Re-key `EpisodeMetadataCache` + `SourceLinkStore` + `ExtensionLinkStore` + legacy `source_pref_*` to `WatchableId`.

**Files to change:**
- `:core:episode-metadata/.../EpisodeMetadataCache.kt` — outer key → `watchableId.stableKey()`.
- `:data:extension/.../SourceLinkStore.kt` — key → `watchableId.stableKey()`.
- `:data:extension/.../ExtensionLinkStore.kt` — value → `WatchableId` (not `Int`).
- `:feature:anime-details/.../DetailsViewPreferenceStore.kt` — already hybrid; formalize to `WatchableId`.
- Legacy `source_pref_*` — migrate to `source_pref_<watchableId>`.

**Migration code:**
- `:core:episode-metadata/.../migration/EpisodeMetadataMigrator.kt`.
- `:data:extension/.../migration/SourceLinkMigrator.kt`.

**Complexity:** Medium. **Risk:** Medium. **Dependencies:** Phase 1, Phase 3 (for consistent `WatchableId` usage). **Shippable:** Yes.

**Completion criteria:**
- Episode metadata cache works for unlinked anime.
- Source links survive link/unlink events (via `WatchableIdMigrator.rekey`).
- Legacy `source_pref_*` migrated.
- CI builds + tests pass.

---

## Phase 5 — `WatchableIdMigrator` for link/unlink events (low risk)

**Goal:** When an anime is linked (unlinked → linked) or unlinked (linked → unlinked), re-key all cross-cutting store entries.

**Files to create:**
- `:core:common/.../migration/WatchableIdMigrator.kt` — orchestrates re-keying across all stores.

**Files to change:**
- `:app/.../navigation/AppController.kt` — call `WatchableIdMigrator.migrate(oldId, newId)` on link/unlink.
- `:feature:search/.../ExtensionLinkingViewModel.kt` — trigger the migration on link.

**Complexity:** Low. **Risk:** Low (the re-key logic is per-store, already implemented in Phases 3-4). **Dependencies:** Phases 3, 4. **Shippable:** Yes.

**Completion criteria:**
- Linking an unlinked anime preserves its watch progress, downloads, history, metadata.
- Unlinking a linked anime preserves the same.
- CI builds + tests pass.

---

## Phase 6 — Download system redesign (HIGH risk — the big one)

**Goal:** Replace `DownloadAnimeInfo.anilistId` with `watchableId`. Replace the composite key. Remove the hard gate. Add the filesystem fallback. Move on-disk folders to the user-direct structure.

**Files to change:**
- `:core:download/.../DownloadModels.kt` — `DownloadAnimeInfo` redesigned (proposal 03 §2.1).
- `:core:download/.../DownloadTask.kt` — `key` uses `watchableId + episodeNumber`.
- `:core:download/.../DownloadManager.kt` — interface takes `WatchableId` (proposal 03 §2.2).
- `:core:download/.../DefaultDownloadManager.kt` — `isEpisodeDownloaded` + `findTask` use the new key + filesystem fallback (proposal 03 §2.3).
- `:core:download/.../DownloadQueue.kt` — `keyFor` uses new composite.
- `:core:download/.../DownloadStorageProvider.kt` — no `ANIKUTA/` subfolder; folder name uses `watchableId.stableKey()` (proposal 03 §2.5).
- `:app/.../download/DownloadOrchestrator.kt` — build `DownloadAnimeInfo` with `watchableId`.
- `:app/.../navigation/AppController.kt` — remove the `anilistId == 0` gate (proposal 03 §2.4); pass `WatchableId`.
- `:feature:download/.../DownloadViewModel.kt` — group by `watchableId.stableKey()`.
- `:feature:anime-details/.../EpisodeDownloadControl.kt` — pass `WatchableId`.

**Migration code:**
- `:core:download/.../migration/DownloadMigration.kt` (new) — re-keys tasks + moves folders (proposal 03 §4).

**Complexity:** High. **Risk:** High (file I/O on disk + re-keying the queue). **Dependencies:** Phases 1, 3, 4, 5 (consistent `WatchableId` everywhere). **Shippable:** Yes, but requires thorough testing.

**Completion criteria:**
- Downloads work for unlinked anime (the gate is gone).
- Downloads survive source switches (the source-switching break is fixed).
- Existing downloads are migrated to the new folder structure automatically.
- The user's chosen SAF folder is used directly (no `ANIKUTA/` subfolder for new downloads).
- CI builds + tests pass + manual testing of the migration on a large library.

---

## Phase 7 — Provider abstraction: migrate browse + search + schedule (medium risk)

**Goal:** Migrate `:feature:browse`, `:feature:search` (AniList tab), `:feature:updates` (schedule) to use the provider registry instead of `AniListApi` directly.

**Files to change:**
- `:feature:browse/.../BrowseScreen.kt` — use `HomeFeedProvider` via the registry.
- `:feature:search/.../SearchViewModel.kt` — AniList tab uses `SearchProvider` via the registry.
- `:feature:updates/.../UpdatesViewModel.kt` + `ScheduleTabContent.kt` — use `AiringScheduleProvider` via the registry.
- `:core:anilist/.../AniListApi.kt` — consolidate the 3 HTTP clients (browse + tracker + Aniyomi translator) into one `AniListClient`.

**Files to create:**
- `:feature:settings/.../MetadataProvidersScreen.kt` — the Settings → Data & Storage → Metadata Providers UI.

**Complexity:** Medium. **Risk:** Medium (refactor of 3 feature modules' data sources). **Dependencies:** Phase 2 (the registry exists). **Parallelizable:** Yes, with Phases 3-5 (different modules). **Shippable:** Yes.

**Completion criteria:**
- Browse, search (AniList tab), and schedule work via the registry.
- The registry's fallback logic works (if AniList is unavailable, a graceful error shows).
- The Settings → Metadata Providers screen shows AniList as the only provider (for now).
- CI builds + tests pass.

---

## Phase 8 — Module-architecture fixes (low risk, parallelizable)

**Goal:** Fix the 4 module violations from Doc 04. Extract shared components to `:core`.

**Files to change:**
- `:core:backup/build.gradle.kts` — remove `:data:extension` dep. Define `BackupSourceLinkProvider` interface in `:core:backup`; let `:data:extension` implement it.
- `:feature:episode-settings/build.gradle.kts` — remove `:feature:anime-details` dep. Extract `EpisodeRowPreview` to `:core:designsystem`.
- `:feature:watch/build.gradle.kts` + `:feature:download/build.gradle.kts` — remove `:feature:video-resolver` dep. Create `:core:video-resolver` (extract `ResolverService` + `VideoTitleParser` + `ResolverStrategyPicker`); feature modules depend on it.
- `:core:tracker/build.gradle.kts` — remove `:core:player` dep. Create `:core:history` (move `WatchProgressStore` + `PlaybackStateStore` there); `:core:tracker` depends on `:core:history`.

**Files to create:**
- `:core:video-resolver/` — new module (logic extracted from `:feature:video-resolver`).
- `:core:history/` — new module (stores moved from `:core:player`).

**Complexity:** Medium. **Risk:** Low (pure refactoring; behavior unchanged). **Dependencies:** Phases 3-4 (so `WatchProgressStore` is already migrated before moving it). **Parallelizable:** Yes, with Phases 2-7. **Shippable:** Yes.

**Completion criteria:**
- 0 module violations (Doc 04's 4 violations all fixed).
- The app builds + all features work.
- CI builds + tests pass.

---

## Phase 9 — Stub cleanup + `:i18n` (low risk, parallelizable)

**Goal:** Remove or document the 9 empty stubs. Fix the `:i18n` phantom module.

**Files to change:**
- `settings.gradle.kts` — remove the 8 removable empty stubs (or add READMEs explaining they're intentional).
- `:app/build.gradle.kts` — remove the `:feature:player` dep.
- `settings.gradle.kts` — either create `:i18n/` directory + minimal build script, OR remove the `:i18n` declaration.

**Complexity:** Low. **Risk:** None. **Dependencies:** None. **Parallelizable:** Yes, with any phase. **Shippable:** Yes.

---

## Phase 10 — Extension system evolution (future, opt-in)

**Goal:** Add `:core:media-source-api` + `:data:media-extension` for CloudStream-style extensions. Generalize `SourceMatcher` into `TitleMatcher`.

**Files to create:**
- `:core:media-source-api/` — CloudStream contract (future).
- `:data:media-extension/` — CloudStream loader (future).
- `:core:video-resolver/.../MediaResolverStrategy.kt` — maps `MediaVideo` → `ResolverServer`/`ResolverVideo`.

**Files to change:**
- `:core:common/.../DataSource.kt` — add `MEDIA_EXTENSION`.
- `:data:extension/.../matcher/SourceMatcher.kt` → extract `TitleMatcher` to `:core:common`.

**Complexity:** High (new extension format). **Risk:** Medium (additive — doesn't touch Aniyomi compat). **Dependencies:** Phase 8 (`:core:video-resolver` exists). **Shippable:** Yes, but CloudStream support is opt-in + deferred.

**This phase is optional / future.** The AniList-coupling restructuring (Phases 1-8) is the priority.

---

## Dependency graph + parallelism

```
Phase 0 (docs) ────┐
                   ├─→ Phase 1 (WatchableId + schema) ──┬─→ Phase 3 (progress) ──→ Phase 4 (metadata+links) ──→ Phase 5 (migrator) ──→ Phase 6 (downloads) ★
                   │                                    │
                   └─→ Phase 2 (provider-api) ─────────┴─→ Phase 7 (browse/search/schedule) ──→ Phase 8 (module fixes) ──→ Phase 9 (stubs) ──→ Phase 10 (extensions, future)

                   Phase 8 + 9 are parallelizable with Phases 3-7.
                   ★ = highest risk phase
```

**Critical path:** Phase 0 → 1 → 3 → 4 → 5 → 6. This is the AniList-decoupling spine.

**Parallel tracks:**
- Phase 2 (provider-api) can run alongside Phase 1.
- Phase 7 (browse/search/schedule migration) can run alongside Phases 3-5.
- Phase 8 (module fixes) can run alongside Phases 3-7.
- Phase 9 (stubs) can run anytime.

---

## Effort estimates

| Phase | Effort | Risk | Notes |
|---|---|---|---|
| 0 (docs) | 1 day | None | |
| 1 (WatchableId + schema) | 3-5 days | Low | Foundational type + migration |
| 2 (provider-api) | 3-5 days | Low | Interfaces + registry |
| 3 (progress migration) | 3-5 days | Medium | Re-key + migrator |
| 4 (metadata + links) | 3-5 days | Medium | Re-key + migrators |
| 5 (WatchableIdMigrator) | 2-3 days | Low | Orchestration |
| 6 (downloads) ★ | 7-10 days | High | Re-key + folder move + gate removal |
| 7 (browse/search/schedule) | 5-7 days | Medium | 3 feature migrations + client consolidation |
| 8 (module fixes) | 3-5 days | Low | Pure refactoring |
| 9 (stubs) | 1 day | None | |
| 10 (extensions, future) | 10+ days | Medium | Optional / deferred |

**Total (Phases 0-9): ~4-6 weeks** of focused work. Phase 10 is future/optional.

---

## Shipping strategy

- **Each phase ships as a feature branch** (`feature/watchable-id`, `feature/download-redesign`, etc.), triggers CI, merges to `main` only when green.
- **Phases 1-5 can ship incrementally** — each leaves the app working, even though the full benefit isn't realized until Phase 6.
- **Phase 6 (downloads) ships as one atomic change** — the redesign + migration + gate removal go together. This is the riskiest single merge.
- **Phases 7-9 ship independently** after Phase 6.
- **The migration is irreversible after Phase 6 + one app version** (per proposal 05 §5). Keep v1 backups until then.

---

## Completion criteria (overall)

The restructuring is complete when:
1. `WatchableId` is the identity type everywhere; `anilistId` is a legacy lookup column only.
2. Unlinked extension anime can be downloaded, tracked (if linked to a tracker), and have their history opened.
3. Downloads survive source switches.
4. The user's chosen SAF folder is used directly (no `ANIKUTA/` subfolder).
5. Adding a new metadata provider (MAL/TMDB) is one new module + one Koin line.
6. The 4 module violations are fixed.
7. The migration runs automatically on update with <1% data loss.
8. CI builds + all tests pass.

---

*Related: `plan/02_risk_assessment.md` (per-phase risks), `plan/03_testing_strategy.md` (test scenarios), `plan/04_decision_records.md` (the architectural decisions).*
