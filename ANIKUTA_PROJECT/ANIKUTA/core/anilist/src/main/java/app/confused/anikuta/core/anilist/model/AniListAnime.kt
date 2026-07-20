package app.confused.anikuta.core.anilist.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AniList anime data — the fields we use for browsing.
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
    val format: String? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val description: String? = null,
    @SerialName("bannerImage") val bannerImage: String? = null,
    val genres: List<String>? = null,
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
    val color: String? = null,
) {
    val best: String? get() = extraLarge ?: large ?: medium
}

/** The display title for an [AniListAnime]. */
val AniListAnime.displayTitle: String get() = title.display

/** The best cover URL for an [AniListAnime]. */
val AniListAnime.coverUrl: String? get() = coverImage?.best
