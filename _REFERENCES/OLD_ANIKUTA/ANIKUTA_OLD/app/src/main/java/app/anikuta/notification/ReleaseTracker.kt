package app.anikuta.notification

import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.model.AniListTitle
import app.anikuta.data.anilist.model.AniListCoverImage
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.ReleaseTrackingStore
import app.anikuta.data.cache.SubDubStore
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.source.bridge.AniyomiSourceBridge
import app.anikuta.source.bridge.SourceMatchResult
import app.anikuta.download.DownloadManager
import app.anikuta.ui.library.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase N-2 (Detection) — The release-tracking brain.
 *
 * This is the core logic that runs when [ReleaseTrackerWorker] wakes up. It:
 *  1. Gets all tracked anime whose nextScheduledCheck is due.
 *  2. For each, fetches the extension's current episode list.
 *  3. Diffs against the stored state via [NewEpisodeDetector].
 *  4. If new episodes found, resolves videos via [SubDubResolver] to detect sub/dub.
 *  5. Records the release time (feeds the offset learning).
 *  6. Fires notifications (Phase N-3 — NotificationDispatcher) + auto-download (Phase N-5).
 *  7. Updates the store + re-schedules via [ReleaseCheckPlanner].
 *
 * Architecture: this class holds NO state — it's a pure orchestrator that
 * delegates to the store, detector, resolver, dispatcher, and planner.
 */
