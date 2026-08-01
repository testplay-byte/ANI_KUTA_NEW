package app.confused.anikuta.core.ads

import app.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User-configurable preferences for the advertising system.
 *
 * # Design Philosophy
 *
 * The advertising system is **highly customizable** — every aspect of ad
 * delivery is controlled by user-facing settings. This ensures:
 * 1. The user is always in control of their ad experience.
 * 2. Testing is easy (e.g., 1000 ads/day for thorough testing).
 * 3. Future fine-tuning doesn't require code changes.
 *
 * # Configurable Parameters
 *
 * - [adsEnabled] — master on/off toggle.
 * - [dailyAdQuota] — how many ads the user sees per day (1–1000).
 *   Default: **1000** (testing mode — gives maximum flexibility for QA).
 *   Production default would be 1–10.
 * - [cooldownMinutes] — after watching an ad, no new ad is shown for this
 *   many minutes. Default: **30**.
 * - [minStaySeconds] — the minimum time the user must stay on the ad URL
 *   before returning. If they return sooner, the ad is NOT counted and a
 *   "please stay longer" message is shown. Default: **2**.
 * - [adUrl] — the URL the user is redirected to when they accept an ad.
 *   Default: `https://1118000.xyz/` (placeholder for testing).
 *
 * # Daily Reset Logic
 *
 * The daily quota resets at midnight (device local time). The [AdTracker]
 * handles this automatically — see [AdTracker.resetDailyIfNeeded].
 *
 * # Future Extensions (architecturally ready)
 *
 * - **Ad scheduling** — only show ads during certain hours.
 * - **Ad frequency capping** — max N ads per hour (in addition to daily quota).
 * - **Remote config** — fetch ad settings from a server (override local).
 * - **A/B testing** — different ad configs for different user cohorts.
 * - **Per-context ads** — different ad frequency for library vs search vs browse.
 *
 * @param preferenceStore the backing preference store.
 */
class AdsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /** Master on/off toggle. Default: true. */
    private val adsEnabledPref = preferenceStore.getBoolean(KEY_ADS_ENABLED, true)

    /** Daily ad quota (1–1000). Default: 1000 (testing mode). */
    private val dailyQuotaPref = preferenceStore.getInt(KEY_DAILY_QUOTA, DEFAULT_DAILY_QUOTA)

    /** Cooldown in minutes after watching an ad. Default: 30. */
    private val cooldownMinutesPref = preferenceStore.getInt(KEY_COOLDOWN_MINUTES, DEFAULT_COOLDOWN_MINUTES)

    /** Minimum stay on ad URL in seconds. Default: 2. */
    private val minStaySecondsPref = preferenceStore.getInt(KEY_MIN_STAY_SECONDS, DEFAULT_MIN_STAY_SECONDS)

    /** The ad URL. Default: https://1118000.xyz/ */
    private val adUrlPref = preferenceStore.getString(KEY_AD_URL, DEFAULT_AD_URL)

    // ── Ads enabled ──

    fun isAdsEnabled(): Boolean = adsEnabledPref.get()

    fun setAdsEnabled(enabled: Boolean) = adsEnabledPref.set(enabled)

    fun observeAdsEnabled(): Flow<Boolean> = adsEnabledPref.changes()

    // ── Daily quota ──

    fun getDailyQuota(): Int = dailyQuotaPref.get().coerceIn(MIN_QUOTA, MAX_QUOTA)

    fun setDailyQuota(quota: Int) {
        dailyQuotaPref.set(quota.coerceIn(MIN_QUOTA, MAX_QUOTA))
    }

    fun observeDailyQuota(): Flow<Int> = dailyQuotaPref.changes().map { it.coerceIn(MIN_QUOTA, MAX_QUOTA) }

    // ── Cooldown ──

    fun getCooldownMinutes(): Int = cooldownMinutesPref.get().coerceIn(0, MAX_COOLDOWN_MINUTES)

    fun setCooldownMinutes(minutes: Int) {
        cooldownMinutesPref.set(minutes.coerceIn(0, MAX_COOLDOWN_MINUTES))
    }

    fun observeCooldownMinutes(): Flow<Int> =
        cooldownMinutesPref.changes().map { it.coerceIn(0, MAX_COOLDOWN_MINUTES) }

    // ── Min stay ──

    fun getMinStaySeconds(): Int = minStaySecondsPref.get().coerceIn(1, MAX_MIN_STAY_SECONDS)

    fun setMinStaySeconds(seconds: Int) {
        minStaySecondsPref.set(seconds.coerceIn(1, MAX_MIN_STAY_SECONDS))
    }

    fun observeMinStaySeconds(): Flow<Int> =
        minStaySecondsPref.changes().map { it.coerceIn(1, MAX_MIN_STAY_SECONDS) }

    // ── Ad URL ──

    fun getAdUrl(): String = adUrlPref.get()

    fun setAdUrl(url: String) = adUrlPref.set(url)

    fun observeAdUrl(): Flow<String> = adUrlPref.changes()

    private companion object {
        private const val KEY_ADS_ENABLED = "pref_ads_enabled"
        private const val KEY_DAILY_QUOTA = "pref_ads_daily_quota"
        private const val KEY_COOLDOWN_MINUTES = "pref_ads_cooldown_minutes"
        private const val KEY_MIN_STAY_SECONDS = "pref_ads_min_stay_seconds"
        private const val KEY_AD_URL = "pref_ads_url"

        private const val MIN_QUOTA = 1
        private const val MAX_QUOTA = 1000
        private const val DEFAULT_DAILY_QUOTA = 1000 // Testing mode — change to 1-10 for production
        private const val DEFAULT_COOLDOWN_MINUTES = 30
        private const val MAX_COOLDOWN_MINUTES = 1440 // 24 hours
        private const val DEFAULT_MIN_STAY_SECONDS = 2
        private const val MAX_MIN_STAY_SECONDS = 60
        private const val DEFAULT_AD_URL = "https://1118000.xyz/"
    }
}
