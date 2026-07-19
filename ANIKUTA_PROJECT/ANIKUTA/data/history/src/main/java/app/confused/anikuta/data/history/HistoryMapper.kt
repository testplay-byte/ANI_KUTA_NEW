package app.confused.anikuta.data.history

import app.confused.anikuta.core.common.model.History

/** Maps SQLDelight query results to the [History] domain model. */
object HistoryMapper {

    fun map(
        id: Long,
        animeId: Long,
        episodeId: Long,
        seenAt: Long,
        lastSecondSeen: Long,
    ): History = History(
        id = id,
        animeId = animeId,
        episodeId = episodeId,
        seenAt = seenAt,
        lastSecondSeen = lastSecondSeen,
    )
}
