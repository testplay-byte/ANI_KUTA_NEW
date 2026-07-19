package app.anikuta.domain.custombuttons.interactor

import app.anikuta.core.util.lang.withNonCancellableContext
import app.anikuta.domain.custombuttons.model.CustomButtonUpdate
import app.anikuta.domain.custombuttons.repository.CustomButtonRepository

class UpdateCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(update: CustomButtonUpdate) = withNonCancellableContext {
        try {
            customButtonRepository.updatePartialCustomButton(update)
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    suspend fun await(updates: List<CustomButtonUpdate>) = withNonCancellableContext {
        try {
            customButtonRepository.updatePartialCustomButtons(updates)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
