package app.anikuta.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.anikuta.R
import app.anikuta.ui.detail.generateDynamicScheme
import app.anikuta.ui.detail.toM3ColorScheme
import app.anikuta.ui.theme.AnikutaTheme
import `is`.xyz.mpv.MPVLib
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.api.get

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * ANI-KUTA PlayerActivity — hosts the MPV video player.
 *
 * Selective copy-paste from aniyomi's `PlayerActivity` (D1): we keep the MPV
 * lifecycle (initialize → addLogObserver → addObserver → loadfile → release),
 * but drop the ~1000 lines of PiP, media session, audio focus, orientation
 * lock, key mapping, screenshot, chapter sync, AniSkip and backup hooks. Those
 * are deferred to later phases. This Activity is intentionally ~280 lines and
 * has zero transitive deps beyond `PlayerPreferences`, `AnikutaMPVView`,
 * `PlayerObserver`, `PlayerViewModel` and `PlayerControls`.
 *
 * Source: REFERENCE/app/.../ui/player/PlayerActivity.kt (reduced).
 *
 * Launch with extras:
 *  - [EXTRA_VIDEO_URL] (required): direct stream URL to play
 *  - [EXTRA_TITLE] (optional): episode/anime title shown in the overlay
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_VIDEO_URL = "app.anikuta.player.VIDEO_URL"
        const val EXTRA_TITLE = "app.anikuta.player.TITLE"
        const val EXTRA_ANILIST_ID = "app.anikuta.player.ANILIST_ID"
        const val EXTRA_EPISODE_URL = "app.anikuta.player.EPISODE_URL"
        const val EXTRA_EPISODE_NUMBER = "app.anikuta.player.EPISODE_NUMBER"
        const val EXTRA_VIDEO_HEADERS = "app.anikuta.player.VIDEO_HEADERS"
        const val EXTRA_COVER_COLOR = "app.anikuta.player.COVER_COLOR"
        const val EXTRA_COVER_URL = "app.anikuta.player.COVER_URL"
        const val EXTRA_ANIME_TITLE = "app.anikuta.player.ANIME_TITLE"
        // ---- Episode switching: current video metadata + source ID ----
        // These allow the player to re-resolve videos for a different episode
        // using the SAME server / audio version / quality as the current video.
        const val EXTRA_SOURCE_ID = "app.anikuta.player.SOURCE_ID"
        const val EXTRA_VIDEO_SERVER = "app.anikuta.player.VIDEO_SERVER"
        const val EXTRA_VIDEO_AUDIO = "app.anikuta.player.VIDEO_AUDIO"
        const val EXTRA_VIDEO_QUALITY = "app.anikuta.player.VIDEO_QUALITY"
        const val MPV_DIR = "mpv"

        /**
         * Copy MPV assets from APK assets to the MPV config directory ROOT.
         *
         * Player redo (PLAYER_REDO_PLAN.md §1, §3.2): this is THE subtitle fix.
         * Both `cacert.pem` and `subfont.ttf` are copied to `mpvDir` (the
         * config-dir root), NOT to a `fonts/` subdirectory. This mirrors
         * aniyomi's `copyAssets()` exactly (REFERENCE PlayerActivity.kt:477).
         *
         * Why subfont.ttf MUST be at the config root: the mpv-lib native
         * `BaseMPVView.initialize()` calls `ass_set_fonts(tracker,
         * "<configDir>/subfont.ttf", "sans-serif", …)` to register the
         * default fallback font + initialise the libass font provider. If
         * `<configDir>/subfont.ttf` does not exist, libass logs:
         *   "Error opening memory font 'subfont.ttf'"
         *   "can't find selected font provider"
         * and NO subtitle text can ever render (video/audio still work).
         * This was ANI-KUTA's subtitle bug for ~15 builds: subfont.ttf was
         * copied to `mpv/fonts/` while the native code looked for it at
         * `mpv/subfont.ttf`.
         *
         * cacert.pem (Mozilla CA bundle) is also at the root for TLS
         * verification of HTTPS subtitle (.vtt) downloads.
         */
        fun copyAssets(context: android.content.Context, mpvDir: java.io.File) {
            val assetManager = context.assets
            val files = arrayOf("cacert.pem", "subfont.ttf")
            for (filename in files) {
                try {
                    val ins = assetManager.open(filename, android.content.res.AssetManager.ACCESS_STREAMING)
                    val outFile = java.io.File(mpvDir, filename)
                    if (!outFile.exists() || outFile.length() != ins.available().toLong()) {
                        java.io.FileOutputStream(outFile).use { out -> ins.copyTo(out) }
                        Log.d("PlayerActivity", "Copied asset: $filename (${outFile.length()} bytes) -> mpv/")
                    }
                    ins.close()
                } catch (e: java.io.IOException) {
                    Log.w("PlayerActivity", "Failed to copy asset: $filename", e)
                }
            }
        }

        /**
         * Centralized MPV initialization — mirrors aniyomi's
         * `setupPlayerMPV()` (REFERENCE PlayerActivity.kt:411) adapted to
         * ANI-KUTA's Compose `AndroidView` architecture (the view is inflated
         * in PlayerScreen, then handed here to be initialised).
         *
         * Sequence (must match aniyomi):
         *  1. Ensure `mpvDir` exists.
         *  2. Write clean `mpv.conf` + `input.conf` (prevents stale
         *     `force-window=yes` from old builds interfering with the
         *     library's own `force-window=no`).
         *  3. `copyAssets()` → `cacert.pem` + `subfont.ttf` to mpvDir ROOT.
         *  4. `sub-ass-force-margins=yes` + `sub-use-margins=yes` (init API).
         *  5. `view.initialize(configDir, cacheDir, logLvl)` — `vo` defaults
         *     to `"gpu"` inside `BaseMPVView.initialize`. Inside it,
         *     `initOptions()` (our override) runs, then `MPVLib.init()`,
         *     then the library sets `force-window=no` + `idle=once`.
         *  6. `addLogObserver` + `addObserver`.
         *  7. HTTP headers for extension proxy URLs (runtime, before loadfile).
         *  8. `sub-fonts-dir` + `osd-fonts-dir` via `setPropertyString`
         *     (RUNTIME — these are NOT init options). Points at `mpv/fonts/`
         *     which is empty by default; the default fallback font
         *     (`subfont.ttf`) already lives at the config root from step 3.
         *
         * See PLAYER_REDO_PLAN.md §3.3 for the full rationale.
         */
        fun initMpvView(
            view: AnikutaMPVView,
            context: android.content.Context,
            observer: PlayerObserver,
            videoHeaders: String,
            logLevel: String = "warn",
        ) {
            val mpvDir = context.filesDir.resolve(MPV_DIR).apply { mkdirs() }

            // 2. Write clean config files (default minimal; user can override
            //    via the advanced settings UI). Overwrites any stale mpv.conf.
            try {
                java.io.File(mpvDir, "mpv.conf").writeText(MpvConfigManager.readMpvConf(context))
                java.io.File(mpvDir, "input.conf").writeText(MpvConfigManager.readInputConf(context))
            } catch (e: Exception) {
                Log.w("PlayerActivity", "Could not write mpv.conf/input.conf", e)
            }

            // 3. Copy assets to mpvDir ROOT (subfont.ttf MUST be at config root).
            copyAssets(context, mpvDir)

            // 4. Subtitle margin options — set BEFORE initialize (matches aniyomi
            //    setupPlayerMPV L425-426). BaseMPVView.initialize calls
            //    MPVLib.create() first, so these apply to the created context.
            MPVLib.setOptionString("sub-ass-force-margins", "yes")
            MPVLib.setOptionString("sub-use-margins", "yes")

            // 5. Initialize MPV. vo defaults to "gpu" inside BaseMPVView.
            view.initialize(mpvDir.absolutePath, context.cacheDir.absolutePath, logLevel)
            Log.d("PlayerActivity", "MPV initialized (configDir=${mpvDir.absolutePath})")

            // 6. Register observers (matches aniyomi).
            MPVLib.addLogObserver(observer)
            MPVLib.addObserver(observer)

            // 7. HTTP headers for extension proxy URLs (runtime, before loadfile).
            val headers = if (videoHeaders.isNotBlank()) videoHeaders
                else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
            MPVLib.setOptionString("http-header-fields", headers)

            // 8. Font directory for USER fonts — runtime setPropertyString
            //    (matches aniyomi copyFontsDirectory L517-518). Points at
            //    mpv/fonts/ (empty by default). The default fallback font
            //    (subfont.ttf) is already at the config root from step 3.
            val fontsDir = java.io.File(mpvDir, "fonts").apply { mkdirs() }
            MPVLib.setPropertyString("sub-fonts-dir", fontsDir.absolutePath)
            MPVLib.setPropertyString("osd-fonts-dir", fontsDir.absolutePath)

            // Diagnostic: confirm the font setup that libass needs.
            val subfont = java.io.File(mpvDir, "subfont.ttf")
            Log.d("PlayerActivity", "SUBTITLE_FONTCHECK: subfont.ttf at config root = ${subfont.exists()} (${subfont.length()} bytes)")
            Log.d("PlayerActivity", "SUBTITLE_FONTCHECK: sub-fonts-dir = ${fontsDir.absolutePath}")
        }

        /**
         * Build a launch Intent for the player.
         * [anilistId] + [episodeUrl] + [episodeNumber] are optional — when
         * provided, watch progress is saved/resumed + AniList sync happens.
         *
         * [sourceId] + [videoServer] + [videoAudio] + [videoQuality] are used
         * for episode switching: when the user taps a different episode in the
         * player's episode list, the player re-resolves the video for that
         * episode using the same source, then auto-selects the video matching
         * the current server / audio / quality.
         */
        fun newIntent(
            context: Context,
            videoUrl: String,
            title: String,
            anilistId: Int = -1,
            episodeUrl: String = "",
            episodeNumber: Float = -1f,
            videoHeaders: String = "",
            coverColor: Int = 0,
            coverUrl: String = "",
            animeTitle: String = "",
            sourceId: Long = -1L,
            videoServer: String = "",
            videoAudio: String = "",
            videoQuality: Int = -1,
        ): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                if (anilistId > 0) putExtra(EXTRA_ANILIST_ID, anilistId)
                if (coverColor != 0) putExtra(EXTRA_COVER_COLOR, coverColor)
                if (coverUrl.isNotBlank()) putExtra(EXTRA_COVER_URL, coverUrl)
                if (animeTitle.isNotBlank()) putExtra(EXTRA_ANIME_TITLE, animeTitle)
                if (episodeUrl.isNotBlank()) putExtra(EXTRA_EPISODE_URL, episodeUrl)
                if (episodeNumber > 0) putExtra(EXTRA_EPISODE_NUMBER, episodeNumber)
                if (videoHeaders.isNotBlank()) putExtra(EXTRA_VIDEO_HEADERS, videoHeaders)
                if (sourceId > 0) putExtra(EXTRA_SOURCE_ID, sourceId)
                if (videoServer.isNotBlank()) putExtra(EXTRA_VIDEO_SERVER, videoServer)
                if (videoAudio.isNotBlank()) putExtra(EXTRA_VIDEO_AUDIO, videoAudio)
                if (videoQuality > 0) putExtra(EXTRA_VIDEO_QUALITY, videoQuality)
            }
    }

    private lateinit var observer: PlayerObserver
    private var viewModel: PlayerViewModel? = null
    private val watchProgress: WatchProgressStore? = try { uy.kohesive.injekt.Injekt.get() } catch (e: Exception) { null }
    private val playbackStateStore: PlaybackStateStore? = try { uy.kohesive.injekt.Injekt.get() } catch (e: Exception) { null }
    private val episodeSeenStore: app.anikuta.data.cache.EpisodeSeenStore? = try { uy.kohesive.injekt.Injekt.get() } catch (e: Exception) { null }
    private var anilistId: Int = -1
    private var episodeUrl: String = ""
    private var resumedPosition: Int? = null
    /** Reference to the MPV view — set from the Composable, used in onDestroy. */
    @Volatile private var mpvViewRef: AnikutaMPVView? = null
    /** Player preferences — used for mode switching + first-time prompt. */
    private var playerPrefs: PlayerPreferences? = null
    /** Whether to show the first-time prompt on launch (read once, passed to Compose as state). */
    private var showFirstTimePrompt: Boolean = false
    /** Whether the video has been loaded yet (delayed if prompt is showing). */
    @Volatile private var videoLoaded: Boolean = false

    // ---- Episode switching: current video metadata + source ----
    // These are read from the Intent and used when the user taps a different
    // episode in the player's episode list. The player re-resolves the video
    // for the new episode using the same source, then auto-selects the video
    // matching the current server / audio / quality.
    private var sourceId: Long = -1L
    private var currentVideoServer: String = ""
    private var currentVideoAudio: String = ""
    private var currentVideoQuality: Int = -1
    /** Current video URL — updated when switching episodes. */
    @Volatile private var currentVideoUrl: String = ""
    /** Current video headers — updated when switching episodes. */
    @Volatile private var currentVideoHeaders: String = ""
    /** Current Video object — holds external subtitle/audio tracks that need
     *  to be loaded into MPV via sub-add/audio-add commands after loadfile. */
    @Volatile private var currentVideo: eu.kanade.tachiyomi.animesource.model.Video? = null
    /** Whether the user has manually turned off subtitles. Prevents auto-select
     *  from re-enabling subtitles after the user explicitly turned them off. */
    @Volatile private var userDisabledSubtitles: Boolean = false
    /** Whether the user has manually changed the audio track. Prevents auto-select
     *  from overriding the user's choice when tracks are reloaded. */
    @Volatile private var userChangedAudioTrack: Boolean = false
    /** Guard to prevent concurrent video resolution (resolveVideosInBackground
     *  vs reResolveAndLoadVideo). Only one resolve runs at a time. */
    @Volatile private var resolveInProgress: Boolean = false
    /** Track which external track URLs have been added to MPV to prevent duplicates. */
    @Volatile private var addedTrackUrls: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    /** Whether this is the first FILE_LOADED for the current video (controls
     *  seekToSavedPosition + start-over overlay — only fires on first load, not
     *  on server/audio/quality switches). */
    @Volatile private var isFirstFileLoad: Boolean = true
    /** Whether MPV has finished loading the current file (FILE_LOADED fired).
     *  sub-add commands are ONLY sent when this is true — sending them before
     *  FILE_LOADED causes MPV to silently drop the track (Bug #1).
     *  Reset to false when a new loadfile is sent. */
    @Volatile private var fileLoaded: Boolean = false
    /** Position to seek to after FILE_LOADED for quality/server switches.
     *  Set by loadSelectedVideo, consumed by handleEvent(FILE_LOADED). */
    @Volatile private var pendingSeekPosition: Int = -1
    /** Resolved video list for the current episode — cached so switchServer/
     *  switchAudioVersion / switchQuality don't need to re-resolve from source. */
    @Volatile private var currentEpisodeVideos: List<eu.kanade.tachiyomi.animesource.model.Video> = emptyList()
    /** Parsed videos for the current episode (cached for fast switching). */
    @Volatile private var currentParsedVideos: List<app.anikuta.ui.detail.ParsedVideo> = emptyList()
    /** Anime cover URL — passed from DetailScreen, saved into WatchProgressStore for History covers. */
    private var coverUrl: String = ""
    /** Anime title — passed from DetailScreen, saved into WatchProgressStore for History display. */
    private var animeTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing"
        anilistId = intent.getIntExtra(EXTRA_ANILIST_ID, -1)
        episodeUrl = intent.getStringExtra(EXTRA_EPISODE_URL) ?: ""
        vmEpisodeNumber = intent.getFloatExtra(EXTRA_EPISODE_NUMBER, -1f).takeIf { it > 0 }
        val coverColor = intent.getIntExtra(EXTRA_COVER_COLOR, 0)
        coverUrl = intent.getStringExtra(EXTRA_COVER_URL) ?: ""
        animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE) ?: ""
        // Episode switching metadata
        sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, -1L)
        currentVideoServer = intent.getStringExtra(EXTRA_VIDEO_SERVER) ?: ""
        currentVideoAudio = intent.getStringExtra(EXTRA_VIDEO_AUDIO) ?: ""
        currentVideoQuality = intent.getIntExtra(EXTRA_VIDEO_QUALITY, -1)
        currentVideoUrl = videoUrl ?: ""
        // FIX (L2): Mirror into the ViewModel StateFlow so the QualitySheet's
        // "current quality" highlight is reactive (was previously stale until
        // the sheet was reopened).
        viewModel?.setCurrentVideoUrl(currentVideoUrl)
        currentVideoHeaders = intent.getStringExtra(EXTRA_VIDEO_HEADERS) ?: ""
        // Create a minimal Video object for the initial load so external tracks
        // (if any were passed) can be loaded. The full Video object with tracks
        // is set when the user switches episodes/servers/quality via loadSelectedVideo.
        currentVideo = eu.kanade.tachiyomi.animesource.model.Video(
            videoUrl = currentVideoUrl,
            videoTitle = title,
        )

        if (videoUrl.isNullOrBlank()) {
            Log.e(TAG, "No video URL provided, finishing")
            finish()
            return
        }

        viewModel = PlayerViewModel(videoUrl, title)

        // Load episode list from disk cache (for the episode list in minimized mode)
        if (anilistId > 0) {
            lifecycleScope.launch {
                try {
                    val cacheStore = uy.kohesive.injekt.Injekt.get<app.anikuta.data.cache.EpisodeCacheStore>()
                    val cached = cacheStore.load(anilistId)
                    if (cached != null) {
                        val (episodes, sourceName) = cached
                        Log.d(TAG, "Loaded ${episodes.size} episodes from cache for player")
                        // Find the current episode index based on the episode URL
                        val currentIndex = episodes.indexOfFirst { it.url == episodeUrl }.coerceAtLeast(0)
                        viewModel?.setEpisodeList(episodes, currentIndex)
                        viewModel?.setAvailableServers(listOf(sourceName), sourceName)
                        // FIX: Immediately set the current server + audio version from
                        // Intent extras so the UI shows them without waiting for the
                        // background video resolution to complete.
                        if (currentVideoServer.isNotBlank()) {
                            viewModel?.setCurrentServer(currentVideoServer)
                        }
                        if (currentVideoAudio.isNotBlank()) {
                            viewModel?.setCurrentAudioVersion(currentVideoAudio)
                        }
                        if (currentVideoQuality > 0) {
                            viewModel?.setCurrentVideoQuality(currentVideoQuality)
                        }
                        Log.d(TAG, "Initial state from Intent: server='$currentVideoServer' audio='$currentVideoAudio' quality=$currentVideoQuality")
                        // Parts 2+3+4: Resolve videos in the background to populate
                        // the server/audio/quality dropdowns + sheets on initial load.
                        // This does NOT reload the video — the one from the Intent is
                        // already playing. It just populates the selection state.
                        resolveVideosInBackground()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load episode cache for player", e)
                }
            }
        }

        // Read video headers from intent — needed by the Composable to set
        // http-header-fields on MPV before loadfile.
        val videoHeaders = intent.getStringExtra(EXTRA_VIDEO_HEADERS) ?: ""

        // Read default player view preference and set initial mode + orientation
        playerPrefs = try { uy.kohesive.injekt.Injekt.get() } catch (e: Exception) { null }
        val defaultView = playerPrefs?.defaultPlayerView()?.get() ?: "ask"
        val promptShown = playerPrefs?.playerPromptShown()?.get() ?: false

        when {
            defaultView == "fullscreen" -> {
                viewModel?.setPlayerMode(PlayerMode.FULLSCREEN)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            defaultView == "minimized" -> {
                viewModel?.setPlayerMode(PlayerMode.MINIMIZED)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            defaultView == "ask" && !promptShown -> {
                // Show prompt — default to minimized until user chooses
                viewModel?.setPlayerMode(PlayerMode.MINIMIZED)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                showFirstTimePrompt = true
            }
            else -> {
                // "ask" but already shown — default to minimized
                viewModel?.setPlayerMode(PlayerMode.MINIMIZED)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // D.5: Request audio focus so other media apps pause when our player starts
        requestAudioFocus()
        // Only hide system bars in fullscreen mode — minimized mode shows the status bar
        if (viewModel?.playerMode?.value == PlayerMode.FULLSCREEN) {
            hideSystemBars()
        }

        observer = PlayerObserver(object : PlayerObserver.Callback {
            override fun onEvent(eventId: Int) = runOnUiThread { handleEvent(eventId) }
            override fun onEventProperty(property: String) = runOnUiThread { handlePropertyString(property) }
            override fun onEventProperty(property: String, value: Long) = runOnUiThread { handlePropertyLong(property, value) }
            override fun onEventProperty(property: String, value: Boolean) = runOnUiThread { handlePropertyBoolean(property, value) }
            override fun onEventProperty(property: String, value: String) = runOnUiThread { handlePropertyString(property, value) }
            override fun onEventProperty(property: String, value: Double) = runOnUiThread { handlePropertyDouble(property, value) }
            override fun onFileEnded(errorMessage: String?) {
                runOnUiThread { if (errorMessage != null) viewModel?.onError(errorMessage) }
            }
        })

        val vm = viewModel!!
        val initialPrompt = showFirstTimePrompt
        val activity = this
        // Set isSwitchingEpisode=true BEFORE setContent so the loading overlay
        // is visible from the very first composition. Without this, there's a
        // gap between the player opening and the LaunchedEffect triggering
        // re-resolution where no loading animation is shown.
        vm.setSwitchingEpisode(true, "initial")
        Log.d(TAG, "Initial isSwitchingEpisode=true (player entry)")
        setContent {
            // Compose state for the prompt — changes trigger recomposition
            var promptVisible by remember { mutableStateOf(initialPrompt) }
            AnikutaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PlayerScreen(
                        viewModel = vm,
                        observer = observer,
                        onBack = { finish() },
                        onViewCreated = { view -> mpvViewRef = view },
                        videoHeaders = videoHeaders,
                        showFirstTimePrompt = promptVisible,
                        shouldDelayVideoLoad = promptVisible,  // Don't load video while prompt is showing
                        onPromptSelect = { view, remember ->
                            activity.handleFirstTimePrompt(view, remember)
                            promptVisible = false
                        },
                        onPromptDismiss = {
                            promptVisible = false
                            activity.loadVideoIfPending()  // Load video if user skips prompt
                        },
                        onModeChange = { mode ->
                            activity.handleModeChange(mode)
                        },
                        onEpisodeSwitch = { index ->
                            activity.switchEpisode(index)
                        },
                        onSwitchServer = { server -> activity.switchServer(server) },
                        onSwitchAudioVersion = { audio -> activity.switchAudioVersion(audio) },
                        onSwitchQuality = { video -> activity.switchQuality(video) },
                        onPiPClick = { activity.enterPiP() },
                        onRotateClick = { activity.toggleOrientation() },
                        onSubtitleDelay = { activity.openSubtitleSettings() },
                        onAudioDelay = { activity.cycleAudioDelay() },
                        onScreenshot = { activity.takeScreenshot() },
                        onSleepTimer = { activity.startSleepTimer() },
                        onUserDisabledSubtitles = { disabled -> activity.userDisabledSubtitles = disabled },
                        onUserChangedAudioTrack = { changed -> activity.userChangedAudioTrack = changed },
                        onReResolveVideo = { activity.loadVideoIfPending() },
                        // FIX (L2): currentVideoUrl is now read from the ViewModel
                        // StateFlow inside PlayerScreen (it stays reactive). We
                        // no longer pass it as a parameter here.
                        coverColor = coverColor,
                    )
                }
            }
        }
    }

    private fun handleEvent(eventId: Int) {
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                Log.d(TAG, "SUBTITLE_DIAG: FILE_LOADED event received")
                // Bug #1 fix: Mark file as loaded BEFORE calling loadExternalTracks.
                // This ensures sub-add commands are accepted by MPV (it silently
                // drops them if no file is loaded).
                fileLoaded = true
                viewModel?.onFileLoaded()
                // Subtitle Fix 4: Safety net — ensure subtitles are visible
                try {
                    MPVLib.setPropertyBoolean("sub-visibility", true)
                    Log.d(TAG, "SUBTITLE_DIAG: Set sub-visibility=true")
                } catch (e: Exception) {
                    Log.w(TAG, "SUBTITLE_DIAG: Could not set sub-visibility", e)
                }
                // Load external subtitle + audio tracks.
                loadExternalTracks()
                // Position preservation for quality/server switches.
                if (pendingSeekPosition > 0) {
                    try {
                        MPVLib.setPropertyInt("time-pos", pendingSeekPosition)
                        viewModel?.onPositionUpdate(pendingSeekPosition)
                        Log.d(TAG, "Seeked to pending position: ${pendingSeekPosition}s")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not seek to pending position", e)
                    }
                    pendingSeekPosition = -1
                }
                // First file load: seek to saved watch progress
                if (isFirstFileLoad) {
                    isFirstFileLoad = false
                    seekToSavedPosition()
                }
                // FIX (P0/P1c): Don't auto-play immediately. Instead, pause and
                // wait for enough buffer (10 seconds ahead) before playing.
                // This prevents stuttering, white screens, and ensures smooth
                // playback start on every switch (server/quality/audio/episode).
                try {
                    MPVLib.setPropertyBoolean("pause", true)
                    viewModel?.onPauseChanged(true)
                    Log.d(TAG, "FILE_LOADED — paused, waiting for buffer before playback")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not pause for buffering", e)
                }
                // Start monitoring buffer — will auto-play when enough is buffered
                startBufferWait()
            }
            MPVLib.mpvEventId.MPV_EVENT_SEEK -> viewModel?.onBufferingChanged(true)
            MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> viewModel?.onBufferingChanged(false)
            MPVLib.mpvEventId.MPV_EVENT_IDLE -> Log.d(TAG, "Player idle")
            MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
                // File ended or failed to load.
                Log.w(TAG, "MPV_EVENT_END_FILE — file ended or failed")
                // FIX (P0): Don't clear isSwitchingEpisode on END_FILE —
                // "replace" mode fires END_FILE for the OLD file before
                // FILE_LOADED for the new one. Clearing here would cause
                // the loading overlay to disappear prematurely and the
                // switching state to be lost. Let FILE_LOADED handle it.
                val vm = viewModel
                if (vm != null && vm.isSwitchingEpisode.value) {
                    // Keep the switching state — the new file is loading
                    Log.d(TAG, "END_FILE: keeping switching state (replace in progress)")
                } else {
                    // Genuine end — episode finished playing. Save progress + mark as seen.
                    Log.d(TAG, "END_FILE: episode finished — saving progress + marking seen")
                    saveProgress()
                }
            }
        }
    }

    /**
     * P1c: Wait for enough buffer before starting playback.
     *
     * After FILE_LOADED, the video is paused. This function polls
     * demuxer-cache-time every 500ms. When 10 seconds are buffered ahead
     * (or after a 15s timeout fallback), it:
     *  1. Clears the loading overlay (isSwitchingEpisode = false)
     *  2. Starts playback (pause = false)
     *  3. Cancels the switch timeout
     *
     * This ensures smooth playback start on every switch without
     * stuttering or white screens.
     */
    private var bufferWaitJob: kotlinx.coroutines.Job? = null
    /** Periodic progress save — runs every 10 seconds during playback */
    private var periodicSaveJob: kotlinx.coroutines.Job? = null
    /** Tracks whether the "marked as watched" toast has been shown for this episode */
    private var hasShownSeenToast = false
    private fun startBufferWait() {
        bufferWaitJob?.cancel()
        bufferWaitJob = lifecycleScope.launch {
            val targetBufferSeconds = 10
            val maxWaitMs = 15_000L
            val startTime = System.currentTimeMillis()
            var elapsed = 0L

            while (elapsed < maxWaitMs) {
                kotlinx.coroutines.delay(500)
                elapsed = System.currentTimeMillis() - startTime

                try {
                    val currentTime = mpvViewRef?.timePos ?: 0
                    // demuxer-cache-time is the timestamp of the end of buffered data
                    val cacheTime = `is`.xyz.mpv.MPVLib.getPropertyInt("demuxer-cache-time") ?: 0
                    val bufferedAhead = cacheTime - currentTime

                    Log.d(TAG, "Buffer wait: ${bufferedAhead}s ahead (need ${targetBufferSeconds}s), elapsed=${elapsed}ms")

                    if (bufferedAhead >= targetBufferSeconds) {
                        // Enough buffer — start playback
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Buffer wait: could not read cache time", e)
                }
            }

            // Start playback and clear loading state
            try {
                MPVLib.setPropertyBoolean("pause", false)
                viewModel?.onPauseChanged(false)
                Log.d(TAG, "Buffer wait complete — starting playback (waited ${elapsed}ms)")

                // Start periodic progress save (every 10 seconds during playback)
                periodicSaveJob?.cancel()
                periodicSaveJob = lifecycleScope.launch {
                    while (true) {
                        kotlinx.coroutines.delay(10000)  // 10 seconds
                        Log.d(TAG, "Periodic save triggered")
                        saveProgress()
                    }
                }
                Log.d(TAG, "Started periodic progress save (every 10s)")
            } catch (e: Exception) {
                Log.w(TAG, "Buffer wait: could not start playback", e)
            }

            // Clear the switching/loading state
            val vm = viewModel
            if (vm != null && vm.isSwitchingEpisode.value) {
                vm.setSwitchingEpisode(false)
                cancelSwitchTimeout()
                Log.d(TAG, "Buffer wait: cleared switching state")
            }
        }
    }

    /** Cancel any ongoing buffer wait. */
    private fun cancelBufferWait() {
        bufferWaitJob?.cancel()
        bufferWaitJob = null
    }

    /**
     * Seek to the last saved position for this episode (task 5.5).
     * Called after MPV_EVENT_FILE_LOADED — the video is ready to seek.
     */    private fun seekToSavedPosition() {
        val pos = watchProgress?.get(anilistId, episodeUrl) ?: return
        if (pos.positionSeconds > 5) {  // don't resume if < 5s in
            resumedPosition = pos.positionSeconds
            try {
                // Use setPropertyInt("time-pos") instead of command("set", "start").
                // "start" is a pre-load option — it only works if set BEFORE
                // loadfile. After FILE_LOADED, we need to seek directly via
                // the time-pos property (or the seek command).
                MPVLib.setPropertyInt("time-pos", pos.positionSeconds)
                viewModel?.onPositionUpdate(pos.positionSeconds)
                // Trigger the "start over?" overlay (Q8 — auto-dismisses in 10s)
                viewModel?.triggerStartOverOverlay()
                Log.d(TAG, "Resumed from ${pos.positionSeconds}s")
            } catch (e: Exception) {
                Log.w(TAG, "Could not seek to saved position", e)
            }
        }

        // Phase C — restore saved audio + subtitle tracks.
        // The PlaybackStateStore remembers which tracks were selected last time.
        // We restore them after the video loads + the seek completes.
        val savedState = playbackStateStore?.get(anilistId, episodeUrl)
        if (savedState != null) {
            try {
                if (savedState.audioTrackId >= 0) {
                    MPVLib.setPropertyInt("aid", savedState.audioTrackId)
                    viewModel?.setCurrentAudioId(savedState.audioTrackId)
                    Log.d(TAG, "Phase C: restored audio track ${savedState.audioTrackId}")
                }
                if (savedState.subtitleTrackId >= 0) {
                    MPVLib.setPropertyInt("sid", savedState.subtitleTrackId)
                    viewModel?.setCurrentSubtitleId(savedState.subtitleTrackId)
                    Log.d(TAG, "Phase C: restored subtitle track ${savedState.subtitleTrackId}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Phase C: could not restore tracks", e)
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

    /**
     * Load external subtitle and audio tracks from the current Video object into MPV.
     *
     * FIX (C1): Deduplicates by URL — if a track URL was already added (e.g. by
     * a concurrent resolveVideosInBackground + reResolveAndLoadVideo), it's skipped.
     * Uses `addedTrackUrls` set to track what's been added.
     *
     * FIX (M2): Passes lang as the 4th argument (correct MPV signature:
     * sub-add <url> [<flags> [<title> [<lang>]]]) — previously lang was passed
     * as the title (3rd arg).
     *
     * FIX (L10): Verified early-return logic is correct. Two guards:
     *  1. `currentVideo == null` → return (no Video object set yet — happens on
     *     the very first FILE_LOADED before resolveVideosInBackground / switch
     *     finishes populating currentVideo).
     *  2. `subtitleTracks.isEmpty() && audioTracks.isEmpty()` → return (Video
     *     object has no external tracks to add — common case for embedded-track
     *     streams). This avoids the try/catch + per-track loop overhead and
     *     makes the early-out visible in logs.
     */
    private fun loadExternalTracks() {
        val video = currentVideo ?: run {
            Log.w(TAG, "SUBTITLE_DIAG: loadExternalTracks called but currentVideo is null")
            return
        }
        // Bug #1 fix: Only add tracks when MPV has finished loading the file.
        // Sending sub-add before FILE_LOADED causes MPV to silently drop the
        // track. The URL then gets added to addedTrackUrls, blocking the
        // correct re-add when FILE_LOADED fires (Bug #5).
        if (!fileLoaded) {
            Log.d(TAG, "SUBTITLE_DIAG: loadExternalTracks skipped — file not loaded yet (waiting for FILE_LOADED)")
            return
        }
        Log.d(TAG, "SUBTITLE_DIAG: loadExternalTracks — video=${video.videoUrl.take(60)}... subs=${video.subtitleTracks.size} audios=${video.audioTracks.size}")
        if (video.subtitleTracks.isEmpty() && video.audioTracks.isEmpty()) {
            Log.d(TAG, "SUBTITLE_DIAG: No external tracks to add")
            updateSubtitleStatus(PlayerViewModel.SubtitleStatus.NONE, "No subtitles for this episode")
            return
        }
        // Log current HTTP headers (needed for subtitle URL download)
        Log.d(TAG, "SUBTITLE_DIAG: Current http-header-fields = ${currentVideoHeaders.take(100)}...")
        // Bug #2 fix: Run sub-add loop on a background thread to avoid blocking
        // the main thread. Each sub-add triggers an HTTPS download inside MPV's
        // native code, which can take 0.3-2s per track. With 6 tracks, that's
        // ~7s of main-thread blocking → 432 dropped frames (the lag the user
        // reported). MPVLib.command() is thread-safe (mpv's command interface
        // is documented as such), so calling it from Dispatchers.IO is safe.
        val headers = currentVideoHeaders
        val subsToAdd = video.subtitleTracks.filter { !addedTrackUrls.contains(it.url) }
        val audiosToAdd = video.audioTracks.filter { !addedTrackUrls.contains(it.url) }
        if (subsToAdd.isEmpty() && audiosToAdd.isEmpty()) {
            Log.d(TAG, "SUBTITLE_DIAG: All external tracks already added")
            return
        }
        Log.d(TAG, "SUBTITLE_DIAG: Adding ${subsToAdd.size} sub + ${audiosToAdd.size} audio tracks (background)")
        lifecycleScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Sub-add loop on background thread
                subsToAdd.forEach { sub ->
                    Log.d(TAG, "SUBTITLE_DIAG: Subtitle URL = ${sub.url}")
                    Log.d(TAG, "SUBTITLE_DIAG: Subtitle lang = ${sub.lang}")
                    try {
                        if (headers.isNotBlank()) {
                            MPVLib.setOptionString("http-header-fields", headers)
                        }
                        Log.d(TAG, "SUBTITLE_DIAG: Sending sub-add: url=${sub.url.take(80)} flags=auto lang=${sub.lang}")
                        MPVLib.command(arrayOf("sub-add", sub.url, "auto", "", sub.lang))
                        addedTrackUrls.add(sub.url)
                        Log.d(TAG, "SUBTITLE_DIAG: sub-add sent successfully for lang=${sub.lang}")
                    } catch (e: Exception) {
                        Log.e(TAG, "SUBTITLE_DIAG: Failed to add subtitle track: ${sub.lang}", e)
                    }
                }
                // Audio-add loop
                audiosToAdd.forEach { audio ->
                    try {
                        MPVLib.command(arrayOf("audio-add", audio.url, "auto", "", audio.lang))
                        addedTrackUrls.add(audio.url)
                        Log.d(TAG, "Added external audio: ${audio.lang}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add audio track: ${audio.lang}", e)
                    }
                }
            }
            // Back on main thread: trigger track-list refresh + auto-select
            Log.d(TAG, "SUBTITLE_DIAG: Processed ${subsToAdd.size} sub + ${audiosToAdd.size} audio track(s)")
            externalTracksJustAdded = true
            mpvViewRef?.let { view ->
                val (subTracks, audioTracks) = view.loadTracks()
                viewModel?.setSubtitleTracks(subTracks)
                viewModel?.setAudioTracks(audioTracks)
                autoSelectSubtitleTrack(view, subTracks)
            }
            // Diagnostic dump after all sub-adds complete
            val viewRef = mpvViewRef
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                viewRef?.let { try { dumpSubtitleState(it) } catch (e: Exception) {} }
            }, 1500)
        }
    }

    /**
     * Save current playback progress to WatchProgressStore (task 5.4).
     * Also saves the playback state (video URL + tracks) to PlaybackStateStore (Phase C).
     */
    private fun saveProgress() {
        val store = watchProgress ?: run { Log.d(TAG, "saveProgress: watchProgress is null — skipping"); return }
        if (anilistId < 0 || episodeUrl.isBlank()) { Log.d(TAG, "saveProgress: no anilistId/episodeUrl — skipping"); return }
        val vm = viewModel ?: run { Log.d(TAG, "saveProgress: viewModel is null — skipping"); return }
        val pos = vm.position.value
        val dur = vm.duration.value

        Log.d(TAG, "═══ saveProgress ═══")
        Log.d(TAG, "  anilistId=$anilistId, episodeUrl=${episodeUrl.take(50)}...")
        Log.d(TAG, "  position=${pos}s, duration=${dur}s")

        // Read the watch threshold (default 85%)
        val threshold = try {
            uy.kohesive.injekt.Injekt.get<app.anikuta.core.preference.PreferenceStore>()
                .getFloat("watch_threshold", 0.85f).get()
        } catch (e: Exception) { 0.85f }
        Log.d(TAG, "  watchThreshold=${threshold * 100}%")

        if (dur > 0 && pos < dur - 2) {  // don't save if basically finished
            store.save(
                anilistId = anilistId,
                episodeUrl = episodeUrl,
                positionSeconds = pos,
                durationSeconds = dur,
                title = vm.title,
                coverUrl = coverUrl.ifBlank { null },
                animeTitle = animeTitle.ifBlank { null },
                episodeNumber = vmEpisodeNumber ?: -1f,
                thumbnailUrl = vm.episodeList.value.getOrNull(vm.currentEpisodeIndex.value)?.preview_url,
            )
            Log.d(TAG, "  ✓ Saved progress: ${pos}s / ${dur}s (${(pos.toFloat() / dur * 100).toInt()}%)")

            // Phase C — save the playback state
            playbackStateStore?.save(
                anilistId = anilistId,
                episodeUrl = episodeUrl,
                videoUrl = currentVideoUrl,
                videoServer = currentVideoServer,
                videoAudio = currentVideoAudio,
                videoQuality = currentVideoQuality,
                videoHeaders = intent.getStringExtra(EXTRA_VIDEO_HEADERS) ?: "",
                audioTrackId = vm.currentAudioId.value,
                subtitleTrackId = vm.currentSubtitleId.value,
                sourceId = sourceId,
            )

            // Mark episode as seen if position >= threshold (default 85%)
            if (dur > 0 && pos >= dur * threshold) {
                episodeSeenStore?.markSeen(anilistId, episodeUrl)
                Log.d(TAG, "  ✓✓ Episode MARKED AS SEEN (pos=${pos}s >= ${(dur * threshold).toInt()}s = ${threshold * 100}% threshold)")
                // Show toast — only ONCE per episode (not on every periodic save)
                if (!hasShownSeenToast) {
                    hasShownSeenToast = true
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Episode marked as watched",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } else {
                Log.d(TAG, "  Not yet at threshold (${pos}s < ${(dur * threshold).toInt()}s) — not marking seen")
            }
        } else if (dur > 0 && pos >= dur - 2) {
            // Episode finished — save final progress (DON'T clear — keep for history)
            // The seen flag is handled by EpisodeSeenStore. WatchProgressStore
            // keeps the entry so the History page can show it.
            store.save(
                anilistId = anilistId,
                episodeUrl = episodeUrl,
                positionSeconds = pos,
                durationSeconds = dur,
                title = vm.title,
                coverUrl = coverUrl.ifBlank { null },
                animeTitle = animeTitle.ifBlank { null },
                episodeNumber = vmEpisodeNumber ?: -1f,
                thumbnailUrl = vm.episodeList.value.getOrNull(vm.currentEpisodeIndex.value)?.preview_url,
            )
            Log.d(TAG, "  ✓✓ Episode FINISHED — saved final progress (kept for history), marked as seen")
            // Mark as seen
            episodeSeenStore?.markSeen(anilistId, episodeUrl)
            // Show toast — only once
            if (!hasShownSeenToast) {
                hasShownSeenToast = true
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PlayerActivity,
                        "Episode marked as watched",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            // Sync to AniList
            syncToAniList()
        } else {
            Log.d(TAG, "  Duration is 0 or invalid — skipping (dur=$dur)")
        }
        Log.d(TAG, "═══ saveProgress END ═══")
    }

    /**
     * Sync watch progress to AniList (task 6.9). Called when an episode is
     * finished. Uses the AniListTracker (if logged in) to update progress.
     *
     * FIX (M8): Switched from `MainScope().launch` to `lifecycleScope.launch`.
     * `MainScope()` creates an unscoped SupervisorJob that lives until manually
     * cancelled — using it from an Activity leaks the coroutine (and any
     * references it captures) across the Activity lifecycle, surviving
     * onDestroy. `lifecycleScope` is tied to the Activity's Lifecycle and is
     * automatically cancelled when the Activity is destroyed.
     */
    private fun syncToAniList() {
        try {
            val tracker: app.anikuta.data.tracker.AniListTracker = uy.kohesive.injekt.Injekt.get()
            if (!tracker.isLoggedIn()) return
            // Extract episode number from the title (best-effort)
            val epNum = vmEpisodeNumber ?: return
            // FIX (M8): Use lifecycleScope (Activity-scoped) instead of MainScope()
            // so the coroutine is cancelled when the Activity is destroyed.
            lifecycleScope.launch {
                tracker.updateProgress(anilistId, epNum.toInt())
            }
            Log.d(TAG, "AniList sync triggered: anime=$anilistId ep=$epNum")
        } catch (e: Exception) {
            Log.w(TAG, "AniList sync failed (not logged in or tracker unavailable)", e)
        }
    }

    /** Episode number passed from the detail page (for AniList sync). */
    private var vmEpisodeNumber: Float? = null

    private fun handlePropertyLong(property: String, value: Long) {
        when (property) {
            "time-pos" -> viewModel?.onPositionUpdate(value.toInt())
            "duration" -> viewModel?.onDurationUpdate(value.toInt())
            "volume" -> viewModel?.onVolumeUpdate(value.toInt())
            // P2b: Buffer-ahead indicator — update ViewModel with cached time
            "demuxer-cache-time" -> viewModel?.onBufferAheadUpdate(value.toInt())
        }
    }

    private fun handlePropertyBoolean(property: String, value: Boolean) {
        when (property) {
            "pause" -> viewModel?.onPauseChanged(value)
            "paused-for-cache", "seeking" -> viewModel?.onBufferingChanged(value)
        }
    }

    private fun handlePropertyString(property: String, value: String) {
        when (property) {
            "track-list" -> {
                mpvViewRef?.let { view ->
                    val (subTracks, audioTracks) = view.loadTracks()
                    Log.d(TAG, "SUBTITLE_DIAG: track-list changed — ${subTracks.size} sub tracks, ${audioTracks.size} audio tracks")
                    subTracks.forEach { t ->
                        Log.d(TAG, "SUBTITLE_DIAG:   sub track: id=${t.id} name='${t.name}' lang='${t.language}'")
                    }
                    viewModel?.setSubtitleTracks(subTracks)
                    viewModel?.setAudioTracks(audioTracks)
                    autoSelectSubtitleTrack(view, subTracks)
                    autoSelectAudioTrack(view, audioTracks)
                    // Log current sid after auto-select
                    val currentSid = view.sid
                    Log.d(TAG, "SUBTITLE_DIAG: After auto-select: sid=$currentSid")
                }
            }
            // Subtitle Fix 3: Handle sid/aid property changes so the UI stays
            // in sync when MPV changes the track internally.
            "sid" -> {
                val sid = value.toIntOrNull() ?: -1
                viewModel?.setCurrentSubtitleId(sid)
                Log.d(TAG, "SUBTITLE_DIAG: sid property changed to: $sid (value='$value')")
            }
            "aid" -> {
                val aid = value.toIntOrNull() ?: -1
                viewModel?.setCurrentAudioId(aid)
                Log.d(TAG, "aid property changed: $aid")
            }
        }
    }

    private fun handlePropertyString(property: String) {
        // Property changed (no value) — track-list is observed as MPV_FORMAT_NONE
        when (property) {
            "track-list" -> {
                mpvViewRef?.let { view ->
                    val (subTracks, audioTracks) = view.loadTracks()
                    viewModel?.setSubtitleTracks(subTracks)
                    viewModel?.setAudioTracks(audioTracks)
                    // Auto-select the first subtitle track if none is selected.
                    autoSelectSubtitleTrack(view, subTracks)
                    // Auto-select the correct audio track based on audio version.
                    autoSelectAudioTrack(view, audioTracks)
                    Log.d(TAG, "Tracks loaded (no-value): ${subTracks.size} sub, ${audioTracks.size} audio")
                }
            }
        }
    }

    /**
     * Auto-select the first available subtitle track if none is selected.
     *
     * Subtitle Fix 2: Previously, if MPV auto-selected an embedded subtitle
     * (sid > 0), external subtitles added via sub-add would never get selected
     * because this function saw sid > 0 and just updated the VM. Now, if
     * external tracks were just added (tracked via `externalTracksJustAdded`),
     * we prefer the external track over the embedded one — external tracks
     * from the extension are usually the "real" subtitles the user wants.
     *
     * Subtitle default-mode preference (player-experiment branch):
     * Reads `playerPrefs.defaultSubtitleMode()`:
     * - "off"  = never auto-select on new episode. User can still turn on via sheet.
     * - "on"   = auto-select the best track on new episode. Default.
     * - "auto" = only auto-select if a track matches `preferredSubtitleLanguage`.
     *
     * CRITICAL: In ALL modes, the user's explicit choice during the current
     * playback session takes precedence. If `userDisabledSubtitles` is true
     * (user tapped "Off" in the subtitle sheet), autoSelectSubtitleTrack does
     * NOTHING — it does not fight the user. The flag is reset to false on
     * every new episode load (loadSelectedVideo / handleEvent FILE_LOADED).
     *
     * This fixes the feedback loop where mode=on kept re-enabling subs after
     * the user tapped Off, making the Off button appear broken.
     */
    @Volatile private var externalTracksJustAdded: Boolean = false

    /** Update the subtitle status indicator shown on the player UI. */
    private fun updateSubtitleStatus(status: PlayerViewModel.SubtitleStatus, detail: String = "") {
        viewModel?.setSubtitleStatus(status, detail)
    }

    /**
     * Dump the full subtitle state from MPV for debugging. Called after sub-add
     * and on track-list changes. Logs:
     *  - track-list/count + each track's type/id/lang/title/selected/codec
     *  - current sid, secondary-sid, sub-visibility, sub-start, sub-delay
     *  - sub-text (the current subtitle text, if any — proves the .vtt has content)
     * This is the key diagnostic: if sub-text is empty after sid=1, the .vtt
     * has no cues for the current timestamp; if sub-visibility is false, subs
     * are hidden; if track-list shows no 'sub' track, sub-add failed.
     */
    private fun dumpSubtitleState(view: AnikutaMPVView) {
        try {
            val count = MPVLib.getPropertyInt("track-list/count") ?: -1
            val sid = view.sid
            val subVis = MPVLib.getPropertyBoolean("sub-visibility") ?: true
            val subStart = try { MPVLib.getPropertyInt("sub-start") ?: -1 } catch (e: Exception) { -1 }
            val subText = try { MPVLib.getPropertyString("sub-text") ?: "" } catch (e: Exception) { "" }
            val subDelay = try { MPVLib.getPropertyString("sub-delay") ?: "0" } catch (e: Exception) { "0" }
            Log.d(TAG, "SUB_DUMP: track-count=$count sid=$sid sub-visibility=$subVis sub-start=$subStart sub-delay=$subDelay sub-text='${subText.take(80)}'")
            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type") ?: "?"
                val id = MPVLib.getPropertyInt("track-list/$i/id") ?: -1
                val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false
                val codec = MPVLib.getPropertyString("track-list/$i/codec") ?: ""
                val external = MPVLib.getPropertyBoolean("track-list/$i/external") ?: false
                Log.d(TAG, "SUB_DUMP:   [$i] type=$type id=$id lang='$lang' title='$title' selected=$selected codec='$codec' external=$external")
            }
        } catch (e: Exception) {
            Log.w(TAG, "SUB_DUMP: failed to dump subtitle state", e)
        }
    }

    private fun autoSelectSubtitleTrack(
        view: AnikutaMPVView,
        subTracks: List<VideoTrack>,
    ) {
        val currentSid = view.sid
        val mode = playerPrefs?.defaultSubtitleMode()?.get() ?: "on"
        val preferredLangs = (playerPrefs?.preferredSubtitleLanguage()?.get() ?: "en,eng")
            .split(",").map { it.trim().lowercase().ifEmpty { null } }.filterNotNull()
        Log.d(TAG, "SUBTITLE_DIAG: autoSelectSubtitleTrack — currentSid=$currentSid tracks=${subTracks.size} mode=$mode userDisabled=$userDisabledSubtitles externalJustAdded=$externalTracksJustAdded preferredLangs=$preferredLangs")

        // USER CHOICE WINS: if the user explicitly turned off subtitles this
        // session, do NOT re-select. This prevents the feedback loop where
        // mode=on fights the user's "Off" tap.
        if (userDisabledSubtitles) {
            Log.d(TAG, "SUBTITLE_DIAG: User disabled subtitles this session — not auto-selecting")
            viewModel?.setCurrentSubtitleId(-1)
            externalTracksJustAdded = false
            return
        }

        val realTracks = subTracks.filter { it.id > 0 }
        if (realTracks.isEmpty()) {
            Log.d(TAG, "SUBTITLE_DIAG: No real subtitle tracks available to select")
            viewModel?.setCurrentSubtitleId(-1)
            return
        }

        // "off" mode — don't auto-select on new episode.
        if (mode == "off") {
            Log.d(TAG, "SUBTITLE_DIAG: mode=off — not auto-selecting")
            viewModel?.setCurrentSubtitleId(-1)
            return
        }

        // Bug #3 fix: Pick the best track: language match first, else first available.
        // When multiple tracks match the preferred language, prefer "Full Subtitles"
        // over "Signs & Songs" (which only translate on-screen text, not dialogue).
        val langMatches = realTracks.filter { track ->
            val t = track.language?.lowercase() ?: ""
            preferredLangs.any { p -> t == p || t.startsWith(p) || p.startsWith(t) }
        }
        // Sort by track quality: "full" > neutral > "signs"/"songs"
        val langMatch = langMatches.sortedBy { track ->
            val name = track.name.lowercase()
            when {
                name.contains("full") -> 0   // prefer full dialogue subtitles
                name.contains("signs") || name.contains("songs") -> 2  // deprioritize signs/songs
                else -> 1  // neutral
            }
        }.firstOrNull()
        val bestTrack = langMatch ?: realTracks.first()
        Log.d(TAG, "SUBTITLE_DIAG: bestTrack id=${bestTrack.id} name='${bestTrack.name}' lang='${bestTrack.language}' (langMatch=${langMatch != null})")

        // "auto" — only select if a language match was found.
        if (mode == "auto" && langMatch == null) {
            Log.d(TAG, "SUBTITLE_DIAG: mode=auto and no language match — not selecting")
            viewModel?.setCurrentSubtitleId(-1)
            return
        }

        // "on" or "auto (matched)" — select / switch to the best track.
        // Bug fix: Removed the `currentSid != bestTrack.id -> true` condition
        // that was overriding the user's manual track selection. Once a track
        // is selected (currentSid > 0), only switch if NEW external tracks
        // were just added (externalTracksJustAdded). Otherwise, respect the
        // user's choice — they may have intentionally selected a non-English
        // track (e.g. Indonesian, Thai).
        val shouldSwitch = when {
            currentSid <= 0 -> true
            externalTracksJustAdded && currentSid != bestTrack.id -> true
            else -> false
        }
        if (shouldSwitch) {
            try {
                view.sid = bestTrack.id
                viewModel?.setCurrentSubtitleId(bestTrack.id)
                updateSubtitleStatus(PlayerViewModel.SubtitleStatus.ON, bestTrack.name)
                Log.d(TAG, "SUBTITLE_DIAG: Selected subtitle track: id=${bestTrack.id} name='${bestTrack.name}' (mode=$mode)")
                dumpSubtitleState(view)
                view.postDelayed({ try { dumpSubtitleState(view) } catch (e: Exception) {} }, 2000)
                // Bug #4 fix: Re-assert sid after 500ms. MPV may internally reset
                // sid to "no" when the track-list changes (e.g. when sub-add with
                // "auto" flag completes). This re-assertion ensures the selected
                // track stays selected.
                val targetSid = bestTrack.id
                view.postDelayed({
                    if (!userDisabledSubtitles && view.sid != targetSid) {
                        try {
                            view.sid = targetSid
                            Log.d(TAG, "SUBTITLE_DIAG: Re-asserted sid=$targetSid after MPV reset (was ${view.sid})")
                        } catch (e: Exception) {
                            Log.w(TAG, "SUBTITLE_DIAG: Could not re-assert sid", e)
                        }
                    }
                }, 500)
            } catch (e: Exception) {
                Log.e(TAG, "SUBTITLE_DIAG: Failed to set subtitle track", e)
            }
        } else {
            viewModel?.setCurrentSubtitleId(currentSid)
        }
        externalTracksJustAdded = false
    }

    /**
     * Auto-select the correct audio track based on the current audio version.
     *
     * ROOT CAUSE of audio switching bug: All audio versions (SUB/DUB/HSUB) from
     * the same extension share the SAME video URL. The audio version is determined
     * by which audio track is selected within the stream, not by a different URL.
     * But we never set `aid` to select the correct audio track — MPV defaults to
     * the first audio track (Japanese/SUB) every time.
     *
     * FIX (H3): Selection is now based on the track's `language` field instead
     * of position in the track list. Previously DUB picked `realAudioTracks.last()`,
     * which is wrong for 3+ tracks (e.g. a stream with jpn+eng+commentary would
     * pick the commentary track as "DUB"). Now:
     *  - DUB: pick the first track whose language CONTAINS "en"/"eng".
     *  - SUB/HSUB: pick the first track whose language CONTAINS "ja"/"jpn".
     *  - Fallback: first real audio track (preserves previous behavior when no
     *    language metadata is present).
     *
     * RECONCILIATION WITH `alang` (initOptions): `alang` is MPV's initial
     * preference for auto-selecting an embedded audio track on load. This
     * function runs on every `track-list` property change and explicitly sets
     * `aid` to enforce the per-version choice, so it overrides alang's initial
     * selection. They are complementary: alang handles the initial MPV pick
     * before we observe track-list; autoSelectAudioTrack enforces the user's
     * SUB/DUB/HSUB choice afterwards. No conflict.
     *
     * Respects [userChangedAudioTrack] — if the user manually changed the audio
     * track via the AudioTracksSheet, we don't override their choice.
     */
    private fun autoSelectAudioTrack(
        view: AnikutaMPVView,
        audioTracks: List<VideoTrack>,
    ) {
        if (userChangedAudioTrack) {
            // User manually selected an audio track — respect their choice
            viewModel?.setCurrentAudioId(view.aid)
            return
        }

        val realAudioTracks = audioTracks.filter { it.id > 0 }
        if (realAudioTracks.isEmpty()) return

        val targetAudioVersion = currentVideoAudio
        val currentAid = view.aid
        Log.d(TAG, "autoSelectAudioTrack: currentAid=$currentAid, targetVersion='$targetAudioVersion', tracks=${realAudioTracks.size}")

        // FIX (H3): Match by language substring instead of track-list position.
        // DUB → track whose language CONTAINS "en" or "eng" (English dub).
        // SUB/HSUB → track whose language CONTAINS "ja" or "jpn" (Japanese sub).
        // Falls back to the first real track when no language matches.
        // Uses contains() (not startsWith) so both 2-letter and 3-letter codes
        // match regardless of where they appear in the language string.
        val desiredAid = when (targetAudioVersion.uppercase()) {
            "DUB" -> realAudioTracks.firstOrNull { track ->
                // FIX (H3): contains("en") also covers "eng"; both checked explicitly per spec.
                track.language?.lowercase()?.let { it.contains("en") || it.contains("eng") } == true
            }?.id ?: realAudioTracks.first().id
            "SUB", "HSUB" -> realAudioTracks.firstOrNull { track ->
                // FIX (H3): "ja" and "jpn" don't overlap as substrings — check both.
                track.language?.lowercase()?.let { it.contains("ja") || it.contains("jpn") } == true
            }?.id ?: realAudioTracks.first().id
            else -> {
                // ANY or unknown — keep current or select first
                if (currentAid > 0) currentAid else realAudioTracks.first().id
            }
        }

        // Only set aid if it's different from current
        if (desiredAid != currentAid) {
            try {
                view.aid = desiredAid
                viewModel?.setCurrentAudioId(desiredAid)
                Log.d(TAG, "Auto-selected audio track: id=$desiredAid (version=$targetAudioVersion)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to auto-select audio track", e)
            }
        } else {
            viewModel?.setCurrentAudioId(currentAid)
        }
    }

    private fun handlePropertyDouble(property: String, value: Double) {
        // speed / aspect — minimal player ignores for now.
    }

    /**
     * Handle mode change — updates orientation + ViewModel state.
     * Called when user taps maximize/minimize buttons.
     */
    /**
     * Switch to a different episode from the player's episode list.
     *
     * Workflow:
     *  1. Show loading overlay on the video area (episode thumbnail + spinner).
     *     Video player controls are hidden during loading.
     *  2. Resolve the video for the new episode using the same source that
     *     provided the current video. The source is looked up by [sourceId]
     *     (passed via Intent from the detail page).
     *  3. Auto-select the video matching the current server / audio / quality.
     *     Falls back gracefully if an exact match isn't available.
     *  4. Load the new video URL into MPV and auto-play.
     *  5. On error: show error message, restore the previous episode index.
     *
     * All steps are logged with [TAG] for debugging.
     */
    fun switchEpisode(index: Int) {
        val vm = viewModel ?: return
        val episodes = vm.episodeList.value
        if (index < 0 || index >= episodes.size) return
        if (index == vm.currentEpisodeIndex.value) return  // already playing

        val episode = episodes[index]
        val previousIndex = vm.currentEpisodeIndex.value
        Log.d(TAG, "=== Episode switch START ===")
        Log.d(TAG, "Switching from episode $previousIndex to $index: ${episode.name}")
        Log.d(TAG, "Current video prefs: server='$currentVideoServer' audio='$currentVideoAudio' quality=$currentVideoQuality")

        // Show loading state — this triggers the loading overlay in the UI
        vm.setSwitchingEpisode(true)
        vm.setCurrentEpisodeIndex(index)

        // Reset the seen-toast flag for the new episode
        hasShownSeenToast = false

        // Phase N-6: Watch-flow auto-download — if the episode being switched to
        // is downloaded, pre-download the next one. Filesystem check (safe, no MPV dependency).
        if (anilistId > 0 && sourceId > 0 && animeTitle.isNotBlank()) {
            val srcName = try {
                uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
                    .get(sourceId)?.name ?: ""
            } catch (e: Exception) { "" }
            if (srcName.isNotBlank()) {
                WatchFlowAutoDownload.maybePreDownloadNext(
                    anilistId = anilistId,
                    animeTitle = animeTitle,
                    sourceId = sourceId,
                    sourceName = srcName,
                    currentEpisodeIndex = index,
                    episodes = episodes,
                )
            }
        }

        // Hide controls during loading
        vm.setControlsVisible(false)
        // Reset user flags for new episode
        userChangedAudioTrack = false
        userDisabledSubtitles = false

        // Pause current video
        try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {
            Log.w(TAG, "Could not pause current video during switch", e)
        }

        lifecycleScope.launch {
            try {
                // Step 1: Get the source
                val source = resolveSource()
                if (source == null) {
                    throw Exception("Could not find source for episode switching (sourceId=$sourceId)")
                }
                Log.d(TAG, "Source resolved: ${source.name} (id=${source.id})")

                // Step 2: Resolve videos for the new episode
                val allVideos = resolveVideoList(source, episode)
                if (allVideos.isEmpty()) {
                    throw Exception("No playable videos found for this episode")
                }
                Log.d(TAG, "Resolved ${allVideos.size} videos for episode ${episode.episode_number}")

                // Step 3: Parse + select best matching video
                val parsedVideos = allVideos.map { app.anikuta.ui.detail.VideoTitleParser.parse(it) }
                // Cache for switchServer/AudioVersion/Quality (no re-resolve needed)
                currentEpisodeVideos = allVideos
                currentParsedVideos = parsedVideos
                val selected = selectBestVideo(parsedVideos)
                Log.d(TAG, "Selected video: server='${selected.server}' audio='${selected.audio}' quality=${selected.quality} url=${selected.video.videoUrl.take(80)}...")

                // Step 4: Build headers for the selected video
                val headers = buildHeaders(selected.video)

                // Step 5: Update state
                currentVideoUrl = selected.video.videoUrl
                viewModel?.setCurrentVideoUrl(currentVideoUrl)  // FIX (L2): reactive highlight
                viewModel?.setCurrentVideoTitle(selected.video.videoTitle)  // FIX: stable highlight
                currentVideoHeaders = headers
                currentVideoServer = selected.server
                currentVideoAudio = selected.audio.name
                currentVideoQuality = selected.quality ?: -1
                currentVideo = selected.video
                episodeUrl = episode.url
                vmEpisodeNumber = episode.episode_number.takeIf { it > 0 }

                // Populate ALL video selection state (servers, videos, audio versions, quality)
                populateVideoSelectionState(parsedVideos, selected)

                // Step 6: Load new video into MPV
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        // Set headers BEFORE loadfile so MPV uses them for the request
                        if (headers.isNotBlank()) {
                            MPVLib.setOptionString("http-header-fields", headers)
                            Log.d(TAG, "Set video headers (${headers.length} chars)")
                        }
                        Log.d(TAG, "Loading new video: ${currentVideoUrl.take(80)}...")
                        val resolvedUrl = resolveUrlForMpv(currentVideoUrl, this@PlayerActivity)
                        MPVLib.command(arrayOf("loadfile", resolvedUrl, "replace"))
                        // Auto-play will happen when MPV_EVENT_FILE_LOADED fires
                        // (see handleEvent). The loading overlay will be cleared
                        // when onFileLoaded() is called.
                    } catch (e: Exception) {
                        throw Exception("Failed to load video into MPV: ${e.message}", e)
                    }
                }

                Log.d(TAG, "=== Episode switch SUCCESS ===")
                // Note: isSwitchingEpisode is cleared when MPV fires FILE_LOADED.
                // But if the file fails to load, onFileEnded will fire with an
                // error. We set a timeout to clear the switching state in case
                // FILE_LOADED never fires.
                launchSwitchTimeout()

            } catch (e: Exception) {
                Log.e(TAG, "=== Episode switch FAILED ===", e)
                // Restore previous episode index
                vm.setCurrentEpisodeIndex(previousIndex)
                vm.setSwitchingEpisode(false)
                vm.setControlsVisible(true)
                // Show error as a Toast (less intrusive than full-screen overlay —
                // the user can continue watching the current episode)
                val errorMsg = e.message ?: "Unknown error"
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PlayerActivity,
                        "Could not switch episode: $errorMsg",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                Log.w(TAG, "Episode switch error shown to user: $errorMsg")
            }
        }
    }

    /**
     * Resolve the source for episode switching. Tries sourceId first, then
     * falls back to looking up by name from the EpisodeCacheStore.
     *
     * SAFETY: Includes retry logic to wait for extensions to load (they load
     * async on app start). Without this, the source lookup might fail if the
     * user switches episodes before extensions are fully loaded.
     */
    private suspend fun resolveSource(): app.anikuta.source.api.AnimeCatalogueSource? {
        val mgr = try {
            uy.kohesive.injekt.Injekt.get<app.anikuta.domain.source.anime.service.AnimeSourceManager>()
        } catch (e: Exception) {
            Log.w(TAG, "AnimeSourceManager unavailable", e)
            return null
        }

        // Try by sourceId first (most reliable) — with retry for async loading
        if (sourceId > 0) {
            var retries = 0
            var source = mgr.get(sourceId)
            while (source == null && retries < 10) {
                kotlinx.coroutines.delay(500)
                retries++
                source = mgr.get(sourceId)
            }
            if (source is app.anikuta.source.api.AnimeCatalogueSource) {
                return source
            }
            if (source != null) {
                Log.w(TAG, "Source id=$sourceId is not a catalogue source (got ${source.javaClass.simpleName})")
            }
        }

        // Fallback: look up by name from EpisodeCacheStore — with retry
        if (anilistId > 0) {
            try {
                val cacheStore = uy.kohesive.injekt.Injekt.get<app.anikuta.data.cache.EpisodeCacheStore>()
                val cached = cacheStore.load(anilistId)
                if (cached != null) {
                    val (_, sourceName) = cached
                    var retries = 0
                    var source = mgr.getCatalogueSources().find { it.name == sourceName }
                    while (source == null && retries < 10) {
                        kotlinx.coroutines.delay(500)
                        retries++
                        source = mgr.getCatalogueSources().find { it.name == sourceName }
                    }
                    if (source != null) {
                        Log.d(TAG, "Source found by name '$sourceName' from cache (after $retries retries)")
                        return source
                    } else {
                        Log.w(TAG, "Source '$sourceName' not found after $retries retries")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not look up source from cache", e)
            }
        }

        return null
    }

    /**
     * Resolve the video list for an episode. Tries getHosterList first (new
     * API), falls back to getVideoList (old API) on any exception.
     * Mirrors DetailViewModel.resolveVideos().
     */
    private suspend fun resolveVideoList(
        source: app.anikuta.source.api.AnimeCatalogueSource,
        episode: app.anikuta.source.api.model.SEpisode,
    ): List<eu.kanade.tachiyomi.animesource.model.Video> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d(TAG, "Trying getHosterList for episode...")
                val hosters = source.getHosterList(episode)
                val videos = hosters.mapNotNull { hoster ->
                    hoster.videoList?.filter { it.videoUrl.isNotBlank() }
                }.flatten()
                Log.d(TAG, "getHosterList returned ${hosters.size} hosters, ${videos.size} videos")
                videos
            } catch (e: Exception) {
                Log.d(TAG, "getHosterList failed (${e.javaClass.simpleName}: ${e.message}), falling back to getVideoList")
                val flat = source.getVideoList(episode)
                flat.filter { it.videoUrl.isNotBlank() }
            }
        }
    }

    /**
     * Select the best matching video from a list of parsed videos.
     *
     * Priority (matching the user's current video):
     *  1. Exact match: same server AND same audio AND same quality
     *  2. Same server AND same audio (any quality, pick highest)
     *  3. Same server (any audio, prefer SUB, pick highest quality)
     *  4. Same audio (any server, pick highest quality)
     *  5. First video (best effort)
     */
    private fun selectBestVideo(
        videos: List<app.anikuta.ui.detail.ParsedVideo>,
    ): app.anikuta.ui.detail.ParsedVideo {
        val targetServer = currentVideoServer
        val targetAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
        val targetQuality = currentVideoQuality

        // 1. Exact match
        videos.firstOrNull { it.server == targetServer && it.audio == targetAudio && it.quality == targetQuality }
            ?.let { return it }

        // 2. Same server + same audio, highest quality
        videos.filter { it.server == targetServer && it.audio == targetAudio }
            .maxByOrNull { it.quality ?: 0 }
            ?.let { return it }

        // 3. Same server, prefer same audio, highest quality
        videos.filter { it.server == targetServer }
            .sortedWith(compareByDescending<app.anikuta.ui.detail.ParsedVideo> { it.audio == targetAudio }
                .thenByDescending { it.quality ?: 0 })
            .firstOrNull()
            ?.let { return it }

        // 4. Same audio, any server, highest quality
        videos.filter { it.audio == targetAudio }
            .maxByOrNull { it.quality ?: 0 }
            ?.let { return it }

        // 5. Best effort: first video (highest quality overall)
        Log.w(TAG, "No server/audio match found — using best-effort first video")
        return videos.maxByOrNull { it.quality ?: 0 } ?: videos.first()
    }

    /**
     * Build HTTP headers string for MPV's http-header-fields option.
     * Matches DetailViewModel.buildHeaders().
     */
    private fun buildHeaders(video: eu.kanade.tachiyomi.animesource.model.Video): String {
        val headers = video.headers
        return if (headers != null && headers.size > 0) {
            headers.toMultimap()
                .mapValues { it.value.firstOrNull() ?: "" }
                .map { "${it.key}: ${it.value.replace(",", "\\,")}" }
                .joinToString(",")
        } else {
            "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
        }
    }

    /**
     * Parts 2+3+4: Populate ALL video selection state in the ViewModel from
     * the parsed video list. Called after episode switching AND after initial
     * background video resolution.
     *
     * Populates: availableServers, currentServer, availableVideos,
     * availableAudioVersions (PER-SERVER), currentAudioVersion, currentVideoQuality.
     *
     * FIX: Audio versions are now filtered to the CURRENT SERVER only. Previously
     * they were derived from ALL servers, so the dropdown showed audio versions
     * that weren't available on the current server — causing failed switches.
     */
    private fun populateVideoSelectionState(
        parsedVideos: List<app.anikuta.ui.detail.ParsedVideo>,
        selected: app.anikuta.ui.detail.ParsedVideo,
    ) {
        val vm = viewModel ?: return
        // Servers: distinct server names from parsed videos
        val servers = parsedVideos.map { it.server }.distinct()
        vm.setAvailableServers(servers, selected.server)
        Log.d(TAG, "Populated servers: $servers (current=${selected.server})")

        // Videos: raw Video objects for the Quality sheet
        vm.setAvailableVideos(parsedVideos.map { it.video })

        // Audio versions: ONLY from the current server (per-server accuracy)
        // This ensures the dropdown only shows audio versions that the current
        // server actually provides. When the user switches servers, this is
        // re-populated by loadSelectedVideo() to match the new server.
        val currentServerAudios = parsedVideos
            .filter { it.server == selected.server }
            .map { it.audio.name }
            .distinct()
        vm.setAvailableAudioVersions(currentServerAudios, selected.audio.name)
        Log.d(TAG, "Populated audio versions for server '${selected.server}': $currentServerAudios (current=${selected.audio.name})")

        // Current quality
        vm.setCurrentVideoQuality(selected.quality ?: -1)
        Log.d(TAG, "Populated quality: ${selected.quality ?: -1}")
    }

    /**
     * Part 3: Switch to a different server while keeping the same audio
     * version and quality (as close as possible). Uses the cached video list
     * — no network re-resolution needed.
     */
    fun switchServer(serverName: String) {
        val vm = viewModel ?: return
        if (serverName == currentVideoServer) {
            Log.d(TAG, "switchServer: already on '$serverName' — skipping")
            return
        }
        if (currentParsedVideos.isEmpty()) {
            Log.w(TAG, "switchServer: no cached videos — cannot switch")
            return
        }
        Log.d(TAG, "=== Server switch: '$currentVideoServer' -> '$serverName' ===")

        // Select best video for the new server: prefer same audio + same quality
        val targetAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
        val candidates = currentParsedVideos.filter { it.server == serverName }
        if (candidates.isEmpty()) {
            Log.w(TAG, "switchServer: no videos for server '$serverName'")
            return
        }
        // Try exact match (same audio + same quality)
        val selected = candidates.firstOrNull { it.audio == targetAudio && it.quality == currentVideoQuality }
            ?: candidates.filter { it.audio == targetAudio }.maxByOrNull { it.quality ?: 0 }
            ?: candidates.maxByOrNull { it.quality ?: 0 }
            ?: candidates.first()

        loadSelectedVideo(selected)
        Log.d(TAG, "=== Server switch SUCCESS: ${selected.server} ${selected.audio} ${selected.quality}p ===")
    }

    /**
     * Part 4: Switch to a different audio version (SUB/DUB/HSUB) while keeping
     * the same server and quality (as close as possible). Uses cached videos.
     */
    fun switchAudioVersion(audioVersion: String) {
        val vm = viewModel ?: return
        if (audioVersion == currentVideoAudio) {
            Log.d(TAG, "switchAudioVersion: already on '$audioVersion' — skipping")
            return
        }
        if (currentParsedVideos.isEmpty()) {
            Log.w(TAG, "switchAudioVersion: no cached videos — cannot switch")
            return
        }
        Log.d(TAG, "=== Audio version switch: '$currentVideoAudio' -> '$audioVersion' ===")

        val targetAudio = app.anikuta.ui.detail.AudioVersion.fromToken(audioVersion)
        val candidates = currentParsedVideos.filter { it.audio == targetAudio }
        if (candidates.isEmpty()) {
            Log.w(TAG, "switchAudioVersion: no videos for audio '$audioVersion'")
            // Show toast — user selected an unavailable version
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    "Audio version '$audioVersion' is not available for this episode",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        // Prefer same server + same quality
        val selected = candidates.firstOrNull { it.server == currentVideoServer && it.quality == currentVideoQuality }
            ?: candidates.filter { it.server == currentVideoServer }.maxByOrNull { it.quality ?: 0 }
            ?: candidates.maxByOrNull { it.quality ?: 0 }
            ?: candidates.first()

        loadSelectedVideo(selected)
        Log.d(TAG, "=== Audio version switch SUCCESS: ${selected.server} ${selected.audio} ${selected.quality}p ===")
    }

    /**
     * Part 2: Switch to a different quality while keeping the same server and
     * audio version. Uses cached videos.
     */
    fun switchQuality(video: eu.kanade.tachiyomi.animesource.model.Video) {
        val parsed = currentParsedVideos.find { it.video.videoUrl == video.videoUrl }
        if (parsed == null) {
            Log.w(TAG, "switchQuality: video not found in cached list")
            return
        }
        Log.d(TAG, "=== Quality switch: $currentVideoQuality -> ${parsed.quality}p ===")
        loadSelectedVideo(parsed)
        Log.d(TAG, "=== Quality switch SUCCESS: ${parsed.server} ${parsed.audio} ${parsed.quality}p ===")
    }

    /**
     * Shared helper: load a parsed video into MPV + update all state.
     * Used by switchServer, switchAudioVersion, switchQuality.
     *
     * FIX: After switching, re-populates the available audio versions to
     * match the NEW server's available audio versions. This ensures the
     * audio dropdown always reflects what the current server provides.
     */
    private fun loadSelectedVideo(selected: app.anikuta.ui.detail.ParsedVideo) {
        val vm = viewModel ?: return
        val headers = buildHeaders(selected.video)

        // Update Activity state
        currentVideoUrl = selected.video.videoUrl
        vm.setCurrentVideoUrl(currentVideoUrl)  // FIX (L2): reactive highlight
        vm.setCurrentVideoTitle(selected.video.videoTitle)  // FIX: stable highlight
        currentVideoHeaders = headers
        currentVideoServer = selected.server
        currentVideoAudio = selected.audio.name
        currentVideoQuality = selected.quality ?: -1
        // Store the Video object so external tracks can be loaded after FILE_LOADED
        currentVideo = selected.video
        // Reset user flags — new video loaded, user hasn't interacted yet
        userChangedAudioTrack = false
        userDisabledSubtitles = false
        // Bug #1/#5 fix: Reset file-loaded flag + track dedup set for the new file.
        fileLoaded = false
        addedTrackUrls.clear()

        // Update VM state (for UI reactivity)
        vm.setCurrentServer(selected.server)
        vm.setCurrentAudioVersion(selected.audio.name)
        vm.setCurrentVideoQuality(selected.quality ?: -1)

        // FIX: Re-populate available audio versions for the NEW server.
        // When the user switches to a different server, the audio versions
        // available may change (some servers have SUB+DUB, others only SUB).
        // This keeps the dropdown accurate per-server.
        if (currentParsedVideos.isNotEmpty()) {
            val newServerAudios = currentParsedVideos
                .filter { it.server == selected.server }
                .map { it.audio.name }
                .distinct()
            vm.setAvailableAudioVersions(newServerAudios, selected.audio.name)
            Log.d(TAG, "Updated audio versions for server '${selected.server}': $newServerAudios (current=${selected.audio.name})")
        }

        // Show loading indicator — "quality" type freezes the last frame
        vm.setSwitchingEpisode(true, "quality")
        vm.setControlsVisible(false)

        // FIX: Preserve playback position across quality/server switches.
        // Set pendingSeekPosition — consumed by handleEvent(FILE_LOADED) after
        // the new video loads. This is more reliable than the MPV "start"
        // property which may not work consistently across MPV builds.
        val savedPosition = mpvViewRef?.timePos ?: 0
        if (savedPosition > 5) {
            pendingSeekPosition = savedPosition
            Log.d(TAG, "Pending seek position set: ${savedPosition}s")
        } else {
            pendingSeekPosition = -1
        }

        // Load into MPV
        lifecycleScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    if (headers.isNotBlank()) {
                        MPVLib.setOptionString("http-header-fields", headers)
                    }
                    Log.d(TAG, "Loading video: ${currentVideoUrl.take(80)}...")
                    val resolvedUrl2 = resolveUrlForMpv(currentVideoUrl, this@PlayerActivity)
                    MPVLib.command(arrayOf("loadfile", resolvedUrl2, "replace"))
                    launchSwitchTimeout()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load video", e)
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Failed to load video: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Parts 2+3+4: Resolve videos for the current episode in the background
     * (on initial load). Populates the server/audio/quality state so the
     * dropdowns and sheets work immediately when the user opens them.
     *
     * FIX (C1): Skips if reResolveAndLoadVideo is already in progress —
     * avoids duplicate network calls and track additions.
     */
    private fun resolveVideosInBackground() {
        val vm = viewModel ?: return
        if (sourceId < 0 && anilistId < 0) return  // no source to resolve from
        // Skip if re-resolve is already running (it does the same work)
        if (resolveInProgress) {
            Log.d(TAG, "Background resolve: skipping — reResolveAndLoadVideo already in progress")
            return
        }
        val episode = vm.episodeList.value.getOrNull(vm.currentEpisodeIndex.value) ?: return

        lifecycleScope.launch {
            try {
                Log.d(TAG, "=== Background video resolution START ===")
                val source = resolveSource() ?: run {
                    Log.w(TAG, "Background resolve: source not found")
                    return@launch
                }
                val allVideos = resolveVideoList(source, episode)
                if (allVideos.isEmpty()) {
                    Log.w(TAG, "Background resolve: no videos found")
                    return@launch
                }
                val parsedVideos = allVideos.map { app.anikuta.ui.detail.VideoTitleParser.parse(it) }
                // Cache for switchServer/AudioVersion/Quality
                currentEpisodeVideos = allVideos
                currentParsedVideos = parsedVideos

                // Find the currently-playing video in the parsed list.
                // FIX: Previously matched by videoUrl, but URLs contain localhost:PORT
                // which changes every resolution. Now matches by server + audio + quality
                // (from Intent extras) to find the correct video without overwriting
                // the user's audio version selection.
                val intentServer = currentVideoServer
                val intentAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
                val intentQuality = currentVideoQuality

                // Try exact match first: same server + same audio + same quality
                var currentParsed = parsedVideos.find {
                    it.server == intentServer && it.audio == intentAudio && it.quality == intentQuality
                }
                // Fallback: same server + same audio (any quality)
                    ?: parsedVideos.find { it.server == intentServer && it.audio == intentAudio }
                // Fallback: same server (prefer same audio, then first)
                    ?: parsedVideos.filter { it.server == intentServer }
                        .sortedByDescending { if (it.audio == intentAudio) 1 else 0 }
                        .firstOrNull()
                // Last resort: match by videoTitle (the original URL might have the
                // same underlying stream even if the proxy URL differs)
                    ?: parsedVideos.find { it.video.videoUrl == currentVideoUrl }

                if (currentParsed != null) {
                    // Update Activity state from the parsed video (more accurate than Intent extras)
                    currentVideoServer = currentParsed.server
                    currentVideoAudio = currentParsed.audio.name
                    currentVideoQuality = currentParsed.quality ?: -1
                    // FIX: Store the full Video object (with subtitleTracks) so
                    // external subtitles can be loaded. The initial currentVideo
                    // from onCreate only had videoUrl + videoTitle (no tracks).
                    currentVideo = currentParsed.video
                    vm.setCurrentVideoTitle(currentParsed.video.videoTitle)
                    populateVideoSelectionState(parsedVideos, currentParsed)
                    // FIX: If the resolved video has external subtitle tracks,
                    // load them into MPV now. The initial loadExternalTracks()
                    // call (on FILE_LOADED) found no tracks because currentVideo
                    // was empty. Now that we have the real Video, add the tracks.
                    val subCount = currentParsed.video.subtitleTracks.size
                    val audioCount = currentParsed.video.audioTracks.size
                    if (subCount > 0 || audioCount > 0) {
                        Log.d(TAG, "Background resolve: loading $subCount sub + $audioCount audio tracks")
                        loadExternalTracks()
                    }
                    Log.d(TAG, "Background resolve: matched current video by server+audio+quality: ${currentParsed.server} ${currentParsed.audio} ${currentParsed.quality}p")
                } else {
                    // The playing video wasn't in the resolved list at all.
                    // Use the Intent extras as the "current" and populate the rest.
                    // FIX: Don't call selectBestVideo (which would overwrite the user's
                    // audio selection). Instead, create a synthetic ParsedVideo from
                    // the Intent extras and use it as the "current".
                    Log.w(TAG, "Background resolve: current video not found in resolved list — keeping Intent extras as current")
                    val selected = selectBestVideo(parsedVideos)
                    currentVideo = selected.video
                    vm.setCurrentVideoTitle(selected.video.videoTitle)
                    // Populate state but use the INTENT audio version, not selected
                    populateVideoSelectionState(parsedVideos, selected)
                    // Restore the correct current values from Intent
                    vm.setCurrentServer(intentServer)
                    vm.setCurrentAudioVersion(intentAudio.name)
                    vm.setCurrentVideoQuality(intentQuality)
                    // Load tracks for the selected video too
                    val subCount = selected.video.subtitleTracks.size
                    val audioCount = selected.video.audioTracks.size
                    if (subCount > 0 || audioCount > 0) {
                        Log.d(TAG, "Background resolve (fallback): loading $subCount sub + $audioCount audio tracks")
                        loadExternalTracks()
                    }
                }
                Log.d(TAG, "=== Background video resolution SUCCESS: ${parsedVideos.size} videos ===")
            } catch (e: Exception) {
                Log.w(TAG, "Background video resolution failed", e)
            }
        }
    }

    /**
     * Timeout for episode switching — if MPV doesn't fire FILE_LOADED within
     * 30 seconds, clear the switching state and show an error.
     */
    private var switchTimeoutJob: kotlinx.coroutines.Job? = null
    private fun launchSwitchTimeout() {
        switchTimeoutJob?.cancel()
        switchTimeoutJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(30_000)
            val vm = viewModel ?: return@launch
            if (vm.isSwitchingEpisode.value) {
                Log.w(TAG, "Episode switch timed out after 30s — clearing state")
                vm.setSwitchingEpisode(false)
                vm.setControlsVisible(true)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PlayerActivity,
                        "Video loading timed out. Please try again.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    /** Cancel the switch timeout when the video loads successfully. */
    private fun cancelSwitchTimeout() {
        switchTimeoutJob?.cancel()
        switchTimeoutJob = null
    }

    fun handleModeChange(mode: PlayerMode) {
        viewModel?.setPlayerMode(mode)
        when (mode) {
            PlayerMode.FULLSCREEN -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                hideSystemBars()
            }
            PlayerMode.MINIMIZED -> {
                showSystemBars()
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
        Log.d(TAG, "Mode changed to: $mode")
    }

    @Suppress("DEPRECATION")
    private fun showSystemBars() {
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    /**
     * Handle first-time prompt selection.
     * Saves the preference, switches to the selected mode, and loads the video
     * (which was delayed until the prompt was resolved).
     */
    fun handleFirstTimePrompt(view: String, remember: Boolean) {
        showFirstTimePrompt = false
        if (remember) {
            playerPrefs?.defaultPlayerView()?.set(view)
            playerPrefs?.playerPromptShown()?.set(true)
        }
        when (view) {
            "fullscreen" -> handleModeChange(PlayerMode.FULLSCREEN)
            "minimized" -> handleModeChange(PlayerMode.MINIMIZED)
        }
        // Now that the prompt is resolved, load the video if it hasn't been loaded yet
        loadVideoIfPending()
        Log.d(TAG, "First-time prompt: view=$view, remember=$remember")
    }

    /**
     * Load the video if it hasn't been loaded yet (delayed by the first-time prompt).
     *
     * FIX (H1): videoLoaded is only set to true AFTER the loadfile is dispatched
     * (or re-resolution starts). If re-resolution fails, videoLoaded is reset to
     * false so the user can retry.
     *
     * FIX: If the video URL is a localhost proxy URL, re-resolve from source.
     */
    fun loadVideoIfPending() {
        if (videoLoaded) return
        val url = viewModel?.videoUrl ?: return

        // Check if the URL is a localhost proxy URL (may be stale after app restart)
        if (url.contains("localhost:") || url.contains("127.0.0.1:")) {
            Log.d(TAG, "Video URL is localhost proxy — may be stale, re-resolving: ${url.take(80)}...")
            videoLoaded = true
            reResolveAndLoadVideo()
        } else {
            videoLoaded = true
            // Bug #1/#5 fix: reset for new file
            fileLoaded = false
            addedTrackUrls.clear()
            Log.d(TAG, "Loading video (direct URL): ${url.take(80)}...")
            try {
                val resolvedUrl3 = resolveUrlForMpv(url, this@PlayerActivity)
                Log.d(TAG, "Resolved URL for MPV: ${resolvedUrl3.take(80)}...")
                MPVLib.command(arrayOf("loadfile", resolvedUrl3, "replace"))
            } catch (e: Exception) {
                Log.e(TAG, "Could not load video (exception)", e)
                videoLoaded = false // allow retry
            } catch (e: Error) {
                // Catch NoClassDefFoundError or other fatal errors
                Log.e(TAG, "Could not load video (error)", e)
                videoLoaded = false
            }
        }
    }

    /**
     * Re-resolve the video URL from the source and load it into MPV.
     *
     * FIX (C1): Uses resolveInProgress guard to prevent concurrent execution
     * with resolveVideosInBackground(). If a resolve is already in progress,
     * this function returns immediately.
     *
     * FIX (C2): Waits for the episode list to be loaded from disk before
     * attempting to resolve. If the episode list is still empty after 5s,
     * gives up with an error.
     *
     * FIX (H1): Resets videoLoaded=false on failure so the user can retry.
     */
    private fun reResolveAndLoadVideo() {
        val vm = viewModel ?: run {
            videoLoaded = false
            return
        }
        // Guard against concurrent resolution
        if (resolveInProgress) {
            Log.d(TAG, "Re-resolve: already in progress — skipping")
            return
        }
        resolveInProgress = true
        vm.setSwitchingEpisode(true, "initial")
        vm.setControlsVisible(false)

        lifecycleScope.launch {
            try {
                val source = resolveSource()
                if (source == null) {
                    Log.e(TAG, "Re-resolve: source not found — cannot load video")
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Could not load video: source not found",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@launch
                }

                // FIX (C2): Wait for episode list to be loaded from disk cache.
                // The episode list loads async — if we don't wait, episode is null
                // and the video never loads.
                var retries = 0
                while (vm.episodeList.value.isEmpty() && retries < 10) {
                    kotlinx.coroutines.delay(500)
                    retries++
                }
                val episode = vm.episodeList.value.getOrNull(vm.currentEpisodeIndex.value)
                if (episode == null) {
                    Log.e(TAG, "Re-resolve: episode not found after $retries retries (${vm.episodeList.value.size} episodes)")
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    videoLoaded = false // allow retry
                    resolveInProgress = false
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Could not load video: episode not found",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@launch
                }

                Log.d(TAG, "Re-resolve: fetching videos from ${source.name}...")
                val allVideos = resolveVideoList(source, episode)
                if (allVideos.isEmpty()) {
                    Log.e(TAG, "Re-resolve: no videos found")
                    vm.setSwitchingEpisode(false)
                    vm.setControlsVisible(true)
                    return@launch
                }

                val parsedVideos = allVideos.map { app.anikuta.ui.detail.VideoTitleParser.parse(it) }
                currentEpisodeVideos = allVideos
                currentParsedVideos = parsedVideos

                // Select best matching video by server + audio + quality from Intent
                val intentServer = currentVideoServer
                val intentAudio = app.anikuta.ui.detail.AudioVersion.fromToken(currentVideoAudio)
                val intentQuality = currentVideoQuality

                val selected = parsedVideos.find {
                    it.server == intentServer && it.audio == intentAudio && it.quality == intentQuality
                } ?: parsedVideos.find { it.server == intentServer && it.audio == intentAudio }
                    ?: parsedVideos.filter { it.server == intentServer }
                        .sortedByDescending { if (it.audio == intentAudio) 1 else 0 }
                        .firstOrNull()
                    ?: selectBestVideo(parsedVideos)

                Log.d(TAG, "Re-resolve: selected ${selected.server} ${selected.audio} ${selected.quality}p")

                // Update all state
                currentVideoUrl = selected.video.videoUrl
                vm.setCurrentVideoUrl(currentVideoUrl)  // FIX (L2): reactive highlight
                vm.setCurrentVideoTitle(selected.video.videoTitle)  // FIX: stable highlight
                currentVideoHeaders = buildHeaders(selected.video)
                currentVideoServer = selected.server
                currentVideoAudio = selected.audio.name
                currentVideoQuality = selected.quality ?: -1
                currentVideo = selected.video
                userChangedAudioTrack = false
                userDisabledSubtitles = false

                // Populate selection state
                populateVideoSelectionState(parsedVideos, selected)

                // Load external tracks
                loadExternalTracks()

                // Load the fresh URL into MPV
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        if (currentVideoHeaders.isNotBlank()) {
                            MPVLib.setOptionString("http-header-fields", currentVideoHeaders)
                        }
                        Log.d(TAG, "Re-resolve: loading fresh URL: ${currentVideoUrl.take(80)}...")
                        // Bug #1/#5 fix: reset for new file
                        fileLoaded = false
                        addedTrackUrls.clear()
                        val resolvedUrl4 = resolveUrlForMpv(currentVideoUrl, this@PlayerActivity)
                        MPVLib.command(arrayOf("loadfile", resolvedUrl4, "replace"))
                        launchSwitchTimeout()
                    } catch (e: Exception) {
                        Log.e(TAG, "Re-resolve: failed to load video", e)
                        vm.setSwitchingEpisode(false)
                        vm.setControlsVisible(true)
                    }
                }

                Log.d(TAG, "=== Re-resolve SUCCESS ===")
                resolveInProgress = false
            } catch (e: Exception) {
                Log.e(TAG, "=== Re-resolve FAILED ===", e)
                vm.setSwitchingEpisode(false)
                vm.setControlsVisible(true)
                videoLoaded = false // allow retry
                resolveInProgress = false
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@PlayerActivity,
                        "Could not load video: ${e.message}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Use View.SYSTEM_UI_FLAG flags as fallback (works on all API levels)
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    /**
     * P2a: Enter PiP mode when the user presses Home — but only if:
     * 1. The pipOnExit setting is enabled (default: false)
     * 2. The video is currently playing (not paused)
     * 3. The device supports PiP (API 26+)
     *
     * The manual PiP button (enterPiP) always works regardless of the setting.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pipEnabled = playerPrefs?.pipOnExit()?.get() ?: false
            val isPlaying = viewModel?.isPlaying?.value ?: false
            if (pipEnabled && isPlaying) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                    Log.d(TAG, "Entered PiP mode (auto on Home, playing=$isPlaying)")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enter PiP mode", e)
                }
            } else {
                Log.d(TAG, "PiP skipped (enabled=$pipEnabled, playing=$isPlaying)")
            }
        }
    }

    /**
     * Phase 6.2 — Manually enter PiP mode (from the PiP button in fullscreen).
     */
    fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
                Log.d(TAG, "Entered PiP mode (manual)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not enter PiP mode", e)
            }
        }
    }

    /**
     * Part 6: Toggle screen orientation between landscape and portrait.
     * Called from the rotate button in fullscreen controls.
     */
    fun toggleOrientation() {
        val current = requestedOrientation
        requestedOrientation = if (current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
            current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ) {
            Log.d(TAG, "Rotate: landscape -> portrait")
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            Log.d(TAG, "Rotate: portrait -> landscape")
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * Part 6: Open subtitle settings (has delay slider).
     * Called from the More Options sheet.
     */
    fun openSubtitleSettings() {
        runOnUiThread {
            android.widget.Toast.makeText(
                this@PlayerActivity,
                "Open Subtitles (CC icon) to access delay settings",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        Log.d(TAG, "Subtitle settings requested (open subtitle sheet)")
    }

    /**
     * Part 6: Cycle audio delay via MPV's audio-delay property.
     *
     * FIX (M7): The previous `when` had a dead branch — `currentDelay < 0.0`
     * was fully covered by `currentDelay < 0.3` and both branches did the same
     * `+0.2` increment, so the negative branch was unreachable distinct code.
     * The cycle is now expressed as a single linear progression:
     *
     *   0.0 → -0.3 → -0.1 → 0.1 → 0.3 → 0.0 (reset)
     *
     * i.e. each press advances by +0.2s, starting at -0.3s and resetting to 0
     * once we reach (or exceed) +0.3s. Distinct values: -0.3, -0.1, 0.1, 0.3.
     */
    fun cycleAudioDelay() {
        try {
            // Use getPropertyString since getPropertyDouble may not exist in MPVLib
            val currentStr = `is`.xyz.mpv.MPVLib.getPropertyString("audio-delay")
            val currentDelay = currentStr?.toDoubleOrNull() ?: 0.0
            // FIX (M7): Single linear cycle — see docstring above.
            val newDelay = when {
                currentDelay == 0.0 -> -0.3       // start: jump to -0.3s
                currentDelay < 0.3 -> currentDelay + 0.2  // advance by 0.2s
                else -> 0.0                       // reset at +0.3s or beyond
            }
            `is`.xyz.mpv.MPVLib.setPropertyDouble("audio-delay", newDelay)
            Log.d(TAG, "Audio delay set to ${newDelay}s (was ${currentDelay}s)")
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Audio delay: ${newDelay}s",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set audio delay", e)
        }
    }

    /**
     * Part 6: Take a screenshot via MPV's screenshot-to-file command.
     */
    fun takeScreenshot() {
        try {
            val dir = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
            val path = "$dir/screenshot_${System.currentTimeMillis()}.png"
            `is`.xyz.mpv.MPVLib.command(arrayOf("screenshot-to-file", path))
            Log.d(TAG, "Screenshot saved to $path")
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Screenshot saved",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screenshot failed", e)
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Screenshot failed: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * Part 6: Sleep timer — pause playback after 15 minutes.
     */
    fun startSleepTimer() {
        runOnUiThread {
            android.widget.Toast.makeText(
                this@PlayerActivity,
                "Sleep timer: playback will pause in 15 minutes",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(15 * 60 * 1000L)
            try { `is`.xyz.mpv.MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
            viewModel?.onPauseChanged(true)
            Log.d(TAG, "Sleep timer fired — paused playback")
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@PlayerActivity,
                    "Sleep timer: playback paused",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * FIX (L4): Tracks whether the video was actually playing (not paused)
     * immediately before [onPause] paused it. Used by [onResume] to decide
     * whether to auto-resume playback when the user returns to the player.
     *
     * Only set when we actually paused the video in onPause (i.e. NOT entering
     * PiP and NOT finishing). Remains false otherwise so onResume is a no-op.
     */
    @Volatile private var wasPlayingBeforeOnPause: Boolean = false

    // ---- D.5: Audio focus ----
    // Requests audio focus so other media apps pause when our player starts,
    // and we pause when another app requests focus (e.g. Spotify, calls).
    private var audioFocusListener: android.media.AudioManager.OnAudioFocusChangeListener? = null
    private var audioManager: android.media.AudioManager? = null

    private fun requestAudioFocus() {
        audioManager = audioManager ?: getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioFocusListener = audioFocusListener ?: android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                    // Permanent loss — pause playback
                    try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
                    viewModel?.onPauseChanged(true)
                    wasPlayingBeforeOnPause = false
                    Log.d(TAG, "Audio focus lost (permanent) — pausing")
                }
                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Transient loss (e.g. call) — pause but can resume
                    val wasPlaying = viewModel?.isPlaying?.value ?: false
                    try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
                    viewModel?.onPauseChanged(true)
                    wasPlayingBeforeOnPause = wasPlaying
                    Log.d(TAG, "Audio focus lost (transient) — pausing")
                }
                android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                    // Regained focus — resume if we were playing before
                    if (wasPlayingBeforeOnPause) {
                        try { MPVLib.setPropertyBoolean("pause", false) } catch (e: Exception) {}
                        viewModel?.onPauseChanged(false)
                        wasPlayingBeforeOnPause = false
                        Log.d(TAG, "Audio focus regained — resuming")
                    }
                }
            }
        }
        val result = audioManager?.requestAudioFocus(
            audioFocusListener,
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.AUDIOFOCUS_GAIN,
        )
        Log.d(TAG, "Audio focus request result: $result")
    }

    private fun abandonAudioFocus() {
        audioFocusListener?.let { listener ->
            audioManager?.abandonAudioFocus(listener)
            Log.d(TAG, "Audio focus abandoned")
        }
        audioFocusListener = null
    }

    override fun onPause() {
        super.onPause()
        // Cancel periodic save + save once before pausing
        periodicSaveJob?.cancel()
        Log.d(TAG, "onPause: cancelled periodic save, saving progress once more")
        saveProgress()
        // FIX (H5): Don't pause if entering PiP mode — the video should keep
        // playing in the PiP window. Only pause when the activity is actually
        // going to background (not PiP).
        if (!isFinishing && !isInPictureInPictureMode) {
            // FIX (L4): Record the playing state BEFORE pausing so onResume
            // can decide whether to auto-resume. We read the ViewModel's
            // isPlaying flow (kept in sync with MPV's "pause" property by
            // [handlePropertyBoolean]).
            wasPlayingBeforeOnPause = viewModel?.isPlaying?.value == true
            try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {
                Log.w(TAG, "Could not pause on background", e)
            }
        } else {
            // PiP or finishing — don't auto-resume in onResume.
            wasPlayingBeforeOnPause = false
        }
        // Note: saveProgress() already called at the start of onPause
    }

    override fun onResume() {
        super.onResume()
        // FIX (L4): If the video was playing when the user backgrounded the
        // app (and we paused it in onPause), un-pause so the video doesn't
        // stay frozen when returning to the player. We do NOT auto-resume if:
        //  - the user was already paused (wasPlayingBeforeOnPause == false)
        //  - we entered PiP (no pause happened, nothing to undo)
        //  - the activity is finishing
        //  - MPV/the view isn't initialized yet
        if (wasPlayingBeforeOnPause && !isFinishing && !isInPictureInPictureMode && mpvViewRef != null) {
            try {
                MPVLib.setPropertyBoolean("pause", false)
                viewModel?.onPauseChanged(false)
                Log.d(TAG, "onResume: auto-resumed playback (was playing before onPause)")
            } catch (e: Exception) {
                Log.w(TAG, "onResume: could not auto-resume playback", e)
            }
        }
        // Always clear the flag — it represents the previous onPause cycle.
        wasPlayingBeforeOnPause = false
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgress()
        // Cancel any ongoing buffer wait
        cancelBufferWait()
        // D.5: Abandon audio focus
        abandonAudioFocus()
        // FIX (M5): Each cleanup call wrapped in its own try/catch so one
        // failure doesn't skip the rest. If removeLogObserver throws, we
        // still need to call removeObserver, stop, and destroy.
        try { MPVLib.removeLogObserver(observer) } catch (e: Exception) {
            Log.w(TAG, "removeLogObserver failed", e)
        }
        try { MPVLib.removeObserver(observer) } catch (e: Exception) {
            Log.w(TAG, "removeObserver failed", e)
        }
        try { MPVLib.command(arrayOf("stop")) } catch (e: Exception) {
            Log.w(TAG, "stop command failed", e)
        }
        // Call destroy via reflection
        try {
            val destroyMethod = mpvViewRef?.javaClass?.getMethod("destroy")
            destroyMethod?.invoke(mpvViewRef)
            Log.d(TAG, "BaseMPVView.destroy() called")
        } catch (e: Exception) {
            try {
                val destroyMethod = MPVLib::class.java.getMethod("destroy")
                destroyMethod.invoke(null)
                Log.d(TAG, "MPVLib.destroy() called")
            } catch (e2: Exception) {
                Log.w(TAG, "Could not call destroy()", e2)
            }
        }
        Log.d(TAG, "Player destroyed — ready for fresh initialize on next open")
    }
}
