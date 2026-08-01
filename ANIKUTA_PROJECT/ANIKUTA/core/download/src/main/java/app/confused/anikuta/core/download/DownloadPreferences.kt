package app.confused.anikuta.core.download

import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.core.preferences.getEnum
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Download settings, persisted via [PreferenceStore] (reactive — observe via
 * `Preference.changes()`). Mirrors the `WatchProgressStore` pattern.
 *
 * **Settings groups:**
 *
 * 1. **General** — folder, method, Wi-Fi-only, concurrency, show-download-button.
 * 2. **Auto-download behavior** — [autoDownload] toggle: when ON, the app
 *    automatically picks the best server/audio/quality based on the user's
 *    preference lists ([qualityPreferences], [audioPreferences],
 *    [serverPreferences]). When OFF, tapping download shows the video picker
 *    sheet so the user chooses manually.
 * 3. **Preference lists** (priority-ordered, reorderable in the settings UI):
 *    - [qualityPreferences] — e.g. ["1080p", "720p", "480p", "360p"]. Top = highest priority.
 *    - [audioPreferences] — e.g. ["SUB", "DUB"]. Top = preferred.
 *    - [serverPreferences] — per-source-id ordered server names.
 * 4. **Fallback strategies** — what to do when the preferred quality/audio
 *    isn't available: [qualityFallback] / [audioFallback].
 *
 * The folder URI is the ONLY setting the user MUST set before downloading.
 */
