package app.confused.anikuta.core.common.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LocalId] + [LocalIdGenerator].
 *
 * Per `plan/03_testing_strategy.md` Phase 1: serialization round-trip,
 * stableKey uniqueness, crossDeviceKey reproducibility, parser robustness.
 */
class LocalIdTest {

    @Test
    fun `forExtension produces correct structured format`() {
        val localId = LocalIdGenerator.forExtension(
            system = ExtensionSystem.ANIYOMI,
            extensionId = 1234567890L,
            sourceContentId = "https://gogoanime.gg/frieren-sousou-no-yakusoku",
        )
        assertEquals(
            "aniyomi:1234567890:https://gogoanime.gg/frieren-sousou-no-yakusoku",
            localId.value,
        )
    }

    @Test
    fun `forProvider produces correct structured format`() {
        val localId = LocalIdGenerator.forProvider(
            provider = MetadataProviderId.ANILIST,
            remoteId = "154587",
        )
        assertEquals("al:154587", localId.value)
    }

    @Test
    fun `forProvider with MAL produces mal-prefixed id`() {
        val localId = LocalIdGenerator.forProvider(
            provider = MetadataProviderId.MAL,
            remoteId = "67890",
        )
        assertEquals("mal:67890", localId.value)
    }

    @Test
    fun `prefix extracts the first segment`() {
        val ext = LocalIdGenerator.forExtension(
            ExtensionSystem.ANIYOMI, 1L, "url",
        )
        assertEquals("aniyomi", ext.prefix)

        val prov = LocalIdGenerator.forProvider(
            MetadataProviderId.ANILIST, "123",
        )
        assertEquals("al", prov.prefix)
    }

    @Test
    fun `system property resolves for extension-sourced id`() {
        val localId = LocalIdGenerator.forExtension(
            ExtensionSystem.CLOUDSTREAM, 999L, "media-id",
        )
        assertEquals(ExtensionSystem.CLOUDSTREAM, localId.system)
        assertNull(localId.provider)
        assertTrue(localId.isExtensionSourced)
        assertFalse(localId.isProviderSourced)
    }

    @Test
    fun `provider property resolves for provider-sourced id`() {
        val localId = LocalIdGenerator.forProvider(
            MetadataProviderId.TMDB, "12345",
        )
        assertEquals(MetadataProviderId.TMDB, localId.provider)
        assertNull(localId.system)
        assertTrue(localId.isProviderSourced)
        assertFalse(localId.isExtensionSourced)
    }

    @Test
    fun `parse round-trips extension-sourced id`() {
        val original = LocalIdGenerator.forExtension(
            ExtensionSystem.ANIYOMI, 1234567890L,
            "https://gogoanime.gg/frieren-sousou-no-yakusoku",
        )
        val parsed = LocalIdGenerator.parse(original)
        assertNotNull(parsed)
        assertTrue(parsed is ParsedLocalId.Extension)
        val ext = parsed as ParsedLocalId.Extension
        assertEquals(ExtensionSystem.ANIYOMI, ext.system)
        assertEquals(1234567890L, ext.extensionId)
        assertEquals("https://gogoanime.gg/frieren-sousou-no-yakusoku", ext.sourceContentId)
    }

    @Test
    fun `parse round-trips provider-sourced id`() {
        val original = LocalIdGenerator.forProvider(
            MetadataProviderId.ANILIST, "154587",
        )
        val parsed = LocalIdGenerator.parse(original)
        assertNotNull(parsed)
        assertTrue(parsed is ParsedLocalId.Provider)
        val prov = parsed as ParsedLocalId.Provider
        assertEquals(MetadataProviderId.ANILIST, prov.provider)
        assertEquals("154587", prov.remoteId)
    }

