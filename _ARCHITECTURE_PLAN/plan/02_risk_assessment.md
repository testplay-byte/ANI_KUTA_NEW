# 02 — Risk Assessment

> **Phase 3 / Implementation.** What could go wrong, per risk: likelihood, impact, mitigation, and how to test for it before it becomes a problem. Driven by `proposals/01-05` + `plan/01_phased_implementation.md`.

---

## Risk register

### R1 — Migration data loss (watch progress for unlinked anime)

**Phase:** 3 (watch progress migration)
**Likelihood:** Certain (by design — Doc 01 §6.3 entries are already unusable).
**Impact:** Low (the affected entries — `anilistId=0` — were already unopenable; no real user-visible regression).
**Mitigation:**
- The migration **drops** `anilistId=0` entries with a warning log (proposal 05 §3 Step 3).
- These entries were already broken (history rows unopenable, per Doc 01 §5.1).
- The migration report surfaces the count of dropped entries to the user (Settings → About → Migration status).
**How to test for it:**
- Create a test DB with known `anilistId=0` watch-progress entries.
- Run the migration.
- Verify the entries are dropped + the count is logged.
- Verify linked-anime entries are preserved.

---

### R2 — Download folder move fails partway (file I/O)

**Phase:** 6 (downloads)
**Likelihood:** Medium (SAF can fail due to permission revocation, disk full, folder locked by another app).
**Impact:** High (partial migration leaves some downloads in old structure, some in new).
**Mitigation:**
- The migration runs in the background with a progress notification (proposal 03 §4.2).
- It's **idempotent** — re-running it skips already-migrated folders (proposal 03 §4.2).
- Old folders are **NOT deleted** until all moves succeed (proposal 03 §4.2).
- The dual-read path (proposal 05 §4) recognizes both old and new structures during the transition.
- A "retry migration" button in Settings lets the user re-run if it fails.
**How to test for it:**
- Kill the app mid-migration (at various points: after 0%, 25%, 50%, 75% of folders moved).
- Relaunch — verify the migration resumes + completes.
- Simulate SAF permission revocation mid-migration — verify graceful failure + retry works.
- Simulate disk-full — verify graceful failure + old folders preserved.

---

### R3 — Source-switching fix breaks for episode-number mismatches

