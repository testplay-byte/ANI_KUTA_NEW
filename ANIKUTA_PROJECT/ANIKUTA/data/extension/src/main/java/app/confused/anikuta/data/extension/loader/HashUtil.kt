package app.confused.anikuta.data.extension.loader

import java.security.MessageDigest

/**
 * SHA-256 hashing helper used by the extension loader to fingerprint APK
 * signing certificates.
 *
 * Ported from the Aniyomi reference's `eu.kanade.tachiyomi.util.lang.Hash`.
 * The trusted-extensions preference stores `"pkgName:versionCode:signatureHash"`
 * entries; the signature hash is `sha256(certificate.toByteArray())` hex-encoded.
 */
internal object HashUtil {

    /** Returns the lowercase-hex SHA-256 digest of [bytes]. */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
