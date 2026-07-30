package app.confused.anikuta.core.appupdate

/**
 * A pluggable source for app update information.
 *
 * # Design
 *
 * The update system supports multiple sources. Currently:
 * - [GitHubUpdateSource] — checks the GitHub Releases API.
 *
 * Future sources (architecturally ready — just add a new implementation):
 * - Custom JSON endpoint — a self-hosted JSON file with version + APK URL.
 * - Firebase Remote Config — for A/B testing update rollouts.
 * - In-app purchases — gate updates behind a premium tier.
 *
 * Each source implements [fetchLatestUpdate] which returns an [AppUpdateInfo]
 * or null if no update is available (or the source is unreachable).
 *
 * # Error Handling
 *
 * Sources should NOT throw — return null on failure. The [AppUpdateManager]
 * handles logging + graceful degradation.
 */
interface UpdateSource {
    /** A unique identifier for this source (e.g., "github", "custom"). */
    val id: String

    /**
     * Fetches the latest available update info, or null if:
     * - No update is available (current version is latest).
     * - The source is unreachable (network error, API rate limit, etc.).
     * - The response is malformed.
     *
     * @param currentVersionCode the installed app's version code (for comparison).
     * @param currentVersionName the installed app's version name (for comparison).
     */
    suspend fun fetchLatestUpdate(currentVersionCode: Long, currentVersionName: String): AppUpdateInfo?
}
