package app.anikuta.domain.category.anime.interactor

import app.anikuta.domain.category.anime.repository.AnimeCategoryRepository
import app.anikuta.domain.category.model.Category
import app.anikuta.domain.category.model.CategoryUpdate
import app.anikuta.domain.library.anime.model.AnimeLibrarySort
import app.anikuta.domain.library.model.plus
import app.anikuta.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForAnimeCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(
        categoryId: Long?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        val category = categoryId?.let { categoryRepository.getAnimeCategory(it) }
        val flags = (category?.flags ?: 0) + type + direction
        if (type == AnimeLibrarySort.Type.Random) {
            preferences.randomAnimeSortSeed().set(Random.nextInt())
        }
        if (category != null && preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartialAnimeCategory(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.animeSortingMode().set(AnimeLibrarySort(type, direction))
            categoryRepository.updateAllAnimeCategoryFlags(flags)
        }
    }

    suspend fun await(
        category: Category?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
