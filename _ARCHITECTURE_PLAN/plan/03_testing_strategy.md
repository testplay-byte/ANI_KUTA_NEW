# 03 — Testing Strategy

> **Phase 3 / Implementation.** How to verify each phase works correctly: scenarios to test, data to test with, migration verification, regression testing. Driven by `plan/01_phased_implementation.md` + `plan/02_risk_assessment.md`.

---

## 1. Testing principles

1. **Each phase has unit tests + integration tests + manual tests.**
2. **Migration tests use synthetic data** that mirrors real-world distributions (large libraries, unlinked anime, downloads from multiple sources).
3. **Regression tests run after every phase** to ensure no existing feature broke.
4. **Manual testing covers the golden paths** (browse → details → watch → download → offline playback) for both linked + unlinked anime.
5. **CI gates every phase** — the APK must build + unit tests must pass before merge.

---

## 2. Unit tests (per phase)

### Phase 1 — `WatchableId` + schema
- `WatchableIdTest`: serialization round-trip, `stableKey()` uniqueness, `crossDeviceKey()` reproducibility.
- `WatchableIdBackfillTest`: every existing `animes` row gets a correct `watchableId` (linked for `anilistId != null`; unlinked for `anilistId == null`).
- `SchemaMigrationTest`: v2 → v3 migration adds columns + indexes; backfill populates `watchable_id_json`.

### Phase 2 — Provider abstraction
- `MetadataProviderRegistryTest`: `forCapability` returns the active provider; fallback chain order is correct; `isAvailable()` caching works.
- `AniListMetadataProviderTest`: delegates to `AniListApi` correctly; produces `UnifiedAnime` with `DataSource.ANILIST`.

### Phase 3 — Watch progress migration
- `WatchProgressMigratorTest`: re-keys `"$anilistId:$episodeUrl"` → `"$watchableId:$episodeNumber"`; drops `anilistId=0` entries; preserves linked entries.
- `WatchProgressStoreTest`: `get(watchableId, episodeNumber)` returns the right progress; `save` uses the new key.
- `HistoryRowOpenabilityTest`: history rows for unlinked anime are now openable (the `anilistId=0` bug is fixed).

### Phase 4 — Metadata + source links migration
- `EpisodeMetadataMigratorTest`: re-keys outer map from `anilistId` to `watchableId.stableKey()`.
- `SourceLinkMigratorTest`: re-keys `SourceLinkStore` + `ExtensionLinkStore`.
- `LegacySourcePrefMigratorTest`: renames `source_pref_<anilistId>` → `source_pref_<watchableId>`.

### Phase 5 — `WatchableIdMigrator`
- `WatchableIdMigratorTest`: link event re-keys all stores; unlink event re-keys all stores; idempotent.
- `DownloadRekeyTest` (mock): the download task's key changes correctly on link/unlink.

### Phase 6 — Downloads
- `DownloadManagerTest`: `isEpisodeDownloaded(watchableId, episodeNumber)` finds tasks across source switches.
- `DownloadStorageProviderTest`: no `ANIKUTA/` subfolder; folder name uses `watchableId.stableKey()`.
- `DownloadMigrationTest`: re-keys tasks + moves folders; idempotent; preserves old folders on failure.
- `SourceSwitchingTest`: download a episode from Source A; switch to Source B; verify the download is visible + offline-playable.
- `UnlinkedDownloadTest`: download an episode for an unlinked extension anime; verify it works (no gate).

### Phase 7 — Provider migration
- `BrowseScreenTest`: uses `HomeFeedProvider` via the registry.
- `SearchProviderTest`: AniList tab uses `SearchProvider` via the registry.
- `ScheduleProviderTest`: schedule uses `AiringScheduleProvider` via the registry.
- `AniListClientConsolidationTest`: browse + tracker + Aniyomi translator all use the single client.

### Phase 8 — Module fixes
- `BuildTest`: all 4 violations are fixed; the graph is a DAG; no circular deps.
- `FeatureRegressionTest`: watch, download, episode-settings, backup all still compile + work.

---

## 3. Integration tests (cross-phase)

### The golden-path integration test (runs after every phase)

```
1. Browse home feed → tap an anime → details page loads.
2. Add to library → appears in library.
3. Watch an episode → progress saves.
4. Download an episode → appears in downloads.
5. Switch to offline → play the downloaded episode.
6. Open history → the watched episode appears → tap it → returns to the anime.
7. Link an extension source → episodes load from the extension.
8. Switch source → downloads stay visible.
```

**Run this for both:**
- A linked anime (AniList ID present).
- An unlinked extension anime (AniList ID null).

**After Phase 6, the unlinked path must work fully** (downloads, history, offline playback). Before Phase 6, the unlinked path breaks at step 4 (download gate).

