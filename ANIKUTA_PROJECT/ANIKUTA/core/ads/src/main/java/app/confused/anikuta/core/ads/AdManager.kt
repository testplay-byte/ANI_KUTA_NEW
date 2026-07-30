package app.confused.anikuta.core.ads

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state machine for a single ad interaction.
 *
 * Lifecycle:
 * ```
 * Idle → DialogShowing → AdInProgress → Completed → Idle
 *                    ↘ Cancelled → Idle
 *                    ↘ AdInProgress → ReturnedTooEarly → DialogShowing (retry)
 * ```
 *
 * - **[Idle]** — no ad in progress. [AdManager.shouldShowAd] can be called.
 * - **[DialogShowing]** — the ad dialog is visible. User must click OK or Cancel.
 * - **[AdInProgress]** — user clicked OK. The browser is open. We're waiting
 *   for the user to return to the app (detected via Activity onResume).
 * - **[ReturnedTooEarly]** — user returned before [AdsPreferences.getMinStaySeconds].
 *   A "please stay longer" message is shown. The ad is NOT counted.
 * - **[Completed]** — user returned after the minimum stay. The ad is counted.
 *   The deferred navigation proceeds. Transitions back to [Idle].
 * - **[Cancelled]** — user clicked Cancel. The ad is NOT counted. The deferred
 *   navigation proceeds. Transitions back to [Idle].
 */
sealed interface AdInteractionState {
    data object Idle : AdInteractionState
    data class DialogShowing(val adUrl: String, val remainingQuota: Int) : AdInteractionState
    data class AdInProgress(val adUrl: String, val openedAt: Long) : AdInteractionState
    data class ReturnedTooEarly(val adUrl: String, val elapsedSeconds: Int, val requiredSeconds: Int) : AdInteractionState
    data object Completed : AdInteractionState
    data object Cancelled : AdInteractionState
}

/**
 * The central ad orchestrator.
 *
 * # Responsibilities
 *
 * 1. **Decide whether to show an ad** — checks [AdsPreferences] (enabled,
 *    quota, cooldown) + [AdTracker] (ads shown today, last ad timestamp).
 * 2. **Manage the ad interaction state machine** — [AdInteractionState].
 * 3. **Enforce the minimum stay** — when the user returns from the browser,
 *    checks if they stayed long enough. If not, shows a "please stay" message.
 * 4. **Record ad views** — only counts an ad if the user stayed for the
 *    minimum time.
 *
 * # Integration with AppController
 *
 * The [AppController] calls [evaluateAdGate] before every anime-detail navigation.
 * If an ad should be shown, AppController stores the deferred navigation lambda
 * + calls [startAdDialog]. The UI renders the ad dialog based on [state].
 * When the ad interaction completes (or is cancelled), AppController executes
 * the deferred navigation.
 *
 * # Cooldown Logic
 *
 * After a completed ad view, no new ad is shown for
 * [AdsPreferences.getCooldownMinutes] minutes. This is checked in
 * [shouldShowAd] via `lastAdTimestamp + cooldownMs > now`.
 *
 * # Daily Quota Logic
 *
 * The user can see at most [AdsPreferences.getDailyQuota] ads per day. The
 * count resets at midnight (device local time) via [AdTracker.resetDailyIfNeeded].
 *
 * @param preferences the ad configuration.
 * @param tracker the on-device ad view tracker.
 */
