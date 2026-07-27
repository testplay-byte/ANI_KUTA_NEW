package app.anikuta.notification

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

/**
 * Phase N-1 (Notifications) — Global notification + auto-download preferences.
 *
 * These are the user-facing settings shown in Settings → Notifications.
 * Per-anime settings live in [ReleaseTrackingStore.TrackedAnime] and override
 * these globals when non-null.
 *
 * See DOCS/PLAN/NOTIFICATIONS-PLAN.md §4.3 for the full settings table.
 */
class NotificationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // ---- General ----

    fun globalNotifyEnabled(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_global_enabled", true)

    /**
     * Notify mode (§2.4):
     * - "anilist"   = Mode 1: notify at AniList airing time
     * - "extension" = Mode 2: notify only when extension confirms (DEFAULT)
     * - "both"      = Mode 3: both AniList + extension notifications
     */
    fun notifyMode(): Preference<String> =
        preferenceStore.getString("notif_mode", "extension")

    fun checkCompletedAnime(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_check_completed", false)

    // ---- Sub/Dub ----

    fun globalNotifySub(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_global_sub", true)

    fun globalNotifyDub(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_global_dub", true)

    // ---- Auto-download (new releases) ----

    fun globalAutoDownloadEnabled(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_autodl_enabled", false)

    fun globalAutoDownloadSub(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_autodl_sub", true)

    fun globalAutoDownloadDub(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_autodl_dub", false)

    /** Preferred quality for auto-downloads: "1080", "720", "360", or "best". */
    fun globalAutoDownloadQuality(): Preference<String> =
        preferenceStore.getString("notif_autodl_quality", "1080")

    /** Preferred audio for auto-downloads: "SUB", "DUB", or "ANY". */
    fun globalAutoDownloadAudio(): Preference<String> =
        preferenceStore.getString("notif_autodl_audio", "SUB")

    // ---- Watch-flow auto-download (Phase N-6, separate feature) ----

    fun watchFlowAutoDownloadEnabled(): Preference<Boolean> =
        preferenceStore.getBoolean("watchflow_autodl_enabled", false)

    fun watchFlowAutoDownloadAudio(): Preference<String> =
        preferenceStore.getString("watchflow_autodl_audio", "SUB")

    fun watchFlowAutoDownloadQuality(): Preference<String> =
        preferenceStore.getString("watchflow_autodl_quality", "1080")

    // ---- Quiet hours ----

    fun quietHoursEnabled(): Preference<Boolean> =
        preferenceStore.getBoolean("notif_quiet_hours_enabled", false)

    /** Quiet hours start hour (0-23). Default 23 (11 PM). */
    fun quietHoursStart(): Preference<Int> =
        preferenceStore.getInt("notif_quiet_start", 23)

    /** Quiet hours end hour (0-23). Default 7 (7 AM). */
    fun quietHoursEnd(): Preference<Int> =
        preferenceStore.getInt("notif_quiet_end", 7)

    // ---- Helpers ----

    /**
     * Check if the current time is within quiet hours.
     * Handles overnight wrap (e.g. 23:00 → 07:00).
     */
    fun isCurrentlyQuietHour(currentHour: Int): Boolean {
        if (!quietHoursEnabled().get()) return false
        val start = quietHoursStart().get()
        val end = quietHoursEnd().get()
        return if (start <= end) {
            currentHour in start until end
        } else {
            // Wraps midnight: e.g. 23 to 7
            currentHour >= start || currentHour < end
        }
    }
}
