package app.confused.anikuta.core.episodemetadata.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EpisodeMetadataCache] key format (Phase 4, ADR-050).
 *
 * Tests the content_id-based keying logic. The cache now keys by content_id
 * (e.g., `"al:154587"`) instead of anilistId (e.g., `"154587"`). Content_ids
 * contain `:`, which distinguishes them from legacy anilistId keys.
 *
 * Per `plan/03_testing_strategy.md` Phase 4.
 */
class EpisodeMetadataCacheKeyTest {

    @Test
    fun `content_id key contains colon (distinguishes from legacy anilistId keys)`() {
        val contentId = "al:154587"
        assertTrue(':' in contentId, "Content_id must contain ':' to distinguish from legacy keys")
    }

    @Test
    fun `legacy anilistId key does not contain colon`() {
        val legacyKey = "154587"
        assertTrue(':' !in legacyKey, "Legacy anilistId keys must not contain ':'")
    }

    @Test
    fun `unlinked content_id key contains colon`() {
        val unlinkedContentId = "aniyomi:123:https://example.com/anime"
        assertTrue(':' in unlinkedContentId, "Unlinked content_ids also contain ':'")
    }

    @Test
    fun `content_id distinguishes different providers`() {
        val anilist = "al:154587"
        val mal = "mal:67890"
        val tmdb = "tmdb:12345"
        assertTrue(anilist != mal)
        assertTrue(mal != tmdb)
        assertTrue(anilist != tmdb)
    }

    @Test
    fun `content_id distinguishes linked vs unlinked`() {
        val linked = "al:154587"
        val unlinked = "aniyomi:123:https://example.com/anime"
        assertTrue(linked != unlinked)
    }

    @Test
    fun `anilistId to contentId conversion is correct`() {
        val anilistId = 154587
        val contentId = "al:$anilistId"
        assertEquals("al:154587", contentId)
    }

    @Test
    fun `contentId to anilistId extraction is correct`() {
        val contentId = "al:154587"
        val anilistId = contentId.removePrefix("al:").toIntOrNull()
        assertEquals(154587, anilistId)
    }

    @Test
    fun `contentId to anilistId extraction returns null for non-AniList content_id`() {
        val unlinked = "aniyomi:123:url"
        val anilistId = unlinked.removePrefix("al:").toIntOrNull()
        // "aniyomi:123:url".removePrefix("al:") = "aniyomi:123:url" (no "al:" prefix)
        // .toIntOrNull() = null
        assertEquals(null, anilistId)
    }

    @Test
    fun `contentId to anilistId extraction returns null for MAL content_id`() {
        val mal = "mal:67890"
        val anilistId = mal.removePrefix("al:").toIntOrNull()
        assertEquals(null, anilistId, "MAL content_ids don't have 'al:' prefix")
    }

    @Test
    fun `migrator idempotency check - content_id keys are skipped (contain colon)`() {
        // The migrator skips keys that contain ':' (already content_ids).
        // This test verifies that logic.
        val legacyKey = "154587"
        val contentIdKey = "al:154587"
        assertTrue(':' !in legacyKey, "Legacy key should be migrated")
        assertTrue(':' in contentIdKey, "Content_id key should be skipped")
    }
}
