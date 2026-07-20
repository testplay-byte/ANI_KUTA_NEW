package com.testplaybyte.animeapp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── AniList API response models ──────────────────────────────────────────

@Serializable
data class AnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
data class CoverImage(
    val large: String? = null,
    val extraLarge: String? = null,
)

@Serializable
data class NextAiringEpisode(
    val airingAt: Long? = null,
    val episode: Int? = null,
)

@Serializable
data class Anime(
    val id: Int,
    val title: AnimeTitle = AnimeTitle(),
    val coverImage: CoverImage = CoverImage(),
    val bannerImage: String? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val format: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val description: String? = null,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val siteUrl: String? = null,
) {
    val displayTitle: String get() = title.romaji ?: title.english ?: "Unknown"
    val coverUrl: String get() = coverImage.large ?: coverImage.extraLarge ?: ""
    val scoreFormatted: String get() = averageScore?.let { "%.1f".format(it / 10.0) } ?: "—"
}

@Serializable
data class AiringSchedule(
    val id: Int,
    val airingAt: Long,
    val episode: Int,
    val media: Anime,
)

// ── Library / History / Settings models ──────────────────────────────────

@Serializable
enum class LibraryStatus { WATCHING, COMPLETED, PLAN }

@Serializable
data class LibraryItem(
    val id: Int,
    val title: String,
    val cover: String,
    val score: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val status: LibraryStatus = LibraryStatus.WATCHING,
    val addedAt: Long = 0,
)

@Serializable
data class HistoryItem(
    val id: Int,
    val title: String,
    val cover: String,
    val viewedAt: Long,
    val episode: Int = 1,
    val totalEpisodes: Int? = null,
    val progress: Int = 0,
    val banner: String = "",
)

enum class PosterStyle { ROUNDED, SOFT, SHARP }
enum class CardDensity { COMPACT, DEFAULT, COMFORTABLE }
enum class AnimSpeed { FAST, NORMAL, SLOW }
enum class LibraryLayout { GRID, LIST }
enum class TextPlacement { BELOW, OVERLAY }
enum class EpisodePosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, HIDDEN }

data class AppSettings(
    val darkTheme: Boolean = true,
    val posterStyle: PosterStyle = PosterStyle.ROUNDED,
    val cardDensity: CardDensity = CardDensity.DEFAULT,
    val animSpeed: AnimSpeed = AnimSpeed.NORMAL,
    val singleLineTitles: Boolean = true,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val libraryColumns: Int = 3,
    val libraryTextPlacement: TextPlacement = TextPlacement.BELOW,
    val libraryShowFormat: Boolean = true,
    val libraryShowEpisodes: Boolean = true,
    val libraryEpisodePosition: EpisodePosition = EpisodePosition.BOTTOM_RIGHT,
)
