package app.confused.anikuta.feature.animedetails

import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata

/**
 * Context passed from the anime-details page to the watch page when the user
 * taps an episode. Carries the anime title + cover URL (for the watch page's
 * header + dynamic theming) and the episode-metadata map (for rich episode-row
 * rendering: titles, descriptions, thumbnails, air dates).
 *
 * This was added because the watch page was previously NOT receiving any of
 * this data — `animeTitle` was hardcoded to `""`, `coverUrl` to `null`, and
 * `EpisodeMetadata` was dropped at the details→watch boundary.
 *
 * Per user: "the meta data of the episode which the user wants to play does
 * not get shared to the watch page."
 */
data class WatchEpisodeContext(
    val animeTitle: String,
    val coverUrl: String?,
    val episodeMetadata: Map<Int, EpisodeMetadata> = emptyMap(),
)
