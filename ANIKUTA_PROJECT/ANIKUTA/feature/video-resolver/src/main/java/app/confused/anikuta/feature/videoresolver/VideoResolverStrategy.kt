package app.confused.anikuta.feature.videoresolver

import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Strategy interface for resolving videos into the UI hierarchy.
 *
 * Two implementations:
 * - [StructuredResolverStrategy] — for well-formatted extensions (like AnikotoS)
 *   that return organized videos with proper server/audio/quality structure.
 *   Groups into the 3-tier hierarchy: Server → Audio → Quality.
 * - [RawResolverStrategy] — for unstructured extensions that return a flat list
 *   of videos with no clear structure. Shows a flat list without forcing grouping.
 *
 * The [ResolverService] picks the strategy based on whether the video titles
 * contain detectable structure (server name + audio version + quality).
 */
interface VideoResolverStrategy {

    /**
     * Groups a flat list of [Video]s into the resolver hierarchy.
     *
     * @return list of [ResolverServer]s for the structured strategy,
     *   or a single server with a flat list for the raw strategy.
     */
    fun resolve(videos: List<Video>): List<ResolverServer>
}

/**
 * Structured resolver — groups videos into the 3-tier hierarchy
 * (Server → Audio → Quality) using [VideoTitleParser].
 *
 * Used when video titles contain detectable structure like
 * "ServerName - SUB - 1080p" or when hosters have proper names.
 */
object StructuredResolverStrategy : VideoResolverStrategy {

    override fun resolve(videos: List<Video>): List<ResolverServer> {
        return VideoTitleParser.groupVideosByServer(videos)
    }
}

/**
 * Raw resolver — shows all videos as a flat list without forcing structure.
 *
 * Used when video titles don't contain detectable server/audio/quality info
 * (e.g., bare URLs, random names). Groups everything under a single "All Videos"
 * server with one "Default" audio version.
 *
 * Per user: "it will just show the raw server list however the extension provides
 * it back and such."
 */
object RawResolverStrategy : VideoResolverStrategy {

    override fun resolve(videos: List<Video>): List<ResolverServer> {
        val resolverVideos = videos.map { video ->
            ResolverVideo(
                quality = video.videoTitle.ifBlank { video.resolution?.let { "${it}p" } ?: "Unknown" },
                url = video.videoUrl,
                videoTitle = video.videoTitle,
                videoHeaders = video.headers?.let { headers ->
                    headers.names().joinToString("\n") { "$it: ${headers[it]}" }
                },
                subtitleTracks = video.subtitleTracks.map {
                    SubtitleTrack(it.url, it.lang)
                },
                audioTracks = video.audioTracks.map {
                    SubtitleTrack(it.url, it.lang)
                },
            )
        }
        return listOf(
            ResolverServer(
                name = "All Videos",
                audioVersions = listOf(
                    ResolverAudioVersion(
                        label = "Default",
                        videos = resolverVideos,
                    ),
                ),
            ),
        )
    }
}

/**
 * Helper: determines which strategy to use based on the video list quality.
 *
 * If most videos have detectable server names (not "Unknown"), uses the
 * structured strategy. Otherwise, uses the raw strategy.
 */
object ResolverStrategyPicker {

    private val QUALITY_REGEX = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)
    private val AUDIO_REGEX = Regex("""\b(SUB|DUB|HSUB|HARDSUB|SUBBED|DUBBED)\b""", RegexOption.IGNORE_CASE)

    /**
     * Picks the best strategy for the given video list.
     *
     * @param videos the flat list of videos from the source
     * @return [StructuredResolverStrategy] if the videos have detectable structure,
     *   [RawResolverStrategy] otherwise
     */
    fun pick(videos: List<Video>): VideoResolverStrategy {
        if (videos.isEmpty()) return StructuredResolverStrategy

        // Check if at least 50% of videos have a detectable server name
        // (i.e., the title contains " - " separator)
        val withStructure = videos.count { video ->
            video.videoTitle.contains(" - ") ||
                AUDIO_REGEX.containsMatchIn(video.videoTitle) ||
                QUALITY_REGEX.containsMatchIn(video.videoTitle)
        }

        return if (withStructure >= videos.size / 2) {
            StructuredResolverStrategy
        } else {
            RawResolverStrategy
        }
    }
}
