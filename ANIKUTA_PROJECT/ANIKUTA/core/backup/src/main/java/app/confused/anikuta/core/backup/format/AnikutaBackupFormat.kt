package app.confused.anikuta.core.backup.format

import android.util.Log
import app.confused.anikuta.core.backup.BackupException
import app.confused.anikuta.core.backup.BackupFormat
import app.confused.anikuta.core.backup.BackupFormatType
import app.confused.anikuta.core.backup.model.BackupContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "AnikutaBackup"
private const val META_ENTRY = "meta.json"
private const val COVERS_DIR = "covers/"
private const val ZIP_MAGIC = 0x504b0304 // "PK\x03\x04"

/**
 * The ANIKUTA backup format — a zip container with gzipped JSON inside.
 *
 * File structure (`.anikuta` extension):
 * ```
 * backup.anikuta (ZIP)
 * ├── meta.json.gz   — gzipped JSON of [BackupContainer] (all provider data)
 * └── covers/        — optional cover images (if COVER_IMAGES category selected)
 *     ├── 12345.jpg
 *     └── 67890.jpg
 * ```
 *
 * - **Create**: serializes [BackupContainer] to JSON → gzips → writes to a zip
 *   alongside any cover images.
 * - **Restore**: opens the zip → reads `meta.json.gz` → gunzips → deserializes.
 *
 * The zip wrapper allows bundling binary cover images alongside the JSON data
 * without base64-encoding them (which would bloat memory). Gzipping the JSON
 * keeps text data compact.
 *
 * All I/O runs on [Dispatchers.IO].
 */
class AnikutaBackupFormat : BackupFormat {

    override val type: BackupFormatType = BackupFormatType.ANIKUTA

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        classDiscriminator = "type"
    }

    override suspend fun write(
        container: BackupContainer,
        covers: Map<Int, ByteArray>,
        output: OutputStream,
    ) {
        withContext(Dispatchers.IO) {
        try {
            val jsonBytes = json.encodeToString(container).toByteArray(Charsets.UTF_8)
            ZipOutputStream(output).use { zip ->
                // Write the gzipped JSON metadata
                val gzipped = ByteArrayOutputStream().use { baos ->
                    GZIPOutputStream(baos).use { it.write(jsonBytes) }
                    baos.toByteArray()
                }
                zip.putNextEntry(ZipEntry(META_ENTRY))
                zip.write(gzipped)
                zip.closeEntry()

                // Write cover images (if any)
                covers.forEach { (anilistId, bytes) ->
                    val entry = ZipEntry("$COVERS_DIR$anilistId.jpg")
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            Log.i(TAG, "ANIKUTA backup written: ${container.entries.size} entries, ${covers.size} covers")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ANIKUTA backup", e)
            throw BackupException.CorruptFile("write failed: ${e.message}", e)
        }
        }
    }

    override suspend fun read(input: InputStream): BackupContainer = withContext(Dispatchers.IO) {
        try {
            var container: BackupContainer? = null
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == META_ENTRY) {
                        val bytes = zip.readBytes()
                        val ungzipped = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                        val jsonStr = ungzipped.toString(Charsets.UTF_8)
                        container = json.decodeFromString<BackupContainer>(jsonStr)
                    }
                    entry = zip.nextEntry
                }
            }
            container ?: throw BackupException.CorruptFile("meta.json not found in backup zip")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ANIKUTA backup", e)
            throw BackupException.CorruptFile("read failed: ${e.message}", e)
        }
    }

    override suspend fun readCovers(input: InputStream): Map<Int, ByteArray> = withContext(Dispatchers.IO) {
        val covers = mutableMapOf<Int, ByteArray>()
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith(COVERS_DIR) && entry.name.endsWith(".jpg")) {
                        val idStr = entry.name.removePrefix(COVERS_DIR).removeSuffix(".jpg")
                        val anilistId = idStr.toIntOrNull()
                        if (anilistId != null) {
                            covers[anilistId] = zip.readBytes()
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read covers from backup (non-fatal)", e)
        }
        Log.d(TAG, "Read ${covers.size} cover images from backup")
        covers
    }

    override fun detect(input: InputStream): Boolean {
        return try {
            input.mark(4)
            val header = ByteArray(4)
            val read = input.read(header)
            input.reset()
            if (read < 4) return false
            // ZIP magic: 0x50 0x4B 0x03 0x04 ("PK\x03\x04")
            header[0].toInt() and 0xFF == 0x50 &&
                header[1].toInt() and 0xFF == 0x4B &&
                header[2].toInt() and 0xFF == 0x03 &&
                header[3].toInt() and 0xFF == 0x04
        } catch (e: Exception) {
            false
        }
    }
}
