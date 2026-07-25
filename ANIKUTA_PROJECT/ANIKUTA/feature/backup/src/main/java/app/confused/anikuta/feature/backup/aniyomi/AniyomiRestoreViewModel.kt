package app.confused.anikuta.feature.backup.aniyomi

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
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
private const val MIN_PROCESSING_MS = 2000L

/**
 * UI state for the Aniyomi restore flow.
 *
 * NEW WORKFLOW (no restore until the very end):
 * 1. FormatDetected → 2. Processing → 3. Summary → 4. Linking →
 * 5. ManualLinking → 6. PreRestoreSummary → 7. Restore (execute) → 8. Success
 */
sealed class AniyomiRestoreState {
    object Idle : AniyomiRestoreState()
    data class FormatDetected(val isSupported: Boolean, val formatName: String, val fileUri: Uri) : AniyomiRestoreState()
    data class Processing(val message: String = "Processing backup…") : AniyomiRestoreState()
    data class Summary(val stats: TranslationStats, val resolutions: List<AnilistResolution>, val translationResult: TranslationResult, val fileUri: Uri, val aniyomiBackup: AniyomiBackup) : AniyomiRestoreState()
    data class Linking(val progress: TranslationProgress?, val resolutions: List<AnilistResolution>, val allDone: Boolean, val translationResult: TranslationResult, val aniyomiBackup: AniyomiBackup) : AniyomiRestoreState()
    data class ManualLinking(val failedAnime: List<AnilistResolution>, val rateLimitedAnime: List<AnilistResolution.RateLimited>, val resolutions: List<AnilistResolution>, val translationResult: TranslationResult, val fileUri: Uri, val aniyomiBackup: AniyomiBackup) : AniyomiRestoreState()
    data class PreRestoreSummary(val resolutions: List<AnilistResolution>, val translationResult: TranslationResult, val aniyomiBackup: AniyomiBackup, val fileUri: Uri) : AniyomiRestoreState()
    data class Restoring(val message: String = "Restoring…") : AniyomiRestoreState()
    data class Success(val stats: TranslationStats, val skippedCount: Int) : AniyomiRestoreState()
    data class Error(val message: String) : AniyomiRestoreState()
}

