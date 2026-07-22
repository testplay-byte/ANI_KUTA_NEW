package app.confused.anikuta.core.common.model

/**
 * A link between an anime and a category (the anime_category junction row).
 *
 * Used by the library page to filter anime by category tab.
 */
data class AnimeCategoryLink(
    val animeId: Long,
    val categoryId: Long,
)
