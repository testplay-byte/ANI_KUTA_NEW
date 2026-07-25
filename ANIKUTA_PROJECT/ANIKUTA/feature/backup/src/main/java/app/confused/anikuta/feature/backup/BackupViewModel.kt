package app.confused.anikuta.feature.backup

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.backup.AutoBackupFrequency
import app.confused.anikuta.core.backup.AutoBackupScheduler
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupManager
import app.confused.anikuta.core.backup.BackupOptions
import app.confused.anikuta.core.backup.BackupPreferences
import app.confused.anikuta.core.backup.BackupResult
import app.confused.anikuta.core.backup.BackupStorage
import app.confused.anikuta.core.backup.CreateSummary
import app.confused.anikuta.core.backup.RestoreSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BackupViewModel"

/** Minimum duration (ms) the restore animation is shown, even if restore finishes faster. */
private const val MIN_RESTORE_ANIMATION_MS = 5000L

/**
 * UI state for the Backup & Restore screen.
 */
sealed class BackupUiState {
    /** Idle — no operation in progress. */
    object Idle : BackupUiState()

    /** Backup creation in progress. */
    data class Creating(val message: String = "Creating backup…") : BackupUiState()

    /** Backup created successfully. */
    data class Created(val summary: CreateSummary) : BackupUiState()

    /** Reading a selected backup file (detecting format + summary). */
    data class ReadingFile(val fileName: String) : BackupUiState()

    /** Restore summary loaded — waiting for user confirmation. */
    data class RestorePending(val summary: RestoreSummary, val fileUri: Uri) : BackupUiState()

    /** Restore in progress (animation shown for at least 5 seconds). */
    data class Restoring(val message: String = "Restoring your data…") : BackupUiState()

    /** Restore completed. */
    data class Restored(val summary: RestoreSummary) : BackupUiState()

    /** Error state with a user-facing message. */
    data class Error(val message: String, val recoverable: Boolean = true) : BackupUiState()
}

/**
 * ViewModel for the Backup & Restore settings screen.
 *
 * Manages:
 * - Manual backup category selection + creation.
 * - Restore: file selection → format detection → summary → confirm → execute.
 * - Auto-backup: enable/disable, frequency, category selection, max-backups.
 * - Storage: SAF folder selection + usage display.
 *
 * **Restore animation:** The restore operation is guaranteed to show the
 * animation for at least [MIN_RESTORE_ANIMATION_MS] (5 seconds), even if the
 * actual restore finishes faster. This gives the user a satisfying visual
 * experience.
 *
 * All backup operations are delegated to [BackupManager] (engine in `:core:backup`).
 * UI state is exposed via [state] (a sealed class) so the screen can render
 * loading/success/error states cleanly.
 */
