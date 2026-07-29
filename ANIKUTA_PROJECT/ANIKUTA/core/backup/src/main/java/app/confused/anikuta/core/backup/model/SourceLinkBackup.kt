package app.confused.anikuta.core.backup.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.asJsonDecoder
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.isString

/**
 * Serializable wrapper for the AniList↔extension link stores.
 *
 * Combines:
 * - `SourceLinkStore` — `Map<String, SourceLinkItem>` keyed by **content_id**
 *   (Phase 4, ADR-050). Pre-Phase-4 backups keyed by anilistId.toString(); the
 *   import path detects + converts those to `"al:$anilistId"` content_ids.
 * - `ExtensionLinkStore` — `Map<String, String>` keyed by `"$sourceId:$animeUrl"`,
 *   value = content_id (e.g., `"al:154587"`). Pre-Phase-4 backups had
 *   `Map<String, Int>` (value = anilistId); [TolerantContentIdMapSerializer]
 *   auto-converts Int values to `"al:$int"` content_ids on read.
 *
 * Both are SharedPreferences-backed JSON maps, so they serialize cleanly.
 */
@Serializable
data class SourceLinkBackup(
    /**
     * Key: content_id (e.g., `"al:154587"`). Pre-Phase-4 backups used
     * anilistId.toString() as the key — the import path detects + converts.
     * Value: the matched extension source.
     */
    val sourceLinks: Map<String, SourceLinkItem> = emptyMap(),
    /**
     * Key: `"$sourceId:$animeUrl"`. Value: content_id (e.g., `"al:154587"`).
     * Pre-Phase-4 backups had Int values (anilistId) — auto-converted on read
     * via [TolerantContentIdMapSerializer].
     */
    @Serializable(with = TolerantContentIdMapSerializer::class)
    val extensionLinks: Map<String, String> = emptyMap(),
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

/**
 * Serializes `Map<String, String>` for content_id maps but tolerates legacy
 * pre-Phase-4 backups that had `Map<String, Int>` (anilistId as Int).
 *
 * On read, Int values are auto-converted to `"al:$int"` content_ids. This
 * keeps backup-format backward-compat: a pre-Phase-4 backup restores cleanly
 * into the Phase-4 stores without a schema bump.
 *
 * On write, only `Map<String, String>` is emitted (the new format).
 */
internal object TolerantContentIdMapSerializer : KSerializer<Map<String, String>> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val jsonDecoder = decoder.asJsonDecoder()
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return emptyMap()
        val out = mutableMapOf<String, String>()
        for ((key, value) in element) {
            when {
                value is JsonPrimitive && value.isString -> out[key] = value.content
                // Legacy pre-Phase-4 backups stored anilistId as a JSON Int.
                // Convert to "al:$int" content_id form.
                value is JsonPrimitive && value.intOrNull != null ->
                    out[key] = "al:${value.intOrNull}"
                else -> { /* skip malformed entry */ }
            }
        }
        return out
    }
}
