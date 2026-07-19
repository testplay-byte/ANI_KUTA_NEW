package app.anikuta.onboarding

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * State for the onboarding wizard.
 * Tracks which step the user is on + completion state.
 */
data class OnboardingState(
    val currentStep: Int = 0,
    val totalSteps: Int = 7,
    val storageFolderUri: String? = null,
    val primaryExtensionPkg: String? = null,
    val secondaryExtensionPkg: String? = null,
    val selectedDesign: String = "material3",
    val backupFileUri: String? = null,
    val isComplete: Boolean = false,
) {
    val stepNames = listOf(
        "Welcome",
        "Permissions",
        "Storage",
        "Extension",
        "Backup Restore",
        "Design",
        "All Set",
    )

    val requiredSteps = setOf(2, 3, 5) // 0-indexed: Storage, Extension, Design

    fun canProceed(): Boolean {
        return when (currentStep) {
            2 -> storageFolderUri != null  // Storage required
            3 -> primaryExtensionPkg != null  // Extension required
            5 -> selectedDesign.isNotEmpty()  // Design required
            else -> true
        }
    }

    fun next(): OnboardingState {
        if (currentStep < totalSteps - 1) {
            return copy(currentStep = currentStep + 1)
        }
        return copy(isComplete = true)
    }

    fun previous(): OnboardingState {
        return copy(currentStep = maxOf(0, currentStep - 1))
    }
}
