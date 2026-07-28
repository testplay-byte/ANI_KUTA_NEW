package app.confused.anikuta.core.preferences

import app.confused.anikuta.core.common.model.MetadataProviderId
import app.confused.anikuta.core.providerapi.MetadataCapability
import app.confused.anikuta.core.providerapi.ProviderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SharedPreferences-backed implementation of [ProviderPreferences].
 *
 * Stores the active provider per capability + the fallback order.
 *
 * # Storage format
 *
 * - Active provider: `pref_provider_active_<capability>` = provider key (e.g., `"al"`).
 *   Empty/missing = use the default (AniList).
 * - Fallback order: `pref_provider_fallback_<capability>` = comma-separated provider keys
 *   (e.g., `"al,mal,tmdb"`). Robust parsing: unknown keys dropped, missing appended.
 *
 * Robustness rules (same as [ContentIdPreferences]):
 * - Unknown keys (future providers this app version doesn't know) are dropped on read.
 * - Missing providers are appended in [MetadataProviderId] declaration order.
 * - Empty/blank input → empty fallback order (the registry uses declaration order).
 */
class AndroidProviderPreferences(
    private val preferenceStore: PreferenceStore,
) : ProviderPreferences {

    override fun activeProviderFor(capability: MetadataCapability): MetadataProviderId? {
        val key = preferenceStore.getString(activeKey(capability), "").get()
        if (key.isBlank()) return null
        return MetadataProviderId.fromKey(key)
    }

    override fun setActiveProvider(capability: MetadataCapability, provider: MetadataProviderId) {
        preferenceStore.getString(activeKey(capability), "").set(provider.key)
    }

    override fun fallbackOrder(capability: MetadataCapability): List<MetadataProviderId> {
        val raw = preferenceStore.getString(fallbackKey(capability), "").get()
        if (raw.isBlank()) return emptyList()

        val parsed = raw.split(SEPARATOR)
            .mapNotNull { token ->
                val key = token.trim()
                if (key.isBlank()) null
                else MetadataProviderId.fromKey(key)
            }

        // De-duplicate while preserving order.
        val seen = mutableSetOf<MetadataProviderId>()
        return parsed.filter { seen.add(it) }
    }

    override fun setFallbackOrder(capability: MetadataCapability, order: List<MetadataProviderId>) {
        val serialized = order.joinToString(separator = SEPARATOR) { it.key }
        preferenceStore.getString(fallbackKey(capability), "").set(serialized)
    }

    override fun observeActiveProvider(capability: MetadataCapability): Flow<MetadataProviderId?> {
        return preferenceStore.getString(activeKey(capability), "").changes()
            .map { key -> if (key.isBlank()) null else MetadataProviderId.fromKey(key) }
    }

    private fun activeKey(capability: MetadataCapability): String =
        "${KEY_ACTIVE_PREFIX}${capability.name}"

    private fun fallbackKey(capability: MetadataCapability): String =
        "${KEY_FALLBACK_PREFIX}${capability.name}"

    companion object {
        private const val KEY_ACTIVE_PREFIX = "pref_provider_active_"
        private const val KEY_FALLBACK_PREFIX = "pref_provider_fallback_"
        private const val SEPARATOR = ","
    }
}
