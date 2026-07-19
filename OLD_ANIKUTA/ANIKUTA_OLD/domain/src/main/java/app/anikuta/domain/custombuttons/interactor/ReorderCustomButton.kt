package app.anikuta.domain.custombuttons.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import app.anikuta.core.util.lang.withNonCancellableContext
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.custombuttons.model.CustomButton
import app.anikuta.domain.custombuttons.model.CustomButtonUpdate
import app.anikuta.domain.custombuttons.repository.CustomButtonRepository

class ReorderCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    private val mutex = Mutex()

    suspend fun changeOrder(customButton: CustomButton, newIndex: Int) = withNonCancellableContext {
        mutex.withLock {
            val customButtons = customButtonRepository.getAll()
                .toMutableList()

            val currentIndex = customButtons.indexOfFirst { it.id == customButton.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            try {
                customButtons.add(newIndex, customButtons.removeAt(currentIndex))

                val updates = customButtons.mapIndexed { index, customButton ->
                    CustomButtonUpdate(
                        id = customButton.id,
                        sortIndex = index.toLong(),
                    )
                }

                customButtonRepository.updatePartialCustomButtons(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
