package app.confused.anikuta.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.download.DownloadManager
import app.confused.anikuta.core.download.DownloadStatus
import app.confused.anikuta.core.download.DownloadTask
import app.confused.anikuta.core.download.ServerDiscoveryStore
import app.confused.anikuta.core.tracker.Tracker
import app.confused.anikuta.core.tracker.TrackerManager
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import app.confused.anikuta.data.extension.repo.ExtensionRepoApi
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import app.confused.anikuta.download.DownloadOrchestrator
import app.confused.anikuta.download.EnqueueResult
import app.confused.anikuta.download.PickerContext
import app.confused.anikuta.feature.animedetails.EpisodeDownloadState
import app.confused.anikuta.feature.animedetails.WatchEpisodeContext
import app.confused.anikuta.feature.search.data.RecentSearchesStore
import app.confused.anikuta.feature.search.data.SearchUiPreferences
import app.confused.anikuta.core.videoresolver.ResolverResult
import app.confused.anikuta.core.videoresolver.ResolverService
import app.confused.anikuta.core.videoresolver.ResolverVideo
import app.confused.anikuta.core.videoresolver.SubtitleTrack
import app.confused.anikuta.core.videoresolver.VideoResolverState
import app.confused.anikuta.feature.watch.WatchRequest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the episode being resolved + its source + the full episode list + the
 * watch context (anime title, cover, metadata map). Used for retry-on-error
 * and for constructing the [WatchRequest] when the user picks a video.
 *
 * Stores [contentId] (Phase 6 identity — e.g. `"al:154587"`) so the resolver
 * overlay (rendered at the root level) can build the [WatchRequest] without
 * needing a reference to the detail screen. The AniList ID for the
 * [WatchRequest] is derived from [contentId] (parsed from the `"al:NNN"`
 * format) — see [AppController.anilistIdFromContentId].
 */
data class ResolveTarget(
    val episode: SEpisode,
    val source: AnimeSource,
    val episodeList: List<SEpisode>,
    val watchCtx: WatchEpisodeContext,
    val contentId: String,
)

/**
 * The central state holder + business-logic coordinator for the app shell.
 *
 * **Why this exists:** The previous `MainActivity.kt` held ~20 `mutableStateOf`
 * vars + all the resolve/download/navigation logic inside the `AnikutaApp()`
 * composable (1174 lines). This class extracts that state + logic into a Koin
 * singleton so that:
 *
 * 1. Voyager `Screen` classes can stay thin (just call composables + forward
 *    events to this controller).
 * 2. Overlay sheets (resolver, linking, download picker) rendered at the root
 *    level can read/write shared state without prop-drilling.
 * 3. Business logic (resolve, download, cancel) lives outside the composable
 *    (addresses `RULES/ai-agent-rules.md` §3 — "UI files contain ONLY display
 *    logic and user event forwarding").
 *
 * **Architecture note (flagged for future refactor):** This class is a
 * coordination point that bridges `:feature:video-resolver`, `:core:download`,
 * `:feature:watch`, and `:feature:anime-details`. It lives in `:app` (the
 * composition root) per Rule §14 — `:core:download` cannot import
 * `:feature:video-resolver`. A future refactor could split this into per-
 * concern coordinators (`WatchCoordinator`, `DownloadActionCoordinator`,
 * `OverlayStateHolder`) — tracked as a follow-up.
 *
 * **Navigator reference:** Set by [AnikutaRoot] via a `SideEffect` so this
 * controller can push/pop screens. Null before the root composable mounts.
 */
