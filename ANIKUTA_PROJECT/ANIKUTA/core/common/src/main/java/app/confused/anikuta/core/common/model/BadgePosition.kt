package app.confused.anikuta.core.common.model

/**
 * Where a badge (episode count or score) is positioned on a library grid card.
 *
 * - [TOP_START] — top-left corner.
 * - [TOP_END] — top-right corner.
 * - [BOTTOM_START] — bottom-left corner (NOT available in compact grid — the
 *   title overlay occupies the bottom).
 * - [BOTTOM_END] — bottom-right corner (NOT available in compact grid).
 *
 * Per user: "if the user has selected compact grid then he will not be given
 * options to select the episode's badges on the bottom right or bottom left
 * corners. He will only be shown the option to show the badges on the top right
 * or top left corners."
 */
enum class BadgePosition {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}
