package app.anikuta.domain.history.anime.model

import app.anikuta.domain.entries.anime.model.AnimeCover
import java.util.Date

data class AnimeHistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val animeId: Long,
    val title: String,
    val episodeNumber: Double,
    val seenAt: Date?,
    val coverData: AnimeCover,
)