**Phase:** 6 (downloads)
**Likelihood:** Low (~5% of cases — Doc 03 §7 of proposal 03).
**Impact:** Medium (two episodes with the same number from different sources share a key → one download shadows the other).
**Mitigation:**
- The redesign keys by `WatchableId + episodeNumber`. If two sources number episodes differently, the user sees the download from whichever source created it first.
- This is the **least-bad outcome** — the alternative (keying by episodeUrl) breaks all source switches.
- A per-anime "episode number mapping" table (future enhancement) can resolve mismatches if they're frequent.
**How to test for it:**
- Create a test anime with Source A (Episode 1 = recap) and Source B (Episode 1 = first real episode).
- Download Episode 1 from Source A.
- Switch to Source B. Verify the download is visible (it plays the Source A file — which may be the "wrong" episode, but it's visible).
- Document this as a known limitation.

---

### R4 — `WatchableIdMigrator` re-key fails (link/unlink event)

**Phase:** 5 (migrator)
**Likelihood:** Low (the re-key logic is per-store, simple).
**Impact:** High (an anime's watch progress / downloads become orphaned after a link/unlink).
**Mitigation:**
- The migrator is **transactional per store** — if one store's re-key fails, the others still complete.
- Each store's `rekey` is idempotent (re-running it on an already-rekeyed store is a no-op).
- Before the re-key, each store exports a backup of its current state (to a `_pre_rekey` pref key).
- If the re-key fails, the user is prompted to retry. The backup can be restored.
**How to test for it:**
- Link an unlinked anime. Verify all stores re-key correctly (watch progress, downloads, metadata, source links).
- Unlink a linked anime. Verify the same.
- Kill the app mid-rekey. Verify it resumes + completes.
- Test with a large library (100+ anime) to ensure the re-key is performant.

---

### R5 — Schema migration fails on corrupted databases

**Phase:** 1 (schema)
**Likelihood:** Low (SQLDelight migrations are well-tested).
**Impact:** High (app can't open the database → bricks the app).
**Mitigation:**
- The `2.sqm` migration is **additive only** (adds columns + indexes; doesn't remove or rename). This is the safest migration type.
- SQLDelight runs migrations in a transaction — if it fails, the DB rolls back to v2.
- On migration failure, the app catches the exception, shows an error, and offers "export data + reset" (last resort).
**How to test for it:**
- Create a v2 database, corrupt it intentionally (e.g., delete a row's required field), run the migration — verify graceful failure.
- Test the migration on a v1 database (pre-`1.sqm`) — verify it chains correctly (v1 → v2 → v3).
- Test on a large v2 database (10,000+ anime) — verify performance.

---

### R6 — Provider-registry fallback causes latency

**Phase:** 7 (browse/search/schedule)
**Likelihood:** Medium (every metadata call now goes through `registry.forCapability(...)` which checks availability).
**Impact:** Low (the availability check is cached with a TTL; the fallback only triggers on actual failure).
**Mitigation:**
- The registry caches `isAvailable()` results with a 5-min TTL (proposal 02 §12).
- The fallback chain is tried only when the primary returns an error, not on every call.
- AniList is the only provider for now — there's no fallback to try (the chain has length 1).
**How to test for it:**
- Benchmark browse/search/schedule latency before + after the registry.
- Verify the overhead is <5ms per call (the registry lookup is an in-memory map).

---

### R7 — Backup/restore cross-version incompatibility

**Phase:** 6 (downloads) + the backup format change
**Likelihood:** Medium (a v0.1 backup restored on v0.2 needs the translator; a v0.2 backup restored on v0.1 needs graceful degradation).
**Impact:** Medium (users who downgrade can't restore their v0.2 backup).
**Mitigation:**
- The backup format bumps to schema v2. The restore path detects the schema version + runs a translator (v1 → v2).
- A v0.2 backup restored on v0.1: the v0.1 app ignores the `watchableId` field (it's additive JSON) + uses the legacy `anilistId`. Graceful degradation.
- The Aniyomi restore translator is updated to produce `WatchableId`-keyed entries.
**How to test for it:**
- Create a v0.1 backup, restore on v0.2 — verify the translator produces correct `WatchableId` entries.
- Create a v0.2 backup, restore on v0.1 — verify graceful degradation (legacy fields work; `watchableId` ignored).
- Test the Aniyomi restore path (`.tachibk` → `WatchableId`-keyed entries).

---

### R8 — The `null → 0` sentinel pollution resurfaces

**Phase:** 1 (`WatchableId` introduction)
**Likelihood:** Low (the type system enforces non-null `WatchableId`).
**Impact:** Medium (if a code path converts `WatchableId` back to a nullable `anilistId` + then to `0`, the pollution returns).
**Mitigation:**
- `WatchableId` is a sealed class — it's never null.
- New code uses `WatchableId`; legacy `anilistId` is only for backward-compat lookups.
- A lint rule / code-review check: no new `anilistId ?: 0` conversions.
**How to test for it:**
- Grep the codebase for `anilistId ?: 0` + `anilistId ?: 0L` — these should not appear in new code.
- Unit test: every `WatchableId` produces a non-empty `stableKey()`.

---

### R9 — Consolidating the 3 AniList HTTP clients breaks the tracker

**Phase:** 7 (client consolidation)
**Likelihood:** Low (the consolidation wraps, not rewrites).
**Impact:** High (tracker sync breaks → user's AniList/MAL progress stops updating).
**Mitigation:**
- The consolidation creates a single `AniListClient` with authenticated + unauthenticated modes.
- `AniListTracker` wraps `AniListClient` (same as `AniListMetadataProvider`).
- The tracker's OAuth flow + token storage is unchanged.
- Extensive testing of tracker sync before + after consolidation.
**How to test for it:**
- Test AniList tracker sync (progress, status, score) before + after consolidation.
- Test MAL tracker sync (it uses its own client — should be unaffected).
- Test the Aniyomi restore translator (it uses the consolidated client).

---

### R10 — Module-extraction refactoring breaks feature modules

**Phase:** 8 (module fixes)
**Likelihood:** Low (pure refactoring; behavior unchanged).
**Impact:** Medium (a feature stops compiling or working).
**Mitigation:**
- Each extraction (e.g., `:core:video-resolver` from `:feature:video-resolver`) is a separate commit.
- CI builds after each extraction.
- The feature modules' imports are updated mechanically (find + replace).
**How to test for it:**
- After each extraction, verify the affected features (watch, download, episode-settings) still work.
- Run the full test suite.

---

### R11 — Migration is too slow for large libraries

**Phase:** 3-6 (all migration steps)
**Likelihood:** Medium (a user with 500+ anime + 10,000+ watch-progress entries might see >30s migration).
**Impact:** Medium (user frustration; "is it frozen?" perception).
**Mitigation:**
- The migration runs in the background with a progress notification (proposal 05 §10).
- Each step is batched (e.g., watch-progress re-keying processes 100 entries per batch with a yield).
- The download folder move is the slowest step — it shows a per-folder progress count.
- If the migration takes >2 minutes, the notification says "This may take a few minutes for large libraries."
**How to test for it:**
- Generate a synthetic library with 500 anime + 10,000 progress entries + 100 downloads.
- Run the migration on a mid-range device — measure time.
- Verify the progress notification updates regularly (not frozen).

---

### R12 — The `preserveLegacyStructure` opt-in fragments the user base

**Phase:** 6 (downloads)
**Likelihood:** Low (most users won't opt in).
**Impact:** Low (the codebase carries two folder-structure code paths).
**Mitigation:**
- The opt-in is off by default — most users get the new structure.
- The legacy code path is removed in a future release (after telemetry shows <1% usage).
**How to test for it:**
- Test both structures (legacy + new) produce correct downloads.
- Verify the toggle in Settings → Downloads works.

---

## Risk summary

| Risk | Phase | Likelihood | Impact | Mitigation strength |
|---|---|---|---|---|
| R1 (progress data loss) | 3 | Certain | Low | ✅ Strong (drop only unusable entries) |
| R2 (folder move fails) | 6 | Medium | High | ✅ Strong (idempotent + retry + dual-read) |
| R3 (ep-number mismatches) | 6 | Low | Medium | ⚠ Acceptable (least-bad outcome) |
| R4 (re-key fails) | 5 | Low | High | ✅ Strong (transactional + backup + retry) |
| R5 (schema migration fails) | 1 | Low | High | ✅ Strong (additive + transactional) |
| R6 (registry latency) | 7 | Medium | Low | ✅ Strong (cached availability) |
| R7 (backup cross-version) | 6 | Medium | Medium | ✅ Strong (translator + graceful degradation) |
| R8 (sentinel pollution) | 1 | Low | Medium | ✅ Strong (type system) |
| R9 (client consolidation) | 7 | Low | High | ✅ Strong (wrap + extensive testing) |
| R10 (module refactoring) | 8 | Low | Medium | ✅ Strong (incremental + CI) |
| R11 (migration too slow) | 3-6 | Medium | Medium | ✅ Strong (background + progress + batching) |
| R12 (legacy structure fragment) | 6 | Low | Low | ✅ Strong (off by default + future removal) |

**The highest-risk phase is Phase 6 (downloads)** — it combines file I/O (R2), source-switching edge cases (R3), backup-format changes (R7), and the slowest migration (R11). It gets the most mitigation: idempotent migration, retry, dual-read paths, progress notification, and extensive testing.

**The lowest-risk phases are 0, 1, 5, 8, 9** — documentation, additive schema, orchestration, pure refactoring, and stub cleanup respectively.

---

## Monitoring + rollback triggers

**Post-release monitoring (after each phase ships):**
- Crash rate (per phase — watch for spikes after a migration).
- Migration-completion rate (Settings → About → Migration status telemetry, if enabled).
- User reports of "missing downloads" or "history rows gone" (the key symptoms of migration failure).

**Rollback triggers:**
- Crash rate >2% above baseline after a phase → investigate + consider rollback.
- Migration-completion rate <95% → the migration is failing for too many users; investigate.
- Any data-loss report confirmed → prioritize a fix + offer the v1 backup restore.

**Rollback procedure:**
- For Phases 1-5 (pre-download): revert the merge; the v1 backups in SharedPreferences restore the old state.
- For Phase 6 (downloads): revert the merge; the download folder move is reversible (move folders back); the v1 `DownloadStore` backup restores the old queue.
- After rollback, the schema is at v3 (can't un-migrate), but the app reads the legacy `anilistId` column + ignores `watchable_id` — backward compatible.

---

*Related: `plan/03_testing_strategy.md` (how to test each risk), `plan/01_phased_implementation.md` (which phase each risk belongs to).*
