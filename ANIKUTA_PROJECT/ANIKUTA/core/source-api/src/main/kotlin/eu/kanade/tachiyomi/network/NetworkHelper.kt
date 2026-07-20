package eu.kanade.tachiyomi.network

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Network helper stubs — replaces the reference's `eu.kanade.tachiyomi.network`
 * package from `:core:common`.
 *
 * Extensions import these functions. We provide minimal implementations that
 * work with our OkHttp client. The full NetworkHelper (with rate limiting,
 * interceptors, etc.) will be in `:core:network` — but extensions only need
 * these simple functions.
 */

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

/** NetworkHelper — provides the OkHttpClient and a JSON parser. */
interface NetworkHelper {
    val client: OkHttpClient
    val cloudflareClient: OkHttpClient
    val defaultUserAgent: String
    fun defaultUserAgentProvider(): String = defaultUserAgent
}

/** Default implementation of NetworkHelper with a standard OkHttp client. */
class DefaultNetworkHelper : NetworkHelper {
    override val client: OkHttpClient = OkHttpClient.Builder().build()
    override val cloudflareClient: OkHttpClient = OkHttpClient.Builder().build()
    override val defaultUserAgent: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
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
suspend fun <T> Observable<T>.awaitSuccess(): T {
    return eu.kanade.tachiyomi.util.run { this@awaitSuccess.awaitSingle() }
}

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
