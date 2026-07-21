package app.confused.anikuta.feature.watch

import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * The data needed to launch the watch page. Constructed by the host
 * (MainActivity) from the resolver video + anime context + episode list.
 *
 * Per ADR-012: the watch page sits between the anime details page and the
 * fullscreen player. This data class threads ALL context the watch page
 * needs — the video URL, anime metadata (for theming + history), the episode
 * list (for switching), and the source (for re-resolution).
 */
data class WatchRequest(
    val videoUrl: String,
    val videoHeaders: String?,
    val videoTitle: String,
    val anilistId: Int,
    val animeTitle: String,
    val coverUrl: String?,
    val coverColor: Int?,
    val episodeUrl: String,
    val episodeNumber: Float,
    val sourceId: Long,
    val videoServer: String,
    val videoAudio: String,
    val videoQuality: Int,
    val episodeList: List<SEpisode>,
)
