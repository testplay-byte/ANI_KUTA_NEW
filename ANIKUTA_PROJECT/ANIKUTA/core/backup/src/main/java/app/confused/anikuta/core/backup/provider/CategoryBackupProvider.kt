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
 * **Anime ID resolution:** The backup may store either the local DB `_id`
 * (for ANIKUTA backups) or the AniList ID (for Aniyomi-translated backups).
 * This provider builds a comprehensive lookup table at the start of import:
 * - `anilistId → localDbId` for all anime in the DB
 * - Also tries direct `_id` match as a fallback
 *
 * This is more reliable than per-query lookups and handles both backup formats.
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

        // ── Build a comprehensive anilistId → localDbId lookup table ──
        // This is done ONCE at the start, before any link insertion.
        // It handles both ANIKUTA backups (animeId = local _id) and
        // Aniyomi-translated backups (animeId = anilistId).
        val allAnime = database.animesQueries.selectAll(BackupMappers::mapAnime).executeAsList()
        val anilistToLocalId = mutableMapOf<Long, Long>()
        val localIdSet = mutableSetOf<Long>()
        allAnime.forEach { anime ->
            localIdSet.add(anime._id)
            if (anime.anilistId != null) {
                anilistToLocalId[anime.anilistId] = anime._id
            }
        }
        Log.i(TAG, "Categories import: built lookup table — ${anilistToLocalId.size} anilistId mappings, ${localIdSet.size} local ids")

        // ── Phase 1: upsert categories, build old-id → new-id remap ──
        val categoryIdRemap = mutableMapOf<Long, Long>()
        var categoriesImported = 0
        database.categoriesQueries.transaction {
            entry.categories.forEach { cat ->
                try {
                    val newId = upsertCategory(database, cat)
                    categoryIdRemap[cat._id] = newId
                    categoriesImported++
                    Log.d(TAG, "Categories import: upserted '${cat.name}' (oldId=${cat._id} → newId=$newId)")
                } catch (e: Exception) {
                    Log.w(TAG, "Categories import: skipped '${cat.name}' — ${e.message}")
                }
            }
        }
        Log.i(TAG, "Categories import: categoryIdRemap = $categoryIdRemap")

        // ── Phase 2: re-insert anime–category links with remapped IDs ──
        var linksImported = 0
        var linksSkipped = 0
        database.anime_categoryQueries.transaction {
            entry.links.forEach { link ->
                try {
                    val newCategoryId = categoryIdRemap[link.categoryId]
                    if (newCategoryId == null) {
                        Log.w(TAG, "Categories import: link skipped — categoryId=${link.categoryId} not in remap (remap keys: ${categoryIdRemap.keys})")
                        linksSkipped++
                        return@forEach
                    }

                    // Resolve the anime's local DB _id.
                    // For ANIKUTA backups: link.animeId IS the local _id (check localIdSet).
                    // For Aniyomi-translated backups: link.animeId is the anilistId (check anilistToLocalId).
                    val localAnimeId = when {
                        localIdSet.contains(link.animeId) -> link.animeId
                        anilistToLocalId.containsKey(link.animeId) -> anilistToLocalId[link.animeId]!!
                        else -> {
                            // Last resort: try DB query (in case the anime was inserted after we built the map)
                            resolveLocalAnimeIdForLink(database, link.animeId)
                        }
                    }

                    if (localAnimeId == null) {
                        Log.w(TAG, "Categories import: could not resolve anime for link (animeId=${link.animeId}, " +
                            "checked localIdSet=${localIdSet.contains(link.animeId)}, " +
                            "anilistMap=${anilistToLocalId.containsKey(link.animeId)}) — skipping")
                        linksSkipped++
                        return@forEach
                    }

                    database.anime_categoryQueries.insert(
                        animeId = localAnimeId,
                        categoryId = newCategoryId,
                        order = link.order,
                    )
                    linksImported++
                    Log.d(TAG, "Categories import: linked animeId=$localAnimeId → categoryId=$newCategoryId")
                } catch (e: Exception) {
                    Log.w(TAG, "Categories import: link failed (animeId=${link.animeId}, categoryId=${link.categoryId}) — ${e.message}")
                    linksSkipped++
                }
            }
        }

        // ── Phase 3: Ensure all restored anime have at least one category ──
        // Anime without any category link should be assigned to the Default
        // category (id=1) so they don't only show in the virtual "All" view.
        // This handles anime that had no category in the backup, OR anime whose
        // category links were skipped (e.g. category not found).
        try {
            val allAnime = database.animesQueries.selectAll(BackupMappers::mapAnime).executeAsList()
            val linkedAnimeIds = database.anime_categoryQueries.selectAll { _, animeId, _, _ -> animeId }.executeAsList().toSet()
            val unlinkedAnime = allAnime.filter { it._id !in linkedAnimeIds && it.favorite }
            var defaultAdded = 0
            database.anime_categoryQueries.transaction {
                unlinkedAnime.forEach { anime ->
                    database.anime_categoryQueries.insert(
                        animeId = anime._id,
                        categoryId = 1L, // Default category
                        order = 0L,
                    )
                    defaultAdded++
                }
            }
            if (defaultAdded > 0) {
                Log.i(TAG, "Categories import: added $defaultAdded anime to Default category (no category link found)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Categories import: failed to assign Default category to unlinked anime", e)
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
    // Try direct _id match (ANIKUTA backups). selectById is `SELECT * FROM
    // animes ...` so the lambda must accept all 42 columns (15 new ADR-050
    // identity + provenance fields added in 2.sqm — only `_id` is used here).
    queries.selectById(animeId) { _id, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> _id }
        .executeAsOneOrNull()?.let { return it }
    // Try AniList ID match (Aniyomi-translated backups)
    queries.selectIdByAnilistId(animeId).executeAsOneOrNull()?.let { return it }
    return null
}
