package app.confused.anikuta.core.preferences

import app.confused.anikuta.core.common.model.MetadataProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User preferences for the extension → metadata-provider auto-linking behavior.
 *
 * # Auto-link (Phase 6+ feature)
 *
 * When the user opens an anime from the extension search tab, the app can
 * automatically search AniList (or another metadata provider) by title and
 * link the extension anime to the matching provider entry. This enables:
 * - AniList metadata enrichment (cover color, score, total episodes, next airing)
 * - Tracker sync (progress + status sync to AniList/MAL)
 * - Cross-device backup/restore (AniList ID is the stable cross-device identity)
 *
 * When auto-link is **OFF**, extension anime stay as extension-only — they
 * show the extension's own details page (no AniList metadata, no tracker sync).
 * The user can still manually link later via the "A" re-link button.
 *
 * # Future extensions (documented for architecture planning)
 *
 * - [linkingProvider]: which metadata provider to search when auto-linking.
 *   Default: AniList. Future: the user can pick MAL, TMDB, etc. via the
 *   Settings → General → Auto-link → Search provider dropdown.
 * - Per-extension config: a Map<extensionPkgName, AutoLinkMode> where
 *   AutoLinkMode = { AUTO, MANUAL, NEVER }. This lets the user auto-link
 *   for some extensions but not others. Stored as a JSON map in prefs.
 * - Per-anime override: the existing "go without linking" choice in the
 *   ExtensionLinkingSheet already provides per-anime control.
 *
 * Per the owner's direction: "think about the future too like being able to
 * configure it properly for each extension and also being able to configure
 * which service to search and link this anime too like tmdb anilist mal."
 */
class LinkingPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /** Whether auto-linking is enabled globally. Default: true (backward compat). */
    private val autoLinkEnabledPref = preferenceStore.getBoolean(KEY_AUTO_LINK_ENABLED, true)

    /** Get the current auto-link setting. */
    fun isAutoLinkEnabled(): Boolean = autoLinkEnabledPref.get()

    /** Set the auto-link setting. */
    fun setAutoLinkEnabled(enabled: Boolean) {
        autoLinkEnabledPref.set(enabled)
    }

    /** Observe the auto-link setting reactively. */
    fun observeAutoLinkEnabled(): Flow<Boolean> = autoLinkEnabledPref.changes()

    /**
     * Which metadata provider to search when auto-linking.
     * Default: AniList. Future: the user can pick MAL, TMDB, etc.
     * Stored as the provider's key string (e.g., "al", "mal", "tmdb").
     */
    private val linkingProviderPref = preferenceStore.getString(
        KEY_LINKING_PROVIDER,
        MetadataProviderId.ANILIST.key,
    )

    /** Get the configured linking provider. */
    fun getLinkingProvider(): MetadataProviderId {
        val key = linkingProviderPref.get()
        return MetadataProviderId.fromKey(key) ?: MetadataProviderId.ANILIST
    }

    /** Set the linking provider. */
    fun setLinkingProvider(provider: MetadataProviderId) {
        linkingProviderPref.set(provider.key)
    }

    /** Observe the linking provider reactively. */
    fun observeLinkingProvider(): Flow<MetadataProviderId> =
        linkingProviderPref.changes().map { key ->
            MetadataProviderId.fromKey(key) ?: MetadataProviderId.ANILIST
        }

    private companion object {
        private const val KEY_AUTO_LINK_ENABLED = "pref_auto_link_enabled"
        private const val KEY_LINKING_PROVIDER = "pref_linking_provider"
    }
}