### The source-switching integration test (Phase 6+)

```
1. Link "Frieren" to AniList + Extension A.
2. Download Episode 1 via Extension A.
3. Verify the download is visible + offline-playable.
4. Switch to Extension B (same anime, different source).
5. Verify the download is STILL visible + offline-playable (the source-switching fix).
6. Verify Episode 1 shows as "Downloaded" on the details page.
7. Download Episode 2 via Extension B.
8. Switch back to Extension A.
9. Verify BOTH downloads (Ep 1 from A, Ep 2 from B) are visible.
```

### The link/unlink integration test (Phase 5+)

```
1. Open an unlinked extension anime.
2. Watch an episode (progress saves under WatchableId.Unlinked).
3. Download an episode (download keyed by WatchableId.Unlinked).
4. Link to AniList → WatchableIdMigrator runs.
5. Verify the watch progress + download are now keyed by WatchableId.Linked.
6. Verify the history row opens the anime.
7. Unlink → WatchableIdMigrator runs again.
8. Verify the watch progress + download are now keyed by WatchableId.Unlinked.
9. Verify everything still works.
```

---

## 4. Migration tests (the critical path)

### Synthetic data generator

Create a test utility that generates a synthetic device state:

```kotlin
class SyntheticDataGenerator {
    fun generateLargeLibrary(animeCount: Int = 500): TestData {
        // 80% linked (anilistId != null), 20% unlinked
        // Each anime has 1-50 episodes
        // 30% of episodes have watch progress
        // 10% of anime have downloads (1-5 episodes each)
        // Downloads from 2-3 different sources per anime
        // 5% of watch-progress entries are anilistId=0 (the polluted entries)
    }
}
```

### Migration test scenarios

| Scenario | Data | Expected result |
|---|---|---|
| Small library | 10 anime, 50 episodes, 5 downloads | All migrate cleanly; <1s |
| Medium library | 100 anime, 1000 episodes, 50 downloads | All migrate cleanly; <10s |
| Large library | 500 anime, 10000 episodes, 200 downloads | All migrate cleanly; <60s |
| Unlinked anime | 20% unlinked | All migrate to `WatchableId.Unlinked`; history rows openable |
| Multi-source downloads | 2-3 sources per anime | Downloads survive source switches post-migration |
| Polluted entries | 5% `anilistId=0` progress | Dropped with warning; linked entries preserved |
| Mid-migration crash | Kill app at 0%, 25%, 50%, 75% | Migration resumes + completes on relaunch |
| SAF permission revoked | Revoke mid-download-migration | Graceful failure; old folders preserved; retry works |
| Disk full | Fill disk mid-download-migration | Graceful failure; old folders preserved; retry works |
| v1 backup restore | Restore a v0.1 backup on v0.2 | Translator produces correct `WatchableId` entries |
| v0.2 backup on v0.1 | Restore a v0.2 backup on v0.1 | Graceful degradation (legacy fields work; `watchableId` ignored) |
| Aniyomi backup restore | Restore a `.tachibk` on v0.2 | Translator produces `WatchableId`-keyed entries |

---

## 5. Regression testing

### Automated regression suite

After every phase, run the full existing test suite (`:data:anime:testDebugUnitTest` is the only one CI runs today — expand this to all modules per the CI improvement in Phase 8).

**Regression scenarios (manual, after every phase):**
1. Browse → details → watch (linked anime).
2. Browse → details → watch (unlinked extension anime).
3. Search (AniList tab + Extension tab).
4. Library (grid + list; categories; sort).
5. History (day-grouped; clear-all).
6. Updates + schedule (pull-to-refresh; calendar).
7. Profile (stats with + without AniList linked).
8. Trackers (AniList + MAL login + sync).
9. Backup (create) + restore (ANIKUTA format + Aniyomi format).
10. Downloads (queue; offline playback; settings).

### Per-phase regression focus

| Phase | Regression focus |
|---|---|
| 1 (WatchableId) | Library + details still load (the new field is dormant). |
| 2 (provider-api) | Details page still works (now via registry). |
| 3 (progress) | Watch + history work for linked + unlinked. |
| 4 (metadata + links) | Details page metadata loads; source linking works. |
| 5 (migrator) | Link/unlink preserves all data. |
| 6 (downloads) | Downloads work for linked + unlinked; source switching preserves downloads. |
| 7 (browse/search/schedule) | Browse + search + schedule work via registry. |
| 8 (module fixes) | All features still compile + work. |
| 9 (stubs) | Build still works (no module missing). |

---

## 6. How to verify migration didn't lose data

### Pre-migration snapshot

Before the migration runs (on first launch post-update), the app takes a snapshot:
- Count of `animes` rows.
- Count of `episodes` rows.
- Count of watch-progress entries (per anilistId).
- Count of downloads (per anilistId).
- Count of source links.

