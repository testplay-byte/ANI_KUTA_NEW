package app.confused.anikuta.download

import android.util.Log
import app.confused.anikuta.core.download.DownloadAnimeInfo
import app.confused.anikuta.core.download.DownloadEpisodeInfo
import app.confused.anikuta.core.download.DownloadManager
import app.confused.anikuta.core.download.DownloadPreferences
import app.confused.anikuta.core.download.DownloadRequest
import app.confused.anikuta.core.download.DownloadTrack
import app.confused.anikuta.core.download.FallbackStrategy
import app.confused.anikuta.core.download.ServerDiscoveryStore
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
    private val serverDiscovery: ServerDiscoveryStore,
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

                    // ── Record discovered server names for this source ──
                    // Passively builds the per-source server list so the user
                    // can configure server preferences in Download Settings.
                    serverDiscovery.recordServers(source.id, result.servers.map { it.name })

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
                            // Check WHY there was no match:
                            val audioFallback = preferences.audioFallback().get()
                            val qualityFallback = preferences.qualityFallback().get()
                            when {
                                // ASK → show the picker so the user can choose manually
                                audioFallback == FallbackStrategy.ASK || qualityFallback == FallbackStrategy.ASK -> {
                                    Log.d(TAG, "No preferred match → showing picker (ASK)")
                                    EnqueueResult.ShowPicker(result.servers, anime, episode, source)
                                }
                                // DO_NOT_DOWNLOAD → tell the user their prefs weren't met
                                audioFallback == FallbackStrategy.DO_NOT_DOWNLOAD -> {
                                    EnqueueResult.Error("No audio version matching your preferences " +
                                        "(${preferences.audioPreferences().get()}). " +
                                        "Adjust your download settings or switch to manual mode.")
                                }
                                qualityFallback == FallbackStrategy.DO_NOT_DOWNLOAD -> {
                                    EnqueueResult.Error("No quality matching your preferences " +
                                        "(${preferences.qualityPreferences().get()}). " +
                                        "Adjust your download settings or switch to manual mode.")
                                }
                                // TRY_NEXT but nothing was available at all
                                else -> EnqueueResult.NoSources
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
     * user's preference lists + fallback strategies.
     *
     * **Algorithm (respects preferences + fallbacks):**
     *
     * 1. **Try preferred audio + preferred quality**: iterate servers (by
     *    preference order), then audios (by preference order), then qualities
     *    (by preference order). For each audio that IS in the user's audio
     *    preference list, check if any quality IS in the user's quality
     *    preference list. First match → Selected.
     *
     * 2. **If no preferred audio+quality match**: check the fallback strategies:
     *    - **Audio fallback**:
     *      - TRY_NEXT → proceed to step 3 (try preferred quality with ANY audio)
     *      - ASK → return NoMatch (the caller will show the picker)
     *      - DO_NOT_DOWNLOAD → return NoMatch (the caller will show an error)
     *    - **Quality fallback**:
     *      - TRY_NEXT → proceed to step 3 (try preferred audio with ANY quality)
     *      - ASK → return NoMatch (the caller will show the picker)
     *      - DO_NOT_DOWNLOAD → return NoMatch (the caller will show an error)
     *
     * 3. **Fallback (TRY_NEXT for both)**: pick the first available server /
     *    audio / quality (best-effort). This is the "just download something"
     *    behavior.
     *
     * 4. If nothing is available at all → NoMatch.
     */
    private fun selectBestVideo(sourceId: Long, servers: List<ResolverServer>): Selection {
        val qualityPrefs = preferences.qualityPreferences().get()
        val audioPrefs = preferences.audioPreferences().get()
        val serverPrefs = preferences.serverPreferences().get()[sourceId.toString()] ?: emptyList()
        val audioFallback = preferences.audioFallback().get()
        val qualityFallback = preferences.qualityFallback().get()

        Log.d(TAG, "selectBestVideo: qualityPrefs=$qualityPrefs, audioPrefs=$audioPrefs, " +
            "audioFallback=$audioFallback, qualityFallback=$qualityFallback")

        val orderedServers = orderByName(servers, serverPrefs) { it.name }

        // ── Step 1: Try preferred audio + preferred quality ──
        for (server in orderedServers) {
            val orderedAudios = orderByName(server.audioVersions, audioPrefs) { it.label }
            for (audio in orderedAudios) {
                // Only consider audios that are in the user's preference list.
                if (!matchesAudio(audio.label, audioPrefs)) continue

                val orderedVideos = orderByQuality(audio.videos, qualityPrefs)
                val match = orderedVideos.firstOrNull { matchesQuality(it, qualityPrefs) }
                if (match != null) {
                    Log.d(TAG, "Auto-picked (preferred): ${server.name} / ${audio.label} / ${match.quality}")
                    return Selection.Selected(match, server.name, audio.label)
                }
            }
        }

        // ── Step 2: No preferred audio+quality match — check fallbacks ──
        // If EITHER fallback is ASK → show the picker.
        if (audioFallback == FallbackStrategy.ASK || qualityFallback == FallbackStrategy.ASK) {
            Log.d(TAG, "No preferred match + fallback=ASK → showing picker")
            return Selection.NoMatch
        }
        // If EITHER fallback is DO_NOT_DOWNLOAD → don't download.
        if (audioFallback == FallbackStrategy.DO_NOT_DOWNLOAD ||
            qualityFallback == FallbackStrategy.DO_NOT_DOWNLOAD) {
            Log.d(TAG, "No preferred match + fallback=DO_NOT_DOWNLOAD → not downloading")
            return Selection.NoMatch
        }

        // ── Step 3: Fallback TRY_NEXT — pick the first available ──
        // (both audio + quality fallbacks must be TRY_NEXT to reach here)
        for (server in orderedServers) {
            val orderedAudios = orderByName(server.audioVersions, audioPrefs) { it.label }
            for (audio in orderedAudios) {
                val orderedVideos = orderByQuality(audio.videos, qualityPrefs)
                val first = orderedVideos.firstOrNull()
                if (first != null) {
                    Log.d(TAG, "Fallback (TRY_NEXT): ${server.name} / ${audio.label} / ${first.quality}")
                    return Selection.Selected(first, server.name, audio.label)
                }
            }
        }

        Log.d(TAG, "No video available at all → NoMatch")
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

    /** True if the audio label is in the user's preference list (case-insensitive). */
    private fun matchesAudio(audioLabel: String, audioPrefs: List<String>): Boolean {
        if (audioPrefs.isEmpty()) return true // no prefs = accept any
        return audioPrefs.any { it.equals(audioLabel, ignoreCase = true) }
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
