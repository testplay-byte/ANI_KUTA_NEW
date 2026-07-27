package app.confused.anikuta.feature.download

import app.confused.anikuta.core.download.DownloadStatus
import app.confused.anikuta.core.download.DownloadTask

/**
 * UI state for the Downloads screen.
 *
 * Two sections:
 *  - [queue]: tasks that are QUEUED / DOWNLOADING / PAUSED / ERROR (the live queue).
 *  - [downloaded]: COMPLETED tasks grouped by anime (for the expandable library list).
 *
 * [folderReady] is false until the user picks a download folder (SAF); the
 * screen shows a setup prompt in that case (explicit error handling — Rule §10).
 */
data class DownloadUiState(
    val queue: List<DownloadTask> = emptyList(),
    val downloaded: Map<DownloadedAnimeKey, List<DownloadTask>> = emptyMap(),
    val folderReady: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = queue.isEmpty() && downloaded.isEmpty()
}

/**
 * A grouping key for completed downloads — by anime. Carries the display fields
 * the card needs (title, cover, anilistId) so the UI doesn't re-derive them.
 */
data class DownloadedAnimeKey(
    val anilistId: Int,
    val title: String,
    val coverUrl: String?,
    val coverColor: Int?,
)

/** Whether a task's status means it shows in the live queue section. */
val DownloadTask.isInQueueSection: Boolean
    get() = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.DOWNLOADING ||
        status == DownloadStatus.PAUSED ||
        status == DownloadStatus.ERROR
