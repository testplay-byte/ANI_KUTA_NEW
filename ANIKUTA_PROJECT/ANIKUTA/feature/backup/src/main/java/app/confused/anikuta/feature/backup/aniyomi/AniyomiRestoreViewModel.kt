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

/** Minimum processing animation time (2 seconds per owner request). */
private const val MIN_PROCESSING_MS = 2000L

/**
 * UI state for the Aniyomi restore flow.
 */
sealed class AniyomiRestoreState {
    /** Initial — waiting for a file URI to be passed in. */
    object Idle : AniyomiRestoreState()

    /** Step 1: Format detected — show the detection screen (red→green on button click). */
    data class FormatDetected(val isSupported: Boolean, val formatName: String, val fileUri: Uri) : AniyomiRestoreState()

    /** Step 2: Processing (decoding + translating, 2 sec min). */
    data class Processing(val message: String = "Processing backup…") : AniyomiRestoreState()

    /** Step 3: Summary — show stats + Restore/Cancel. */
    data class Summary(
        val stats: TranslationStats,
        val resolutions: List<AnilistResolution>,
        val translationResult: TranslationResult,
        val fileUri: Uri,
    ) : AniyomiRestoreState()

    /** Step 4: Linking — STAYS until user clicks "Next". Restore has already executed. */
    data class Linking(
        val progress: TranslationProgress?,
        val resolutions: List<AnilistResolution>,
        val allDone: Boolean,
        val restoreSuccess: Boolean,
    ) : AniyomiRestoreState()

    /** Step 5: Manual linking — user picks matches for failed anime. */
    data class ManualLinking(
        val failedAnime: List<AnilistResolution.Failed>,
        val resolutions: List<AnilistResolution>,
        val translationResult: TranslationResult,
        val fileUri: Uri,
    ) : AniyomiRestoreState()

    /** Step 6: Success — auto-close after 5 seconds. */
    data class Success(val stats: TranslationStats, val skippedCount: Int) : AniyomiRestoreState()

    /** Error. */
    data class Error(val message: String) : AniyomiRestoreState()
}

/**
 * ViewModel for the Aniyomi restore flow (6-step multi-screen).
 *
 * Key behaviors:
 * - File URI is passed in directly (no double file picker).
 * - Processing animation: 2 sec min.
 * - Linking screen STAYS until user clicks "Next" (doesn't auto-advance).
 * - Rate limited via AniListRateLimiter (80 req/min dynamic).
 */
