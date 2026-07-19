package app.anikuta.util.lang

import java.security.MessageDigest

/**
 * SHA-256 hash utility for extension signature verification.
 */
object Hash {
    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
