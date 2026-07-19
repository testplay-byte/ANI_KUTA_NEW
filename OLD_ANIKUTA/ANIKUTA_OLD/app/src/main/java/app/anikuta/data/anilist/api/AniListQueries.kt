package app.anikuta.data.anilist.api

/**
 * AniList GraphQL queries for the home page.
 * Based on the AniList API v2: https://docs.anilist.co/
 *
 * Each query returns the fields needed for the home page sections.
 */
object AniListQueries {

    val trending = """
        query Trending(${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: TRENDING_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()

    val popular = """
        query Popular(${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, sort: POPULARITY_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()

    val freshlyUpdated = """
        query FreshlyUpdated(${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, status: RELEASING, sort: UPDATED_AT_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
              nextAiringEpisode { airingAt episode timeUntilAiring }
            }
          }
        }
    """.trimIndent()

    val genres = """
        query Genres {
          GenreCollection
        }
    """.trimIndent()

    val browseByGenre = """
        query BrowseByGenre(${'$'}genre: String, ${'$'}page: Int = 1, ${'$'}perPage: Int = 20) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, genre: ${'$'}genre, sort: POPULARITY_DESC) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()

    val airingSchedule = """
        query AiringSchedule(${'$'}airingAt: Int) {
          Page(page: 1, perPage: 50) {
            airingSchedules(airingAt_lesser: ${'$'}airingAt) {
              airingAt
              episode
              media {
                id
                title { romaji english native }
                coverImage { large medium }
                episodes
              }
            }
          }
        }
    """.trimIndent()

    val animeDetails = """
        query AnimeDetails(${'$'}id: Int) {
          Media(id: ${'$'}id, type: ANIME) {
            id
            idMal
            title { romaji english native }
            coverImage { extraLarge large medium color }
            bannerImage
            description
            averageScore
            meanScore
            episodes
            duration
            genres
            season
            seasonYear
            format
            status
            studios { nodes { name isAnimationStudio } }
            nextAiringEpisode { airingAt episode timeUntilAiring }
            streamingEpisodes { title thumbnail url }
            relations {
              edges { relationType node { id title { romaji english } type } }
            }
          }
        }
    """.trimIndent()

    /**
     * Phase 5 (Q5 decision: AniList-only for Phase 5; extension search in Phase 7).
     * Search query against AniList using SEARCH_MATCH sort — produces results
     * ranked by title similarity to the search term. Returns the same Media
     * field selection as trending/popular so [AniListRepository.parseMediaList]
     * works unchanged.
     */
    val searchAnime = """
        query SearchAnime(${'$'}search: String!, ${'$'}page: Int = 1, ${'$'}perPage: Int = 25) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, search: ${'$'}search, sort: SEARCH_MATCH) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()

    /** Search including adult results (isAdult: true). */
    val searchAnimeWithAdult = """
        query SearchAnimeWithAdult(${'$'}search: String!, ${'$'}page: Int = 1, ${'$'}perPage: Int = 25) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            media(type: ANIME, search: ${'$'}search, sort: SEARCH_MATCH, isAdult: true) {
              id
              title { romaji english native }
              coverImage { extraLarge large medium color }
              bannerImage
              averageScore
              episodes
              genres
              season
              seasonYear
              format
              status
            }
          }
        }
    """.trimIndent()
}
