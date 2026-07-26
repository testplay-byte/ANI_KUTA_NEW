package app.anikuta.domain.category.anime.interactor

import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.category.anime.repository.AnimeCategoryRepository
import app.anikuta.domain.category.model.Category

class GetAnimeCategories(
    private val categoryRepository: AnimeCategoryRepository,
) {

    fun subscribe(): Flow<List<Category>> {
        return categoryRepository.getAllAnimeCategoriesAsFlow()
    }

    fun subscribe(animeId: Long): Flow<List<Category>> {
        return categoryRepository.getCategoriesByAnimeIdAsFlow(animeId)
    }

    suspend fun await(): List<Category> {
        return categoryRepository.getAllAnimeCategories()
    }

    suspend fun await(animeId: Long): List<Category> {
        return categoryRepository.getCategoriesByAnimeId(animeId)
    }
}
