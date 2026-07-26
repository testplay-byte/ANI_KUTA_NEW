package app.anikuta.domain.items.episode.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EpisodeRecognition] — the regex-based episode-number parser.
 *
 * This is one of the most bug-prone pieces of pure logic in the app: it parses
 * episode numbers from messy release titles like "One Piece - 1015 [1080p]",
 * "s01e01v2", "Episode 12.5", etc. A silent regression here breaks every
 * episode list in the app.
 */
class EpisodeRecognitionTest {

    // -------------------------------------------------------------------------
    // Basic cases: ep.xx, exx, episode xx, ep xx
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_s01e01_returns1() {
        // From the source docstring: kaguya-sama wa kokurasetai - s01e01v2 (BD 1080p HEVC) -R> 01
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "kaguya-sama wa kokurasetai",
            episodeName = "kaguya-sama wa kokurasetai - s01e01v2 (BD 1080p HEVC)",
        )
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_episodePrefix_returnsNumber() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime Episode 12",
        )
        assertEquals(12.0, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_epPrefix_returnsNumber() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime ep 5",
        )
        assertEquals(5.0, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_bareE_returnsNumber() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime e3",
        )
        assertEquals(3.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Single number in title
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_singleNumber_returnsIt() {
        // From the source docstring: Bleach 567: Down With Snowwhite -R> 567
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Bleach",
            episodeName = "Bleach 567: Down With Snowwhite",
        )
        assertEquals(567.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Decimal / sub-episode numbers
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_decimalNumber_returnsDecimal() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime Episode 12.5",
        )
        assertEquals(12.5, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_commaDecimal_returnsDecimal() {
        // Commas are converted to dots: "12,5" -> 12.5
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime Episode 12,5",
        )
        assertEquals(12.5, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Alpha suffixes: extra, omake, special, .a/.b/.c
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_extraSuffix_adds99() {
        // "12 extra" -> 12.99
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime 12 extra",
        )
        assertEquals(12.99, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_omakeSuffix_adds98() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime 12 omake",
        )
        assertEquals(12.98, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_specialSuffix_adds97() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime 12 special",
        )
        assertEquals(12.97, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_alphaSuffix_a_adds1() {
        // x.a -> x.1, x.b -> x.2, etc (single-char alpha, a-i only)
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime 12.a",
        )
        assertEquals(12.1, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_alphaSuffix_b_adds2() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime 12.b",
        )
        assertEquals(12.2, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Tags / quality markers (should be ignored)
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_qualityTag_ignored() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime 12 [1080p]",
        )
        assertEquals(12.0, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_versionTag_ignored() {
        // "v2", "ver2", "version2", "season2", "s2" are unwanted and removed
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime 12 v2",
        )
        assertEquals(12.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // No number found
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_noNumber_returnsNegativeOne() {
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime Special OVA",
        )
        // -1.0 means "not recognized"
        assertEquals(-1.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // episodeNumber override (known number passed in)
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_knownNumberPassedThrough() {
        // If episodeNumber is known and valid (> -1 or == -2), return it as-is.
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime Episode 5",
            episodeNumber = 99.0,
        )
        assertEquals(99.0, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_unknownNumberFlagParsesFromName() {
        // episodeNumber == -1.0 means "unknown" — should still parse from name.
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Some Anime",
            episodeName = "Some Anime Episode 5",
            episodeNumber = -1.0,
        )
        assertEquals(5.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Tag stripping
    // -------------------------------------------------------------------------

    @Test
    fun parseEpisodeNumber_leadingTagStripped() {
        // [flugel] kaguya-sama... -> kaguya-sama... (tag stripped, then parsed)
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "kaguya-sama wa kokurasetai",
            episodeName = "[flugel] kaguya-sama wa kokurasetai - s01e01v2 (bd 1080p hevc) [multi audio] [80ac7b2e]",
        )
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_animeTitleRemovedFromEpisodeName() {
        // The anime title is removed from the episode name before parsing,
        // so "One Piece - One Piece 1015" still parses to 1015.
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "One Piece",
            episodeName = "One Piece 1015",
        )
        assertEquals(1015.0, result, 0.001)
    }

    @Test
    fun parseEpisodeNumber_returnsNonNegativeForValidInput() {
        // Sanity: a clearly-valid episode name should never return a negative.
        val result = EpisodeRecognition.parseEpisodeNumber(
            animeTitle = "Attack on Titan",
            episodeName = "Attack on Titan Episode 1",
        )
        assertTrue("Expected non-negative episode number, got $result", result >= 0.0)
    }
}
