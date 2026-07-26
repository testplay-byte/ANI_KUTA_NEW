package app.anikuta.download

import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Phase 7 — Download preferences with drag-and-drop priority lists.
 *
 * Replaces the old single-value prefs with ordered-list prefs:
 *  - [preferredQualityOrder] — e.g. ["360p","720p","1080p"] (top = highest priority)
 *  - [preferredAudioOrder] — e.g. ["dub","sub"] (top = highest priority)
 *  - [preferredServerOrder] — e.g. ["VidPlay-1","HD-1"] (top = highest priority)
 *  - [qualityVsAudioPriority] — QUALITY_FIRST or AUDIO_FIRST
 *  - [audioFallbackMode] — FAIL (don't download) or NEXT (fall back to next audio)
 *
 * These prefs OVERRIDE the extension's own settings for downloads.
 * The [DownloadVideoResolver] applies them to the raw video list.
 */
class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Ordered priority list of qualities (highest priority first).
     * Stored as a JSON-serialized list of strings.
     * Default: ["1080p","720p","360p"] (best quality first).
     */
    fun preferredQualityOrder(): Preference<List<String>> {
        return PreferenceListDelegate(
            preferenceStore.getString("download_quality_order", "[\"1080p\",\"720p\",\"360p\"]"),
            json,
        )
    }

    /**
     * Ordered priority list of audio versions (highest priority first).
     * Default: ["sub","dub"] (sub first, dub fallback).
     */
    fun preferredAudioOrder(): Preference<List<String>> {
        return PreferenceListDelegate(
            preferenceStore.getString("download_audio_order", "[\"sub\",\"dub\"]"),
            json,
        )
    }

    /**
     * Ordered priority list of servers (highest priority first).
     * Empty by default — populated from the trusted sources' server names.
     */
    fun preferredServerOrder(): Preference<List<String>> {
        return PreferenceListDelegate(
            preferenceStore.getString("download_server_order", "[]"),
            json,
        )
    }

    /** Which dimension wins when both quality and audio can't be satisfied. */
    fun qualityVsAudioPriority(): Preference<String> =
        preferenceStore.getString("download_priority_mode", PriorityMode.QUALITY_FIRST.value)

    /** What to do when the preferred audio version is unavailable. */
    fun audioFallbackMode(): Preference<String> =
        preferenceStore.getString("download_audio_fallback", AudioFallback.NEXT.value)

    fun maxConcurrentDownloads(): Preference<Int> =
        preferenceStore.getInt("download_max_concurrent", 1)

    /**
     * Download method: "single_pass", "hls_direct", or "segment".
     * - single_pass: aniyomi's approach (one FFmpeg call, correct size/duration, no resume)
     * - hls_direct: HTTP segment download (resume, precise progress, may not work with all proxies)
     * - segment: FFmpeg -ss segments (resume, precise progress, wrong size for short videos)
     */
    fun downloadMethod(): Preference<String> =
        preferenceStore.getString("download_method", "single_pass")

    fun deleteAfterWatching(): Preference<Boolean> =
        preferenceStore.getBoolean("download_delete_after_watch", false)

    fun downloadOverWifiOnly(): Preference<Boolean> =
        preferenceStore.getBoolean("download_wifi_only", false)

    /** Show download size info (downloaded/total bytes) on download cards. Default: true. */
    fun showDownloadSize(): Preference<Boolean> =
        preferenceStore.getBoolean("pref_show_download_size", true)

    /**
     * Migrate old single-value prefs (Phase 6) to single-element ordered lists.
     * Called on first run of Phase 7. Idempotent — safe to call multiple times.
     */
    fun migrateFromPhase6() {
        val oldQuality = preferenceStore.getString("download_quality", "").get()
        if (oldQuality.isNotBlank() && preferredQualityOrder().get().isEmpty()) {
            preferredQualityOrder().set(listOf(oldQuality))
        }
        val oldAudio = preferenceStore.getString("download_audio_version", "").get()
        if (oldAudio.isNotBlank() && preferredAudioOrder().get().isEmpty()) {
            preferredAudioOrder().set(listOf(oldAudio))
        }
        val oldServer = preferenceStore.getString("download_preferred_server", "").get()
        if (oldServer.isNotBlank() && preferredServerOrder().get().isEmpty()) {
            preferredServerOrder().set(listOf(oldServer))
        }
    }
}

enum class PriorityMode(val label: String, val value: String) {
    QUALITY_FIRST("Quality first", "quality_first"),
    AUDIO_FIRST("Audio first", "audio_first");

    companion object {
        fun fromValue(v: String): PriorityMode = entries.find { it.value == v } ?: QUALITY_FIRST
    }
}

enum class AudioFallback(val label: String, val value: String) {
    FAIL("Show error (don't download)", "fail"),
    NEXT("Download next available audio", "next");

    companion object {
        fun fromValue(v: String): AudioFallback = entries.find { it.value == v } ?: NEXT
    }
}

/**
 * Delegate that wraps a String Preference (holding JSON) as a Preference<List<String>>.
 * Uses [kotlinx.serialization] to serialize/deserialize the list.
 */
private class PreferenceListDelegate(
    private val stringPref: Preference<String>,
    private val json: Json,
) : Preference<List<String>> {

    private val stringSerializer = String.serializer()

    override fun key(): String = stringPref.key()

    override fun get(): List<String> {
        return try {
            json.decodeFromString(ListSerializer(stringSerializer), stringPref.get())
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun set(value: List<String>) {
        stringPref.set(json.encodeToString(ListSerializer(stringSerializer), value))
    }

    override fun isSet(): Boolean = stringPref.isSet()

    override fun delete() = stringPref.delete()

    override fun defaultValue(): List<String> = emptyList()

    override fun changes(): Flow<List<String>> =
        stringPref.changes().map { get() }

    override fun stateIn(scope: CoroutineScope) =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}
