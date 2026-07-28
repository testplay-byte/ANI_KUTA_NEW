package app.confused.anikuta.core.providerapi

import app.confused.anikuta.core.common.model.MetadataProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MetadataProviderRegistry].
 *
 * Per `plan/03_testing_strategy.md` Phase 2: fallback logic, capability lookup,
 * active-provider selection, unavailable-provider fallback.
 */
class MetadataProviderRegistryTest {

    /** A fake HomeFeedProvider for testing. */
    private class FakeHomeFeedProvider(
        override val id: MetadataProviderId,
        override val displayName: String = id.displayName,
        private val available: Boolean = true,
        override val capabilities: Set<MetadataCapability> = setOf(MetadataCapability.HOME_FEED),
    ) : HomeFeedProvider {
        override val requiresAuth: Boolean = false
        override suspend fun isAvailable(): Boolean = available
        override suspend fun fetchTrending(page: Int, perPage: Int) = emptyList<app.confused.anikuta.core.common.model.details.UnifiedAnime>()
        override suspend fun fetchPopular(page: Int, perPage: Int) = emptyList()
    }

    /** A fake provider that supports multiple capabilities. */
    private class FakeMultiCapProvider(
        override val id: MetadataProviderId,
        private val available: Boolean = true,
        private val caps: Set<MetadataCapability> = setOf(MetadataCapability.HOME_FEED, MetadataCapability.SEARCH),
    ) : MetadataProvider {
        override val displayName: String = id.displayName
        override val requiresAuth: Boolean = false
        override val capabilities: Set<MetadataCapability> = caps
        override suspend fun isAvailable(): Boolean = available
    }

    /** A fake [ProviderPreferences] for testing. */
    private class FakeProviderPreferences : ProviderPreferences {
        var active: MutableMap<MetadataCapability, MetadataProviderId> = mutableMapOf()
        var fallback: MutableMap<MetadataCapability, List<MetadataProviderId>> = mutableMapOf()

        override fun activeProviderFor(capability: MetadataCapability): MetadataProviderId? = active[capability]
        override fun setActiveProvider(capability: MetadataCapability, provider: MetadataProviderId) {
            active[capability] = provider
        }
        override fun fallbackOrder(capability: MetadataCapability): List<MetadataProviderId> = fallback[capability] ?: emptyList()
        override fun setFallbackOrder(capability: MetadataCapability, order: List<MetadataProviderId>) {
            fallback[capability] = order
        }
        override fun observeActiveProvider(capability: MetadataCapability): Flow<MetadataProviderId?> =
            MutableStateFlow(active[capability])
    }

    @Test
    fun `forCapability returns the active provider when available`() = runTest {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL)
        val prefs = FakeProviderPreferences().apply {
            active[MetadataCapability.HOME_FEED] = MetadataProviderId.MAL
        }
        val registry = MetadataProviderRegistry(listOf(anilist, mal), prefs)

