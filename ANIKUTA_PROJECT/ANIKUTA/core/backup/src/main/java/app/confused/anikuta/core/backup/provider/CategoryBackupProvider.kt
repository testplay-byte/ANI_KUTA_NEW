package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.AnimeCategoryBackup
import app.confused.anikuta.core.backup.model.CategoryBackup
import app.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Backs up user categories + anime–category junction links.
 *
 * Export reads all `categories` rows + all `anime_category` rows. Import
 * upserts categories by name (the Default category is matched, not duplicated),
 * builds an old-id→new-id remap, then re-inserts anime–category links using
 * the remapped category IDs and resolved local anime IDs.
 *
 * Missing anime (not in the local DB) are skipped gracefully — their category
 * links are dropped but the categories themselves are still restored.
 */
class CategoryBackupProvider(
    private val database: AnikutaDatabase,
) : BackupProvider {

    override val id: String = BackupCategory.CATEGORIES.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val categories = database.categoriesQueries
                .selectAll(BackupMappers::mapCategory)
                .executeAsList()
            val links = database.anime_categoryQueries
                .selectAll(BackupMappers::mapAnimeCategory)
                .executeAsList()
            Log.i(TAG, "Categories export: ${categories.size} categories, ${links.size} links")
            BackupEntry.Categories(categories = categories, links = links)
        } catch (e: Exception) {
            Log.e(TAG, "Categories export failed", e)
            BackupEntry.Categories()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.Categories) { "Expected Categories entry, got ${entry.providerId}" }
        if (entry.categories.isEmpty() && entry.links.isEmpty()) return@withContext false

        // Phase 1: upsert categories, build old-id → new-id remap
        val categoryIdRemap = mutableMapOf<Long, Long>()
        var categoriesImported = 0
        database.categoriesQueries.transaction {
            entry.categories.forEach { cat ->
                try {
                    val newId = upsertCategory(database, cat)
                    categoryIdRemap[cat._id] = newId
                    categoriesImported++
                } catch (e: Exception) {
                    Log.w(TAG, "Categories import: skipped '${cat.name}' — ${e.message}")
                }
            }
        }

        // Phase 2: re-insert anime–category links with remapped IDs
        var linksImported = 0
        var linksSkipped = 0
        database.anime_categoryQueries.transaction {
            entry.links.forEach { link ->
                try {
                    val newCategoryId = categoryIdRemap[link.categoryId]
                    if (newCategoryId == null) {
                        linksSkipped++
                        return@forEach
                    }
                    // Resolve the anime's local DB _id from the backup's animeId.
                    // The backup may store either:
                    // - The original local DB _id (for ANIKUTA backups), OR
                    // - The AniList ID (for Aniyomi-translated backups, where the
                    //   translator sets animeId = anilistId.toLong())
                    // We try both: first by direct _id match, then by anilist_id.
                    val localAnimeId = resolveLocalAnimeIdForLink(database, link.animeId)
                    if (localAnimeId == null) {
                        Log.w(TAG, "Categories import: could not resolve anime for link (animeId=${link.animeId}) — skipping")
                        linksSkipped++
                        return@forEach
                    }
                    database.anime_categoryQueries.insert(
                        animeId = localAnimeId,
                        categoryId = newCategoryId,
                        order = link.order,
                    )
                    linksImported++
                } catch (e: Exception) {
                    linksSkipped++
                }
            }
        }
        Log.i(TAG, "Categories import: $categoriesImported categories, $linksImported links, $linksSkipped links skipped")
        categoriesImported > 0 || linksImported > 0
    }
}

/**
 * Upserts a category by name. If a category with the same name exists, update
 * its flags/order; otherwise insert. The Default category (id=1) is matched,
 * not duplicated.
 *
 * @return the local DB id of the category (existing or newly inserted).
 */
internal fun upsertCategory(database: AnikutaDatabase, cat: CategoryBackup): Long {
    val queries = database.categoriesQueries
    val existing = queries.selectByName(cat.name) { _id, _, _, _, _ -> _id }.executeAsOneOrNull()
    return if (existing != null) {
        queries.update(
            name = cat.name,
            order = cat.order,
            flags = cat.flags,
            hidden = if (cat.hidden) 1L else 0L,
            id = existing,
        )
        existing
    } else {
        queries.insert(
            name = cat.name,
            order = cat.order,
            flags = cat.flags,
            hidden = if (cat.hidden) 1L else 0L,
        )
        queries.lastInsertedRowId().executeAsOne()
    }
}

/**
 * Resolves a backup animeId to the local DB _id.
 *
 * For ANIKUTA backups: the animeId IS the local DB _id (direct match).
 * For Aniyomi-translated backups: the animeId is the AniList ID (we need
 * to look it up via selectIdByAnilistId).
 *
 * Tries direct _id first, then AniList ID lookup.
 */
internal fun resolveLocalAnimeIdForLink(database: AnikutaDatabase, animeId: Long): Long? {
    val queries = database.animesQueries
    // Try direct _id match (ANIKUTA backups)
    queries.selectById(animeId) { _id, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> _id }
        .executeAsOneOrNull()?.let { return it }
    // Try AniList ID match (Aniyomi-translated backups)
    queries.selectIdByAnilistId(animeId).executeAsOneOrNull()?.let { return it }
    return null
}
