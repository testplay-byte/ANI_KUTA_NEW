package eu.kanade.tachiyomi.animesource.model





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
