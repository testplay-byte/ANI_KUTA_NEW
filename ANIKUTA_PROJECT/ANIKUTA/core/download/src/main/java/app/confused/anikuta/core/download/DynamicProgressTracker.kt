package app.confused.anikuta.core.download

/**
 * Tracks download progress dynamically, handling unknown + changing total sizes.
 *
 * **The problem this solves:**
 * - Many video CDNs don't send `Content-Length` (chunked transfer encoding) →
 *   `total = -1` → the old code showed a stuck progress bar.
 * - Some servers send an initial `Content-Length` that changes mid-download
 *   (redirects, dynamic generation) → the progress bar would jump backward.
 * - The progress bar should NEVER show 100% until the download is verified
 *   complete (the owner's request: "it will never go directly to 100% unless
 *   it is 100% verified that it is completed").
 *
 * **The solution (per the owner's spec):**
 * - If `total` is known + stable: `progress = (downloaded / total) * 90` —
 *   capped at 90% so the bar never shows 100% until completion.
 * - If `total` is unknown (-1): estimate it from the download speed + a
 *   heuristic (grow the estimate as bytes arrive, so the bar advances slowly
 *   toward 90%). If the real total becomes known, snap to the correct ratio.
 * - If `total` changes mid-download: use the LARGER of the old + new total
 *   (never let the bar jump backward). If the downloaded bytes exceed the
 *   new total, re-estimate.
 * - On completion: the queue sets progress to 100 (the tracker's job ends at 90).
 *
 * **Thread-safety:** all methods are synchronous + stateless (pure math on the
 * inputs). The caller (DownloadQueue) handles thread-safety via its mutex.
 */
object DynamicProgressTracker {

    /** The max progress shown during download (never 100 until complete). */
    private const val MAX_INCOMPLETE_PROGRESS = 90

    /** The initial size estimate when total is unknown (10 MB). Grows over time. */
    private const val INITIAL_ESTIMATE_BYTES = 10L * 1024 * 1024

    /**
     * Minimum valid total bytes (1 MB). Content-Length values below this are
     * treated as invalid (some servers return 44B for redirects/error pages).
     * If the downloaded bytes exceed this threshold, the reported total is
     * ignored + the estimator takes over.
     */
    private const val MIN_VALID_TOTAL_BYTES = 1L * 1024 * 1024

    /**
     * Computes the display progress (0..90) + the display total bytes.
     *
     * @param downloaded The bytes downloaded so far.
     * @param reportedTotal The total bytes reported by the server (-1 = unknown).
     * @param previousTotal The total bytes from the previous tick (for change detection).
     * @param previousEstimate The running estimate when total is unknown (0 = no estimate yet).
     * @return [ProgressUpdate] with the display progress, display total, + updated estimate.
     */
    fun compute(
        downloaded: Long,
        reportedTotal: Long,
        previousTotal: Long,
        previousEstimate: Long,
    ): ProgressUpdate {
        // ── Sanity check: ignore obviously-wrong Content-Length ──
        // Some servers return Content-Length: 44 (a redirect/error page) or
        // other tiny values for chunked streams. A real video is at least
        // 1 MB. If the reported total is < 1 MB but we've already downloaded
        // more than that, the server is lying → treat as unknown.
        val effectiveReportedTotal = if (reportedTotal in 1..MIN_VALID_TOTAL_BYTES &&
            downloaded > reportedTotal) {
            -1L // treat as unknown
        } else {
            reportedTotal
        }

        // ── Case 1: total is known + stable + valid ──
        if (effectiveReportedTotal >= MIN_VALID_TOTAL_BYTES) {
            // If the total changed (server sent a different Content-Length),
            // use the larger value so the bar never jumps backward.
            val effectiveTotal = maxOf(effectiveReportedTotal, previousTotal.coerceAtLeast(0))
            val ratio = (downloaded.toDouble() / effectiveTotal).coerceIn(0.0, 1.0)
            val progress = (ratio * MAX_INCOMPLETE_PROGRESS).toInt().coerceIn(0, MAX_INCOMPLETE_PROGRESS)
            return ProgressUpdate(
                progress = progress,
                displayTotalBytes = effectiveTotal,
                updatedEstimate = 0L,
            )
        }

        // ── Case 2: total is unknown (-1) or too small to be real — estimate ──
        // Grow the estimate so the bar advances slowly toward 90%. If the
        // downloaded bytes exceed 90% of the estimate, grow the estimate.
        var estimate = if (previousEstimate > 0) previousEstimate else INITIAL_ESTIMATE_BYTES
        // If downloaded exceeds 90% of the estimate, grow the estimate by 50%.
        while (downloaded > estimate * 0.9) {
            estimate = (estimate * 1.5).toLong()
        }
        val ratio = (downloaded.toDouble() / estimate).coerceIn(0.0, 0.9)
        val progress = (ratio * MAX_INCOMPLETE_PROGRESS).toInt().coerceIn(0, MAX_INCOMPLETE_PROGRESS)
        return ProgressUpdate(
            progress = progress,
            displayTotalBytes = estimate, // show the estimate as the total
            updatedEstimate = estimate,
        )
    }

    /** The result of [compute]. */
    data class ProgressUpdate(
        /** Display progress 0..90 (never 100 until complete). */
        val progress: Int,
        /** The total bytes to display (real or estimated). */
        val displayTotalBytes: Long,
        /** The updated running estimate (for the next tick; 0 if total is known). */
        val updatedEstimate: Long,
    )
}
