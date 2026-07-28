# 05 — Migration Strategy

> **Phase 2 / Proposed Architecture.** Detailed plan for migrating existing user data (library entries, downloads, watch history, preferences, extension links) from the current AniList-first identity system to the new `WatchableId`-based system. Grounded in `analysis/01_content_identification_flow.md`, `analysis/06_data_layer_analysis.md`, `proposals/01_internal_id_system.md`, `proposals/03_download_system_redesign.md`.

---

## 1. The migration challenge

The restructuring changes **how anime are identified** across 7 cross-cutting stores + the download folder structure + the backup format. Existing user data must be preserved.

**Data that needs migration:**

| Data | Current key / location | New key / location | Migration complexity |
|---|---|---|---|
| Library (`animes` table) | `_id` PK, `anilist_id` nullable column | Add `watchable_id` JSON column | Low (schema migration) |
| Episodes (`episodes` table) | `anime_id` FK | No change (already local-PK) | None |
| Watch progress (`WatchProgressStore`) | `"$anilistId:$episodeUrl"` in SharedPreferences | `"$watchableId:$episodeNumber"` | **High** (re-key every entry) |
| Playback state (`PlaybackStateStore`) | `"$anilistId:$episodeUrl"` | `"$watchableId:$episodeNumber"` | High |
| Downloads (queue + on-disk) | `"$anilistId:$episodeUrl"` + `<Title [anilistId]>/Episode NNN/` | `"$watchableId:$episodeNumber"` + `<Title [watchableId]>/Episode NNN/` | **High** (re-key + move folders) |
| Episode metadata cache | `anilistId` + `episodeNumber` | `watchableId` + `episodeNumber` | Medium |
| Source links (`SourceLinkStore`) | `anilistId.toString()` | `watchableId.stableKey()` | Medium |
| Extension links (`ExtensionLinkStore`) | `"$sourceId:$url"` → anilistId | `"$sourceId:$url"` → watchableId | Medium |
| Tracker bindings (`animetrack` table) | `anime_id` (local `_id`) | No change (already local-PK) | None |
| Categories (`anime_category` table) | `anime_id` (local `_id`) | No change | None |
| Details-view prefs | Hybrid (already correct) | Generalize to `watchableId` | Low |
| Legacy `source_pref_<anilistId>` | `anilistId` | `watchableId` | Low |
| Backup format (`.anikuta`) | Schema v1 (anilistId-keyed) | Schema v2 (watchableId-keyed) | Medium (version bump + translator) |

---

## 2. Migration principles

1. **Automatic on app update.** The user should not need to take any action. The migration runs on first launch post-update.
2. **Idempotent.** Re-running the migration on an already-migrated device is a no-op (gated by preferences).
3. **Non-destructive.** The old data is preserved until the migration succeeds. If it fails partway, the user can retry.
4. **Observable.** The user sees a migration progress notification. Failures are logged + surfaced.
5. **Backward-compatible during transition.** The app recognizes both old and new formats during the transition period, so a failed migration doesn't brick the app.
6. **Testable.** Each migration step has unit tests with synthetic data (large libraries, downloads from multiple sources, unlinked anime, etc.).

---

## 3. The migration process (step-by-step)

### Step 0: Pre-migration checks

On first launch post-update, before any migration:
- Check available disk space (for the download folder move — needs temporary duplication).
- Check SAF folder is still accessible (the user might have revoked the permission).
- Check `pref_migration_v2_done` — if true, skip entirely.

