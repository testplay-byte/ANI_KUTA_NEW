package app.confused.anikuta.feature.videoresolver

/**
 * Video resolver state — the 4-state machine for the resolver.
 *
 * Per `OLD_ANIKUTA/ANALYSIS/details-episodes-resolution-screens.md` §3:
 * - Hidden: no sheet shown.
 * - Resolving: full-screen scrim with "Resolving..." text.
 * - Cached: instant render with "Refreshing..." badge + background re-resolve.
 * - Show: the full picker with servers/audio/quality.
 */
sealed interface VideoResolverState {
    data object Hidden : VideoResolverState
    data class Resolving(val episodeNumber: Int) : VideoResolverState
    data class Cached(val episodeNumber: Int) : VideoResolverState
    data class Show(
        val episodeNumber: Int,
        val servers: List<ResolverServer>,
    ) : VideoResolverState
    data class NoSources(val episodeNumber: Int) : VideoResolverState
}

/**
 * A server entry in the resolver — top level of the 3-tier hierarchy.
 *
 * Per the design language: Server → Audio → Quality.
 * When extensions are loaded, each server comes from the source's hoster list.
 */
data class ResolverServer(
    val name: String,
    val audioVersions: List<ResolverAudioVersion>,
)

/**
 * An audio version (SUB/DUB/HSUB/etc.) within a server.
 */
data class ResolverAudioVersion(
    val label: String, // "SUB", "DUB", "HSUB", etc.
    val videos: List<ResolverVideo>,
)

/**
 * A single video quality option within an audio version.
 */
data class ResolverVideo(
    val quality: String, // "1080p", "720p", "480p", etc.
    val url: String,
    val videoTitle: String = "",
)
