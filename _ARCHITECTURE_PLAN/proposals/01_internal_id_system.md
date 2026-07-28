# 01 — Internal Content Identification System

> **Phase 2 / Proposed Architecture.** Proposes a system for identifying anime independently of any external provider. Evaluates multiple approaches (hash-based, name-based, composite, UUID, hybrid) and recommends one with clear reasoning. Grounded in the Phase 1 findings (`analysis/01_content_identification_flow.md`, `analysis/06_data_layer_analysis.md`).

---

## 1. The problem

ANIKUTA has **two parallel identity systems that don't fully interoperate** (Doc 01 §1):

1. **SQLDelight layer** — local-PK-based (`animes._id`). AniList-optional.
2. **Cross-cutting stores** (`WatchProgressStore`, `PlaybackStateStore`, `DownloadTask`, `EpisodeMetadataCache`, `SourceLinkStore`, legacy `source_pref_<anilistId>`) — anilistId-keyed, non-nullable. AniList-required.

This produces **7 concrete failure modes** for unlinked extension anime (Doc 01 §6): downloads blocked, episode metadata skipped, watch progress polluted, tracker sync skipped, backup partially excluded, updates skipped, library sort broken.

The composite key `"$anilistId:$episodeUrl"` is duplicated across **9+ files** with no central helper (Doc 02 §3), and it breaks when source switching changes the `episodeUrl` (Doc 02 §10).

**The goal:** a universal internal identifier that:
- Works for any content regardless of provider linkage.
- Is reproducible across users/devices (for backup/restore).
- Handles the same anime from different sources.
- Handles content not on any provider.
- Interacts cleanly with external provider IDs (AniList, MAL, TMDB).
- Has acceptable collision risk.
- Is type-safe (eliminates the `null → 0` sentinel pollution).

---

## 2. Approaches evaluated

### 2.1 Approach A — Hash of canonical title (deterministic, reproducible)

**Mechanism:** `internalId = SHA256(normalize(title))` where `normalize` = lowercase, strip special characters, collapse whitespace, optionally strip common suffixes ("Season 1", "2nd Season", etc.).

**Pros:**
- ✅ Deterministic — two users watching "Frieren" get the same `internalId`.
- ✅ Reproducible across devices — backup/restore "just works" without an ID-mapping table.
- ✅ Works for content not on any provider.
- ✅ Survives provider changes — the ID doesn't depend on AniList/MAL/TMDB.

**Cons:**
- ❌ **Collision risk** — series with very similar names ("Bleach" vs "Bleach: Thousand-Year Blood War" after normalization; "Attack on Titan" vs "Attack on Titan: Junior High"). Normalization heuristics reduce but don't eliminate this.
- ❌ Title ambiguity — the same anime can have multiple titles (romaji, English, native, synonyms). Which one is canonical? AniList's `title.english ?: title.romaji` is a heuristic, not a guarantee.
- ❌ Different anime can share a title (rare but real — e.g., two different "Kanon" anime from 2002 and 2006).
- ❌ Hashing is irreversible — can't recover the title from the ID (debugging is harder).

**Verdict:** Too risky as a sole identifier. Collision rate is low but non-zero, and a collision silently merges two different anime (data corruption). Acceptable as a *fallback* component when no provider ID exists.

### 2.2 Approach B — Database UUID (random, non-reproducible)

**Mechanism:** `internalId = UUID.randomUUID()` generated on first encounter, stored in `animes._id` (or a new `internal_id` column).

**Pros:**
- ✅ Zero collision risk.
- ✅ Simple to implement.
- ✅ Works for any content.

