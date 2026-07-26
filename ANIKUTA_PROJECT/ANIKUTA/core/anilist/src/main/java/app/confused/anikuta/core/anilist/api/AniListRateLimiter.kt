package app.confused.anikuta.core.anilist.api

import android.util.Log
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedDeque

private const val TAG = "AniListRateLimiter"

/**
 * Rate limiter for AniList API calls.
 *
 * AniList's rate limit is 90 requests per minute (with a burst allowance).
 * To be safe, we enforce a **maximum of 80 requests per minute**.
 *
 * **Dynamic speed adjustment:**
 * - For the first 40 requests in a minute window, calls proceed at normal speed
 *   (no artificial delay).
 * - After 40 requests, we begin adding incremental delays to spread the
 *   remaining 40 requests across the rest of the minute.
 * - If we approach the 80-request cap, delays increase to ensure we never
 *   exceed the limit.
 *
 * This allows fast processing for small backups (≤40 anime) while preventing
 * rate-limit errors for larger backups.
 *
 * **Thread-safe:** Uses atomic counters + a concurrent deque of timestamps.
 */
class AniListRateLimiter {

    /** Maximum requests per minute (safety margin below AniList's 90/min). */
    private val maxPerMinute = 80

    /** Threshold for "fast mode" — below this, no artificial delay. */
    private val fastModeThreshold = 40

    /** Sliding window of request timestamps (epoch ms). */
    private val requestTimestamps = ConcurrentLinkedDeque<Long>()

    /** Total requests made (for logging). */
    private val totalRequests = AtomicInteger(0)

    /**
     * Call this before every AniList API request.
     * Blocks (suspends) until it's safe to make the next request.
     */
    suspend fun acquire() {
        val now = System.currentTimeMillis()
        val minuteAgo = now - 60_000L

        // Purge timestamps older than 1 minute
        while (true) {
            val oldest = requestTimestamps.peekFirst() ?: break
            if (oldest < minuteAgo) {
                requestTimestamps.pollFirst()
            } else {
                break
            }
        }

        val countInLastMinute = requestTimestamps.size

        if (countInLastMinute >= maxPerMinute) {
            // We've hit the cap — wait until the oldest request exits the window
            val oldest = requestTimestamps.peekFirst() ?: now
            val waitMs = (oldest + 60_000L) - now
            if (waitMs > 0) {
                Log.w(TAG, "Rate limit reached ($countInLastMinute/min). Waiting ${waitMs}ms.")
                delay(waitMs)
            }
        } else if (countInLastMinute >= fastModeThreshold) {
            // Slow mode — spread the remaining requests across the rest of the minute
            val remainingSlots = maxPerMinute - countInLastMinute
            val timeRemainingInMinute = 60_000L - (now - (requestTimestamps.peekFirst() ?: now))
            if (remainingSlots > 0 && timeRemainingInMinute > 0) {
                val delayPerRequest = (timeRemainingInMinute / remainingSlots).coerceAtLeast(200L)
                // Add a small random jitter to avoid thundering herd
                val jitter = (0..100L).random()
                delay(delayPerRequest + jitter)
            }
        }

        // Record this request
        requestTimestamps.addLast(System.currentTimeMillis())
        val total = totalRequests.incrementAndGet()
        if (total % 10 == 0) {
            Log.d(TAG, "AniList API calls: $total total, ${requestTimestamps.size} in last minute")
        }
    }

    /** Returns the current count of requests in the last minute. */
    fun currentRate(): Int = requestTimestamps.size

    /** Returns the total requests made since this limiter was created. */
    fun totalRequests(): Int = totalRequests.get()
}
