package app.anikuta.player

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Phase 6.1 — MPV configuration file management.
 *
 * Creates and manages MPV config files in the app's files directory:
 *  - mpv/mpv.conf: main MPV configuration
 *  - mpv/input.conf: key bindings
 *  - mpv/fonts/: custom subtitle fonts directory
 *
 * Advanced users can edit these files for full MPV control.
 * The app reads these at MPV initialization time.
 */
object MpvConfigManager {

    private const val TAG = "MpvConfigManager"
    private const val MPV_DIR = "mpv"

    /**
     * Ensure the MPV config directory exists and contains default config files.
     * Called before MPV initialization.
     */
    fun ensureConfigFiles(context: Context) {
        val mpvDir = File(context.filesDir, MPV_DIR)
        if (!mpvDir.exists()) mpvDir.mkdirs()

        val fontsDir = File(mpvDir, "fonts")
        if (!fontsDir.exists()) fontsDir.mkdirs()

        // Create default mpv.conf if it doesn't exist
        val mpvConf = File(mpvDir, "mpv.conf")
        if (!mpvConf.exists()) {
            mpvConf.writeText(DEFAULT_MPV_CONF)
            Log.d(TAG, "Created default mpv.conf")
        }

        // Create default input.conf if it doesn't exist
        val inputConf = File(mpvDir, "input.conf")
        if (!inputConf.exists()) {
            inputConf.writeText(DEFAULT_INPUT_CONF)
            Log.d(TAG, "Created default input.conf")
        }
    }

    /**
     * Get the MPV config directory path.
     */
    fun getConfigDir(context: Context): String {
        return File(context.filesDir, MPV_DIR).absolutePath
    }

    /**
     * Read the current mpv.conf content.
     */
    fun readMpvConf(context: Context): String {
        val file = File(context.filesDir, "$MPV_DIR/mpv.conf")
        return if (file.exists()) file.readText() else DEFAULT_MPV_CONF
    }

    /**
     * Write new content to mpv.conf.
     */
    fun writeMpvConf(context: Context, content: String) {
        val file = File(context.filesDir, "$MPV_DIR/mpv.conf")
        file.writeText(content)
        Log.d(TAG, "mpv.conf updated")
    }

    /**
     * Read the current input.conf content.
     */
    fun readInputConf(context: Context): String {
        val file = File(context.filesDir, "$MPV_DIR/input.conf")
        return if (file.exists()) file.readText() else DEFAULT_INPUT_CONF
    }

    /**
     * Write new content to input.conf.
     */
    fun writeInputConf(context: Context, content: String) {
        val file = File(context.filesDir, "$MPV_DIR/input.conf")
        file.writeText(content)
        Log.d(TAG, "input.conf updated")
    }

    // Default mpv.conf — minimal, safe defaults for anime streaming.
    //
    // Player redo (PLAYER_REDO_PLAN.md §3.5): `force-window` is intentionally
    // NOT set here. The mpv-lib `BaseMPVView.initialize()` forces
    // `force-window=no` after init and toggles it in surface callbacks, so a
    // `force-window=yes` in this file would be overridden anyway — and on some
    // builds it caused "Both surface and native_window are NULL" races. Leaving
    // it out matches aniyomi (whose mpv.conf default is empty).
    //
    // `cache` / `cache-secs` are also NOT set here — buffering is controlled by
    // `demuxer-max-bytes` (set at runtime in AnikutaMPVView.initOptions).
    //
    // Most real config is applied programmatically via setOptionString /
    // setPropertyString; this file is a user-editable override surface only.
    private val DEFAULT_MPV_CONF = """
        # ANI-KUTA MPV Configuration
        # Edit this file for advanced MPV settings.
        # Changes apply on next player launch.
        #
        # NOTE: Most options are set programmatically at runtime. Only add
        # overrides here that you want to take precedence. Do NOT add
        # `force-window` (managed by the player library) or `cache-secs`
        # (use demuxer-max-bytes instead).

        # Audio
        alang=jpn,eng

        # Subtitles (defaults; also set programmatically)
        sub-font=Sans Serif
        sub-font-size=55
        sub-border-size=3
        sub-pos=100

        # Network
        tls-verify=yes
        ytdl=no
    """.trimIndent()

    // Default input.conf — basic key bindings
    private val DEFAULT_INPUT_CONF = """
        # ANI-KUTA MPV Input Configuration
        # Key bindings for the MPV player.

        # Playback
        SPACE cycle pause
        LEFT seek -10
        RIGHT seek 10
        UP seek 60
        DOWN seek -60

        # Volume
        WHEEL_UP add volume 5
        WHEEL_DOWN add volume -5

        # Subtitles
        j cycle sub
        J cycle sub down

        # Audio
        k cycle audio

        # Quit
        q quit
        ESC quit
    """.trimIndent()
}
// Phase 6 build trigger
