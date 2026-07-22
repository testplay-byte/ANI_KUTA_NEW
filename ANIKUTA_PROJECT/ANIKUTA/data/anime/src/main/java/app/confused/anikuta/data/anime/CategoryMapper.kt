package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Category

/**
 * Maps SQLDelight query results to the [Category] domain model.
 *
 * Parameter order matches the `categories` table columns (CREATE TABLE order):
 * _id, name, category_order, flags, hidden.
 */
object CategoryMapper {

    @Suppress("UNUSED_PARAMETER")
    fun map(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): Category = Category(
        id = id,
        name = name,
        order = order,
        flags = flags.toInt(),
        hidden = hidden != 0L,
    )
}