class BackupViewModel(
    private val backupManager: BackupManager,
    private val backupStorage: BackupStorage,
    private val backupPreferences: BackupPreferences,
    private val autoBackupScheduler: AutoBackupScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    // Manual backup category selection
    private val _manualCategories = MutableStateFlow(BackupCategory.defaultSelection.toMutableSet())
    val manualCategories: StateFlow<Set<String>> = _manualCategories.asStateFlow()

    // Auto-backup category selection (separate from manual)
    private val _autoCategories = MutableStateFlow(backupPreferences.autoCategories.get().toMutableSet())
    val autoCategories: StateFlow<Set<String>> = _autoCategories.asStateFlow()

    // Auto-backup enabled + frequency
    private val _autoEnabled = MutableStateFlow(backupPreferences.autoEnabled.get())
    val autoEnabled: StateFlow<Boolean> = _autoEnabled.asStateFlow()

    private val _autoFrequency = MutableStateFlow(AutoBackupFrequency.fromName(backupPreferences.autoFrequency.get()))
    val autoFrequency: StateFlow<AutoBackupFrequency> = _autoFrequency.asStateFlow()

    // Max auto-backups to keep (1-4)
    private val _autoMaxKeep = MutableStateFlow(backupPreferences.autoMaxKeep.get())
    val autoMaxKeep: StateFlow<Int> = _autoMaxKeep.asStateFlow()

    // Storage
    private val _folderUri = MutableStateFlow(backupPreferences.folderUri.get())
    val folderUri: StateFlow<String> = _folderUri.asStateFlow()

    private val _storageUsage = MutableStateFlow(0L)
    val storageUsage: StateFlow<Long> = _storageUsage.asStateFlow()

    init {
        refreshStorageUsage()
    }

    // ── Manual backup ──

    fun toggleManualCategory(categoryId: String) {
        _manualCategories.update { current ->
            current.toMutableSet().apply {
                if (contains(categoryId)) remove(categoryId) else add(categoryId)
            }
        }
    }

    fun createBackup() {
        if (_manualCategories.value.isEmpty()) {
            _state.value = BackupUiState.Error("Select at least one category to back up.")
            return
        }
        if (!backupStorage.hasFolder()) {
            _state.value = BackupUiState.Error("No backup folder selected. Choose a folder in the Storage section below.", recoverable = true)
            return
        }
        viewModelScope.launch {
            _state.value = BackupUiState.Creating()
            try {
                val fileName = backupStorage.generateBackupName(isAuto = false)
                val output = backupStorage.createManualBackupFile(fileName)
                if (output == null) {
                    _state.value = BackupUiState.Error("Failed to create backup file. Check folder permissions.")
                    return@launch
                }
                output.use { stream ->
                    val options = BackupOptions(categories = _manualCategories.value)
                    when (val result = backupManager.createBackup(options, stream)) {
                        is BackupResult.Success -> {
                            backupPreferences.lastManualBackup.set(System.currentTimeMillis())
                            _state.value = BackupUiState.Created(result.data.copy(
                                filePath = fileName,
                            ))
                            refreshStorageUsage()
                            Log.i(TAG, "Backup created: ${result.data.itemCount} items, ${result.data.categoryCount} categories")
                        }
                        is BackupResult.Error -> {
                            _state.value = BackupUiState.Error(result.message, result.recoverable)
                        }
                        is BackupResult.InProgress -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "createBackup failed", e)
                _state.value = BackupUiState.Error("Backup failed: ${e.message}")
            }
        }
    }

    // ── Restore ──

    fun onSelectBackupFile(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.ReadingFile(uri.lastPathSegment ?: "backup file")
            try {
                val input = backupStorage.openInput(uri)
                if (input == null) {
                    _state.value = BackupUiState.Error("Cannot open the selected file.")
                    return@launch
                }
                input.use { stream ->
                    when (val result = backupManager.readSummary(stream)) {
                        is BackupResult.Success -> {
                            _state.value = BackupUiState.RestorePending(result.data, uri)
                        }
                        is BackupResult.Error -> {
                            _state.value = BackupUiState.Error(result.message, result.recoverable)
                        }
                        is BackupResult.InProgress -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onSelectBackupFile failed", e)
                _state.value = BackupUiState.Error("Failed to read backup: ${e.message}")
            }
        }
    }

    /**
     * Confirms + executes the restore.
     *
     * The restore animation is shown for at least [MIN_RESTORE_ANIMATION_MS]
     * (5 seconds), even if the actual restore finishes faster. This is done by
     * running the restore + a delay concurrently and waiting for both.
     */
    fun confirmRestore(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Restoring()
            Log.i(TAG, "confirmRestore: starting (min ${MIN_RESTORE_ANIMATION_MS}ms animation)")
            try {
                // Read the file fresh (the previous stream was closed by readSummary)
                val input = backupStorage.openInput(uri)
                if (input == null) {
                    _state.value = BackupUiState.Error("Cannot open the backup file.")
                    return@launch
                }

                // Run restore + minimum-delay concurrently
                val restoreDeferred = async {
                    input.use { stream ->
                        backupManager.restoreBackup(stream)
                    }
                }
                val minDelayDeferred = async {
                    delay(MIN_RESTORE_ANIMATION_MS)
                }

                // Wait for both to complete
                minDelayDeferred.await()
                val result = restoreDeferred.await()

                when (result) {
                    is BackupResult.Success -> {
                        Log.i(TAG, "confirmRestore: success — ${result.data.totalImported} imported")
                        _state.value = BackupUiState.Restored(result.data)
                    }
                    is BackupResult.Error -> {
                        Log.e(TAG, "confirmRestore: failed — ${result.message}")
                        _state.value = BackupUiState.Error(result.message, result.recoverable)
                    }
                    is BackupResult.InProgress -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "confirmRestore failed", e)
                _state.value = BackupUiState.Error("Restore failed: ${e.message}")
            }
        }
    }

    fun dismissState() {
        _state.value = BackupUiState.Idle
    }

    // ── Auto-backup ──

    fun toggleAutoEnabled(enabled: Boolean) {
        _autoEnabled.value = enabled
        backupPreferences.autoEnabled.set(enabled)
        autoBackupScheduler.reschedule(enabled, _autoFrequency.value)
        Log.i(TAG, "Auto-backup ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoFrequency(frequency: AutoBackupFrequency) {
        _autoFrequency.value = frequency
        backupPreferences.autoFrequency.set(frequency.name)
        if (_autoEnabled.value) {
            autoBackupScheduler.reschedule(true, frequency)
        }
        Log.i(TAG, "Auto-backup frequency set to ${frequency.name}")
    }

    fun setAutoMaxKeep(maxKeep: Int) {
        val clamped = maxKeep.coerceIn(BackupPreferences.MAX_KEEP_MIN, BackupPreferences.MAX_KEEP_MAX)
        _autoMaxKeep.value = clamped
        backupPreferences.autoMaxKeep.set(clamped)
        Log.i(TAG, "Auto-backup max-keep set to $clamped")
    }

    fun toggleAutoCategory(categoryId: String) {
        _autoCategories.update { current ->
            current.toMutableSet().apply {
                if (contains(categoryId)) remove(categoryId) else add(categoryId)
            }
        }
        backupPreferences.autoCategories.set(_autoCategories.value)
    }

    // ── Storage ──

    fun setFolder(uri: Uri) {
        if (backupStorage.setFolderUri(uri)) {
            _folderUri.value = uri.toString()
            refreshStorageUsage()
        } else {
            _state.value = BackupUiState.Error("Failed to set backup folder. Try a different location.")
        }
    }

    fun refreshStorageUsage() {
        _storageUsage.value = backupStorage.getStorageUsage()
    }

    /** All backup categories (for the checkbox lists). */
    val categories: List<BackupCategory> = BackupCategory.entries
}
