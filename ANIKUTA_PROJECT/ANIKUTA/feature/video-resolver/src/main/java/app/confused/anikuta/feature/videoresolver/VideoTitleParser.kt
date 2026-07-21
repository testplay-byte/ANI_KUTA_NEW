package app.confused.anikuta.feature.videoresolver

import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Parses video titles to extract the server name, audio version (SUB/DUB/HSUB),
 * and quality/resolution, then groups videos into the 3-tier hierarchy
 * (Server → Audio → Quality) used by [VideoResolverSheet].
 *
 * Ported from the old ANIKUTA project's `VideoTitleParser.kt` with improvements:
 * - Uses [Video.resolution] (structured field) when available, falls back to regex.
 * - Handles the `"ServerName - SUB - 1080p"` format AND bare titles like `"1080p"`.
 *
 * **Sorting** (per the design language `video-resolver.md` §6):
 * - Servers: alphabetical.
 * - Audio versions: SUB → DUB → HSUB → Unknown.
 * - Quality: highest first (descending).
 */
object VideoTitleParser {

    private val QUALITY_REGEX = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)
    private val AUDIO_REGEX = Regex("""\b(SUB|DUB|HSUB|HARDSUB|SUBBED|DUBBED)\b""", RegexOption.IGNORE_CASE)

    /**
     * The audio version of a video. Extensions encode this in the video title
     * (e.g. `"Server - SUB - 1080p"`). The order here defines the sort priority
     * in the picker (SUB first, Unknown last).
     */
    enum class AudioVersion(val label: String) {
        SUB("SUB"),
        DUB("DUB"),
        HSUB("HSUB"),
        UNKNOWN("Unknown");

        companion object {
            fun fromToken(token: String): AudioVersion = when (token.uppercase()) {
                "SUB", "SUBBED" -> SUB
                "DUB", "DUBBED" -> DUB
                "HSUB", "HARDSUB" -> HSUB
                else -> UNKNOWN
            }
        }
    }

    /** The result of parsing one [Video]'s title. */
    data class ParsedVideo(
        val video: Video,
        val server: String,
        val audio: AudioVersion,
        val quality: Int?,
    )

    /**
     * Parses [video]'s title into a [ParsedVideo].
     *
     * - **Quality**: prefers [Video.resolution] (structured); falls back to regex
     *   `\d{3,4}p` extraction from [Video.videoTitle].
     * - **Audio**: regex-matches SUB/DUB/HSUB/HARDSUB/SUBBED/DUBBED.
     * - **Server**: the substring before the first `" - "` separator (e.g.
     *   `"VidPlay-1 - SUB - 360p"` → `"VidPlay-1"`). Falls back to the full
     *   trimmed title if no `" - "` is present.
     */
    fun parse(video: Video): ParsedVideo {
        val title = video.videoTitle.ifBlank { video.quality }

        val quality = video.resolution
            ?: QUALITY_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull()

        val audio = AUDIO_REGEX.find(title)?.value?.let { AudioVersion.fromToken(it) }
            ?: AudioVersion.UNKNOWN

        val server = title.substringBefore(" - ").trim().ifBlank { "Unknown" }

        return ParsedVideo(video, server, audio, quality)
    }

    /**
     * Groups a flat list of [Video]s into the 3-tier hierarchy:
     * `List<ResolverServer>` → each has `List<ResolverAudioVersion>` → each has
     * `List<ResolverVideo>`.
     *
     * Servers are sorted alphabetically; audio versions in SUB→DUB→HSUB→Unknown
     * order; quality descending (highest first).
     */
    fun groupVideosByServer(videos: List<Video>): List<ResolverServer> {
        val parsed = videos.map { parse(it) }
        val byServer = parsed.groupBy { it.server }
        val audioOrder = listOf(
            AudioVersion.SUB, AudioVersion.DUB, AudioVersion.HSUB, AudioVersion.UNKNOWN,
        )

        return byServer.entries.sortedBy { it.key }.map { (serverName, parsedVideos) ->
            val byAudio = parsedVideos.groupBy { it.audio }
            val audioVersions = byAudio.entries
                .sortedBy { audioOrder.indexOf(it.key) }
                .map { (audio, vids) ->
                    val sorted = vids.sortedByDescending { it.quality ?: 0 }
                    ResolverAudioVersion(
                        label = audio.label,
                        videos = sorted.map { pv ->
                            ResolverVideo(
                                quality = pv.quality?.let { "${it}p" } ?: "Unknown",
                                url = pv.video.videoUrl,
                                videoTitle = pv.video.videoTitle,
                                videoHeaders = pv.video.headers?.let { headers ->
                                    headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                                },
                            )
                        },
                    )
                }
            ResolverServer(serverName, audioVersions)
        }
    }
}
