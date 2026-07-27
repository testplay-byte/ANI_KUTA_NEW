package app.confused.anikuta.feature.videoresolver

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Parses video titles to extract the audio version (SUB/DUB/HSUB) and quality,
 * then groups videos into the 3-tier hierarchy (Server → Audio → Quality).
 *
 * **Smart detection** (per user requirements):
 * - Audio versions (SUB, DUB, HSUB, H-SUB, A-DUB, SUBBED, DUBBED, HARDSUB) are
 *   NEVER treated as server names. They are extracted from the title and used
 *   as the audio version label.
 * - Server names come from (in priority order):
 *   1. The [Hoster.hosterName] (if available from the hoster-based API)
 *   2. The text before `" - "` in the title IF it's not an audio version
 *   3. Auto-generated names: "Server A", "Server B", "Server C", ... (per user:
 *      "if it cannot detect the server name but it did actually detect the audio
 *      versions, then what it will do is that it will name the servers A, B, C")
 *   4. If NOTHING is detectable (no audio, no quality, no server) → returns null,
 *      signaling the caller to use [RawResolverStrategy] instead.
 *
 * **Sorting**:
 * - Servers: preferred server first (if any), then alphabetical.
 * - Audio versions: SUB → DUB → HSUB → Unknown.
 * - Quality: highest first (descending).
 */
object VideoTitleParser {

    private val QUALITY_REGEX = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)

    /** All known audio version + language tokens (case-insensitive). These are NEVER treated as server names. */
    private val AUDIO_TOKENS = setOf(
        "SUB", "SUBBED", "HSUB", "HARDSUB", "H-SUB", "HARDSUBBED",
        "DUB", "DUBBED", "A-DUB", "ADUB",
        // Language names that extensions sometimes use as the "audio version" part
        "JAPANESE", "ENGLISH", "SPANISH", "FRENCH", "GERMAN", "PORTUGUESE", "ITALIAN",
        "KOREAN", "CHINESE", "RUSSIAN", "CHINESE",
        "ENG", "JPN", "ESP", "FRA", "DEU", "POR", "ITA", "KOR", "CHI", "RUS",
    )

    /** Regex that matches any known audio token as a whole word. */
    private val AUDIO_REGEX = Regex("""\b(SUB|DUB|HSUB|HARDSUB|H-SUB|HARDSUBBED|SUBBED|DUBBED|A-DUB|ADUB)\b""", RegexOption.IGNORE_CASE)

    /**
     * The audio version of a video. The order here defines the sort priority
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
                "DUB", "DUBBED", "A-DUB", "ADUB" -> DUB
                "HSUB", "HARDSUB", "H-SUB", "HARDSUBBED" -> HSUB
                else -> UNKNOWN
            }
        }
    }

    /** True if [text] is a known audio version token (case-insensitive). */
    fun isAudioToken(text: String): Boolean =
        text.uppercase() in AUDIO_TOKENS

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
     * - **Quality**: prefers [Video.resolution]; falls back to regex `\d{3,4}p`.
     * - **Audio**: regex-matches known audio tokens. NOT used as server name.
     * - **Server**: extracted from title parts that are NOT audio tokens or quality.
     *   Returns "[UNPARSEABLE]" if no server name can be extracted.
     */
    fun parse(video: Video, hosterName: String? = null): ParsedVideo {
        val title = video.videoTitle.ifBlank { video.quality }

        val quality = video.resolution
            ?: QUALITY_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull()

        val audioMatch = AUDIO_REGEX.find(title)
        val audio = audioMatch?.value?.let { AudioVersion.fromToken(it) }
            ?: AudioVersion.UNKNOWN

        // ── Server name extraction ──
        // Priority 1: hosterName from the Hoster object
        if (hosterName != null && hosterName.isNotBlank() && hosterName != Hoster.NO_HOSTER_LIST) {
            return ParsedVideo(video, hosterName, audio, quality)
        }

        // Priority 2: split by " - " and take parts that are NOT audio tokens or quality
        val parts = title.split(" - ").map { it.trim() }.filter { it.isNotBlank() }
        val serverParts = parts.filter { part ->
            !isAudioToken(part) &&
            !QUALITY_REGEX.matches(part) &&
            !part.all { it.isDigit() }
        }

        val server = serverParts.firstOrNull()?.ifBlank { null }
            ?: return ParsedVideo(video, "[UNPARSEABLE]", audio, quality)

        return ParsedVideo(video, server, audio, quality)
    }

    /**
     * Groups a flat list of [Video]s into the 3-tier hierarchy.
     * Uses [hosterNames] to map videos to their server names when available.
     *
     * If ALL videos are unparseable (server="[UNPARSEABLE]"), returns an empty
     * list, signaling the caller to fall back to [RawResolverStrategy].
     *
     * If SOME videos have server names and others don't, the unparseable ones
     * get auto-named "Server A", "Server B", etc. per the user's request.
     *
     * @param hosterNames optional map of video index → hoster name (from the
     *   Hoster.videoList ordering). If null, all server names come from title parsing.
     */
    fun groupVideosByServer(
        videos: List<Video>,
        hosterNames: Map<Int, String>? = null,
    ): List<ResolverServer> {
        val parsed = videos.mapIndexed { index, video ->
            parse(video, hosterNames?.get(index))
        }

        // Check if ALL are unparseable → return empty (caller falls back to raw)
        val allUnparseable = parsed.all { it.server == "[UNPARSEABLE]" }
        if (allUnparseable) {
            return emptyList()
        }

        // Auto-name unparseable servers: A, B, C, ...
        val autoNamed = mutableListOf<ParsedVideo>()
        var autoCounter = 0
        for (pv in parsed) {
            if (pv.server == "[UNPARSEABLE]") {
                autoCounter++
                autoNamed.add(pv.copy(server = "Server ${'A' + autoCounter - 1}"))
            } else {
                autoNamed.add(pv)
            }
        }

        val byServer = autoNamed.groupBy { it.server }
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
                                    headers.names().joinToString("\n") { "$it: ${headers[it]}" }
                                },
                                subtitleTracks = pv.video.subtitleTracks.map {
                                    SubtitleTrack(it.url, it.lang)
                                },
                                audioTracks = pv.video.audioTracks.map {
                                    SubtitleTrack(it.url, it.lang)
                                },
                            )
                        },
                    )
                }
            ResolverServer(serverName, audioVersions)
        }
    }
}
