package app.anikuta.download.engine.hls

import okhttp3.Headers

/**
 * Sealed result of parsing an m3u8 playlist file.
 *
 * Two variants:
 * - [Master]: multi-variant playlist (contains quality options)
 * - [Media]: segment playlist (contains actual .ts segment URLs)
 */
sealed interface HlsPlaylist {
    val baseUrl: String
    val headers: Headers

    /**
     * Master (multi-variant) playlist — lists alternative quality streams.
     * Recognised by the presence of #EXT-X-STREAM-INF tags.
     */
    data class Master(
        override val baseUrl: String,
        override val headers: Headers,
        val variants: List<Variant>,
    ) : HlsPlaylist {
        data class Variant(
            val uri: String,
            val bandwidth: Long,
            val resolution: String?,
            val codecs: String?,
            val frameRate: Double?,
        )
    }

    /**
     * Media (segment) playlist — lists the actual .ts segments to download.
     * Recognised by the presence of #EXTINF tags.
     */
    data class Media(
        override val baseUrl: String,
        override val headers: Headers,
        val version: Int,
        val targetDurationSec: Int,
        val mediaSequence: Long,
        val isVod: Boolean,
        val segments: List<Segment>,
    ) : HlsPlaylist {
        /**
         * One media segment.
         * @param index 0-based position in the playlist
         * @param sequenceNumber EXT-X-MEDIA-SEQUENCE + index
         * @param durationSec from #EXTINF
         * @param url resolved absolute URL
         * @param key encryption params (null = unencrypted)
         */
        data class Segment(
            val index: Int,
            val sequenceNumber: Long,
            val durationSec: Double,
            val url: String,
            val key: Key?,
        )

        /**
         * Encryption key params from #EXT-X-KEY.
         * Applies to all following segments until the next #EXT-X-KEY.
         */
        data class Key(
            val method: Method,
            val uri: String?,
            val iv: ByteArray?,
        ) {
            enum class Method { NONE, AES_128, SAMPLE_AES, UNKNOWN }
        }
    }
}
