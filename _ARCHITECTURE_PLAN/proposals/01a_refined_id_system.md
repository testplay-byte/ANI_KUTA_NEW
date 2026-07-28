# 01a — Refined Internal ID System (Multi-Component Local ID + Content Grouping)

> **Phase 2 / Proposed Architecture (REFINED).** This document supersedes `proposals/01_internal_id_system.md` with a richer, more flexible identity model based on the owner's direction: a **multi-component local ID** derived from (system + repo + extension + source-content-id + name), with external provider IDs stored alongside. It introduces a **two-tier model** (per-source `local_id` + per-content `content_id`) to handle source-switching gracefully.
>
> **Status:** Proposed (pending review). On approval, ADR-040 is revised + ADR-050 is added (see `plan/04_decision_records.md`).

---

## 1. The owner's direction (recap + interpretation)

The owner proposes that the internal ID — the "local ID" — be generated from **multiple components**, not a single provider ID:

1. **The system** — which extension format/protocol is used (Aniyomi/Mihon lineage today; CloudStream in the future; other "core systems" later).
2. **The repository** — which repo URL the extension came from (there are multiple repos for the Aniyomi system).
3. **The extension** — which extension (name + stable extension ID) was used.
4. **The source content ID** — the anime/series/movie's own ID within that source (e.g., Aniyomi's `SAnime.url`).
5. **The name** — the anime's name (for cross-device reproducibility + human verification).

Plus: **external provider IDs** (AniList, MAL, TMDB, etc.) stored alongside, "if available," for flexibility + backup/restore.

The owner's goal: **maximum flexibility for the future** — the ability to add new core systems (CloudStream and beyond) without rework, and the ability to restore content from any stored signal.

---

## 2. Assessment: is this a good idea?

**Short answer: Yes — with one refinement.**

### 2.1 What's sound about the owner's proposal

1. **Storing all components separately is excellent.** The current `SourceLinkStore` stores only `(sourceId, animeUrl, animeTitle)` — it drops the repo URL, the extension name, and the system. On restore, if the extension isn't installed, there's no way to know which repo to fetch it from. The owner's proposal fixes this by storing the full provenance.

2. **The "system" concept is the right abstraction.** Today the codebase implicitly assumes "Aniyomi" everywhere (the `eu.kanade.tachiyomi.animesource.*` package). Elevating "system" to an explicit, stored field future-proofs for CloudStream + beyond. Adding a new core system = a new enum value, not a rewrite.

3. **The repo URL is a real gap.** I verified: `AnimeExtension.Installed.repoUrl: String?` exists (`data/extension/.../model/AnimeExtension.kt:62`) but is **not propagated** to `SourceLinkStore` (which stores only `sourceId + animeUrl + animeTitle`). So today, if you uninstall an extension and reinstall it from a different repo, the link is orphaned. The owner's proposal closes this gap.

4. **External IDs as metadata (not the primary key) is the right call.** Hardcoding `anilistId` as the primary key (current state) is what created the coupling. Making it one of several optional stored fields is the decoupling.

5. **A deterministic, multi-component local ID is reproducible across devices.** This is critical for backup/restore: two users watching the same anime from the same extension get the same local ID, so restore "just works" without an ID-mapping table.

### 2.2 The one flaw + the refinement

**The flaw:** A local ID built from (system + repo + extension + source-content-id + name) is **per-(anime, source)** — it identifies "this anime, from this extension, from this repo." If a user watches "Frieren" via Extension A, then switches to Extension B, the local ID changes (because the extension + source-content-id change). This means cross-cutting stores keyed by `local_id` (watch progress, downloads) would **not survive a source switch** — the exact bug we're trying to fix (Doc 02 §10).

**The refinement: a two-tier identity model.** Introduce a second, higher-level identifier — the **`content_id`** — that represents "the anime itself," independent of source. Cross-cutting stores key off `content_id`, not `local_id`. The `content_id` is derived from the best available external link (AniList ID → MAL ID → TMDB ID → title-hash fallback). Multiple `local_id`s (one per source) can map to the same `content_id`.

This gives the owner everything they asked for (rich, per-source `local_id` + stored external IDs + full provenance) **and** solves source-switching (via the `content_id` grouping layer).

### 2.3 Additional components I recommend (beyond the owner's list)

For maximum future flexibility, I recommend storing these additional components alongside the owner's five:

