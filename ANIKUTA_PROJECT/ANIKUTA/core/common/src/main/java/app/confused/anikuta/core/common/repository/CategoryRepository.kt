package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.Category
import kotlinx.coroutines.flow.Flow

/** Repository interface for category data access. */
interface CategoryRepository {

    fun observeAll(): Flow<List<Category>>

    suspend fun getById(id: Long): Category?

    suspend fun create(name: String, order: Long, flags: Int): Long

    suspend fun update(category: Category)

    suspend fun delete(id: Long)
}
