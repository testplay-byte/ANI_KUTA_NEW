package app.anikuta.domain.custombuttons.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import app.anikuta.core.util.lang.withNonCancellableContext
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.custombuttons.model.CustomButton
import app.anikuta.domain.custombuttons.model.CustomButtonUpdate
import app.anikuta.domain.custombuttons.repository.CustomButtonRepository

class ToggleFavoriteCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    private val mutex = Mutex()

    suspend fun await(customButton: CustomButton) = withNonCancellableContext {
        try {
            if (customButton.isFavorite) {
                val update = CustomButtonUpdate(
                    id = customButton.id,
                    isFavorite = false,
                )
                customButtonRepository.updatePartialCustomButton(update)
                Result.Success
            } else {
                mutex.withLock {
                    val customButtons = customButtonRepository.getAll()
                        .toMutableList()
                    val updates = customButtons.map { btn ->
                        CustomButtonUpdate(
                            id = btn.id,
                            isFavorite = btn.id == customButton.id,
                        )
                    }
                    customButtonRepository.updatePartialCustomButtons(updates)
                    Result.Success
                }
            }
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
