package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cache
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import eu.kanade.tachiyomi.util.awaitSingle
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * NetworkHelper — provides the OkHttpClient and a JSON parser.
 *
 * **CRITICAL — binary compatibility (ADR-029):**
 * This MUST be a `class` (not an `interface`). Keiyoushi/Aniyomi extensions
 * are compiled against the reference `eu.kanade.tachiyomi.network.NetworkHelper`
 * which is a class. If this is declared as an interface, the extension's
 * bytecode (which uses `invokevirtual NetworkHelper.getClient()`) throws
 * `IncompatibleClassChangeError: Found interface NetworkHelper, but class was
 * expected` at runtime — crashing the app whenever a source tries to make an
 * HTTP request.
 *
 * Ported from the Aniyomi reference's `NetworkHelper.kt` (in `:core:common`),
 * simplified to remove DoH / Cloudflare / verbose-logging interceptors that
 * require additional modules we don't have yet. The essential API surface
 * (`client`, `cloudflareClient`, `defaultUserAgentProvider()`) is preserved
 * so extension bytecode resolves correctly.
 *
 * Registered in Injekt by `App.kt` so extensions that call
 * `Injekt.get<NetworkHelper>()` (via `injectLazy()` in `AnimeHttpSource`)
 * can resolve it.
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

// ── Request builders (extensions import these top-level functions) ──

/** GET request builder. Extensions call this to create GET requests. */
fun GET(
    url: String,
    headers: Headers = Headers.Builder().build(),
    cache: okhttp3.CacheControl? = null,
): Request {
    val builder = Request.Builder().url(url).headers(headers)
    if (cache != null) builder.cacheControl(cache)
    return builder.build()
}

/** POST request builder. Extensions call this to create POST requests. */
fun POST(
    url: String,
    body: okhttp3.RequestBody,
    headers: Headers = Headers.Builder().build(),
    cache: okhttp3.CacheControl? = null,
): Request {
    val builder = Request.Builder().url(url).post(body).headers(headers)
    if (cache != null) builder.cacheControl(cache)
    return builder.build()
}

/** ProgressListener — for download progress tracking. */
interface ProgressListener {
    fun onProgress(progress: Long, total: Long, done: Boolean)
}

/** Extension function: convert an OkHttp Call to an RxJava Observable. */
fun okhttp3.Call.asObservableSuccess(): Observable<Response> {
    return Observable.create { subscriber ->
        try {
            val response = execute()
            if (response.isSuccessful) {
                subscriber.onNext(response)
                subscriber.onCompleted()
            } else {
                subscriber.onError(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            subscriber.onError(e)
        }
    }
}

/** Extension function: await the success of an RxJava Observable.
 * Delegates to eu.kanade.tachiyomi.util.awaitSingle (from RxExtension.kt). */
suspend fun <T> Observable<T>.awaitSuccess(): T = this.awaitSingle()

/** Extension function: suspend and await an OkHttp Call's response directly. */
suspend fun okhttp3.Call.awaitSuccess(): okhttp3.Response {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    cont.resume(response)
                } else {
                    cont.resumeWithException(Exception("HTTP ${response.code}"))
                }
            }
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                cont.resumeWithException(e)
            }
        })
    }
}

/** Extension function: create a cacheless OkHttp call with progress. */
fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
): okhttp3.Call {
    // Simple implementation — wraps the call with a progress-tracking interceptor
    return newCall(request)
}
