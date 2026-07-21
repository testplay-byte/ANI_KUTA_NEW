package app.confused.anikuta.feature.watch

import app.confused.anikuta.feature.videoresolver.ResolverServer
import app.confused.anikuta.feature.videoresolver.SubtitleTrack
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * The data needed to launch the watch page. Constructed by the host
 * (MainActivity) from the resolver video + anime context + episode list.
 *
 * Per ADR-012: the watch page sits between the anime details page and the
 * fullscreen player. This data class threads ALL context the watch page
 * needs — the video URL, anime metadata (for theming + history), the episode
 * list (for switching), the source (for re-resolution), subtitle/audio tracks
 * (for external track loading), and the resolved servers (for quality switching).
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
    val source: AnimeSource? = null,
    val videoServer: String,
    val videoAudio: String,
    val videoQuality: Int,
    val episodeList: List<SEpisode>,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<SubtitleTrack> = emptyList(),
    val resolvedServers: List<ResolverServer> = emptyList(),
)
