package app.confused.anikuta.download

import android.util.Log
import app.confused.anikuta.core.download.DownloadAnimeInfo
import app.confused.anikuta.core.download.DownloadEpisodeInfo
import app.confused.anikuta.core.download.DownloadManager
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.DownloadRequest
import app.confused.anikuta.core.download.DownloadTrack
import app.confused.anikuta.core.download.FallbackStrategy
import app.confused.anikuta.core.download.TrackKind
import app.confused.anikuta.feature.videoresolver.ResolverResult
import app.confused.anikuta.feature.videoresolver.ResolverServer
import app.confused.anikuta.feature.videoresolver.ResolverVideo
import app.confused.anikuta.feature.videoresolver.ResolverService
import app.confused.anikuta.feature.videoresolver.SubtitleTrack
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * Bridges `:feature:video-resolver` and `:core:download`.
 *
 * **Why this lives in `:app` (not a feature/core module):** `:core:download`
 * cannot import `:feature:video-resolver` (Rule §14 — feature isolation), so
 * the resolve→select→enqueue orchestration happens here, where both are available.
 *
 * **Two modes (per the owner's spec):**
 *
 * 1. **Auto-download ON** (default): resolve → [selectBestVideo] picks the best
 *    (server, audio, quality) based on the user's [DownloadPreferences] priority
 *    lists → enqueue. Single tap, no picker.
 *    - If the preferred quality/audio aren't available, the [FallbackStrategy]
 *      applies (TRY_NEXT / ASK / DO_NOT_DOWNLOAD).
 *    - If the fallback is ASK, the result is [EnqueueResult.ShowPicker] → the
 *      host shows the video picker sheet.
 *
 * 2. **Auto-download OFF**: resolve → always return [EnqueueResult.ShowPicker]
 *    with the resolved servers → the host shows the picker sheet → the user
 *    picks → [enqueueSpecific] is called with the chosen video.
 *
 * All network work runs on `Dispatchers.IO` (ResolverService enforces it).
 *
 * @param resolver The shared [ResolverService] (also used by the watch flow).
 * @param manager The download manager (Koin-injected).
 * @param preferences Download preferences (auto-download + priority lists).
 */
class DownloadOrchestrator(
    private val resolver: ResolverService,
    private val manager: DownloadManager,
    private val preferences: DownloadPreferences,
) {

    /**
     * Resolve + enqueue a download (auto-download mode).
     *
     * @return [EnqueueResult] — Success, ShowPicker (auto-off or fallback=ASK),
     *   NoSources, or Error.
     */
    suspend fun enqueueDownload(
        anime: DownloadAnimeInfo,
        episode: SEpisode,
        source: AnimeSource,
    ): EnqueueResult {
        if (!manager.isFolderReady()) {
            Log.w(TAG, "enqueueDownload: no download folder configured")
            return EnqueueResult.Error("No download folder set. Open Downloads → settings to pick one.")
        }

        Log.i(TAG, "Resolving video for download: ${anime.title} EP ${episode.episode_number}")
        return try {
            when (val result = resolver.resolve(source, episode)) {
                is ResolverResult.Success -> {
                    if (result.servers.isEmpty()) return EnqueueResult.NoSources

                    // If auto-download is OFF, always show the picker.
                    if (!preferences.autoDownload().get()) {
                        return EnqueueResult.ShowPicker(
                            servers = result.servers,
                            anime = anime,
                            episode = episode,
                            source = source,
                        )
                    }

                    // Auto-download ON — select the best video.
                    val selection = selectBestVideo(source.id, result.servers)
                    when (selection) {
                        is Selection.Selected -> {
                            val request = buildRequest(anime, episode, source, selection)
                            val taskId = manager.enqueueDownload(request)
                            if (taskId < 0) {
                                EnqueueResult.Error("Failed to enqueue (invalid request).")
                            } else {
                                Log.i(TAG, "Enqueued: ${anime.title} EP ${episode.episode_number} " +
                                    "(${selection.serverName}/${selection.audioLabel}/${selection.video.quality})")
                                EnqueueResult.Success(taskId)
                            }
                        }
                        is Selection.NoMatch -> {
                            // Fallback ASK → show the picker.
                            if (preferences.qualityFallback().get() == FallbackStrategy.ASK ||
                                preferences.audioFallback().get() == FallbackStrategy.ASK) {
                                EnqueueResult.ShowPicker(result.servers, anime, episode, source)
                            } else if (preferences.qualityFallback().get() == FallbackStrategy.DO_NOT_DOWNLOAD) {
                                EnqueueResult.Error("No video matching your quality preferences. " +
                                    "Adjust your download settings or switch to manual mode.")
                            } else {
                                EnqueueResult.NoSources
                            }
                        }
                    }
                }
                is ResolverResult.NoSources -> EnqueueResult.NoSources
                is ResolverResult.Error -> EnqueueResult.Error(result.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download enqueue failed", e)
            EnqueueResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Enqueue a specific video (manual-pick mode — the user chose from the
     * picker sheet). [context] carries the anime/episode/source from the
     * resolve phase so we don't re-resolve.
     */
    suspend fun enqueueSpecific(
        video: ResolverVideo,
        serverName: String,
        audioLabel: String,
        context: PickerContext,
    ): EnqueueResult {
        if (!manager.isFolderReady()) {
            return EnqueueResult.Error("No download folder set.")
        }
        return try {
            val request = buildRequest(context.anime, context.episode, context.source,
                Selection.Selected(video, serverName, audioLabel))
            val taskId = manager.enqueueDownload(request)
            if (taskId < 0) {
                EnqueueResult.Error("Failed to enqueue (invalid request).")
            } else {
                Log.i(TAG, "Enqueued (manual): ${context.anime.title} EP ${context.episode.episode_number} " +
                    "($serverName/$audioLabel/${video.quality})")
                EnqueueResult.Success(taskId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "enqueueSpecific failed", e)
            EnqueueResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Selects the best (server, audio, quality) from [servers] based on the
     * user's preference lists. Pure logic (no I/O) — inline because it depends
     * on `ResolverServer` (which can't be imported by `:core:download`).
     */
    private fun selectBestVideo(sourceId: Long, servers: List<ResolverServer>): Selection {
        val qualityPrefs = preferences.qualityPreferences().get()
        val audioPrefs = preferences.audioPreferences().get()
        val serverPrefs = preferences.serverPreferences().get()[sourceId.toString()] ?: emptyList()

        val orderedServers = orderByName(servers, serverPrefs) { it.name }
        for (server in orderedServers) {
            val orderedAudios = orderByName(server.audioVersions, audioPrefs) { it.label }
            for (audio in orderedAudios) {
                val orderedVideos = orderByQuality(audio.videos, qualityPrefs)
                val match = orderedVideos.firstOrNull { matchesQuality(it, qualityPrefs) }
                if (match != null) {
                    Log.d(TAG, "Auto-picked: ${server.name} / ${audio.label} / ${match.quality}")
                    return Selection.Selected(match, server.name, audio.label)
                }
            }
        }
        // Fallback: TRY_NEXT → pick the first available.
        if (preferences.qualityFallback().get() == FallbackStrategy.TRY_NEXT) {
            val first = orderedServers.firstOrNull()?.let { s ->
                val a = s.audioVersions.firstOrNull()
                val v = a?.videos?.firstOrNull()
                if (v != null) Triple(s, a, v) else null
            }
            if (first != null) {
                val (s, a, v) = first
                Log.d(TAG, "Fallback (TRY_NEXT): ${s.name} / ${a.label} / ${v.quality}")
                return Selection.Selected(v, s.name, a.label)
            }
        }
        return Selection.NoMatch
    }

    private fun <T> orderByName(items: List<T>, prefs: List<String>, nameOf: (T) -> String): List<T> {
        if (prefs.isEmpty()) return items
        val prefOrder = prefs.withIndex().associate { it.value.uppercase() to it.index }
        return items.sortedBy { prefOrder[nameOf(it).uppercase()] ?: Int.MAX_VALUE }
    }

    private fun orderByQuality(videos: List<ResolverVideo>, qualityPrefs: List<String>): List<ResolverVideo> {
        if (qualityPrefs.isEmpty()) return videos
        val prefOrder = qualityPrefs.withIndex().associate { it.value.uppercase() to it.index }
        return videos.sortedBy { prefOrder[it.quality.uppercase()] ?: Int.MAX_VALUE }
    }

    private fun matchesQuality(video: ResolverVideo, qualityPrefs: List<String>): Boolean {
        if (qualityPrefs.isEmpty()) return true
        return qualityPrefs.any { it.equals(video.quality, ignoreCase = true) }
    }

    private fun buildRequest(
        anime: DownloadAnimeInfo,
        episode: SEpisode,
        source: AnimeSource,
        selection: Selection.Selected,
    ): DownloadRequest {
        val epInfo = DownloadEpisodeInfo(
            episodeUrl = episode.url,
            episodeNumber = episode.episode_number,
            name = episode.name,
            scanlator = episode.scanlator,
        )
        return DownloadRequest(
            anime = anime,
            episode = epInfo,
            videoUrl = selection.video.url,
            videoHeaders = selection.video.videoHeaders,
            subtitleTracks = selection.video.subtitleTracks.map { it.toDownloadTrack(TrackKind.SUBTITLE) },
            audioTracks = selection.video.audioTracks.map { it.toDownloadTrack(TrackKind.AUDIO) },
            sourceId = source.id,
            videoServer = selection.serverName,
            videoQuality = selection.video.quality,
            videoAudio = selection.audioLabel,
        )
    }

    private fun SubtitleTrack.toDownloadTrack(kind: TrackKind) =
        DownloadTrack(url = url, lang = lang, kind = kind)

    /** Internal selection result. */
    private sealed interface Selection {
        data class Selected(
            val video: ResolverVideo,
            val serverName: String,
            val audioLabel: String,
        ) : Selection
        data object NoMatch : Selection
    }

    companion object {
        private const val TAG = "AnikutaDownloadOrch"
    }
}

/** Context for the picker → enqueueSpecific flow (carries resolve results). */
data class PickerContext(
    val anime: DownloadAnimeInfo,
    val episode: SEpisode,
    val source: AnimeSource,
)

/** Result of [DownloadOrchestrator.enqueueDownload]. */
sealed interface EnqueueResult {
    data class Success(val taskId: Long) : EnqueueResult
    /** Auto-download is OFF or fallback=ASK — the host should show the picker sheet. */
    data class ShowPicker(
        val servers: List<ResolverServer>,
        val anime: DownloadAnimeInfo,
        val episode: SEpisode,
        val source: AnimeSource,
    ) : EnqueueResult
    data object NoSources : EnqueueResult
    data class Error(val message: String) : EnqueueResult
}