**Cons:**
- ❌ **Not reproducible across users/devices** — two users watching "Frieren" get different UUIDs. Backup/restore requires an ID-mapping table (resolve by title/provider ID on restore).
- ❌ Breaks the cross-device restore use case — the backup must resolve "which anime is this?" on the target device, which brings back the title-matching problem (Approach A's weakness).
- ❌ Doesn't help with the "same anime from different sources" scenario — each source would create a separate UUID unless an explicit merge step runs.

**Verdict:** Solves the collision problem but reintroduces the cross-device problem. Not ideal as the primary identifier, but acceptable as the local DB PK (which is already `animes._id` AUTOINCREMENT — a UUID would be a drop-in replacement with no real benefit).

### 2.3 Approach C — Composite key (title hash + episode count + year)

**Mechanism:** `internalId = SHA256(normalize(title) + "|" + totalEpisodes + "|" + startYear)`.

**Pros:**
- ✅ Deterministic + reproducible.
- ✅ Lower collision risk than Approach A — "Bleach" (366 eps, 2004) vs "Bleach: TYBW" (52 eps, 2022) would differ.
- ✅ Works for content not on any provider (if episode count + year are known).

**Cons:**
- ❌ **Episode count and year are often unknown or inaccurate** for ongoing anime, extension-only anime, or anime with ambiguous season splits.
- ❌ Episode count varies by source (some sources count OVAs, some don't).
- ❌ Year can be ambiguous (broadcast year vs production year vs streaming year).
- ❌ Still has the title-canonicalization problem.
- ❌ More complex to compute and debug.

**Verdict:** Better than Approach A but fragile. The "unknown episode count" case is common enough to make this unreliable.

### 2.4 Approach D — Provider-ID-based (current approach, formalized)

**Mechanism:** `internalId = anilistId` (with fallback to `sourceId + url` for unlinked). This is essentially what the app does today, just typed.

**Pros:**
- ✅ Minimal change from current architecture.
- ✅ AniList IDs are stable, unique, and globally shared.
- ✅ Backup/restore works for linked anime (anilistId is the cross-device key).

**Cons:**
- ❌ Doesn't solve the core problem — unlinked anime remain second-class.
- ❌ The fallback (`sourceId + url`) only works if the same extension is installed on the target device.
- ❌ Hardcodes AniList as the identity provider (the exact thing we're trying to decouple from).

**Verdict:** This is the status quo. It's the baseline to improve upon, not the destination.

### 2.5 Approach E — Hybrid: provider-ID-first + deterministic-fallback (RECOMMENDED)

**Mechanism:** A typed `WatchableId` value class with two variants:

```kotlin
@Serializable
sealed class WatchableId {
    /** A content entry linked to a metadata provider. */
    @Serializable
    data class Linked(
        val provider: MetadataProvider,   // ANILIST, MAL, TMDB, ...
        val remoteId: String,             // provider's stable ID (as a string for generality)
    ) : WatchableId() {
        init { require(remoteId.isNotBlank()) }
    }

    /** A content entry with no provider linkage — identified by its source + url + a title hash. */
    @Serializable
    data class Unlinked(
        val sourceId: Long,
        val sourceUrl: String,
        val titleHash: String,            // SHA256(normalize(title)) — for cross-device reproducibility
    ) : WatchableId() {
        init { require(sourceUrl.isNotBlank()); require(titleHash.length == 64) }
    }

    /** A stable, serializable string key for use in SharedPreferences / composite keys. */
    fun stableKey(): String = when (this) {
        is Linked   -> "${provider.id}:${remoteId}"
        is Unlinked -> "ext:${sourceId}:${sourceUrl}"   // titleHash is auxiliary, not in the key
    }

    /** A reproducible cross-device identity (for backup/restore). */
    fun crossDeviceKey(): String = when (this) {
        is Linked   -> "${provider.id}:${remoteId}"      // stable across devices
        is Unlinked -> "th:${titleHash}"                 // stable across devices IF title matches
    }
}

enum class MetadataProvider(val id: String) {
    ANILIST("al"),
    MAL("mal"),
    TMDB("tmdb"),
    KITSU("kitsu"),
}
```

**How it works in practice:**

1. **Discovery:** When an anime is first encountered:
   - From AniList → `WatchableId.Linked(ANILIST, anilistId.toString())`.
   - From an extension (linked) → `WatchableId.Linked(ANILIST, anilistId.toString())` (the link is to AniList).
   - From an extension (unlinked) → `WatchableId.Unlinked(sourceId, sourceUrl, titleHash)`.
   - From MAL (future) → `WatchableId.Linked(MAL, malId.toString())`.

2. **Storage in `animes` table:** Add two columns:
   ```sql
   ALTER TABLE animes ADD COLUMN watchable_id_type TEXT;       -- "linked" or "unlinked"
   ALTER TABLE animes ADD COLUMN watchable_id_provider TEXT;   -- "al", "mal", "tmdb", or NULL
   ALTER TABLE animes ADD COLUMN watchable_id_remote TEXT;     -- the remote ID, or NULL
   ALTER TABLE animes ADD COLUMN watchable_id_source_id INTEGER; -- for unlinked
   ALTER TABLE animes ADD COLUMN watchable_id_source_url TEXT;   -- for unlinked
   ALTER TABLE animes ADD COLUMN watchable_id_title_hash TEXT;   -- for unlinked (cross-device)
   ```
   Or, more simply, a single denormalized `watchable_id_stable_key TEXT UNIQUE` column + a `watchable_id_cross_device_key TEXT` column. (See §4 for the schema decision.)

3. **Cross-cutting stores:** Every store (`WatchProgressStore`, `DownloadTask.key`, `EpisodeMetadataCache`, `SourceLinkStore`) migrates from `"$anilistId:$episodeUrl"` to `WatchableId.stableKey() + ":" + episodeNumber` (episode-number-based, not episodeUrl-based — see Doc 03's source-switching fix).

4. **Backup/restore:** Serialize `WatchableId` directly. On restore:
   - `Linked` → resolve by `(provider, remoteId)` (works across devices).
   - `Unlinked` → resolve by `titleHash` (works across devices IF the same anime is found by title), with `sourceId + url` as a secondary fallback (works IF the same extension is installed).

5. **Linking upgrade:** When an unlinked anime gets linked to AniList, the `WatchableId` changes from `Unlinked` to `Linked`. All cross-cutting store entries keyed by the old `Unlinked.stableKey()` must be **migrated** to the new `Linked.stableKey()`. This is a one-time migration per link event, handled by a `WatchableIdMigrator`.

---

## 3. Why Approach E (hybrid) over the alternatives

| Criterion | A (title hash) | B (UUID) | C (composite) | D (provider-ID, current) | **E (hybrid)** |
|---|---|---|---|---|---|
| Collision risk | High | None | Medium | None (for linked) | **Low** (linked: none; unlinked: title-hash fallback is rare) |
| Cross-device reproducibility | ✅ | ❌ | ✅ | ✅ (linked) / ❌ (unlinked) | **✅** (linked: by provider ID; unlinked: by title hash) |
| Works without provider | ✅ | ✅ | ⚠ (needs ep count + year) | ❌ | **✅** |
| Survives provider change | ✅ | ✅ | ✅ | ❌ | **✅** |
| Same anime, different sources | ❌ (title collision) | ❌ (separate UUIDs) | ⚠ | ⚠ | **✅** (linked: same provider ID; unlinked: separate, but linkable later) |
| Type safety | ❌ (string) | ❌ (UUID string) | ❌ (string) | ❌ (Int + null) | **✅** (sealed class) |
| Migration effort | Large | Medium | Large | n/a (current) | **Medium** |
| Debuggability | ❌ (irreversible hash) | ⚠ | ❌ | ✅ | **✅** (Linked shows provider+ID; Unlinked shows source+url) |

**Approach E wins because:**
1. It uses the **most stable available identifier** at any time — provider ID when linked, source+url+title-hash when unlinked.
2. It's **type-safe** — the `null → 0` sentinel pollution is eliminated at the type level.
3. It **upgrades gracefully** — an unlinked anime can be linked later, and the migration is a well-defined event.
4. It's **reproducible across devices** for both linked (by provider ID) and unlinked (by title hash) anime.
5. It **doesn't over-engineer** — it doesn't try to solve collisions for unlinked anime with heuristics; it accepts that unlinked anime are identified by their source+url (stable within a device) + title hash (stable across devices, with residual collision risk that's acceptable for the fallback case).

**The key insight:** most anime *will* be linked to a provider (AniList today; MAL/TMDB tomorrow). The unlinked case is the minority. Approach E optimizes for the linked case (zero collision, perfect reproducibility) while providing a reasonable fallback for the unlinked case (source+url for in-device stability, title hash for cross-device best-effort).

---

## 4. Schema design

### 4.1 Option A: Denormalized columns (RECOMMENDED)

Add a single `watchable_id` JSON column to `animes`:

```sql
ALTER TABLE animes ADD COLUMN watchable_id_json TEXT NOT NULL DEFAULT '{}';
-- JSON: {"type":"linked","provider":"al","remoteId":"12345"}
--    or {"type":"unlinked","sourceId":100,"sourceUrl":"https://...","titleHash":"abc..."}
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_watchable_id ON animes(watchable_id_json);
```

**Pros:** Single column, easy to serialize/deserialize, the unique index is straightforward.
**Cons:** JSON in SQLite is less queryable. (But we rarely query by `watchable_id` directly — we resolve via `anilist_id` or `source_id + url`.)

### 4.2 Option B: Normalized columns

```sql
ALTER TABLE animes ADD COLUMN wid_type TEXT NOT NULL DEFAULT 'unlinked';     -- 'linked' | 'unlinked'
ALTER TABLE animes ADD COLUMN wid_provider TEXT;                              -- 'al','mal','tmdb' (NULL if unlinked)
ALTER TABLE animes ADD COLUMN wid_remote_id TEXT;                             -- provider ID (NULL if unlinked)
ALTER TABLE animes ADD COLUMN wid_source_id INTEGER;                          -- for unlinked
ALTER TABLE animes ADD COLUMN wid_source_url TEXT;                            -- for unlinked
ALTER TABLE animes ADD COLUMN wid_title_hash TEXT;                            -- for unlinked (cross-device)
```

**Pros:** Queryable (e.g., `WHERE wid_provider = 'al' AND wid_remote_id = ?`).
**Cons:** 6 columns, more migration boilerplate.

### 4.3 Recommendation: Option A (denormalized JSON)

The `watchable_id` is an opaque identity token — we rarely query by its internals. We resolve via `anilist_id` (for backward compat) or `source_id + url` (for unlinked). The JSON column keeps the schema clean and the migration simple. The existing `anilist_id` and `source_id + url` columns remain for backward compatibility + efficient lookups.

**The `anilist_id` column stays** (not removed) — it's the lookup key for AniList-linked anime. But the cross-cutting stores migrate from `anilistId` to `WatchableId.stableKey()`.

---

## 5. How it handles the key scenarios

### 5.1 An anime that exists on one provider but not another

- User discovers it via AniList → `WatchableId.Linked(ANILIST, "12345")`.
- AniList has it; MAL doesn't. The `WatchableId` is `Linked(ANILIST, "12345")`.
- If the user later links a MAL account, the anime is already identified — no re-linking needed.
- If AniList goes down, the `WatchableId` is still valid (it's a stored value, not a live lookup).

### 5.2 The same anime watched from different sources/extensions

- User watches "Frieren" via Extension A → `WatchableId.Linked(ANILIST, "154587")` (because it's linked to AniList).
- User switches to Extension B → same `WatchableId` (the link is to AniList, not to the extension).
- Downloads, watch progress, history all key off `WatchableId.stableKey()` + `episodeNumber` → **they survive the source switch** (this fixes Doc 02 §10).

### 5.3 Content shared between users (backup/restore)

- User A backs up "Frieren" → backup entry has `WatchableId.Linked(ANILIST, "154587")`.
- User B restores → resolves by `(ANILIST, "154587")` → finds (or creates) the local `animes` row → all cross-cutting store entries migrate to the new local `_id` but keep the same `WatchableId.stableKey()`.
- **For unlinked anime:** User A backs up an unlinked extension anime → `WatchableId.Unlinked(sourceId, url, titleHash)`. User B restores → tries `titleHash` first (cross-device), falls back to `sourceId + url` (same-extension). If neither matches, the anime is created fresh on User B's device.

### 5.4 An anime that was linked to AniList and then unlinked

- This is the **linking-upgrade-in-reverse** scenario. When a user unlinks:
  - The `WatchableId` changes from `Linked(ANILIST, "12345")` to `Unlinked(sourceId, url, titleHash)`.
  - All cross-cutting store entries keyed by the old `Linked.stableKey()` must migrate to the new `Unlinked.stableKey()`.
  - **The anime retains its identity and downloads** — this is the key requirement from the prompt.
- The `WatchableIdMigrator` handles this (same as the link event, in reverse).

### 5.5 Content not on any provider

- User discovers via an extension, chooses "go without linking" → `WatchableId.Unlinked(sourceId, url, titleHash)`.
- Everything works: downloads, watch progress, history, library, categories. Tracker sync is the only thing that can't work (no provider to sync to), and that's correct.

### 5.6 Interaction with external provider IDs

- `WatchableId.Linked(provider, remoteId)` IS the external provider ID, typed.
- Multiple providers can be linked simultaneously in the future (e.g., `Linked(ANILIST, "12345")` + a separate `Linked(MAL, "67890")` in a `provider_links` table). For now, one provider link per anime is sufficient.
- The `animetrack` table already handles tracker-side IDs (`remote_id` for AniList/MAL trackers) — this is orthogonal to `WatchableId`.

---

## 6. Collision risk + mitigation

### 6.1 Linked anime: zero collision risk

`WatchableId.Linked(ANILIST, "12345")` is globally unique by definition (AniList IDs are unique). Same for MAL/TMDB.

### 6.2 Unlinked anime: title-hash collision risk

The `titleHash` is `SHA256(normalize(title))`. Collision probability:
- SHA256 itself: negligible (~10^-38 for accidental collision).
- **Title normalization collisions:** this is the real risk. Two different anime with the same normalized title would produce the same hash. Examples:
  - "Kanon" (2002) vs "Kanon" (2006) — same title, different anime. Normalization doesn't distinguish them.
  - "Fate/Zero" vs "Fate Zero" — normalization would merge them (which is correct, they're the same anime).
  - "Bleach" vs "Bleach" (different year) — same title, different anime.

**Mitigation:**
1. **The `titleHash` is ONLY used for cross-device restore fallback.** In-device, the `Unlinked` is keyed by `sourceId + url` (which is unique per source). Two unlinked anime on the same device with the same title hash but different sources get different `stableKey()` values.
2. **On restore, if `titleHash` matches multiple anime on the target device, the restore prompts the user to disambiguate** (or picks the one with a matching `sourceId + url` if the same extension is installed).
3. **Normalization is conservative** — it strips common suffixes ("Season N", "Nth Season", "Cour N") but preserves distinguishing tokens. The normalization rules are documented + tested.
4. **The collision risk is accepted** for the unlinked cross-device case because: (a) it's rare (most anime are linked), (b) it only affects restore (not normal operation), (c) the user can disambiguate.

### 6.3 The `sourceId + url` uniqueness

`selectBySourceAndUrl` currently has no unique index (Doc 06 §2.1). This should be fixed:
```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_source_url ON animes(source_id, url);
```
This ensures in-device uniqueness for unlinked anime.

---

## 7. The `WatchableIdMigrator` — link/unlink events

When a `WatchableId` changes (link or unlink event), all cross-cutting store entries must be re-keyed. This is the `WatchableIdMigrator`'s job.

```kotlin
class WatchableIdMigrator(
    private val watchProgressStore: WatchProgressStore,
    private val playbackStateStore: PlaybackStateStore,
    private val downloadManager: DownloadManager,
    private val episodeMetadataCache: EpisodeMetadataCache,
    private val sourceLinkStore: SourceLinkStore,
    // ... all anilistId-keyed stores
) {
    fun migrate(oldId: WatchableId, newId: WatchableId) {
        watchProgressStore.rekey(oldId, newId)
        playbackStateStore.rekey(oldId, newId)
        downloadManager.rekey(oldId, newId)
        episodeMetadataCache.rekey(oldId, newId)
        sourceLinkStore.rekey(oldId, newId)
        // ...
    }
}
```

**Each store implements a `rekey(oldId, newId)` method** that iterates its entries, finds those keyed by `oldId.stableKey()`, and rewrites them with `newId.stableKey()`.

**This is a well-bounded, testable operation** — it's the same logic for every store, and it runs at a single point in time (the link/unlink event). It's not a rolling migration.

---

## 8. Integration with the existing system

### 8.1 The `Anime` domain model

Add a `watchableId: WatchableId` field to `Anime` (and `UnifiedAnime`). The existing `anilistId: Int?` stays (for backward compat + AniList-specific lookups), but new code uses `watchableId`.

### 8.2 The cross-cutting stores

Each store changes its key type:
- `WatchProgressStore`: `Map<String, Progress>` where key = `watchableId.stableKey() + ":" + episodeNumber` (NOT episodeUrl — this fixes the source-switching break).
- `DownloadTask.key`: `watchableId.stableKey() + ":" + episodeNumber`.
- `EpisodeMetadataCache`: outer key = `watchableId.stableKey()`, inner = `episodeNumber`.
- `SourceLinkStore`: key = `watchableId.stableKey()`.

### 8.3 The `DownloadManager` interface

The 5 anilistId-taking methods change to take `WatchableId`:
```kotlin
interface DownloadManager {
    fun isEpisodeDownloaded(watchableId: WatchableId, episodeNumber: Float): Boolean
    fun findTask(watchableId: WatchableId, episodeNumber: Float): DownloadTask?
    fun getDownloadedEpisodes(watchableId: WatchableId): List<DownloadedEpisode>
    // ...
}
```

### 8.4 The hard gate

The `if (anilistId == 0) { Toast; return }` gate at `AppController.kt:509-512` is **removed**. Downloads work for any `WatchableId`. The download engine was already AniList-free (Doc 02 §3) — only the orchestration layer changes.

### 8.5 Backup/restore

`BackupContainer` serializes `WatchableId` directly:
```kotlin
@Serializable
data class AnimeBackup(
    val watchableId: WatchableId,   // replaces anilistId: Long?
    val title: String,
    val sourceId: Long,
    val sourceUrl: String,
    // ...
)
```
On restore, resolve by `watchableId.crossDeviceKey()` — `Linked` by provider ID, `Unlinked` by title hash.

---

## 9. Trade-offs accepted

1. **Unlinked cross-device restore is best-effort.** The title-hash fallback can collide. We accept this because (a) it's rare, (b) it only affects restore, (c) the user can disambiguate. The alternative (UUID) breaks cross-device restore entirely.

2. **The `WatchableIdMigrator` adds complexity.** Link/unlink events now trigger a re-keying operation. We accept this because the alternative (immutable IDs that never change) means unlinked anime can never be linked later without losing their history — which is worse.

3. **Two identity columns on `animes` (`anilist_id` + `watchable_id_json`).** Redundant for linked anime. We accept this for backward compatibility + efficient AniList lookups (the `anilist_id` column has a partial unique index; `watchable_id_json` would need parsing to query by AniList ID).

4. **`episodeNumber` replaces `episodeUrl` in composite keys.** This means two episodes with the same number from different sources share a key. We accept this because (a) it fixes the source-switching break, (b) episode numbers are the user-meaningful identity, (c) the rare case of two different episodes with the same number (e.g., a recap + a normal episode both "Episode 12") is handled by the user picking the right one in the UI.

---

## 10. Conditions for revisiting

- If the unlinked-anime title-hash collision rate proves higher than expected (>0.1% of restores), revisit the normalization rules or add a disambiguation field (year, episode count) to the cross-device key.
- If a second metadata provider (MAL/TMDB) is added and users frequently link the same anime to multiple providers, consider a `provider_links` table (many providers per anime) instead of a single `WatchableId.Linked`.
- If the `WatchableIdMigrator` proves fragile (re-keying failures), consider making `WatchableId` immutable and using a separate `anime_identity_history` table to track changes.

---

## 11. Summary

**Recommendation:** Approach E (hybrid) — a typed `WatchableId` sealed class with `Linked(provider, remoteId)` and `Unlinked(sourceId, url, titleHash)` variants. Migrate all 7 anilistId-keyed cross-cutting stores to `WatchableId.stableKey()`. Replace `episodeUrl` with `episodeNumber` in composite keys (fixes source-switching). Add a `WatchableIdMigrator` for link/unlink events. Remove the anilistId hard gate on downloads.

**Why this approach:**
- Type-safe (eliminates `null → 0` sentinel pollution).
- Reproducible across devices (linked: by provider ID; unlinked: by title hash).
- Survives provider changes.
- Handles unlinked content.
- Fixes the source-switching download break.
- Medium migration effort (the schema barely changes; the work is in the stores).

**Driven by evidence:** Doc 01 (the dual-identity-system tension), Doc 02 (the composite-key duplication + source-switching break), Doc 03 (the 7 anilistId-keyed stores + the `DetailsViewPreferenceStore` hybrid template), Doc 06 (the schema is AniList-optional; the stores are not).

---

*Related: `proposals/02_provider_abstraction.md` (the metadata-provider side), `proposals/03_download_system_redesign.md` (the download-specific changes), `proposals/05_migration_strategy.md` (the migration path).*
