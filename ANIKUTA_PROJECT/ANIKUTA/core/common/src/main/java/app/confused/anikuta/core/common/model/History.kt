package app.confused.anikuta.core.common.model

/** Domain model for a watch-history entry. */
data class History(
    val id: Long,
    val animeId: Long,
    val episodeId: Long,
    val seenAt: Long,
    val lastSecondSeen: Long,
)