        val provider = registry.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        assertNotNull(provider)
        assertEquals(MetadataProviderId.MAL, provider!!.id)
    }

    @Test
    fun `forCapability falls back to next provider when active is unavailable`() = runTest {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST, available = true)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL, available = false)
        val prefs = FakeProviderPreferences().apply {
            active[MetadataCapability.HOME_FEED] = MetadataProviderId.MAL
        }
        val registry = MetadataProviderRegistry(listOf(anilist, mal), prefs)

        val provider = registry.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        assertNotNull(provider)
        assertEquals(MetadataProviderId.ANILIST, provider!!.id, "Should fall back to AniList when MAL is unavailable")
    }

    @Test
    fun `forCapability returns null when no provider is available`() = runTest {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST, available = false)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL, available = false)
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(listOf(anilist, mal), prefs)

        val provider = registry.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        assertNull(provider)
    }

    @Test
    fun `forCapability returns null when no provider implements the capability`() = runTest {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST)  // only HOME_FEED
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(listOf(anilist), prefs)

        val provider = registry.forCapability<SearchProvider>(MetadataCapability.SEARCH)
        assertNull(provider, "AniList doesn't implement SEARCH in this test fixture")
    }

    @Test
    fun `forCapability uses declaration order when no active preference set`() = runTest {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL)
        val prefs = FakeProviderPreferences()  // no active set
        val registry = MetadataProviderRegistry(listOf(anilist, mal), prefs)

        val provider = registry.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        assertNotNull(provider)
        assertEquals(MetadataProviderId.ANILIST, provider!!.id, "Default should be first registered (AniList)")
    }

    @Test
    fun `forCapability respects user fallback order`() = runTest {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL)
        val tmdb = FakeHomeFeedProvider(MetadataProviderId.TMDB)
        val prefs = FakeProviderPreferences().apply {
            // Active = AniList, but fallback order puts TMDB second (before MAL).
            active[MetadataCapability.HOME_FEED] = MetadataProviderId.ANILIST
            fallback[MetadataCapability.HOME_FEED] = listOf(MetadataProviderId.TMDB, MetadataProviderId.MAL)
        }
        val registry = MetadataProviderRegistry(listOf(anilist, mal, tmdb), prefs)

        // AniList unavailable → should fall back to TMDB (per user order), not MAL.
        val anilistUnavailable = FakeHomeFeedProvider(MetadataProviderId.ANILIST, available = false)
        val registry2 = MetadataProviderRegistry(listOf(anilistUnavailable, mal, tmdb), prefs)
        val provider = registry2.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        assertNotNull(provider)
        assertEquals(MetadataProviderId.TMDB, provider!!.id)
    }

    @Test
    fun `forCapability handles isAvailable throwing`() = runTest {
        val throwingProvider = object : HomeFeedProvider {
            override val id = MetadataProviderId.ANILIST
            override val displayName = "Throwing"
            override val requiresAuth = false
            override val capabilities = setOf(MetadataCapability.HOME_FEED)
            override suspend fun isAvailable(): Boolean = error("network crash")
            override suspend fun fetchTrending(page: Int, perPage: Int) = emptyList<app.confused.anikuta.core.common.model.details.UnifiedAnime>()
            override suspend fun fetchPopular(page: Int, perPage: Int) = emptyList()
        }
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL, available = true)
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(listOf(throwingProvider, mal), prefs)

        val provider = registry.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        assertNotNull(provider)
        assertEquals(MetadataProviderId.MAL, provider!!.id, "Should skip throwing provider and fall back to MAL")
    }

    @Test
    fun `allForCapability returns all providers regardless of availability`() {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST, available = false)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL, available = false)
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(listOf(anilist, mal), prefs)

        val providers = registry.allForCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED)
        assertEquals(2, providers.size)
    }

    @Test
    fun `allForCapability filters by capability`() {
        val homeOnly = FakeHomeFeedProvider(MetadataProviderId.ANILIST)
        val multiCap = FakeMultiCapProvider(MetadataProviderId.MAL, caps = setOf(MetadataCapability.HOME_FEED, MetadataCapability.SEARCH))
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(listOf(homeOnly, multiCap), prefs)

        val homeProviders = registry.allForCapability<MetadataProvider>(MetadataCapability.HOME_FEED)
        assertEquals(2, homeProviders.size, "Both implement HOME_FEED")

        val searchProviders = registry.allForCapability<MetadataProvider>(MetadataCapability.SEARCH)
        assertEquals(1, searchProviders.size, "Only multiCap implements SEARCH")
        assertEquals(MetadataProviderId.MAL, searchProviders.first().id)
    }

    @Test
    fun `byId returns the provider with the matching id`() {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL)
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(listOf(anilist, mal), prefs)

        assertEquals(MetadataProviderId.ANILIST, registry.byId(MetadataProviderId.ANILIST)?.id)
        assertEquals(MetadataProviderId.MAL, registry.byId(MetadataProviderId.MAL)?.id)
        assertNull(registry.byId(MetadataProviderId.TMDB))
    }

    @Test
    fun `all exposes all registered providers`() {
        val anilist = FakeHomeFeedProvider(MetadataProviderId.ANILIST)
        val mal = FakeHomeFeedProvider(MetadataProviderId.MAL)
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(listOf(anilist, mal), prefs)

        assertEquals(2, registry.all.size)
    }

    @Test
    fun `empty registry returns null for forCapability`() = runTest {
        val prefs = FakeProviderPreferences()
        val registry = MetadataProviderRegistry(emptyList(), prefs)

        assertNull(registry.forCapability<HomeFeedProvider>(MetadataCapability.HOME_FEED))
    }
}
