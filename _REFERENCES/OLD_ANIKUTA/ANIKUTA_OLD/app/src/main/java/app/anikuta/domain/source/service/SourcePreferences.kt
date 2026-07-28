package app.anikuta.domain.source.service

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

/**
 * SourcePreferences — minimal implementation for extension system.
 */
class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun enabledLanguages(): Preference<Set<String>> =
        preferenceStore.getStringSet("source_languages", setOf("en"))

    fun showNsfwSource(): Preference<Boolean> =
        preferenceStore.getBoolean("show_nsfw_source", true)

    fun trustedExtensions(): Preference<Set<String>> =
        preferenceStore.getStringSet("trusted_extensions", emptySet())

    /**
     * User-selected "Sources" — the extensions the user has explicitly trusted.
     * These are the ONLY extensions used for search/resolve/play. Max
     * [TrustAnimeExtension.MAX_TRUSTED] (2) entries.
     *
     * Phase 7 — replaces the old primaryExtensionPkg/secondaryExtensionPkgs
     * fields with a simpler trust-based model.
     */
    fun trustedSources(): Preference<Set<String>> =
        preferenceStore.getStringSet("trusted_sources", emptySet())

    /**
     * Ordered list of trusted extension package names (highest priority first).
     * The AniyomiSourceBridge tries them in this order when matching an anime.
     * Empty = use insertion order (current behavior).
     *
     * Phase 7 — sources priority drag-and-drop. Stored as a JSON string.
     */
    fun sourcePriorityOrder(): Preference<String> =
        preferenceStore.getString("source_priority_order", "[]")

    fun extensionUpdatesCount(): Preference<Int> =
        preferenceStore.getInt("extension_updates_count", 0)

    fun animeExtensionUpdatesCount(): Preference<Int> =
        preferenceStore.getInt("anime_extension_updates_count", 0)

    /**
     * User-selected primary anime extension package (highest-priority source
     * for the AniyomiSourceBridge to try when matching an anime).
     *
     * Empty string = auto-pick best match (current default behavior — the
     * bridge scores all installed sources and picks the highest-fidelity
     * match). Setting a value here pins the bridge to that single extension
     * first; if it has no match the bridge falls back to [secondaryExtensionPkgs]
     * and finally to the auto-pick path.
     *
     * Phase 6 tasks 6.1-6.6 — wired into the bridge in a follow-up task.
     */
    fun primaryExtensionPkg(): Preference<String> =
        preferenceStore.getString("primary_extension_pkg", "")

    /**
     * Ordered fallback set of anime extension package names tried after the
     * primary (if set) fails to match. Empty by default (auto-pick path).
     */
    fun secondaryExtensionPkgs(): Preference<Set<String>> =
        preferenceStore.getStringSet("secondary_extension_pkgs", emptySet())
}