class ReleaseTracker(
    private val store: ReleaseTrackingStore,
    private val sourceManager: AnimeSourceManager,
    private val bridge: AniyomiSourceBridge,
    private val subDubResolver: SubDubResolver,
    private val prefs: NotificationPreferences,
    private val planner: ReleaseCheckPlanner,
    private val libraryStore: LibraryStore,
    private val subDubStore: SubDubStore,
    private val anilistRepository: AniListRepository,
    private val notificationDispatcher: NotificationDispatcher,
    private val downloadManager: DownloadManager,
) {

    companion object {
        private const val TAG = "ReleaseTracker"
    }

    /**
     * Check all tracked anime whose nextScheduledCheck is due.
     * Called by [ReleaseTrackerWorker.doWork].
     *
     * @return The number of anime that were checked.
     */
    suspend fun checkDueAnime(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dueAnime = store.getDueChecks(now)

        if (dueAnime.isEmpty()) {
            Log.d(TAG, "checkDueAnime: no anime due")
            return@withContext 0
        }

        Log.d(TAG, "checkDueAnime: ${dueAnime.size} anime due for check")
        var checked = 0

        for (tracked in dueAnime) {
            try {
                checkSingleAnime(tracked, now)
                checked++
            } catch (e: Exception) {
                Log.e(TAG, "checkDueAnime: failed to check '${tracked.title}' (id=${tracked.anilistId})", e)
                // Schedule a retry for this anime
                scheduleRetry(tracked, now)
            }
        }

        Log.d(TAG, "checkDueAnime: checked $checked/${dueAnime.size} anime")
        checked
    }

    /**
     * Check a single tracked anime for new episodes.
     */
    private suspend fun checkSingleAnime(tracked: ReleaseTrackingStore.TrackedAnime, now: Long) {
        Log.d(TAG, "checkSingleAnime: → '${tracked.title}' (id=${tracked.anilistId})")

        // 1. Get the AniList airing time for the next episode
        val anilistAiringAt = getAniListAiringTime(tracked.anilistId)

        // 2. Get the extension source (from cached mapping, or search)
        val source = getSource(tracked)
        if (source == null) {
            Log.w(TAG, "checkSingleAnime: no source available for '${tracked.title}' — will retry later")
            scheduleRetry(tracked, now)
            return
        }

        // 3. Fetch the current episode list from the extension
        val episodes = fetchEpisodes(source, tracked)
        if (episodes == null) {
            Log.w(TAG, "checkSingleAnime: failed to fetch episodes for '${tracked.title}' — will retry later")
            scheduleRetry(tracked, now)
            return
        }

        // 4. Get current sub/dub counts (from SubDubStore cache, or resolve videos)
        val subDubInfo = subDubStore.get(tracked.anilistId)
        val currentSubCount = subDubInfo?.subCount ?: 0
        val currentDubCount = subDubInfo?.dubCount ?: 0

        // 5. Diff against stored state
        val diffResult = NewEpisodeDetector.diff(tracked, episodes, currentSubCount, currentDubCount)

        if (diffResult.hasNewContent) {
            Log.d(TAG, "checkSingleAnime: NEW CONTENT for '${tracked.title}': " +
                "newEps=${diffResult.newEpisodes} newSub=${diffResult.newSubDetected} newDub=${diffResult.newDubDetected}")

            // 6. Resolve videos for the new episode(s) to detect sub/dub (if needed)
            var resolvedHasSub = diffResult.newSubDetected
            var resolvedHasDub = diffResult.newDubDetected

            if (diffResult.newEpisodes.isNotEmpty() && prefs.notifyMode().get() != "anilist") {
                // Resolve videos for the newest new episode
                val newestNewEpisode = episodes
                    .sortedByDescending { it.episode_number }
                    .firstOrNull { it.episode_number in diffResult.newEpisodes }

                if (newestNewEpisode != null) {
                    val resolveResult = subDubResolver.resolve(source, newestNewEpisode)
                    if (resolveResult != null && !resolveResult.timedOut) {
                        resolvedHasSub = resolveResult.hasSub
                        resolvedHasDub = resolveResult.hasDub
                    }
                }
            }

            // 7. Record the release time (feeds offset learning)
            if (anilistAiringAt > 0 && diffResult.newEpisodes.isNotEmpty()) {
                store.recordReleaseTime(
                    anilistId = tracked.anilistId,
                    anilistAiringAt = anilistAiringAt,
                    extensionDetectedAt = now,
                    isDub = false,
                )
            }

            // 8. Update the store with new counts
            store.updateEpisodeCounts(
                anilistId = tracked.anilistId,
                episodeCount = diffResult.currentEpisodeCount,
                subCount = diffResult.currentSubCount,
                dubCount = diffResult.currentDubCount,
                checkTime = now,
            )

            // 9. Fire notifications via NotificationDispatcher
            fireNotifications(tracked, diffResult, resolvedHasSub, resolvedHasDub, anilistAiringAt)

            // 10. Trigger auto-download (Phase N-5)
            if (shouldAutoDownload(tracked, resolvedHasSub, resolvedHasDub)) {
                triggerAutoDownload(tracked, source, episodes, diffResult.newEpisodes)
            }
        } else {
            Log.d(TAG, "checkSingleAnime: no new content for '${tracked.title}'")
            // Update the check time even if no new content
            store.updateEpisodeCounts(
                anilistId = tracked.anilistId,
                episodeCount = diffResult.currentEpisodeCount,
                subCount = diffResult.currentSubCount,
                dubCount = diffResult.currentDubCount,
                checkTime = now,
            )
        }

        // 11. Schedule the next check
        scheduleNextCheck(tracked, anilistAiringAt, now)
    }

    /**
     * Get the AniList airing time for the next episode of an anime.
     * Returns 0 if unknown.
     */
    private suspend fun getAniListAiringTime(anilistId: Int): Long {
        return try {
            // getAnimeDetails returns AniListAnime with nextAiringEpisode.airingAt (Unix seconds)
            val anime = anilistRepository.getAnimeDetails(anilistId)
            anime.nextAiringEpisode?.airingAt?.times(1000L) ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "getAniListAiringTime: failed for id=$anilistId: ${e.message}")
            0L
        }
    }

    /**
     * Get the extension source for a tracked anime.
     * Uses the cached sourceId if available; otherwise searches via the bridge.
     */
    private suspend fun getSource(tracked: ReleaseTrackingStore.TrackedAnime): AnimeCatalogueSource? {
        // Try cached sourceId first
        val sourceId = tracked.sourceId
        if (sourceId != null) {
            val source = sourceManager.get(sourceId) as? AnimeCatalogueSource
            if (source != null) return source
        }

        // Fall back to searching via the bridge
        return try {
            // Construct a minimal AniListAnime to search.
            val anilistAnime = AniListAnime(
                id = tracked.anilistId,
                title = AniListTitle(romaji = tracked.title, english = tracked.title, native = tracked.title),
                coverImage = AniListCoverImage(large = tracked.coverUrl),
            )
            val matchResult = bridge.findMatch(anilistAnime)
            when (matchResult) {
                is SourceMatchResult.SingleMatch -> {
                    // Find the source by name (same pattern as DetailViewModel.loadEpisodes)
                    val source = sourceManager.getCatalogueSources()
                        .find { it.name == matchResult.sourceName } as? AnimeCatalogueSource
                    if (source != null) {
                        // Cache the source mapping for next time
                        store.updateSourceMapping(tracked.anilistId, source.id, matchResult.sAnime.url)
                    }
                    source
                }
                is SourceMatchResult.MultipleMatches -> {
                    // Auto-pick the best (same as DetailViewModel)
                    val source = sourceManager.getCatalogueSources()
                        .find { it.name == matchResult.bestSourceName } as? AnimeCatalogueSource
                    if (source != null) {
                        store.updateSourceMapping(tracked.anilistId, source.id, matchResult.best.url)
                    }
                    source
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSource: failed to find source for '${tracked.title}': ${e.message}")
            null
        }
    }

    /**
     * Fetch the episode list from the extension source.
     */
    private suspend fun fetchEpisodes(
        source: AnimeCatalogueSource,
        tracked: ReleaseTrackingStore.TrackedAnime,
    ): List<SEpisode>? {
        return try {
            val animeUrl = tracked.animeUrl ?: return null

            // Create a minimal SAnime with just the URL (the source uses it to fetch episodes)
            val sAnime = SAnime.create().apply {
                url = animeUrl
                title = tracked.title
            }

            // getEpisodeList is the suspend wrapper around fetchEpisodeList (RxJava Observable)
            withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
        } catch (e: Exception) {
            Log.w(TAG, "fetchEpisodes: failed for '${tracked.title}': ${e.message}")
            null
        }
    }

    /**
     * Fire notifications based on the notify mode + per-anime settings.
     *
     * Mode 1 (anilist): notify at airing time — no sub/dub distinction.
     * Mode 2 (extension): notify only when extension confirms — sub/dub distinction.
     * Mode 3 (both): both notifications.
     */
    private fun fireNotifications(
        tracked: ReleaseTrackingStore.TrackedAnime,
        diffResult: NewEpisodeDetector.DiffResult,
        hasSub: Boolean,
        hasDub: Boolean,
        anilistAiringAt: Long,
    ) {
        val notifyEnabled = tracked.notifyOnNew ?: prefs.globalNotifyEnabled().get()
        if (!notifyEnabled) return

        val mode = prefs.notifyMode().get()
        val notifySub = tracked.notifySub ?: prefs.globalNotifySub().get()
        val notifyDub = tracked.notifyDub ?: prefs.globalNotifyDub().get()

        val notifications = mutableListOf<NotificationDispatcher.NewEpisodeNotification>()

        // Mode 1 or 3: AniList-based notification (no sub/dub distinction)
        if (mode == "anilist" || mode == "both") {
            if (diffResult.newEpisodes.isNotEmpty()) {
                notifications.add(
                    NotificationDispatcher.NewEpisodeNotification(
                        anilistId = tracked.anilistId,
                        title = tracked.title,
                        episodeNumber = diffResult.newEpisodes.maxOrNull() ?: 0f,
                        episodeName = null,
                        coverUrl = tracked.coverUrl,
                        hasSub = false,
                        hasDub = false,
                        isAniListOnly = true,
                    )
                )
            }
        }

        // Mode 2 or 3: Extension-confirmed notification (with sub/dub)
        if (mode == "extension" || mode == "both") {
            if (diffResult.newEpisodes.isNotEmpty()) {
                // Sub notification
                if (notifySub && hasSub) {
                    notifications.add(
                        NotificationDispatcher.NewEpisodeNotification(
                            anilistId = tracked.anilistId,
                            title = tracked.title,
                            episodeNumber = diffResult.newEpisodes.maxOrNull() ?: 0f,
                            episodeName = null,
                            coverUrl = tracked.coverUrl,
                            hasSub = true,
                            hasDub = false,
                            isAniListOnly = false,
                        )
                    )
                }
                // Dub notification
                if (notifyDub && hasDub) {
                    notifications.add(
                        NotificationDispatcher.NewEpisodeNotification(
                            anilistId = tracked.anilistId,
                            title = tracked.title,
                            episodeNumber = diffResult.newEpisodes.maxOrNull() ?: 0f,
                            episodeName = null,
                            coverUrl = tracked.coverUrl,
                            hasSub = false,
                            hasDub = true,
                            isAniListOnly = false,
                        )
                    )
                }
                // If neither sub nor dub was detected but we still have a new episode,
                // send a generic notification (audio = ANY)
                if (!hasSub && !hasDub && (notifySub || notifyDub)) {
                    notifications.add(
                        NotificationDispatcher.NewEpisodeNotification(
                            anilistId = tracked.anilistId,
                            title = tracked.title,
                            episodeNumber = diffResult.newEpisodes.maxOrNull() ?: 0f,
                            episodeName = null,
                            coverUrl = tracked.coverUrl,
                            hasSub = false,
                            hasDub = false,
                            isAniListOnly = false,
                        )
                    )
                }
            }

            // Dub-lag: new dub detected for an older episode
            if (diffResult.newDubDetected && notifyDub && !diffResult.newEpisodes.isNotEmpty()) {
                notifications.add(
                    NotificationDispatcher.NewEpisodeNotification(
                        anilistId = tracked.anilistId,
                        title = tracked.title,
                        episodeNumber = 0f,  // unknown — dub lag
                        episodeName = "New dub episode available",
                        coverUrl = tracked.coverUrl,
                        hasSub = false,
                        hasDub = true,
                        isAniListOnly = false,
                    )
                )
            }
        }

        if (notifications.isNotEmpty()) {
            notificationDispatcher.notifyNewEpisodes(notifications)
            Log.d(TAG, "fireNotifications: sent ${notifications.size} notification(s) for '${tracked.title}'")
        }
    }

    /**
     * Trigger auto-download for new episodes via the DownloadManager.
     * Called when a new episode is detected AND the user opted in for auto-download.
     *
     * The DownloadManager resolves videos internally (via DownloadVideoResolver)
     * and picks the best quality. The user's preferred audio (SUB/DUB) from
     * NotificationPreferences is logged but NOT yet wired into the resolver —
     * that's a future enhancement (documented in the plan §2.5 Supabase note).
     */
    private fun triggerAutoDownload(
        tracked: ReleaseTrackingStore.TrackedAnime,
        source: AnimeCatalogueSource,
        episodes: List<SEpisode>,
        newEpisodeNumbers: List<Float>,
    ) {
        val sourceId = tracked.sourceId ?: source.id
        val sourceName = source.name

        for (episodeNumber in newEpisodeNumbers) {
            val episode = episodes.find { it.episode_number == episodeNumber } ?: continue
            try {
                val downloadId = downloadManager.enqueueDownload(
                    anilistId = tracked.anilistId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    animeTitle = tracked.title,
                    episode = episode,
                )
                Log.d(TAG, "triggerAutoDownload: ✓ enqueued '${episode.name}' for '${tracked.title}' (dlId=$downloadId)")
            } catch (e: Exception) {
                Log.e(TAG, "triggerAutoDownload: failed to enqueue '${episode.name}' for '${tracked.title}'", e)
            }
        }
    }

    /**
     * Check if auto-download should trigger for this anime.
     */
    private fun shouldAutoDownload(
        tracked: ReleaseTrackingStore.TrackedAnime,
        hasSub: Boolean,
        hasDub: Boolean,
    ): Boolean {
        val autoDlEnabled = tracked.autoDownloadNew ?: prefs.globalAutoDownloadEnabled().get()
        if (!autoDlEnabled) return false

        // Per-anime sub/dub settings override global. If per-anime is null (inherit global),
        // use the global audio preference (radio selector: SUB / DUB / ANY).
        val perAnimeSubSet = tracked.autoDownloadSub != null
        val perAnimeDubSet = tracked.autoDownloadDub != null

        return if (perAnimeSubSet || perAnimeDubSet) {
            // Per-anime override: use the per-anime sub/dub toggles
            val autoDlSub = tracked.autoDownloadSub ?: false
            val autoDlDub = tracked.autoDownloadDub ?: false
            (autoDlSub && hasSub) || (autoDlDub && hasDub)
        } else {
            // No per-anime override: use the global audio preference (radio selector)
            when (prefs.globalAutoDownloadAudio().get()) {
                "SUB" -> hasSub
                "DUB" -> hasDub
                "ANY" -> hasSub || hasDub
                else -> hasSub || hasDub  // default to "any"
            }
        }
    }

    /**
     * Schedule the next check for an anime.
     */
    private fun scheduleNextCheck(
        tracked: ReleaseTrackingStore.TrackedAnime,
        anilistAiringAt: Long,
        now: Long,
    ) {
        val nextCheck = planner.computeNextCheckTime(tracked, anilistAiringAt, now)
        store.setNextScheduledCheck(tracked.anilistId, nextCheck)
    }

    /**
     * Schedule a retry (episode not yet available — retry in RETRY_INTERVAL_MS).
     * Only retries if within the MAX_RETRY_WINDOW from the original airing time.
     */
    private fun scheduleRetry(tracked: ReleaseTrackingStore.TrackedAnime, now: Long) {
        // Check if we've exceeded the max retry window
        val firstCheckTime = tracked.lastSeenAiringAt
        if (firstCheckTime > 0 && now - firstCheckTime > ReleaseCheckPlanner.MAX_RETRY_WINDOW_MS) {
            Log.d(TAG, "scheduleRetry: max retry window exceeded for '${tracked.title}' — giving up until next airing")
            // Schedule for 24h from now as a fallback
            store.setNextScheduledCheck(tracked.anilistId, now + ReleaseCheckPlanner.FALLBACK_CHECK_INTERVAL_MS)
        } else {
            // Retry in RETRY_INTERVAL_MS
            store.setNextScheduledCheck(tracked.anilistId, now + ReleaseCheckPlanner.RETRY_INTERVAL_MS)
        }
    }

    // ---- Public API for the detail page to feed the tracker ----

    /**
     * Called when the user manually refreshes the episode list on the detail
     * page and new episodes are found. This feeds the release-time offset
     * learning (§2.5 of the plan).
     *
     * @param anilistId The AniList anime ID.
     * @param anilistAiringAt The AniList airing time for the detected episode (epoch millis), or 0.
     * @param newEpisodeCount The current episode count from the extension.
     * @param isDub True if this was a dub episode detection.
     */
    fun recordManualDetection(
        anilistId: Int,
        anilistAiringAt: Long,
        newEpisodeCount: Int,
        isDub: Boolean,
    ) {
        if (anilistAiringAt > 0) {
            store.recordReleaseTime(
                anilistId = anilistId,
                anilistAiringAt = anilistAiringAt,
                extensionDetectedAt = System.currentTimeMillis(),
                isDub = isDub,
            )
        }
        store.updateEpisodeCounts(
            anilistId = anilistId,
            episodeCount = newEpisodeCount,
            subCount = subDubStore.get(anilistId)?.subCount ?: 0,
            dubCount = subDubStore.get(anilistId)?.dubCount ?: 0,
            checkTime = System.currentTimeMillis(),
        )
    }

    /**
     * Called when the user adds an anime to their library.
     * Starts tracking it with global defaults.
     */
    fun startTracking(anilistId: Int, title: String, coverUrl: String?) {
        if (store.isTracked(anilistId)) return  // Already tracked
        val now = System.currentTimeMillis()
        store.put(
            ReleaseTrackingStore.TrackedAnime(
                anilistId = anilistId,
                title = title,
                coverUrl = coverUrl,
                nextScheduledCheck = now + ReleaseCheckPlanner.FALLBACK_CHECK_INTERVAL_MS,  // First check in 24h
            )
        )
        Log.d(TAG, "startTracking: now tracking '$title' (id=$anilistId)")
    }

    /**
     * Called when the user removes an anime from their library.
     * Stops tracking it and removes the stored state.
     */
    fun stopTracking(anilistId: Int) {
        store.remove(anilistId)
        Log.d(TAG, "stopTracking: stopped tracking id=$anilistId")
    }
}
