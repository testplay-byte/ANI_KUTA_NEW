package app.confused.anikuta.feature.watch

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.designsystem.theme.generateDynamicScheme
import app.confused.anikuta.core.player.AnikutaMPVView
import app.confused.anikuta.core.player.EpisodeListItem
import app.confused.anikuta.core.player.PlayerInitializer
import app.confused.anikuta.core.player.PlayerLoadingState
import app.confused.anikuta.core.player.PlayerMode
import app.confused.anikuta.core.player.PlayerObserver
import app.confused.anikuta.core.player.PlayerPreferences
import app.confused.anikuta.core.player.PlayerStateHolder
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.core.player.controls.EpisodeSwitchingOverlay
import app.confused.anikuta.core.player.controls.MinimizedControls
import app.confused.anikuta.feature.videoresolver.ResolverResult
import app.confused.anikuta.feature.videoresolver.ResolverService
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "AnikutaWatchScreen"

/**
 * The YouTube-style watch page (ADR-012).
 *
 * Layout (minimized mode):
 * ```
 * ┌─────────────────────────────┐
 * │      Top Navigation Bar     │  ← above the player, always visible
 * ├─────────────────────────────┤
 * │      Video Player (16:9)    │  ← sticky, always present
 * ├─────────────────────────────┤
 * │   Episode Description       │  ← scrolls
 * │   Episode List              │  ← scrolls
 * └─────────────────────────────┘
 * ```
 *
 * Layout (fullscreen mode):
 * ```
 * ┌─────────────────────────────┐
 * │                             │
 * │    Video Player (fills)     │  ← edge-to-edge, no top bar
 * │                             │
 * └─────────────────────────────┘
 * ```
 *
 * CRITICAL: The MPV [AnikutaMPVView] is hosted in a SINGLE [AndroidView] that
 * is ALWAYS in composition — it is NEVER disposed or recreated during mode
 * switches (ADR-025). Only the overlay controls and surrounding layout change.
 * This prevents the "child already has a parent" crash that occurred when two
 * separate AndroidView composables tried to share the same cached View.
 */
