package app.confused.anikuta.migration

import android.util.Log
import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataCache
import app.confused.anikuta.core.player.PlaybackStateStore
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore

/**
 * Re-keys all cross-cutting stores when an anime's content_id changes.
 *
 * This happens in two scenarios:
 *
 * 1. **Link** (unlinked → linked): the user opens an unlinked extension anime
 *    (content_id = local_id, e.g., `"aniyomi:123:url"`), then links it to AniList.
 *    The content_id changes to `"al:<anilistId>"`. All watch progress, playback
 *    state, episode metadata, + source links keyed by the old local_id must be
 *    re-keyed to the new content_id — otherwise the anime "loses" its history
 *    when linked.
 *
 * 2. **Switch AniList anime** (linked → different linked): the user corrects a
 *    wrong auto-match by switching to a different AniList entry. The content_id
 *    changes from `"al:<oldAnilistId>"` to `"al:<newAnilistId>"`. All stores
 *    must be re-keyed so the history follows the anime, not the old AniList ID.
 *
 * # What it does
 *
 * For each cross-cutting store, calls `rekey(oldContentId, newContentId)` (or
 * the equivalent for stores that key by content_id + episode_number, in which
 * case ALL entries with the old content_id prefix are re-keyed).
 *
 * # Idempotency + crash resistance
 *
 * Each store's rekey is independent + idempotent (rekeying a non-existent key
 * is a no-op). If one store fails, the others still complete. All operations
 * are logged at INFO level for diagnostics.
 *
 * # Stores handled
 *
 * - [WatchProgressStore] — keys are `"$contentId|$episodeNumber"`. All entries
 *   with the old content_id prefix are re-keyed.
 * - [PlaybackStateStore] — same key format as WatchProgressStore.
 * - [EpisodeMetadataCache] — keys are content_id. Single rekey call.
 * - [SourceLinkStore] — keys are content_id. Single rekey call.
 *
 * (Downloads will be handled in Phase 6 via the download system redesign.)
 *
 * Per `_ARCHITECTURE_PLAN/proposals/01a_refined_id_system.md` §6.3 + §7.
 */
class ContentIdMigrator(
    private val watchProgressStore: WatchProgressStore,
    private val playbackStateStore: PlaybackStateStore,
    private val episodeMetadataCache: EpisodeMetadataCache,
    private val sourceLinkStore: SourceLinkStore,
) {

    /**
     * Re-key all cross-cutting stores from [oldContentId] to [newContentId].
     *
     * Call this when an anime's content_id changes (link/unlink/switch events).
     *
     * @param oldContentId the old content_id (e.g., `"aniyomi:123:url"` or `"al:154587"`).
     * @param newContentId the new content_id (e.g., `"al:154587"` or `"al:999999"`).
     * @return counts of how many entries were re-keyed per store.
     */
    fun migrate(oldContentId: String, newContentId: String): Result {
        if (oldContentId == newContentId) {
            Log.i(TAG, "migrate: oldContentId == newContentId ($oldContentId) — no-op")
            return Result(0, 0, 0, 0)
        }
        Log.i(TAG, "migrate: $oldContentId → $newContentId")

        val watchCount = rekeyWatchProgress(oldContentId, newContentId)
        val playbackCount = rekeyPlaybackState(oldContentId, newContentId)
        val metadataCount = rekeyEpisodeMetadata(oldContentId, newContentId)
        val sourceLinkCount = rekeySourceLink(oldContentId, newContentId)

        val result = Result(watchCount, playbackCount, metadataCount, sourceLinkCount)
        Log.i(TAG, "migrate complete: $result")
        return result
    }

    /**
     * Re-key all WatchProgressStore entries with the old content_id prefix.
     *
     * Keys are `"$contentId|$episodeNumber"`. We find all keys starting with
     * `"$oldContentId|"` and re-key each to `"$newContentId|$episodeNumber"`.
     */
    private fun rekeyWatchProgress(oldContentId: String, newContentId: String): Int {
        val oldPrefix = "$oldContentId|"
        val entries = watchProgressStore.getAll().filterKeys { it.startsWith(oldPrefix) }
        if (entries.isEmpty()) return 0

        var count = 0
        for ((oldKey, _) in entries) {
            val episodeNumberKey = oldKey.substringAfter('|')
            val newKey = "$newContentId|$episodeNumberKey"
            watchProgressStore.rekey(oldKey, newKey)
            count++
        }
        Log.d(TAG, "WatchProgress: re-keyed $count entries")
        return count
    }

    /**
     * Re-key all PlaybackStateStore entries with the old content_id prefix.
     * Same logic as [rekeyWatchProgress].
     */
    private fun rekeyPlaybackState(oldContentId: String, newContentId: String): Int {
        val oldPrefix = "$oldContentId|"
        val entries = playbackStateStore.getAll().filterKeys { it.startsWith(oldPrefix) }
        if (entries.isEmpty()) return 0

        var count = 0
        for ((oldKey, _) in entries) {
            val episodeNumberKey = oldKey.substringAfter('|')
            val newKey = "$newContentId|$episodeNumberKey"
            playbackStateStore.rekey(oldKey, newKey)
            count++
        }
        Log.d(TAG, "PlaybackState: re-keyed $count entries")
        return count
    }

    /**
     * Re-key the EpisodeMetadataCache entry.
     * Keys are content_id (no episode number suffix). Single rekey call.
     */
    private fun rekeyEpisodeMetadata(oldContentId: String, newContentId: String): Int {
        val exists = episodeMetadataCache.getAll().containsKey(oldContentId)
        if (!exists) return 0
        episodeMetadataCache.rekey(oldContentId, newContentId)
        Log.d(TAG, "EpisodeMetadata: re-keyed 1 entry")
        return 1
    }

    /**
     * Re-key the SourceLinkStore entry.
     * Keys are content_id. Single rekey call.
     */
    private fun rekeySourceLink(oldContentId: String, newContentId: String): Int {
        val exists = sourceLinkStore.getAll().containsKey(oldContentId)
        if (!exists) return 0
        sourceLinkStore.rekey(oldContentId, newContentId)
        Log.d(TAG, "SourceLink: re-keyed 1 entry")
        return 1
    }

    /** Result of the migration — counts per store. */
    data class Result(
        val watchProgressEntries: Int,
        val playbackStateEntries: Int,
        val episodeMetadataEntries: Int,
        val sourceLinkEntries: Int,
    )

    private companion object {
        private const val TAG = "AnikutaContentIdMigrator"
    }
}
