package app.confused.anikuta.core.common.model

/**
 * The extension system / contract a content source uses.
 *
 * ANIKUTA supports multiple "core systems" — each is a distinct extension
 * format with its own contract, package layout, and loading mechanism.
 * Today only [ANIYOMI] is implemented; [CLOUDSTREAM] and others are
 * future additions (see `_ARCHITECTURE_PLAN/proposals/01a_refined_id_system.md`
 * and `_ARCHITECTURE_PLAN/proposals/04_extension_evolution.md`).
 *
 * This enum is the "system" component of the [LocalId] (ADR-050):
 * ```
 * local_id = "<system>:<extensionId>:<sourceContentId>"
 * ```
 *
 * Per ADR-029, the Aniyomi/Mihon lineage (Tachiyomi forks that use the
 * `eu.kanade.tachiyomi.animesource.*` package) is treated as ONE system —
 * `ANIYOMI` — because the contract is identical across forks. A fork that
 * changes the contract would be a new [ExtensionSystem].
 *
 * Adding a new core system: add an enum constant here (one line) + create
 * the parallel `:core:<system>-source-api` + `:data:<system>-extension`
 * modules. Everything downstream (local_id, content_id, downloads, backup)
 * works unchanged because [key] is a stable string.
 */
enum class ExtensionSystem(
    /** The stable string key used in [LocalId] serialization + the `system` DB column. */
    val key: String,
    /** Human-readable name for UI display. */
    val displayName: String,
) {
    /**
     * The Aniyomi / Mihon / Tachiyomi-fork system.
     *
     * Contract: `eu.kanade.tachiyomi.animesource.*` (ADR-029).
     * Source IDs are deterministic: `MD5(name/lang/versionId).takeLowest64Bits() and Long.MAX_VALUE`
     * (see `core/source-api/.../online/AnimeHttpSource.kt`).
     */
    ANIYOMI("aniyomi", "Aniyomi / Mihon"),

    /**
     * The CloudStream 3 system (future).
     *
     * Contract: `com.lagradost.cloudstream3.*`.
     * Parallel module stack — see `proposals/04_extension_evolution.md`.
     */
    CLOUDSTREAM("cloudstream", "CloudStream"),
    ;

    companion object {
        /** Resolve a [key] string back to its [ExtensionSystem], or null if unknown. */
        fun fromKey(key: String): ExtensionSystem? = entries.firstOrNull { it.key == key }
    }
}
