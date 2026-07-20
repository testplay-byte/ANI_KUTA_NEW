package app.confused.anikuta.core.episodemetadata.source

/**
 * Registry for episode metadata sources.
 *
 * Per ADR-022: sources are registered here. Adding a new source (like TMDB)
 * = 1 new file + 1 registration call. No existing code changes.
 *
 * Usage:
 * ```kotlin
 * val registry = EpisodeMetadataSourceRegistry()
 * registry.register(AniListEpisodeMetadataSource())
 * registry.register(JikanEpisodeMetadataSource())
 * // Later: registry.register(TmdbEpisodeMetadataSource())
 *
 * val sources = registry.getAll() // ordered by priority
 * ```
 */
class EpisodeMetadataSourceRegistry {
    private val sources = mutableListOf<EpisodeMetadataSource>()

    fun register(source: EpisodeMetadataSource) {
        sources.add(source)
    }

    fun unregister(id: String) {
        sources.removeAll { it.id == id }
    }

    fun getAll(): List<EpisodeMetadataSource> = sources.toList()

    fun getSupported(request: app.confused.anikuta.core.episodemetadata.model.EpisodeMetadataRequest): List<EpisodeMetadataSource> =
        sources.filter { it.supports(request) }
}
