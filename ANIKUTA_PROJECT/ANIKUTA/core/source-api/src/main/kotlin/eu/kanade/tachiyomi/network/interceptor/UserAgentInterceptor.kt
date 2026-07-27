package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds a default `User-Agent` header to requests that don't already have one.
 *
 * Ported from the Aniyomi reference's `core/common/.../network/interceptor/UserAgentInterceptor.kt`.
 * Extensions compiled against the reference expect this class to exist at this exact
 * package + name.
 *
 * @param defaultUserAgentProvider called to get the current User-Agent string.
 */
class UserAgentInterceptor(
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            val newRequest = originalRequest
                .newBuilder()
                .removeHeader("User-Agent")
                .addHeader("User-Agent", defaultUserAgentProvider())
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