Store this in `pref_migration_snapshot_v1`.

### Post-migration verification

After the migration, the app verifies:
- `animes` row count unchanged.
- `episodes` row count unchanged.
- Watch-progress entries: linked count preserved; `anilistId=0` count dropped (expected, logged).
- Downloads: all folders moved; count preserved.
- Source links: count preserved.

If any count mismatches unexpectedly, the migration reports a warning + offers "restore from v1 backup."

### User-facing migration report

Settings → About → Migration status:
- Schema version (v3).
- Migration status (done / in progress / failed).
- Counts: anime migrated, episodes, watch-progress (preserved/dropped), downloads moved, source links re-keyed.
- Timestamp.
- "Restore from v1 backup" button (if available).

---

## 7. Performance testing

### Migration performance

- **Target:** migration completes in <60s for a 500-anime library on a mid-range device (e.g., Pixel 5a).
- **Target:** migration completes in <10s for a 100-anime library.
- **Target:** download folder move completes in <30s for 200 downloads.

If these targets aren't met, optimize the bottleneck (likely the SAF DocumentFile operations for the folder move — batch + parallelize where possible).

### Runtime performance (post-migration)

- **Target:** watch-progress save/load latency <5ms (unchanged from pre-migration).
- **Target:** `isEpisodeDownloaded` lookup <10ms (in-memory path) / <100ms (filesystem fallback).
- **Target:** browse/search/schedule latency unchanged (the registry adds <5ms overhead).

---

## 8. Test data distributions

To mirror real-world data, the synthetic generator uses these distributions:

| Metric | Distribution |
|---|---|
| Anime per user | Long-tail: 50% have <50, 10% have >500 |
| Episodes per anime | Uniform 1-50 (ongoing) or 12-26 (completed) |
| Watch-progress coverage | 30% of watched episodes have progress |
| Download coverage | 10% of anime have downloads; 1-5 episodes each |
| Source diversity | 70% use 1 source; 25% use 2; 5% use 3+ |
| Unlinked anime | 20% of library (the "go without linking" path) |
| Polluted entries | 5% of watch-progress is `anilistId=0` (the bug) |
| Aniyomi restore users | 5% restore from Aniyomi backups |

---

## 9. Manual test checklist (per phase, before merge)

### Phase 6 (downloads) — the critical checklist

- [ ] Download a linked anime episode → file on disk in new structure.
- [ ] Download an unlinked anime episode → works (no gate).
- [ ] Offline playback of a linked download → plays local file.
- [ ] Offline playback of an unlinked download → plays local file.
- [ ] Source switch → download stays visible + offline-playable.
- [ ] Link an unlinked anime → download re-keys + stays visible.
- [ ] Unlink a linked anime → download re-keys + stays visible.
- [ ] Migration: old `ANIKUTA/downloads/anime/` structure → new user-direct structure.
- [ ] Migration: idempotent (re-run is a no-op).
- [ ] Migration: mid-migration crash → resumes.
- [ ] SAF folder: user's chosen folder used directly (no `ANIKUTA/` subfolder for new downloads).
- [ ] `preserveLegacyStructure` opt-in → keeps old nesting.
- [ ] Downloads screen → groups by `watchableId` (survives source switches at library level).
- [ ] Detail page episode row → shows correct download state post-source-switch.

---

## 10. CI improvements (part of Phase 8)

Today CI only runs `:data:anime:testDebugUnitTest` (Doc: CI analysis). Expand to:
- All module unit tests (`./gradlew test`).
- Lint (`./gradlew lint`).
- ktlint/Spotless (`./gradlew spotlessCheck`).
- The migration test suite (synthetic data).
- The golden-path integration test (instrumentation test on an emulator — future).

**Gating:** No merge to `main` unless all tests pass.

---

## 11. Summary

**Testing strategy:**
1. **Unit tests per phase** — the type + migration logic is tested in isolation.
2. **Integration tests** — the golden path (browse → watch → download → offline) for both linked + unlinked anime, after every phase.
3. **Migration tests with synthetic data** — large libraries, multi-source downloads, unlinked anime, mid-migration crashes, SAF failures.
4. **Regression tests** — the full feature suite after every phase.
5. **Pre/post-migration snapshots** — verify no data loss.
6. **Performance tests** — migration <60s for 500 anime; runtime overhead <5ms.
7. **Manual checklist per phase** — especially Phase 6 (downloads).
8. **CI improvements** — expand test coverage + add lint + ktlint.

**The highest testing investment is Phase 6 (downloads)** — it's the highest-risk phase + the one with the most user-visible consequences if it fails.

---

*Related: `plan/01_phased_implementation.md` (which phase to test), `plan/02_risk_assessment.md` (which risks to test for).*