@Composable
fun WatchScreen(
    watchRequest: WatchRequest,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerPreferences = koinInject<PlayerPreferences>()
    val watchProgressStore = koinInject<WatchProgressStore>()
    val resolverService = remember { ResolverService() }
    val stateHolder = remember { PlayerStateHolder() }

    // Sheet visibility state (bottom-up menus)
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSubtitleSettingsSheet by remember { mutableStateOf(false) }
    // Cached resolved servers for quality switching — initialized from WatchRequest
    var resolvedServers by remember(watchRequest) {
        mutableStateOf(watchRequest.resolvedServers)
    }

    // Set the companion playerPreferences BEFORE inflating the view
    AnikutaMPVView.playerPreferences = playerPreferences

    // MPV view — cached, NEVER recreated. Owned by this composable.
    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    var observer by remember { mutableStateOf<PlayerObserver?>(null) }
    var mpvInitialized by remember { mutableStateOf(false) }

    val playerMode by stateHolder.playerMode.collectAsStateWithLifecycle()
    val isSwitching by stateHolder.isSwitchingEpisode.collectAsStateWithLifecycle()
    val controlsVisible by stateHolder.controlsVisible.collectAsStateWithLifecycle()

    // ── Immersive mode: hide/show system bars based on player mode ──
    // Per OLD project's hideSystemBars()/showSystemBars() pattern.
    DisposableEffect(playerMode) {
        val activity = context as? Activity
        if (playerMode == PlayerMode.FULLSCREEN) {
            // Hide system bars (immersive sticky)
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            @Suppress("DEPRECATION")
            (context as? Activity)?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        } else {
            // Show system bars (minimized mode)
            (context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            @Suppress("DEPRECATION")
            (context as? Activity)?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        onDispose { }
    }

    // Auto-hide controls: 5s minimized, 4s fullscreen
    LaunchedEffect(controlsVisible, playerMode, isSwitching) {
        if (controlsVisible && !isSwitching) {
            val delayMs = if (playerMode == PlayerMode.FULLSCREEN) 4000L else 5000L
            delay(delayMs)
            stateHolder.setControlsVisible(false)
        }
    }

    // Nested BackHandler for fullscreen → minimized (NOT exit watch page)
    // CRITICAL: Force SENSOR_PORTRAIT on minimize so the screen rotates back
    // to portrait. Using UNSPECIFIED leaves it in landscape.
    BackHandler(enabled = playerMode == PlayerMode.FULLSCREEN) {
        stateHolder.setPlayerMode(PlayerMode.MINIMIZED)
        (context as? Activity)?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    // Initialize episode list from the watch request + set current episode index
    LaunchedEffect(watchRequest) {
        stateHolder.setEpisodeList(
            watchRequest.episodeList.map { ep ->
                EpisodeListItem(
                    url = ep.url,
                    name = ep.name,
                    episodeNumber = ep.episode_number,
                    scanlator = ep.scanlator,
                    dateUpload = ep.date_upload ?: 0L,
                    summary = ep.summary,
                    thumbnailUrl = null,
                    seen = false,
                )
            }
        )
        // Find the index of the tapped episode. Match by URL first, then by
        // episode_number as fallback (URLs may differ between source calls).
        // Use Float comparison with tolerance for episode_number.
        val tappedIndex = watchRequest.episodeList.indexOfFirst { it.url == watchRequest.episodeUrl }
        val fallbackIndex = watchRequest.episodeList.indexOfFirst {
            kotlin.math.abs(it.episode_number - watchRequest.episodeNumber) < 0.01f
        }
        val finalIndex = when {
            tappedIndex >= 0 -> tappedIndex
            fallbackIndex >= 0 -> fallbackIndex
            else -> 0
        }
        // Log the episode list for debugging
        Log.i(TAG, "Episode index: tappedUrl=${watchRequest.episodeUrl}, " +
            "tappedEpisodeNumber=${watchRequest.episodeNumber}, " +
            "tappedIndex=$tappedIndex, fallbackIndex=$fallbackIndex, finalIndex=$finalIndex, " +
            "listSize=${watchRequest.episodeList.size}")
        watchRequest.episodeList.forEachIndexed { i, ep ->
            Log.d(TAG, "  ep[$i]: num=${ep.episode_number}, url=${ep.url.take(50)}, name=${ep.name}")
        }
        stateHolder.setCurrentEpisodeIndex(finalIndex)
        stateHolder.setCurrentVideoTitle(watchRequest.videoTitle)
        stateHolder.setCurrentVideoUrl(watchRequest.videoUrl)
        // CRITICAL: Also track the current episode URL + number for progress saving.
        // These update when the user switches episodes, so progress is saved
        // against the CORRECT episode (not the original watch request).
        stateHolder.setCurrentEpisodeUrl(watchRequest.episodeUrl)
        stateHolder.setCurrentEpisodeNumber(watchRequest.episodeNumber)
    }

    // ── MPV initialization ──
    // Called once when the AndroidView factory first creates the view.
    val initMpv: (AnikutaMPVView) -> Unit = remember { { view ->
        if (!mpvInitialized) {
            val obs = PlayerObserver(object : PlayerObserver.Callback {
                override fun onEvent(eventId: Int) {
                    when (eventId) {
                        MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                            Log.i(TAG, "MPV_EVENT_FILE_LOADED")
                            stateHolder.setSwitchingEpisode(false)
                            stateHolder.setLoadingState(PlayerLoadingState.READY)
                            stateHolder.setErrorMessage(null)

                            // ── Load external subtitle tracks via sub-add ──
                            // CRITICAL: sub-add MUST be sent AFTER FILE_LOADED.
                            // Sending before causes MPV to silently drop the track.
                            // Run on Dispatchers.IO because each sub-add triggers
                            // an HTTPS download inside MPV native code.
                            val subsToAdd = watchRequest.subtitleTracks
                            val audiosToAdd = watchRequest.audioTracks
                            if (subsToAdd.isNotEmpty() || audiosToAdd.isNotEmpty()) {
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        // Set HTTP headers for subtitle downloads
                                        val headers = watchRequest.videoHeaders
                                        if (!headers.isNullOrBlank()) {
                                            try {
                                                MPVLib.setOptionString("http-header-fields", headers)
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Failed to set headers for subs", e)
                                            }
                                        }
                                        // Send sub-add for each external subtitle track
                                        subsToAdd.forEach { sub ->
                                            try {
                                                Log.i(TAG, "sub-add: url=${sub.url.take(60)}... lang=${sub.lang}")
                                                MPVLib.command(arrayOf("sub-add", sub.url, "auto", "", sub.lang))
                                            } catch (e: Exception) {
                                                Log.e(TAG, "sub-add failed for lang=${sub.lang}", e)
                                            }
                                        }
                                        // Send audio-add for each external audio track
                                        audiosToAdd.forEach { audio ->
                                            try {
                                                Log.i(TAG, "audio-add: url=${audio.url.take(60)}... lang=${audio.lang}")
                                                MPVLib.command(arrayOf("audio-add", audio.url, "auto", "", audio.lang))
                                            } catch (e: Exception) {
                                                Log.e(TAG, "audio-add failed for lang=${audio.lang}", e)
                                            }
                                        }
                                    }
                                    // Re-read track-list after adding external tracks
                                    try {
                                        val (subs, audio) = view.loadTracks()
                                        stateHolder.setSubtitleTracks(subs)
                                        stateHolder.setAudioTracks(audio)
                                        Log.i(TAG, "Tracks loaded: ${subs.size} subs, ${audio.size} audio")
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to reload tracks after sub-add", e)
                                    }
                                }
                            } else {
                                // No external tracks — just read internal tracks
                                try {
                                    val (subs, audio) = view.loadTracks()
                                    stateHolder.setSubtitleTracks(subs)
                                    stateHolder.setAudioTracks(audio)
                                    Log.i(TAG, "Internal tracks loaded: ${subs.size} subs, ${audio.size} audio")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to load tracks", e)
                                }
                            }

                            // ── Resume position ──
                            // Use the CURRENT episode URL (not the original watch request)
                            // so progress is looked up for the right episode after switching.
                            val currentEpUrl = stateHolder.currentEpisodeUrl.value
                            val progress = watchProgressStore.get(
                                watchRequest.anilistId,
                                currentEpUrl,
                            )
                            if (progress != null && progress.positionSeconds > 5) {
                                // If the user watched >90% of the video, start from the
                                // beginning instead of resuming at the end. This prevents
                                // the "video loads at max position, can't seek back" issue.
                                val duration = progress.durationSeconds
                                val resumeThreshold = 0.9 // 90%
                                val shouldStartOver = duration > 0 &&
                                    progress.positionSeconds.toFloat() / duration.toFloat() >= resumeThreshold

                                if (shouldStartOver) {
                                    Log.i(TAG, "Watched >90% (pos=${progress.positionSeconds}s, dur=${duration}s) — starting from beginning")
                                    try {
                                        MPVLib.setPropertyInt("time-pos", 0)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to reset position to 0", e)
                                    }
                                } else {
                                    Log.i(TAG, "Resuming at ${progress.positionSeconds}s / ${duration}s")
                                    try {
                                        MPVLib.setPropertyInt("time-pos", progress.positionSeconds)
                                        stateHolder.setShowStartOverOverlay(true)
                                        scope.launch {
                                            delay(10000)
                                            stateHolder.setShowStartOverOverlay(false)
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to seek to saved position", e)
                                    }
                                }
                            } else {
                                // No saved progress or <5s — start from beginning.
                                // CRITICAL: Reset position to 0 on every new FILE_LOADED
                                // to prevent the "position stuck at max" issue when
                                // switching episodes. MPV sometimes inherits the previous
                                // file's position if keep-open=true.
                                try {
                                    MPVLib.setPropertyInt("time-pos", 0)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to reset position to 0", e)
                                }
                            }
                        }
                        MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
                            Log.i(TAG, "MPV_EVENT_END_FILE")
                        }
                    }
                }

                override fun onEventProperty(property: String) {}

                override fun onEventProperty(property: String, value: Long) {
                    when (property) {
                        "time-pos" -> stateHolder.setPosition(value.toInt())
                        "duration" -> stateHolder.setDuration(value.toInt())
                        "demuxer-cache-time" -> stateHolder.setBufferAheadTime(value.toInt())
                    }
                }

                override fun onEventProperty(property: String, value: Boolean) {
                    when (property) {
                        "pause" -> stateHolder.setPlaying(!value)
                        "paused-for-cache" -> stateHolder.setBuffering(value)
                        "seeking" -> stateHolder.setBuffering(value)
                    }
                }

                override fun onEventProperty(property: String, value: String) {
                    when (property) {
                        "sid" -> stateHolder.setCurrentSubtitleId(value.toIntOrNull() ?: -1)
                        "aid" -> stateHolder.setCurrentAudioId(value.toIntOrNull() ?: -1)
                    }
                }

                override fun onEventProperty(property: String, value: Double) {}

                override fun onFileEnded(errorMessage: String?) {
                    Log.w(TAG, "File ended: $errorMessage")
                    // MPV sends END_FILE for both normal end-of-file AND errors.
                    // If there's an error message, show the error overlay so the
                    // user knows the video failed (instead of a frozen player).
                    // Only show the error if we're not already switching episodes
                    // (switching triggers a deliberate END_FILE on the old file).
                    if (errorMessage != null && !stateHolder.isSwitchingEpisode.value) {
                        stateHolder.setErrorMessage(errorMessage)
                        stateHolder.setSwitchingEpisode(false)
                        stateHolder.setLoadingState(PlayerLoadingState.ERROR)
                    }
                }
            })
            observer = obs

            try {
                PlayerInitializer.initMpvView(
                    view = view,
                    context = context,
                    observer = obs,
                    videoHeaders = watchRequest.videoHeaders ?: "",
                )
                mpvInitialized = true
                stateHolder.setLoadingState(PlayerLoadingState.LOADING)

                // Load the initial video
                PlayerInitializer.loadVideo(view, watchRequest.videoUrl, context)
            } catch (e: Exception) {
                Log.e(TAG, "MPV initialization failed", e)
                stateHolder.setLoadingState(PlayerLoadingState.ERROR)
                stateHolder.setErrorMessage("Failed to initialize player: ${e.message}")
            }
        }
    } }

    // Cleanup on dispose — save progress + destroy MPV
    DisposableEffect(Unit) {
        onDispose {
            try {
                val pos = stateHolder.position.value
                val dur = stateHolder.duration.value
                val currentEpUrl = stateHolder.currentEpisodeUrl.value
                val currentEpNum = stateHolder.currentEpisodeNumber.value
                if (dur > 0 && pos > 0 && currentEpUrl.isNotEmpty()) {
                    watchProgressStore.save(
                        anilistId = watchRequest.anilistId,
                        episodeUrl = currentEpUrl,
                        positionSeconds = pos,
                        durationSeconds = dur,
                        title = stateHolder.currentVideoTitle.value,
                        coverUrl = watchRequest.coverUrl,
                        animeTitle = watchRequest.animeTitle,
                        episodeNumber = currentEpNum,
                    )
                    Log.i(TAG, "Progress saved on dispose: ${pos}s / ${dur}s for ep $currentEpNum")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save progress on dispose", e)
            }

            try {
                mpvView?.let { view ->
                    observer?.let { obs ->
                        PlayerInitializer.destroyMpv(view, obs)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MPV destroy failed", e)
            }
        }
    }

    // Periodic progress save (every 10 seconds)
    LaunchedEffect(mpvInitialized) {
        while (mpvInitialized) {
            delay(10000)
            try {
                val pos = stateHolder.position.value
                val dur = stateHolder.duration.value
                val currentEpUrl = stateHolder.currentEpisodeUrl.value
                val currentEpNum = stateHolder.currentEpisodeNumber.value
                if (dur > 0 && pos > 0 && currentEpUrl.isNotEmpty()) {
                    watchProgressStore.save(
                        anilistId = watchRequest.anilistId,
                        episodeUrl = currentEpUrl,
                        positionSeconds = pos,
                        durationSeconds = dur,
                        title = stateHolder.currentVideoTitle.value,
                        coverUrl = watchRequest.coverUrl,
                        animeTitle = watchRequest.animeTitle,
                        episodeNumber = currentEpNum,
                    )
                    Log.d(TAG, "Progress saved: ${pos}s / ${dur}s for ep $currentEpNum")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Periodic progress save failed", e)
            }
        }
    }

    // ── Episode switching ──
    val switchEpisode: (Int) -> Unit = remember(watchRequest, stateHolder) { { index ->
        val episode = watchRequest.episodeList.getOrNull(index)
        if (episode != null && index != stateHolder.currentEpisodeIndex.value) {
            stateHolder.setSwitchingEpisode(true)
            stateHolder.setCurrentEpisodeIndex(index)
            // CRITICAL: Update the current episode URL + number so that:
            // 1. Progress is saved against the CORRECT episode (not the original)
            // 2. Resume position lookup uses the CORRECT episode URL
            stateHolder.setCurrentEpisodeUrl(episode.url)
            stateHolder.setCurrentEpisodeNumber(episode.episode_number)
            // Reset position + duration so the UI doesn't show the old episode's values
            stateHolder.setPosition(0)
            stateHolder.setDuration(0)
            stateHolder.setErrorMessage(null)
            Log.i(TAG, "Switching to episode ${episode.episode_number}: ${episode.name} (url=${episode.url})")

            scope.launch {
                try {
                    val source = watchRequest.source
                    if (source == null) {
                        Log.e(TAG, "Source not available for episode switching")
                        stateHolder.setSwitchingEpisode(false)
                        stateHolder.setErrorMessage("Source not available for episode switching")
                    } else {
                        when (val result = resolverService.resolve(source, episode)) {
                            is ResolverResult.Success -> {
                                val firstVideo = result.servers.firstOrNull()
                                    ?.audioVersions?.firstOrNull()
                                    ?.videos?.firstOrNull()

                                if (firstVideo != null) {
                                    mpvView?.let { view ->
                                        PlayerInitializer.loadVideo(view, firstVideo.url, context)
                                        stateHolder.setCurrentVideoTitle(
                                            firstVideo.videoTitle.ifBlank { episode.name }
                                        )
                                        stateHolder.setCurrentVideoUrl(firstVideo.url)
                                        // Update resolved servers so the quality sheet
                                        // shows the new episode's servers
                                        resolvedServers = result.servers
                                    }
                                } else {
                                    Log.w(TAG, "No videos found for episode ${episode.episode_number}")
                                    stateHolder.setSwitchingEpisode(false)
                                    stateHolder.setErrorMessage("No videos found for this episode")
                                }
                            }
                            is ResolverResult.NoSources -> {
                                Log.w(TAG, "No sources for episode ${episode.episode_number}")
                                stateHolder.setSwitchingEpisode(false)
                                stateHolder.setErrorMessage("No sources available for this episode")
                            }
                            is ResolverResult.Error -> {
                                Log.e(TAG, "Error resolving episode: ${result.message}")
                                stateHolder.setSwitchingEpisode(false)
                                stateHolder.setErrorMessage("Failed to resolve: ${result.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Episode switch failed", e)
                    stateHolder.setSwitchingEpisode(false)
                    stateHolder.setErrorMessage("Episode switch failed: ${e.message}")
                }
            }
        } else if (episode != null && index == stateHolder.currentEpisodeIndex.value) {
            // Tapping the current episode — do nothing (it's already playing)
            Log.d(TAG, "Tapped current episode (index=$index) — ignoring")
        }
    } }

    // ── Cover-color dynamic theming (watch-page.md §7) ──
    val dynamicScheme = watchRequest.coverColor?.takeIf { it != 0 }?.let {
        generateDynamicScheme(it, darkTheme = true, amoled = false)
    }

    // ── Quality switching: use pre-resolved servers from WatchRequest, or resolve on-demand ──
    val onQualityClick: () -> Unit = {
        // resolvedServers is initialized from watchRequest.resolvedServers on launch.
        // If empty, resolve on-demand.
        if (resolvedServers.isEmpty()) {
            Log.i(TAG, "No pre-resolved servers — resolving on-demand for quality sheet")
            scope.launch {
                try {
                    val source = watchRequest.source
                    val episode = watchRequest.episodeList.getOrNull(stateHolder.currentEpisodeIndex.value)
                    if (source != null && episode != null) {
                        when (val result = resolverService.resolve(source, episode)) {
                            is ResolverResult.Success -> {
                                resolvedServers = result.servers
                                Log.i(TAG, "Resolved servers for quality sheet: ${resolvedServers.size} servers")
                            }
                            else -> {
                                Log.w(TAG, "Failed to resolve servers for quality sheet")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Quality resolve failed", e)
                }
            }
        } else {
            Log.i(TAG, "Using cached resolved servers: ${resolvedServers.size} servers")
        }
        showQualitySheet = true
    }

    // ── Quality selection handler ──
    val onQualitySelected: (app.confused.anikuta.feature.videoresolver.ResolverVideo) -> Unit = { video ->
        Log.i(TAG, "Quality selected: ${video.quality} (${video.url})")
        stateHolder.setSwitchingEpisode(true)
        mpvView?.let { view ->
            PlayerInitializer.loadVideo(view, video.url, context)
            stateHolder.setCurrentVideoTitle(video.videoTitle)
            stateHolder.setCurrentVideoUrl(video.url)
            // Update subtitle tracks for the new quality
            // (will be loaded via sub-add on next FILE_LOADED)
        }
    }

    // ── Subtitle track selection handler ──
    val onSubtitleSelected: (Int) -> Unit = { trackId ->
        try {
            if (trackId <= 0) {
                MPVLib.setPropertyString("sid", "no")
                stateHolder.setCurrentSubtitleId(-1)
                Log.i(TAG, "Subtitles disabled")
            } else {
                MPVLib.setPropertyInt("sid", trackId)
                stateHolder.setCurrentSubtitleId(trackId)
                Log.i(TAG, "Subtitle track set to $trackId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set subtitle track", e)
        }
    }

    val screenContent: @Composable () -> Unit = {
        WatchScreenContent(
            watchRequest = watchRequest,
            stateHolder = stateHolder,
            playerPreferences = playerPreferences,
            mpvView = mpvView,
            initMpv = initMpv,
            playerMode = playerMode,
            isSwitching = isSwitching,
            onBack = onBack,
            onSwitchEpisode = switchEpisode,
            onMpvViewCreated = { v -> mpvView = v },
            showQualitySheet = showQualitySheet,
            showSubtitleSheet = showSubtitleSheet,
            resolvedServers = resolvedServers,
            onQualityClick = onQualityClick,
            onQualitySelected = onQualitySelected,
            onSubtitleClick = { showSubtitleSheet = true },
            onSubtitleSelected = onSubtitleSelected,
            onDismissSheet = {
                showQualitySheet = false
                showSubtitleSheet = false
                showSubtitleSettingsSheet = false
            },
            showSubtitleSettingsSheet = showSubtitleSettingsSheet,
            onDismissSettingsSheet = { showSubtitleSettingsSheet = false },
            onOpenSubtitleSettings = {
                showSubtitleSheet = false
                showSubtitleSettingsSheet = true
            },
        )
    }

    if (dynamicScheme != null) {
        MaterialTheme(colorScheme = dynamicScheme, content = screenContent)
    } else {
        screenContent()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WatchScreenContent — renders the layout. The MPV AndroidView is in a SINGLE
// place (PlayerSurface) and is NEVER disposed during mode switches.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WatchScreenContent(
    watchRequest: WatchRequest,
    stateHolder: PlayerStateHolder,
    playerPreferences: PlayerPreferences,
    mpvView: AnikutaMPVView?,
    initMpv: (AnikutaMPVView) -> Unit,
    playerMode: PlayerMode,
    isSwitching: Boolean,
    onBack: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onMpvViewCreated: (AnikutaMPVView) -> Unit,
    showQualitySheet: Boolean,
    showSubtitleSheet: Boolean,
    resolvedServers: List<app.confused.anikuta.feature.videoresolver.ResolverServer>,
    onQualityClick: () -> Unit,
    onQualitySelected: (app.confused.anikuta.feature.videoresolver.ResolverVideo) -> Unit,
    onSubtitleClick: () -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onDismissSheet: () -> Unit,
    showSubtitleSettingsSheet: Boolean,
    onDismissSettingsSheet: () -> Unit,
    onOpenSubtitleSettings: () -> Unit,
) {
    val context = LocalContext.current
    if (playerMode == PlayerMode.FULLSCREEN) {
        // ── Fullscreen mode ──
        // Player fills the entire screen. No top bar, no scrollable content.
        // Edge-to-edge immersive (no statusBarsPadding — player.md §3 hard rule).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // SINGLE AndroidView — always present, never disposed
            PlayerSurface(
                mpvView = mpvView,
                initMpv = initMpv,
                onMpvViewCreated = onMpvViewCreated,
                modifier = Modifier.fillMaxSize(),
            )

            // Overlay: switching indicator or fullscreen controls
            if (isSwitching) {
                EpisodeSwitchingOverlay(
                    episodeThumbnailUrl = null,
                    episodeTitle = watchRequest.videoTitle,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FullscreenControlsOverlay(
                    watchRequest = watchRequest,
                    stateHolder = stateHolder,
                    playerPreferences = playerPreferences,
                    onBack = onBack,
                    onQualityClick = onQualityClick,
                    onSubtitleClick = onSubtitleClick,
                )
            }
        }
    } else {
        // ── Minimized mode (YouTube-style watch page) ──
        // Top bar → Player (16:9, rounded, padded) → Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Top navigation bar — floating pill, ABOVE the player.
            // Shows the app name "ANIKUTA" (not the episode title) per user request.
            WatchTopBar(
                title = "ANIKUTA",
                onBack = onBack,
            )

            // Player area — 16:9, rounded corners, horizontal padding,
            // small top gap below the top bar. Always present, NEVER disposed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .padding(horizontal = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                ) {
                    PlayerSurface(
                        mpvView = mpvView,
                        initMpv = initMpv,
                        onMpvViewCreated = onMpvViewCreated,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Controls overlay — switching / error / normal
                    val errorMessage = stateHolder.errorMessage.collectAsStateWithLifecycle().value
                    when {
                        isSwitching -> {
                            EpisodeSwitchingOverlay(
                                episodeThumbnailUrl = null,
                                episodeTitle = stateHolder.currentVideoTitle.value,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        errorMessage != null -> {
                            // Visual error state — shows when video fails to load
                            // (MPV "fatal error", "error reading packet", etc.)
                            PlayerErrorOverlay(
                                message = errorMessage,
                                onRetry = {
                                    stateHolder.setErrorMessage(null)
                                    // Re-load the current video URL
                                    mpvView?.let { view ->
                                        val currentUrl = stateHolder.currentVideoUrl.value
                                        if (currentUrl.isNotEmpty()) {
                                            PlayerInitializer.loadVideo(view, currentUrl, context)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        else -> {
                            MinimizedControlsOverlay(
                                stateHolder = stateHolder,
                                playerPreferences = playerPreferences,
                                onMaximize = {
                                    stateHolder.setPlayerMode(PlayerMode.FULLSCREEN)
                                    (context as? Activity)?.requestedOrientation =
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                },
                                onQualityClick = onQualityClick,
                                onSubtitleClick = onSubtitleClick,
                            )
                        }
                    }
                }
            }

            // Scrollable content — description + episode list
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Episode description — dedicated background card section.
                // Uses the CURRENT episode's info (not the original watch request,
                // so it updates when the user switches episodes).
                item(key = "description") {
                    val currentEp = watchRequest.episodeList.getOrNull(stateHolder.currentEpisodeIndex.value)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                    ) {
                        EpisodeDescriptionSection(
                            episodeNumber = currentEp?.episode_number ?: watchRequest.episodeNumber,
                            episodeTitle = currentEp?.name ?: watchRequest.videoTitle,
                            summary = currentEp?.summary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }

                // Episodes header + list — dedicated background card section.
                item(key = "episodes_section") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            // Episodes header — accent-colored with count badge
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Episodes",
                                    fontFamily = RobotoFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text = "${watchRequest.episodeList.size}",
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            // Episode list (plain Column — NO nested LazyColumn)
                            watchRequest.episodeList.forEachIndexed { index, ep ->
                                EpisodeRow(
                                    episodeNumber = ep.episode_number,
                                    episodeTitle = ep.name,
                                    summary = ep.summary,
                                    isCurrent = index == stateHolder.currentEpisodeIndex.value,
                                    isSwitching = isSwitching && index == stateHolder.currentEpisodeIndex.value,
                                    onClick = { onSwitchEpisode(index) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Bottom-up sheets (rendered on top of everything) ──
    if (showQualitySheet) {
        // Extract the current server name from the video title (format: "Server - SUB - 1080p")
        val currentServerName = stateHolder.currentVideoTitle.value.substringBefore(" - ").trim()
        app.confused.anikuta.feature.watch.sheets.QualitySheet(
            servers = resolvedServers,
            currentVideoTitle = stateHolder.currentVideoTitle.value,
            onQualitySelected = onQualitySelected,
            onDismiss = onDismissSheet,
            currentServerName = currentServerName,
        )
    }

    if (showSubtitleSheet) {
        app.confused.anikuta.feature.watch.sheets.SubtitleTracksSheet(
            tracks = stateHolder.subtitleTracks.value,
            currentTrackId = stateHolder.currentSubtitleId.value,
            onTrackSelected = onSubtitleSelected,
            onOpenSettings = onOpenSubtitleSettings,
            onDismiss = onDismissSheet,
        )
    }

    if (showSubtitleSettingsSheet) {
        app.confused.anikuta.core.player.controls.SubtitleSettingsSheet(
            playerPreferences = playerPreferences,
            onApplySettings = {
                try {
                    mpvView?.applySubtitlePreferences()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply subtitle settings", e)
                }
            },
            onDismiss = onDismissSettingsSheet,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerSurface — the SINGLE AndroidView that hosts the MPV view.
//
// CRITICAL FIX: This composable is called in exactly ONE place per mode branch.
// When switching modes, the old branch disposes its PlayerSurface (detaching
// the view from its AndroidViewHolder), and the new branch creates a new
// PlayerSurface. The factory includes a safety check:
//   (view.parent as? ViewGroup)?.removeView(view)
// This ensures that if the disposal hasn't fully completed before the new
// AndroidView tries to attach the view, we manually detach it first —
// preventing the "child already has a parent" IllegalStateException.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerSurface(
    mpvView: AnikutaMPVView?,
    initMpv: (AnikutaMPVView) -> Unit,
    onMpvViewCreated: (AnikutaMPVView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            // Reuse the cached view, or inflate a new one on first creation
            val view = mpvView ?: (
                LayoutInflater.from(ctx)
                    .inflate(app.confused.anikuta.core.player.R.layout.mpv_view, null) as AnikutaMPVView
            ).also { v ->
                onMpvViewCreated(v)
                initMpv(v)
            }

            // CRITICAL: Remove from any previous parent before re-adding.
            // When switching between minimized and fullscreen mode, the old
            // AndroidViewHolder may not have removed the view yet when the new
            // one tries to add it. This prevents the crash:
            //   "The specified child already has a parent"
            (view.parent as? ViewGroup)?.removeView(view)

            view
        },
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls overlays — thin wrappers that wire MPV callbacks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MinimizedControlsOverlay(
    stateHolder: PlayerStateHolder,
    playerPreferences: PlayerPreferences,
    onMaximize: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
) {
    val context = LocalContext.current
    MinimizedControls(
        stateHolder = stateHolder,
        playerPreferences = playerPreferences,
        onTogglePlay = {
            try {
                val paused = MPVLib.getPropertyBoolean("pause") ?: false
                MPVLib.setPropertyBoolean("pause", !paused)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle play", e)
            }
        },
        onSeekTo = { pos ->
            try { MPVLib.setPropertyInt("time-pos", pos) } catch (e: Exception) { Log.e(TAG, "Seek failed", e) }
        },
        onSeekRelative = { delta ->
            try { MPVLib.command(arrayOf("seek", delta.toString(), "relative")) } catch (e: Exception) { Log.e(TAG, "Seek relative failed", e) }
        },
        onMaximize = onMaximize,
        onSubtitleClick = onSubtitleClick,
        onQualityClick = onQualityClick,
    )
}

@Composable
private fun FullscreenControlsOverlay(
    watchRequest: WatchRequest,
    stateHolder: PlayerStateHolder,
    playerPreferences: PlayerPreferences,
    onBack: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
) {
    val context = LocalContext.current
    app.confused.anikuta.core.player.controls.FullscreenControls(
        stateHolder = stateHolder,
        playerPreferences = playerPreferences,
        onBack = {
            stateHolder.setPlayerMode(PlayerMode.MINIMIZED)
            (context as? Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        },
        onTogglePlay = {
            try {
                val paused = MPVLib.getPropertyBoolean("pause") ?: false
                MPVLib.setPropertyBoolean("pause", !paused)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle play", e)
            }
        },
        onSeekTo = { pos ->
            try { MPVLib.setPropertyInt("time-pos", pos) } catch (e: Exception) { Log.e(TAG, "Seek failed", e) }
        },
        onSeekRelative = { delta ->
            try { MPVLib.command(arrayOf("seek", delta.toString(), "relative")) } catch (e: Exception) { Log.e(TAG, "Seek relative failed", e) }
        },
        onMinimize = {
            stateHolder.setPlayerMode(PlayerMode.MINIMIZED)
            (context as? Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        },
        onLockToggle = { stateHolder.setControlsLocked(!stateHolder.controlsLocked.value) },
        onSubtitleClick = onSubtitleClick,
        onAudioClick = { /* TODO: open audio sheet */ },
        onQualityClick = onQualityClick,
        onSpeedClick = { /* TODO: open speed sheet */ },
        onServerClick = { /* TODO: open server sheet */ },
        onMoreClick = { /* TODO: open more sheet */ },
        onSkipForward = {
            try { MPVLib.command(arrayOf("seek", playerPreferences.skipButtonDuration().get().toString(), "relative")) } catch (e: Exception) { Log.e(TAG, "Skip failed", e) }
        },
        onRotateClick = {
            (context as? Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        },
        onPiPClick = {
            try {
                (context as? Activity)?.enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().build()
                )
            } catch (e: Exception) { Log.w(TAG, "PiP not available", e) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The floating pill-shaped top navigation bar.
 *
 * Per design language: floating (not edge-to-edge), pill-shaped (rounded
 * corners), with proper spacing on all sides (top/bottom/left/right).
 * Contains: back button (circular, secondaryContainer) + anime title
 * (primary, ExtraBold) + settings button (circular, secondaryContainer).
 *
 * Ported from the old project's PlayerScreen.kt floating top bar, adapted
 * to the new project's design language (#B1F256, RobotoFamily ExtraBold).
 */
@Composable
private fun WatchTopBar(title: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Back button — circular, secondaryContainer
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            // Title — centered, ExtraBold, primary color
            Text(
                text = title.ifBlank { "Now Playing" },
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            // Settings button — circular, secondaryContainer (placeholder for now)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { /* TODO: open player settings */ },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Episode description section — shows the current episode's number, title,
 * and synopsis in a beautiful card. Per design language: accent-colored
 * episode number badge, large ExtraBold title, muted summary.
 */
@Composable
private fun EpisodeDescriptionSection(
    episodeNumber: Float,
    episodeTitle: String,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Episode number badge — accent-colored pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "EP ${episodeNumber.toInt()}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Episode title — large, ExtraBold
        Text(
            text = episodeTitle,
            fontFamily = RobotoFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Summary — expandable (3 lines collapsed, full when expanded)
        if (!summary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            Text(
                text = if (expanded) "Show less" else "Show more",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

/**
 * A single episode row in the watch page episode list.
 *
 * Per design language:
 * - Alternating card backgrounds (surfaceContainerLow / surfaceContainerHigh)
 * - Current episode: highlighted with primary border + tonal elevation + "now playing" icon
 * - Episode number badge (primaryContainer pill)
 * - Title (Medium weight, onSurface)
 * - Proper spacing and rounded corners (12dp)
 * - Switching animation: pulsing background on current episode
 */
@Composable
private fun EpisodeRow(
    episodeNumber: Float,
    episodeTitle: String,
    summary: String?,
    isCurrent: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
) {
    // Alternating background colors (zebra stripe)
    val isEven = episodeNumber.toInt() % 2 == 0
    val baseColor = if (isEven) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    }
    val cardColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        baseColor
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = if (isCurrent) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        tonalElevation = if (isCurrent) 3.dp else 0.dp,
        shadowElevation = if (isCurrent) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode number badge — primaryContainer pill
            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = formatEpisodeNumber(episodeNumber),
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            // Episode title + "now playing" indicator
            Column(modifier = Modifier.weight(1f)) {
                if (isCurrent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Now Playing",
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = episodeTitle.ifBlank { "Episode ${formatEpisodeNumber(episodeNumber)}" },
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Switching indicator on current episode
            if (isCurrent && isSwitching) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Formats an episode number: 5.0f → "5", 5.5f → "5.5", -1f → "?". */
private fun formatEpisodeNumber(num: Float): String {
    if (num <= 0f) return "?"
    return if (num == num.toLong().toFloat()) num.toLong().toString() else num.toString()
}

/**
 * Visual error overlay shown when a video fails to load or play.
 * Displays the error message + a retry button. The overlay sits on top
 * of the black player background so the user sees a clear error state
 * instead of a frozen player.
 */
@Composable
private fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Playback Error",
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onRetry() },
            ) {
                Text(
                    text = "Retry",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}
