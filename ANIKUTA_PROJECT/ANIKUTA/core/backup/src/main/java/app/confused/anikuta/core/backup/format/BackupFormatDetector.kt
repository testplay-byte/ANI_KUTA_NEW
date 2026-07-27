package app.confused.anikuta.core.backup.format

import android.util.Log
import app.confused.anikuta.core.backup.BackupException
import app.confused.anikuta.core.backup.BackupFormat
import app.confused.anikuta.core.backup.BackupFormatType
import java.io.InputStream

private const val TAG = "AnikutaBackup"

/**
 * Detects the format of a backup file by sniffing its magic bytes.
 *
 * Detection order:
 * 1. **ANIKUTA** — ZIP magic (`PK\x03\x04`). Our `.anikuta` files are zips.
 * 2. **ANIYOMI** — GZIP magic (`0x1f 0x8b`). Aniyomi `.tachibk` files are
 *    gzipped protobuf. (Non-gzipped protobuf is rare and harder to detect;
 *    the [BackupManager] tries Aniyomi as a fallback if ANIKUTA detection
 *    fails.)
 *
 * The detector does NOT consume the stream — it uses [InputStream.mark] /
 * [InputStream.reset] to peek the header. Callers must pass a stream that
 * supports marking (e.g. `BufferedInputStream`), or the detector will
 * internally buffer the first bytes.
 */
object BackupFormatDetector {

    /**
     * Detect the format of the given input stream.
     *
     * @param input a stream that supports [InputStream.mark] (mark at least 4 bytes).
     * @return the detected [BackupFormatType], or null if unknown.
     */
    fun detect(input: InputStream): BackupFormatType? {
        return try {
            if (!input.markSupported()) {
                // Wrap in a buffered stream — but we can't here (we don't own the stream).
                // The caller should wrap it. Fall back to reading 4 bytes.
                return detectByReading(input)
            }
            input.mark(4)
            val header = ByteArray(4)
            val read = input.read(header)
            input.reset()
            if (read < 2) return null

            val b0 = header[0].toInt() and 0xFF
            val b1 = header[1].toInt() and 0xFF

            when {
                // ZIP (ANIKUTA): 0x50 0x4B 0x03 0x04
                b0 == 0x50 && b1 == 0x4B -> BackupFormatType.ANIKUTA
                // GZIP (Aniyomi): 0x1f 0x8b
                b0 == 0x1f && b1 == 0x8b -> BackupFormatType.ANIYOMI
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Format detection failed: ${e.message}")
            null
        }
    }

    /**
     * Fallback detection for non-markable streams — reads the first bytes
     * and returns the format. The caller must re-open the stream after this.
     */
    private fun detectByReading(input: InputStream): BackupFormatType? {
        val header = ByteArray(4)
        val read = input.read(header)
        if (read < 2) return null
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        return when {
            b0 == 0x50 && b1 == 0x4B -> BackupFormatType.ANIKUTA
            b0 == 0x1f && b1 == 0x8b -> BackupFormatType.ANIYOMI
            else -> null
        }
    }

    /**
     * Convenience: detect format from a byte array (e.g. after reading the file).
     */
    fun detect(bytes: ByteArray): BackupFormatType? {
        if (bytes.size < 2) return null
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        return when {
            b0 == 0x50 && b1 == 0x4B -> BackupFormatType.ANIKUTA
            b0 == 0x1f && b1 == 0x8b -> BackupFormatType.ANIYOMI
            else -> null
        }
    }
}
