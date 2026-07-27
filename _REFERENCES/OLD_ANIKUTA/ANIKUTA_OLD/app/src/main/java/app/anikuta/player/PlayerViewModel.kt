package app.anikuta.player

import android.util.Log
import androidx.lifecycle.ViewModel
import app.anikuta.source.api.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ANI-KUTA PlayerViewModel — holds the player UI state.
 *
 * Phase 1 extensions:
 *  - [playerMode]: MINIMIZED or FULLSCREEN
 *  - [episodeList]: episodes from the matched source (for the episodes list view)
 *  - [currentEpisodeIndex]: which episode is currently playing
 *  - [isSwitchingEpisode]: loading state when switching episodes
 *  - [subtitleTracks] / [audioTracks]: track lists from MPV
 *  - [currentSubtitleId] / [currentAudioId]: selected track IDs
 *
 * This VM intentionally does NOT touch MPV directly; the Activity owns the MPV
 * view (it must run on the UI thread + manage the native surface). The VM just
 * exposes a state surface the Compose controls observe.
 */
class PlayerViewModel(
    val videoUrl: String,
    val title: String,
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _loadingState = MutableStateFlow(PlayerLoadingState.INITIALIZING)
    val loadingState: StateFlow<PlayerLoadingState> = _loadingState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    /** P2b: Demuxer cache time — how far ahead the video is buffered (seconds).
     *  Used by the seekbar to draw a buffer-ahead segment. */
    private val _bufferAheadTime = MutableStateFlow(0)
    val bufferAheadTime: StateFlow<Int> = _bufferAheadTime.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    /** Whether controls are locked (all input disabled except unlock). */
    private val _controlsLocked = MutableStateFlow(false)
    val controlsLocked: StateFlow<Boolean> = _controlsLocked.asStateFlow()

    /** Whether the lock button itself is visible (auto-hides on inactivity). */
    private val _lockButtonVisible = MutableStateFlow(false)
    val lockButtonVisible: StateFlow<Boolean> = _lockButtonVisible.asStateFlow()

    /** Shows "Do you want to start over?" overlay for 10s after resume (Q8). */
    private val _showStartOverOverlay = MutableStateFlow(false)
    val showStartOverOverlay: StateFlow<Boolean> = _showStartOverOverlay.asStateFlow()

    // ---- Phase 1: Player mode + episode list + tracks ----

    /** Current display mode: minimized or fullscreen. */
    private val _playerMode = MutableStateFlow(PlayerMode.MINIMIZED)
    val playerMode: StateFlow<PlayerMode> = _playerMode.asStateFlow()

    /** Episodes from the matched source (for the episodes list in minimized mode). */
    private val _episodeList = MutableStateFlow<List<SEpisode>>(emptyList())
    val episodeList: StateFlow<List<SEpisode>> = _episodeList.asStateFlow()

    /** Index of the currently playing episode in [episodeList]. */
    private val _currentEpisodeIndex = MutableStateFlow(0)
    val currentEpisodeIndex: StateFlow<Int> = _currentEpisodeIndex.asStateFlow()

    /** True while switching to a different episode (shows loading on video). */
    private val _isSwitchingEpisode = MutableStateFlow(false)
    val isSwitchingEpisode: StateFlow<Boolean> = _isSwitchingEpisode.asStateFlow()

    /** What kind of switch is happening — controls the loading overlay style.
     *  - "episode": full overlay with episode thumbnail (episode switch)
     *  - "quality": semi-transparent overlay on top of frozen video frame (quality/server/audio switch)
     *  - "initial": full overlay with episode thumbnail (player entry) */
    private val _switchType = MutableStateFlow("initial")
    val switchType: StateFlow<String> = _switchType.asStateFlow()

    /** Available subtitle tracks from MPV. */
    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<VideoTrack>> = _subtitleTracks.asStateFlow()

    /** Available audio tracks from MPV. */
    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks: StateFlow<List<VideoTrack>> = _audioTracks.asStateFlow()

    /** Currently selected subtitle track ID (-1 = off). */
    private val _currentSubtitleId = MutableStateFlow(-1)
    val currentSubtitleId: StateFlow<Int> = _currentSubtitleId.asStateFlow()

    /** Currently selected audio track ID. */
    private val _currentAudioId = MutableStateFlow(-1)

    // ---- Subtitle status indicator (player-experiment branch) ----
    // Tracks the subtitle pipeline state so the UI can show a temporary pill:
    //   IDLE → DOWNLOADING → LOADED → ON  (or OFF / NONE / ERROR)
    // The pill auto-fades after a few seconds (handled in PlayerScreen).

    /** Subtitle pipeline status for the on-screen indicator. */
    enum class SubtitleStatus {
        IDLE,           // no subs requested yet
        DOWNLOADING,    // sub-add sent, waiting for .vtt to download + parse
        LOADED,         // track added to MPV's track-list
        ON,             // sid > 0, subtitles rendering
        OFF,            // user turned off (or mode=off)
        NONE,           // no subtitle tracks available for this episode
        ERROR,          // subtitle download/parse failed
    }

    private val _subtitleStatus = MutableStateFlow(SubtitleStatus.IDLE)
    val subtitleStatus: StateFlow<SubtitleStatus> = _subtitleStatus.asStateFlow()

    /** Human-readable detail for the subtitle status pill (e.g. track name, error). */
    private val _subtitleStatusDetail = MutableStateFlow("")
    val subtitleStatusDetail: StateFlow<String> = _subtitleStatusDetail.asStateFlow()

    /** Monotonic counter — bumped every time the status changes, so the UI can
     *  re-trigger its auto-fade animation even when the status value is the same. */
    private val _subtitleStatusTick = MutableStateFlow(0L)
    val subtitleStatusTick: StateFlow<Long> = _subtitleStatusTick.asStateFlow()

    fun setSubtitleStatus(status: SubtitleStatus, detail: String = "") {
        _subtitleStatus.value = status
        _subtitleStatusDetail.value = detail
        _subtitleStatusTick.value = _subtitleStatusTick.value + 1
    }

    val currentAudioId: StateFlow<Int> = _currentAudioId.asStateFlow()

    /** Available servers (from video resolution). */
    private val _availableServers = MutableStateFlow<List<String>>(emptyList())
    val availableServers: StateFlow<List<String>> = _availableServers.asStateFlow()

    /** Currently selected server. */
    private val _currentServer = MutableStateFlow("")
    val currentServer: StateFlow<String> = _currentServer.asStateFlow()

    // ---- Parts 2+3+4: Video selection state ----
    // These hold the resolved video list for the current episode + the
    // currently-selected server/audio/quality. They drive the Quality sheet,
    // Server dropdown/sheet, and Audio version dropdown.

    /** All resolved videos for the current episode (for the Quality sheet). */
    private val _availableVideos = MutableStateFlow<List<Video>>(emptyList())
    val availableVideos: StateFlow<List<Video>> = _availableVideos.asStateFlow()

    /** All audio versions available for the current episode (e.g. ["SUB","DUB"]). */
    private val _availableAudioVersions = MutableStateFlow<List<String>>(emptyList())
    val availableAudioVersions: StateFlow<List<String>> = _availableAudioVersions.asStateFlow()

    /** Currently selected audio version (e.g. "SUB", "DUB", "HSUB", "ANY"). */
    private val _currentAudioVersion = MutableStateFlow("SUB")
    val currentAudioVersion: StateFlow<String> = _currentAudioVersion.asStateFlow()

    /** Currently selected video quality (e.g. 1080, 720, -1 = unknown). */
    private val _currentVideoQuality = MutableStateFlow(-1)
    val currentVideoQuality: StateFlow<Int> = _currentVideoQuality.asStateFlow()

    /**
     * Currently loaded video URL. FIX (L2): This was previously an Activity
     * `@Volatile var` read by the QualitySheet, but changes to it did not
     * trigger Compose recomposition — so the "current quality" highlight in
     * the sheet was stale until the sheet was re-opened. Hoisting it into the
     * ViewModel as a StateFlow makes the highlight reactive to
     * switchServer / switchAudioVersion / switchQuality / switchEpisode calls.
     */
    private val _currentVideoUrl = MutableStateFlow("")
    val currentVideoUrl: StateFlow<String> = _currentVideoUrl.asStateFlow()

    /** Current video title — stable across re-resolutions (unlike URL which
     *  contains localhost:PORT). Used for quality sheet highlight. */
    private val _currentVideoTitle = MutableStateFlow("")
    val currentVideoTitle: StateFlow<String> = _currentVideoTitle.asStateFlow()

    // ---- Mode switching ----

    fun setPlayerMode(mode: PlayerMode) {
        _playerMode.value = mode
    }

    fun togglePlayerMode() {
        _playerMode.value = if (_playerMode.value == PlayerMode.MINIMIZED) {
            PlayerMode.FULLSCREEN
        } else {
            PlayerMode.MINIMIZED
        }
    }

    // ---- Episode list management ----

    fun setEpisodeList(episodes: List<SEpisode>, currentIndex: Int = 0) {
        _episodeList.value = episodes
        _currentEpisodeIndex.value = currentIndex
    }

    fun setCurrentEpisodeIndex(index: Int) {
        _currentEpisodeIndex.value = index
    }

    fun setSwitchingEpisode(switching: Boolean, type: String = "episode") {
        _isSwitchingEpisode.value = switching
        if (switching) {
            _switchType.value = type
        }
    }

    // ---- Track management ----

    fun setSubtitleTracks(tracks: List<VideoTrack>) {
        _subtitleTracks.value = tracks
    }

    fun setAudioTracks(tracks: List<VideoTrack>) {
        _audioTracks.value = tracks
    }

    fun setCurrentSubtitleId(id: Int) {
        _currentSubtitleId.value = id
    }

    fun setCurrentAudioId(id: Int) {
        _currentAudioId.value = id
    }

    // ---- Server management ----

    fun setAvailableServers(servers: List<String>, current: String) {
        _availableServers.value = servers
        _currentServer.value = current
    }

    fun setCurrentServer(server: String) {
        _currentServer.value = server
    }

    // ---- Parts 2+3+4: Video selection setters ----

    fun setAvailableVideos(videos: List<Video>) {
        _availableVideos.value = videos
    }

    fun setAvailableAudioVersions(versions: List<String>, current: String) {
        _availableAudioVersions.value = versions
        _currentAudioVersion.value = current
    }

    fun setCurrentAudioVersion(version: String) {
        _currentAudioVersion.value = version
    }

    fun setCurrentVideoQuality(quality: Int) {
        _currentVideoQuality.value = quality
    }

    /** FIX (L2): Update the current video URL StateFlow. */
    fun setCurrentVideoUrl(url: String) {
        _currentVideoUrl.value = url
    }

    fun setCurrentVideoTitle(title: String) {
        _currentVideoTitle.value = title
    }

    // ---- Original state mutations (from Activity's MPV observer) ----

    /** Set by the Activity when playback resumes from a saved position. */
    fun triggerStartOverOverlay() {
        _showStartOverOverlay.value = true
    }

    /** Called when the overlay auto-dismisses (10s) or the user taps "start over". */
    fun dismissStartOverOverlay() {
        _showStartOverOverlay.value = false
    }

    fun onFileLoaded() {
        Log.d(TAG, "File loaded")
        _loadingState.value = PlayerLoadingState.READY
    }

    fun onPositionUpdate(seconds: Int) {
        _position.value = seconds
    }

    fun onDurationUpdate(seconds: Int) {
        if (seconds > 0) _duration.value = seconds
    }

    fun onPauseChanged(paused: Boolean) {
        _isPlaying.value = !paused
    }

    fun onVolumeUpdate(value: Int) {
        _volume.value = value
    }

    /** P2b: Update the buffer-ahead time from demuxer-cache-time. */
    fun onBufferAheadUpdate(cacheTime: Int) {
        _bufferAheadTime.value = cacheTime
    }

    fun onBufferingChanged(buffering: Boolean) {
        _buffering.value = buffering
    }

    fun onError(message: String) {
        if (message.isBlank()) {
            // Clear error state — back to READY (or INITIALIZING if never ready)
            _errorMessage.value = null
            if (_loadingState.value == PlayerLoadingState.ERROR) {
                _loadingState.value = PlayerLoadingState.READY
            }
            Log.d(TAG, "Error cleared")
        } else {
            Log.e(TAG, "Player error: $message")
            _loadingState.value = PlayerLoadingState.ERROR
            _errorMessage.value = message
        }
    }

    /** Clear the error state without a message. */
    fun clearError() {
        _errorMessage.value = null
        if (_loadingState.value == PlayerLoadingState.ERROR) {
            _loadingState.value = PlayerLoadingState.READY
        }
    }

    fun toggleControls() {
        _controlsVisible.value = !_controlsVisible.value
    }

    fun setControlsVisible(visible: Boolean) {
        _controlsVisible.value = visible
    }

    // ---- Lock controls (Phase 2.2) ----

    /** Lock all controls. Only the lock button can unlock. */
    fun lockControls() {
        _controlsLocked.value = true
        _controlsVisible.value = false
        _lockButtonVisible.value = false
    }

    /** Unlock controls — restores normal control visibility. */
    fun unlockControls() {
        _controlsLocked.value = false
        _controlsVisible.value = true
        _lockButtonVisible.value = false
    }

    /** Toggle lock state. */
    fun toggleLock() {
        if (_controlsLocked.value) unlockControls() else lockControls()
    }

    /** Show the lock button (when user taps screen while locked). */
    fun showLockButton() {
        _lockButtonVisible.value = true
    }

    /** Hide the lock button (auto-hide on inactivity). */
    fun hideLockButton() {
        _lockButtonVisible.value = false
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }

    // ---- Download support (Phase: PLAYER-DL-BTN) ----
    //
    // Mirrors DetailViewModel's wiring of the global DownloadManager so the
    // player's episode list can render a per-episode download button that
    // reflects the live download queue state (DOWNLOADING / QUEUE / ERROR /
    // PAUSED / DOWNLOADED / etc.) and progress (0–100).
    //
    // IMPORTANT: the player does NOT have the anime's full filesystem-scan
    // path that the detail page uses (no matchedSource / anime title here).
    // [downloadedOnDisk] is therefore a stub that always reports an empty
    // set. The on-disk green-checkmark for an episode that finished outside
    // the player won't be reflected here, but the in-queue DOWNLOADED state
    // will be — and that's enough to drive the button UI.
    //
    // onDownloadClick is also a no-op stub for now. Enqueueing a download
    // requires the anime title + source (which the player only has
    // indirectly via sourceId/anilistId on PlayerActivity). Wiring that
    // through is intentionally deferred — the priority for this phase is to
    // SHOW the button with correct state. The click handler is a TODO.

    private val downloadManager: app.anikuta.download.DownloadManager? = try {
        uy.kohesive.injekt.Injekt.get()
    } catch (e: Exception) { null }

    /** Live download status per episode URL (mirrors DetailViewModel.downloadStatus). */
    val downloadStatus: kotlinx.coroutines.flow.StateFlow<Map<String, app.anikuta.download.Download.State>> =
        downloadManager?.downloadStatusMap
            ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

    /** Live download progress per episode URL, 0–100 (mirrors DetailViewModel.downloadProgress). */
    val downloadProgress: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> =
        downloadManager?.downloadProgressMap
            ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

    /**
     * Stub on-disk set — always empty in the player. See note above.
     * Exposed so the same UI code path as the detail page can be reused.
     */
    private val _downloadedOnDisk = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val downloadedOnDisk: kotlinx.coroutines.flow.StateFlow<Set<String>> = _downloadedOnDisk
}
