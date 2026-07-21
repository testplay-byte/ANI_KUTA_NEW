package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Catches any uncaught exceptions from later in the chain and rethrows as a non-fatal
 * IOException to avoid catastrophic failure.
 *
 * This should be the first interceptor in the client.
 *
 * Ported from the Aniyomi reference's `core/common/.../network/interceptor/UncaughtExceptionInterceptor.kt`.
 * Extensions compiled against the reference expect this class to exist at this exact
 * package + name. Missing it causes `NoClassDefFoundError` at runtime.
 *
 * See https://square.github.io/okhttp/4.x/okhttp/okhttp3/-interceptor/
 */
class UncaughtExceptionInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: Exception) {
            if (e is IOException) {
                throw e
            } else {
                throw IOException(e)
            }
        }
    }
}