If pre-checks fail, prompt the user (don't silently skip — they need to know).

### Step 1: Schema migration (SQLDelight)

**`2.sqm` migration file** (v2 → v3):

```sql
-- Add the watchable_id JSON column to animes
ALTER TABLE animes ADD COLUMN watchable_id_json TEXT NOT NULL DEFAULT '{}';

-- Add the missing ADR-024 status columns to episodes (Doc 06 §2.2)
ALTER TABLE episodes ADD COLUMN release_date INTEGER;
ALTER TABLE episodes ADD COLUMN last_refresh INTEGER NOT NULL DEFAULT 0;
ALTER TABLE episodes ADD COLUMN last_metadata_fetch INTEGER;
ALTER TABLE episodes ADD COLUMN next_episode_check INTEGER;

-- Add unique constraint on episodes(anime_id, episode_number)
CREATE UNIQUE INDEX IF NOT EXISTS idx_episodes_anime_epnum
ON episodes(anime_id, episode_number);

-- Add unique constraint on animes(source_id, url) for unlinked
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_source_url
ON animes(source_id, url);
```

SQLDelight handles this automatically on database open. No data loss.

### Step 2: Populate `watchable_id_json` for existing `animes` rows

```kotlin
class WatchableIdBackfill {
    fun backfill() {
        val allAnime = animeRepository.getAll()
        for (anime in allAnime) {
            val watchableId = when {
                anime.anilistId != null ->
                    WatchableId.Linked(MetadataProviderId.ANILIST, anime.anilistId.toString())
                anime.sourceId != 0L ->
                    WatchableId.Unlinked(anime.sourceId, anime.url, titleHash(anime.title))
                else ->
                    WatchableId.Unlinked(0L, anime.url, titleHash(anime.title))  // edge case
            }
            animeRepository.updateWatchableId(anime.id, watchableId)
        }
    }
}
```

This is a one-time backfill. Every existing `animes` row gets a `watchable_id_json` value derived from its existing `anilist_id` (if present) or `source_id + url + title` (if unlinked).

### Step 3: Re-key `WatchProgressStore`

```kotlin
class WatchProgressMigrator {
    fun migrate() {
        val oldMap = watchProgressStore.getAllRaw()   // Map<String, Progress> keyed by "$anilistId:$episodeUrl"
        val newMap = mutableMapOf<String, Progress>()

        for ((oldKey, progress) in oldMap) {
            val parts = oldKey.split(":", limit = 2)
            val anilistId = parts[0].toIntOrNull()
            val episodeUrl = parts.getOrNull(1) ?: continue

            if (anilistId == null || anilistId == 0) {
                // Unlinked anime progress — was saved under "0:<url>". Try to resolve the anime
                // by source+url (if we can find which source). If we can't, drop it (it was
                // already broken — unopenable history rows, per Doc 01 §5.1).
                logger.warn("Dropping unlinked progress: $oldKey")
                continue
            }

            val anime = animeRepository.getByAnilistId(anilistId) ?: continue
            val watchableId = anime.watchableId   // now populated by Step 2

            // Find the episode by URL to get its number
            val episode = episodeRepository.getByAnimeIdAndUrl(anime.id, episodeUrl)
            val episodeNumber = episode?.episodeNumber ?: continue

            val newKey = "${watchableId.stableKey()}:${episodeNumberKey(episodeNumber)}"
            newMap[newKey] = progress
        }

        watchProgressStore.replaceAll(newMap)
    }
}
```

**Edge case — `anilistId == 0` entries:** These are the polluted "0:<url>" entries from unlinked anime (Doc 01 §6.3). They're unopenable anyway. The migration drops them (with a warning log). This is data loss, but the data was already unusable.

**Edge case — episode not found by URL:** If the episode URL doesn't match any episode in the DB (e.g., the source changed), the progress entry is dropped. This is the same data-loss case as today (source-switching break), just made explicit.

### Step 4: Re-key `PlaybackStateStore`

Same logic as Step 3, applied to `PlaybackStateStore` (resume positions).

### Step 5: Re-key `DownloadStore` + move on-disk folders

(See `proposals/03_download_system_redesign.md` §4 for the full `DownloadMigration`.)

- Re-key every `DownloadTask` from `"$anilistId:$episodeUrl"` to `"$watchableId:$episodeNumber"`.
- Move every on-disk anime folder from `<root>/ANIKUTA/downloads/anime/<Title [anilistId]>/` to `<root>/<Title [watchableId]>/`.
- Update each episode's `metadata.json` with the new `watchableId` + `sourceId`.
- Delete the old `ANIKUTA/downloads/anime/` structure if all moves succeed.

**This is the riskiest step** (file I/O on potentially large libraries). It runs in the background with a progress notification. If it fails partway, the old folders are preserved and the user can retry.

### Step 6: Re-key `EpisodeMetadataCache`

```kotlin
class EpisodeMetadataMigrator {
    fun migrate() {
        val oldCache = episodeMetadataCache.getAllRaw()  // Map<String, Map<String, Item>> keyed by anilistId
        val newCache = mutableMapOf<String, Map<String, Item>>()

        for ((anilistIdStr, epMap) in oldCache) {
            val anilistId = anilistIdStr.toIntOrNull() ?: continue
            val anime = animeRepository.getByAnilistId(anilistId) ?: continue
            val watchableId = anime.watchableId
            newCache[watchableId.stableKey()] = epMap   // inner key (episodeNumber) unchanged
        }

        episodeMetadataCache.replaceAll(newCache)
    }
}
```

### Step 7: Re-key `SourceLinkStore` + `ExtensionLinkStore`

```kotlin
class SourceLinkMigrator {
    fun migrate() {
        // SourceLinkStore: Map<anilistId.toString(), SourceLinkItem> → Map<watchableId.stableKey(), SourceLinkItem>
        val oldLinks = sourceLinkStore.getAllRaw()
        val newLinks = mutableMapOf<String, SourceLinkItem>()
        for ((anilistIdStr, link) in oldLinks) {
            val anilistId = anilistIdStr.toIntOrNull() ?: continue
            val watchableId = WatchableId.Linked(MetadataProviderId.ANILIST, anilistIdStr)
            newLinks[watchableId.stableKey()] = link
        }
        sourceLinkStore.replaceAll(newLinks)

        // ExtensionLinkStore: Map<"$sourceId:$url", Int(anilistId)> → Map<"$sourceId:$url", WatchableId>
        val oldExtLinks = extensionLinkStore.getAllRaw()
        val newExtLinks = mutableMapOf<String, WatchableId>()
        for ((key, anilistId) in oldExtLinks) {
            val watchableId = WatchableId.Linked(MetadataProviderId.ANILIST, anilistId.toString())
            newExtLinks[key] = watchableId
        }
        extensionLinkStore.replaceAll(newExtLinks)
    }
}
```

### Step 8: Re-key legacy `source_pref_<anilistId>`

```kotlin
class LegacySourcePrefMigrator {
    fun migrate() {
        // For each SharedPreferences key matching "source_pref_<anilistId>"
        // → rename to "source_pref_<watchableId.stableKey()>"
    }
}
```

### Step 9: Mark migration complete

```kotlin
preferences.setMigrationV2Done(true)
```

All future launches skip the migration.

---

## 4. The transition period (dual-format support)

During the transition (between the migration starting and completing), the app must recognize both old and new formats. This is critical for crash-resilience — if the app crashes mid-migration, it must still work on the next launch.

**Strategy:** Each store's read path checks both formats:
- `WatchProgressStore.get(watchableId, episodeNumber)`:
  1. Try the new key `"${watchableId.stableKey()}:${episodeNumber}"`.
  2. If not found AND `watchableId` is `Linked(ANILIST, id)`, try the old key `"${id}:${episodeUrl}"` (requires looking up the episode URL).
  3. If still not found, return null.

**This dual-read path is removed in a future release** (after telemetry confirms <0.1% of users are still hitting the old-format fallback).

---

## 5. Rollback plan

If the migration catastrophically fails (e.g., corrupts data):

1. **The `2.sqm` schema migration is irreversible** (SQLite `ALTER TABLE ADD COLUMN` can't be undone without a table rebuild). But it's additive (only adds columns), so it doesn't destroy data.
2. **The cross-cutting store re-keying IS reversible** if we keep a backup of the old SharedPreferences. Before Step 3, `WatchProgressStore` exports its current map to a backup pref key (`pref_watch_progress_map_v1_backup`). If rollback is needed, restore from the backup.
3. **The download folder move is reversible** by moving folders back. But this is fragile — if the user added new downloads post-migration, they'd be in the new structure.

**Recommendation:** Keep the v1 backups for one app version (e.g., v0.2 → v0.3 migrates; v0.4 removes the backups). The user can downgrade to v0.1 (pre-migration) if needed during that window. After v0.4, the migration is permanent.

**If rollback is needed mid-migration:**
- Stop the migration.
- Restore `WatchProgressStore` + `PlaybackStateStore` + `EpisodeMetadataCache` + `SourceLinkStore` + `ExtensionLinkStore` from their v1 backups.
- Restore `DownloadStore` from its v1 backup.
- Move on-disk download folders back to the old structure (best-effort).
- Log the failure + surface to the user.

---

## 6. How existing downloads on disk are preserved + re-keyed

(Detailed in `proposals/03_download_system_redesign.md` §4. Summary here.)

**Before migration:**
```
<USER_FOLDER>/ANIKUTA/downloads/anime/Frieren [12345]/Episode 001/video.mp4
```

**After migration:**
```
<USER_FOLDER>/Frieren [al-12345]/Episode 001/video.mp4
```

**The process:**
1. Parse the old folder name `<Title [anilistId]>` → `(title, anilistId)`.
2. Resolve `WatchableId.Linked(ANILIST, anilistId.toString())`.
3. Compute the new folder name `<Title [${sanitize(watchableId.stableKey())}]>`.
4. Move the folder (SAF `DocumentFile.renameTo` + `moveContents`).
5. Update each `Episode NNN/data/metadata.json` with the new `watchableId` + `sourceId`.
6. Re-key the corresponding `DownloadTask` in `DownloadStore`.

**Idempotent:** If the new folder already exists (re-run after partial failure), skip.

**Preserves the user's chosen folder:** The SAF folder URI (`DownloadPreferences.downloadFolderUri`) is unchanged. Only the contents move.

---

## 7. How the app handles the transition period

**Three states:**

1. **Pre-migration (v0.1):** Old format only. `anilistId`-keyed everywhere.
2. **Migration in progress:** Background migration + dual-read paths. User sees a notification.
3. **Post-migration (v0.2+):** New format only. `WatchableId`-keyed. Old-format backups retained for one version.

**The dual-read paths** (§4) ensure the app works during state 2. If the migration crashes at step 5 (downloads), watch progress + history (steps 3-4, already done) use the new format, while downloads use the old format (via the fallback read path). The app remains functional.

---

## 8. Testing the migration

(See `plan/03_testing_strategy.md` for the full testing plan.)

**Key test scenarios:**
- Large library (500+ anime, 10,000+ episodes).
- Downloads from multiple sources (to test the source-switching fix).
- Unlinked extension anime (to test `WatchableId.Unlinked` migration).
- Corrupted entries (anilistId = 0, missing episodes, orphaned source links).
- Mid-migration crash (kill the app at each step; verify it resumes correctly).
- Restore from v1 backup (verify rollback works).
- Backup created on v0.1, restored on v0.2 (verify the backup translator).
- Backup created on v0.2, restored on v0.1 (verify graceful degradation — old app ignores new fields).

---

## 9. Migration complexity + risk per step

| Step | Complexity | Risk | Mitigation |
|---|---|---|---|
| 1 (Schema) | Low | Low (additive) | SQLDelight handles automatically |
| 2 (Backfill watchable_id) | Low | Low | One-time; idempotent |
| 3 (WatchProgress re-key) | Medium | Medium (data loss for anilistId=0 entries) | Drop only unusable entries; log |
| 4 (PlaybackState re-key) | Medium | Medium | Same as Step 3 |
| 5 (Downloads re-key + move) | **High** | **High** (file I/O) | Background + progress + retry + keep old until success |
| 6 (EpisodeMetadata re-key) | Low | Low | In-memory; fast |
| 7 (SourceLink re-key) | Low | Low | In-memory; fast |
| 8 (Legacy source_pref) | Low | Low | In-memory; fast |
| 9 (Mark done) | Trivial | None | — |

**Step 5 is the highest risk.** It moves files on disk, which is slow + can fail (SAF permission revoked, disk full, folder locked). The mitigation: run in background, keep old folders until all moves succeed, allow retry.

---

## 10. Communication to users

**In-app notification on first launch post-update:**
> "ANIKUTA is updating its data format. Your library, downloads, and history are being migrated. This may take a few minutes for large libraries."

**Progress notification (for Step 5):**
> "Migrating downloads... (42/150)"

**Completion:**
> "Migration complete. Your data is ready."

**Failure:**
> "Migration could not complete. Your data is preserved. [Retry] [Report]"

**Settings → About → Migration status** (for debugging):
- Current schema version.
- Migration v2 status (done / in progress / failed).
- Counts: anime migrated, downloads moved, progress entries re-keyed, entries dropped.

---

## 11. Summary

**Recommendation:**
1. **Automatic migration on app update**, gated by `pref_migration_v2_done`.
2. **9-step process:** schema migration → backfill `watchable_id` → re-key WatchProgress → re-key PlaybackState → re-key + move Downloads → re-key EpisodeMetadata → re-key SourceLinks → re-key legacy prefs → mark done.
3. **Dual-read paths during transition** so the app works even if the migration crashes mid-way.
4. **v1 backups retained for one version** for rollback.
5. **Step 5 (downloads) is the highest risk** — background + progress + retry + keep-old-until-success.
6. **Tested with synthetic large libraries, multi-source downloads, unlinked anime, and mid-migration crashes.**

**Why this approach:**
- The user takes no action (automatic).
- Data is preserved (non-destructive, with backups).
- The app keeps working during the transition (dual-read paths).
- The riskiest step (downloads) has the most mitigation.
- The migration is testable + observable + rollback-able.

**Driven by evidence:** Doc 01 (the 7 anilistId-keyed stores), Doc 06 (the schema + preference key catalog), proposal 03 (the download-specific migration).

---

*Related: `plan/01_phased_implementation.md` (when each migration step ships), `plan/02_risk_assessment.md` (per-step risks), `plan/03_testing_strategy.md` (test scenarios).*
