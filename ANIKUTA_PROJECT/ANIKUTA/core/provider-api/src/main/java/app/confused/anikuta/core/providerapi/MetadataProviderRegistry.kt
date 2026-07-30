package app.confused.anikuta.core.providerapi

import app.confused.anikuta.core.common.model.MetadataProviderId
import kotlinx.coroutines.flow.Flow

/**
 * User preferences for metadata-provider selection.
 *
 * Per ADR-041 + the owner's direction, the user can select the active provider
 * per capability (Home feed, Search, Schedule, Cover images, Details) and
 * reorder the fallback order. Stored in SharedPreferences via [ProviderPreferences].
 *
 * The registry consults this to pick the primary provider for a capability,
 * then falls through the fallback order if the primary is unavailable.
 */
interface ProviderPreferences {

    /**
     * Get the user's active (preferred) provider for a capability.
     *
     * Returns null if the user hasn't set a preference → the registry uses the
     * default (AniList for all capabilities).
     */
    fun activeProviderFor(capability: MetadataCapability): MetadataProviderId?

    /**
     * Set the user's active provider for a capability.
     */
    fun setActiveProvider(capability: MetadataCapability, provider: MetadataProviderId)

    /**
     * Get the fallback order for a capability (a list of provider IDs to try
     * if the primary is unavailable). The active provider is always first;
     * the rest follow in declaration order.
     */
    fun fallbackOrder(capability: MetadataCapability): List<MetadataProviderId>

    /**
     * Set a custom fallback order for a capability.
     */
    fun setFallbackOrder(capability: MetadataCapability, order: List<MetadataProviderId>)

    /**
     * Observe changes to the active provider for a capability (for reactive UI).
     */
    fun observeActiveProvider(capability: MetadataCapability): Flow<MetadataProviderId?>
}

/**
 * Registry of all registered [MetadataProvider]s.
 *
 * Populated via Koin multi-binding (`single<List<MetadataProvider>>`), the same
 * pattern used for `List<AnimeDetailsProvider>` (see `DetailsModule.kt`).
 *
 * # Usage from a ViewModel
 * ```
 * val homeProvider = registry.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
 *     ?: error("No HomeFeedProvider registered")
 * val trending = homeProvider.fetchTrending()
 * ```
 *
 * # Fallback behavior
 *
 * When the active provider for a capability is unavailable ([isAvailable] returns
 * `false`), the registry tries the next provider in the fallback order. If all
 * fail, [forCapability] returns null (the UI shows a "provider unavailable" state).
 *
 * Adding a provider = one new module + one entry in the `listOf(...)` Koin binding.
 * No changes to this registry or the consumers.
 *
 * Per `_ARCHITECTURE_PLAN/proposals/02_provider_abstraction.md` §2.3.
 */
class MetadataProviderRegistry(
    @PublishedApi internal val providers: List<MetadataProvider>,
    @PublishedApi internal val preferences: ProviderPreferences,
) {

    /** All registered providers (rarely needed directly — use [forCapability]). */
    val all: List<MetadataProvider> get() = providers

    /**
     * Resolve the active (available) provider for a capability.
     *
     * Tries the active provider first; if unavailable, falls through the fallback
     * order. Returns null if no provider for [capability] is available.
     *
     * @param T the capability sub-interface (e.g., [HomeFeedProvider]).
     * @param capability the capability to resolve.
     * @return the provider, or null if none available.
     */
    suspend inline fun <reified T : MetadataProvider> forCapability(capability: MetadataCapability): T? {
        val candidates = providers
            .filter { it.capabilities.contains(capability) }
            .filterIsInstance<T>()

        if (candidates.isEmpty()) return null

        // Build the fallback order: active provider first, then the user's
        // fallback order, then any remaining providers in declaration order.
        val active = preferences.activeProviderFor(capability)
        val userFallback = preferences.fallbackOrder(capability)
        val ordered = mutableListOf<T>()

        // 1. Active provider (if set).
        if (active != null) {
            candidates.firstOrNull { it.id == active }?.let { ordered.add(it) }
        }
        // 2. User's fallback order (skip the active, already added).
        for (providerId in userFallback) {
            if (providerId == active) continue
            candidates.firstOrNull { it.id == providerId && it !in ordered }?.let { ordered.add(it) }
        }
        // 3. Any remaining providers (declaration order).
        for (candidate in candidates) {
            if (candidate !in ordered) ordered.add(candidate)
        }

        // Try each in order; return the first available.
        for (provider in ordered) {
            try {
                if (provider.isAvailable()) {
                    return provider
                }
            } catch (_: Throwable) {
                // isAvailable() threw — treat as unavailable, try next.
            }
        }
        return null
    }

    /**
     * Get ALL providers that implement a capability (regardless of availability).
     *
     * Used by the Settings → Metadata Providers screen to list available providers
     * per capability.
     */
    inline fun <reified T : MetadataProvider> allForCapability(capability: MetadataCapability): List<T> =
        providers
            .filter { it.capabilities.contains(capability) }
            .filterIsInstance<T>()

    /**
     * Get a specific provider by ID (regardless of capability).
     *
     * Returns null if no provider with [id] is registered.
     */
    fun byId(id: MetadataProviderId): MetadataProvider? =
        providers.firstOrNull { it.id == id }
}
