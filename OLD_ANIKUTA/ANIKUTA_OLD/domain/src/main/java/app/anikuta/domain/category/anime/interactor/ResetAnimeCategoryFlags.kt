package app.anikuta.domain.category.anime.interactor

import app.anikuta.domain.category.anime.repository.AnimeCategoryRepository
import app.anikuta.domain.library.model.plus
import app.anikuta.domain.library.service.LibraryPreferences

class ResetAnimeCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await() {
        val sort = preferences.animeSortingMode().get()
        categoryRepository.updateAllAnimeCategoryFlags(sort.type + sort.direction)
    }
}
