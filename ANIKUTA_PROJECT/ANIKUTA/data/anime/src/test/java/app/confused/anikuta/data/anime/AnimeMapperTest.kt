package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.AnimeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AnimeMapper].
 *
 * Verifies the mapping logic between SQLDelight column values (Long, String)
 * and the [Anime] domain model (Boolean, Int, List<String>).
 *
 * Per `RULES/ai-agent-rules.md` §10: covers success path + edge cases.
 */
class AnimeMapperTest {

    @Test
    fun `maps full anime with all fields`() {
        val anime = AnimeMapper.map(
            id = 1L,
            url = "https://example.com/anime/1",
            title = "Test Anime",
            artist = "Test Artist",
            author = "Test Author",
            description = "A test anime.",
            genre = "Action,Comedy,Drama",
            coverUrl = "https://example.com/cover.jpg",
            status = AnimeStatus.ONGOING.toLong(),
            thumbnailUrl = "https://example.com/thumb.jpg",
            favorite = 1L,
            sourceId = 100L,
            dateAdded = 1700000000L,
            viewerFlags = 0L,
            nextUpdate = 1700100000L,
            updateStrategy = 0L,
            coverLastModified = 1700000000L,
            releaseDate = 1699000000L,
            lastRefresh = 1700050000L,
            lastMetadataFetch = 1700060000L,
            nextEpisodeCheck = 1700110000L,
            anilistId = 12345L,
            coverColor = "#B1F256",
            score = 85.0,
            totalEpisodes = 24L,
            lastWatched = 1700070000L,
        )

        assertEquals(1L, anime.id)
        assertEquals("Test Anime", anime.title)
        assertEquals(listOf("Action", "Comedy", "Drama"), anime.genre)
        assertEquals(AnimeStatus.ONGOING, anime.status)
        assertTrue(anime.favorite)
        assertEquals(1700050000L, anime.lastRefresh)
        assertEquals(1700060000L, anime.lastMetadataFetch)
        assertEquals(1700110000L, anime.nextEpisodeCheck)
    }

    @Test
    fun `maps anime with null fields`() {
        val anime = AnimeMapper.map(
            id = 2L,
            url = "https://example.com/anime/2",
            title = "Minimal Anime",
            artist = null,
            author = null,
            description = null,
            genre = null,
            coverUrl = null,
            status = 0L,
            thumbnailUrl = null,
            favorite = 0L,
            sourceId = 100L,
            dateAdded = 1700000000L,
            viewerFlags = 0L,
            nextUpdate = 0L,
            updateStrategy = 0L,
            coverLastModified = 0L,
            releaseDate = null,
            lastRefresh = 0L,
            lastMetadataFetch = null,
            nextEpisodeCheck = null,
            anilistId = null,
            coverColor = null,
            score = null,
            totalEpisodes = null,
            lastWatched = 0L,
        )

        assertEquals(2L, anime.id)
        assertEquals("Minimal Anime", anime.title)
        assertTrue(anime.genre.isEmpty())
        assertTrue(!anime.favorite)
        assertEquals(null, anime.releaseDate)
        assertEquals(null, anime.lastMetadataFetch)
        assertEquals(null, anime.nextEpisodeCheck)
    }

    @Test
    fun `maps anime with empty genre string`() {
        val anime = AnimeMapper.map(
            id = 3L,
            url = "https://example.com/anime/3",
            title = "No Genre",
            artist = null,
            author = null,
            description = null,
            genre = "",
            coverUrl = null,
            status = 0L,
            thumbnailUrl = null,
            favorite = 0L,
            sourceId = 100L,
            dateAdded = 1700000000L,
            viewerFlags = 0L,
            nextUpdate = 0L,
            updateStrategy = 0L,
            coverLastModified = 0L,
            releaseDate = null,
            lastRefresh = 0L,
            lastMetadataFetch = null,
            nextEpisodeCheck = null,
            anilistId = null,
            coverColor = null,
            score = null,
            totalEpisodes = null,
            lastWatched = 0L,
        )

        assertTrue(anime.genre.isEmpty())
    }

    @Test
    fun `genre with whitespace is filtered`() {
        val anime = AnimeMapper.map(
            id = 4L,
            url = "",
            title = "Whitespace Genre",
            artist = null,
            author = null,
            description = null,
            genre = "Action, , Comedy,",
            coverUrl = null,
            status = 0L,
            thumbnailUrl = null,
            favorite = 0L,
            sourceId = 100L,
            dateAdded = 0L,
            viewerFlags = 0L,
            nextUpdate = 0L,
            updateStrategy = 0L,
            coverLastModified = 0L,
            releaseDate = null,
            lastRefresh = 0L,
            lastMetadataFetch = null,
            nextEpisodeCheck = null,
            anilistId = null,
            coverColor = null,
            score = null,
            totalEpisodes = null,
            lastWatched = 0L,
        )

        assertEquals(listOf("Action", "Comedy"), anime.genre)
    }
}
