package app.anikuta.domain.custombuttons.interactor

import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.custombuttons.model.CustomButton
import app.anikuta.domain.custombuttons.repository.CustomButtonRepository

class GetCustomButtons(
    private val customButtonRepository: CustomButtonRepository,
) {
    fun subscribeAll(): Flow<List<CustomButton>> {
        return customButtonRepository.subscribeAll()
    }

    suspend fun getAll(): List<CustomButton> {
        return customButtonRepository.getAll()
    }
}