class AniyomiRestoreViewModel(
    private val anilistApi: AniListApi,
    private val backupStorage: BackupStorage,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow<AniyomiRestoreState>(AniyomiRestoreState.Idle)
    val state: StateFlow<AniyomiRestoreState> = _state.asStateFlow()

    private var decodedAniyomi: AniyomiBackup? = null

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
                decodedAniyomi = aniyomi
                Log.i(TAG, "Decoded Aniyomi backup: ${aniyomi.backupAnime.size} total anime, ${aniyomi.backupAnime.count { it.favorite }} favorite, ${aniyomi.backupManga.size} manga")

                val trans = AniyomiBackupTranslator(anilistApi)
                val translationDeferred = async { trans.translate(aniyomi) }
                val minDelayDeferred = async { delay(MIN_PROCESSING_MS) }
                minDelayDeferred.await()
                val result = translationDeferred.await()

                _state.value = AniyomiRestoreState.Summary(
                    stats = result.stats,
                    resolutions = result.resolutions,
                    translationResult = result,
                    fileUri = uri,
                    aniyomiBackup = aniyomi,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                _state.value = AniyomiRestoreState.Error("Processing failed: ${e.message}")
            }
        }
    }

    /**
     * Step 3 → Step 4: User clicked "Restore" on the summary screen.
     * Starts the linking animation. NO actual restore happens here.
     */
    fun onRestoreFromSummary(uri: Uri) {
        val current = _state.value as? AniyomiRestoreState.Summary ?: return
        viewModelScope.launch {
            _state.value = AniyomiRestoreState.Linking(
                progress = null,
                resolutions = current.resolutions,
                allDone = false,
                translationResult = current.translationResult,
                aniyomiBackup = current.aniyomiBackup,
            )

            // Animate the linking results (300ms per anime)
            current.resolutions.forEachIndexed { index, res ->
                delay(300)
                _state.value = AniyomiRestoreState.Linking(
                    progress = TranslationProgress(
                        currentIndex = index + 1,
                        total = current.resolutions.size,
                        currentTitle = when (res) {
                            is AnilistResolution.Resolved -> res.anilistAnime?.title?.romaji ?: res.anilistAnime?.title?.english ?: res.originalTitle
                            is AnilistResolution.Failed -> res.title
                            is AnilistResolution.RateLimited -> res.title
                        },
                        resolved = current.resolutions.take(index + 1).count { it is AnilistResolution.Resolved },
                        failed = current.resolutions.take(index + 1).count { it is AnilistResolution.Failed },
                        resolution = res,
                    ),
                    resolutions = current.resolutions,
                    allDone = (index + 1 >= current.resolutions.size),
                    translationResult = current.translationResult,
                    aniyomiBackup = current.aniyomiBackup,
                )
            }
            // STAY on the Linking screen — user must click "Next" to proceed.
        }
    }

    /**
     * Retry rate-limited anime. Called from the Linking screen's "Retry remaining" button.
     */
    fun onRetryRateLimited() {
        val current = _state.value as? AniyomiRestoreState.Linking ?: return
        viewModelScope.launch {
            val trans = AniyomiBackupTranslator(anilistApi)
            val result = trans.retryRateLimited(current.aniyomiBackup, current.resolutions)

            // Re-animate the retried entries
            val updatedResolutions = result.resolutions
            _state.value = current.copy(
                resolutions = updatedResolutions,
                progress = TranslationProgress(
                    currentIndex = updatedResolutions.size,
                    total = updatedResolutions.size,
                    currentTitle = "",
                    resolved = updatedResolutions.count { it is AnilistResolution.Resolved },
                    failed = updatedResolutions.count { it is AnilistResolution.Failed },
                    resolution = null,
                ),
                translationResult = result,
            )
        }
    }

    /**
     * Step 4 → Step 5: User clicked "Next" on the linking screen.
     * Goes to manual linking if there are failed/rate-limited anime, otherwise to pre-restore summary.
     */
    fun onNextFromLinking() {
        val current = _state.value as? AniyomiRestoreState.Linking ?: return
        val failed = current.resolutions.filter { it is AnilistResolution.Failed }
        val rateLimited = current.resolutions.filterIsInstance<AnilistResolution.RateLimited>()

        if (failed.isEmpty() && rateLimited.isEmpty()) {
            // No failures → go directly to pre-restore summary
            _state.value = AniyomiRestoreState.PreRestoreSummary(
                resolutions = current.resolutions,
                translationResult = current.translationResult,
                aniyomiBackup = current.aniyomiBackup,
                fileUri = Uri.EMPTY,
            )
        } else {
            _state.value = AniyomiRestoreState.ManualLinking(
                failedAnime = failed + rateLimited,
                rateLimitedAnime = rateLimited,
                resolutions = current.resolutions,
                translationResult = current.translationResult,
                fileUri = Uri.EMPTY,
                aniyomiBackup = current.aniyomiBackup,
            )
        }
    }

    /**
     * Step 5 → Step 6: User clicked "Continue" on the manual linking screen.
     * Goes to the pre-restore summary. NO restore yet.
     */
    fun onContinueFromManualLinking() {
        val current = _state.value as? AniyomiRestoreState.ManualLinking ?: return
        _state.value = AniyomiRestoreState.PreRestoreSummary(
            resolutions = current.resolutions,
            translationResult = current.translationResult,
            aniyomiBackup = current.aniyomiBackup,
            fileUri = current.fileUri,
        )
    }

    /**
     * Step 6 → Step 7: User clicked "Restore" on the pre-restore summary.
     * THIS is where the actual restore happens.
     */
    fun onExecuteRestore() {
        val current = _state.value as? AniyomiRestoreState.PreRestoreSummary ?: return
        viewModelScope.launch {
            _state.value = AniyomiRestoreState.Restoring()
            try {
                val container = current.translationResult.container
                when (val result = backupManager.restoreBackupFromContainer(container)) {
                    is BackupResult.Success -> {
                        val skipped = current.resolutions.count {
                            it is AnilistResolution.Failed || it is AnilistResolution.RateLimited
                        }
                        val stats = TranslationStats(
                            totalAnime = current.resolutions.size,
                            resolvedAnime = current.resolutions.count { it is AnilistResolution.Resolved },
                            failedAnime = skipped,
                            totalEpisodes = 0, totalCategories = 0, totalManga = 0, totalMangaCategories = 0,
                        )
                        _state.value = AniyomiRestoreState.Success(stats = stats, skippedCount = skipped)
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

    suspend fun searchAniList(query: String): List<AniListAnime> {
        return try { anilistApi.searchByTitleMultiple(query, perPage = 10) }
        catch (e: Exception) { Log.e(TAG, "searchAniList failed", e); emptyList() }
    }

    fun manuallyLink(failed: AnilistResolution, anime: AniListAnime) {
        val current = _state.value as? AniyomiRestoreState.ManualLinking ?: return
        val newResolved = AnilistResolution.Resolved(anime.id, anime, "manual", failed.title)
        val updatedResolutions = current.resolutions.map { res ->
            if (res == failed || (res is AnilistResolution.Failed && res.title == failed.title) ||
                (res is AnilistResolution.RateLimited && res.title == failed.title)) {
                newResolved
            } else { res }
        }
        val updatedFailed = current.failedAnime.filter { it != failed }
        _state.value = current.copy(
            failedAnime = updatedFailed,
            resolutions = updatedResolutions,
        )
    }

    fun markAsWrong(resolved: AnilistResolution.Resolved) {
        val current = _state.value as? AniyomiRestoreState.Linking ?: return
        val updatedResolutions = current.resolutions.map { res ->
            if (res is AnilistResolution.Resolved && res.anilistId == resolved.anilistId) {
                AnilistResolution.Failed(resolved.originalTitle, "Marked as wrong match")
            } else { res }
        }
        _state.value = current.copy(resolutions = updatedResolutions)
    }

    fun cancel() { _state.value = AniyomiRestoreState.Idle }
    fun reset() { _state.value = AniyomiRestoreState.Idle }
}
