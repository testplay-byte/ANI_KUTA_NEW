package app.confused.anikuta.core.common.model

/**
 * Tier 1 identity — a per-source identifier for a content entry (anime / series / movie).
 *
 * **Format (structured string, NOT a hash):**
 * - Extension-sourced: `"<system>:<extensionId>:<sourceContentId>"`
 *   - Example: `"aniyomi:1234567890:https://gogoanime.gg/frieren-sousou-no-yakusoku"`
 *   - Example: `"cloudstream:9876543210:frieren"` (future)
 * - Provider-sourced (no extension): `"<providerKey>:<remoteId>"`
 *   - Example: `"al:154587"` (AniList-only anime, no extension linked yet)
 *   - Example: `"mal:67890"` (MAL-only)
 *
 * **Why a structured string, not a hash:**
 * - Debuggable: `"aniyomi:1234567890:https://..."` is readable in logs.
 * - Self-describing: the prefix tells you which system/contract the source uses.
 * - Uniqueness is guaranteed by the components, not by hash collision avoidance.
 *   - `extensionId` is `MD5(name/lang/versionId).takeLowest64Bits()` — unique per extension.
 *   - `sourceContentId` is the source's own unique ID for that content (e.g., `SAnime.url`).
 *   - The combination `(system, extensionId, sourceContentId)` is globally unique.
 *
 * **Uniqueness contract:** [value] is globally unique. Two different content entries
 * never share a [LocalId]. This is enforced by the components, not by the string.
 *
 * **Per ADR-050** (the refined two-tier identity model). The [LocalId] is stored on
 * the `animes` table with full source provenance (system, repo_url, extension_name,
 * etc.). Cross-cutting stores key off [ContentId] (Tier 2), NOT [LocalId] — because
 * [LocalId] changes when the user switches source, while [ContentId] stays the same.
 *
 * @see LocalIdGenerator
 * @see ContentId
 */
@JvmInline
value class LocalId(val value: String) {

    /** The first segment — either an [ExtensionSystem.key] or a [MetadataProviderId.key]. */
    val prefix: String
        get() = value.substringBefore(':')

    /** Resolve [prefix] to an [ExtensionSystem], or null if it's a provider-sourced id. */
    val system: ExtensionSystem?
        get() = ExtensionSystem.fromKey(prefix)

    /** Resolve [prefix] to a [MetadataProviderId], or null if it's an extension-sourced id. */
    val provider: MetadataProviderId?
        get() = MetadataProviderId.fromKey(prefix)

    /** True if this [LocalId] is extension-sourced (3 segments). */
    val isExtensionSourced: Boolean
        get() = system != null

    /** True if this [LocalId] is provider-sourced (2 segments). */
    val isProviderSourced: Boolean
        get() = provider != null

    override fun toString(): String = value

    companion object {
        /**
         * Wrap a raw string into a [LocalId] WITHOUT validation. For SQLDelight/JSON interop
         * where the value comes from a trusted source (the DB). Does NOT throw — a malformed
         * DB value produces a [LocalId] with a malformed [value], which [LocalIdGenerator.parse]
         * returns null for (graceful degradation instead of a crash).
         *
         * For NEW local_ids created by application code, use [LocalIdGenerator.forExtension]
         * or [LocalIdGenerator.forProvider] (which validate their inputs).
         */
        fun unsafe(value: String): LocalId = LocalId(value)
    }
}

/**
 * The parsed components of a [LocalId] — for debugging + restore logic.
 */
sealed class ParsedLocalId {
    /** An extension-sourced local_id: `"<system>:<extensionId>:<sourceContentId>"`. */
    data class Extension(
        val system: ExtensionSystem,
        val extensionId: Long,
        val sourceContentId: String,
    ) : ParsedLocalId()

    /** A provider-sourced local_id: `"<providerKey>:<remoteId>"`. */
    data class Provider(
        val provider: MetadataProviderId,
        val remoteId: String,
    ) : ParsedLocalId()
}

/**
 * Generates [LocalId]s from their components.
 *
 * Use [forExtension] for content from an extension source, [forProvider] for content
 * from a metadata provider with no extension linked yet.
 */
object LocalIdGenerator {

    /**
     * Generate a [LocalId] for content from an extension source.
     *
     * Format: `"<system.key>:<extensionId>:<sourceContentId>"`
     *
     * @param system The extension system (Aniyomi, CloudStream, …).
     * @param extensionId The extension's stable source ID (= `AnimeSource.id`).
     * @param sourceContentId The source's own unique ID for this content (= `SAnime.url`
     *        for Aniyomi, or the equivalent for other systems).
     */
    fun forExtension(
        system: ExtensionSystem,
        extensionId: Long,
        sourceContentId: String,
    ): LocalId {
        require(extensionId > 0) { "extensionId must be positive: $extensionId" }
        require(sourceContentId.isNotBlank()) { "sourceContentId must not be blank" }
        return LocalId("${system.key}:$extensionId:$sourceContentId")
    }

    /**
     * Generate a [LocalId] for content from a metadata provider (no extension linked).
     *
     * Format: `"<provider.key>:<remoteId>"`
     *
     * @param provider The metadata provider (AniList, MAL, …).
     * @param remoteId The provider's stable ID for this content (as a string for generality;
     *        AniList/MAL IDs are integers, but TMDB/Kitsu may use other formats).
     */
    fun forProvider(
        provider: MetadataProviderId,
        remoteId: String,
    ): LocalId {
        require(remoteId.isNotBlank()) { "remoteId must not be blank" }
        return LocalId("${provider.key}:$remoteId")
    }

    /**
     * Parse a [LocalId] back into its components.
     *
     * Returns null if the string is malformed (unknown system/provider prefix,
     * non-numeric extensionId, or wrong segment count).
     */
    fun parse(localId: LocalId): ParsedLocalId? {
        val value = localId.value
        // Split into at most 3 parts — the sourceContentId may itself contain ':'.
        val parts = value.split(":", limit = 3)
        return when (parts.size) {
            3 -> {
                val system = ExtensionSystem.fromKey(parts[0]) ?: return null
                val extensionId = parts[1].toLongOrNull() ?: return null
                val sourceContentId = parts[2]
                if (sourceContentId.isBlank()) return null
                ParsedLocalId.Extension(system, extensionId, sourceContentId)
            }
            2 -> {
                val provider = MetadataProviderId.fromKey(parts[0]) ?: return null
                val remoteId = parts[1]
                if (remoteId.isBlank()) return null
                ParsedLocalId.Provider(provider, remoteId)
            }
            else -> null
        }
    }
}
