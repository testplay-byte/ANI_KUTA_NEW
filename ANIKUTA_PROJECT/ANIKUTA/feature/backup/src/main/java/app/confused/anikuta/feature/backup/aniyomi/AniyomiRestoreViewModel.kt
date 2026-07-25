package app.confused.anikuta.feature.backup.aniyomi

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.backup.BackupManager
import app.confused.anikuta.core.backup.BackupResult
import app.confused.anikuta.core.backup.BackupStorage
import app.confused.anikuta.core.backup.format.BackupFormatDetector
import app.confused.anikuta.core.backup.format.AniyomiBackupFormat
import app.confused.anikuta.core.backup.format.aniyomi.AniyomiBackup
import app.confused.anikuta.core.backup.translation.AnilistResolution
import app.confused.anikuta.core.backup.translation.AniyomiBackupTranslator
import app.confused.anikuta.core.backup.translation.TranslationResult
import app.confused.anikuta.core.backup.translation.TranslationStats
import app.confused.anikuta.core.backup.translation.TranslationProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AniyomiRestoreVM"

/**
 * UI state for the Aniyomi restore flow.
 */
sealed class AniyomiRestoreState {
    object Idle : AniyomiRestoreState()
    data class FormatDetected(val isSupported: Boolean, val formatName: String, val fileUri: Uri) : AniyomiRestoreState()
    data class Processing(val message: String = "Processing backup…") : AniyomiRestoreState()
    data class Summary(val stats: TranslationStats, val resolutions: List<AnilistResolution>, val translationResult: TranslationResult, val fileUri: Uri) : AniyomiRestoreState()
    data class Linking(val progress: TranslationProgress?, val resolutions: List<AnilistResolution>) : AniyomiRestoreState()
    data class ManualLinking(val failedAnime: List<AnilistResolution.Failed>, val resolutions: List<AnilistResolution>, val translationResult: TranslationResult, val fileUri: Uri) : AniyomiRestoreState()
    data class Success(val stats: TranslationStats, val skippedCount: Int) : AniyomiRestoreState()
    data class Error(val message: String) : AniyomiRestoreState()
}

/**
 * ViewModel for the Aniyomi restore flow (6-step multi-screen).
 *
 * Uses [AniyomiBackupTranslator] with [AniListRateLimiter] (80 req/min dynamic).
 * Min 5-second animation on processing step.
 */
class AniyomiRestoreViewModel(
    private val anilistApi: AniListApi,
    private val backupStorage: BackupStorage,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow<AniyomiRestoreState>(AniyomiRestoreState.Idle)
    val state: StateFlow<AniyomiRestoreState> = _state.asStateFlow()

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val input = backupStorage.openInput(uri)
                if (input == null) {
                    _state.value = AniyomiRestoreState.Error("Cannot open the selected file.")
                    return@launch
                }
                val bytes = input.use { it.readBytes() }
                val formatType = BackupFormatDetector.detect(bytes)
                val isSupported = formatType != null
                val formatName = formatType?.displayName ?: "Unknown format"
                _state.value = AniyomiRestoreState.FormatDetected(isSupported, formatName, uri)
            } catch (e: Exception) {
                Log.e(TAG, "onFileSelected failed", e)
                _state.value = AniyomiRestoreState.Error("Failed to read file: ${e.message}")
            }
        }
    }

    fun onContinueFromDetection(uri: Uri) {
        viewModelScope.launch {
            _state.value = AniyomiRestoreState.Processing()
            try {
                val input = backupStorage.openInput(uri)
                if (input == null) {
                    _state.value = AniyomiRestoreState.Error("Cannot open the backup file.")
                    return@launch
                }
                val aniyomiFormat = AniyomiBackupFormat()
                val aniyomi = input.use { stream -> aniyomiFormat.decodeRaw(stream) }
                Log.i(TAG, "Decoded Aniyomi backup: ${aniyomi.backupAnime.size} anime, ${aniyomi.backupManga.size} manga")

                val trans = AniyomiBackupTranslator(anilistApi)
                val translationDeferred = async { trans.translate(aniyomi) }
                val minDelayDeferred = async { delay(5000) }
                minDelayDeferred.await()
                val result = translationDeferred.await()

                _state.value = AniyomiRestoreState.Summary(
                    stats = result.stats,
                    resolutions = result.resolutions,
                    translationResult = result,
                    fileUri = uri,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                _state.value = AniyomiRestoreState.Error("Processing failed: ${e.message}")
            }
        }
    }

    fun onRestoreFromSummary(uri: Uri) {
        val current = _state.value as? AniyomiRestoreState.Summary ?: return
        viewModelScope.launch {
            _state.value = AniyomiRestoreState.Linking(progress = null, resolutions = current.resolutions)

            // Animate the linking results (300ms per anime)
            current.resolutions.forEachIndexed { index, res ->
                delay(300)
                _state.value = AniyomiRestoreState.Linking(
                    progress = TranslationProgress(
                        currentIndex = index + 1,
                        total = current.resolutions.size,
                        currentTitle = when (res) {
                            is AnilistResolution.Resolved -> res.anilistAnime?.title?.romaji ?: ""
                            is AnilistResolution.Failed -> res.title
                        },
                        resolved = current.resolutions.take(index + 1).count { it is AnilistResolution.Resolved },
                        failed = current.resolutions.take(index + 1).count { it is AnilistResolution.Failed },
                        resolution = res,
                    ),
                    resolutions = current.resolutions,
                )
            }

            // Execute the actual restore
            try {
                val container = current.translationResult.container
                when (val result = backupManager.restoreBackupFromContainer(container)) {
                    is BackupResult.Success -> {
                        val failed = current.resolutions.filterIsInstance<AnilistResolution.Failed>()
                        if (failed.isEmpty()) {
                            _state.value = AniyomiRestoreState.Success(stats = current.stats, skippedCount = 0)
                        } else {
                            _state.value = AniyomiRestoreState.ManualLinking(
                                failedAnime = failed,
                                resolutions = current.resolutions,
                                translationResult = current.translationResult,
                                fileUri = uri,
                            )
                        }
                    }
                    is BackupResult.Error -> _state.value = AniyomiRestoreState.Error(result.message)
                    is BackupResult.InProgress -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                _state.value = AniyomiRestoreState.Error("Restore failed: ${e.message}")
            }
        }
    }

    fun onSkipManualLinking() {
        val current = _state.value as? AniyomiRestoreState.ManualLinking ?: return
        _state.value = AniyomiRestoreState.Success(
            stats = current.translationResult.stats,
            skippedCount = current.failedAnime.size,
        )
    }

    fun cancel() { _state.value = AniyomiRestoreState.Idle }
    fun reset() { _state.value = AniyomiRestoreState.Idle }
}
