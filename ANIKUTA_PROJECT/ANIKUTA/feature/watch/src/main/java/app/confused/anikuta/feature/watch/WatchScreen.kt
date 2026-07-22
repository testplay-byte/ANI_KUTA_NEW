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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import eu.kanade.tachiyomi.animesource.model.SEpisode
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
    val position by stateHolder.position.collectAsStateWithLifecycle()
    val duration by stateHolder.duration.collectAsStateWithLifecycle()
    val isPlaying by stateHolder.isPlaying.collectAsStateWithLifecycle()
    val buffering by stateHolder.buffering.collectAsStateWithLifecycle()
    val errorMessage by stateHolder.errorMessage.collectAsStateWithLifecycle()

    // ── "Video finished" state ──
    // When the video reaches the end (position >= duration - 2), the controls
    // are permanently shown — they do NOT auto-hide. The user must tap to
    // restart or switch episodes. This prevents the confusion of controls
    // disappearing when the video is done.
    val isVideoFinished = duration > 0 && position >= duration - 2 && !isPlaying

    // Force controls visible when video is finished (can't be hidden by tapping)
    LaunchedEffect(isVideoFinished) {
        if (isVideoFinished) {
            stateHolder.setControlsVisible(true)
        }
    }

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

    // Auto-hide controls: 5s minimized, 4s fullscreen.
    // CRITICAL: Do NOT auto-hide when the video is finished (position at end).
    // When the video is done, the controls stay permanently visible so the
    // user can restart or switch episodes. Per user request: "If the video is
    // fully complete... the controls of the videos will be permanently shown."
    LaunchedEffect(controlsVisible, playerMode, isSwitching, isVideoFinished) {
        if (controlsVisible && !isSwitching && !isVideoFinished) {
            val delayMs = if (playerMode == PlayerMode.FULLSCREEN) 4000L else 5000L
            delay(delayMs)
            // Re-check isVideoFinished after the delay (it may have changed)
            if (!isVideoFinished) {
                stateHolder.setControlsVisible(false)
            }
        }
    }

    // Switching timeout safety net: if isSwitching stays true for 30 seconds
    // without FILE_LOADED clearing it, force-clear it and show an error.
    // This prevents the loading overlay from being stuck forever if a video
    // fails to load (e.g. server returns an empty stream, HLS parse error).
    LaunchedEffect(isSwitching) {
        if (isSwitching) {
            delay(30000) // 30 seconds
            if (stateHolder.isSwitchingEpisode.value) {
                Log.e(TAG, "Switching timeout — force-clearing after 30s")
                stateHolder.setSwitchingEpisode(false)
                stateHolder.setErrorMessage("Video failed to load (timeout). Try a different server or quality.")
                stateHolder.setLoadingState(PlayerLoadingState.ERROR)
            }
        }
    }

    // ── Fatal-error watchdog ──
    // Detects when MPV loads a file (FILE_LOADED fires) but the video can't
    // actually play — e.g. the HLS stream returns "error reading packet:
    // Invalid argument" + "treating it as fatal error". MPV doesn't send an
    // END_FILE event for this type of demuxer error, so the player just sits
    // frozen.
    //
    // Two failure modes:
    // 1. Position stays at 0 (stream never started) — position==0 && !playing
    // 2. Position jumped to end (keep-open=true after fatal demuxer error) —
    //    position >= duration-2 && !playing && duration > 0
    //
    // Watchdog: if either condition persists for 15 seconds after load, show
    // a fatal error so the user knows the server is broken.
    LaunchedEffect(mpvInitialized, isSwitching, position, isPlaying, duration) {
        if (mpvInitialized && !isSwitching && !isPlaying && errorMessage == null && duration > 0) {
            // Check both failure modes
            val stuckAtStart = position == 0
            val stuckAtEnd = position >= duration - 2
            if (stuckAtStart || stuckAtEnd) {
                val mode = if (stuckAtStart) "pos=0" else "pos>=dur-2 (pos=$position, dur=$duration)"
                Log.w(TAG, "Watchdog: video loaded but not playing ($mode) — starting 15s watchdog")
                delay(15000) // 15 seconds
                // Re-check: if still stuck, it's a fatal error
                val stillStuck = stateHolder.position.value == 0 ||
                    stateHolder.position.value >= stateHolder.duration.value - 2
                if (stillStuck && !stateHolder.isPlaying.value && stateHolder.errorMessage.value == null) {
                    Log.e(TAG, "Watchdog: FATAL — video stuck for 15s after load. Server is not responding.")
                    stateHolder.setErrorMessage("This server is not responding. The video stream may be broken. Try a different server or quality.")
                    stateHolder.setLoadingState(PlayerLoadingState.ERROR)
                }
            }
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
                    Log.w(TAG, "File ended: $errorMessage (switching=${stateHolder.isSwitchingEpisode.value})")
                    // MPV sends END_FILE for both normal end-of-file AND errors.
                    // If there's an error message, show the error overlay so the
                    // user knows the video failed (instead of a frozen player).
                    // Only show the error if we're not already switching episodes
                    // (switching triggers a deliberate END_FILE on the old file).
                    if (errorMessage != null && !stateHolder.isSwitchingEpisode.value) {
                        Log.e(TAG, "Playback error: $errorMessage")
                        stateHolder.setErrorMessage(errorMessage)
                        stateHolder.setSwitchingEpisode(false)
                        stateHolder.setLoadingState(PlayerLoadingState.ERROR)
                    } else if (errorMessage != null && stateHolder.isSwitchingEpisode.value) {
                        // We're switching — the END_FILE is from the OLD file being
                        // replaced. Don't show an error; the new file will load next.
                        Log.d(TAG, "END_FILE during switch — old file ended, new file loading")
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
    // Remembers the current server name, audio version, and quality from the
    // current video title, and tries to match them when resolving the new episode.
    // Only auto-switches to a different server/audio/quality if the preferred
    // one isn't available.
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

            // Parse the current video title to remember the preferred server/audio/quality.
            // Format: "ServerName - SUB - 1080p" (parsed by VideoTitleParser).
            val currentTitle = stateHolder.currentVideoTitle.value
            val preferredServer = currentTitle.substringBefore(" - ").trim().ifBlank { "" }
            val preferredAudio = Regex("""\b(SUB|DUB|HSUB|HARDSUB|SUBBED|DUBBED)\b""", RegexOption.IGNORE_CASE)
                .find(currentTitle)?.value?.uppercase() ?: ""
            val preferredQuality = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)
                .find(currentTitle)?.groupValues?.get(1)?.toIntOrNull()
            Log.i(TAG, "Preferred: server='$preferredServer', audio='$preferredAudio', quality=$preferredQuality")

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
                                // Try to find the best video matching the preferred server/audio/quality.
                                // Selection priority:
                                // 1. Exact match: same server + same audio + same quality
                                // 2. Same server + same audio (highest quality)
                                // 3. Same server (prefer same audio, highest quality)
                                // 4. Same audio (any server, highest quality)
                                // 5. First available (highest quality)
                                val selectedVideo = selectBestVideo(
                                    servers = result.servers,
                                    preferredServer = preferredServer,
                                    preferredAudio = preferredAudio,
                                    preferredQuality = preferredQuality,
                                )

                                if (selectedVideo != null) {
                                    Log.i(TAG, "Selected video: ${selectedVideo.videoTitle} (${selectedVideo.quality})")
                                    mpvView?.let { view ->
                                        PlayerInitializer.loadVideo(view, selectedVideo.url, context)
                                        stateHolder.setCurrentVideoTitle(
                                            selectedVideo.videoTitle.ifBlank { episode.name }
                                        )
                                        stateHolder.setCurrentVideoUrl(selectedVideo.url)
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
    // CRITICAL: Collect currentEpisodeIndex as state so the UI updates reactively
    // when the user switches episodes. Reading stateHolder.currentEpisodeIndex.value
    // as a snapshot does NOT trigger recomposition — the description + episode list
    // highlight would stay on the old episode until something else triggered a
    // recompose. This was the root cause of "selection not showing properly".
    val currentEpisodeIndex by stateHolder.currentEpisodeIndex.collectAsStateWithLifecycle()
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
                                onOpenQuality = {
                                    // User acknowledged the error — clear it + open the
                                    // quality/server sheet so they can pick a different one.
                                    stateHolder.setErrorMessage(null)
                                    onQualityClick()
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
                    val currentEp = watchRequest.episodeList.getOrNull(currentEpisodeIndex)
                    val epNumInt = currentEp?.episode_number?.toInt() ?: watchRequest.episodeNumber.toInt()
                    val currentMetadata = watchRequest.episodeMetadata[epNumInt]
                    // Audio availability for the current episode
                    val haystack = ((currentEp?.scanlator ?: "") + " " + (currentEp?.name ?: "")).uppercase()
                    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
                    val hasSub = haystack.contains("SUB") && !hasHsub
                    val hasDub = haystack.contains("DUB") && !hasHsub
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
                            episodeTitle = currentMetadata?.title
                                ?: app.confused.anikuta.core.episodemetadata.util.EpisodeTitleParser.parseTitle(
                                    currentEp?.name ?: "", currentEp?.episode_number ?: watchRequest.episodeNumber,
                                )
                                ?: currentEp?.name ?: watchRequest.videoTitle,
                            summary = currentMetadata?.description ?: currentEp?.summary,
                            airDate = currentMetadata?.airDate,
                            hasSub = hasSub,
                            hasDub = hasDub,
                            hasHsub = hasHsub,
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
                            val watchDisplayPrefs = rememberWatchEpisodeDisplayPrefs()
                            watchRequest.episodeList.forEachIndexed { index, ep ->
                                val epNumInt = ep.episode_number.toInt().let { if (it > 0) it else 0 }
                                val epMetadata = watchRequest.episodeMetadata[epNumInt]
                                EpisodeRow(
                                    episode = ep,
                                    metadata = epMetadata,
                                    displayPrefs = watchDisplayPrefs,
                                    isCurrent = index == currentEpisodeIndex,
                                    isSwitching = isSwitching && index == currentEpisodeIndex,
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
    airDate: Long? = null,
    hasSub: Boolean = false,
    hasDub: Boolean = false,
    hasHsub: Boolean = false,
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
        // Release date + audio versions row
        val dateText = if (airDate != null && airDate > 0) formatWatchDate(airDate) else null
        val hasAudio = hasSub || hasDub || hasHsub
        if (dateText != null || hasAudio) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dateText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Text(
                            text = dateText,
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (hasAudio) {
                    WatchAudioPills(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
                }
            }
        }
        // Summary — expandable (3 lines collapsed, full when expanded)
        if (!summary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
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

/** Formats epoch seconds to "MMM d, yyyy". */
private fun formatWatchDate(epochSeconds: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(epochSeconds * 1000L))
    } catch (e: Exception) { "" }
}

/** Audio pills for the watch page — full names (SUB•DUB) with dot separators. */
@Composable
private fun WatchAudioPills(hasSub: Boolean, hasDub: Boolean, hasHsub: Boolean) {
    val parts = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
    if (parts.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            parts.forEachIndexed { idx, label ->
                if (idx > 0) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * A single episode row in the watch page episode list — mirrors the details-page
 * two-section design (thumbnail + title + meta on top; synopsis on bottom) but
 * uses [WatchEpisodeDisplayPrefs] (separate from the details page) + highlights
 * the current episode with a primary border + "Now Playing" indicator.
 *
 * Per user: "add the whole metadata functionality here too and all of those
 * things like showing the thumbnail, title, and release date, and also showing
 * the sub and dub episode availability and also the description, and also
 * properly highlighting the episode."
 */
@Composable
private fun EpisodeRow(
    episode: SEpisode,
    metadata: app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata?,
    displayPrefs: WatchEpisodeDisplayPrefs,
    isCurrent: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
) {
    val epNum = episode.episode_number
    val epNumText = "EP ${formatEpisodeNumber(epNum)}"
    val bareEpNum = formatEpisodeNumber(epNum)

    // Use metadata title if available, otherwise parse the extension title
    val displayTitle = metadata?.title
        ?: app.confused.anikuta.core.episodemetadata.util.EpisodeTitleParser.parseTitle(episode.name, epNum)
        ?: episode.name.ifBlank { "Episode $bareEpNum" }
    val description = metadata?.description ?: episode.summary
    val thumbnailUrl = if (displayPrefs.showThumbnails) {
        metadata?.thumbnailUrl ?: episode.preview_url
    } else null

    // Audio availability — parse BOTH scanlator AND episode name
    val haystack = ((episode.scanlator ?: "") + " " + episode.name).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    val hasAnyAudio = displayPrefs.showAudioPills && (hasSub || hasDub || hasHsub)

    // Date — prefer metadata airDate, fall back to episode.date_upload
    val dateText = if (displayPrefs.showDates) {
        val airDate = metadata?.airDate
        when {
            airDate != null && airDate > 0 -> formatWatchDate(airDate)
            episode.date_upload > 0 -> formatWatchDate(episode.date_upload / 1000)
            else -> null
        }
    } else null

    val (thumbWidth, thumbHeight) = when (displayPrefs.thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }

    // Card color — current episode gets highlighted; no alternating zebra-stripe
    val cardColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // ══ TOP SECTION: thumbnail + title/meta ══
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Thumbnail (left) with EP overlay badge
                if (thumbnailUrl != null) {
                    Box {
                        coil3.compose.AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier
                                .size(width = thumbWidth, height = thumbHeight)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        if (displayPrefs.showEpisodeNumber) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                            ) {
                                Text(
                                    text = epNumText,
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                } else if (displayPrefs.showEpisodeNumber) {
                    // Circle fallback (no thumbnail)
                    Surface(
                        shape = CircleShape,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = bareEpNum,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                }

                // Right column: title (top) + meta (bottom)
                val hasMetaRow = dateText != null || hasAnyAudio
                if (displayPrefs.showTitles || hasMetaRow) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Title (with optional background) + "Now Playing" indicator
                        if (displayPrefs.showTitles) {
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
                                Spacer(Modifier.size(2.dp))
                            }
                            if (displayPrefs.showTitleBackground) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = displayTitle,
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = displayPrefs.titleMaxLines,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                Text(
                                    text = displayTitle,
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = displayPrefs.titleMaxLines,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Date + Audio pills (separate, with spacer)
                        if (hasMetaRow) {
                            Spacer(Modifier.size(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (dateText != null) {
                                    WatchDatePill(text = dateText, showBackground = displayPrefs.showDateBackground)
                                }
                                if (hasAnyAudio) {
                                    WatchAudioPillsWithBg(
                                        hasSub = hasSub,
                                        hasDub = hasDub,
                                        hasHsub = hasHsub,
                                        showBackground = displayPrefs.showAudioBackground,
                                    )
                                }
                            }
                        }
                    }
                } else if (thumbnailUrl != null || displayPrefs.showEpisodeNumber) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Switching indicator on current episode
                if (isCurrent && isSwitching) {
                    Spacer(modifier = Modifier.size(8.dp))
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ══ BOTTOM SECTION: Synopsis (with optional background) ══
            if (displayPrefs.showSummaries && !description.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                if (displayPrefs.showSynopsisBackground) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = description,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = displayPrefs.synopsisMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Text(
                        text = description,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = displayPrefs.synopsisMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Date pill for the watch episode row. */
@Composable
private fun WatchDatePill(text: String, showBackground: Boolean) {
    if (showBackground) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {
            Text(
                text = text,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                maxLines = 1,
                softWrap = false,
            )
        }
    } else {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Audio pills for the watch episode row — with optional background. */
@Composable
private fun WatchAudioPillsWithBg(
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
    showBackground: Boolean,
) {
    val parts = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
    if (parts.isEmpty()) return
    if (showBackground) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                parts.forEachIndexed { idx, label ->
                    if (idx > 0) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            parts.forEachIndexed { idx, label ->
                if (idx > 0) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
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
 * Selects the best video from a list of resolved servers, trying to match
 * the preferred server name, audio version, and quality.
 *
 * Selection priority:
 * 1. Exact match: same server + same audio + same quality
 * 2. Same server + same audio (highest quality available)
 * 3. Same server (prefer same audio, highest quality)
 * 4. Same audio (any server, highest quality)
 * 5. First available (highest quality from first server)
 *
 * This ensures the user's previous selection is remembered when switching
 * episodes — they don't get jumped to a different server/quality unexpectedly.
 *
 * @param servers the resolved servers for the new episode
 * @param preferredServer the server name from the previously-playing video
 * @param preferredAudio the audio version (SUB/DUB/HSUB) from the previous video
 * @param preferredQuality the quality (e.g. 1080) from the previous video
 * @return the best matching [ResolverVideo], or null if no videos available
 */
private fun selectBestVideo(
    servers: List<app.confused.anikuta.feature.videoresolver.ResolverServer>,
    preferredServer: String,
    preferredAudio: String,
    preferredQuality: Int?,
): app.confused.anikuta.feature.videoresolver.ResolverVideo? {
    if (servers.isEmpty()) return null
    Log.d(TAG, "selectBestVideo: ${servers.size} servers, preferred: server='$preferredServer' audio='$preferredAudio' quality=$preferredQuality")

    // Helper: parse quality string ("1080p" → 1080) from a ResolverVideo
    fun parseQuality(q: String): Int? = Regex("""(\d{3,4})""").find(q)?.groupValues?.get(1)?.toIntOrNull()
    // Helper: parse audio from videoTitle
    fun parseAudio(title: String): String = Regex("""\b(SUB|DUB|HSUB|HARDSUB|SUBBED|DUBBED)\b""", RegexOption.IGNORE_CASE)
        .find(title)?.value?.uppercase() ?: ""
    // Helper: parse server from videoTitle
    fun parseServer(title: String): String = title.substringBefore(" - ").trim().ifBlank { "Unknown" }

    // Priority 1: Exact match (same server + same audio + same quality)
    if (preferredServer.isNotBlank()) {
        for (server in servers) {
            if (server.name == preferredServer) {
                for (audio in server.audioVersions) {
                    if (preferredAudio.isBlank() || audio.label == preferredAudio) {
                        for (video in audio.videos) {
                            val vq = parseQuality(video.quality)
                            if (preferredQuality != null && vq == preferredQuality) {
                                Log.d(TAG, "  → exact match: ${video.videoTitle}")
                                return video
                            }
                        }
                    }
                }
            }
        }
    }

    // Priority 2: Same server + same audio (highest quality)
    if (preferredServer.isNotBlank()) {
        val server = servers.firstOrNull { it.name == preferredServer }
        if (server != null) {
            val audio = if (preferredAudio.isNotBlank()) {
                server.audioVersions.firstOrNull { it.label == preferredAudio }
            } else null
            // Try preferred audio first, then any audio
            val audioToUse = audio ?: server.audioVersions.firstOrNull()
            if (audioToUse != null && audioToUse.videos.isNotEmpty()) {
                Log.d(TAG, "  → same server+audio (highest q): ${audioToUse.videos.first().videoTitle}")
                return audioToUse.videos.first() // already sorted highest-first
            }
        }
    }

    // Priority 3: Same server (any audio, highest quality)
    if (preferredServer.isNotBlank()) {
        val server = servers.firstOrNull { it.name == preferredServer }
        if (server != null) {
            // Find highest quality across all audio versions
            val best = server.audioVersions
                .flatMap { it.videos }
                .maxByOrNull { parseQuality(it.quality) ?: 0 }
            if (best != null) {
                Log.d(TAG, "  → same server (any audio): ${best.videoTitle}")
                return best
            }
        }
    }

    // Priority 4: Same audio (any server, highest quality)
    if (preferredAudio.isNotBlank()) {
        val best = servers
            .flatMap { server -> server.audioVersions.filter { it.label == preferredAudio } }
            .flatMap { it.videos }
            .maxByOrNull { parseQuality(it.quality) ?: 0 }
        if (best != null) {
            Log.d(TAG, "  → same audio (any server): ${best.videoTitle}")
            return best
        }
    }

    // Priority 5: First available (highest quality from first server)
    val fallback = servers.firstOrNull()
        ?.audioVersions?.firstOrNull()
        ?.videos?.firstOrNull()
    Log.d(TAG, "  → fallback (first available): ${fallback?.videoTitle}")
    return fallback
}

/**
 * Visual error overlay shown when a video fails to load or play.
 * Displays the error message + a retry button + an "OK" button that opens
 * the quality/server sheet so the user can pick a different server.
 * The overlay sits on top of the black player background so the user sees
 * a clear error state instead of a frozen player.
 */
@Composable
private fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onOpenQuality: () -> Unit = {},
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
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Retry button — re-loads the same video URL
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { onRetry() },
                ) {
                    Text(
                        text = "Retry",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                // OK button — opens the quality/server sheet so the user can
                // pick a different server or quality
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { onOpenQuality() },
                ) {
                    Text(
                        text = "OK",
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
}
