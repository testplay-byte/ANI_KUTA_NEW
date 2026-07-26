package app.confused.anikuta.core.download

import kotlinx.serialization.Serializable

/**
 * Lightweight anime identity for downloads.
 *
 * ANIKUTA is AniList-first (ADR-010), so downloads are keyed by [anilistId].
 * The [title] + [coverUrl] are carried for the Downloads screen UI (cover +
 * name) and for the AniList-first folder name (`Anime Title [anilistId]`).
 *
 * **Design decision (vs. the prompt's `Anime` type):** the implementation
 * prompt's `DownloadManager` interface used `anime: Anime`, but ANIKUTA has no
 * single shared `Anime` domain model used everywhere (the watch flow threads
 * `anilistId/title/coverUrl` directly via `WatchRequest`). Introducing a
 * dedicated, minimal DTO here keeps `:core:download` decoupled from any
 * specific data layer and makes the interface stable even if the anime model
 * changes upstream. This is more modular + future-proof (Rule §8).
 *
 * @param anilistId The AniList ID — the primary key for the folder structure.
 * @param title English/romaji title (from AniList) for the folder name + UI.
 * @param coverUrl Cover image URL for the Downloads screen thumbnail.
 * @param coverColor Optional dominant cover color (for theming); nullable.
 */
@Serializable
data class DownloadAnimeInfo(
    val anilistId: Int,
    val title: String,
    val coverUrl: String? = null,
    val coverColor: Int? = null,
)

/**
 * A subtitle or audio track to download alongside the video.
 *
 * Mirrors `eu.kanade.tachiyomi.animesource.model.Track(url, lang)`. We use our
 * own serializable type so `:core:download` does not leak the source-api `Track`
 * into its persisted store (the store is JSON; keeping our own type means a
 * source-api change can't break saved queues).
 *
 * @param url The remote URL of the track file.
 * @param lang Human-readable language label (e.g. "English", "Japanese").
 * @param kind Whether this is a SUBTITLE or AUDIO track.
 */
@Serializable
data class DownloadTrack(
    val url: String,
    val lang: String = "",
    val kind: TrackKind = TrackKind.SUBTITLE,
)

@Serializable
enum class TrackKind { SUBTITLE, AUDIO }

/**
 * Episode identity for a download.
 *
 * Carries the [SEpisode]-equivalent fields the engine needs (the source-api
 * `SEpisode` is NOT serializable in our store format, so we mirror the relevant
 * fields). The [episodeUrl] is the stable key for offline-playback lookup.
 *
 * @param episodeUrl The source episode URL — stable key (matches WatchRequest.episodeUrl).
 * @param episodeNumber The episode number (float; .5 = special). Drives the
 *   `Episode NNN` folder name (zero-padded 3-digit, floored).
 * @param name The episode display name (for the Downloads screen + metadata.json).
 * @param scanlator The scanlator/scanlator string (audio-version hint), optional.
 */
@Serializable
data class DownloadEpisodeInfo(
    val episodeUrl: String,
    val episodeNumber: Float,
    val name: String,
    val scanlator: String? = null,
)

/**
 * A completed, on-disk downloaded episode — returned by
 * [DownloadManager.getDownloadedEpisodes] for the Downloads screen.
 *
 * @param episode The episode identity.
 * @param videoUri The content:// URI of the downloaded video (playable by MPV
 *   via `resolveUrlForMpv`).
 * @param subtitleUris The content:// URIs of downloaded subtitle files.
 * @param sizeBytes Total size of the episode folder on disk (video + subtitles).
 * @param completedAt Epoch millis when the download finished.
 */
data class DownloadedEpisode(
    val episode: DownloadEpisodeInfo,
    val videoUri: String,
    val subtitleUris: List<String>,
    val sizeBytes: Long,
    val completedAt: Long,
)
