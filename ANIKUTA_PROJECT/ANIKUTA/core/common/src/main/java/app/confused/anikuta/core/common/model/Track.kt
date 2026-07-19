package app.confused.anikuta.core.common.model

/** Domain model for a tracker binding (anime ↔ tracker service). */
data class Track(
    val id: Long,
    val animeId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val remoteUrl: String?,
    val lastSeen: Long,
    val score: Float,
    val status: Int,
    val totalEpisodes: Int,
    val displayScore: String?,
)
