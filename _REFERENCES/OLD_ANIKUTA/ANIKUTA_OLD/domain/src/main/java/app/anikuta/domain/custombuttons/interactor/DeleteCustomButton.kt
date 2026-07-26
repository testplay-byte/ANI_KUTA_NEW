package app.anikuta.domain.custombuttons.interactor

import logcat.LogPriority
import app.anikuta.core.util.lang.withNonCancellableContext
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.custombuttons.model.CustomButtonUpdate
import app.anikuta.domain.custombuttons.repository.CustomButtonRepository

class DeleteCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(customButtonId: Long) = withNonCancellableContext {
        try {
            customButtonRepository.deleteCustomButton(customButtonId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val customButtons = customButtonRepository.getAll()
        val updates = customButtons.mapIndexed { index, customButton ->
            CustomButtonUpdate(
                id = customButton.id,
                sortIndex = index.toLong(),
            )
        }

        try {
            customButtonRepository.updatePartialCustomButtons(updates)
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
