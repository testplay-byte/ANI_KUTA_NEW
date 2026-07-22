package app.confused.anikuta.core.common.repository

import app.confused.anikuta.core.common.model.AnimeCategoryLink
import app.confused.anikuta.core.common.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for category data access + anime↔category junction.
 *
 * Per `RULES/ai-agent-rules.md` §3: ViewModels depend on this interface only,
 * never on the implementation. The implementation lives in `:data:anime`.
 *
 * The Default category (id = [Category.DEFAULT_ID] = 1) is created
 * automatically on first launch via [ensureDefaultExists]. It cannot be
 * deleted (enforced at the DB layer via a trigger); [delete] on id=1 throws.
 *
 * Category CRUD (hide/delete/rename/reorder) is surfaced in Settings per
 * user decision Q8.
 */
interface CategoryRepository {

    // ── Category reads ──

    /** All categories including hidden ones, ordered by [Category.order]. */
    fun observeAll(): Flow<List<Category>>

    /** Non-hidden categories only, ordered by [Category.order]. */
    fun observeVisible(): Flow<List<Category>>

    suspend fun getAll(): List<Category>

    suspend fun getById(id: Long): Category?

    suspend fun getByName(name: String): Category?

    // ── Category writes ──

    /**
     * Create a new category with the given name.
     * @return the new category's id.
     * @throws IllegalArgumentException if [name] is blank.
     */
    suspend fun create(name: String): Long

    /** Rename a category. Refused for the Default category (id=1). */
    suspend fun rename(id: Long, name: String)

    /**
     * Delete a category. Refused for the Default category (id=1) — throws.
     * Anime in the deleted category fall back to having no category (surface
     * in the Default bucket on the library page). The anime_category join
     * rows are removed via ON DELETE CASCADE.
     * Remaining categories are renumbered 0..n-1.
     */
    suspend fun delete(id: Long)

    /**
     * Move [id] to [newIndex] in the display order. Other categories shift.
     * The Default category (id=1) is excluded from reordering — it stays
     * first. Returns silently if [id] is the Default category.
     */
    suspend fun reorder(id: Long, newIndex: Int)

    /** Toggle the hidden flag. Refused for the Default category (id=1). */
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** Ensure the Default category (id=1) exists. Safe to call on every startup. */
    suspend fun ensureDefaultExists()

    // ── Anime ↔ Category junction ──

    /** Observe ALL anime↔category links (for library tab filtering). */
    fun observeAllLinks(): Flow<List<AnimeCategoryLink>>

    /** Observe the categories assigned to an anime (by animes._id). */
    fun observeCategoriesForAnime(animeId: Long): Flow<List<Category>>

    /** Get the categories assigned to an anime (by animes._id). */
    suspend fun getAnimeCategories(animeId: Long): List<Category>

    /**
     * Full-replace the category assignments for an anime.
     * Deletes all existing anime_category rows for [animeId], then inserts
     * one row per id in [categoryIds]. Transactional.
     */
    suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>)

    /** Count how many anime are in a category. */
    suspend fun countAnimeInCategory(categoryId: Long): Int
}
