package app.confused.anikuta.core.common.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SourceProvenance].
 *
 * Verifies the data class holds all the provenance fields per ADR-050 §5.1,
 * and that [ExtensionSystem] resolves correctly from the stored key.
 */
class SourceProvenanceTest {

    @Test
    fun `SourceProvenance holds all fields`() {
        val provenance = SourceProvenance(
            system = ExtensionSystem.ANIYOMI,
            repoUrl = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo",
            repoName = "Aniyomi Extensions",
            extensionPkgName = "eu.kanade.tachiyomi.animeextension.en.gogoanime",
            extensionName = "GogoAnime",
            extensionVersionName = "1.4.3",
            extensionVersionCode = 143L,
            extensionLang = "en",
            isNsfw = false,
            sourceName = "GogoAnime",
            discoveredAt = 1700000000000L,
            lastResolvedAt = 1700000001000L,
            linkConfidence = 2,
        )
        assertEquals(ExtensionSystem.ANIYOMI, provenance.system)
        assertEquals("https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo", provenance.repoUrl)
        assertEquals("Aniyomi Extensions", provenance.repoName)
        assertEquals("eu.kanade.tachiyomi.animeextension.en.gogoanime", provenance.extensionPkgName)
        assertEquals("GogoAnime", provenance.extensionName)
        assertEquals("1.4.3", provenance.extensionVersionName)
        assertEquals(143L, provenance.extensionVersionCode)
        assertEquals("en", provenance.extensionLang)
        assertFalse(provenance.isNsfw)
        assertEquals("GogoAnime", provenance.sourceName)
        assertEquals(1700000000000L, provenance.discoveredAt)
        assertEquals(1700000001000L, provenance.lastResolvedAt)
        assertEquals(2, provenance.linkConfidence)
    }

    @Test
    fun `SourceProvenance allows nullable fields to be null`() {
        val provenance = SourceProvenance(
            system = ExtensionSystem.ANIYOMI,
            repoUrl = null,
            repoName = null,
            extensionPkgName = null,
            extensionName = null,
            extensionVersionName = null,
            extensionVersionCode = null,
            extensionLang = null,
            isNsfw = false,
            sourceName = null,
            discoveredAt = 0L,
            lastResolvedAt = 0L,
            linkConfidence = 0,
        )
        assertNull(provenance.repoUrl)
        assertNull(provenance.extensionName)
        assertEquals(0, provenance.linkConfidence)
    }

    @Test
    fun `SourceProvenance supports CloudStream system`() {
        val provenance = SourceProvenance(
            system = ExtensionSystem.CLOUDSTREAM,
            repoUrl = "https://example.com/cloudstream-repo",
            repoName = "CloudStream Repo",
            extensionPkgName = "com.lagradost.cloudstream3.example",
            extensionName = "Example CS Provider",
            extensionVersionName = "1.0",
            extensionVersionCode = 1L,
            extensionLang = "en",
            isNsfw = true,
            sourceName = "Example",
            discoveredAt = 0L,
            lastResolvedAt = 0L,
            linkConfidence = 1,
        )
        assertEquals(ExtensionSystem.CLOUDSTREAM, provenance.system)
        assertTrue(provenance.isNsfw)
    }

    @Test
    fun `linkConfidence values are distinguishable`() {
        val none = SourceProvenance(
            system = ExtensionSystem.ANIYOMI, repoUrl = null, repoName = null,
            extensionPkgName = null, extensionName = null, extensionVersionName = null,
            extensionVersionCode = null, extensionLang = null, isNsfw = false,
            sourceName = null, discoveredAt = 0L, lastResolvedAt = 0L, linkConfidence = 0,
        )
        val auto = none.copy(linkConfidence = 1)
        val confirmed = none.copy(linkConfidence = 2)

        assertEquals(0, none.linkConfidence)
        assertEquals(1, auto.linkConfidence)
        assertEquals(2, confirmed.linkConfidence)
        assertTrue(none.linkConfidence < auto.linkConfidence)
        assertTrue(auto.linkConfidence < confirmed.linkConfidence)
    }

    @Test
    fun `ExtensionSystem key round-trips for all systems`() {
        for (system in ExtensionSystem.entries) {
            assertNotNull(system.key)
            assertTrue(system.key.isNotBlank())
            assertEquals(system, ExtensionSystem.fromKey(system.key))
        }
    }

    @Test
    fun `MetadataProviderId key round-trips for all providers`() {
        for (provider in MetadataProviderId.entries) {
            assertNotNull(provider.key)
            assertTrue(provider.key.isNotBlank())
            assertEquals(provider, MetadataProviderId.fromKey(provider.key))
        }
    }

    @Test
    fun `all MetadataProviderId keys are distinct`() {
        val keys = MetadataProviderId.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size, "Provider keys must be unique")
    }

    @Test
    fun `all ExtensionSystem keys are distinct`() {
        val keys = ExtensionSystem.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size, "System keys must be unique")
    }
}
