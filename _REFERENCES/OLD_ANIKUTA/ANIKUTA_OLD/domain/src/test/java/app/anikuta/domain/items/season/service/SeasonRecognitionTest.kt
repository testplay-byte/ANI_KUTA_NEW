package app.anikuta.domain.items.season.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SeasonRecognition] — the regex-based season-number parser.
 *
 * Mirrors [EpisodeRecognitionTest] but for seasons. A regression here breaks
 * season-based episode grouping for series anime.
 */
class SeasonRecognitionTest {

    // -------------------------------------------------------------------------
    // Basic cases: s.xx, s xx, season xx, sxx
    // -------------------------------------------------------------------------

    @Test
    fun parseSeasonNumber_s02_returns2() {
        // From the source docstring: Boku.no.Hero.Academia.S02.1080p-ITH -R> 2
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Boku no Hero Academia",
            seasonName = "Boku.no.Hero.Academia.S02.1080p-ITH",
        )
        assertEquals(2.0, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_seasonPrefix_returnsNumber() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime Season 3",
        )
        assertEquals(3.0, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_bareS_returnsNumber() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime s4",
        )
        assertEquals(4.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Single number in title
    // -------------------------------------------------------------------------

    @Test
    fun parseSeasonNumber_singleNumber_returnsIt() {
        // From the source docstring: Boku no Hero Academia 2 -R> 2
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Boku no Hero Academia",
            seasonName = "Boku no Hero Academia 2",
        )
        assertEquals(2.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Decimal / sub-season numbers
    // -------------------------------------------------------------------------

    @Test
    fun parseSeasonNumber_decimalNumber_returnsDecimal() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime Season 2.5",
        )
        assertEquals(2.5, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_commaDecimal_returnsDecimal() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime Season 2,5",
        )
        assertEquals(2.5, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Alpha suffixes: extra, omake, special, .a/.b/.c
    // -------------------------------------------------------------------------

    @Test
    fun parseSeasonNumber_extraSuffix_adds99() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime 2 extra",
        )
        assertEquals(2.99, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_specialSuffix_adds97() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime 2 special",
        )
        assertEquals(2.97, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_alphaSuffix_a_adds1() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime 2.a",
        )
        assertEquals(2.1, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // Tags / quality markers (should be ignored)
    // -------------------------------------------------------------------------

    @Test
    fun parseSeasonNumber_qualityTag_ignored() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime Season 2 (BD Remux 1080p H.264 FLAC)",
        )
        assertEquals(2.0, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_tagStripped() {
        // From the source docstring: [FLE] Boku no Hero Academia Season 2 (BD Remux 1080p H.264 FLAC) [Dual Audio]
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Boku no Hero Academia",
            seasonName = "[FLE] Boku no Hero Academia Season 2 (BD Remux 1080p H.264 FLAC) [Dual Audio]",
        )
        assertEquals(2.0, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_yearInParens_ignored() {
        // (2017) is an unwanted year tag — should be removed, not parsed as the season.
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Boku no Hero Academia",
            seasonName = "Boku no Hero Academia (2017) Season 2",
        )
        assertEquals(2.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // No number found
    // -------------------------------------------------------------------------

    @Test
    fun parseSeasonNumber_noNumber_returnsNegativeOne() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime Special OVA",
        )
        assertEquals(-1.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // seasonNumber override (known number passed in)
    // -------------------------------------------------------------------------

    @Test
    fun parseSeasonNumber_knownNumberPassedThrough() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime Season 2",
            seasonNumber = 5.0,
        )
        assertEquals(5.0, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_unknownNumberFlagParsesFromName() {
        // seasonNumber == -1.0 means "unknown" — should still parse from name.
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "Some Anime",
            seasonName = "Some Anime Season 2",
            seasonNumber = -1.0,
        )
        assertEquals(2.0, result, 0.001)
    }

    @Test
    fun parseSeasonNumber_animeTitleRemovedFromSeasonName() {
        val result = SeasonRecognition.parseSeasonNumber(
            animeTitle = "One Piece",
            seasonName = "One Piece 2",
        )
        assertEquals(2.0, result, 0.001)
    }
}
