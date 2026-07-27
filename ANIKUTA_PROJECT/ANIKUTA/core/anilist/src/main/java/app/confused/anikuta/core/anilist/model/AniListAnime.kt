package app.confused.anikuta.core.anilist.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AniList anime data — the fields we use for browsing + details.
 *
 * This is a subset of AniList's Media type. See:
 * https://docs.anilist.co/reference/interface/media/
 */
@Serializable
data class AniListAnime(
    val id: Int,
    val title: AniListTitle,
    @SerialName("coverImage") val coverImage: AniListCoverImage? = null,
    @SerialName("averageScore") val averageScore: Int? = null,
    @SerialName("meanScore") val meanScore: Int? = null,
    @SerialName("popularity") val popularity: Int? = null,
    @SerialName("favourites") val favourites: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val description: String? = null,
    @SerialName("bannerImage") val bannerImage: String? = null,
    val genres: List<String>? = null,
    val season: String? = null,
    @SerialName("seasonYear") val seasonYear: Int? = null,
    @SerialName("startDate") val startDate: AniListFuzzyDate? = null,
    @SerialName("endDate") val endDate: AniListFuzzyDate? = null,
    @SerialName("studios") val studios: AniListStudioConnection? = null,
    @SerialName("nextAiringEpisode") val nextAiringEpisode: AniListAiringSchedule? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("countryOfOrigin") val countryOfOrigin: String? = null,
    @SerialName("isAdult") val isAdult: Boolean? = null,
    @SerialName("idMal") val idMal: Int? = null,
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
) {
    /** The best display title (English > Romaji > Native). */
    val display: String get() = english ?: romaji ?: native ?: "Unknown"
}

@Serializable
data class AniListCoverImage(
    val medium: String? = null,
    val large: String? = null,
    val extraLarge: String? = null,
    val color: String? = null, // Hex color like "#FF5722" from AniList
) {
    val best: String? get() = extraLarge ?: large ?: medium
}

@Serializable
data class AniListFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
) {
    /** Format as "Apr 12, 2024" or partial if fields are missing. */
    fun displayString(): String? {
        if (year == null && month == null && day == null) return null
        val monthName = month?.let {
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").getOrNull(it - 1)
        }
        return listOfNotNull(monthName, day?.toString(), year?.toString())
            .joinToString(" ")
    }
}

@Serializable
data class AniListStudioConnection(
    val nodes: List<AniListStudio>? = null,
) {
    /** Main studio (animation producer). */
    val mainStudio: AniListStudio? get() = nodes?.firstOrNull { it.isAnimationStudio }
}

@Serializable
data class AniListStudio(
    val id: Int,
    val name: String,
    @SerialName("isAnimationStudio") val isAnimationStudio: Boolean = false,
)

@Serializable
data class AniListAiringSchedule(
    val id: Int? = null,
    @SerialName("airingAt") val airingAt: Int? = null, // Unix timestamp (seconds)
    @SerialName("timeUntilAiring") val timeUntilAiring: Int? = null, // Seconds until airing
    val episode: Int? = null,
)

/**
 * Airing-schedule info for one anime — the payload of `AniListApi.fetchAiringSchedule`.
 *
 * Used by the Updates > Schedule tab. Parsed manually (not via `@Serializable`)
 * in `AniListApi.parseAiringSchedule` because the query selects only a subset
 * of fields and we don't want to add nullable list fields to the
 * `@Serializable AniListAnime` model used by every other query.
 *
 * @property anilistId The AniList media ID.
 * @property title Display title (English > Romaji > Native > "Unknown").
 * @property coverUrl The `coverImage.large` URL (or null).
 * @property coverColor Hex color string from AniList (e.g. "#FF5722"), or null.
 * @property nextAiringEpisode The single next airing episode, or null if the
 *   anime has finished / no scheduled episode.
 * @property upcomingEpisodes All not-yet-aired episodes from AniList's
 *   `airingSchedule(notYetAired: true)`. May be empty.
 */
data class AiringScheduleInfo(
    val anilistId: Int,
    val title: String,
    val coverUrl: String?,
    val coverColor: String?,
    val nextAiringEpisode: AniListAiringSchedule?,
    val upcomingEpisodes: List<AniListAiringSchedule>,
)

// ── Extension helpers ────────────────────────────────────────────────────────

/** The display title for an [AniListAnime]. */
val AniListAnime.displayTitle: String get() = title.display

/** The best cover URL for an [AniListAnime]. */
val AniListAnime.coverUrl: String? get() = coverImage?.best

/** The cover color as a hex string (e.g. "#FF5722") from AniList, or null. */
val AniListAnime.coverColorHex: String? get() = coverImage?.color

/** The season + year display string (e.g. "Spring 2024"), or null. */
val AniListAnime.seasonDisplay: String? get() {
    val s = season ?: return null
    val y = seasonYear ?: return s
    return "$s $y"
}

/** The main studio name, or null. */
val AniListAnime.studioName: String? get() = studios?.mainStudio?.name

/** The start date display string, or null. */
val AniListAnime.startDateDisplay: String? get() = startDate?.displayString()

/** Format the next airing episode as "EP N in 2d 5h", or null. */
val AniListAnime.nextAiringDisplay: String? get() {
    val next = nextAiringEpisode ?: return null
    val ep = next.episode ?: return null
    val secs = next.timeUntilAiring ?: return "EP $ep soon"
    val days = secs / 86400
    val hours = (secs % 86400) / 3600
    val mins = (secs % 3600) / 60
    val timeStr = when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "soon"
    }
    return "EP $ep in $timeStr"
}