class AdManager(
    private val preferences: AdsPreferences,
    private val tracker: AdTracker,
) {

    private val _state = MutableStateFlow<AdInteractionState>(AdInteractionState.Idle)
    val state: StateFlow<AdInteractionState> = _state.asStateFlow()

    /**
     * Evaluates whether an ad should be shown right now.
     *
     * Checks (in order):
     * 1. Is ads enabled? → No → return false.
     * 2. Is there an ad already in progress? → Yes → return false (don't stack).
     * 3. Has the daily quota been reached? → Yes → return false.
     * 4. Is the user in cooldown? → Yes → return false.
     *
     * @return true if an ad should be shown before proceeding with the action.
     */
    fun shouldShowAd(): Boolean {
        if (!preferences.isAdsEnabled()) {
            return false
        }
        if (_state.value !is AdInteractionState.Idle) {
            // An ad interaction is already in progress — don't stack.
            return false
        }
        tracker.resetDailyIfNeeded()
        val shownToday = tracker.getAdsShownToday()
        val quota = preferences.getDailyQuota()
        if (shownToday >= quota) {
            Log.d(TAG, "shouldShowAd: daily quota reached ($shownToday/$quota)")
            return false
        }
        val lastAd = tracker.getLastAdTimestamp()
        if (lastAd > 0) {
            val cooldownMs = preferences.getCooldownMinutes() * 60_000L
            val elapsed = System.currentTimeMillis() - lastAd
            if (elapsed < cooldownMs) {
                val remainingMin = (cooldownMs - elapsed) / 60_000
                Log.d(TAG, "shouldShowAd: in cooldown (${remainingMin}min remaining)")
                return false
            }
        }
        return true
    }

    /**
     * Transitions to [AdInteractionState.DialogShowing].
     *
     * Called by AppController when [shouldShowAd] returns true and the
     * deferred navigation has been stored. The UI should render the ad dialog.
     */
    fun startAdDialog() {
        val remaining = preferences.getDailyQuota() - tracker.getAdsShownToday()
        _state.value = AdInteractionState.DialogShowing(
            adUrl = preferences.getAdUrl(),
            remainingQuota = remaining.coerceAtLeast(0),
        )
        Log.i(TAG, "startAdDialog: showing ad dialog (url=${preferences.getAdUrl()}, remaining=$remaining)")
    }

    /**
     * User clicked OK on the ad dialog.
     *
     * Transitions to [AdInteractionState.AdInProgress] + records the timestamp.
     * The caller (AppController) should open the browser to [adUrl] at this point.
     */
    fun acceptAd() {
        val current = _state.value
        if (current !is AdInteractionState.DialogShowing) return
        _state.value = AdInteractionState.AdInProgress(
            adUrl = current.adUrl,
            openedAt = System.currentTimeMillis(),
        )
        Log.i(TAG, "acceptAd: user accepted — browser should open to ${current.adUrl}")
    }

    /**
     * User clicked Cancel on the ad dialog.
     *
     * Transitions to [AdInteractionState.Cancelled], then back to [Idle].
     * The ad is NOT counted. The caller should proceed with the deferred navigation.
     *
     * @return true to signal the caller to proceed with the deferred navigation.
     */
    fun cancelAd(): Boolean {
        _state.value = AdInteractionState.Cancelled
        Log.i(TAG, "cancelAd: user cancelled — proceeding without ad")
        _state.value = AdInteractionState.Idle
        return true
    }

    /**
     * Called when the user returns to the app after accepting the ad.
     *
     * Checks if the user stayed for at least [AdsPreferences.getMinStaySeconds].
     * - If yes → records the ad view + transitions to [Completed] → [Idle].
     *   Returns `true` (ad counted, proceed with navigation).
     * - If no → transitions to [ReturnedTooEarly]. Returns `false` (ad NOT
     *   counted, do NOT proceed — the UI will show a "please stay" message).
     *
     * @return true if the ad was counted, false if the user returned too early.
     */
    fun onAdReturn(): Boolean {
        val current = _state.value
        if (current !is AdInteractionState.AdInProgress) return true // No ad in progress — proceed

        val elapsedMs = System.currentTimeMillis() - current.openedAt
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        val requiredSeconds = preferences.getMinStaySeconds()

        if (elapsedSeconds >= requiredSeconds) {
            // Ad counted — record + proceed.
            tracker.recordAdView()
            _state.value = AdInteractionState.Completed
            Log.i(TAG, "onAdReturn: ad counted (stayed ${elapsedSeconds}s >= ${requiredSeconds}s)")
            _state.value = AdInteractionState.Idle
            return true
        } else {
            // Returned too early — don't count.
            _state.value = AdInteractionState.ReturnedTooEarly(
                adUrl = current.adUrl,
                elapsedSeconds = elapsedSeconds,
                requiredSeconds = requiredSeconds,
            )
            Log.i(TAG, "onAdReturn: returned too early (${elapsedSeconds}s < ${requiredSeconds}s)")
            return false
        }
    }

    /**
     * Dismisses the "returned too early" message + goes back to the dialog
     * so the user can try again.
     */
    fun dismissTooEarly() {
        if (_state.value is AdInteractionState.ReturnedTooEarly) {
            startAdDialog() // Back to dialog showing
        }
    }

    /**
     * Dismisses the "returned too early" message + cancels the ad entirely.
     * The caller should proceed with the deferred navigation (ad NOT counted).
     */
    fun cancelFromTooEarly(): Boolean {
        if (_state.value is AdInteractionState.ReturnedTooEarly) {
            _state.value = AdInteractionState.Idle
            Log.i(TAG, "cancelFromTooEarly: user gave up — proceeding without ad")
            return true
        }
        return false
    }

    /**
     * Forces the ad system back to idle (for debugging / reset).
     */
    fun forceReset() {
        _state.value = AdInteractionState.Idle
    }

    /**
     * Convenience: returns the remaining cooldown in milliseconds, or 0 if
     * not in cooldown.
     */
    fun getRemainingCooldownMs(): Long {
        val lastAd = tracker.getLastAdTimestamp()
        if (lastAd == 0L) return 0L
        val cooldownMs = preferences.getCooldownMinutes() * 60_000L
        val elapsed = System.currentTimeMillis() - lastAd
        return (cooldownMs - elapsed).coerceAtLeast(0L)
    }

    private companion object {
        private const val TAG = "AnikutaAdManager"
    }
}
