package app.confused.anikuta.feature.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.tracker.TrackerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the Trackers settings page.
 *
 * Observes the login state of all trackers via [TrackerManager].
 * Provides login/logout actions.
 *
 * Per RULES §3: calls TrackerManager only — never APIs directly.
 */
class TrackersViewModel(
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackersState(isLoading = true))
    val state: StateFlow<TrackersState> = _state.asStateFlow()

    init {
        observeTrackers()
    }

    /** Observe all trackers' login state. */
    private fun observeTrackers() {
        combine(
            trackerManager.anilist.username,
            trackerManager.mal.username,
        ) { anilistUser, malUser ->
            buildList {
                add(TrackerUiState(
                    id = TrackerManager.ANILIST_ID,
                    name = "AniList",
                    isLoggedIn = anilistUser != null,
                    username = anilistUser,
                ))
                add(TrackerUiState(
                    id = TrackerManager.MAL_ID,
                    name = "MyAnimeList",
                    isLoggedIn = malUser != null,
                    username = malUser,
                ))
            }
        }.onEach { trackers ->
            _state.value = TrackersState(trackers = trackers, isLoading = false)
        }.launchIn(viewModelScope)
    }

    /** Returns the OAuth login URL for a tracker. */
    fun getAuthUrl(trackerId: Int): String? {
        val tracker = trackerManager.getTracker(trackerId) ?: return null
        return tracker.getAuthUrl()
    }

    /** Logs out of a tracker. */
    fun logout(trackerId: Int) {
        val tracker = trackerManager.getTracker(trackerId) ?: return
        viewModelScope.launch {
            tracker.logout()
        }
    }
}
