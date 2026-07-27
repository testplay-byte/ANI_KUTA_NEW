package app.confused.anikuta.core.tracker.mal

import android.util.Base64
import java.security.SecureRandom

/**
 * PKCE (Proof Key for Code Exchange) utility for MAL OAuth.
 *
 * Generates a random code_verifier and derives the code_challenge using
 * the "plain" method (code_challenge = code_verifier). MAL accepts both
 * "plain" and "S256"; we use "plain" for simplicity (matching Aniyomi's
 * approach).
 */
object PkceUtil {
    /** Generate a random 50-byte code_verifier, base64url-encoded (no padding). */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(50)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }
}
