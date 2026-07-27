package app.confused.anikuta.core.common.model

/**
 * Domain model for a library category.
 *
 * The Default category has a fixed id ([DEFAULT_ID] = 1) and is created
 * automatically on first launch. It cannot be deleted (enforced at the DB
 * layer via a trigger).
 *
 * - [hidden] — if true, the category is filtered out of the visible list
 *   (used by the library page and the category tab strip).
 * - [order] — display order (lower = earlier). Renumbered on reorder/delete.
 * - [flags] — reserved for future per-category sort/display (currently unused;
 *   sort and display mode are global per user decision Q2/Q3).
 */
data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Int,
    val hidden: Boolean,
) {
    /** Whether this is the Default system category. */
    val isDefault: Boolean get() = id == DEFAULT_ID

    companion object {
        /** The fixed id of the Default category. */
        const val DEFAULT_ID = 1L
    }
}