    @Test
    fun `parse handles sourceContentId containing colons`() {
        // URLs often contain colons — the parser must preserve them in the 3rd segment.
        val original = LocalIdGenerator.forExtension(
            ExtensionSystem.ANIYOMI, 1L,
            "https://example.com:8080/path?query=value",
        )
        val parsed = LocalIdGenerator.parse(original)
        assertNotNull(parsed)
        val ext = parsed as ParsedLocalId.Extension
        assertEquals("https://example.com:8080/path?query=value", ext.sourceContentId)
    }

    @Test
    fun `parse returns null for unknown system prefix`() {
        val bogus = LocalId.unsafe("unknownsystem:123:url")
        assertNull(LocalIdGenerator.parse(bogus))
    }

    @Test
    fun `parse returns null for unknown provider prefix`() {
        val bogus = LocalId.unsafe("xx:123")
        assertNull(LocalIdGenerator.parse(bogus))
    }

    @Test
    fun `parse returns null for non-numeric extensionId`() {
        val bogus = LocalId.unsafe("aniyomi:notanumber:url")
        assertNull(LocalIdGenerator.parse(bogus))
    }

    @Test
    fun `parse returns null for blank sourceContentId`() {
        val bogus = LocalId.unsafe("aniyomi:123:")
        assertNull(LocalIdGenerator.parse(bogus))
    }

    @Test
    fun `forExtension rejects blank sourceContentId`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 1L, "")
        }
    }

    @Test
    fun `forExtension rejects non-positive extensionId`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 0L, "url")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, -1L, "url")
        }
    }

    @Test
    fun `forProvider rejects blank remoteId`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalIdGenerator.forProvider(MetadataProviderId.ANILIST, "")
        }
    }

    @Test
    fun `unsafe bypasses validation for trusted DB values`() {
        // unsafe() must NOT throw even for malformed values — it's for reading
        // potentially-corrupt DB rows without crashing the mapper.
        val noColon = LocalId.unsafe("nocolonhere")
        assertEquals("nocolonhere", noColon.value)
        val blank = LocalId.unsafe("")
        assertEquals("", blank.value)
    }

    @Test
    fun `generators reject invalid inputs at creation time`() {
        // Validation lives in the generators, not the value class constructor,
        // so that DB reads (via unsafe) never crash.
        assertThrows(IllegalArgumentException::class.java) {
            LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 1L, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalIdGenerator.forProvider(MetadataProviderId.ANILIST, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 0L, "url")
        }
    }

    @Test
    fun `ExtensionSystem fromKey round-trips all values`() {
        for (system in ExtensionSystem.entries) {
            assertEquals(system, ExtensionSystem.fromKey(system.key))
        }
    }

    @Test
    fun `ExtensionSystem fromKey returns null for unknown key`() {
        assertNull(ExtensionSystem.fromKey("nonexistent"))
    }

    @Test
    fun `MetadataProviderId fromKey round-trips all values`() {
        for (provider in MetadataProviderId.entries) {
            assertEquals(provider, MetadataProviderId.fromKey(provider.key))
        }
    }

    @Test
    fun `MetadataProviderId fromKey returns null for unknown key`() {
        assertNull(MetadataProviderId.fromKey("xx"))
    }

    @Test
    fun `two extension ids with different sources are distinct`() {
        val a = LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 1L, "url-a")
        val b = LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 1L, "url-b")
        assertFalse(a.value == b.value, "Different sourceContentId must produce different local_ids")
    }

    @Test
    fun `two extension ids with different extensionIds are distinct`() {
        val a = LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 1L, "same-url")
        val b = LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 2L, "same-url")
        assertFalse(a.value == b.value, "Different extensionId must produce different local_ids")
    }

    @Test
    fun `two extension ids with different systems are distinct`() {
        val a = LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 1L, "same-url")
        val b = LocalIdGenerator.forExtension(ExtensionSystem.CLOUDSTREAM, 1L, "same-url")
        assertFalse(a.value == b.value, "Different system must produce different local_ids")
    }
}