| Component | Why | Already available? |
|---|---|---|
| `extension_version` (versionName + versionCode) | Debugging + compat checks on restore (an old backup may reference an extension version that's since been updated) | ✅ On `AnimeExtension.Installed` |
| `extension_lang` | Aniyomi extensions are lang-specific; needed to re-resolve the right extension on restore | ✅ On `AnimeExtension.Installed` |
| `is_nsfw` | Filtering + display | ✅ On `AnimeExtension.Installed` |
| `source_name` (distinct from extension name) | One extension can have multiple sources via `AnimeSourceFactory`; the source name disambiguates | ✅ On `AnimeSource` |
| `discovered_at` / `last_resolved_at` timestamps | Debugging + stale-link detection | ❌ Add |
| `link_confidence` (auto-matched vs user-confirmed) | Whether the AniList link was auto-matched (may be wrong) or user-confirmed | ❌ Add (improves UX) |

### 2.4 Verdict

The owner's proposal is **sound and is the right direction.** The two-tier refinement (adding `content_id`) makes it also solve source-switching. I recommend adopting it. The rest of this document details the design.

---

## 3. The two-tier identity model

### 3.1 Tier 1 — `local_id` (per-source identity)

The `local_id` identifies **a specific anime from a specific source**. It is deterministic, reproducible, and unique per (system, extension, source-content-id).

**Generation:** a structured string (NOT an opaque hash — structured strings are debuggable + self-describing):

```
local_id = "<system>:<extensionId>:<sourceContentId>"
```

**Examples:**
- Aniyomi extension (sourceId=1234567890, SAnime.url=`https://gogoanime.gg/frieren-sousou-no-yakusoku`):
  `aniyomi:1234567890:https://gogoanime.gg/frieren-sousou-no-yakusoku`
- CloudStream extension (future, sourceId=9876543210, mediaId=`frieren`):
  `cloudstream:9876543210:frieren`
- AniList-only (no extension yet):
  `anilist:154587` (special case — no extension components; uses the AniList ID directly)
- MAL-only (future, no extension):
  `mal:67890`

**Why a structured string, not a hash:**
- **Debuggable:** you can read `aniyomi:1234567890:https://...` in a log + immediately know what it is. A SHA-256 hash is opaque.
- **Self-describing:** the `system` prefix tells you which contract the source uses.
- **Uniqueness is guaranteed by the components,** not by hash collision avoidance. `extensionId` is already `MD5(name/lang/versionId).takeLowest64Bits()` (deterministic + stable across reinstalls — Doc 05 §3.2). `sourceContentId` is the source's own unique ID for that anime. The combination `(system, extensionId, sourceContentId)` is globally unique.
- **The name + repoUrl are stored as metadata columns,** not in the ID. They don't affect uniqueness (the components already guarantee it); they're for restore + display.

**The "name" component:** The owner wanted the name in the local ID. I recommend storing it as a **metadata column** (`title`) rather than in the ID itself, because (a) the ID is already unique without it, (b) names can change (AniList title updates, localization), and embedding a mutable field in an ID creates stale IDs, (c) the title is already a column on `animes`. The name IS used for cross-device fallback matching (Tier 2, §3.2).

### 3.2 Tier 2 — `content_id` (per-content identity, for grouping)

The `content_id` identifies **the anime itself**, independent of source. It is what cross-cutting stores (watch progress, downloads, history) key off. Multiple `local_id`s map to one `content_id`.

**Generation (priority order):**

```
content_id =
  if anilistId != null  → "al:<anilistId>"           # AniList ID (most common today)
  else if malId != null → "mal:<malId>"              # MAL ID
  else if tmdbId != null → "tmdb:<tmdbId>"           # TMDB ID (future)
  else if kitsuId != null → "kitsu:<kitsuId>"        # Kitsu ID (future)
  else                   → local_id                  # fallback: per-source identity (no grouping)
```

**Why priority order:** An anime can be linked to multiple providers (AniList + MAL + TMDB). We pick one canonical `content_id` by priority. AniList wins (it's the most complete anime metadata source today + the current default). MAL/TMDB/Kitsu are fallbacks for anime not on AniList.

**The fallback (no external link):** If no external ID is available, `content_id = local_id`. This means unlinked anime are per-source (no grouping). This is acceptable — unlinked anime are the minority, and the user can link them later (which upgrades the `content_id` from `local_id` to `al:<anilistId>`, triggering a `ContentIdMigrator` re-key).

**Source-switching (the key benefit):**
1. User watches "Frieren" via Extension A → `local_id_A = "aniyomi:111:https://extA.com/frieren"`, `content_id = "al:154587"` (linked to AniList).
2. Watch progress + downloads key off `content_id = "al:154587"`.
3. User switches to Extension B → `local_id_B = "aniyomi:222:https://extB.com/frieren"`, `content_id = "al:154587"` (same — it's the same AniList anime).
4. Watch progress + downloads are found by `content_id` → **they survive the switch.**

### 3.3 The two-tier relationship

```
   content_id: "al:154587"  (the anime "Frieren", identified by AniList)
       │
       ├── local_id: "aniyomi:111:https://extA.com/frieren"  (Extension A's version)
       │     └── stored metadata: system=ANIYOMI, repoUrl=..., extensionName="GogoAnime", ...
       │
       ├── local_id: "aniyomi:222:https://extB.com/frieren"  (Extension B's version)
       │     └── stored metadata: system=ANIYOMI, repoUrl=..., extensionName="AnimePahe", ...
       │
       └── local_id: "cloudstream:333:frieren"  (future, CloudStream's version)
             └── stored metadata: system=CLOUDSTREAM, repoUrl=..., extensionName="CloudStream Frieren", ...
```

- **Cross-cutting stores** (watch progress, downloads, history, episode metadata, tracker) key off `content_id`.
- **Source-specific data** (episode list, video resolution, source prefs) key off `local_id`.
- **Backup/restore** serializes both: `content_id` for cross-device identity, `local_id` + full metadata for re-resolution.

---

## 4. The `ExtensionSystem` enum (the "core system" concept)

```kotlin
// :core:common
enum class ExtensionSystem(val key: String, val displayName: String) {
    ANIYOMI("aniyomi", "Aniyomi / Mihon"),      // eu.kanade.tachiyomi.animesource.* contract
    CLOUDSTREAM("cloudstream", "CloudStream"),   // com.lagradost.cloudstream3.* contract (future)
    // Future core systems added here:
    // ANIMEKAI("animekai", "AnimeKai"),
    // DOKI("doki", "Doki"),
    ;
    companion object {
        fun fromKey(key: String): ExtensionSystem? = entries.firstOrNull { it.key == key }
    }
}
```

**Why an enum, not a string:** type safety + exhaustiveness in `when` blocks + compile-time checks when adding a new system.

**The "Aniyomi/Mihon lineage" grouping:** Aniyomi, Mihon, and their forks all use the same `eu.kanade.tachiyomi.animesource.*` contract (ADR-029). They're one "system" for identity purposes. A fork of Aniyomi that changes the contract would be a new `ExtensionSystem`.

---

## 5. The data model

### 5.1 The `animes` table (revised schema, v3)

```sql
CREATE TABLE animes (
    -- Existing PK + core fields (unchanged)
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,                         -- = sourceContentId (the source's URL/ID for this anime)
    title TEXT NOT NULL,
    artist TEXT,
    author TEXT,
    description TEXT,
    genre TEXT,
    cover_url TEXT,
    status INTEGER NOT NULL,
    thumbnail_url TEXT,
    favorite INTEGER NOT NULL DEFAULT 0,
    source_id INTEGER NOT NULL,                -- = extensionId (the extension's stable source ID)
    date_added INTEGER NOT NULL,
    viewer_flags INTEGER NOT NULL DEFAULT 0,
    next_update INTEGER NOT NULL DEFAULT 0,
    update_strategy INTEGER NOT NULL DEFAULT 0,
    cover_last_modified INTEGER NOT NULL DEFAULT 0,

    -- ADR-024 status-tracking columns (existing)
    release_date INTEGER,
    last_refresh INTEGER NOT NULL DEFAULT 0,
    last_metadata_fetch INTEGER,
    next_episode_check INTEGER,

    -- ═══ NEW: Identity columns (Tier 1 + Tier 2) ═══
    local_id TEXT NOT NULL,                    -- "<system>:<extensionId>:<sourceContentId>"
    content_id TEXT NOT NULL,                  -- "al:<anilistId>" | "mal:<malId>" | ... | = local_id (fallback)

    -- ═══ NEW: Source provenance (Tier 1 metadata) ═══
    system TEXT NOT NULL,                      -- "aniyomi" | "cloudstream" | ... (ExtensionSystem.key)
    repo_url TEXT,                             -- the extension repo base URL (nullable if installed from APK)
    repo_name TEXT,                            -- human-readable repo name (from ExtensionRepo.name)
    extension_pkg_name TEXT,                   -- the extension's package name (e.g., "eu.kanade.tachiyomi.animeextension.en.gogoanime")
    extension_name TEXT,                       -- human-readable extension name
    extension_version_name TEXT,               -- e.g., "1.4.3"
    extension_version_code INTEGER,            -- e.g., 143
    extension_lang TEXT,                       -- e.g., "en"
    is_nsfw INTEGER NOT NULL DEFAULT 0,        -- extension NSFW flag
    source_name TEXT,                          -- the source's display name (distinct from extension name; one extension can have multiple sources)

    -- ═══ EXISTING: External provider ID columns (kept for backward compat + efficient lookups) ═══
    anilist_id INTEGER,                        -- nullable; partial unique index
    -- mal_id INTEGER,                        -- future
    -- tmdb_id INTEGER,                       -- future

    -- ═══ EXISTING: Library columns ═══
    cover_color TEXT,
    score REAL,
    total_episodes INTEGER,
    last_watched INTEGER NOT NULL DEFAULT 0,
    next_airing_episode INTEGER,

    -- ═══ NEW: Metadata ═══
    discovered_at INTEGER NOT NULL DEFAULT 0,  -- when this local_id was first created
    last_resolved_at INTEGER NOT NULL DEFAULT 0, -- when content_id was last re-resolved
    link_confidence INTEGER NOT NULL DEFAULT 0 -- 0=none, 1=auto-matched, 2=user-confirmed
);

-- ═══ INDEXES ═══
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_local_id ON animes(local_id);
CREATE INDEX IF NOT EXISTS idx_animes_content_id ON animes(content_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_anilist_id ON animes(anilist_id) WHERE anilist_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_animes_source_url ON animes(source_id, url);
```

**Key design decisions:**
- `local_id` is UNIQUE — one row per (anime, source). The same anime from a different source gets a different row + different `local_id`.
- `content_id` is indexed (not unique) — multiple rows (different sources) can share the same `content_id`.
- External IDs (`anilist_id`, etc.) stay as columns for efficient lookups + backward compat. (For unlimited external IDs, see the `content_links` table in §5.2.)
- The existing `source_id` + `url` columns are retained (= `extensionId` + `sourceContentId`) for backward compat with existing queries. They're now also part of `local_id`.
- Full source provenance (system, repo, extension name/version/lang, source name) is stored per row — this is the owner's key ask.

### 5.2 The `content_links` table (optional, for unlimited external IDs)

If we want to support linking an anime to **many** providers (AniList + MAL + TMDB + Kitsu + custom), a separate junction table is more flexible than fixed columns:

```sql
CREATE TABLE content_links (
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    content_id TEXT NOT NULL,
    provider TEXT NOT NULL,                   -- "anilist" | "mal" | "tmdb" | "kitsu" | ...
    remote_id TEXT NOT NULL,                  -- the provider's ID (as a string for generality)
    linked_at INTEGER NOT NULL,
    link_confidence INTEGER NOT NULL DEFAULT 0, -- 0=auto, 1=user-confirmed
    UNIQUE(content_id, provider)
);
CREATE INDEX IF NOT EXISTS idx_content_links_content ON content_links(content_id);
CREATE INDEX IF NOT EXISTS idx_content_links_provider_remote ON content_links(provider, remote_id);
```

**Recommendation:** Start with the fixed columns on `animes` (`anilist_id`, + add `mal_id`, `tmdb_id` as needed) for simplicity + query speed. Migrate to the `content_links` table when 4+ providers are supported. The `content_id` derivation logic (§3.2) works either way.

### 5.3 The `source_bindings` view (conceptual)

The relationship "which sources provide this content" is queryable:
```sql
SELECT local_id, system, extension_name, source_name, repo_url
FROM animes
WHERE content_id = ?;
```
This returns all source bindings for a given content. No separate table needed — it's a query on `animes`.

---

## 6. ID generation (concrete algorithms)

### 6.1 `local_id` generation

```kotlin
// :core:common
object LocalIdGenerator {
    /**
     * Generate a local_id for an anime from an extension source.
     * Format: "<system>:<extensionId>:<sourceContentId>"
     */
    fun forExtension(
        system: ExtensionSystem,
        extensionId: Long,           // AnimeSource.id (= MD5(name/lang/versionId))
        sourceContentId: String,     // SAnime.url (Aniyomi) or equivalent
    ): String {
        require(sourceContentId.isNotBlank())
        return "${system.key}:${extensionId}:${sourceContentId}"
    }

    /**
     * Generate a local_id for an anime from a metadata provider (no extension).
     * Format: "<providerKey>:<remoteId>"
     */
    fun forProvider(
        provider: MetadataProviderId,  // ANILIST, MAL, TMDB, ...
        remoteId: String,
    ): String {
        require(remoteId.isNotBlank())
        return "${provider.key}:${remoteId}"
    }

    /**
     * Parse a local_id back into its components (for debugging + restore).
     */
    fun parse(localId: String): ParsedLocalId? {
        val parts = localId.split(":", limit = 3)
        return when {
            parts.size == 3 -> ParsedLocalId.Extension(
                system = ExtensionSystem.fromKey(parts[0]) ?: return null,
                extensionId = parts[1].toLongOrNull() ?: return null,
                sourceContentId = parts[2],
            )
            parts.size == 2 -> ParsedLocalId.Provider(
                provider = MetadataProviderId.fromKey(parts[0]) ?: return null,
                remoteId = parts[1],
            )
            else -> null
        }
    }
}

sealed class ParsedLocalId {
    data class Extension(val system: ExtensionSystem, val extensionId: Long, val sourceContentId: String) : ParsedLocalId()
    data class Provider(val provider: MetadataProviderId, val remoteId: String) : ParsedLocalId()
}
```

### 6.2 `content_id` generation

```kotlin
// :core:common
object ContentIdGenerator {
    /**
     * Generate a content_id from the best available external link.
     * Priority: anilist > mal > tmdb > kitsu > local_id (fallback).
     */
    fun forAnime(anime: Anime): String {
        anime.anilistId?.let { return "al:$it" }
        // anime.malId?.let { return "mal:$it" }      // future
        // anime.tmdbId?.let { return "tmdb:$it" }    // future
        return anime.localId   // fallback: per-source identity (no grouping)
    }

    /**
     * Generate a content_id from a content_links table row set.
     * (Use this version when the content_links table is implemented.)
     */
    fun fromLinks(links: List<ContentLink>): String {
        val priority = listOf("anilist", "mal", "tmdb", "kitsu")
        for (provider in priority) {
            links.firstOrNull { it.provider == provider }?.let { return "${provider}:${it.remoteId}" }
        }
        return links.firstOrNull()?.localId ?: error("no links")
    }
}
```

### 6.3 The `ContentIdMigrator` (link/unlink events)

When an anime's external link changes (link to AniList, unlink, add MAL), the `content_id` may change. The `ContentIdMigrator` re-keys all cross-cutting stores:

```kotlin
class ContentIdMigrator(
    private val watchProgressStore: WatchProgressStore,
    private val playbackStateStore: PlaybackStateStore,
    private val downloadManager: DownloadManager,
    private val episodeMetadataCache: EpisodeMetadataCache,
    private val sourceLinkStore: SourceLinkStore,
    // ... all content_id-keyed stores
) {
    /**
     * Called when an anime's content_id changes (link/unlink event).
     * Re-keys all cross-cutting store entries from oldContentId to newContentId.
     */
    fun migrate(oldContentId: String, newContentId: String) {
        watchProgressStore.rekey(oldContentId, newContentId)
        playbackStateStore.rekey(oldContentId, newContentId)
        downloadManager.rekey(oldContentId, newContentId)
        episodeMetadataCache.rekey(oldContentId, newContentId)
        // sourceLinkStore keys off local_id (not content_id), so it's not migrated here
    }
}
```

**This is the same concept as the original `WatchableIdMigrator`**, but operating on `content_id` strings instead of `WatchableId` values.

---

## 7. How the cross-cutting stores use the two tiers

| Store | Keys off | Why |
|---|---|---|
| `WatchProgressStore` | `content_id + episodeNumber` | Survives source switches (same content_id) |
| `PlaybackStateStore` | `content_id + episodeNumber` | Same |
| `DownloadTask.key` | `content_id + episodeNumber` | Same + fixes the source-switching download break |
| `EpisodeMetadataCache` | `content_id` (outer) + `episodeNumber` (inner) | Same |
| `SourceLinkStore` | `local_id` | Source links are per-source by definition |
| `ExtensionLinkStore` | `local_id` → `content_id` (for grouping) | Maps a source binding to its content |
| `DetailsViewPreferenceStore` | `content_id` | Per-anime display prefs survive source switches |
| Tracker sync (`TrackSyncManager`) | `content_id` | Syncs the anime, not the source |
| Backup/restore | Both `content_id` + `local_id` + full metadata | Cross-device identity + re-resolution |

**The key shift from the original proposal:** cross-cutting stores key off `content_id` (Tier 2), NOT `local_id` (Tier 1). This is what makes source-switching work. `local_id` is the granular per-source identity; `content_id` is the grouping layer.

---

## 8. How it handles the key scenarios

### 8.1 An anime from one source, linked to AniList

- Discovered via Extension A → `local_id = "aniyomi:111:https://extA.com/frieren"`.
- User links to AniList (ID 154587) → `content_id = "al:154587"`.
- Watch progress + downloads key off `"al:154587"`.
- Backup serializes: `content_id="al:154587"`, `local_id="aniyomi:111:..."`, `system=ANIYOMI`, `repoUrl=...`, `extensionName=...`, `anilistId=154587`.

### 8.2 The same anime watched from different sources

- User watches "Frieren" via Extension A → `local_id_A`, `content_id = "al:154587"`.
- User switches to Extension B → `local_id_B` (different), `content_id = "al:154587"` (same).
- Watch progress + downloads found by `content_id` → **survive the switch**.
- Both `local_id_A` and `local_id_B` rows exist in `animes` (or the row is updated with the new `local_id` when the source changes — see §8.5).

### 8.3 Content shared between users (backup/restore)

- User A backs up "Frieren" → backup has `content_id="al:154587"`, `local_id="aniyomi:111:..."`, full provenance.
- User B restores:
  1. Resolve by `content_id="al:154587"` → find or create the `animes` row.
  2. Try to resolve the `local_id`: parse `"aniyomi:111:..."` → check if Extension 111 is installed.
     - If yes → bind the source; episodes load.
     - If no → check `repoUrl` → offer to install the extension from that repo.
     - If the extension isn't available from any known repo → the anime is still in the library (via `content_id`), but episodes can't load until a source is re-linked.
  3. Watch progress + downloads restore by `content_id`.

### 8.4 An anime that was linked to AniList and then unlinked

- Before unlink: `content_id = "al:154587"`, `local_id = "aniyomi:111:..."`.
- User unlinks → `content_id` changes from `"al:154587"` to `local_id` (`"aniyomi:111:..."`).
- `ContentIdMigrator.migrate("al:154587", "aniyomi:111:...")` re-keys all cross-cutting stores.
- **The anime retains its identity, watch progress, and downloads** — they're now under the `local_id` fallback.
- If the user re-links later, the `content_id` upgrades back + the migrator re-keys again.

### 8.5 Source switching + the `animes` table (one row or many?)

When the user switches source for the same anime, there are two options:

**Option A — Update the existing row:** The `local_id` + source provenance columns are overwritten with the new source's values. `content_id` stays the same (if linked). One row per anime.

**Option B — Multiple rows:** Each source gets its own row (different `local_id`, same `content_id`). The library deduplicates by `content_id`.

**Recommendation: Option A** (update the existing row). It's simpler, matches the current behavior (one row per library entry), and avoids library duplication. The "other sources available" are queryable via `SourceLinkStore` (which can store multiple source bindings per `content_id` if we extend it).

**Exception:** If the user explicitly wants to track the same anime from two sources simultaneously (e.g., SUB from one, DUB from another), Option B (multiple rows) supports that. This can be a future enhancement; for now, Option A.

### 8.6 Content not on any provider (unlinked extension anime)

- Discovered via Extension, "go without linking" → `local_id = "aniyomi:111:..."`, `content_id = local_id` (fallback).
- Everything works: downloads, watch progress, history, library, categories. Tracker sync can't work (no provider to sync to) — correct.
- The anime is a first-class citizen (the original 7 failure modes are fixed).

### 8.7 Adding a new core system (e.g., CloudStream)

1. Add `CLOUDSTREAM` to the `ExtensionSystem` enum (one line).
2. Create `:core:media-source-api` + `:data:media-extension` (parallel modules — proposal 04).
3. `LocalIdGenerator.forExtension(CLOUDSTREAM, mediaExtensionId, mediaContentId)` produces `cloudstream:<id>:<mediaId>`.
4. Everything downstream (content_id, cross-cutting stores, backup/restore) works unchanged.
5. **The schema doesn't change** — `system` is a TEXT column that accepts the new key.

---

## 9. The `SourceLinkStore` redesign

The current `SourceLinkStore` (`data/extension/.../cache/SourceLinkStore.kt`) stores only `(sourceId, animeUrl, animeTitle)` keyed by `anilistId`. It needs to be redesigned to store the full provenance + key off `content_id` (or `local_id`).

**Redesigned `SourceLinkStore` (in-memory shape):**

```kotlin
@Serializable
data class SourceBinding(
    val localId: String,           // "aniyomi:111:https://..."
    val contentId: String,         // "al:154587" (or = localId if unlinked)
    val system: String,            // "aniyomi" | "cloudstream" | ...
    val repoUrl: String?,          // the extension repo base URL
    val repoName: String?,         // human-readable repo name
    val extensionPkgName: String,  // the extension's package name
    val extensionName: String,     // human-readable extension name
    val extensionVersionName: String?,
    val extensionVersionCode: Long?,
    val extensionLang: String?,
    val extensionId: Long,         // the source's stable ID (= AnimeSource.id)
    val sourceName: String,        // the source's display name
    val sourceContentId: String,   // SAnime.url or equivalent
    val animeTitle: String,
    val isNsfw: Boolean,
    val discoveredAt: Long,
    val linkConfidence: Int,       // 0=auto, 1=user-confirmed
)

class SourceLinkStore(...) {
    // Keyed by content_id (for "which sources provide this content?")
    fun getBindings(contentId: String): List<SourceBinding>
    fun saveBinding(binding: SourceBinding)
    fun removeBinding(localId: String)
    fun getAll(): List<SourceBinding>   // for backup

    // Also keyed by local_id (for "what content does this source binding represent?")
    fun getBindingByLocalId(localId: String): SourceBinding?
}
```

**This is the owner's key ask realized:** every component (system, repo, extension name/version/lang, source name, source content ID) is stored. On restore, the app has everything needed to re-resolve the content.

---

## 10. The backup format (revised)

The `.anikuta` backup (ADR-036) serializes the full identity:

```kotlin
@Serializable
data class AnimeBackupV2(
    // Tier 2: cross-device content identity
    val contentId: String,              // "al:154587" (or local_id fallback)

    // Tier 1: per-source identity + full provenance
    val localId: String,                // "aniyomi:111:https://..."
    val system: String,                 // "aniyomi" | "cloudstream" | ...
    val repoUrl: String?,
    val repoName: String?,
    val extensionPkgName: String,
    val extensionName: String,
    val extensionVersionName: String?,
    val extensionVersionCode: Long?,
    val extensionLang: String?,
    val extensionId: Long,
    val sourceName: String,
    val sourceContentId: String,

    // External provider IDs (all that are known)
    val anilistId: Int? = null,
    val malId: Int? = null,             // future
    val tmdbId: Int? = null,            // future

    // Anime metadata
    val title: String,
    val coverUrl: String?,
    val coverColor: String?,
    val score: Double?,
    val totalEpisodes: Int?,
    val status: Int,
    val genres: List<String>,

    // Bookkeeping
    val discoveredAt: Long,
    val linkConfidence: Int,
)
```

**Restore logic:**
1. Try to resolve by `contentId` (cross-device, if external link exists).
2. If the content is found, try to bind the source from `localId` (parse → check if extension installed → check repo → offer install).
3. If the content is NOT found (no external link, or external link doesn't resolve on this device), create a new `animes` row from the backup fields.
4. Restore watch progress + downloads by `contentId`.

---

## 11. Collision risk analysis

### 11.1 `local_id` collision

**Risk:** Two different anime with the same `(system, extensionId, sourceContentId)`.

**Assessment:** Negligible. `extensionId` is `MD5(name/lang/versionId)` — unique per extension. `sourceContentId` is the source's own unique ID for that anime (e.g., `SAnime.url`). The source guarantees uniqueness within itself. The combination is globally unique.

**Edge case:** A source reuses a URL after delisting an anime (rare). Mitigation: the `title` + `discoveredAt` metadata disambiguates; the user can resolve manually.

### 11.2 `content_id` collision (for the title-hash fallback)

**Risk:** Two different unlinked anime with the same title → same `content_id` (if we used title-hash). 

**Assessment:** We DON'T use title-hash for `content_id`. The fallback is `content_id = local_id`, which is unique per source. So unlinked anime never share a `content_id` unless they're from the same source (in which case they're the same anime).

**The only collision risk** is if two users back up unlinked anime from different sources with the same title — but on restore, each user's `local_id` is different (different extensionId), so no collision.

### 11.3 External ID collision

**Risk:** Two different anime with the same AniList ID. 

**Assessment:** Impossible — AniList IDs are globally unique. Same for MAL/TMDB.

---

## 12. Migration from the current system

(See `proposals/05_migration_strategy.md` for the full migration plan. Summary of how it adapts to the two-tier model:)

1. **Schema migration (`2.sqm`):** Add `local_id`, `content_id`, `system`, `repo_url`, `repo_name`, `extension_pkg_name`, `extension_name`, `extension_version_name`, `extension_version_code`, `extension_lang`, `is_nsfw`, `source_name`, `discovered_at`, `last_resolved_at`, `link_confidence` columns to `animes`. Add the unique index on `local_id` + the index on `content_id`.

2. **Backfill:**
   - For every existing `animes` row:
     - `local_id` = `LocalIdGenerator.forExtension(ANIYOMI, source_id, url)` (or `forProvider(ANILIST, anilistId)` if `source_id == 0`).
     - `content_id` = `ContentIdGenerator.forAnime(this)` (uses `anilist_id` if present, else `local_id`).
     - `system` = `"aniyomi"` (all existing anime are Aniyomi-system).
     - `repo_url`, `extension_name`, etc. = resolve from `AnimeExtensionManager` by `source_id` (best-effort; some may be null if the extension is uninstalled).

3. **Re-key cross-cutting stores:**
   - `WatchProgressStore`: `"$anilistId:$episodeUrl"` → `"$content_id:$episodeNumber"`.
   - `PlaybackStateStore`: same.
   - `DownloadTask.key`: `"$anilistId:$episodeUrl"` → `"$content_id:$episodeNumber"`.
   - `EpisodeMetadataCache`: outer key `anilistId` → `content_id`.
   - `SourceLinkStore`: re-keyed by `content_id`; the stored `SourceLink` is enriched to `SourceBinding` with full provenance.

4. **Drop the `anilistId == 0` gate** (Doc 02 §3) — `content_id` is never null/zero.

5. **Mark migration done.**

---

## 13. Comparison with the original `WatchableId` proposal

| Aspect | Original (`WatchableId`) | Refined (two-tier) |
|---|---|---|
| Identity type | Sealed class: `Linked \| Unlinked` | Two strings: `local_id` + `content_id` |
| Source provenance stored? | Minimal (`sourceId` on `Unlinked`) | Full (system, repo, extension name/version/lang, source name) |
| Per-source identity? | Only for `Unlinked` | Always (every anime has a `local_id`) |
| Source-switching fix | Via `Linked` (source-independent) | Via `content_id` (grouping layer) |
| Cross-device reproducibility | `Linked`: by provider ID; `Unlinked`: by title hash | `content_id`: by provider ID; `local_id`: by source components (deterministic) |
| Adding a new core system | New `WatchableId` variant? | New `ExtensionSystem` enum value (one line) |
| Backup richness | `anilistId` + `sourceId + url` | Full provenance (owner's ask) |
| Debuggability | Opaque sealed types | Self-describing structured strings |
| Migration effort | Medium | Medium (similar; more columns to backfill) |

**The refined model is strictly better** for the owner's goals (flexibility, future core systems, rich backup/restore). The cost is a few more columns on `animes` + a slightly more complex `content_id` derivation — both acceptable.

---

## 14. Trade-offs accepted

1. **Two tiers add a concept.** Developers must understand `local_id` vs `content_id`. Mitigation: clear naming + KDoc + the §7 table.
2. **`content_id` can change on link/unlink.** The `ContentIdMigrator` handles this, but it's a moving part. Mitigation: the migrator is transactional + idempotent + tested.
3. **Structured strings are longer than hashes.** `"aniyomi:1234567890:https://gogoanime.gg/frieren-sousou-no-yakusoku"` is ~70 chars vs a 16-char hash. Mitigation: storage is cheap; debuggability is worth it. (If storage matters, we can add a hash column as an alternate key.)
4. **External IDs as fixed columns (`anilist_id`, `mal_id`, `tmdb_id`) limit to ~3-4 providers.** Mitigation: the `content_links` table (§5.2) is the upgrade path for unlimited providers.
5. **Option A (update row on source switch) means the old `local_id` is lost.** If the user switches back, the old source binding must be re-resolved. Mitigation: `SourceLinkStore` can store multiple bindings per `content_id` (the "other sources" list).

---

## 15. Conditions for revisiting

- If `content_id` migration on link/unlink proves fragile, make `content_id` immutable (always = first-known external link, or a UUID) + track changes in a `content_id_history` table.
- If 4+ external providers are needed, migrate to the `content_links` table (§5.2).
- If Option A (update row on source switch) causes user confusion ("where did my old source go?"), switch to Option B (multiple rows per `content_id`).
- If structured strings prove too long for SharedPreferences keys, add a hash column (`local_id_hash`) as the index, keeping the structured string for debugging.

---

## 16. Summary

**Recommendation:** Adopt the owner's multi-component local ID proposal, refined into a **two-tier model**:

- **Tier 1 — `local_id`** (per-source): `"<system>:<extensionId>:<sourceContentId>"`. Stored on `animes` with full provenance (system, repo, extension name/version/lang, source name). Deterministic, reproducible, debuggable.
- **Tier 2 — `content_id`** (per-content): `"al:<anilistId>"` (or `mal:`, `tmdb:`, ... by priority; fallback to `local_id`). Used by cross-cutting stores to survive source switches + for cross-device backup identity.
- **External IDs** stored as columns (`anilist_id`, + `mal_id`, `tmdb_id` as needed) or in a `content_links` table (for unlimited providers).
- **`ContentIdMigrator`** re-keys cross-cutting stores on link/unlink events.
- **`ExtensionSystem` enum** makes adding a new core system (CloudStream, etc.) a one-line change.

**Why this is the right approach:**
- Honors the owner's direction (multi-component local ID + full provenance + external IDs).
- Solves source-switching (via the `content_id` grouping layer).
- Future-proofs for new core systems (enum + structured strings).
- Richer backup/restore (every signal is stored).
- Debuggable (self-describing structured IDs, not opaque hashes).
- The repo URL infrastructure already exists (`AnimeExtension.Installed.repoUrl`) — it just needs to be propagated to the stored identity.

**Driven by:**
- Owner's direction (this document).
- `analysis/01_content_identification_flow.md` (the dual-identity tension).
- `analysis/05_extension_system_analysis.md` (the `AnimeExtension.Installed` model + source ID determinism).
- `analysis/06_data_layer_analysis.md` (the schema is AniList-optional; the stores are not).
- Code verification: `data/extension/.../model/AnimeExtension.kt:62` (`repoUrl` field exists), `data/extension/.../repo/ExtensionRepo.kt` (full repo model), `data/extension/.../cache/SourceLinkStore.kt` (current minimal link storage).

---

*Related: `proposals/02_provider_abstraction.md` (the metadata-provider side), `proposals/03_download_system_redesign.md` (updated to key off `content_id`), `proposals/05_migration_strategy.md` (the migration path), `plan/04_decision_records.md` (ADR-040 revised + ADR-050 added).*
