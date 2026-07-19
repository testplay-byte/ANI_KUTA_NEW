package app.anikuta.ui.detail

import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Phase 7 — Parses video titles to extract (server, audio version, quality).
 *
 * Most extensions (AniKoto, and others using the anikototheme/multisrc) format
 * video titles as `"{server} - {audio} - {quality}"`, e.g.:
 *   "VidPlay-1 - SUB - 360p"
 *   "HD-1 - DUB - 1080p"
 *   "VidPlay-1 - HSUB - 720p"
 *
 * Some extensions return a flat list (1 server group "Default") where all
 * 27 videos follow this same title format. This parser extracts the real
 * server/audio/quality from the title so we can re-group by audio version
 * and sort by quality — regardless of how the extension structured the list.
 *
 * Graceful degradation: if the title doesn't match the expected format (other
 * extension formats), falls back to:
 *   - server = full title
 *   - audio = ANY
 *   - quality = Video.resolution or null
 */
data class ParsedVideo(
    val video: Video,
    val server: String,
    val audio: AudioVersion,
    val quality: Int?,  // e.g. 1080, 720, 360 — null if unknown
)

enum class AudioVersion(val label: String) {
    SUB("Sub"),
    DUB("Dub"),
    HSUB("Hardsub"),
    ANY("Any");

    companion object {
        fun fromToken(token: String?): AudioVersion {
            if (token == null) return ANY
            return when (token.uppercase()) {
                "SUB", "SUBBED" -> SUB
                "DUB", "DUBBED" -> DUB
                "HSUB", "HARDSUB" -> HSUB
                else -> ANY
            }
        }
    }
}

object VideoTitleParser {
    private val QUALITY_REGEX = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)
    private val AUDIO_REGEX = Regex("""\b(SUB|DUB|HSUB|HARDSUB|SUBBED|DUBBED)\b""", RegexOption.IGNORE_CASE)

    /**
     * Parse a [Video]'s title into [ParsedVideo].
     *
     * Uses [Video.resolution] when non-null; otherwise regex-extracts from the title.
     * Detects audio version (SUB/DUB/HSUB) from the title.
     * Extracts server name as the token before the first " - " separator.
     */
    fun parse(video: Video): ParsedVideo {
        val title = video.videoTitle

        // Quality: prefer the structured field, fall back to regex
        val quality = video.resolution
            ?: QUALITY_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull()

        // Audio version: scan for keyword
        val audioToken = AUDIO_REGEX.find(title)?.value
        val audio = AudioVersion.fromToken(audioToken)

        // Server: the token before the first " - " separator
        // e.g. "VidPlay-1 - SUB - 360p" → "VidPlay-1"
        val server = if (title.contains(" - ")) {
            title.substringBefore(" - ").trim()
        } else {
            title.trim()
        }

        return ParsedVideo(
            video = video,
            server = server.ifBlank { "Server" },
            audio = audio,
            quality = quality,
        )
    }
}

/**
 * Groups a flat list of videos into **server** sections (top level), each
 * containing **audio** sections (expandable), each containing videos sorted
 * by quality descending (1080p top, 360p bottom).
 *
 * Hierarchy (per user's Phase 7 feedback):
 *   ServerSection (top-level header, collapsible)
 *     └─ AudioSubSection (expandable within server)
 *          └─ Video (quality desc, resolution chip on the right)
 */
data class ServerSection(
    val serverName: String,
    val audioSections: List<AudioSubSection>,
)

data class AudioSubSection(
    val audio: AudioVersion,
    val videos: List<Video>,  // sorted by quality descending
)

/**
 * Build [ServerSection] list from a flat list of videos.
 *
 * Groups by server (top level), then by audio within each server, then sorts
 * by quality descending within each audio section.
 */
fun groupVideosByServer(videos: List<Video>): List<ServerSection> {
    val parsed = videos.map { VideoTitleParser.parse(it) }

    // Group by server, then by audio within each server
    val byServer = parsed.groupBy { it.server }

    return byServer.entries
        .sortedBy { it.key }
        .map { (serverName, parsedVideos) ->
            val byAudio = parsedVideos.groupBy { it.audio }
            val audioSections = byAudio.entries
                .sortedBy { audioOrder.indexOf(it.key) }
                .map { (audio, vids) ->
                    AudioSubSection(
                        audio = audio,
                        videos = vids.sortedByDescending { it.quality ?: 0 }
                            .map { it.video },
                    )
                }
            ServerSection(serverName = serverName, audioSections = audioSections)
        }
}

// Keep the old AudioSection grouping for backward compatibility (unused now).
data class AudioSection(
    val audio: AudioVersion,
    val servers: List<ServerSection>,
)

/** Display order for audio versions in the picker. */
private val audioOrder = listOf(AudioVersion.SUB, AudioVersion.DUB, AudioVersion.HSUB, AudioVersion.ANY)
