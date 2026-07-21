package app.confused.anikuta.feature.watch

import android.app.Activity
import android.content.res.Configuration
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import app.confused.anikuta.core.player.AnikutaMPVView
import app.confused.anikuta.core.player.PlayerInitializer
import app.confused.anikuta.core.player.PlayerMode
import app.confused.anikuta.core.player.PlayerObserver
import app.confused.anikuta.core.player.PlayerPreferences
import app.confused.anikuta.core.player.PlayerStateHolder
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.core.player.controls.EpisodeSwitchingOverlay
import app.confused.anikuta.core.player.controls.MinimizedControls
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "AnikutaWatchScreen"

/**
 * The YouTube-style watch page (ADR-012).
 *
 * Hosts the MPV mini-player at the top (16:9), episode description below,
 * and the episode list below that. The whole page scrolls as one unit except
 * the player which stays sticky at the top.
 *
 * The MPV AndroidView is NEVER recreated — it's cached in a remember block
 * and reused across MINIMIZED ↔ FULLSCREEN mode switches (ADR-025).
 *
 * @param watchRequest the video URL + anime metadata + episode list
 * @param onBack called when the user presses back from the watch page
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
    val stateHolder = remember { PlayerStateHolder() }

    // Set the companion playerPreferences BEFORE inflating the view
    AnikutaMPVView.playerPreferences = playerPreferences

    // MPV view — cached, NEVER recreated
    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    var observer by remember { mutableStateOf<PlayerObserver?>(null) }
    var mpvInitialized by remember { mutableStateOf(false) }

    val playerMode by stateHolder.playerMode.collectAsStateWithLifecycle()
    val isSwitching by stateHolder.isSwitchingEpisode.collectAsStateWithLifecycle()

    // Nested BackHandler for fullscreen → minimized
    BackHandler(enabled = playerMode == PlayerMode.FULLSCREEN) {
        stateHolder.setPlayerMode(PlayerMode.MINIMIZED)
        // Restore portrait orientation
        (context as? Activity)?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Initialize episode list from the watch request
    LaunchedEffect(watchRequest) {
        stateHolder.setEpisodeList(
            watchRequest.episodeList.map { ep ->
                app.confused.anikuta.core.player.EpisodeListItem(
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

    // Initialize MPV when the view is first created
    val initMpv: (AnikutaMPVView) -> Unit = { view ->
        if (!mpvInitialized) {
            val obs = PlayerObserver(object : PlayerObserver.Callback {
                override fun onEvent(eventId: Int) {
                    when (eventId) {
                        MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                            Log.i(TAG, "MPV_EVENT_FILE_LOADED")
                            stateHolder.setSwitchingEpisode(false)
                            stateHolder.setLoadingState(app.confused.anikuta.core.player.PlayerLoadingState.READY)
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

                override fun onEventProperty(property: String) {
                    // No-value property change
                }

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

                override fun onEventProperty(property: String, value: Double) {
                    // Double-valued property changes (e.g. volume) — not currently tracked
                }

                override fun onFileEnded(errorMessage: String?) {
                    Log.w(TAG, "File ended: $errorMessage")
                    if (errorMessage != null) {
                        stateHolder.setErrorMessage(errorMessage)
                    }
                }
            })
            observer = obs

            PlayerInitializer.initMpvView(
                view = view,
                context = context,
                observer = obs,
                videoHeaders = watchRequest.videoHeaders ?: "",
            )
            mpvInitialized = true

            // Load the initial video
            PlayerInitializer.loadVideo(view, watchRequest.videoUrl, context)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            observer?.let { obs ->
                mpvView?.let { view ->
                    // Save progress before destroying
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
                    }
                    PlayerInitializer.destroyMpv(view, obs)
                }
            }
        }
    }

    // Periodic progress save
    LaunchedEffect(mpvInitialized) {
        while (mpvInitialized) {
            delay(10000)
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
        }
    }

    if (playerMode == PlayerMode.FULLSCREEN) {
        // ── Fullscreen mode ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // The SAME MPV view — re-parented, not recreated
            mpvView?.let { view ->
                AndroidView(
                    factory = { view },
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: run {
                // First creation — inflate the view
                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(app.confused.anikuta.core.player.R.layout.mpv_view, null) as AnikutaMPVView
                        mpvView = view
                        initMpv(view)
                        view
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Fullscreen controls overlay
            if (isSwitching) {
                EpisodeSwitchingOverlay(
                    episodeThumbnailUrl = null,
                    episodeTitle = watchRequest.videoTitle,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
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
        }
    } else {
        // ── Minimized mode (YouTube-style watch page) ──
        val listState = rememberLazyListState()
        val topBarVisible by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 200 }
        }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Sticky player section
                item(key = "player") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                    ) {
                        mpvView?.let { view ->
                            AndroidView(
                                factory = { view },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } ?: run {
                            AndroidView(
                                factory = { ctx ->
                                    val view = LayoutInflater.from(ctx)
                                        .inflate(app.confused.anikuta.core.player.R.layout.mpv_view, null) as AnikutaMPVView
                                    mpvView = view
                                    initMpv(view)
                                    view
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // Controls overlay
                        if (isSwitching) {
                            EpisodeSwitchingOverlay(
                                episodeThumbnailUrl = null,
                                episodeTitle = watchRequest.videoTitle,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
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
                                onMaximize = {
                                    stateHolder.setPlayerMode(PlayerMode.FULLSCREEN)
                                    (context as? Activity)?.requestedOrientation =
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                },
                                onSubtitleClick = { /* TODO: open subtitle sheet */ },
                                onQualityClick = { /* TODO: open quality sheet */ },
                            )
                        }
                    }
                }

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
                                onClick = {
                                    // TODO: switch episode — re-resolve video
                                    Log.i(TAG, "Episode ${ep.episode_number} tapped — switching not yet implemented")
                                },
                            )
                        }
                    }
                }
            }

            // Floating top bar
            AnimatedVisibility(
                visible = topBarVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                WatchTopBar(
                    title = watchRequest.animeTitle,
                    onBack = onBack,
                )
            }
        }
    }
}

// ── Helper composables ──

@Composable
private fun WatchTopBar(title: String, onBack: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
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
                maxLines = 3,
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
