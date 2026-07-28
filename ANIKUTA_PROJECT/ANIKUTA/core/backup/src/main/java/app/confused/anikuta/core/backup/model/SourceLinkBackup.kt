package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper for the AniList↔extension link stores.
 *
 * Combines:
 * - `SourceLinkStore` — `Map<String, SourceLinkItem>` keyed by AniList ID
 *   (maps an AniList anime to its extension source match).
 * - `ExtensionLinkStore` — `Map<String, Int>` keyed by `"$sourceId:$animeUrl"`
 *   (maps an extension anime to its AniList ID).
 *
 * Both are SharedPreferences-backed JSON maps, so they serialize cleanly.
 */
@Serializable
data class SourceLinkBackup(
    /** Key: AniList anime ID (as string). Value: the matched extension source. */
    val sourceLinks: Map<String, SourceLinkItem> = emptyMap(),
    /** Key: "$sourceId:$animeUrl". Value: the linked AniList anime ID. */
    val extensionLinks: Map<String, Int> = emptyMap(),
)

/**
 * One AniList→extension source link (mirrors SourceLinkStore.SourceLink).
 */
@Serializable
data class SourceLinkItem(
    val sourceId: Long,
    val animeUrl: String,
    val animeTitle: String,
)
