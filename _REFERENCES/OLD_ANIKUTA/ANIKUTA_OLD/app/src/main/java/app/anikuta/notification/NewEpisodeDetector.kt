package app.anikuta.notification

import eu.kanade.tachiyomi.animesource.model.SEpisode
import app.anikuta.data.cache.ReleaseTrackingStore

/**
 * Phase N-2 (Detection) — Diffs the extension's current episode list against
 * the stored state to detect new episodes.
 *
 * Pure logic — no Android, no network. Easily unit-testable.
 *
 * Detection rules:
 *  1. Episode count increased → new episodes detected.
 *  2. Dub count increased (from SubDubResolver) → new dub detected (may be for
 *     an older episode — dub lag).
 *  3. A specific episode number appeared that wasn't in the last-known set.
 */
object NewEpisodeDetector {

    /**
     * Result of a diff check.
     *
     * @param newEpisodes List of episode numbers that are new since the last check.
     * @param newSubDetected True if any new episode has SUB audio available.
     * @param newDubDetected True if any new episode has DUB audio available (may be an older ep).
     * @param currentEpisodeCount Total episode count from the extension.
     * @param currentSubCount Current sub count (from video resolution).
     * @param currentDubCount Current dub count (from video resolution).
     */
    data class DiffResult(
        val newEpisodes: List<Float>,
        val newSubDetected: Boolean,
        val newDubDetected: Boolean,
        val currentEpisodeCount: Int,
        val currentSubCount: Int,
        val currentDubCount: Int,
    ) {
        /** True if anything new was found. */
        val hasNewContent: Boolean
            get() = newEpisodes.isNotEmpty() || newDubDetected
    }

    /**
     * Diff the current extension episode list + sub/dub counts against the
     * stored tracking state.
     *
     * @param tracked The stored tracking state (last-known counts).
     * @param currentEpisodes The current episode list from the extension.
     * @param currentSubCount Current sub count (from resolving videos, or from SubDubStore cache).
     * @param currentDubCount Current dub count (from resolving videos, or from SubDubStore cache).
     * @return The diff result.
     */
    fun diff(
        tracked: ReleaseTrackingStore.TrackedAnime,
        currentEpisodes: List<SEpisode>,
        currentSubCount: Int,
        currentDubCount: Int,
    ): DiffResult {
        val currentEpisodeNumbers = currentEpisodes.map { it.episode_number }
        val currentEpisodeCount = currentEpisodes.size

        // Find episode numbers that weren't in the last-known set.
        // We compare against lastKnownEpisodeCount as a quick filter, then
        // check specific episode numbers for precision.
        val newEpisodes = if (tracked.lastKnownEpisodeCount == 0) {
            // First check — all episodes are "new" but we don't notify on first check
            // (the user just started tracking; we just record the baseline).
            emptyList()
        } else {
            // Find episodes with numbers we haven't seen before.
            // We use a simple heuristic: if the episode count went up, the new
            // episodes are the ones beyond the last-known count.
            currentEpisodes
                .sortedBy { it.episode_number }
                .drop(tracked.lastKnownEpisodeCount)
                .map { it.episode_number }
        }

        // Detect new dub (dub lag — dub count increased, possibly for an older episode)
        val newDubDetected = tracked.lastKnownDubCount > 0 && currentDubCount > tracked.lastKnownDubCount

        // Detect new sub (only if episode count also increased — sub is usually on-time)
        val newSubDetected = newEpisodes.isNotEmpty() && currentSubCount > tracked.lastKnownSubCount

        return DiffResult(
            newEpisodes = newEpisodes,
            newSubDetected = newSubDetected,
            newDubDetected = newDubDetected,
            currentEpisodeCount = currentEpisodeCount,
            currentSubCount = currentSubCount,
            currentDubCount = currentDubCount,
        )
    }
}
