package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * NetworkHelper — provides the OkHttpClient used by all extension sources.
 *
 * **CRITICAL — binary compatibility (ADR-029):**
 * This MUST be a `class` (not an `interface`). Keiyoushi/Aniyomi extensions
 * are compiled against the reference `eu.kanade.tachiyomi.network.NetworkHelper`
 * which is a class. If this is declared as an interface, the extension's
 * bytecode (which uses `invokevirtual NetworkHelper.getClient()`) throws
 * `IncompatibleClassChangeError` at runtime.
 *
 * Registered in Injekt by `App.kt` so extensions that call
 * `Injekt.get<NetworkHelper>()` (via `injectLazy()` in `AnimeHttpSource`)
 * can resolve it.
 *
 * **Interceptors:**
 * The client is configured with the same interceptor chain as the reference:
 * - [UncaughtExceptionInterceptor] — wraps non-IOExceptions as IOExceptions
 * - [UserAgentInterceptor] — adds a default User-Agent header
 * - [IgnoreGzipInterceptor] — allows Brotli to handle compression
 *
 * Cloudflare and DoH interceptors are omitted (they require WebView and
 * additional deps that aren't set up yet).
 *
 * @param context used for the OkHttp cache directory (optional — null skips caching).
 */
class NetworkHelper(
    private val context: Context? = null,
) {

    private val cacheDir: File? = context?.cacheDir?.let { File(it, "network_cache") }

    private val clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        .addNetworkInterceptor(IgnoreGzipInterceptor())
        .apply {
            // Cache (5 MiB) — only if we have a context with a cache dir.
            cacheDir?.let { dir ->
                dir.mkdirs()
                cache(Cache(directory = dir, maxSize = 5L * 1024 * 1024))
            }
        }

    /** The default OkHttp client used by sources for HTTP requests. */
    val client: OkHttpClient = clientBuilder.build()

    /**
     * Cloudflare-capable client.
     *
     * The reference has a separate CloudflareInterceptor here. We don't have
     * the WebView-based Cloudflare bypass yet, so this is the same as [client].
     * Extensions that call `network.cloudflareClient` will still resolve.
     */
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    /** The default User-Agent string sent with requests if none is specified. */
    val defaultUserAgent: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /** Provider function — extensions call this to get the current User-Agent. */
    fun defaultUserAgentProvider(): String = defaultUserAgent
}

/**
 * Backward-compat alias — the previous stub declared `DefaultNetworkHelper` as
 * a separate class. Now that `NetworkHelper` is a real class, this alias lets
 * any code that still references `DefaultNetworkHelper()` compile unchanged.
 * `DefaultNetworkHelper()` ≡ `NetworkHelper()` (no-arg → no cache).
 */
typealias DefaultNetworkHelper = NetworkHelper
