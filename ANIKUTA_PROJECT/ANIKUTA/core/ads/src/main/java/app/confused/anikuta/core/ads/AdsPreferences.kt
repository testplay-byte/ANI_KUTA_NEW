package app.confused.anikuta.core.ads

import app.confused.anikuta.core.preferences.PreferenceStore

/**
 * User-configurable advertising preferences — persisted via [PreferenceStore].
 *
 * The ANIKUTA ad system (planned, see ADR roadmap) is opt-in and non-intrusive.
 * These preferences control the user-facing behavior:
 *
 * - [adsEnabled] — master switch. When `false`, NO ads are shown anywhere.
 * - [dailyQuota] — max number of ads the user is willing to see per day.
 *   Default `6` (= the "2 ads/day" wizard default × 3, the wizard's frequency→quota
 *   multiplier). The wizard maps its 1–3 frequency picker to `frequency * 3`.
 * - [cooldownMinutes] — minimum gap between two consecutive ad impressions.
 *   Default `30`. Prevents ad fatigue.
 * - [minStaySeconds] — minimum time the user must keep an ad open before it
 *   counts as "viewed". Default `5`. Matches typical rewarded-ad UX.
 * - [adUrl] — the URL of the ad creative / landing page. Default points at the
 *   ANIKUTA placeholder. The (future) ad-rendering overlay loads this in a WebView.
 *
 * **Why this lives in `:core:ads` (not `:core:preferences`):** per ADR-018, ad
 * config is its own bounded context. Keeping it next to the (future) ad-rendering
 * code avoids leaking ad-domain concerns into the generic preferences module.
 */
class AdsPreferences(
    private val store: PreferenceStore,
) {
    private val adsEnabledPref = store.getBoolean("pref_ads_enabled", false)
    private val dailyQuotaPref = store.getInt("pref_ads_daily_quota", DEFAULT_DAILY_QUOTA)
    private val cooldownMinutesPref = store.getInt("pref_ads_cooldown_minutes", DEFAULT_COOLDOWN_MINUTES)
    private val minStaySecondsPref = store.getInt("pref_ads_min_stay_seconds", DEFAULT_MIN_STAY_SECONDS)
    private val adUrlPref = store.getString("pref_ads_url", DEFAULT_AD_URL)

    /** Master switch. When `false`, no ads are shown anywhere. */
    fun isAdsEnabled(): Boolean = adsEnabledPref.get()

    /** Master switch. Set to `true` to opt into the ad system. */
    fun setAdsEnabled(enabled: Boolean) = adsEnabledPref.set(enabled)

    /** Max ads per day. The wizard's frequency picker maps to `frequency * 3`. */
    fun getDailyQuota(): Int = dailyQuotaPref.get()

    /** Max ads per day. The wizard's frequency picker maps to `frequency * 3`. */
    fun setDailyQuota(quota: Int) = dailyQuotaPref.set(quota)

    /** Minimum minutes between two consecutive ad impressions. */
    fun getCooldownMinutes(): Int = cooldownMinutesPref.get()

    /** Minimum minutes between two consecutive ad impressions. */
    fun setCooldownMinutes(minutes: Int) = cooldownMinutesPref.set(minutes)

    /** Minimum seconds the user must keep an ad open before it counts as viewed. */
    fun getMinStaySeconds(): Int = minStaySecondsPref.get()

    /** Minimum seconds the user must keep an ad open before it counts as viewed. */
    fun setMinStaySeconds(seconds: Int) = minStaySecondsPref.set(seconds)

    /** The URL of the ad creative / landing page. */
    fun getAdUrl(): String = adUrlPref.get()

    /** The URL of the ad creative / landing page. */
    fun setAdUrl(url: String) = adUrlPref.set(url)

    companion object {
        const val DEFAULT_DAILY_QUOTA = 6
        const val DEFAULT_COOLDOWN_MINUTES = 30
        const val DEFAULT_MIN_STAY_SECONDS = 5
        const val DEFAULT_AD_URL = "https://anikuta.app/ads"
    }
}
