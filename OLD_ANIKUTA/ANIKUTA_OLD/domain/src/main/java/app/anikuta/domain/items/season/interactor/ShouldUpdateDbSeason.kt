package app.anikuta.domain.items.season.interactor

import app.anikuta.domain.entries.anime.model.Anime

class ShouldUpdateDbSeason {
    fun await(dbSeason: Anime, sourceSeason: Anime): Boolean {
        return dbSeason.title != sourceSeason.title ||
            dbSeason.seasonNumber != sourceSeason.seasonNumber ||
            dbSeason.seasonSourceOrder != sourceSeason.seasonSourceOrder
    }
}
