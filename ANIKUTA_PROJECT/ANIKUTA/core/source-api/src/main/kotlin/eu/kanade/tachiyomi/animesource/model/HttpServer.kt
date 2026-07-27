package eu.kanade.tachiyomi.animesource.model

import fi.iki.elonen.NanoHTTPD

/**
 * HttpServer — a local NanoHTTPD server used by some anime sources to
 * proxy/rewrite URLs for MPV playback.
 *
 * Replaces logcat/tachiyomi.core.common imports with android.util.Log.
 */
open class HttpServer : NanoHTTPD(0) {
    val url: String
        get() = "http://localhost:$listeningPort"

    fun isRunning(): Boolean {
        return isRunning
    }

    @Volatile
    private var isRunning = false

    override fun start() {
        try {
            super.start()
            isRunning = true
        } catch (e: Exception) {
            android.util.Log.d("HttpServer", "Failed to start http server", e)
        }
    }

    override fun stop() {
        super.stop()
        isRunning = false
    }
}
