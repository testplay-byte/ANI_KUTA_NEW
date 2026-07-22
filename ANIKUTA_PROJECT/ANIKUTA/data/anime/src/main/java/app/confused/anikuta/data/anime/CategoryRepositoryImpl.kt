package app.confused.anikuta.data.anime

import app.confused.anikuta.core.common.model.Category
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.core.common.di.DispatcherProvider
import app.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SQLDelight-backed implementation of [CategoryRepository].
 *
 * Handles both the `categories` table and the `anime_category` junction.
 *
 * The Default category (id=1) is seeded in the `.sq` file (fresh installs)
 * and in the `1.sqm` migration (existing installs). [ensureDefaultExists]
 * is a safety net called on app startup.
 *
 * Reordering uses a [Mutex] to serialize concurrent reorder calls (prevents
 * races from rapid drag-and-drop events) — mirrors the Aniyomi pattern.
 *
 * Logging (ADR-033): uses tag [TAG].
 */
class CategoryRepositoryImpl(
    private val database: AnikutaDatabase,
    private val dispatchers: DispatcherProvider,
) : CategoryRepository {

    private val reorderMutex = Mutex()

    // ── Category reads ──

    override fun observeAll(): Flow<List<Category>> =
        database.categoriesQueries.selectAll(CategoryMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override fun observeVisible(): Flow<List<Category>> =
        database.categoriesQueries.selectVisible(CategoryMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override suspend fun getAll(): List<Category> =
        database.categoriesQueries.selectAll(CategoryMapper::map).executeAsList()

    override suspend fun getById(id: Long): Category? =
        database.categoriesQueries.selectById(id, CategoryMapper::map).executeAsOneOrNull()

    override suspend fun getByName(name: String): Category? =
        database.categoriesQueries.selectByName(name, CategoryMapper::map).executeAsOneOrNull()

    // ── Category writes ──

    override suspend fun create(name: String): Long {
        require(name.isNotBlank()) { "Category name cannot be blank" }
        Log.d(TAG, "create: name=$name")
        return database.transactionWithResult {
            val maxOrder = database.categoriesQueries.selectAll(CategoryMapper::map)
                .executeAsList()
                .maxOfOrNull { it.order } ?: 0L
            val nextOrder = if (maxOrder < 0) 0L else maxOrder + 1
            database.categoriesQueries.insert(
                name = name.trim(),
                order = nextOrder,
                flags = 0L,
                hidden = 0L,
            )
            database.categoriesQueries.lastInsertedRowId().executeAsOne()
        }
    }

    override suspend fun rename(id: Long, name: String) {
        require(name.isNotBlank()) { "Category name cannot be blank" }
        if (id == Category.DEFAULT_ID) {
            Log.w(TAG, "rename: refused for Default category (id=1)")
            return
        }
        Log.d(TAG, "rename: id=$id, name=$name")
        database.categoriesQueries.updateName(name = name.trim(), id = id)
    }

    override suspend fun delete(id: Long) {
        if (id == Category.DEFAULT_ID) {
            Log.w(TAG, "delete: refused for Default category (id=1)")
            throw IllegalArgumentException("Default category cannot be deleted")
        }
        Log.d(TAG, "delete: id=$id")
        database.transaction {
            database.categoriesQueries.delete(id)
            // Renumber remaining categories 0..n-1 (Default stays at 0 since it's first).
            val remaining = database.categoriesQueries.selectAll(CategoryMapper::map).executeAsList()
            remaining.forEachIndexed { index, category ->
                database.categoriesQueries.updateOrder(order = index.toLong(), id = category.id)
            }
        }
    }

    override suspend fun reorder(id: Long, newIndex: Int) {
        if (id == Category.DEFAULT_ID) {
            Log.w(TAG, "reorder: refused for Default category (id=1)")
            return
        }
        reorderMutex.withLock {
            database.transaction {
                val categories = database.categoriesQueries.selectAll(CategoryMapper::map)
                    .executeAsList()
                    .toMutableList()
                val currentIndex = categories.indexOfFirst { it.id == id }
                if (currentIndex == -1) return@transaction
                if (currentIndex == newIndex) return@transaction

                // Remove + insert at new position.
                val moved = categories.removeAt(currentIndex)
                val clampedNewIndex = newIndex.coerceIn(0, categories.size)
                categories.add(clampedNewIndex, moved)

                // Renumber all (Default gets 0, rest follow).
                categories.forEachIndexed { index, category ->
                    database.categoriesQueries.updateOrder(order = index.toLong(), id = category.id)
                }
            }
        }
    }

    override suspend fun setHidden(id: Long, hidden: Boolean) {
        if (id == Category.DEFAULT_ID) {
            Log.w(TAG, "setHidden: refused for Default category (id=1)")
            return
        }
        Log.d(TAG, "setHidden: id=$id, hidden=$hidden")
        database.categoriesQueries.updateHidden(hidden = if (hidden) 1L else 0L, id = id)
    }

    override suspend fun ensureDefaultExists() {
        // The .sq file seeds Default on fresh installs; the 1.sqm migration
        // seeds it on existing installs. This is a safety net for edge cases
        // (e.g. a DB that predates the migration). Uses INSERT OR IGNORE so
        // it's a no-op if the Default row already exists.
        try {
            database.categoriesQueries.insertDefault()
            val default = database.categoriesQueries.selectDefault(CategoryMapper::map).executeAsOneOrNull()
            if (default == null) {
                Log.e(TAG, "ensureDefaultExists: Default category still missing after insertDefault")
            } else {
                Log.d(TAG, "ensureDefaultExists: Default category OK (id=${default.id})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureDefaultExists: failed to verify/create Default category", e)
        }
    }

    // ── Anime ↔ Category junction ──

    override fun observeCategoriesForAnime(animeId: Long): Flow<List<Category>> =
        database.anime_categoryQueries.selectCategoriesByAnimeId(animeId, CategoryMapper::map)
            .asFlow()
            .mapToList(dispatchers.io)

    override suspend fun getAnimeCategories(animeId: Long): List<Category> =
        database.anime_categoryQueries.selectCategoriesByAnimeId(animeId, CategoryMapper::map)
            .executeAsList()

    override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
        Log.d(TAG, "setAnimeCategories: animeId=$animeId, categoryIds=$categoryIds")
        database.transaction {
            database.anime_categoryQueries.deleteByAnimeId(animeId)
            categoryIds.forEachIndexed { index, categoryId ->
                database.anime_categoryQueries.insert(
                    animeId = animeId,
                    categoryId = categoryId,
                    order = index.toLong(),
                )
            }
        }
    }

    override suspend fun countAnimeInCategory(categoryId: Long): Int =
        database.anime_categoryQueries.countAnimeInCategory(categoryId)
            .executeAsOne()
            .toInt()

    companion object {
        private const val TAG = "AnikutaCatRepo"
    }
}
