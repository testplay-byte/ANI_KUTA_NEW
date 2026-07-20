package app.confused.anikuta.feature.videoresolver

/**
 * The video resolver state machine — drives [VideoResolverSheet].
 *
 * Per `DESIGN_LANGUAGE/04-screens/video-resolver.md` §4:
 * - [Hidden] — no sheet shown.
 * - [Resolving] — spinner + "Resolving video sources..." while the source is queried.
 * - [Show] — the full picker with the 3-tier server/audio/quality hierarchy.
 * - [NoSources] — the source returned no playable videos.
 * - [Error] — the resolution failed (network error, timeout, etc.).
 */
sealed interface VideoResolverState {
    data object Hidden : VideoResolverState
    data class Resolving(val episodeNumber: Int) : VideoResolverState
    data class Show(
        val episodeNumber: Int,
        val servers: List<ResolverServer>,
    ) : VideoResolverState
    data class NoSources(val episodeNumber: Int) : VideoResolverState
    data class Error(val episodeNumber: Int, val message: String) : VideoResolverState
}

/**
 * A server entry in the resolver — top level of the 3-tier hierarchy.
 * Per the design language: Server → Audio → Quality.
 */
data class ResolverServer(
    val name: String,
    val audioVersions: List<ResolverAudioVersion>,
)

/**
 * An audio version (SUB/DUB/HSUB/etc.) within a server.
 */
data class ResolverAudioVersion(
    val label: String,
    val videos: List<ResolverVideo>,
)

/**
 * A single video quality option within an audio version.
 */
data class ResolverVideo(
    val quality: String,
    val url: String,
    val videoTitle: String = "",
)
