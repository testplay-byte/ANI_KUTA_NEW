package app.confused.anikuta.core.player

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state holder for the player — used by both the watch page's mini-player
 * and the fullscreen player. This is a plain class (NOT a ViewModel) so it can
 * be owned by the screen-level composable and shared across mode switches
 * without recreation.
 *
 * Per ADR-025: the MPV view is never recreated on mode switches. This state
 * holder is also never recreated — it persists across MINIMIZED ↔ FULLSCREEN
 * transitions.
 *
 * The host (Activity / WatchScreen) pushes MPV events into this holder via
 * the `update*` methods. The UI observes the [StateFlow]s.
 */
class PlayerStateHolder {

    private val scope = CoroutineScope(SupervisorJob())

    // ── Player mode ──
    private val _playerMode = MutableStateFlow(PlayerMode.MINIMIZED)
    val playerMode: StateFlow<PlayerMode> = _playerMode.asStateFlow()

    // ── Loading / error ──
    private val _loadingState = MutableStateFlow(PlayerLoadingState.READY)
    val loadingState: StateFlow<PlayerLoadingState> = _loadingState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Playback state ──
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _bufferAheadTime = MutableStateFlow(0)
    val bufferAheadTime: StateFlow<Int> = _bufferAheadTime.asStateFlow()

    // ── Controls visibility ──
    private val _controlsVisible = MutableStateFlow(false)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _controlsLocked = MutableStateFlow(false)
    val controlsLocked: StateFlow<Boolean> = _controlsLocked.asStateFlow()

    // ── Episode list ──
    private val _episodeList = MutableStateFlow<List<EpisodeListItem>>(emptyList())
    val episodeList: StateFlow<List<EpisodeListItem>> = _episodeList.asStateFlow()

    private val _currentEpisodeIndex = MutableStateFlow(0)
    val currentEpisodeIndex: StateFlow<Int> = _currentEpisodeIndex.asStateFlow()

    private val _isSwitchingEpisode = MutableStateFlow(false)
    val isSwitchingEpisode: StateFlow<Boolean> = _isSwitchingEpisode.asStateFlow()

    // ── Tracks ──
    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<VideoTrack>> = _subtitleTracks.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks: StateFlow<List<VideoTrack>> = _audioTracks.asStateFlow()

    private val _currentSubtitleId = MutableStateFlow(-1)
    val currentSubtitleId: StateFlow<Int> = _currentSubtitleId.asStateFlow()

    private val _currentAudioId = MutableStateFlow(-1)
    val currentAudioId: StateFlow<Int> = _currentAudioId.asStateFlow()

    // ── Video resolution state ──
    private val _availableServers = MutableStateFlow<List<String>>(emptyList())
    val availableServers: StateFlow<List<String>> = _availableServers.asStateFlow()

    private val _currentServer = MutableStateFlow("")
    val currentServer: StateFlow<String> = _currentServer.asStateFlow()

    private val _availableAudioVersions = MutableStateFlow<List<String>>(emptyList())
    val availableAudioVersions: StateFlow<List<String>> = _availableAudioVersions.asStateFlow()

    private val _currentAudioVersion = MutableStateFlow("SUB")
    val currentAudioVersion: StateFlow<String> = _currentAudioVersion.asStateFlow()

    private val _currentVideoQuality = MutableStateFlow(-1)
    val currentVideoQuality: StateFlow<Int> = _currentVideoQuality.asStateFlow()

    private val _currentVideoUrl = MutableStateFlow("")
    val currentVideoUrl: StateFlow<String> = _currentVideoUrl.asStateFlow()

    private val _currentVideoTitle = MutableStateFlow("")
    val currentVideoTitle: StateFlow<String> = _currentVideoTitle.asStateFlow()

    // ── Resume prompt ──
    private val _showStartOverOverlay = MutableStateFlow(false)
    val showStartOverOverlay: StateFlow<Boolean> = _showStartOverOverlay.asStateFlow()

    // ── Mutators (called by the MPV host) ──

    fun setPlayerMode(mode: PlayerMode) { _playerMode.value = mode }
    fun setLoadingState(state: PlayerLoadingState) { _loadingState.value = state }
    fun setErrorMessage(msg: String?) { _errorMessage.value = msg }
    fun setPlaying(playing: Boolean) { _isPlaying.value = playing }
    fun setPosition(pos: Int) { _position.value = pos }
    fun setDuration(dur: Int) { _duration.value = dur }
    fun setBuffering(buffering: Boolean) { _buffering.value = buffering }
    fun setBufferAheadTime(time: Int) { _bufferAheadTime.value = time }
    fun setControlsVisible(visible: Boolean) { _controlsVisible.value = visible }
    fun setControlsLocked(locked: Boolean) { _controlsLocked.value = locked }
    fun setEpisodeList(list: List<EpisodeListItem>) { _episodeList.value = list }
    fun setCurrentEpisodeIndex(index: Int) { _currentEpisodeIndex.value = index }
    fun setSwitchingEpisode(switching: Boolean) { _isSwitchingEpisode.value = switching }
    fun setSubtitleTracks(tracks: List<VideoTrack>) { _subtitleTracks.value = tracks }
    fun setAudioTracks(tracks: List<VideoTrack>) { _audioTracks.value = tracks }
    fun setCurrentSubtitleId(id: Int) { _currentSubtitleId.value = id }
    fun setCurrentAudioId(id: Int) { _currentAudioId.value = id }
    fun setAvailableServers(servers: List<String>) { _availableServers.value = servers }
    fun setCurrentServer(server: String) { _currentServer.value = server }
    fun setAvailableAudioVersions(versions: List<String>) { _availableAudioVersions.value = versions }
    fun setCurrentAudioVersion(version: String) { _currentAudioVersion.value = version }
    fun setCurrentVideoQuality(quality: Int) { _currentVideoQuality.value = quality }
    fun setCurrentVideoUrl(url: String) { _currentVideoUrl.value = url }
    fun setCurrentVideoTitle(title: String) { _currentVideoTitle.value = title }
    fun setShowStartOverOverlay(show: Boolean) { _showStartOverOverlay.value = show }

    fun toggleControls() { _controlsVisible.value = !_controlsVisible.value }
    fun togglePlayPause() { /* handled by MPV host */ }

    companion object {
        private const val TAG = "AnikutaPlayerState"
    }
}

/**
 * Lightweight episode list item for the player's episode list.
 * Maps from [eu.kanade.tachiyomi.animesource.model.SEpisode].
 */
data class EpisodeListItem(
    val url: String,
    val name: String,
    val episodeNumber: Float,
    val scanlator: String?,
    val dateUpload: Long,
    val summary: String?,
    val thumbnailUrl: String?,
    val seen: Boolean = false,
)
