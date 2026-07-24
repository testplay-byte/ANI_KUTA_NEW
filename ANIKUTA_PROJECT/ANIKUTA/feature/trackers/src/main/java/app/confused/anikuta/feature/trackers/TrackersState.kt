package app.confused.anikuta.feature.trackers

/** UI state for a single tracker in the Trackers settings page. */
data class TrackerUiState(
    val id: Int,
    val name: String,
    val isLoggedIn: Boolean,
    val username: String?,
)

/** Overall UI state for the Trackers settings page. */
data class TrackersState(
    val trackers: List<TrackerUiState> = emptyList(),
    val isLoading: Boolean = true,
)
