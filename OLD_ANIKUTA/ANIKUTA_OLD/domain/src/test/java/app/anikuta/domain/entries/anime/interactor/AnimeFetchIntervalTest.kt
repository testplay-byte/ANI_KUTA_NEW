package app.anikuta.domain.entries.anime.interactor

import app.anikuta.domain.items.episode.model.Episode
import app.anikuta.domain.entries.anime.model.Anime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [AnimeFetchInterval] — calculates the refresh interval for an
 * anime based on its episode upload/fetch dates.
 *
 * Tests target the pure-logic methods [calculateInterval] (internal) and
 * [getWindow] (public). The full [toAnimeUpdate] suspend method requires a
 * `GetEpisodesByAnimeId` dependency and is tested via manual testing.
 */
class AnimeFetchIntervalTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val interactor = AnimeFetchInterval(getEpisodesByAnimeId = throw NotImplementedError("not used in these tests"))

    // -------------------------------------------------------------------------
    // calculateInterval — the 3-tier logic
    // -------------------------------------------------------------------------

    @Test
    fun calculateInterval_emptyEpisodes_returnsDefault7() {
        // No episodes → falls through to the default 7-day interval.
        val interval = interactor.calculateInterval(episodes = emptyList(), zone = zone)
        assertEquals(7, interval)
    }

    @Test
    fun calculateInterval_noUploadDates_returnsDefault7() {
        // Episodes exist but none have valid dateUpload (> 0) and not enough
        // fetch dates → default 7.
        val episodes = listOf(
            episode(dateFetch = 0L, dateUpload = 0L),
            episode(dateFetch = 0L, dateUpload = 0L),
        )
        val interval = interactor.calculateInterval(episodes = episodes, zone = zone)
        assertEquals(7, interval)
    }

    @Test
    fun calculateInterval_enoughUploadDates_usesUploadDelta() {
        // 3+ episodes with valid upload dates spaced 7 days apart → interval 7.
        val now = ZonedDateTime.of(2026, 1, 10, 0, 0, 0, 0, zone)
        val episodes = listOf(
            episode(dateUpload = now.toEpochMilli()),
            episode(dateUpload = now.minusDays(7).toEpochMilli()),
            episode(dateUpload = now.minusDays(14).toEpochMilli()),
        )
        val interval = interactor.calculateInterval(episodes = episodes, zone = zone)
        assertEquals(7, interval)
    }

    @Test
    fun calculateInterval_uploadDatesSpaced3Days_returns3() {
        val now = ZonedDateTime.of(2026, 1, 10, 0, 0, 0, 0, zone)
        val episodes = listOf(
            episode(dateUpload = now.toEpochMilli()),
            episode(dateUpload = now.minusDays(3).toEpochMilli()),
            episode(dateUpload = now.minusDays(6).toEpochMilli()),
        )
        val interval = interactor.calculateInterval(episodes = episodes, zone = zone)
        assertEquals(3, interval)
    }

    @Test
    fun calculateInterval_enoughFetchDatesButNoUpload_usesFetchDelta() {
        // Fewer than 3 upload dates, but 3+ fetch dates spaced 7 days apart → uses fetch delta.
        val now = ZonedDateTime.of(2026, 1, 10, 0, 0, 0, 0, zone)
        val episodes = listOf(
            episode(dateUpload = 0L, dateFetch = now.toEpochMilli()),
            episode(dateUpload = 0L, dateFetch = now.minusDays(7).toEpochMilli()),
            episode(dateUpload = 0L, dateFetch = now.minusDays(14).toEpochMilli()),
        )
        val interval = interactor.calculateInterval(episodes = episodes, zone = zone)
        assertEquals(7, interval)
    }

    @Test
    fun calculateInterval_intervalClampedToMax28() {
        // Upload dates spaced 60 days apart → raw interval 30, clamped to MAX_INTERVAL (28).
        val now = ZonedDateTime.of(2026, 1, 10, 0, 0, 0, 0, zone)
        val episodes = listOf(
            episode(dateUpload = now.toEpochMilli()),
            episode(dateUpload = now.minusDays(60).toEpochMilli()),
            episode(dateUpload = now.minusDays(120).toEpochMilli()),
        )
        val interval = interactor.calculateInterval(episodes = episodes, zone = zone)
        // 120 days / 2 periods = 60, but coerced to MAX_INTERVAL (28).
        assertEquals(AnimeFetchInterval.MAX_INTERVAL, interval)
        assertEquals(28, interval)
    }

    @Test
    fun calculateInterval_intervalClampedToMin1() {
        // Upload dates spaced 0 days apart (same day) → 0/2 = 0, coerced to min 1.
        val now = ZonedDateTime.of(2026, 1, 10, 0, 0, 0, 0, zone)
        val episodes = listOf(
            episode(dateUpload = now.toEpochMilli()),
            episode(dateUpload = now.toEpochMilli()),
            episode(dateUpload = now.toEpochMilli()),
        )
        val interval = interactor.calculateInterval(episodes = episodes, zone = zone)
        assertTrue("Expected interval >= 1, got $interval", interval >= 1)
    }

    @Test
    fun calculateInterval_maxIntervalConstantIs28() {
        // Guard against accidentally changing MAX_INTERVAL (other logic depends on it).
        assertEquals(28, AnimeFetchInterval.MAX_INTERVAL)
    }

    // -------------------------------------------------------------------------
    // getWindow — the grace-period window around "today"
    // -------------------------------------------------------------------------

    @Test
    fun getWindow_returnsLowerAndUpperBounds() {
        val today = ZonedDateTime.of(2026, 1, 10, 12, 0, 0, 0, zone)
        val window = interactor.getWindow(today)

        // GRACE_PERIOD = 1 day. Lower = today midnight - 1 day; upper = today midnight + 1 day - 1 ms.
        val lowerExpected = today.toLocalDate().atStartOfDay(zone).minusDays(1).toEpochSecond() * 1000
        val upperExpected = today.toLocalDate().atStartOfDay(zone).plusDays(1).toEpochSecond() * 1000 - 1

        assertEquals(lowerExpected, window.first)
        assertEquals(upperExpected, window.second)
    }

    @Test
    fun getWindow_lowerIsBeforeUpper() {
        val now = ZonedDateTime.now(zone)
        val window = interactor.getWindow(now)
        assertTrue("Lower bound must be < upper bound", window.first < window.second)
    }

    @Test
    fun getWindow_spanIsApproximately2Days() {
        // GRACE_PERIOD=1 on each side → window spans ~2 days (minus 1ms).
        val now = ZonedDateTime.now(zone)
        val window = interactor.getWindow(now)
        val spanMillis = window.second - window.first
        val twoDaysMillis = 2L * 24 * 60 * 60 * 1000
        // Allow a small tolerance for the -1ms and zone rounding.
        assertTrue("Expected ~2 day span, got ${spanMillis}ms", spanMillis in (twoDaysMillis - 1000)..twoDaysMillis)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds an Episode with only the date fields set (the rest defaulted). */
    private fun episode(
        dateUpload: Long = 0L,
        dateFetch: Long = 0L,
    ): Episode {
        return Episode(
            id = 0L,
            animeId = 0L,
            seen = false,
            bookmark = false,
            fillermark = false,
            lastSecondSeen = 0L,
            totalSeconds = 0L,
            dateFetch = dateFetch,
            sourceOrder = 0L,
            url = "",
            name = "",
            dateUpload = dateUpload,
            episodeNumber = 0.0,
            scanlator = null,
            summary = null,
            previewUrl = null,
            lastModifiedAt = 0L,
            version = 0L,
        )
    }
}
