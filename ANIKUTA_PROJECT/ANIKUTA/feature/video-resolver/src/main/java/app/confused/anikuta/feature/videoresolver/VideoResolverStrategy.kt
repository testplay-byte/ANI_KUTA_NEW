package app.confused.anikuta.feature.videoresolver

import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Strategy interface for resolving videos into the UI hierarchy.
 *
 * Two implementations:
 * - [StructuredResolverStrategy] — for well-formatted extensions (like AnikotoS)
 *   that return organized videos. Groups into the 3-tier hierarchy: Server → Audio → Quality.
 * - [RawResolverStrategy] — for unstructured extensions that return a flat list
 *   of videos. Shows a flat list without forcing grouping.
 */
interface VideoResolverStrategy {

    /**
     * Groups a flat list of [Video]s into the resolver hierarchy.
     *
     * @param videos the flat list of playable videos
     * @param hosterNames optional map of video index → hoster name (from the
     *   Hoster.videoList ordering). Used as server names when available.
     * @return list of [ResolverServer]s.
     */
    fun resolve(videos: List<Video>, hosterNames: Map<Int, String>? = null): List<ResolverServer>
}

/**
 * Structured resolver — groups videos into the 3-tier hierarchy
 * (Server → Audio → Quality) using [VideoTitleParser].
 *
 * Server names come from (in priority):
 * 1. The hoster name (passed via [hosterNames])
 * 2. Parsed from the video title (text before " - " that isn't an audio token)
 * 3. Auto-named "Server A", "Server B", etc.
 *
 * If ALL videos are unparseable, returns empty list (caller falls back to raw).
 */
object StructuredResolverStrategy : VideoResolverStrategy {

    override fun resolve(videos: List<Video>, hosterNames: Map<Int, String>?): List<ResolverServer> {
        return VideoTitleParser.groupVideosByServer(videos, hosterNames)
    }
}

/**
 * Raw resolver — shows all videos as a flat list without forcing structure.
 *
 * Per user: "if it cannot make any sense of things... it should not do any
 * formatting on it at all. It should just show the raw names of the files and
 * options and such directly without formatting them."
 */
object RawResolverStrategy : VideoResolverStrategy {

    override fun resolve(videos: List<Video>, hosterNames: Map<Int, String>?): List<ResolverServer> {
        val resolverVideos = videos.mapIndexed { index, video ->
            val serverLabel = hosterNames?.get(index) ?: video.videoTitle.ifBlank { "Unknown" }
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
 * Auto-detects which strategy to use.
 *
 * If >= 50% of videos have detectable structure (server name, audio tokens,
 * quality info) OR hoster names are available → structured.
 * Otherwise → raw (flat list, no forced formatting).
 */
object ResolverStrategyPicker {

    private val QUALITY_REGEX = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)
    private val AUDIO_REGEX = Regex("""\b(SUB|DUB|HSUB|HARDSUB|H-SUB|HARDSUBBED|SUBBED|DUBBED|A-DUB|ADUB)\b""", RegexOption.IGNORE_CASE)

    fun pick(videos: List<Video>, hasHosterNames: Boolean = false): VideoResolverStrategy {
        if (videos.isEmpty()) return StructuredResolverStrategy

        // If we have hoster names, always use structured (the hoster name IS the server)
        if (hasHosterNames) return StructuredResolverStrategy

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
