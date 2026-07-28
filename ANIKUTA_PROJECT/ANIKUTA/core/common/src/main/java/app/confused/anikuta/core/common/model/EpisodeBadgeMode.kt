package app.confused.anikuta.core.common.model

/**
 * Controls what the episode badge on library cards shows.
 *
 * - [TOTAL] — shows the total episode count from AniList (`Anime.totalEpisodes`).
 * - [RELEASED] — shows the number of aired episodes (`Anime.releasedEpisodes`).
 *   This is the user's preferred default — it focuses on how many episodes
 *   are actually available to watch, not the planned total.
 * - [OFF] — no episode badge.
 */
enum class EpisodeBadgeMode {
    TOTAL,
    RELEASED,
    OFF,
}
