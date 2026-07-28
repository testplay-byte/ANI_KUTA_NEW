package app.confused.anikuta.core.common.model

/**
 * Tier 2 identity — a per-content identifier that groups all source bindings
 * of the same anime / series / movie, independent of which source produced them.
 *
 * **Format:**
 * - Provider-linked: `"<providerKey>:<remoteId>"` (e.g., `"al:154587"` for AniList,
 *   `"mal:67890"` for MAL, `"tmdb:12345"` for TMDB).
 * - Fallback (no provider link): `= localId.value` (the per-source identity, no grouping).
 *
 * **Purpose:** Cross-cutting stores (watch progress, downloads, history, episode
 * metadata, tracker sync) key off [ContentId] because it survives source switches.
 * When the user switches from Extension A to Extension B for the same anime, the
 * [LocalId] changes but the [ContentId] stays the same (it's the same AniList anime).
 *
 * **Derivation priority (USER-CONFIGURABLE per ADR-050):**
 * The [ContentIdGenerator] picks the best available provider link in priority order.
 * The default order is [MetadataProviderId] declaration order (AniList → MAL → TMDB → Kitsu),
 * but the user can reorder it via `ContentIdPreferences` (Settings → Data & Storage →
 * Content Identity). This lets a MAL-primary user prefer `"mal:..."` over `"al:..."`.
 *
 * **Fallback:** If no provider link exists, [ContentId] = [LocalId] (per-source).
 * This means unlinked anime are per-source (no grouping). The user can link them
 * later, which upgrades the [ContentId] (triggering a `ContentIdMigrator` re-key).
 *
 * **Link/unlink events:** When the provider link changes, the [ContentId] may change.
 * The `ContentIdMigrator` (future) re-keys all cross-cutting stores from the old
 * [ContentId] to the new one. This is a single-point-in-time operation.
 *
 * Per ADR-050. See `_ARCHITECTURE_PLAN/proposals/01a_refined_id_system.md`.
 *
 * @see ContentIdGenerator
 * @see LocalId
 */
@JvmInline
value class ContentId(val value: String) {

    /** The first segment — either a [MetadataProviderId.key] or an [ExtensionSystem.key] (fallback). */
    val prefix: String
        get() = value.substringBefore(':')

    /** Resolve [prefix] to a [MetadataProviderId], or null if it's a fallback (= localId). */
    val provider: MetadataProviderId?
        get() = MetadataProviderId.fromKey(prefix)

    /** True if this [ContentId] is provider-linked (has grouping). */
    val isProviderLinked: Boolean
        get() = provider != null

    /** True if this [ContentId] is the fallback (= localId, no grouping). */
    val isFallback: Boolean
        get() = provider == null

    override fun toString(): String = value

    companion object {
        /**
         * Wrap a raw string into a [ContentId] WITHOUT validation. For SQLDelight/JSON interop
         * where the value comes from a trusted source (the DB). Does NOT throw.
         *
         * For NEW content_ids, use [ContentIdGenerator.generate] (which validates + derives).
         */
        fun unsafe(value: String): ContentId = ContentId(value)
    }
}

/**
 * Generates [ContentId]s from the best available provider link, in user-configurable
 * priority order.
 *
 * The priority is supplied by [ContentIdPriority] (a value object wrapping an ordered
 * list of [MetadataProviderId]). The default is [ContentIdPriority.DEFAULT] (AniList →
 * MAL → TMDB → Kitsu), but the user can change it via `ContentIdPreferences`.
 */
object ContentIdGenerator {

    /**
     * Generate a [ContentId] for an anime given its provider links + priority order.
     *
     * @param links The provider links available for this anime (provider → remoteId).
     *        Empty if no provider link exists.
     * @param localId The [LocalId] (per-source identity) — used as the fallback when
     *        [links] is empty or no link matches the priority order.
     * @param priority The user's preferred provider priority order.
     */
    fun generate(
        links: Map<MetadataProviderId, String>,
        localId: LocalId,
        priority: ContentIdPriority = ContentIdPriority.DEFAULT,
    ): ContentId {
        // Try each provider in priority order — first match wins.
        for (provider in priority.order) {
            val remoteId = links[provider]
            if (!remoteId.isNullOrBlank()) {
                return ContentId("${provider.key}:$remoteId")
            }
        }
        // Fallback: no provider link available → per-source identity (no grouping).
        return ContentId(localId.value)
    }

    /**
     * Convenience overload for the common case: only AniList is linked (or nothing).
     *
     * @param anilistId The AniList media ID, or null if not linked.
     * @param localId The [LocalId] (per-source identity) — fallback.
     * @param priority The user's preferred provider priority order.
     */
    fun generate(
        anilistId: Int?,
        localId: LocalId,
        priority: ContentIdPriority = ContentIdPriority.DEFAULT,
    ): ContentId {
        val links = buildMap {
            if (anilistId != null) put(MetadataProviderId.ANILIST, anilistId.toString())
        }
        return generate(links, localId, priority)
    }
}

/**
 * The user's preferred priority order for [ContentId] derivation.
 *
 * Wrapped in a value object so [ContentIdPreferences] can store + observe it reactively.
 * The [order] is a List (not a Set) because the order is the whole point.
 *
 * Default: [DEFAULT] = `listOf(ANILIST, MAL, TMDB, KITSU)` — the declaration order of
 * [MetadataProviderId]. The user can reorder this in Settings.
 *
 * Per the owner's direction (rev 2 of the identity model): "the user can select this
 * all by himself and hopefully it can be done in the future easily without issues."
 */
@JvmInline
value class ContentIdPriority(val order: List<MetadataProviderId>) {
    init {
        require(order.isNotEmpty()) { "ContentIdPriority.order must not be empty" }
        require(order.distinct().size == order.size) {
            "ContentIdPriority.order must not contain duplicates: $order"
        }
    }

    companion object {
        /** The default priority: AniList → MAL → TMDB → Kitsu (declaration order). */
        val DEFAULT = ContentIdPriority(MetadataProviderId.entries.toList())
    }
}
