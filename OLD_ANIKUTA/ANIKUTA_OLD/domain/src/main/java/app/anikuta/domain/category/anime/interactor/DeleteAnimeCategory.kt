package app.anikuta.domain.category.anime.interactor

import logcat.LogPriority
import app.anikuta.core.util.lang.withNonCancellableContext
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.category.anime.repository.AnimeCategoryRepository
import app.anikuta.domain.category.model.CategoryUpdate
import app.anikuta.domain.download.service.DownloadPreferences
import app.anikuta.domain.library.service.LibraryPreferences

class DeleteAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {

    suspend fun await(categoryId: Long) = withNonCancellableContext {
        try {
            categoryRepository.deleteAnimeCategory(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = categoryRepository.getAllAnimeCategories()
        val updates = categories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
            )
        }

        val defaultCategory = libraryPreferences.defaultAnimeCategory().get()
        if (defaultCategory == categoryId.toInt()) {
            libraryPreferences.defaultAnimeCategory().delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.animeUpdateCategories(),
            libraryPreferences.animeUpdateCategoriesExclude(),
            downloadPreferences.removeExcludeAnimeCategories(),
            downloadPreferences.downloadNewEpisodeCategories(),
            downloadPreferences.downloadNewEpisodeCategoriesExclude(),
        )
        val categoryIdString = categoryId.toString()
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            if (categoryIdString !in ids) return@forEach
            preference.set(ids.minus(categoryIdString))
        }

        try {
            categoryRepository.updatePartialAnimeCategories(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
