package app.confused.anikuta.core.common.model

/** Domain model for a library category. */
data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Int,
)
