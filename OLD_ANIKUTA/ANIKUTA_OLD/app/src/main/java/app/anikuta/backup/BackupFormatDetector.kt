package app.anikuta.backup

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Detects, serializes, and deserializes backup files in two formats:
 *
 * 1. **AniKuta format** (.anikuta): JSON with a magic header "ANIKUTA1".
 *    Human-readable, contains all our data (library, history, searches, settings, etc.)
 *
 * 2. **Aniyomi format** (.json.gz or .protobuf.gz): Protocol Buffers, gzipped.
 *    Compatible with aniyomi's backup format — can be imported/exported by aniyomi.
 *
 * Auto-detection on restore:
 *  - Read all bytes from the stream ONCE (ContentResolver streams don't support
 *    mark/reset, so we can't peek and rewind).
 *  - Check first 8 bytes for magic header → AniKuta format.
 *  - Otherwise try protobuf decode (gzip first, then raw) → Aniyomi format.
 *  - If both fail → error.
 */
object BackupFormatDetector {

    private const val TAG = "BackupFormat"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    enum class Format { ANIKUTA, ANIYOMI, UNKNOWN }

    /**
     * Detect the format of a backup file by reading all bytes ONCE.
     *
     * IMPORTANT: ContentResolver.openInputStream() returns streams that do NOT
     * support mark()/reset(). We must read all bytes in one pass, then work
     * with the byte array.
     */
    fun detect(bytes: ByteArray): Format {
        return try {
            if (bytes.size < 8) return Format.UNKNOWN

            // Check for AniKuta magic header (first 8 bytes)
            val header = String(bytes, 0, 8, Charsets.UTF_8)
            if (header == AnikutaBackup.MAGIC) {
                return Format.ANIKUTA
            }

            // Not AniKuta — try Aniyomi (protobuf, possibly gzipped)
            // Try gzip + protobuf first
            try {
                val decompressed = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), decompressed)
                Format.ANIYOMI
            } catch (e: Exception) {
                // Maybe not gzipped — try raw protobuf
                try {
                    ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
                    Format.ANIYOMI
                } catch (e2: Exception) {
                    Log.w(TAG, "detect: not AniKuta, not Aniyomi (gzip: ${e.message}, raw: ${e2.message})")
                    Format.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "detect failed: ${e.message}")
            Format.UNKNOWN
        }
    }

    /**
     * Convenience method: detect format from an InputStream.
     * Reads all bytes into memory, then calls detect(ByteArray).
     */
    fun detect(input: InputStream): Format {
        return try {
            detect(input.readBytes())
        } catch (e: Exception) {
            Log.w(TAG, "detect(InputStream) failed: ${e.message}")
            Format.UNKNOWN
        }
    }

    // ---- AniKuta format (.anikuta) ----

    /**
     * Serialize an AniKuta backup to an output stream (JSON with magic header).
     * Format: [8 bytes magic "ANIKUTA1"] + [JSON string, UTF-8 encoded]
     */
    fun writeAnikuta(backup: AnikutaBackup, output: OutputStream) {
        val jsonStr = json.encodeToString(AnikutaBackup.serializer(), backup)
        val data = (AnikutaBackup.MAGIC + jsonStr).toByteArray(Charsets.UTF_8)
        output.write(data)
        output.flush()
    }

    /**
     * Deserialize an AniKuta backup from a byte array.
     */
    fun readAnikuta(bytes: ByteArray): AnikutaBackup? {
        return try {
            if (bytes.size < 8) return null
            val header = String(bytes, 0, 8, Charsets.UTF_8)
            if (header != AnikutaBackup.MAGIC) return null
            val jsonStr = String(bytes, 8, bytes.size - 8, Charsets.UTF_8)
            json.decodeFromString(AnikutaBackup.serializer(), jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "readAnikuta failed", e)
            null
        }
    }

    /**
     * Convenience method: deserialize from an InputStream.
     */
    fun readAnikuta(input: InputStream): AnikutaBackup? {
        return try {
            readAnikuta(input.readBytes())
        } catch (e: Exception) {
            Log.e(TAG, "readAnikuta(InputStream) failed", e)
            null
        }
    }

    // ---- Aniyomi format (.json.gz — actually protobuf+gzip) ----

    /**
     * Serialize an aniyomi-compatible backup (protobuf + gzip).
     */
    fun writeAniyomi(backup: AniyomiBackup, output: OutputStream) {
        val protoBytes = ProtoBuf.encodeToByteArray(AniyomiBackup.serializer(), backup)
        GZIPOutputStream(output).use { it.write(protoBytes) }
    }

    /**
     * Deserialize an aniyomi-format backup from a byte array.
     * Tries gzip first, then raw protobuf.
     */
    fun readAniyomi(bytes: ByteArray): AniyomiBackup? {
        return try {
            // Try gzip first
            try {
                val decompressed = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), decompressed)
            } catch (e: Exception) {
                // Maybe not gzipped — try raw protobuf
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "readAniyomi failed", e)
            null
        }
    }

    /**
     * Convenience method: deserialize from an InputStream.
     */
    fun readAniyomi(input: InputStream): AniyomiBackup? {
        return try {
            readAniyomi(input.readBytes())
        } catch (e: Exception) {
            Log.e(TAG, "readAniyomi(InputStream) failed", e)
            null
        }
    }
}
