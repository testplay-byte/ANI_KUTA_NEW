package eu.kanade.tachiyomi.network

/**
 * Listener for download progress tracking.
 *
 * **CRITICAL — binary compatibility (ADR-029):**
 * The method MUST be named `update` (not `onProgress`). Extensions compiled
 * against the reference call `progressListener.update(bytesRead, contentLength, done)`.
 * If the method has a different name, extensions throw `NoSuchMethodError`.
 *
 * Ported from the Aniyomi reference's `core/common/.../network/ProgressListener.kt`.
 */
interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}
