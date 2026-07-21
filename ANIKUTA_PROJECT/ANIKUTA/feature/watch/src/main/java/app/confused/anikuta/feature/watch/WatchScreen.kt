package app.confused.anikuta.feature.watch

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

    // Set the companion playerPreferences BEFORE inflating the view
    AnikutaMPVView.playerPreferences = playerPreferences

    // MPV view — cached, NEVER recreated. Owned by this composable.
    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    var observer by remember { mutableStateOf<PlayerObserver?>(null) }
    var mpvInitialized by remember { mutableStateOf(false) }

    val playerMode by stateHolder.playerMode.collectAsStateWithLifecycle()
    val isSwitching by stateHolder.isSwitchingEpisode.collectAsStateWithLifecycle()
    val controlsVisible by stateHolder.controlsVisible.collectAsStateWithLifecycle()

    // Auto-hide controls: 5s minimized, 4s fullscreen
    LaunchedEffect(controlsVisible, playerMode, isSwitching) {
        if (controlsVisible && !isSwitching) {
            val delayMs = if (playerMode == PlayerMode.FULLSCREEN) 4000L else 5000L
            delay(delayMs)
            stateHolder.setControlsVisible(false)
        }
    }

    // Nested BackHandler for fullscreen → minimized (NOT exit watch page)
    BackHandler(enabled = playerMode == PlayerMode.FULLSCREEN) {
        stateHolder.setPlayerMode(PlayerMode.MINIMIZED)
        (context as? Activity)?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Initialize episode list from the watch request
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
                            // Load tracks
                            try {
                                val (subs, audio) = view.loadTracks()
                                stateHolder.setSubtitleTracks(subs)
                                stateHolder.setAudioTracks(audio)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to load tracks", e)
                            }
                            // Resume position
                            val progress = watchProgressStore.get(
                                watchRequest.anilistId,
                                watchRequest.episodeUrl,
                            )
                            if (progress != null && progress.positionSeconds > 5) {
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
                    if (errorMessage != null) {
                        stateHolder.setErrorMessage(errorMessage)
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
                if (dur > 0 && pos > 0) {
                    watchProgressStore.save(
                        anilistId = watchRequest.anilistId,
                        episodeUrl = watchRequest.episodeUrl,
                        positionSeconds = pos,
                        durationSeconds = dur,
                        title = watchRequest.videoTitle,
                        coverUrl = watchRequest.coverUrl,
                        animeTitle = watchRequest.animeTitle,
                        episodeNumber = watchRequest.episodeNumber,
                    )
                    Log.i(TAG, "Progress saved on dispose: ${pos}s / ${dur}s")
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
                if (dur > 0 && pos > 0) {
                    watchProgressStore.save(
                        anilistId = watchRequest.anilistId,
                        episodeUrl = watchRequest.episodeUrl,
                        positionSeconds = pos,
                        durationSeconds = dur,
                        title = watchRequest.videoTitle,
                        coverUrl = watchRequest.coverUrl,
                        animeTitle = watchRequest.animeTitle,
                        episodeNumber = watchRequest.episodeNumber,
                    )
                    Log.d(TAG, "Progress saved: ${pos}s / ${dur}s")
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
            Log.i(TAG, "Switching to episode ${episode.episode_number}: ${episode.name}")

            scope.launch {
                try {
                    val source = watchRequest.source
                    if (source == null) {
                        Log.e(TAG, "Source not available for episode switching")
                        stateHolder.setSwitchingEpisode(false)
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
                                    }
                                } else {
                                    Log.w(TAG, "No videos found for episode ${episode.episode_number}")
                                    stateHolder.setSwitchingEpisode(false)
                                }
                            }
                            is ResolverResult.NoSources -> {
                                Log.w(TAG, "No sources for episode ${episode.episode_number}")
                                stateHolder.setSwitchingEpisode(false)
                            }
                            is ResolverResult.Error -> {
                                Log.e(TAG, "Error resolving episode: ${result.message}")
                                stateHolder.setSwitchingEpisode(false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Episode switch failed", e)
                    stateHolder.setSwitchingEpisode(false)
                }
            }
        }
    } }

    // ── Cover-color dynamic theming (watch-page.md §7) ──
    val dynamicScheme = watchRequest.coverColor?.takeIf { it != 0 }?.let {
        generateDynamicScheme(it, darkTheme = true, amoled = false)
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
                )
            }
        }
    } else {
        // ── Minimized mode (YouTube-style watch page) ──
        // Top bar → Player (16:9) → Scrollable content (description + episodes)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Top navigation bar — ABOVE the player, always visible
            WatchTopBar(
                title = watchRequest.animeTitle.ifBlank { watchRequest.videoTitle },
                onBack = onBack,
            )

            // Player area — 16:9, always present, NEVER disposed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                PlayerSurface(
                    mpvView = mpvView,
                    initMpv = initMpv,
                    onMpvViewCreated = onMpvViewCreated,
                    modifier = Modifier.fillMaxSize(),
                )

                // Controls overlay
                if (isSwitching) {
                    EpisodeSwitchingOverlay(
                        episodeThumbnailUrl = null,
                        episodeTitle = watchRequest.videoTitle,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MinimizedControlsOverlay(
                        stateHolder = stateHolder,
                        playerPreferences = playerPreferences,
                        onMaximize = {
                            stateHolder.setPlayerMode(PlayerMode.FULLSCREEN)
                            (context as? Activity)?.requestedOrientation =
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                    )
                }
            }

            // Scrollable content — description + episode list
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Episode description
                item(key = "description") {
                    EpisodeDescriptionSection(
                        episodeNumber = watchRequest.episodeNumber,
                        episodeTitle = watchRequest.videoTitle,
                        summary = watchRequest.episodeList.getOrNull(0)?.summary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }

                // Divider
                item(key = "divider") {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                // Episodes header
                item(key = "episodes_header") {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                // Episode list (plain Column inside item — NO nested LazyColumn)
                item(key = "episodes") {
                    Column {
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
        onSubtitleClick = { /* TODO: open subtitle sheet */ },
        onQualityClick = { /* TODO: open quality sheet */ },
    )
}

@Composable
private fun FullscreenControlsOverlay(
    watchRequest: WatchRequest,
    stateHolder: PlayerStateHolder,
    playerPreferences: PlayerPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    app.confused.anikuta.core.player.controls.FullscreenControls(
        stateHolder = stateHolder,
        playerPreferences = playerPreferences,
        onBack = {
            stateHolder.setPlayerMode(PlayerMode.MINIMIZED)
            (context as? Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        },
        onLockToggle = { stateHolder.setControlsLocked(!stateHolder.controlsLocked.value) },
        onSubtitleClick = { /* TODO: open sheet */ },
        onAudioClick = { /* TODO: open sheet */ },
        onQualityClick = { /* TODO: open sheet */ },
        onSpeedClick = { /* TODO: open sheet */ },
        onServerClick = { /* TODO: open sheet */ },
        onMoreClick = { /* TODO: open sheet */ },
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

@Composable
private fun WatchTopBar(title: String, onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title.ifBlank { "Now Playing" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EpisodeDescriptionSection(
    episodeNumber: Float,
    episodeTitle: String,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = "EPISODE ${episodeNumber.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = episodeTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!summary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episodeNumber: Float,
    episodeTitle: String,
    summary: String?,
    isCurrent: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = if (isCurrent) 3.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCurrent) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Now playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EP ${episodeNumber.toInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = episodeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
