package app.anikuta.player

import android.util.Log
import `is`.xyz.mpv.MPVLib

/**
 * ANI-KUTA PlayerObserver — bridges MPV lib events into the app.
 *
 * Selective copy-paste from aniyomi's `PlayerObserver` (D1): same observer
 * interfaces (`MPVLib.EventObserver`, `MPVLib.LogObserver`), but instead of a
 * hard reference to `PlayerActivity`, it dispatches to a [Callback] interface.
 * This keeps the player logic testable and lets the Activity register a lambda.
 *
 * Source: REFERENCE/app/.../ui/player/PlayerObserver.kt (decoupled).
 */
class PlayerObserver(
    private val callback: Callback,
) : MPVLib.EventObserver, MPVLib.LogObserver {

    interface Callback {
        fun onEvent(eventId: Int)
        fun onEventProperty(property: String)
        fun onEventProperty(property: String, value: Long)
        fun onEventProperty(property: String, value: Boolean)
        fun onEventProperty(property: String, value: String)
        fun onEventProperty(property: String, value: Double)
        fun onFileEnded(errorMessage: String?)
    }

    private var httpError: String? = null

    override fun eventProperty(property: String) {
        callback.onEventProperty(property)
    }

    override fun eventProperty(property: String, value: Long) {
        callback.onEventProperty(property, value)
    }

    override fun eventProperty(property: String, value: Boolean) {
        callback.onEventProperty(property, value)
    }

    override fun eventProperty(property: String, value: String) {
        callback.onEventProperty(property, value)
    }

    override fun eventProperty(property: String, value: Double) {
        callback.onEventProperty(property, value)
    }

    override fun event(eventId: Int) {
        callback.onEvent(eventId)
    }

    override fun efEvent(err: String?) {
        var message = err ?: "Error: File ended"
        httpError?.let {
            message = "$message: $it"
            httpError = null
        }
        Log.e(TAG, message)
        callback.onFileEnded(message)
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        when (level) {
            MPVLib.mpvLogLevel.MPV_LOG_LEVEL_FATAL,
            MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> Log.e("mpv/$prefix", text)
            MPVLib.mpvLogLevel.MPV_LOG_LEVEL_WARN -> Log.w("mpv/$prefix", text)
            MPVLib.mpvLogLevel.MPV_LOG_LEVEL_INFO -> Log.i("mpv/$prefix", text)
            else -> Log.v("mpv/$prefix", text)
        }
        if (text.contains("HTTP error")) httpError = text
    }

    companion object {
        private const val TAG = "PlayerObserver"
    }
}
