package app.confused.anikuta.core.player

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib
import java.io.File

/**
 * Centralized MPV initialization — ported from OLD PlayerActivity.initMpvView()
 * + copyAssets(). Extracted as a standalone object so it can be called from
 * the Compose `AndroidView.factory` without needing a PlayerActivity.
 *
 * Sequence (must match aniyomi):
 *  1. Ensure `mpvDir` exists.
 *  2. Write clean `mpv.conf` + `input.conf`.
 *  3. `copyAssets()` → `subfont.ttf` to mpvDir ROOT (NOT fonts/ — this was
 *     a subtitle rendering bug that took 15 builds to fix).
 *  4. `sub-ass-force-margins=yes` + `sub-use-margins=yes` (init API).
 *  5. `view.initialize(configDir, cacheDir, logLvl)`.
 *  6. `addLogObserver` + `addObserver`.
 *  7. HTTP headers for extension proxy URLs (runtime, before loadfile).
 *  8. `sub-fonts-dir` + `osd-fonts-dir` via `setPropertyString` (RUNTIME).
 */
object PlayerInitializer {

    private const val TAG = "AnikutaPlayer"
    const val MPV_DIR = "mpv"

    /**
     * Copy `subfont.ttf` from assets to the MPV config-dir ROOT.
     *
     * CRITICAL: `subfont.ttf` MUST be at the config root, NOT in `fonts/`.
     * The native `BaseMPVView.initialize()` calls
     * `ass_setFonts(tracker, "<configDir>/subfont.ttf", "sans-serif", …)`.
     * If the file is missing, libass logs "Error opening memory font" and
     * NO subtitle text can render (video/audio still work).
     *
     * `cacert.pem` is also copied if present (for TLS verification of HTTPS
     * subtitle downloads). If not present, MPV falls back to system CAs.
     */
    fun copyAssets(context: Context, mpvDir: File) {
        val assetManager = context.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        for (filename in files) {
            try {
                val ins = assetManager.open(filename, android.content.res.AssetManager.ACCESS_STREAMING)
                val outFile = File(mpvDir, filename)
                if (!outFile.exists() || outFile.length() != ins.available().toLong()) {
                    java.io.FileOutputStream(outFile).use { out -> ins.copyTo(out) }
                    Log.d(TAG, "Copied asset: $filename (${outFile.length()} bytes) -> mpv/")
                }
                ins.close()
            } catch (e: java.io.IOException) {
                Log.w(TAG, "Asset not found (non-fatal): $filename")
            }
        }
    }

    /**
     * Initialize MPV on the given [view]. Must be called EXACTLY ONCE per
     * process — calling `view.initialize()` twice is a native SIGABRT.
     *
     * @param view the AnikutaMPVView (inflated from R.layout.mpv_view)
     * @param context the Context (for filesDir, cacheDir, assets)
     * @param observer the PlayerObserver (receives MPV events)
     * @param videoHeaders HTTP headers for extension proxy URLs (comma-separated
     *   "Key: Value" pairs, or empty for default User-Agent)
     * @param logLevel MPV log level ("warn" default, "info" for debug)
     */
    fun initMpvView(
        view: AnikutaMPVView,
        context: Context,
        observer: PlayerObserver,
        videoHeaders: String,
        logLevel: String = "warn",
    ) {
        val mpvDir = context.filesDir.resolve(MPV_DIR).apply { mkdirs() }

        // 2. Write clean config files
        try {
            File(mpvDir, "mpv.conf").writeText(MpvConfigManager.readMpvConf(context))
            File(mpvDir, "input.conf").writeText(MpvConfigManager.readInputConf(context))
        } catch (e: Exception) {
            Log.w(TAG, "Could not write mpv.conf/input.conf", e)
        }

        // 3. Copy assets to mpvDir ROOT (subfont.ttf MUST be at config root)
        copyAssets(context, mpvDir)

        // 4. Subtitle margin options — set BEFORE initialize
        MPVLib.setOptionString("sub-ass-force-margins", "yes")
        MPVLib.setOptionString("sub-use-margins", "yes")

        // 5. Initialize MPV
        view.initialize(mpvDir.absolutePath, context.cacheDir.absolutePath, logLevel)
        Log.d(TAG, "MPV initialized (configDir=${mpvDir.absolutePath})")

        // 6. Register observers
        MPVLib.addLogObserver(observer)
        MPVLib.addObserver(observer)

        // 7. HTTP headers for extension proxy URLs
        val headers = if (videoHeaders.isNotBlank()) videoHeaders
            else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
        MPVLib.setOptionString("http-header-fields", headers)

        // 8. Font directory for USER fonts — runtime setPropertyString
        val fontsDir = File(mpvDir, "fonts").apply { mkdirs() }
        MPVLib.setPropertyString("sub-fonts-dir", fontsDir.absolutePath)
        MPVLib.setPropertyString("osd-fonts-dir", fontsDir.absolutePath)

        // Diagnostic
        val subfont = File(mpvDir, "subfont.ttf")
        Log.d(TAG, "SUBTITLE_FONTCHECK: subfont.ttf at config root = ${subfont.exists()} (${subfont.length()} bytes)")
    }

    /**
     * Load a video URL into MPV. For offline URLs (fd://, content://), delays
     * 500ms so SurfaceView's surfaceCreated fires first (prevents
     * `assertion WinID != 0` crash).
     */
    fun loadVideo(view: AnikutaMPVView, url: String, context: Context) {
        val resolvedUrl = resolveUrlForMpv(url, context)
        Log.i(TAG, "Loading video: $resolvedUrl")

        if (resolvedUrl.startsWith("fd://") || resolvedUrl.startsWith("content://")) {
            view.postDelayed({
                try {
                    MPVLib.command(arrayOf("loadfile", resolvedUrl, "replace"))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load offline video", e)
                }
            }, 500)
        } else {
            try {
                MPVLib.command(arrayOf("loadfile", resolvedUrl, "replace"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video", e)
            }
        }
    }

    /**
     * Destroy MPV safely. Each cleanup step wrapped in its own try/catch so
     * one failure doesn't skip the rest.
     */
    fun destroyMpv(view: AnikutaMPVView, observer: PlayerObserver) {
        try { MPVLib.removeLogObserver(observer) } catch (e: Exception) { Log.w(TAG, "removeLogObserver failed", e) }
        try { MPVLib.removeObserver(observer) } catch (e: Exception) { Log.w(TAG, "removeObserver failed", e) }
        try { MPVLib.command(arrayOf("stop")) } catch (e: Exception) { Log.w(TAG, "stop command failed", e) }
        try {
            // destroy() is not on the public API surface — use reflection
            val destroyMethod = view.javaClass.getMethod("destroy")
            destroyMethod.invoke(view)
        } catch (e: Exception) {
            try {
                val destroyMethod = MPVLib::class.java.getMethod("destroy")
                destroyMethod.invoke(null)
            } catch (e2: Exception) {
                Log.w(TAG, "MPV destroy failed", e2)
            }
        }
        Log.d(TAG, "MPV destroyed")
    }
}
