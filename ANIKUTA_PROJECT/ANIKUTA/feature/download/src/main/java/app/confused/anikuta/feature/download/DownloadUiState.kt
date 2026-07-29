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
 * the card needs (title, cover, contentId) so the UI doesn't re-derive them.
 *
 * **Phase 6 (ADR-050):** keyed by [contentId] (String, e.g. `"al:154587"`).
 * Replaces the old `anilistId: Int` field — unlinked extension anime now have
 * a stable key (their content_id falls back to the local_id).
 */
data class DownloadedAnimeKey(
    val contentId: String,
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
