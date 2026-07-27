package app.confused.anikuta.navigation

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import app.confused.anikuta.core.anilist.api.AniListApi
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
import app.confused.anikuta.feature.videoresolver.ResolverResult
import app.confused.anikuta.feature.videoresolver.ResolverService
import app.confused.anikuta.feature.videoresolver.ResolverVideo
import app.confused.anikuta.feature.videoresolver.SubtitleTrack
import app.confused.anikuta.feature.videoresolver.VideoResolverState
import app.confused.anikuta.feature.watch.WatchRequest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the episode being resolved + its source + the full episode list + the
 * watch context (anime title, cover, metadata map). Used for retry-on-error
 * and for constructing the [WatchRequest] when the user picks a video.
 *
 * Stores [anilistId] so the resolver overlay (rendered at the root level) can
 * build the [WatchRequest] without needing a reference to the detail screen.
 */
data class ResolveTarget(
    val episode: SEpisode,
    val source: AnimeSource,
    val episodeList: List<SEpisode>,
    val watchCtx: WatchEpisodeContext,
    val anilistId: Int,
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
    val recentsStore: RecentSearchesStore,
    val searchUiPreferences: SearchUiPreferences,
    val repoRepository: ExtensionRepoRepository,
    val repoApi: ExtensionRepoApi,
    private val serverDiscoveryStore: ServerDiscoveryStore,
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
        private set

    /** Download video picker sheet target (null = sheet hidden). */
    var downloadPickerTarget by mutableStateOf<EnqueueResult.ShowPicker?>(null)
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

    fun pushDetail(anilistId: Int) {
        navigator?.push(AnimeDetailDestination(anilistId))
    }

    /**
     * Push the unified details page in extension mode (replaces the old
     * `pushExtensionDetail` which pushed the now-removed `ExtensionDetailScreen`).
     *
     * @param anilistId optional — when non-null, the ExtensionDetailsProvider
     *   merges AniList metadata into the view (linked extension anime).
     */
    fun pushExtensionDetail(source: AnimeCatalogueSource, sAnime: SAnime, anilistId: Int? = null) {
        navigator?.push(ExtensionAnimeDetailDestination(source, sAnime, anilistId))
    }

    fun pushWatch(watchRequest: WatchRequest) {
        navigator?.push(WatchDestination(watchRequest))
    }

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

    fun startLinking(source: AnimeCatalogueSource, sAnime: SAnime) {
        linkingTarget = source to sAnime
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
     */
    fun resolveEpisode(
        episode: SEpisode,
        source: AnimeSource,
        episodeList: List<SEpisode>,
        watchCtx: WatchEpisodeContext,
        anilistId: Int,
    ) {
        val epNum = episode.episode_number.toInt().let { if (it > 0) it else 0 }

        scope.launch {
            // ── Offline-playback short-circuit ──
            try {
                if (anilistId != 0 && downloadManager.isEpisodeDownloaded(anilistId, episode.url)) {
                    val videoUri = downloadManager.getDownloadedVideoUri(anilistId, episode.url)
                    val subUris = downloadManager.getDownloadedSubtitleUris(anilistId, episode.url)
                    if (videoUri != null) {
                        Log.i("AnikutaDownload", "Playing offline: ${episode.name} ($videoUri)")
                        pushWatch(
                            WatchRequest(
                                videoUrl = videoUri,
                                videoHeaders = null,
                                videoTitle = episode.name,
                                anilistId = anilistId,
                                animeTitle = watchCtx.animeTitle,
                                coverUrl = watchCtx.coverUrl,
                                coverColor = null,
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
                    }
                }
            } catch (e: Exception) {
                Log.w("AnikutaDownload", "Offline check failed, falling back to stream", e)
            }

            // ── Streaming path ──
            resolveTarget = ResolveTarget(episode, source, episodeList, watchCtx, anilistId)
            resolverState = VideoResolverState.Resolving(epNum)
            Log.i("AnikutaResolver", "Resolving: ${episode.name} from ${source.name} (${episodeList.size} episodes)")

            when (val result = resolverService.resolve(source, episode)) {
                is ResolverResult.Success -> {
                    // Record discovered servers during WATCH resolution so the
                    // user's server preferences are populated as they browse/watch.
                    try {
                        serverDiscoveryStore.recordServers(source.id, result.servers.map { it.name })
                    } catch (e: Exception) {
                        Log.w("AnikutaDownload", "Failed to record servers during watch", e)
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
            pushWatch(
                WatchRequest(
                    videoUrl = video.url,
                    videoHeaders = video.videoHeaders,
                    videoTitle = video.videoTitle.ifBlank { target.episode.name },
                    anilistId = target.anilistId,
                    animeTitle = target.watchCtx.animeTitle,
                    coverUrl = target.watchCtx.coverUrl,
                    coverColor = null, // TODO: extract from coverUrl via Palette
                    episodeUrl = target.episode.url,
                    episodeNumber = target.episode.episode_number,
                    sourceId = target.source.id,
                    source = target.source,
                    videoServer = "",
                    videoAudio = "",
                    videoQuality = 0,
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
        resolveEpisode(target.episode, target.source, target.episodeList, target.watchCtx, target.anilistId)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Downloads
    // ════════════════════════════════════════════════════════════════════

    /**
     * Enqueues a download for an episode. Resolves the video URL (same flow as
     * watching) then enqueues via [DownloadOrchestrator].
     */
    fun downloadEpisode(
        episode: SEpisode,
        source: AnimeSource,
        watchCtx: WatchEpisodeContext,
        anilistId: Int,
    ) {
        if (anilistId == 0) {
            Toast.makeText(context, "Cannot download — anime not linked", Toast.LENGTH_SHORT).show()
            return
        }
        val animeInfo = app.confused.anikuta.core.download.DownloadAnimeInfo(
            anilistId = anilistId,
            title = watchCtx.animeTitle.ifBlank { "Anime $anilistId" },
            coverUrl = watchCtx.coverUrl,
        )
        // Immediate Resolving state on the row — instant feedback.
        resolvingEpisodes[episode.url] = true
        Log.i("AnikutaDownload", "Download requested: ${animeInfo.title} EP ${episode.episode_number}")
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
                Log.e("AnikutaDownload", "Download enqueue failed", e)
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

    fun cancelDownload(anilistId: Int, episodeUrl: String) {
        // If resolving, just clear the resolving flag (the resolve coroutine
        // will complete + its result is ignored since the user cancelled).
        if (resolvingEpisodes[episodeUrl] == true) {
            resolvingEpisodes.remove(episodeUrl)
            downloadPickerTarget?.let { if (it.episode.url == episodeUrl) downloadPickerTarget = null }
            return
        }
        val task = downloadTasksFlow.value["$anilistId:$episodeUrl"] ?: return
        scope.launch { downloadManager.cancelDownload(task.id) }
    }

    fun resumeDownload(anilistId: Int, episodeUrl: String) {
        val task = downloadTasksFlow.value["$anilistId:$episodeUrl"] ?: return
        scope.launch { downloadManager.resumeDownload(task.id) }
    }

    fun retryDownload(anilistId: Int, episodeUrl: String) {
        val task = downloadTasksFlow.value["$anilistId:$episodeUrl"] ?: return
        scope.launch { downloadManager.retryDownload(task.id) }
    }

    fun deleteDownload(anilistId: Int, episodeUrl: String) {
        val task = downloadTasksFlow.value["$anilistId:$episodeUrl"] ?: return
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
     * Keyed by episode URL (stripped of the anilistId prefix).
     *
     * Reads [resolvingEpisodes] (a Compose SnapshotStateMap) so that composables
     * calling this during composition will recompose when it changes.
     */
    fun getDownloadStates(
        anilistId: Int,
        tasksMap: Map<String, DownloadTask>,
    ): Map<String, EpisodeDownloadState> {
        val states = tasksMap
            .filterKeys { it.startsWith("$anilistId:") }
            .mapKeys { it.key.substringAfter(':') }
            .mapValues { (_, task) ->
                when (task.status) {
                    DownloadStatus.QUEUED -> EpisodeDownloadState.Queued
                    DownloadStatus.DOWNLOADING -> EpisodeDownloadState.Downloading(task.progress)
                    DownloadStatus.PAUSED -> EpisodeDownloadState.Paused
                    DownloadStatus.ERROR -> EpisodeDownloadState.Error(task.errorMessage)
                    DownloadStatus.COMPLETED -> EpisodeDownloadState.Downloaded
                    DownloadStatus.CANCELLED -> EpisodeDownloadState.NotDownloaded
                }
            }
            .toMutableMap()
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
     * Navigates to the anime detail page. If the extension detail page is on
     * top, it's replaced; otherwise the detail page is pushed.
     */
    fun onLinked(anilistId: Int, wasCached: Boolean, sAnimeTitle: String) {
        linkingTarget = null
        val nav = navigator
        // If we're on the extension-entry unified page, replace it with the AniList-
        // entry unified page (same screen, AniList data source). Otherwise push.
        if (nav != null && nav.lastItem is ExtensionAnimeDetailDestination) {
            nav.replace(AnimeDetailDestination(anilistId))
        } else {
            nav?.push(AnimeDetailDestination(anilistId))
        }
        if (!wasCached) {
            Toast.makeText(context, "Linked to AniList", Toast.LENGTH_SHORT).show()
            Log.i("AnikutaSearch", "Linked (fresh): $sAnimeTitle → AniList $anilistId")
        } else {
            Log.i("AnikutaSearch", "Linked (cached): $sAnimeTitle → AniList $anilistId (no toast)")
        }
    }

    /**
     * Called when the user picks "go without linking" on the linking sheet.
     * Pushes the unified details page in extension mode (anilistId = null →
     * unlinked extension anime). Replaces the old `ExtensionDetailScreen` push.
     */
    fun onGoWithoutLinking(source: AnimeCatalogueSource, sAnime: SAnime) {
        linkingTarget = null
        val nav = navigator
        if (nav != null && nav.lastItem !is ExtensionAnimeDetailDestination) {
            nav.push(ExtensionAnimeDetailDestination(source, sAnime, anilistId = null))
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
}
