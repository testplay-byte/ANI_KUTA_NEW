package app.anikuta.download.engine.hls

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Downloads a single .ts segment via HTTP GET and writes it to a local file.
 *
 * Handles AES-128-CBC decryption if the segment is encrypted (per #EXT-X-KEY).
 * Each segment is decrypted independently (cipher state resets per segment).
 *
 * Key features:
 * - Streams to disk (no full in-memory buffering — segments are 2-10 MB each)
 * - AES key is cached per engine instance (fetched once, reused)
 * - IV derived from segment sequence number if not provided in EXT-X-KEY
 *
 * @param client OkHttp client (from NetworkHelper)
 */
class HlsSegmentDownloader(
    private val client: OkHttpClient,
) {
    companion object {
        private const val TAG = "HlsSegmentDownloader"
        private const val BUFFER_SIZE = 16 * 1024
    }

    /** Result of a single segment download. */
    data class Result(
        val success: Boolean,
        val sizeBytes: Long,
        val error: String? = null,
    )

    /** Cached AES keys (key URL → 16 raw bytes). Survives across segments. */
    private val keyCache = mutableMapOf<String, ByteArray>()

    /**
     * Download [segment] to [outFile] in cache dir.
     *
     * - Streams the response body to disk (no full in-memory buffering)
     * - If encrypted: fetches key (cached), decrypts in 16KB blocks
     * - Verifies the file is non-empty after write
     *
     * @return Result with size and success flag
     */
    suspend fun download(
        segment: HlsPlaylist.Media.Segment,
        headers: Headers,
        outFile: File,
    ): Result = withContext(Dispatchers.IO) {
        if (outFile.exists()) outFile.delete()
        outFile.parentFile?.mkdirs()

        val req = Request.Builder().url(segment.url).headers(headers).get().build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result(false, 0, "HTTP ${resp.code}")
                }
                val body = resp.body ?: return@withContext Result(false, 0, "empty body")

                val key = segment.key
                if (key?.method == HlsPlaylist.Media.Key.Method.AES_128) {
                    // Encrypted segment — fetch key, decrypt, write
                    val keyBytes = fetchKey(key.uri!!, headers)
                        ?: return@withContext Result(false, 0, "key fetch failed")
                    val iv = key.iv ?: deriveIv(segment.sequenceNumber)
                    val cipher = buildAesCipher(keyBytes, iv)
                    streamDecrypt(body.byteStream(), outFile, cipher)
                } else {
                    // Unencrypted segment — direct stream copy
                    body.byteStream().use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            val size = outFile.length()
            if (size == 0L) {
                Result(false, 0, "empty segment file")
            } else {
                Result(true, size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "segment ${segment.index} failed: ${e.message}")
            outFile.delete()
            Result(false, 0, e.message)
        }
    }

    /**
     * Fetch (and cache) the AES-128 key. Returns 16 bytes or null on failure.
     * The key is fetched once and cached for all subsequent segments using the same key URL.
     */
    private fun fetchKey(url: String, headers: Headers): ByteArray? {
        keyCache[url]?.let { return it }
        return try {
            val req = Request.Builder().url(url).headers(headers).get().build()
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.size != 16) {
                    Log.e(TAG, "AES key wrong size: ${bytes.size} (expected 16)")
                    return null
                }
                keyCache[url] = bytes
                bytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "AES key fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Build an AES-128-CBC decryption cipher.
     */
    private fun buildAesCipher(key: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher
    }

    /**
     * Stream-decrypt: read chunks, cipher.update() on each, doFinal() at the end.
     * Writes plaintext to [outFile].
     */
    private fun streamDecrypt(
        input: java.io.InputStream,
        outFile: File,
        cipher: Cipher,
    ) {
        outFile.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                val plain = cipher.update(buffer, 0, n)
                if (plain != null) output.write(plain)
            }
            val tail = cipher.doFinal()
            if (tail != null) output.write(tail)
        }
    }

    /**
     * RFC 8216 §5.2: IV = sequence number as 128-bit big-endian integer.
     * Used when EXT-X-KEY doesn't provide an explicit IV.
     */
    private fun deriveIv(seq: Long): ByteArray {
        val iv = ByteArray(16)
        iv[12] = ((seq ushr 24) and 0xFF).toByte()
        iv[13] = ((seq ushr 16) and 0xFF).toByte()
        iv[14] = ((seq ushr 8) and 0xFF).toByte()
        iv[15] = (seq and 0xFF).toByte()
        return iv
    }
}
