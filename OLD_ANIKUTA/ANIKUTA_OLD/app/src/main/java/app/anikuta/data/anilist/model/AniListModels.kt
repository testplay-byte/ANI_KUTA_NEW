package app.anikuta.data.anilist.model

import kotlinx.serialization.Serializable

/**
 * AniList anime data models for the home page.
 * These are our own models (not from aniyomi) — simplified from the AniList API response.
 */

@Serializable
data class AniListAnime(
    val id: Int,
    val idMal: Int? = null,
    val title: AniListTitle,
    val coverImage: AniListCoverImage,
    val bannerImage: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val genres: List<String>? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val nextAiringEpisode: AniListNextAiring? = null,
    val streamingEpisodes: List<AniListStreamingEpisode>? = null,
)

@Serializable
data class AniListStreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
) {
    fun preferred(): String = english ?: romaji ?: native ?: "Unknown"
}

@Serializable
data class AniListCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
    val color: String? = null,
) {
    fun best(): String? = extraLarge ?: large ?: medium
}

@Serializable
data class AniListNextAiring(
    val airingAt: Int? = null,
    val episode: Int? = null,
    val timeUntilAiring: Int? = null,
)

@Serializable
data class AniListAiringSchedule(
    val airingAt: Int,
    val episode: Int,
    val media: AniListAnime,
)

@Serializable
data class AniListPage(
    val Page: AniListPageData,
)

@Serializable
data class AniListPageData(
    val media: List<AniListAnime> = emptyList(),
)

@Serializable
data class AniListAiringPage(
    val Page: AniListAiringPageData,
)

@Serializable
data class AniListAiringPageData(
    val airingSchedules: List<AniListAiringSchedule> = emptyList(),
)

@Serializable
data class AniListGenresResponse(
    val data: AniListGenresData,
)

@Serializable
data class AniListGenresData(
    val GenreCollection: List<String> = emptyList(),
)

@Serializable
data class AniListMediaResponse(
    val data: AniListMediaData,
)

@Serializable
data class AniListMediaData(
    val Media: AniListAnime,
)

/** GraphQL request body — built manually (not @Serializable due to Any? type). */
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
)
