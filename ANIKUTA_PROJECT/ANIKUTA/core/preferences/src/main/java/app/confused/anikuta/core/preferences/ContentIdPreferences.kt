package app.confused.anikuta.core.preferences

import app.confused.anikuta.core.common.model.ContentIdPriority
import app.confused.anikuta.core.common.model.MetadataProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User preferences for [ContentIdPriority] — the order in which metadata
 * providers are tried when deriving a [app.confused.anikuta.core.common.model.ContentId].
 *
 * Per ADR-050 + the owner's direction (rev 2): the priority is **user-configurable**.
 * The default is [ContentIdPriority.DEFAULT] (AniList → MAL → TMDB → Kitsu).
 * The user can reorder it in Settings → Data & Storage → Content Identity.
 *
 * Stored as a comma-separated list of [MetadataProviderId.key] values in
 * SharedPreferences (e.g., `"al,mal,tmdb,kitsu"`). Unknown keys (from a
 * future provider that this app version doesn't know) are dropped on read;
 * missing providers are appended in declaration order so no provider is
 * silently lost.
 */
class ContentIdPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /** The reactive [ContentIdPriority] preference. */
    private val priorityPref: Preference<ContentIdPriority> = preferenceStore.getObject(
        key = KEY_PRIORITY,
        defaultValue = ContentIdPriority.DEFAULT,
        serializer = { priority ->
            priority.order.joinToString(separator = SEPARATOR) { it.key }
        },
        deserializer = { str ->
            parsePriority(str)
        },
    )

    /** Get the current [ContentIdPriority]. */
    fun getPriority(): ContentIdPriority = priorityPref.get()

    /** Set the [ContentIdPriority]. */
    fun setPriority(priority: ContentIdPriority) {
        priorityPref.set(priority)
    }

    /** Observe the [ContentIdPriority] reactively. */
    fun observePriority(): Flow<ContentIdPriority> = priorityPref.changes().map { it }

    /**
     * Parse a stored priority string into a [ContentIdPriority].
     *
     * Robustness rules:
     * - Unknown keys (future providers this app version doesn't know) are dropped.
     * - Missing providers are appended in [MetadataProviderId] declaration order,
     *   so the result always contains ALL known providers exactly once.
     * - Empty/blank input → [ContentIdPriority.DEFAULT].
     */
    private fun parsePriority(str: String): ContentIdPriority {
        if (str.isBlank()) return ContentIdPriority.DEFAULT

        val parsed = str.split(SEPARATOR)
            .mapNotNull { token ->
                val key = token.trim()
                if (key.isBlank()) null
                else MetadataProviderId.fromKey(key)
            }

        // De-duplicate while preserving order.
        val seen = mutableSetOf<MetadataProviderId>()
        val ordered = parsed.filter { seen.add(it) }.toMutableList()

        // Append any missing providers in declaration order.
        for (provider in MetadataProviderId.entries) {
            if (provider !in seen) ordered.add(provider)
        }

        return if (ordered.isEmpty()) ContentIdPriority.DEFAULT
        else ContentIdPriority(ordered)
    }

    companion object {
        private const val KEY_PRIORITY = "pref_content_id_priority"
        private const val SEPARATOR = ","
    }
}
