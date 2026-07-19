package app.anikuta.domain.items.episode.interactor

import app.anikuta.core.util.lang.withNonCancellableContext
import app.anikuta.domain.entries.anime.interactor.GetAnimeFavorites
import app.anikuta.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import app.anikuta.domain.entries.anime.model.Anime
import app.anikuta.domain.library.service.LibraryPreferences

class SetAnimeDefaultEpisodeFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val getFavorites: GetAnimeFavorites,
) {

    suspend fun await(anime: Anime) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setAnimeEpisodeFlags.awaitSetAllFlags(
                    animeId = anime.id,
                    unseenFilter = filterEpisodeBySeen().get(),
                    downloadedFilter = filterEpisodeByDownloaded().get(),
                    bookmarkedFilter = filterEpisodeByBookmarked().get(),
                    fillermarkedFilter = filterEpisodeByFillermarked().get(),
                    sortingMode = sortEpisodeBySourceOrNumber().get(),
                    sortingDirection = sortEpisodeByAscendingOrDescending().get(),
                    displayMode = displayEpisodeByNameOrNumber().get(),
                    showPreviews = showEpisodeThumbnailPreviews().get(),
                    showSummaries = showEpisodeSummaries().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
