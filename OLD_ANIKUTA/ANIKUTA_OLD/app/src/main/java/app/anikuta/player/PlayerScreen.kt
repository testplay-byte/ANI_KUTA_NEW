package app.anikuta.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import app.anikuta.R
import app.anikuta.ui.detail.generateDynamicScheme
import app.anikuta.ui.detail.toM3ColorScheme
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import uy.kohesive.injekt.api.get

/**
 * The Compose screen: hosts the MPV surface + controls overlay.
 *
 * Phase 1.8: Now supports two modes:
 *  - MINIMIZED: Video at top (16:9), server/version dropdowns, episode list below
 *  - FULLSCREEN: Video fills screen with overlay controls
 *
 * The MPV AndroidView is ALWAYS present (fills the screen). It is never
 * disposed/recreated during mode transitions — only the overlay layout changes.
 * MPV handles its own aspect ratio (letterboxing), so in MINIMIZED mode the
 * bottom portion of the video is covered by an opaque Surface showing the
 * dropdowns + episodes list.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
internal fun PlayerScreen(
    viewModel: PlayerViewModel,
    observer: PlayerObserver,
    onBack: () -> Unit,
    onViewCreated: (AnikutaMPVView) -> Unit = {},
    videoHeaders: String = "",
    showFirstTimePrompt: Boolean = false,
    shouldDelayVideoLoad: Boolean = false,
    onPromptSelect: (String, Boolean) -> Unit = { _, _ -> },
    onPromptDismiss: () -> Unit = {},
    onModeChange: (PlayerMode) -> Unit = {},
    onEpisodeSwitch: (Int) -> Unit = {},
    // Parts 2+3+4: switching callbacks (route to Activity methods)
    onSwitchServer: (String) -> Unit = {},
    onSwitchAudioVersion: (String) -> Unit = {},
    onSwitchQuality: (eu.kanade.tachiyomi.animesource.model.Video) -> Unit = {},
    // Part 6: PiP + rotate callbacks
    onPiPClick: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    // Part 6: More options callbacks (subtitle delay, audio delay, screenshot, sleep timer)
    onSubtitleDelay: () -> Unit = {},
    onAudioDelay: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    // Callback to set the userDisabledSubtitles flag on the Activity
    onUserDisabledSubtitles: (Boolean) -> Unit = {},
    // Callback to set the userChangedAudioTrack flag on the Activity
    onUserChangedAudioTrack: (Boolean) -> Unit = {},
    // Callback to trigger video re-resolution (for stale localhost URLs)
    onReResolveVideo: () -> Unit = {},
    // FIX (L2): `currentVideoUrl` parameter removed — QualitySheet now reads
    // the current URL from `viewModel.currentVideoUrl` StateFlow directly so
    // the highlight recomposes when the user switches server/audio/quality.
    coverColor: Int = 0,  // ARGB color for dynamic theming (0 = use default theme)
) {
    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    val playerMode by viewModel.playerMode.collectAsState()

    // ---- Current episode info (declared early — needed by scroll state) ----
    val episodeList by viewModel.episodeList.collectAsState()
    val currentIndex by viewModel.currentEpisodeIndex.collectAsState()
    val currentEpisode = episodeList.getOrNull(currentIndex)

    // ---- Download state (Phase: PLAYER-DL-BTN) ----
    // Collected here so the inline episode rows in the LazyColumn below can
    // render the per-episode download button with live queue state.
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedOnDisk by viewModel.downloadedOnDisk.collectAsState()

    // ---- LazyColumn scroll state ----
    // FIX: Force-scroll to the VERY top when the episode list first loads.
    // The list loads asynchronously from disk cache (lifecycleScope.launch in
    // onCreate), so the scroll must happen AFTER the items are loaded AND
    // after the LazyColumn has laid them out. We use a small delay (100ms)
    // to ensure layout is complete, then force scrollToItem(0, 0).
    //
    // The one-time guard (hasScrolledToTop) ensures this only fires on the
    // INITIAL load — NOT when the user switches episodes later.
    val episodeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasScrolledToTop = remember { mutableStateOf(false) }
    LaunchedEffect(episodeList.isNotEmpty()) {
        if (episodeList.isNotEmpty() && !hasScrolledToTop.value) {
            // Wait for the LazyColumn to finish laying out the items.
            // Without this delay, scrollToItem runs before layout and is a no-op.
            kotlinx.coroutines.delay(100)
            episodeListState.scrollToItem(0, 0)
            hasScrolledToTop.value = true
            Log.d("PlayerActivity", "Episode list force-scrolled to very top (first load)")
        }
    }

    // ---- Sheet state (Phase 3.7) ----
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showServerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    // ---- Player preferences ----
    // FIX (M4): Declared early so `currentSpeed` can read the persisted speed
    // value on first composition. Was previously declared further down.
    val playerPrefs = remember {
        try { uy.kohesive.injekt.Injekt.get<PlayerPreferences>() }
        catch (e: Exception) { null }
    }

    // Available videos for quality sheet — now backed by ViewModel (Part 2).
    // Populated by populateVideoSelectionState() during episode switch + initial load.
    val availableVideos by viewModel.availableVideos.collectAsState()
    val availableServers by viewModel.availableServers.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()
    // FIX (M4): Read the persisted playback speed from PlayerPreferences so the
    // SpeedSheet opens with the correct value (was previously hard-coded to
    // 1.0f). The preference is also written back from SpeedSheet.onSelect below.
    val currentSpeed by remember { mutableFloatStateOf(playerPrefs?.playerSpeed()?.get() ?: 1.0f) }

    val controlsLocked by viewModel.controlsLocked.collectAsState()
    val lockButtonVisible by viewModel.lockButtonVisible.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    // Episode switching state — drives the loading overlay on the video area
    val isSwitchingEpisode by viewModel.isSwitchingEpisode.collectAsState()
    val switchType by viewModel.switchType.collectAsState()

    // Auto-hide controls after inactivity:
    // - Fullscreen: 4 seconds (existing behavior)
    // - Minimized: 5 seconds (new — user requested)
    // The controls fade out smoothly via AnimatedVisibility (fadeIn/fadeOut)
    // in both MinimizedControls and FullscreenControls.
    LaunchedEffect(controlsVisible, playerMode, controlsLocked) {
        if (controlsVisible && !controlsLocked) {
            when (playerMode) {
                PlayerMode.FULLSCREEN -> {
                    kotlinx.coroutines.delay(4000)
                    viewModel.setControlsVisible(false)
                }
                PlayerMode.MINIMIZED -> {
                    kotlinx.coroutines.delay(5000)
                    viewModel.setControlsVisible(false)
                }
            }
        }
    }

    // Auto-hide lock button after 3 seconds
    LaunchedEffect(lockButtonVisible) {
        if (lockButtonVisible) {
            kotlinx.coroutines.delay(3000)
            viewModel.hideLockButton()
        }
    }

    // ---- Dynamic theming (same as detail page) ----
    // If coverColor is provided, generate a color scheme from it and wrap
    // the entire player in MaterialTheme override.
    val defaultScheme = MaterialTheme.colorScheme
    val themedColorScheme = remember(coverColor) {
        if (coverColor != 0) {
            generateDynamicScheme(Color(coverColor)).toM3ColorScheme()
        } else {
            defaultScheme
        }
    }

    // FIX: If the video URL is a localhost proxy URL and there's no first-time
    // prompt, we need to re-resolve it. The AndroidView factory skips localhost
    // URLs, so we trigger loadVideoIfPending() here which handles re-resolution.
    LaunchedEffect(mpvView) {
        if (mpvView != null && !showFirstTimePrompt) {
            val url = viewModel.videoUrl
            if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
                Log.d("PlayerActivity", "LaunchedEffect: triggering re-resolution for localhost URL")
                onReResolveVideo()
            }
        }
    }

    // FIX: Observe showTopBar reactively via stateIn().collectAsState() so the
    // player UI updates immediately when the user toggles the setting in
    // PlayerSettingsScreen. Previously this used `remember { ... .get() }`
    // which only read the value once on first composition and never updated.
    // (playerPrefs is now declared earlier — see "Player preferences" block above.)
    val prefsScope = rememberCoroutineScope()
    val showTopBar by (playerPrefs?.showPlayerTopBar()?.stateIn(prefsScope)
        ?: MutableStateFlow(true)).collectAsState()

    // (episodeList / currentIndex / currentEpisode are declared above, near scroll state)

    MaterialTheme(colorScheme = themedColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    when (playerMode) {
        PlayerMode.MINIMIZED -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // ---- Floating pill-shaped top bar (conditional) ----
                if (showTopBar) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onBack() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                "AniKuta",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { /* Player settings */ },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // ---- Video area (themed background + rounded corners) ----
                // Added top padding (8dp) when top bar is shown so there's a
                // small gap between the top nav bar and the video player.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!showTopBar) Modifier.statusBarsPadding()
                            else Modifier.padding(top = 8.dp),
                        )
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                // FIX: Reuse existing MPV view if already created.
                                // Previously this ALWAYS created a new view and called
                                // view.initialize() — but MPV can only be initialized
                                // once per process. When switching from FULLSCREEN →
                                // MINIMIZED, this would crash with a native SIGABRT.
                                // Now checks mpvView first, same as the FULLSCREEN branch.
                                //
                                // FIX (M6): The `mpvInitialized` flag was removed because it
                                // is no longer needed — the `mpvView` local state above is
                                // the only guard required within a single Composable
                                // lifecycle. Cross-Activity re-creation is handled by the
                                // Activity's own onDestroy cleanup (which tears MPV down).
                                val existingView = mpvView
                                if (existingView != null) {
                                    existingView
                                } else {
                                    val view = android.view.LayoutInflater
                                        .from(ctx)
                                        .inflate(R.layout.mpv_view, null) as AnikutaMPVView
                                    mpvView = view
                                    onViewCreated(view)
                                    // Player redo: centralized MPV init (mirrors aniyomi
                                    // setupPlayerMPV). Handles config files, asset copy to
                                    // config root (subfont.ttf fix), sub-ass margins,
                                    // initialize, observers, http headers, and runtime
                                    // sub-fonts-dir/osd-fonts-dir. See PLAYER_REDO_PLAN.md.
                                    PlayerActivity.initMpvView(view, ctx, observer, videoHeaders, "warn")
                                    if (!shouldDelayVideoLoad) {
                                        // FIX: Don't load localhost proxy URLs directly — they may be
                                        // stale after app restart. loadVideoIfPending() handles re-resolution.
                                        val url = viewModel.videoUrl
                                        if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
                                            Log.d("PlayerActivity", "Video URL is localhost proxy — deferring to loadVideoIfPending for re-resolution")
                                        } else {
                                            Log.d("PlayerActivity", "Loading video (direct URL): ${url.take(80)}...")
                                            val resolvedUrl = app.anikuta.player.resolveUrlForMpv(url, ctx)
                                            // FIX (D1): For offline playback (fd:// or content://), delay loadfile
                                            // until the SurfaceView has a surface. MPV's vo_android_init crashes
                                            // with "assertion WinID != 0 && WinID != -1 failed" if loadfile is
                                            // called before surfaceCreated fires. For HTTP URLs, MPV can buffer
                                            // without a surface, so no delay is needed.
                                            if (resolvedUrl.startsWith("fd://") || resolvedUrl.startsWith("content://")) {
                                                Log.d("PlayerActivity", "Offline playback — delaying loadfile 500ms for surface creation: $resolvedUrl")
                                                view.postDelayed({
                                                    MPVLib.command(arrayOf("loadfile", resolvedUrl, "replace"))
                                                }, 500)
                                            } else {
                                                MPVLib.command(arrayOf("loadfile", resolvedUrl, "replace"))
                                            }
                                        }
                                    } else {
                                        Log.d("PlayerActivity", "Video load delayed (prompt showing)")
                                    }
                                    view
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // ---- Loading overlay ----
                        // "initial" and "episode": full overlay with episode thumbnail
                        // "quality": semi-transparent overlay on top of frozen video frame
                        if (isSwitchingEpisode) {
                            if (switchType == "quality") {
                                // Quality/server/audio switch: freeze last frame,
                                // show semi-transparent loading overlay on top
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Semi-transparent dark scrim over the frozen video frame
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                    )
                                    // Loading spinner
                                    androidx.compose.material3.CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            } else {
                                // Episode switch or initial entry: full overlay with thumbnail
                                app.anikuta.player.controls.EpisodeSwitchingOverlay(
                                    episodeThumbnailUrl = currentEpisode?.preview_url,
                                    episodeTitle = currentEpisode?.let {
                                        app.anikuta.ui.detail.EpisodeTitleParser.getDisplayTitle(
                                            it.name, it.episode_number,
                                        )
                                    },
                                )
                            }
                        } else {
                            // Controls overlay on top of the video (hidden during switching)
                            app.anikuta.player.controls.MinimizedControls(
                                viewModel = viewModel,
                                onTogglePlay = {
                                    mpvView?.let { v ->
                                        val now = v.paused ?: true
                                        v.paused = !now
                                        viewModel.onPauseChanged(!now)
                                    }
                                },
                                onSeekRelative = { delta ->
                                    mpvView?.let { v ->
                                        val cur = v.timePos ?: 0
                                        val target = (cur + delta).coerceAtLeast(0)
                                        v.timePos = target
                                        viewModel.onPositionUpdate(target)
                                    }
                                },
                                onSeekTo = { seconds ->
                                    mpvView?.timePos = seconds
                                    viewModel.onPositionUpdate(seconds)
                                },
                                onMaximize = { onModeChange(PlayerMode.FULLSCREEN) },
                                onQualityClick = { showQualitySheet = true },
                                onSubtitleClick = { showSubtitleSheet = true },
                            )
                        }
                    }
                }

                // ---- Below-video content (scrolls as one unit) ----
                // Episode details (title, description, date) + server dropdowns + episodes list
                // All in a single LazyColumn so they scroll together (YouTube-style).
                //
                // FIX: Reduced top padding from 16dp to 13dp per user request.
                // FIX: Added a fade-out gradient overlay at the top of the
                // LazyColumn so episodes visually fade out BEFORE reaching the
                // video player edge. The gradient goes from the themed
                // background color (opaque) to transparent over ~20dp, creating
                // a smooth "disappear" zone that prevents episodes from touching
                // the video.
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = episodeListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 13.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    // Episode details (episode-number badge + bigger title + date + description)
                    if (currentEpisode != null) {
                        item(key = "episode_details") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                // Episode number badge (prominent)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = "EPISODE ${app.anikuta.ui.detail.EpisodeTitleParser.formatEpisodeNumber(currentEpisode.episode_number)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Episode title (bigger — titleLarge for prominence)
                                Text(
                                    text = app.anikuta.ui.detail.EpisodeTitleParser.getDisplayTitle(
                                        currentEpisode.name, currentEpisode.episode_number,
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                // Episode date
                                if (currentEpisode.date_upload > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatDate(currentEpisode.date_upload),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Episode description
                                val summaryText = currentEpisode.summary
                                if (!summaryText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = summaryText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // Server + version dropdowns
                    item(key = "dropdowns") {
                        app.anikuta.player.controls.ServerVersionDropdowns(
                            viewModel = viewModel,
                            onServerSelected = { server -> onSwitchServer(server) },
                            onAudioVersionSelected = { audio -> onSwitchAudioVersion(audio) },
                        )
                    }

                    // Separator between details section and episodes list
                    item(key = "separator") {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                        )
                    }

                    // Episodes section header
                    item(key = "episodes_header") {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    // Episodes list (items added directly — scrolls with the details)
                    itemsIndexed(episodeList, key = { _, ep -> "ep_${ep.url}" }) { index, episode ->
                        app.anikuta.player.controls.PlayerEpisodeRowInline(
                            episode = episode,
                            index = index,
                            isCurrent = index == currentIndex,
                            isSwitching = viewModel.isSwitchingEpisode.value && index == currentIndex,
                            onClick = { onEpisodeSwitch(index) },
                            prefs = null,  // Uses default PlayerEpisodePreferences from Injekt
                            // Phase: PLAYER-DL-BTN — download state from PlayerViewModel.
                            // onDownloadClick / onDownloadLongClick are no-op stubs for
                            // now (enqueueing needs the anime title + source which live on
                            // PlayerActivity, not the ViewModel — deferred).
                            downloadStatus = downloadStatus,
                            downloadProgress = downloadProgress,
                            downloadedOnDisk = downloadedOnDisk,
                            onDownloadClick = {},
                            onDownloadLongClick = {},
                        )
                    }
                    } // end LazyColumn

                    // Fade-out gradient overlay — episodes fade out before reaching the video.
                    // Thicker (35dp) and darker (fully opaque at top) for a more prominent
                    // "disappear zone" that clearly separates episodes from the video player.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(35.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.background,
                                    0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                    1f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                ),
                            ),
                    )
                } // end Box (LazyColumn wrapper)
            }
        }
        PlayerMode.FULLSCREEN -> {
            // Fullscreen: video fills entire screen, controls overlay on top
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        // If MPV was already initialized (from minimized mode),
                        // we need to re-parent the view, not re-create it.
                        // AndroidView handles this: if the factory already ran,
                        // it reuses the same view instance.
                        //
                        // FIX (M6): The `mpvInitialized` flag was removed — the
                        // `mpvView` local state is the only guard required.
                        // Cross-Activity re-creation is handled by the Activity's
                        // own onDestroy cleanup.
                        val existingView = mpvView
                        if (existingView != null) {
                            existingView
                        } else {
                            val view = android.view.LayoutInflater
                                .from(ctx)
                                .inflate(R.layout.mpv_view, null) as AnikutaMPVView
                            mpvView = view
                            onViewCreated(view)
                            // Player redo: centralized MPV init (mirrors aniyomi
                            // setupPlayerMPV). See PLAYER_REDO_PLAN.md.
                            PlayerActivity.initMpvView(view, ctx, observer, videoHeaders, "warn")
                            if (!shouldDelayVideoLoad) {
                                // FIX (C3): Don't load localhost proxy URLs directly —
                                // they may be stale after app restart.
                                val url = viewModel.videoUrl
                                if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
                                    Log.d("PlayerActivity", "Video URL is localhost proxy — deferring to loadVideoIfPending for re-resolution")
                                } else {
                                    Log.d("PlayerActivity", "Loading video (direct URL): ${url.take(80)}...")
                                    val resolvedUrlFs = app.anikuta.player.resolveUrlForMpv(url, ctx)
                                    // FIX (D1): For offline playback, delay loadfile until surface is created.
                                    if (resolvedUrlFs.startsWith("fd://") || resolvedUrlFs.startsWith("content://")) {
                                        Log.d("PlayerActivity", "Offline playback (fullscreen) — delaying loadfile 500ms for surface creation: $resolvedUrlFs")
                                        view.postDelayed({
                                            MPVLib.command(arrayOf("loadfile", resolvedUrlFs, "replace"))
                                        }, 500)
                                    } else {
                                        MPVLib.command(arrayOf("loadfile", resolvedUrlFs, "replace"))
                                    }
                                }
                            }
                            view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (!controlsLocked) {
                    app.anikuta.player.controls.PlayerGestureHandler(
                        viewModel = viewModel,
                        onSeekRelative = { delta ->
                            mpvView?.let { v ->
                                val cur = v.timePos ?: 0
                                val target = (cur + delta).coerceAtLeast(0)
                                v.timePos = target
                                viewModel.onPositionUpdate(target)
                            }
                        },
                        onSeekTo = { seconds ->
                            mpvView?.timePos = seconds
                            viewModel.onPositionUpdate(seconds)
                        },
                        onToggleControls = { viewModel.toggleControls() },
                    )

                    app.anikuta.player.controls.FullscreenControls(
                        viewModel = viewModel,
                        onBack = onBack,
                        onTogglePlay = {
                            mpvView?.let { v ->
                                val now = v.paused ?: true
                                v.paused = !now
                                viewModel.onPauseChanged(!now)
                            }
                        },
                        onSeekRelative = { delta ->
                            mpvView?.let { v ->
                                val cur = v.timePos ?: 0
                                val target = (cur + delta).coerceAtLeast(0)
                                v.timePos = target
                                viewModel.onPositionUpdate(target)
                            }
                        },
                        onSeekTo = { seconds ->
                            mpvView?.timePos = seconds
                            viewModel.onPositionUpdate(seconds)
                        },
                        onMinimize = { onModeChange(PlayerMode.MINIMIZED) },
                        onLockToggle = { viewModel.lockControls() },
                        onQualityClick = { showQualitySheet = true },
                        onSubtitleClick = { showSubtitleSheet = true },
                        onAudioClick = { showAudioSheet = true },
                        onServerClick = { showServerSheet = true },
                        onSpeedClick = { showSpeedSheet = true },
                        onMoreClick = { showMoreSheet = true },
                        onSkipForward = {
                            mpvView?.let { v ->
                                val cur = v.timePos ?: 0
                                val target = cur + 85
                                v.timePos = target
                                viewModel.onPositionUpdate(target)
                            }
                        },
                        onPiPClick = { onPiPClick() },
                        onRotateClick = { onRotateClick() },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { viewModel.showLockButton() }
                            },
                    )
                }
            }
        }
    }

    // Lock button (standalone overlay — only in fullscreen when locked)
    if (controlsLocked && lockButtonVisible && playerMode == PlayerMode.FULLSCREEN) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .size(44.dp)
                .clickable { viewModel.unlockControls() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Unlock controls",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    // ---- Error overlay only ----
    val loadingState by viewModel.loadingState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    if (loadingState == app.anikuta.player.PlayerLoadingState.ERROR) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠️", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    errorMessage ?: "Video failed to load.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
    }

    // ---- Resume "start over?" overlay ----
    val showStartOver by viewModel.showStartOverOverlay.collectAsState()
    if (showStartOver) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(10_000)
            viewModel.dismissStartOverOverlay()
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .clickable {
                    mpvView?.timePos = 0
                    viewModel.onPositionUpdate(0)
                    viewModel.dismissStartOverOverlay()
                },
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.8f),
        ) {
            Text(
                "Do you want to start over? Click to start over.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    // ---- First-time prompt ----
    if (showFirstTimePrompt) {
        app.anikuta.player.controls.FirstTimePlayerPrompt(
            onSelect = { view, remember -> onPromptSelect(view, remember) },
            onDismiss = onPromptDismiss,
        )
    }

    // ---- Selection sheets ----
    if (showQualitySheet) {
        // Read display mode preference + current server/audio from ViewModel
        val qualityDisplayMode = remember {
            try { uy.kohesive.injekt.Injekt.get<PlayerPreferences>().qualitySheetDisplayMode().get() }
            catch (e: Exception) { "current" }
        }
        val currentServerForQuality by viewModel.currentServer.collectAsState()
        val currentAudioForQuality by viewModel.currentAudioVersion.collectAsState()
        // FIX (L2): Read the current video URL from the ViewModel StateFlow so
        // the "current quality" highlight recomposes when the user switches
        // server/audio/quality while the sheet is open. Previously this came
        // from a stale Activity field read once on first composition.
        val currentVideoUrlForQuality by viewModel.currentVideoUrl.collectAsState()
        val currentVideoTitleForQuality by viewModel.currentVideoTitle.collectAsState()
        app.anikuta.player.controls.sheets.QualitySheet(
            videos = availableVideos,
            currentVideoUrl = currentVideoUrlForQuality,
            currentVideoTitle = currentVideoTitleForQuality,
            currentVideoServer = currentServerForQuality,
            currentAudioVersion = currentAudioForQuality,
            displayMode = qualityDisplayMode,
            onSelect = { video ->
                Log.d("PlayerActivity", "Quality selected: ${video.videoTitle}")
                showQualitySheet = false
                onSwitchQuality(video)
            },
            onDismiss = { showQualitySheet = false },
        )
    }
    if (showSubtitleSheet) {
        mpvView?.let { view ->
            val (subTracks, audioTracks) = view.loadTracks()
            Log.d("PlayerActivity", "Subtitle sheet opened: ${subTracks.size} sub tracks, ${audioTracks.size} audio tracks")
            viewModel.setSubtitleTracks(subTracks)
            viewModel.setAudioTracks(audioTracks)
            viewModel.setCurrentSubtitleId(view.sid)
            viewModel.setCurrentAudioId(view.aid)
        }
        app.anikuta.player.controls.sheets.SubtitleTracksSheet(
            viewModel = viewModel,
            onSelect = { trackId ->
                Log.d("PlayerActivity", "Subtitle track selected: id=$trackId")
                try {
                    // Use the AnikutaMPVView.sid setter which correctly handles
                    // both "off" (-1 → setPropertyString("sid", "no")) and
                    // track selection (positive → setPropertyInt("sid", id)).
                    mpvView?.sid = trackId
                    // Track whether the user manually disabled subtitles
                    onUserDisabledSubtitles(trackId <= 0)
                    if (trackId <= 0) {
                        Log.d("PlayerActivity", "Subtitles turned off (sid=no) — userDisabledSubtitles=true")
                        viewModel.setSubtitleStatus(PlayerViewModel.SubtitleStatus.OFF, "User turned off")
                    } else {
                        Log.d("PlayerActivity", "Subtitle track set: sid=$trackId")
                        viewModel.setSubtitleStatus(PlayerViewModel.SubtitleStatus.ON, "Track $trackId")
                    }
                    viewModel.setCurrentSubtitleId(trackId)
                } catch (e: Exception) {
                    Log.e("PlayerActivity", "Failed to set subtitle track: $trackId", e)
                }
            },
            onApplySettings = {
                // Part 5: Apply subtitle preferences live to MPV
                mpvView?.applySubtitlePreferences()
            },
            onDismiss = { showSubtitleSheet = false },
        )
    }
    if (showAudioSheet) {
        mpvView?.let { view ->
            val (subTracks, audioTracks) = view.loadTracks()
            viewModel.setSubtitleTracks(subTracks)
            viewModel.setAudioTracks(audioTracks)
            viewModel.setCurrentAudioId(view.aid)
        }
        app.anikuta.player.controls.sheets.AudioTracksSheet(
            viewModel = viewModel,
            onSelect = { trackId ->
                mpvView?.aid = trackId
                viewModel.setCurrentAudioId(trackId)
                // Mark that the user manually changed the audio track
                onUserChangedAudioTrack(true)
                Log.d("PlayerActivity", "Audio track manually selected: id=$trackId")
            },
            onDismiss = { showAudioSheet = false },
        )
    }
    if (showServerSheet) {
        app.anikuta.player.controls.sheets.ServerSheet(
            servers = availableServers.ifEmpty { listOf("Default") },
            currentServer = currentServer.ifBlank { "Default" },
            onSelect = { server ->
                showServerSheet = false
                onSwitchServer(server)
            },
            onDismiss = { showServerSheet = false },
        )
    }
    if (showSpeedSheet) {
        app.anikuta.player.controls.sheets.SpeedSheet(
            currentSpeed = currentSpeed,
            onSelect = { speed ->
                try {
                    `is`.xyz.mpv.MPVLib.setPropertyDouble("speed", speed.toDouble())
                    // FIX (M4): Persist the chosen speed so the next player open
                    // (and the SpeedSheet's next open) reflects the user's choice.
                    playerPrefs?.playerSpeed()?.set(speed)
                } catch (e: Exception) {
                    Log.w("PlayerActivity", "Could not set speed", e)
                }
            },
            onDismiss = { showSpeedSheet = false },
        )
    }
    if (showMoreSheet) {
        app.anikuta.player.controls.sheets.MoreOptionsSheet(
            onSubtitleDelay = {
                showMoreSheet = false
                onSubtitleDelay()
            },
            onAudioDelay = {
                showMoreSheet = false
                onAudioDelay()
            },
            onScreenshot = {
                showMoreSheet = false
                onScreenshot()
            },
            onSleepTimer = {
                showMoreSheet = false
                onSleepTimer()
            },
            onDismiss = { showMoreSheet = false },
        )
    }
    } // end Box
    } // end MaterialTheme
}