class AppController(
    private val resolverService: ResolverService,
    val downloadManager: DownloadManager,
    private val downloadOrchestrator: DownloadOrchestrator,
    val trackerManager: TrackerManager,
    val anilistApi: AniListApi,
    val extensionManager: AnimeExtensionManager,
    val sourceMatcher: SourceMatcher,
    val extensionLinkStore: ExtensionLinkStore,
    val sourceLinkStore: app.confused.anikuta.data.extension.cache.SourceLinkStore,
    val recentsStore: RecentSearchesStore,
    val searchUiPreferences: SearchUiPreferences,
    val repoRepository: ExtensionRepoRepository,
    val repoApi: ExtensionRepoApi,
    private val serverDiscoveryStore: ServerDiscoveryStore,
    val themePrefs: app.confused.anikuta.core.preferences.ThemePreferences,
    val linkingPreferences: app.confused.anikuta.core.preferences.LinkingPreferences,
    val animeRepository: AnimeRepository,
    val adManager: app.confused.anikuta.core.ads.AdManager,
    val updateManager: app.confused.anikuta.core.appupdate.AppUpdateManager,
    private val context: Context,
) {

    // ── Navigator reference (set by AnikutaRoot) ──
    var navigator: Navigator? = null

    // ── Overlay state (read by AnikutaRoot, written by screens + this controller) ──

    /** Video resolver sheet state. */
    var resolverState by mutableStateOf<VideoResolverState>(VideoResolverState.Hidden)
        private set

    /** The episode being resolved (for retry + building WatchRequest on video pick). */
    var resolveTarget by mutableStateOf<ResolveTarget?>(null)
        private set

    /** Extension → AniList linking sheet target (null = sheet hidden). */
    var linkingTarget by mutableStateOf<Pair<AnimeCatalogueSource, SAnime>?>(null)

    // ── Pending unlink download action ──
    // When the user taps "Unlink from AniList" and the anime has downloaded episodes,
    // this state is set with the details. AnikutaRoot observes it + shows an AlertDialog
    // asking: "Transfer downloads to the extension-only entry, or delete them?"
    data class UnlinkDownloadAction(
        val anilistId: Int,
        val sourceId: Long?,
        val animeUrl: String?,
        val animeTitle: String,
        val hasDownloads: Boolean,
    )
    var pendingUnlinkDownloadAction by mutableStateOf<UnlinkDownloadAction?>(null)
        private set

    /** Download video picker sheet target (null = sheet hidden). */
    var downloadPickerTarget by mutableStateOf<EnqueueResult.ShowPicker?>(null)
        private set

    /**
     * The deferred navigation action that is waiting for the ad interaction
     * to complete. When non-null, the [AdDialog] is shown.
     *
     * Set by [withAdGate] before any anime-detail navigation. Cleared when
     * the ad interaction completes (or is cancelled) — at which point the
     * deferred action is executed.
     */
    var pendingAdNavigation by mutableStateOf<(() -> Unit)?>(null)
        private set

    /**
     * Whether the ad system is waiting for the user to return from the browser.
     *
     * When true, [onAdReturn] should be called (from the Activity's onResume).
     * The ad manager checks if the user stayed long enough.
     */
    var adAwaitingReturn by mutableStateOf(false)
        private set

    /**
     * The update info for the update dialog. When non-null, the
     * [UpdateBottomSheet] is shown (if the dismiss cooldown allows).
     */
    var showUpdateDialog by mutableStateOf(false)
        private set

    /** Episodes currently resolving (tapped download, waiting for source response). */
    val resolvingEpisodes = mutableStateMapOf<String, Boolean>()

    /** The tracker ID when an OAuth login is in progress. */
    var pendingTrackerAuth by mutableStateOf<Int?>(null)
        private set

    /** Current bottom-nav tab ("home" | "library" | "search" | "more"). */
    var currentTab by mutableStateOf("home")
        private set

    // ── Coroutine scope for async work (resolve, download, OAuth) ──
    // Declared BEFORE the init block so it's available during construction.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Download tasks flow (from the download manager) ──
    // Collected into a MutableStateFlow so that both `.value` access (for
    // download action functions) and Flow collection (for composables) work.
    // The raw `episodeDownloadStates` is a regular Flow (not StateFlow), so we
    // bridge it here.
    private val _downloadTasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val downloadTasksFlow: StateFlow<Map<String, DownloadTask>> = _downloadTasks.asStateFlow()

    init {
        scope.launch {
            downloadManager.episodeDownloadStates.collect { tasks ->
                _downloadTasks.value = tasks
            }
        }
    }

    // ── Download error toast tracking ──
    private val previousErrorIds = mutableStateOf<Set<Long>>(emptySet())

    // ════════════════════════════════════════════════════════════════════
    //  Navigation
    // ════════════════════════════════════════════════════════════════════

    /**
     * Ad gate — checks if an ad should be shown before executing [action].
     *
     * Called before EVERY anime-detail navigation. If [AdManager.shouldShowAd]
     * returns true, the action is deferred (stored in [pendingAdNavigation]) +
     * the ad dialog is shown. When the ad interaction completes (or is
     * cancelled), [executePendingNavigation] runs the deferred action.
     *
     * If no ad is needed, [action] runs immediately.
     *
     * This ensures the user sees ads before opening anime details — regardless
     * of which entry point they used (browse, search, library, downloads,
     * history, continue-watching, extension linking, etc.).
     *
     * @param action the navigation action to execute (possibly deferred).
     */
    private fun withAdGate(action: () -> Unit) {
        if (adManager.shouldShowAd()) {
            // Store the deferred action + show the ad dialog.
            pendingAdNavigation = action
            adManager.startAdDialog()
            Log.i("AnikutaAds", "withAdGate: ad gate triggered — navigation deferred")
        } else {
            // No ad needed — execute immediately.
            action()
        }
    }

    /**
     * Executes the deferred navigation after the ad interaction completes.
     * Called by [onAdAccepted] (after the user returns from the browser) or
     * [onAdCancelled] (when the user cancels).
     */
    private fun executePendingNavigation() {
        val action = pendingAdNavigation
        pendingAdNavigation = null
        action?.invoke()
    }

    /**
     * Called by the UI when the user clicks OK on the ad dialog.
     *
     * Opens the browser to the ad URL + sets [adAwaitingReturn] so the
     * Activity's onResume can call [onAdReturn].
     */
    fun onAdAccepted() {
        val state = adManager.state.value as? app.confused.anikuta.core.ads.AdInteractionState.DialogShowing
            ?: return
        adManager.acceptAd()
        adAwaitingReturn = true
        // Open the browser.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.adUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Log.i("AnikutaAds", "onAdAccepted: opened browser to ${state.adUrl}")
        } catch (e: Exception) {
            Log.e("AnikutaAds", "onAdAccepted: failed to open browser", e)
            // If the browser can't open, treat as a cancel — proceed with navigation.
            adAwaitingReturn = false
            adManager.cancelAd()
            executePendingNavigation()
        }
    }

    /**
     * Called by the UI when the user clicks Cancel on the ad dialog.
     * Cancels the ad + proceeds with the deferred navigation (ad NOT counted).
     */
    fun onAdCancelled() {
        adManager.cancelAd()
        executePendingNavigation()
    }

    /**
     * Called by the Activity's onResume lifecycle callback when the ad system
     * is awaiting the user's return from the browser.
     *
     * Checks if the user stayed long enough:
     * - If yes → records the ad + proceeds with navigation.
     * - If no → transitions to "ReturnedTooEarly" (UI shows a message).
     */
    fun onAdReturn() {
        if (!adAwaitingReturn) return
        adAwaitingReturn = false
        val counted = adManager.onAdReturn()
        if (counted) {
            // Ad was counted — proceed with the deferred navigation.
            executePendingNavigation()
        }
        // If not counted (returned too early), the UI will show the message.
        // The user can then dismiss it (→ onAdTooEarlyDismiss) or retry.
    }

    /**
     * Called by the UI when the user dismisses the "returned too early" message.
     * Goes back to the ad dialog so the user can try again.
     */
    fun onAdTooEarlyRetry() {
        adManager.dismissTooEarly()
    }

    /**
     * Called by the UI when the user gives up from the "returned too early" state.
     * Cancels the ad + proceeds with navigation (ad NOT counted).
     */
    fun onAdTooEarlyCancel() {
        adManager.cancelFromTooEarly()
        executePendingNavigation()
    }

    // ── Update dialog controls ──

    /** Shows the update dialog (called by AnikutaRoot on startup if an update is available). */
    fun showUpdateSheet() {
        showUpdateDialog = true
    }

    /** Dismisses the update dialog + records the 6-hour cooldown. */
    fun dismissUpdateSheet() {
        showUpdateDialog = false
        updateManager.dismissUpdate()
    }

    /** Hides the update dialog WITHOUT recording the cooldown (for after download starts). */
    fun hideUpdateSheet() {
        showUpdateDialog = false
    }

    fun pushDetail(anilistId: Int) {
        withAdGate {
            navigator?.push(AnimeDetailDestination(anilistId))
        }
    }

    /**
     * Open the anime detail page for a downloaded anime, given its [contentId].
     *
     * **Phase 6 (ADR-050):** handles BOTH content_id flavors:
     *  - `"al:NNN"` (AniList-linked): extracts the AniList ID + pushes [AnimeDetailDestination].
     *  - `"aniyomi:<sourceId>:<url>"` (unlinked extension anime): resolves the
     *    source via [sourceMatcher], reconstructs a minimal `SAnime` (url only —
     *    the title is loaded by the detail page from the source), and pushes
     *    [ExtensionAnimeDetailDestination] in extension mode.
     *
     * Used by the Downloaded Files screen's "tap an episode" callback. The actual
     * offline playback happens when the user taps an episode on the detail page
     * (`resolveEpisode` short-circuits to the local copy).
     *
     * If the source is no longer installed, shows a toast + logs (the user can
     * still delete the download or find the anime via Search).
     */
    fun openDownloadedAnimeByContentId(contentId: String) {
        val anilistId = anilistIdFromContentId(contentId)
        if (anilistId != 0) {
            pushDetail(anilistId)
            return
        }
        // Unlinked extension anime — parse "aniyomi:<sourceId>:<url>".
        val parts = contentId.split(":", limit = 3)
        if (parts.size == 3 && parts[0] == "aniyomi") {
            val sourceId = parts[1].toLongOrNull()
            val sourceContentId = parts[2]
            if (sourceId != null && sourceContentId.isNotBlank()) {
                val source = sourceMatcher.getSourceById(sourceId)
                if (source != null) {
                    val sAnime = SAnimeImpl().apply {
                        url = sourceContentId
                        // Title left blank — ExtensionDetailsProvider loads it
                        // from the source on the detail page.
                        title = ""
                    }
                    pushExtensionDetail(source, sAnime, anilistId = null)
                    Log.i(TAG, "openDownloadedAnimeByContentId: opened unlinked anime " +
                        "(contentId=$contentId source=${source.name})")
                    return
                } else {
                    Toast.makeText(
                        context,
                        "Source no longer installed for this download",
                        Toast.LENGTH_LONG,
                    ).show()
                    Log.w(TAG, "openDownloadedAnimeByContentId: source $sourceId not installed " +
                        "(contentId=$contentId)")
                    return
                }
            }
        }
        Toast.makeText(
            context,
            "Cannot open this anime from here — use the Library",
            Toast.LENGTH_LONG,
        ).show()
        Log.w(TAG, "openDownloadedAnimeByContentId: cannot resolve contentId=$contentId")
    }

    /**
     * Push the unified details page in extension mode (replaces the old
     * `pushExtensionDetail` which pushed the now-removed `ExtensionDetailScreen`).
     *
     * @param anilistId optional — when non-null, the ExtensionDetailsProvider
     *   merges AniList metadata into the view (linked extension anime).
     */
    fun pushExtensionDetail(source: AnimeCatalogueSource, sAnime: SAnime, anilistId: Int? = null) {
        withAdGate {
            // Parcelable fix: pass only serializable primitives — the live source +
            // SAnime are resolved in ExtensionAnimeDetailDestination.Content().
            navigator?.push(ExtensionAnimeDetailDestination(
                sourceId = source.id,
                animeUrl = sAnime.url,
                animeTitle = sAnime.title,
                thumbnailUrl = sAnime.thumbnail_url,
                anilistId = anilistId,
            ))
        }
    }

    /**
     * Opens a library anime's details page. Handles BOTH linked + unlinked anime:
     * - **Linked** (`anilistId != null`): pushes [AnimeDetailDestination] (AniList mode).
     * - **Unlinked** (`anilistId == null`): resolves the extension source from
     *   `anime.sourceId`, reconstructs the `SAnime` from `anime.url` + `anime.title`,
     *   and pushes [ExtensionAnimeDetailDestination] (extension mode).
     *
     * If the source is no longer installed, shows a toast + falls back to pushing
     * the AniList details page with `anilistId = 0` (which will show an error state).
     *
     * This fixes the bug where unlinked extension anime saved to the library were
     * not openable on tap (the old `onOpenAnime(anilistId ?: return)` silently bailed).
     *
     * ═══ Fix 6 (UNLINK-LINK-SAVE-FIXES) ═══
     * **Branching change:** when `anime.sourceId > 0` we now ALWAYS open in
     * Extension mode (passing `anilistId` along) — even if `anilistId != null`.
     * This survives stale library snapshots: the library list re-emits rows
     * asynchronously after `unlinkFromAniList` clears `anilist_id`, so a brief
     * window exists where the snapshot still has the old `anilist_id` but the DB
     * row's `anilist_id` is already NULL. Opening in AniList mode during that
     * window would put the new VM in AniList mode → `observeByAnilistId` returns
     * null → `_isSaved = false` (the "saved anime sometimes shows unsaved" bug).
     * Opening in Extension mode keeps the `(sourceId, url)` natural key as the
     * primary identity, which is always present on the row.
     */
    fun openLibraryAnime(anime: app.confused.anikuta.core.common.model.Anime) {
        val anilistId = anime.anilistId
        val sourceId = anime.sourceId

        // ── Branch 1: extension-sourced row (sourceId > 0) — always Extension mode ──
        if (sourceId > 0L) {
            val source = sourceMatcher.getSourceById(sourceId)
            val sAnime = SAnimeImpl().apply {
                url = anime.url
                title = anime.title
            }
            if (source != null) {
                Log.i(TAG, "openLibraryAnime: opening in Extension mode " +
                    "(sourceId=$sourceId, anilistId=$anilistId, title='${anime.title}')")
                pushExtensionDetail(source, sAnime, anilistId = anilistId)
            } else {
                // Source not installed — still open the details page with saved DB data.
                // The user can see saved episodes but can't play/download (the source
                // is gone). They can use the "Source unavailable" chip on the details
                // page to switch to another extension (or "Link to AniList" to link
                // the saved DB data to a fresh AniList entry).
                Log.w(TAG, "openLibraryAnime: Source $sourceId not installed — opening " +
                    "with saved data for '${anime.title}' (anilistId=$anilistId)")
                navigator?.push(LibraryExtensionDetailDestination(
                    sourceId = sourceId,
                    animeUrl = anime.url,
                    animeTitle = anime.title,
                ))
            }
            return
        }

        // ── Branch 2: AniList-only row (sourceId == 0, anilistId != null) — AniList mode ──
        if (anilistId != null) {
            Log.i(TAG, "openLibraryAnime: opening in AniList mode " +
                "(sourceId=$sourceId, anilistId=$anilistId, title='${anime.title}')")
            pushDetail(anilistId)
            return
        }

        // ── Branch 3: no usable identity — bail with a toast (defensive) ──
        Log.w(TAG, "openLibraryAnime: no usable identity (sourceId=$sourceId, " +
            "anilistId=$anilistId) — cannot open '${anime.title}'")
        Toast.makeText(
            context,
            "Cannot open '${anime.title}' — no source or AniList link",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * Unlinks an anime from AniList — transitions the library entry to extension-only.
     *
     * **Behavior (Phase fix):**
     * 1. Find the existing library row by anilistId.
     * 2. Clear `anilist_id` on that row (keeps `source_id`/`url`/`favorite=true`
     *    — so the anime stays saved in the library as an extension-only entry).
     * 3. Remove the SourceLinkStore entry for `"al:$anilistId"` (AniList → ext link).
     * 4. Remove the ExtensionLinkStore entry (ext → AniList reverse-link).
     * 5. Remove the per-anime view preference (so a future re-link starts fresh).
     * 6. Navigate to `ExtensionAnimeDetailDestination` (or `LibraryExtensionDetailDestination`
     *    if the source extension was uninstalled) with the sourceId + SAnime from the
     *    saved link — so the details page reopens in extension mode.
     *
     * @param anilistId the AniList ID to unlink.
     * @param sourceId the extension source ID (if known — otherwise resolved from SourceLinkStore).
     * @param animeUrl the extension anime URL (if known — otherwise resolved from SourceLinkStore).
     */
    fun unlinkFromAniList(anilistId: Int, sourceId: Long? = null, animeUrl: String? = null) {
        // Phase 4: SourceLinkStore keys by content_id ("al:$anilistId").
        val contentId = "al:$anilistId"
        val link = sourceLinkStore.getLink(contentId)
        val sid = sourceId ?: link?.sourceId
        val url = animeUrl ?: link?.animeUrl
        val title = link?.animeTitle ?: "Unknown"

        scope.launch {
            // ── Check if the anime has downloaded episodes ──
            // If yes, prompt the user: transfer or delete?
            val hasDownloads = withContext(Dispatchers.IO) {
                try {
                    downloadManager.getDownloadedEpisodes(contentId).isNotEmpty()
                } catch (e: Exception) {
                    Log.w(TAG, "unlinkFromAniList: failed to check downloads (assuming none)", e)
                    false
                }
            }

            if (hasDownloads) {
                Log.i(TAG, "unlinkFromAniList: anime has downloaded episodes — prompting user")
                pendingUnlinkDownloadAction = UnlinkDownloadAction(
                    anilistId = anilistId,
                    sourceId = sid,
                    animeUrl = url,
                    animeTitle = title,
                    hasDownloads = true,
                )
            } else {
                // No downloads — proceed directly with the unlink.
                performUnlink(anilistId, sid, url, title, contentId, deleteDownloads = false)
            }
        }
    }

    /**
     * Called by the AnikutaRoot's AlertDialog when the user picks an action
     * for the downloads during unlink.
     *
     * @param deleteDownloads true = delete all downloaded episodes for this anime.
     *   false = transfer (keep them — the contentId changes from "al:X" to the
     *   extension's local_id, so the ContentIdMigrator re-keys them).
     */
    fun confirmUnlinkWithDownloadAction(deleteDownloads: Boolean) {
        val action = pendingUnlinkDownloadAction ?: return
        pendingUnlinkDownloadAction = null
        val contentId = "al:${action.anilistId}"
        scope.launch {
            if (deleteDownloads) {
                Log.i(TAG, "confirmUnlink: deleting downloads for contentId=$contentId")
                try {
                    downloadManager.deleteAnimeDownloads(contentId)
                } catch (e: Exception) {
                    Log.e(TAG, "confirmUnlink: failed to delete downloads (non-fatal)", e)
                }
            } else {
                Log.i(TAG, "confirmUnlink: transferring downloads (ContentIdMigrator will re-key)")
                // The ContentIdMigrator (Phase 5) handles re-keying from "al:X" to the
                // extension's local_id when the anilist_id is cleared. The downloads stay
                // on disk; their keys are updated by the migrator.
                // TODO: trigger ContentIdMigrator.migrate("al:X", localId) here once
                // the local_id is known. For now, the downloads stay under the old key
                // — they'll be found by the filesystem fallback in isEpisodeDownloaded.
            }
            performUnlink(action.anilistId, action.sourceId, action.animeUrl,
                action.animeTitle, contentId, deleteDownloads)
        }
    }

    /** Cancels the unlink download action dialog (user tapped "Cancel"). */
    fun cancelUnlinkDownloadAction() {
        pendingUnlinkDownloadAction = null
    }

    private suspend fun performUnlink(
        anilistId: Int,
        sid: Long?,
        url: String?,
        title: String,
        contentId: String,
        deleteDownloads: Boolean,
    ) {
        try {
            // ── Step 1+2: Clear anilist_id on the library row (transition to extension-only) ──
            val existing = animeRepository.getByAnilistId(anilistId)
            if (existing != null) {
                animeRepository.clearAnilistId(existing.id)
                Log.i(TAG, "performUnlink: cleared anilistId on row id=${existing.id} " +
                    "(now extension-only, favorite=${existing.favorite})")
            } else {
                Log.w(TAG, "performUnlink: no library row found for anilistId=$anilistId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "performUnlink: failed to clear anilistId (non-fatal)", e)
        }

        // ── Step 3: Remove the SourceLinkStore entry ──
        sourceLinkStore.removeLink(contentId)

        // ── Step 4: Remove the ExtensionLinkStore entry ──
        if (sid != null && url != null) {
            extensionLinkStore.unlink(sid, url)
        }

        // ── Step 5: Remove the view preference ──
        try {
            org.koin.core.context.GlobalContext.get()
                .get<app.confused.anikuta.data.extension.cache.DetailsViewPreferenceStore>()
                .remove(anilistId)
        } catch (e: Exception) {
            Log.w(TAG, "performUnlink: failed to remove view preference (non-fatal)", e)
        }

        Log.i(TAG, "performUnlink: unlinked anilistId=$anilistId from source " +
            "$sid (url=$url, title=$title, deleteDownloads=$deleteDownloads)")

        // ── Step 6: Navigate to the extension-mode details page ──
        if (sid != null && url != null) {
            val source = sourceMatcher.getSourceById(sid)
            val sAnime = SAnimeImpl().apply {
                this.url = url
                this.title = title
            }
            if (source != null) {
                Log.i(TAG, "performUnlink: navigating to ExtensionAnimeDetailDestination " +
                    "(source='${source.name}', forceInitialRefresh=true)")
                navigator?.replace(ExtensionAnimeDetailDestination(
                    sourceId = source.id,
                    animeUrl = sAnime.url,
                    animeTitle = sAnime.title,
                    thumbnailUrl = sAnime.thumbnail_url,
                    anilistId = null,
                    forceInitialRefresh = true,
                ))
            } else {
                Log.i(TAG, "performUnlink: navigating to LibraryExtensionDetailDestination " +
                    "(sourceId=$sid, forceInitialRefresh=true)")
                navigator?.replace(LibraryExtensionDetailDestination(
                    sourceId = sid,
                    animeUrl = url,
                    animeTitle = title,
                    forceInitialRefresh = true,
                ))
            }
        } else {
            Toast.makeText(context, "Unlinked from AniList", Toast.LENGTH_SHORT).show()
            navigator?.pop()
        }
    }

    var pendingExtensionSource: AnimeCatalogueSource? = null
        private set
    var pendingExtensionSAnime: SAnime? = null
        private set
    var pendingAniyomiFileUri: android.net.Uri? = null

    fun pushWatch(watchRequest: WatchRequest) {
        pendingWatchRequest = watchRequest
        navigator?.push(WatchDestination)
    }

    /** The WatchRequest for the current/last watch session. Stored here (not in
     *  the Voyager Screen) so it survives Activity recreation. */
    var pendingWatchRequest: WatchRequest? = null
        private set

    fun switchTab(route: String) {
        currentTab = route
        val tabScreen = when (route) {
            "home" -> BrowseTabDestination
            "library" -> LibraryTabDestination
            "search" -> SearchTabDestination
            "more" -> MoreTabDestination
            else -> BrowseTabDestination
        }
        navigator?.replace(tabScreen)
    }

    /**
     * Clears the entire back stack and shows the Library tab. Used after a
     * backup restore completes (the user should land on their restored library,
     * not on the backup/restore wizard).
     */
    fun navigateToLibraryTab() {
        currentTab = "library"
        navigator?.replaceAll(LibraryTabDestination)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Overlay state mutations
    // ════════════════════════════════════════════════════════════════════

    fun hideResolver() {
        resolverState = VideoResolverState.Hidden
    }

    /**
     * Switches the AniList entry for the currently-viewed anime. Used by the
     * "Switch anime" three-dot menu option — the user searched AniList + picked
     * a different (correct) anime.
     *
     * Updates the SourceLinkStore (moves the source link from the old anilistId
     * to the new one) + ExtensionLinkStore (updates the sourceId:url→anilistId
     * mapping), then navigates to the new anime's details page (replaces the
     * current page — no stacking).
     */
    fun switchAnilistAnime(currentAnilistId: Int, newAnilistId: Int) {
        if (currentAnilistId == newAnilistId) {
            Log.i("AnikutaSearch", "switchAnilistAnime: same anime — no-op")
            return
        }
        // Phase 4: SourceLinkStore keys by content_id ("al:$anilistId").
        val oldContentId = "al:$currentAnilistId"
        val newContentId = "al:$newAnilistId"
        // Move the source link from the old anilistId to the new one.
        val link = sourceLinkStore.getLink(oldContentId)
        if (link != null) {
            sourceLinkStore.removeLink(oldContentId)
            sourceLinkStore.saveLink(newContentId, link.sourceId, link.animeUrl, link.animeTitle)
            // Update the extension→anilist mapping too (backward-compat method converts anilistId → contentId).
            extensionLinkStore.linkByAnilistId(link.sourceId, link.animeUrl, newAnilistId)
            Log.i("AnikutaSearch", "switchAnilistAnime: moved link $oldContentId → $newContentId (source=${link.sourceId})")
        } else {
            Log.w("AnikutaSearch", "switchAnilistAnime: no saved link for $oldContentId — nothing to move")
        }

        // Phase 5: re-key all cross-cutting stores (watch progress, playback state,
        // episode metadata) from the old content_id to the new one. This ensures
        // the anime's history follows it when the user corrects a wrong auto-match.
        try {
            val migrator = org.koin.core.context.GlobalContext.get()
                .get<app.confused.anikuta.migration.ContentIdMigrator>()
            migrator.migrate(oldContentId, newContentId)
        } catch (e: Exception) {
            Log.w("AnikutaSearch", "switchAnilistAnime: ContentIdMigrator failed (non-fatal)", e)
        }

        // Navigate to the new anime (replace — no stacking).
        navigator?.replace(AnimeDetailDestination(newAnilistId))
    }

    fun startLinking(source: AnimeCatalogueSource, sAnime: SAnime) {
        linkingTarget = source to sAnime
    }

    /**
     * Starts the AniList linking flow from the AniList details page (when the user
     * taps "Switch anime" to correct the auto-match link). Resolves the extension
     * source + SAnime from the saved [SourceLinkStore] link, then calls [startLinking].
     *
     * Used by [AnimeDetailDestination] when the user is on the AniList details page
     * but wants to re-link to a different AniList entry (the auto-match picked the
     * wrong one).
     */
    fun startLinkingFromAnilist(anilistId: Int) {
        // Phase 4: SourceLinkStore keys by content_id ("al:$anilistId").
        val contentId = "al:$anilistId"
        val link = sourceLinkStore.getLink(contentId) ?: run {
            android.widget.Toast.makeText(
                context,
                "No extension source linked — open from search to link one",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val source = sourceMatcher.getSourceById(link.sourceId) ?: run {
            android.widget.Toast.makeText(
                context,
                "Source '${link.sourceId}' no longer installed",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val sAnime = eu.kanade.tachiyomi.animesource.model.SAnimeImpl().apply {
            url = link.animeUrl
            title = link.animeTitle
        }
        startLinking(source, sAnime)
    }

    fun dismissLinking() {
        linkingTarget = null
    }

    fun dismissDownloadPicker() {
        downloadPickerTarget = null
    }

    fun updatePendingTrackerAuth(trackerId: Int?) {
        pendingTrackerAuth = trackerId
    }

    // ════════════════════════════════════════════════════════════════════
    //  Episode resolution (watch flow)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Resolves videos from [source] for [episode] and updates [resolverState].
     * Called when the user taps an episode on the detail screen.
     *
     * **Offline-playback short-circuit:** FIRST checks
     * [DownloadManager.isEpisodeDownloaded]. If a completed copy is on disk,
     * builds a [WatchRequest] from the local file URI + local subtitle URIs
     * and pushes [WatchDestination] directly — skipping the resolver sheet.
     * Otherwise falls through to streaming resolution.
     *
     * **Phase 6 (ADR-050):** identity is [contentId] (e.g. `"al:154587"`).
     * Offline checks look up by `(contentId, episodeNumber)` — source-
     * independent, so a switched extension source still finds the download.
     * The [WatchRequest.anilistId] is derived from [contentId] (the `"al:NNN"`
     * segment) — 0 when the anime is unlinked (watch-progress save is skipped
     * in that case; flagged as a Phase 6 follow-up).
     */
    fun resolveEpisode(
        episode: SEpisode,
        source: AnimeSource,
        episodeList: List<SEpisode>,
        watchCtx: WatchEpisodeContext,
        contentId: String,
    ) {
        val epNum = episode.episode_number.toInt().let { if (it > 0) it else 0 }
        val anilistId = anilistIdFromContentId(contentId)

        scope.launch {
            // ── Offline-playback short-circuit ──
            try {
                if (downloadManager.isEpisodeDownloaded(contentId, episode.episode_number)) {
                    Log.i(TAG, "Offline hit: contentId=$contentId EP ${episode.episode_number} " +
                        "(anilistId=$anilistId) — using local copy")
                    val videoUri = downloadManager.getDownloadedVideoUri(contentId, episode.episode_number)
                    val subUris = downloadManager.getDownloadedSubtitleUris(contentId, episode.episode_number)
                    if (videoUri != null) {
                        Log.i(TAG, "Playing offline: ${episode.name} ($videoUri)")
                        pushWatch(
                            WatchRequest(
                                videoUrl = videoUri,
                                videoHeaders = null,
                                videoTitle = episode.name,
                                anilistId = anilistId,
                                animeTitle = watchCtx.animeTitle,
                                coverUrl = watchCtx.coverUrl,
                                coverColor = watchCtx.coverColorArgb.takeIf { it != 0 },
                                episodeUrl = episode.url,
                                episodeNumber = episode.episode_number,
                                sourceId = source.id,
                                source = source,
                                videoServer = "",
                                videoAudio = "",
                                videoQuality = 0,
                                episodeList = episodeList,
                                episodeMetadata = watchCtx.episodeMetadata,
                                subtitleTracks = subUris.map {
                                    SubtitleTrack(it, "Downloaded")
                                },
                                audioTracks = emptyList(),
                                resolvedServers = emptyList(),
                            )
                        )
                        return@launch
                    } else {
                        Log.w(TAG, "isEpisodeDownloaded=true but videoUri=null — falling back to stream")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Offline check failed, falling back to stream", e)
            }

            // ── Streaming path ──
            resolveTarget = ResolveTarget(episode, source, episodeList, watchCtx, contentId)
            resolverState = VideoResolverState.Resolving(epNum)
            Log.i("AnikutaResolver", "Resolving: ${episode.name} from ${source.name} " +
                "(${episodeList.size} episodes) contentId=$contentId")

            when (val result = resolverService.resolve(source, episode)) {
                is ResolverResult.Success -> {
                    // Record discovered servers during WATCH resolution so the
                    // user's server preferences are populated as they browse/watch.
                    try {
                        serverDiscoveryStore.recordServers(source.id, result.servers.map { it.name })
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to record servers during watch", e)
                    }
                    resolverState = VideoResolverState.Show(epNum, result.servers)
                }
                is ResolverResult.NoSources -> {
                    resolverState = VideoResolverState.NoSources(epNum)
                    Toast.makeText(context, "No video sources available", Toast.LENGTH_SHORT).show()
                }
                is ResolverResult.Error -> {
                    resolverState = VideoResolverState.Error(epNum, result.message)
                    Toast.makeText(context, "Failed to resolve: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Called when the user picks a video from the resolver sheet.
     * Builds a [WatchRequest] from the picked video + the stored [resolveTarget]
     * and pushes [WatchDestination].
     */
    fun onVideoSelected(video: ResolverVideo) {
        Log.i("AnikutaResolver", "Video selected: ${video.quality} (${video.url})")
        // CRITICAL: Capture servers BEFORE clearing resolverState.
        val servers = (resolverState as? VideoResolverState.Show)?.servers ?: emptyList()
        val target = resolveTarget
        resolverState = VideoResolverState.Hidden
        if (target != null) {
            // Find which server + audio version contains this video
            var serverName = ""
            var audioVersion = ""
            for (server in servers) {
                for (audio in server.audioVersions) {
                    if (audio.videos.any { it.url == video.url }) {
                        serverName = server.name
                        audioVersion = audio.label
                        break
                    }
                }
                if (serverName.isNotEmpty()) break
            }
            // Parse quality int from the quality string (e.g. "1080p" → 1080)
            val qualityInt = video.quality.replace("p", "").toIntOrNull() ?: 0
            val anilistId = anilistIdFromContentId(target.contentId)

            pushWatch(
                WatchRequest(
                    videoUrl = video.url,
                    videoHeaders = video.videoHeaders,
                    videoTitle = video.videoTitle.ifBlank { target.episode.name },
                    anilistId = anilistId,
                    animeTitle = target.watchCtx.animeTitle,
                    coverUrl = target.watchCtx.coverUrl,
                    coverColor = target.watchCtx.coverColorArgb.takeIf { it != 0 },
                    episodeUrl = target.episode.url,
                    episodeNumber = target.episode.episode_number,
                    sourceId = target.source.id,
                    source = target.source,
                    videoServer = serverName,
                    videoAudio = audioVersion,
                    videoQuality = qualityInt,
                    episodeList = target.episodeList,
                    episodeMetadata = target.watchCtx.episodeMetadata,
                    subtitleTracks = video.subtitleTracks,
                    audioTracks = video.audioTracks,
                    resolvedServers = servers,
                )
            )
        }
    }

    /** Retry the last resolution (from the resolver sheet's error state). */
    fun retryResolve() {
        val target = resolveTarget ?: return
        resolveEpisode(target.episode, target.source, target.episodeList, target.watchCtx, target.contentId)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Downloads
    // ════════════════════════════════════════════════════════════════════

    /**
     * Enqueues a download for an episode. Resolves the video URL (same flow as
     * watching) then enqueues via [DownloadOrchestrator].
     *
     * **Phase 6 (ADR-050):** takes [contentId] (String, e.g. `"al:154587"`).
     * The old `if (anilistId == 0)` hard gate is REMOVED — unlinked extension
     * anime (whose content_id is the local_id, e.g. `"aniyomi:123:url"`) are
     * now downloadable. The [contentId] should be derived by the caller from
     * the anime's content_id (Phase 1 backfill populates this), or fall back to
     * `"al:$anilistId"` for AniList-linked anime. Should never be empty — if
     * it is, log a warning + return (defensive; shouldn't happen with Phase 1).
     */
    fun downloadEpisode(
        episode: SEpisode,
        source: AnimeSource,
        watchCtx: WatchEpisodeContext,
        contentId: String,
    ) {
        if (contentId.isBlank()) {
            Log.w(TAG, "downloadEpisode: blank contentId — cannot enqueue (anilistId fallback should have produced one)")
            Toast.makeText(context, "Cannot download — no content identity for this anime", Toast.LENGTH_SHORT).show()
            return
        }
        val animeInfo = app.confused.anikuta.core.download.DownloadAnimeInfo(
            contentId = contentId,
            title = watchCtx.animeTitle.ifBlank { "Anime" },
            coverUrl = watchCtx.coverUrl,
        )
        // Immediate Resolving state on the row — instant feedback.
        resolvingEpisodes[episode.url] = true
        Log.i(TAG, "Download requested: ${animeInfo.title} EP ${episode.episode_number} " +
            "(contentId=$contentId)")
        scope.launch {
            try {
                val result = downloadOrchestrator.enqueueDownload(animeInfo, episode, source)
                when (result) {
                    is EnqueueResult.Success ->
                        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                    is EnqueueResult.ShowPicker -> {
                        downloadPickerTarget = result
                    }
                    is EnqueueResult.NoSources ->
                        Toast.makeText(context, "No video sources available for this episode", Toast.LENGTH_LONG).show()
                    is EnqueueResult.Error ->
                        Toast.makeText(context, "Download failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download enqueue failed", e)
                Toast.makeText(context, "Download failed: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            } finally {
                resolvingEpisodes.remove(episode.url)
            }
        }
    }

    /** Called when the user picks a video from the download picker sheet. */
    fun enqueuePickedVideo(
        video: ResolverVideo,
        serverName: String,
        audioLabel: String,
    ) {
        val target = downloadPickerTarget ?: return
        downloadPickerTarget = null
        resolvingEpisodes[target.episode.url] = true
        scope.launch {
            try {
                val ctx = PickerContext(
                    anime = target.anime,
                    episode = target.episode,
                    source = target.source,
                )
                val result = downloadOrchestrator.enqueueSpecific(video, serverName, audioLabel, ctx)
                when (result) {
                    is EnqueueResult.Success ->
                        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                    is EnqueueResult.Error ->
                        Toast.makeText(context, "Download failed: ${result.message}", Toast.LENGTH_LONG).show()
                    else -> {}
                }
            } finally {
                resolvingEpisodes.remove(target.episode.url)
            }
        }
    }

    // ── Download row actions ──
    //
    // Phase 6 (ADR-050): these take (contentId, episodeUrl) — the contentId
    // replaces the old anilistId, and the task is located by iterating the
    // downloadTasksFlow (keyed by `"$contentId|$episodeNumber"`) and matching
    // on (contentId, episodeUrl). Keeping episodeUrl in the signature means
    // the UI callbacks (EpisodesSection / EpisodeRow / AnimeDetailScreen) don't
    // need to change.

    fun cancelDownload(contentId: String, episodeUrl: String) {
        // If resolving, just clear the resolving flag (the resolve coroutine
        // will complete + its result is ignored since the user cancelled).
        if (resolvingEpisodes[episodeUrl] == true) {
            resolvingEpisodes.remove(episodeUrl)
            downloadPickerTarget?.let { if (it.episode.url == episodeUrl) downloadPickerTarget = null }
            return
        }
        val task = downloadTasksFlow.value.values.firstOrNull {
            it.request.anime.contentId == contentId && it.request.episode.episodeUrl == episodeUrl
        } ?: run {
            Log.w(TAG, "cancelDownload: no task for contentId=$contentId episodeUrl=$episodeUrl")
            return
        }
        scope.launch { downloadManager.cancelDownload(task.id) }
    }

    fun resumeDownload(contentId: String, episodeUrl: String) {
        val task = downloadTasksFlow.value.values.firstOrNull {
            it.request.anime.contentId == contentId && it.request.episode.episodeUrl == episodeUrl
        } ?: return
        scope.launch { downloadManager.resumeDownload(task.id) }
    }

    fun retryDownload(contentId: String, episodeUrl: String) {
        val task = downloadTasksFlow.value.values.firstOrNull {
            it.request.anime.contentId == contentId && it.request.episode.episodeUrl == episodeUrl
        } ?: return
        scope.launch { downloadManager.retryDownload(task.id) }
    }

    fun deleteDownload(contentId: String, episodeUrl: String) {
        val task = downloadTasksFlow.value.values.firstOrNull {
            it.request.anime.contentId == contentId && it.request.episode.episodeUrl == episodeUrl
        } ?: return
        scope.launch {
            downloadManager.deleteDownload(task.id)
            Toast.makeText(context, "Download deleted", Toast.LENGTH_SHORT).show()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Download state helpers (for AnimeDetailDestination)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Builds the per-episode download-state map for the given anime.
     *
     * **Phase 6 (ADR-050):** iterates [tasksMap] + filters by
     * `task.request.anime.contentId == contentId`. The returned map is keyed by
     * `episodeUrl` (derived from each task's `request.episode.episodeUrl`) so
     * the UI (`EpisodesSection`/`EpisodeRow`) can keep looking up state by
     * `episode.url` — no UI changes needed despite the underlying composite
     * task key changing from `"$anilistId:$episodeUrl"` → `"$contentId|$episodeNumber"`.
     *
     * Reads [resolvingEpisodes] (a Compose SnapshotStateMap) so that composables
     * calling this during composition will recompose when it changes.
     */
    fun getDownloadStates(
        contentId: String,
        tasksMap: Map<String, DownloadTask>,
    ): Map<String, EpisodeDownloadState> {
        val states = mutableMapOf<String, EpisodeDownloadState>()
        tasksMap.values.forEach { task ->
            if (task.request.anime.contentId == contentId) {
                states[task.request.episode.episodeUrl] = when (task.status) {
                    DownloadStatus.QUEUED -> EpisodeDownloadState.Queued
                    DownloadStatus.DOWNLOADING -> EpisodeDownloadState.Downloading(task.progress)
                    DownloadStatus.PAUSED -> EpisodeDownloadState.Paused
                    DownloadStatus.ERROR -> EpisodeDownloadState.Error(task.errorMessage)
                    DownloadStatus.COMPLETED -> EpisodeDownloadState.Downloaded
                    DownloadStatus.CANCELLED -> EpisodeDownloadState.NotDownloaded
                }
            }
        }
        // Merge resolving episodes — these take priority (immediate spinner).
        resolvingEpisodes.forEach { (episodeUrl, isResolving) ->
            if (isResolving) {
                states[episodeUrl] = EpisodeDownloadState.Resolving
            }
        }
        return states
    }

    /**
     * Checks ALL download tasks for new ERROR transitions and shows a toast.
     * Called from [AnikutaRoot]'s LaunchedEffect observing the download flow.
     */
    fun checkForDownloadErrors(tasksMap: Map<String, DownloadTask>) {
        val currentErrors = tasksMap.values
            .filter { it.status == DownloadStatus.ERROR }
        val newErrors = currentErrors.filter { it.id !in previousErrorIds.value }
        if (newErrors.isNotEmpty()) {
            val firstError = newErrors.first()
            val msg = firstError.errorMessage ?: "Unknown error"
            Toast.makeText(context, "Download failed: $msg", Toast.LENGTH_LONG).show()
            previousErrorIds.value = (previousErrorIds.value + newErrors.map { it.id }).toSet()
        }
        // Clean up old non-error IDs from the tracking set
        val currentIds = tasksMap.values.map { it.id }.toSet()
        previousErrorIds.value = previousErrorIds.value.intersect(currentIds)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Linking flow callbacks
    // ════════════════════════════════════════════════════════════════════

    /**
     * Called when the extension→AniList linking sheet successfully links.
     *
     * **Fix 1 (SOURCE-SWITCH-FIXES):** Now navigates to
     * [ExtensionAnimeDetailDestination] (passing the original extension [source]
     * + [sAnime] that the user tapped) instead of [AnimeDetailDestination].
     * This way:
     *  - The details page opens in **Extension mode** (uses the tapped extension
     *    as the source — no SourceMatcher re-matching needed).
     *  - The [anilistId] is passed for AniList metadata enrichment (Stage-D
     *    merge in `ExtensionDetailsProvider`).
     *  - The original extension source + SAnime that the user tapped is
     *    preserved (previously lost when navigating to the AniList-mode page).
     *
     * If the extension detail page is on top, it's replaced; otherwise the
     * detail page is pushed.
     */
    fun onLinked(
        anilistId: Int,
        wasCached: Boolean,
        source: AnimeCatalogueSource,
        sAnime: SAnime,
    ) {
        linkingTarget = null
        val nav = navigator
        // If we're ALREADY on a detail page (either flavor), REPLACE it — don't push
        // a second one. Pushing two AnimeDetailDestination instances causes a Voyager
        // SaveableStateHolder key collision crash during the transition.
        val onDetailPage = nav?.lastItem is ExtensionAnimeDetailDestination ||
            nav?.lastItem is AnimeDetailDestination
        // Navigate to ExtensionAnimeDetailDestination so the page opens in Extension
        // mode (using the tapped extension as the source). The anilistId is passed
        // for AniList metadata enrichment (Stage-D merge). No re-matching via
        // SourceMatcher needed — the source is already known.
        Log.i("AnikutaSearch", "onLinked: navigating to ExtensionAnimeDetailDestination " +
            "(source='${source.name}', sAnime.title='${sAnime.title}', anilistId=$anilistId, " +
            "wasCached=$wasCached, onDetailPage=$onDetailPage)")

        // ═══ Fix 5 (UNLINK-LINK-SAVE-FIXES) ═══
        // Persist the REVERSE link (AniList → extension) in SourceLinkStore so:
        //   - Cold starts can resolve the extension source from the AniList ID
        //     (ExtensionDetailsProvider.load(ByAniListId) reverse-looks-up via
        //     sourceLinkStore.getLink("al:$anilistId")).
        //   - `unlinkFromAniList` can find the source/url without relying on the
        //     live destination (which may not be on the stack at unlink time).
        // The ExtensionLinkStore (ext → AniList) is written by
        // ExtensionLinkingViewModel.attemptLink — this is the symmetric write.
        try {
            sourceLinkStore.saveLink(
                contentId = "al:$anilistId",
                sourceId = source.id,
                animeUrl = sAnime.url,
                animeTitle = sAnime.title,
            )
            Log.i("AnikutaSearch", "onLinked: saved SourceLinkStore reverse link " +
                "(contentId=al:$anilistId → sourceId=${source.id}, url=${sAnime.url})")
        } catch (e: Exception) {
            Log.w("AnikutaSearch", "onLinked: failed to save SourceLinkStore reverse " +
                "link (non-fatal) — anilistId=$anilistId", e)
        }

        // Ad gate: wrap the navigation so an ad is shown before the detail page opens.
        withAdGate {
            if (onDetailPage) {
                nav?.replace(ExtensionAnimeDetailDestination(
                    sourceId = source.id,
                    animeUrl = sAnime.url,
                    animeTitle = sAnime.title,
                    thumbnailUrl = sAnime.thumbnail_url,
                    anilistId = anilistId,
                ))
            } else {
                nav?.push(ExtensionAnimeDetailDestination(
                    sourceId = source.id,
                    animeUrl = sAnime.url,
                    animeTitle = sAnime.title,
                    thumbnailUrl = sAnime.thumbnail_url,
                    anilistId = anilistId,
                ))
            }
        }
        if (!wasCached) {
            Toast.makeText(context, "Linked to AniList", Toast.LENGTH_SHORT).show()
            Log.i("AnikutaSearch", "Linked (fresh): ${sAnime.title} → AniList $anilistId")
        } else {
            Log.i("AnikutaSearch", "Linked (cached): ${sAnime.title} → AniList $anilistId (no toast)")
        }
    }

    /**
     * Called when the user picks "go without linking" on the linking sheet.
     * Pushes the unified details page in extension mode (anilistId = null →
     * unlinked extension anime). Replaces the old `ExtensionDetailScreen` push.
     */
    fun onGoWithoutLinking(source: AnimeCatalogueSource, sAnime: SAnime) {
        linkingTarget = null
        // Parcelable fix: pass only serializable primitives. The pendingExtension*
        // fields are kept for backward-compat with any code that still reads them,
        // but the destination itself no longer needs them.
        pendingExtensionSource = source
        pendingExtensionSAnime = sAnime
        val nav = navigator
        withAdGate {
            if (nav != null && nav.lastItem !is ExtensionAnimeDetailDestination) {
                nav.push(ExtensionAnimeDetailDestination(
                    sourceId = source.id,
                    animeUrl = sAnime.url,
                    animeTitle = sAnime.title,
                    thumbnailUrl = sAnime.thumbnail_url,
                    anilistId = null,
                ))
            }
        }
        Log.i("AnikutaSearch", "Go-without-linking: ${sAnime.title} from ${source.name}")
    }

    // ════════════════════════════════════════════════════════════════════
    //  OAuth callback processing
    // ════════════════════════════════════════════════════════════════════

    /**
     * Processes an OAuth callback URL (from the tracker redirect).
     * Determines the tracker by the URL host, calls its auth callback, and
     * shows a toast with the result.
     */
    fun handleOAuthCallback(callbackUrl: String) {
        val uri = android.net.Uri.parse(callbackUrl)
        val trackerId = when (uri.host) {
            "anilist-auth" -> Tracker.ANILIST_ID
            "myanimelist-auth" -> Tracker.MAL_ID
            else -> pendingTrackerAuth
        }
        val tracker = trackerId?.let { trackerManager.getTracker(it) }
        if (tracker != null) {
            scope.launch {
                val success = tracker.handleAuthCallback(callbackUrl)
                if (success) {
                    Toast.makeText(context, "${tracker.name} connected", Toast.LENGTH_SHORT).show()
                    Log.i("AnikutaTracker", "${tracker.name} login successful")
                } else {
                    Toast.makeText(context, "${tracker.name} login failed", Toast.LENGTH_SHORT).show()
                    Log.e("AnikutaTracker", "${tracker.name} login failed")
                }
                pendingTrackerAuth = null
            }
        } else {
            pendingTrackerAuth = null
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Phase 6 helpers (ADR-050 — content_id migration)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Extracts the AniList media ID from a [contentId] of the form `"al:NNN"`.
     * Returns 0 for non-AniList content_ids (unlinked extension anime,
     * MAL/TMDB-linked anime, etc.) — WatchRequest.anilistId is a legacy Int
     * field used by watch-progress saving (Phase 3) + tracker sync, both of
     * which skip cleanly when anilistId == 0.
     *
     * Tricky: a future Phase 6 follow-up should add `contentId: String` to
     * WatchRequest so unlinked offline playback also saves watch progress.
     */
    private fun anilistIdFromContentId(contentId: String): Int {
        if (!contentId.startsWith("al:")) return 0
        return contentId.removePrefix("al:").toIntOrNull() ?: 0
    }

    companion object {
        private const val TAG = "AnikutaAppController"
    }
}
