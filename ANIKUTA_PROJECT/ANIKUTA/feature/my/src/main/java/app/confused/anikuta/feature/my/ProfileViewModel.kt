package app.confused.anikuta.feature.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.core.tracker.StatsCalculator
import app.confused.anikuta.core.tracker.TrackerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.util.Log

/**
 * ViewModel for the My Profile page.
 *
 * Observes [StatsCalculator.observeStats] for local stats and, when AniList
 * is linked, fetches enriched [TrackerUserStats] periodically.
 *
 * Per RULES §3: calls Repository/StatsCalculator only — never APIs directly.
 */
class ProfileViewModel(
    private val statsCalculator: StatsCalculator,
    private val trackerManager: TrackerManager,
    private val watchProgressStore: WatchProgressStore,
    private val preferences: ProfilePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState(isLoading = true))
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        observeLocalStats()
        observeAniListState()
        observePreferences()
    }

    /** Observe local stats (library + watch progress). Always active. */
    private fun observeLocalStats() {
        viewModelScope.launch {
            statsCalculator.observeStats().collectLatest { stats ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    localStats = stats,
                )
            }
        }
    }

    /** Observe AniList login state + fetch enriched stats when linked. */
    private fun observeAniListState() {
        viewModelScope.launch {
            trackerManager.anilist.username.collectLatest { username ->
                val linked = username != null
                _state.value = _state.value.copy(
                    isAniListLinked = linked,
                    anilistUsername = username,
                )
                if (linked) {
                    fetchAniListAvatar()
                    fetchAniListStats()
                } else {
                    _state.value = _state.value.copy(
                        anilistAvatarUrl = null,
                        anilistStats = null,
                    )
                }
            }
        }
    }

    /** Observe user preferences (display name, avatar, stats source). */
    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                preferences.displayName.changes(),
                preferences.displayAvatarUrl.changes(),
                preferences.useTrackerStats.changes(),
            ) { name, avatar, useTracker ->
                Triple(name, avatar, useTracker)
            }.collectLatest { (name, avatar, useTracker) ->
                _state.value = _state.value.copy(
                    displayName = name,
                    displayAvatarUrl = avatar,
                    useTrackerStats = useTracker,
                )
            }
        }
    }

    /** Fetch the AniList avatar URL. */
    private fun fetchAniListAvatar() {
        viewModelScope.launch {
            statsCalculator.observeAniListAvatar().collectLatest { avatar ->
                _state.value = _state.value.copy(anilistAvatarUrl = avatar)
            }
        }
    }

    /** Fetch enriched AniList stats (one-shot; re-fetched on refresh). */
    fun fetchAniListStats() {
        viewModelScope.launch {
            try {
                val stats = statsCalculator.fetchAniListStats()
                _state.value = _state.value.copy(anilistStats = stats)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch AniList stats", e)
            }
        }
    }

    /**
     * Refresh all stats (called by pull-to-refresh).
     *
     * Sets [ProfileState.isRefreshing] to true while the refresh is in progress,
     * then re-fetches AniList stats (if linked). Local stats auto-update via
     * the reactive Flow in [observeLocalStats].
     */
    fun refresh() {
        if (_state.value.isRefreshing) return // already refreshing
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            try {
                if (trackerManager.anilist.isLoggedIn) {
                    val stats = statsCalculator.fetchAniListStats()
                    _state.value = _state.value.copy(anilistStats = stats)
                }
                // Local stats auto-update via Flow; brief delay so the indicator is visible
                kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    /** Set the display name (user-customizable). */
    fun setDisplayName(name: String) {
        preferences.displayName.set(name)
    }

    /** Set the display avatar URL (user-customizable). */
    fun setDisplayAvatarUrl(url: String) {
        preferences.displayAvatarUrl.set(url)
    }

    /** Set whether to use tracker stats or local stats. */
    fun setUseTrackerStats(use: Boolean) {
        preferences.useTrackerStats.set(use)
    }

    /** Reset watch progress data (called by the reset-stats dialog). */
    fun resetWatchHistory() {
        viewModelScope.launch {
            watchProgressStore.deleteAll()
            Log.i(TAG, "Watch history reset")
        }
    }

    companion object {
        private const val TAG = "AnikutaProfileVM"
    }
}
