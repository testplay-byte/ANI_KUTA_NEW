package app.anikuta.domain.source.anime.model

import app.anikuta.source.api.model.FetchType

data class DeletableAnime(
    val animeId: Long,
    val sourceId: Long,
    val fetchType: FetchType,
)