class DownloadPreferences(
    private val store: PreferenceStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ── General ──

    /** The SAF tree URI (`content://...`) of the user-selected ANIKUTA root folder. */
    fun downloadFolderUri(): Preference<String> =
        store.getString(KEY_FOLDER_URI, "")

    /** True once the user has picked a download folder. */
    val hasDownloadFolder: Boolean
        get() = downloadFolderUri().get().isNotBlank()

    /** The download method (NORMAL = single-threaded; ADVANCED = multi-threaded + resume). Default: ADVANCED. */
    fun method(): Preference<DownloadMethod> =
        store.getEnum(KEY_METHOD, DownloadMethod.ADVANCED)

    /** Only download on Wi-Fi (default true). */
    fun wifiOnly(): Preference<Boolean> =
        store.getBoolean(KEY_WIFI_ONLY, true)

    /** Max parallel downloads (default 1; 1..5 clamped at the UI layer). */
    fun concurrentDownloads(): Preference<Int> =
        store.getInt(KEY_CONCURRENT, 1)

    /** Show the download button on episode rows (default true). */
    fun showDownloadButton(): Preference<Boolean> =
        store.getBoolean(KEY_SHOW_BUTTON, true)

    // ── Auto-download behavior ──

    /**
     * When ON, tapping download auto-picks the best video based on the
     * preference lists (no picker sheet). When OFF, shows the picker sheet.
     * Default: OFF (user picks the video manually).
     */
    fun autoDownload(): Preference<Boolean> =
        store.getBoolean(KEY_AUTO_PICK, false)

    // ── Preference lists (priority-ordered) ──

    /**
     * Ordered quality preferences (e.g. ["1080p", "720p", "480p", "360p"]).
     * Top = highest priority. When auto-download is ON, the app picks the
     * first available quality in this order. If none match, falls back to
     * [qualityFallback] strategy.
     */
    fun qualityPreferences(): Preference<List<String>> =
        store.getObject(
            KEY_QUALITY_PREFS,
            DEFAULT_QUALITY_PREFS,
            { json.encodeToString(ListSerializer(String.serializer()), it) },
            { str ->
                try { json.decodeFromString(ListSerializer(String.serializer()), str) }
                catch (e: Exception) { DEFAULT_QUALITY_PREFS }
            },
        )

    /**
     * Ordered audio-version preferences (e.g. ["SUB", "DUB"]).
     * Top = preferred. Same logic as [qualityPreferences].
     */
    fun audioPreferences(): Preference<List<String>> =
        store.getObject(
            KEY_AUDIO_PREFS,
            DEFAULT_AUDIO_PREFS,
            { json.encodeToString(ListSerializer(String.serializer()), it) },
            { str ->
                try { json.decodeFromString(ListSerializer(String.serializer()), str) }
                catch (e: Exception) { DEFAULT_AUDIO_PREFS }
            },
        )

    /**
     * Per-source server preferences. Keyed by source ID (as string) → ordered
     * list of server names. The app auto-detects available servers per source
     * at resolve time; this stores the user's priority order. Servers not in
     * the list are appended at the end (alphabetically).
     */
    fun serverPreferences(): Preference<Map<String, List<String>>> =
        store.getObject(
            KEY_SERVER_PREFS,
            emptyMap(),
            { json.encodeToString(kotlinx.serialization.builtins.MapSerializer(
                String.serializer(), ListSerializer(String.serializer())), it) },
            { str ->
                try { json.decodeFromString(kotlinx.serialization.builtins.MapSerializer(
                    String.serializer(), ListSerializer(String.serializer())), str) }
                catch (e: Exception) { emptyMap() }
            },
        )

    // ── Fallback strategies ──

    /** What to do when the preferred quality isn't available. */
    fun qualityFallback(): Preference<FallbackStrategy> =
        store.getEnum(KEY_QUALITY_FALLBACK, FallbackStrategy.TRY_NEXT)

    /** What to do when the preferred audio version isn't available. */
    fun audioFallback(): Preference<FallbackStrategy> =
        store.getEnum(KEY_AUDIO_FALLBACK, FallbackStrategy.TRY_NEXT)

    /** What to do when the preferred server isn't available. */
    fun serverFallback(): Preference<FallbackStrategy> =
        store.getEnum(KEY_SERVER_FALLBACK, FallbackStrategy.TRY_NEXT)

    // ── Advanced download method settings ──

    /** Number of parallel threads for the Advanced method (default 4; 1..8). */
    fun advancedThreadCount(): Preference<Int> =
        store.getInt(KEY_ADV_THREADS, 8)  // 8 threads (maximum) by default

    /** Max retries per chunk on failure for the Advanced method (default 3; 0..10). */
    fun advancedMaxRetries(): Preference<Int> =
        store.getInt(KEY_ADV_RETRIES, 25)  // 25 retries by default

    /**
     * Min file size (in MB) to use multi-threading. Files smaller than this
     * use a single thread (overhead of parallel chunks isn't worth it for
     * small files). Default: 5 MB.
     */
    fun advancedMinSizeMb(): Preference<Int> =
        store.getInt(KEY_ADV_MIN_SIZE_MB, 1)  // 1 MB — multi-threading for all files

    companion object {
        private const val KEY_FOLDER_URI = "pref_dl_folder_uri"
        private const val KEY_METHOD = "pref_dl_method"
        private const val KEY_WIFI_ONLY = "pref_dl_wifi_only"
        private const val KEY_CONCURRENT = "pref_dl_concurrent"
        private const val KEY_SHOW_BUTTON = "pref_dl_show_button"
        private const val KEY_AUTO_PICK = "pref_dl_auto_pick"
        private const val KEY_QUALITY_PREFS = "pref_dl_quality_prefs"
        private const val KEY_AUDIO_PREFS = "pref_dl_audio_prefs"
        private const val KEY_SERVER_PREFS = "pref_dl_server_prefs"
        private const val KEY_QUALITY_FALLBACK = "pref_dl_quality_fallback"
        private const val KEY_AUDIO_FALLBACK = "pref_dl_audio_fallback"
        private const val KEY_SERVER_FALLBACK = "pref_dl_server_fallback"
        private const val KEY_ADV_THREADS = "pref_dl_adv_threads"
        private const val KEY_ADV_RETRIES = "pref_dl_adv_retries"
        private const val KEY_ADV_MIN_SIZE_MB = "pref_dl_adv_min_size_mb"

        /** Default quality priority (highest first). */
        val DEFAULT_QUALITY_PREFS = listOf("1080p", "720p", "480p", "360p")
        /** Default audio-version priority (SUB preferred for anime). */
        val DEFAULT_AUDIO_PREFS = listOf("SUB", "DUB")
    }
}

/** The download method (per the owner's spec — Normal vs Advanced). */
enum class DownloadMethod {
    /** Normal: single-threaded OkHttp download. No resume. Works for HLS + direct video. */
    NORMAL,
    /** Advanced: multi-threaded Range-request download with resume + auto-retry. Direct video only. */
    ADVANCED,
}

/**
 * Fallback strategy when the user's preferred option (quality/audio/server)
 * isn't available.
 *
 *  - [TRY_NEXT] — automatically try the next option in the preference list.
 *    If nothing matches, pick the first available (best-effort). Default.
 *  - [ASK] — surface the picker sheet so the user decides (auto-download is
 *    effectively bypassed for this download).
 *  - [DO_NOT_DOWNLOAD] — don't download; show an error.
 */
enum class FallbackStrategy {
    TRY_NEXT,
    ASK,
    DO_NOT_DOWNLOAD,
}
