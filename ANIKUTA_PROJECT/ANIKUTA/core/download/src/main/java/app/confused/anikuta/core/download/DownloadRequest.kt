package app.confused.anikuta.core.download

import kotlinx.serialization.Serializable

/**
 * The input to [DownloadManager.enqueueDownload] — an ALREADY-RESOLVED video.
 *
 * **Why resolved (not a raw source/episode)?** Per ARCHITECTURE.md §3 module
 * boundaries, `:core:download` MUST NOT import `:feature:video-resolver`
 * (feature isolation — Rule §14). Video URL resolution is orchestrated by
 * `:app`'s `DownloadOrchestrator`, which calls `ResolverService.resolve()`,
 * picks the best [ResolverVideo], and hands the resolved URL + headers +
 * subtitle tracks to this module via [DownloadRequest]. This keeps the engine
 * pure and lets a future `OneDmDownloadManager` swap in without touching
 * resolution logic (ADR-020 future-proofing).
 *
 * The orchestrator auto-picks the preferred/highest-quality video so the
 * download button is a single tap (documented as a decision in the worklog;
 * a future enhancement could surface a quality picker).
 *
 * @param anime The anime identity (anilistId drives the folder structure).
 * @param episode The episode identity (episodeUrl is the offline-playback key).
 * @param videoUrl The direct video file URL to download (from ResolverVideo.url).
 * @param videoHeaders HTTP headers required by the video URL (Referer, User-Agent,
 *   etc.) — passed to OkHttp as request headers. Nullable/blank = none.
 * @param subtitleTracks ALL subtitle tracks to download alongside (per
 *   DOWNLOADS-PLAN: subtitles are ALWAYS downloaded, no user option).
 * @param audioTracks Optional audio tracks to download (stored alongside).
 * @param sourceId The source ID (for logging + future re-download).
 */
@Serializable
data class DownloadRequest(
    val anime: DownloadAnimeInfo,
    val episode: DownloadEpisodeInfo,
    val videoUrl: String,
    val videoHeaders: String? = null,
    val subtitleTracks: List<DownloadTrack> = emptyList(),
    val audioTracks: List<DownloadTrack> = emptyList(),
    val sourceId: Long = 0L,
    /** The server name (for UI display on the downloads page). */
    val videoServer: String = "",
    /** The quality label (e.g. "1080p") — for UI display. */
    val videoQuality: String = "",
    /** The audio version label (e.g. "SUB") — for UI display. */
    val videoAudio: String = "",
)