class AniyomiRestoreViewModel(
    private val anilistApi: AniListApi,
    private val backupStorage: BackupStorage,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow<AniyomiRestoreState>(AniyomiRestoreState.Idle)
    val state: StateFlow<AniyomiRestoreState> = _state.asStateFlow()

    /**
     * Called directly with a file URI (no file picker — the caller already picked it).
     * Reads the first bytes to detect the format.
     */
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

    /**
     * Step 1 → Step 2: User clicked "Continue" on the format detection screen.
     * Starts processing (decode + translate) with a minimum 2-second animation.
     */
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
                val minDelayDeferred = async { delay(MIN_PROCESSING_MS) }
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

    /**
     * Step 3 → Step 4: User clicked "Restore" on the summary screen.
     * Executes the actual restore + animates the linking results.
     * STAYS on the Linking screen until the user clicks "Next".
     */
    fun onRestoreFromSummary(uri: Uri) {
        val current = _state.value as? AniyomiRestoreState.Summary ?: return
        viewModelScope.launch {
            // Execute the actual restore FIRST (before animating)
            var restoreSuccess = false
            try {
                val container = current.translationResult.container
                when (val result = backupManager.restoreBackupFromContainer(container)) {
                    is BackupResult.Success -> {
                        restoreSuccess = true
                        Log.i(TAG, "Restore completed: ${result.data.totalImported} imported")
                    }
                    is BackupResult.Error -> {
                        Log.e(TAG, "Restore failed: ${result.message}")
                        _state.value = AniyomiRestoreState.Error(result.message)
                        return@launch
                    }
                    is BackupResult.InProgress -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                _state.value = AniyomiRestoreState.Error("Restore failed: ${e.message}")
                return@launch
            }

            // Now animate the linking results (300ms per anime)
            _state.value = AniyomiRestoreState.Linking(
                progress = null,
                resolutions = current.resolutions,
                allDone = false,
                restoreSuccess = restoreSuccess,
            )

            current.resolutions.forEachIndexed { index, res ->
                delay(300)
                _state.value = AniyomiRestoreState.Linking(
                    progress = TranslationProgress(
                        currentIndex = index + 1,
                        total = current.resolutions.size,
                        currentTitle = when (res) {
                            is AnilistResolution.Resolved -> res.anilistAnime?.title?.romaji ?: res.anilistAnime?.title?.english ?: ""
                            is AnilistResolution.Failed -> res.title
                        },
                        resolved = current.resolutions.take(index + 1).count { it is AnilistResolution.Resolved },
                        failed = current.resolutions.take(index + 1).count { it is AnilistResolution.Failed },
                        resolution = res,
                    ),
                    resolutions = current.resolutions,
                    allDone = (index + 1 >= current.resolutions.size),
                    restoreSuccess = restoreSuccess,
                )
            }
            // STAY on the Linking screen — user must click "Next" to proceed.
        }
    }

    /**
     * Step 4 → Step 5/6: User clicked "Next" on the linking screen.
     * If there are failed anime → go to manual linking. Otherwise → success.
     */
    fun onNextFromLinking() {
        val current = _state.value as? AniyomiRestoreState.Linking ?: return
        val failed = current.resolutions.filterIsInstance<AnilistResolution.Failed>()
        if (failed.isEmpty()) {
            val stats = TranslationStats(
                totalAnime = current.resolutions.size,
                resolvedAnime = current.resolutions.count { it is AnilistResolution.Resolved },
                failedAnime = 0,
                totalEpisodes = 0,
                totalCategories = 0,
                totalManga = 0,
                totalMangaCategories = 0,
            )
            _state.value = AniyomiRestoreState.Success(stats = stats, skippedCount = 0)
        } else {
            // Build a dummy TranslationResult for the ManualLinking state
            val dummyResult = TranslationResult(
                container = app.confused.anikuta.core.backup.model.BackupContainer(
                    createdAt = System.currentTimeMillis(),
                    entries = emptyList(),
                ),
                resolutions = current.resolutions,
                stats = TranslationStats(
                    totalAnime = current.resolutions.size,
                    resolvedAnime = current.resolutions.count { it is AnilistResolution.Resolved },
                    failedAnime = failed.size,
                    totalEpisodes = 0,
                    totalCategories = 0,
                    totalManga = 0,
                    totalMangaCategories = 0,
                ),
            )
            _state.value = AniyomiRestoreState.ManualLinking(
                failedAnime = failed,
                resolutions = current.resolutions,
                translationResult = dummyResult,
                fileUri = Uri.EMPTY,
            )
        }
    }

    /**
     * Step 5 → Step 6: User clicked "Skip & Continue" on the manual linking screen.
     */
    fun onSkipManualLinking() {
        val current = _state.value as? AniyomiRestoreState.ManualLinking ?: return
        _state.value = AniyomiRestoreState.Success(
            stats = current.translationResult.stats,
            skippedCount = current.failedAnime.size,
        )
    }

    /**
     * Searches AniList by title. Used by the manual linking screen.
     * @param query the search query (usually the anime title from the backup).
     * @return list of matching AniList anime.
     */
    suspend fun searchAniList(query: String): List<app.confused.anikuta.core.anilist.model.AniListAnime> {
        return try {
            anilistApi.searchByTitleMultiple(query, perPage = 10)
        } catch (e: Exception) {
            Log.e(TAG, "searchAniList failed for '$query'", e)
            emptyList()
        }
    }

    /**
     * Manually links a failed anime to an AniList anime.
     * Removes the anime from the failed list + updates the restore.
     * @param failed the failed resolution to link.
     * @param anime the AniList anime the user selected.
     */
    fun manuallyLink(failed: AnilistResolution.Failed, anime: app.confused.anikuta.core.anilist.model.AniListAnime) {
        val current = _state.value as? AniyomiRestoreState.ManualLinking ?: return
        Log.i(TAG, "Manual link: '${failed.title}' → AniList #${anime.id} (${anime.title.romaji})")

        // Remove the failed anime from the list
        val updatedFailed = current.failedAnime.filter { it.title != failed.title }

        if (updatedFailed.isEmpty()) {
            // All failed anime have been linked → go to success
            val stats = TranslationStats(
                totalAnime = current.resolutions.size,
                resolvedAnime = current.resolutions.count { it is AnilistResolution.Resolved } + 1,
                failedAnime = 0,
                totalEpisodes = 0,
                totalCategories = 0,
                totalManga = 0,
                totalMangaCategories = 0,
            )
            _state.value = AniyomiRestoreState.Success(stats = stats, skippedCount = 0)
        } else {
            // Still have failed anime → update the list
            _state.value = current.copy(failedAnime = updatedFailed)
        }
    }

    fun cancel() { _state.value = AniyomiRestoreState.Idle }
    fun reset() { _state.value = AniyomiRestoreState.Idle }
}
