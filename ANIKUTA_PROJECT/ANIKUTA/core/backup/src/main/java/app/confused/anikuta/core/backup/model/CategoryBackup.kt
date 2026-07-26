package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable representation of a row in the `categories` SQLDelight table.
 *
 * The Default category (id=1) is always seeded on fresh installs, so it's
 * included in backups but the restore logic skips re-inserting it if it
 * already exists.
 */
@Serializable
data class CategoryBackup(
    val _id: Long = 0,
    val name: String,
    val order: Long = 0,
    val flags: Long = 0,
    val hidden: Boolean = false,
)

/**
 * Serializable representation of a row in the `anime_category` junction table.
 *
 * Links an anime (by its local DB [animeId]) to a category (by [categoryId]).
 * The restore logic re-maps old category ids to new ones if categories were
 * merged or the Default category was reused.
 */
@Serializable
data class AnimeCategoryBackup(
    val animeId: Long,
    val categoryId: Long,
    val order: Long = 0,
)
