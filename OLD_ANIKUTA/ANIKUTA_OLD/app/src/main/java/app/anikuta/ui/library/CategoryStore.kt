package app.anikuta.ui.library

import android.util.Log
import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * SharedPreferences-backed category store for the Library.
 *
 * Stores user-created categories + anime→category assignments. Keyed by AniList
 * ID (not SQLDelight `_id`) because our app is AniList-first and the full
 * SQLDelight `animes` migration is deferred (needs `source` + `url` from
 * AniyomiSourceBridge).
 *
 * Data model:
 *   - Categories: List<Category> (ordered, id=0 is "Default", undeletable)
 *   - Assignments: Map<String(anilistId), Set<Long(categoryId)>>
 *
 * Reactive via [changes] Flow — LibraryViewModel collects it to update the UI
 * in real time when categories are created/renamed/deleted or anime are
 * assigned.
 *
 * Related files (edit one → check the others):
 *   - LibraryViewModel.kt — reads categories + assignments via [changes]
 *   - LibraryScreen.kt — renders category tabs
 *   - AnimeCategoryScreen.kt — manage categories (create/rename/delete/reorder)
 *   - DetailViewModel.kt — assigns anime to categories (future)
 */
class CategoryStore(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        private const val TAG = "CategoryStore"
        private const val CATEGORIES_KEY = "pref_library_categories"
        private const val ASSIGNMENTS_KEY = "pref_library_category_assignments"
        private const val DEFAULT_CATEGORY_ID = 0L
        private const val DEFAULT_CATEGORY_NAME = "Default"
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Category(
        val id: Long,
        val name: String,
        val order: Int,
    )

    @Serializable
    data class CategoryState(
        val categories: List<Category>,
        val assignments: Map<String, Set<Long>>,
    )

    private val categoriesPref = preferenceStore.getObject(
        key = CATEGORIES_KEY,
        defaultValue = listOf(Category(DEFAULT_CATEGORY_ID, DEFAULT_CATEGORY_NAME, 0)),
        serializer = { list ->
            json.encodeToString(ListSerializer(Category.serializer()), list)
        },
        deserializer = { str ->
            try {
                val list = json.decodeFromString(ListSerializer(Category.serializer()), str)
                // Ensure the Default category always exists.
                if (list.none { it.id == DEFAULT_CATEGORY_ID }) {
                    listOf(Category(DEFAULT_CATEGORY_ID, DEFAULT_CATEGORY_NAME, 0)) + list
                } else {
                    list
                }
            } catch (e: Exception) {
                listOf(Category(DEFAULT_CATEGORY_ID, DEFAULT_CATEGORY_NAME, 0))
            }
        },
    )

    private val assignmentsPref = preferenceStore.getObject(
        key = ASSIGNMENTS_KEY,
        defaultValue = emptyMap<String, Set<Long>>(),
        serializer = { map ->
            json.encodeToString(
                MapSerializer(String.serializer(), SetSerializer(Long.serializer())),
                map,
            )
        },
        deserializer = { str ->
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), SetSerializer(Long.serializer())),
                    str,
                )
            } catch (e: Exception) {
                emptyMap()
            }
        },
    )

    /**
     * Reactive stream of the full category state (categories + assignments).
     * Emits on any change. LibraryViewModel collects this.
     */
    val changes: Flow<CategoryState> = categoriesPref.changes().map { cats ->
        CategoryState(
            categories = cats,
            assignments = assignmentsPref.get(),
        )
    }

    /** Get all categories (ordered). */
    fun getCategories(): List<Category> = categoriesPref.get()

    /** Get all assignments (anilistId → categoryIds). */
    fun getAssignments(): Map<String, Set<Long>> = assignmentsPref.get()

    /** Snapshot of the full category state (for backup). */
    fun getStateSnapshot(): CategoryState = CategoryState(
        categories = categoriesPref.get(),
        assignments = assignmentsPref.get(),
    )

    /** Restore a category from backup (used by BackupManager). */
    fun restoreCategory(category: app.anikuta.backup.BackupCategory) {
        val cats = categoriesPref.get().toMutableList()
        // Replace if exists, else add
        val existing = cats.indexOfFirst { it.id == category.id }
        val restored = Category(category.id, category.name, category.order)
        if (existing >= 0) cats[existing] = restored else cats.add(restored)
        categoriesPref.set(cats)
    }

    /** Restore assignments from backup (used by BackupManager). */
    fun restoreAssignments(assignments: Map<String, List<Long>>) {
        val restored = assignments.mapValues { (_, ids) -> ids.toSet() }
        assignmentsPref.set(restored)
    }

    /** Create a new category with the given name. Returns the new category's id. */
    fun createCategory(name: String): Long {
        val cats = categoriesPref.get().toMutableList()
        val nextId = (cats.maxOfOrNull { it.id } ?: 0L) + 1L
        val nextOrder = (cats.maxOfOrNull { it.order } ?: 0) + 1
        cats.add(Category(nextId, name.trim(), nextOrder))
        categoriesPref.set(cats)
        Log.d(TAG, "Created category: ${name.trim()} (id=$nextId)")
        return nextId
    }

    /** Rename a category. No-op if not found. Can't rename the Default category. */
    fun renameCategory(id: Long, newName: String) {
        if (id == DEFAULT_CATEGORY_ID) return
        val cats = categoriesPref.get().map { cat ->
            if (cat.id == id) cat.copy(name = newName.trim()) else cat
        }
        categoriesPref.set(cats)
    }

    /** Delete a category. Removes it + clears its assignments. Can't delete Default. */
    fun deleteCategory(id: Long) {
        if (id == DEFAULT_CATEGORY_ID) return
        val cats = categoriesPref.get().filter { it.id != id }
        categoriesPref.set(cats)
        // Remove this category from all assignments.
        val assignments = assignmentsPref.get().mapValues { (_, ids) ->
            ids.filter { it != id }.toSet()
        }
        assignmentsPref.set(assignments)
    }

    /** Reorder categories (list is in the new order). Default stays first. */
    fun reorderCategories(orderedIds: List<Long>) {
        val current = categoriesPref.get().associateBy { it.id }
        val newOrder = orderedIds.mapIndexed { index, id ->
            current[id]?.copy(order = index) ?: return
        }
        // Ensure Default is first.
        val sorted = newOrder.sortedWith(compareBy({ it.id != DEFAULT_CATEGORY_ID }, { it.order }))
        categoriesPref.set(sorted)
    }

    /** Assign an anime to a set of categories (replaces existing assignments). */
    fun setAnimeCategories(anilistId: Int, categoryIds: Set<Long>) {
        val map = assignmentsPref.get().toMutableMap()
        // Always include Default if no categories specified.
        val effective = if (categoryIds.isEmpty()) setOf(DEFAULT_CATEGORY_ID) else categoryIds
        map[anilistId.toString()] = effective
        assignmentsPref.set(map)
    }

    /** Get the category IDs assigned to an anime. */
    fun getAnimeCategories(anilistId: Int): Set<Long> {
        return assignmentsPref.get()[anilistId.toString()] ?: setOf(DEFAULT_CATEGORY_ID)
    }
}
