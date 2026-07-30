package app.confused.anikuta.core.common.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContentId] + [ContentIdGenerator] + [ContentIdPriority].
 *
 * Per `plan/03_testing_strategy.md` Phase 1: priority order, fallback behavior,
 * user-configurable priority, reproducibility.
 */
class ContentIdTest {

    @Test
    fun `generate with AniList link produces al-prefixed content_id`() {
        val localId = LocalIdGenerator.forExtension(
            ExtensionSystem.ANIYOMI, 1L, "url",
        )
        val contentId = ContentIdGenerator.generate(
            anilistId = 154587,
            localId = localId,
        )
        assertEquals("al:154587", contentId.value)
        assertTrue(contentId.isProviderLinked)
        assertFalse(contentId.isFallback)
        assertEquals(MetadataProviderId.ANILIST, contentId.provider)
    }

    @Test
    fun `generate with null AniList link falls back to local_id`() {
        val localId = LocalIdGenerator.forExtension(
            ExtensionSystem.ANIYOMI, 1L, "https://example.com/anime",
        )
        val contentId = ContentIdGenerator.generate(
            anilistId = null,
            localId = localId,
        )
        assertEquals(localId.value, contentId.value)
        assertTrue(contentId.isFallback)
        assertFalse(contentId.isProviderLinked)
    }

    @Test
    fun `generate with links map uses priority order`() {
        val localId = LocalIdGenerator.forProvider(MetadataProviderId.ANILIST, "1")
        val links = mapOf(
            MetadataProviderId.ANILIST to "154587",
            MetadataProviderId.MAL to "67890",
        )
        // Default priority: AniList first.
        val contentId = ContentIdGenerator.generate(links, localId)
        assertEquals("al:154587", contentId.value)
    }

    @Test
    fun `generate respects user-configured priority order`() {
        val localId = LocalIdGenerator.forProvider(MetadataProviderId.ANILIST, "1")
        val links = mapOf(
            MetadataProviderId.ANILIST to "154587",
            MetadataProviderId.MAL to "67890",
        )
        // User prefers MAL first.
        val malPriority = ContentIdPriority(
            listOf(MetadataProviderId.MAL, MetadataProviderId.ANILIST, MetadataProviderId.TMDB, MetadataProviderId.KITSU)
        )
        val contentId = ContentIdGenerator.generate(links, localId, malPriority)
        assertEquals("mal:67890", contentId.value)
    }

    @Test
    fun `generate falls through priority to first available link`() {
        val localId = LocalIdGenerator.forProvider(MetadataProviderId.ANILIST, "1")
        val links = mapOf(
            MetadataProviderId.TMDB to "12345",
        )
        // Default priority skips AniList + MAL (not in links), uses TMDB.
        val contentId = ContentIdGenerator.generate(links, localId)
        assertEquals("tmdb:12345", contentId.value)
    }

    @Test
    fun `generate with empty links falls back to local_id`() {
        val localId = LocalIdGenerator.forExtension(
            ExtensionSystem.ANIYOMI, 1L, "url",
        )
        val contentId = ContentIdGenerator.generate(
            links = emptyMap(),
            localId = localId,
        )
        assertEquals(localId.value, contentId.value)
        assertTrue(contentId.isFallback)
    }

    @Test
    fun `generate ignores blank remoteIds in links`() {
        val localId = LocalIdGenerator.forProvider(MetadataProviderId.ANILIST, "1")
        val links = mapOf(
            MetadataProviderId.ANILIST to "",   // blank — should be skipped
            MetadataProviderId.MAL to "67890",
        )
        val contentId = ContentIdGenerator.generate(links, localId)
        assertEquals("mal:67890", contentId.value)
    }

    @Test
    fun `generate with null anilistId convenience overload falls back`() {
        val localId = LocalIdGenerator.forProvider(MetadataProviderId.ANILIST, "1")
        val contentId = ContentIdGenerator.generate(
            anilistId = null,
            localId = localId,
        )
        assertEquals(localId.value, contentId.value)
    }

    @Test
    fun `unsafe bypasses validation for trusted DB values`() {
        // unsafe() must NOT throw even for malformed values — it's for reading
        // potentially-corrupt DB rows without crashing the mapper.
        val noColon = ContentId.unsafe("nocolonhere")
        assertEquals("nocolonhere", noColon.value)
        val blank = ContentId.unsafe("")
        assertEquals("", blank.value)
    }

    @Test
    fun `ContentId provider resolves for al-prefix`() {
        val contentId = ContentId("al:154587")
        assertEquals(MetadataProviderId.ANILIST, contentId.provider)
        assertTrue(contentId.isProviderLinked)
    }

    @Test
    fun `ContentId provider is null for fallback (extension-system prefix)`() {
        // A fallback content_id = local_id, which starts with a system key (e.g., "aniyomi").
        val contentId = ContentId("aniyomi:123:url")
        assertNull(contentId.provider)
        assertTrue(contentId.isFallback)
    }

    @Test
    fun `ContentIdPriority default is declaration order`() {
        val default = ContentIdPriority.DEFAULT
        assertEquals(MetadataProviderId.entries.toList(), default.order)
    }

    @Test
    fun `ContentIdPriority rejects empty order`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentIdPriority(emptyList())
        }
    }

    @Test
    fun `ContentIdPriority rejects duplicates`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentIdPriority(
                listOf(MetadataProviderId.ANILIST, MetadataProviderId.ANILIST, MetadataProviderId.MAL)
            )
        }
    }

    @Test
    fun `ContentIdPriority accepts custom order`() {
        val custom = ContentIdPriority(
            listOf(MetadataProviderId.MAL, MetadataProviderId.ANILIST, MetadataProviderId.TMDB, MetadataProviderId.KITSU)
        )
        assertEquals(MetadataProviderId.MAL, custom.order.first())
    }

    @Test
    fun `ContentId is reproducible for same inputs`() {
        val localId = LocalIdGenerator.forExtension(ExtensionSystem.ANIYOMI, 1L, "url")
        val a = ContentIdGenerator.generate(anilistId = 42, localId = localId)
        val b = ContentIdGenerator.generate(anilistId = 42, localId = localId)
        assertEquals(a.value, b.value, "Same inputs must produce same content_id (deterministic)")
    }
}