/** Format epoch millis as a readable date string. */
internal fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(epochMillis))
    } catch (e: Exception) {
        ""
    }
}

/**
 * Subtitle status indicator pill — shows a small overlay on the video when
 * the subtitle pipeline state changes, then auto-fades after 4 seconds.
 *
 * States shown:
 * - DOWNLOADING (blue)  "Downloading subtitles (English)…"
 * - LOADED      (blue)  "Subtitles loaded (English)"
 * - ON          (green) "Subtitles ON"
 * - OFF         (gray)  "Subtitles OFF"
 * - NONE        (amber) "No subtitles for this episode"
 * - ERROR       (red)   "Subtitle error"
 * - IDLE        — not shown
 *
 * The pill is positioned at the bottom-center of the video container, above
 * the controls. It uses [subtitleStatusTick] to re-trigger the fade animation
 * every time the status changes (even if the status value is the same).
 */
@androidx.compose.runtime.Composable
private fun androidx.compose.foundation.layout.BoxScope.SubtitleStatusPill(viewModel: PlayerViewModel) {
    val status by viewModel.subtitleStatus.collectAsState()
    val detail by viewModel.subtitleStatusDetail.collectAsState()
    val tick by viewModel.subtitleStatusTick.collectAsState()

    // Only show the pill for transient / informative states. ON and OFF are
    // the user's explicit choice (or the normal auto-selected state) — they
    // don't need a popup every time a video starts. The pill is reserved for:
    //   DOWNLOADING — "fetching subtitles..." (brief, informative)
    //   NONE        — "no subtitles for this episode" (saves the user opening the sheet)
    //   ERROR       — "subtitle error" (something went wrong)
    // LOADED is skipped too (it immediately transitions to ON, which we skip).
    val shouldShow = status == PlayerViewModel.SubtitleStatus.DOWNLOADING ||
        status == PlayerViewModel.SubtitleStatus.NONE ||
        status == PlayerViewModel.SubtitleStatus.ERROR
    if (!shouldShow) return

    // Auto-fade: visible for 4s after each tick, then alpha → 0.
    var alpha by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    LaunchedEffect(tick) {
        alpha = 1f
        kotlinx.coroutines.delay(4000)
        alpha = 0f
    }
    if (alpha <= 0f) return

    val (icon, color, label) = when (status) {
        PlayerViewModel.SubtitleStatus.DOWNLOADING -> Triple("⬇", Color(0xFF42A5F5), "Downloading subtitles" + if (detail.isNotBlank()) " ($detail)" else "" + "…")
        PlayerViewModel.SubtitleStatus.NONE -> Triple("!", Color(0xFFFFA726), "No subtitles for this episode")
        PlayerViewModel.SubtitleStatus.ERROR -> Triple("✕", Color(0xFFEF5350), "Subtitle error" + if (detail.isNotBlank()) ": $detail" else "")
        // ON / OFF / LOADED / IDLE are filtered out above; these are unreachable
        // but required for exhaustiveness.
        else -> return
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.75f),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 56.dp)
            .alpha(alpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = icon,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
