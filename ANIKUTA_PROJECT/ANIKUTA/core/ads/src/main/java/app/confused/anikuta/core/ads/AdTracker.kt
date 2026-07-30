package app.confused.anikuta.core.ads

import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * On-device tracker for ad views.
 *
 * # Privacy
 *
 * ALL tracking is **on-device only**. No data is sent to any server. The
 * tracking data is used solely to enforce the user's configured ad quota +
 * cooldown — it is never transmitted, synced, or shared.
 *
 * # What is tracked
 *
 * - [adsShownToday] — how many ads the user has seen today (resets at midnight).
 * - [lastAdShownTimestamp] — when the last ad was watched (for cooldown).
 * - [totalAdsShown] — lifetime count of ads watched (for stats display).
 * - [lastResetDate] — the date string of the last daily reset (for detecting
 *   day rollover).
 *
 * # Daily Reset
 *
 * [resetDailyIfNeeded] checks if the current date differs from [lastResetDate].
 * If so, [adsShownToday] is reset to 0 and [lastResetDate] is updated. This
 * is called automatically before every [shouldShowAd] check in [AdManager].
 *
 * # Thread Safety
 *
 * PreferenceStore is thread-safe (backed by SharedPreferences with `apply()`).
 * All reads/writes are synchronous + atomic. No locks needed.
 *
 * @param preferenceStore the backing preference store.
 */
class AdTracker(
    private val preferenceStore: PreferenceStore,
) {
    private val adsShownTodayPref = preferenceStore.getInt(KEY_ADS_SHOWN_TODAY, 0)
    private val lastAdTimestampPref = preferenceStore.getLong(KEY_LAST_AD_TIMESTAMP, 0L)
    private val totalAdsPref = preferenceStore.getInt(KEY_TOTAL_ADS, 0)
    private val lastResetDatePref = preferenceStore.getString(KEY_LAST_RESET_DATE, "")

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Checks if the date has changed since the last daily reset.
     * If so, resets [adsShownToday] to 0 + updates [lastResetDate].
     *
     * Call this before any quota check to ensure the count is accurate.
     */
    fun resetDailyIfNeeded() {
        val today = dateFormatter.format(Date())
        val lastReset = lastResetDatePref.get()
        if (today != lastReset) {
            adsShownTodayPref.set(0)
            lastResetDatePref.set(today)
        }
    }

    /** How many ads the user has seen today (0 if never or after reset). */
    fun getAdsShownToday(): Int {
        resetDailyIfNeeded()
        return adsShownTodayPref.get()
    }

    /** Timestamp (epoch ms) of the last watched ad, or 0 if never. */
    fun getLastAdTimestamp(): Long = lastAdTimestampPref.get()

    /** Lifetime count of ads watched. */
    fun getTotalAdsShown(): Int = totalAdsPref.get()

    /**
     * Records a completed ad view.
     *
     * Increments [adsShownToday] + [totalAdsShown], sets [lastAdTimestamp] to now.
     * Called by [AdManager] ONLY when the user has accepted the ad AND stayed
     * for the minimum required time.
     */
    fun recordAdView() {
        resetDailyIfNeeded()
        adsShownTodayPref.set(adsShownTodayPref.get() + 1)
        totalAdsPref.set(totalAdsPref.get() + 1)
        lastAdTimestampPref.set(System.currentTimeMillis())
    }

    /**
     * Observable flow of ads shown today (for UI display).
     * Automatically triggers a daily reset on first read.
     */
    fun observeAdsShownToday(): Flow<Int> = adsShownTodayPref.changes().map { it }

    /**
     * Observable flow of the last ad timestamp (for cooldown display).
     */
    fun observeLastAdTimestamp(): Flow<Long> = lastAdTimestampPref.changes().map { it }

    /**
     * Observable flow of total lifetime ads (for stats display).
     */
    fun observeTotalAdsShown(): Flow<Int> = totalAdsPref.changes().map { it }

    /**
     * Resets ALL tracking data (for debugging/testing).
     * Does NOT reset the user's preferences — only the tracking counters.
     */
    fun resetAll() {
        adsShownTodayPref.set(0)
        lastAdTimestampPref.set(0L)
        totalAdsPref.set(0)
        lastResetDatePref.set(dateFormatter.format(Date()))
    }

    private companion object {
        private const val KEY_ADS_SHOWN_TODAY = "pref_ads_shown_today"
        private const val KEY_LAST_AD_TIMESTAMP = "pref_ads_last_timestamp"
        private const val KEY_TOTAL_ADS = "pref_ads_total"
        private const val KEY_LAST_RESET_DATE = "pref_ads_last_reset_date"
    }
}
