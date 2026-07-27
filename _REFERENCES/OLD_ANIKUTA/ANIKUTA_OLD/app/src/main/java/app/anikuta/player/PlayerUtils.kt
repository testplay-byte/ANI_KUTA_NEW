package app.anikuta.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import `is`.xyz.mpv.Utils

/**
 * Ported from aniyomi's PlayerUtils. Resolves content:// URIs (from SAF
 * downloads) to file descriptor paths that MPV can play.
 *
 * MPV's `loadfile` command takes a string URL. For HTTP/HLS URLs this works
 * directly. For downloaded files stored via SAF (content:// URIs), we need
 * to open a file descriptor and pass it as `fd://<fd>` or let
 * `Utils.findRealPath()` convert it to a real path.
 *
 * This is the key enabler for offline playback of downloaded episodes.
 */
private const val TAG = "PlayerUtils"

fun Uri.openContentFd(context: Context): String? {
    return try {
        val pfd = context.contentResolver.openFileDescriptor(this, "r")
        if (pfd == null) {
            Log.e(TAG, "openFileDescriptor returned null for $this")
            return null
        }
        val fd = pfd.detachFd()
        Log.d(TAG, "Got fd: $fd for URI: $this")
        val realPath = Utils.findRealPath(fd)
        if (realPath != null) {
            Log.d(TAG, "findRealPath returned: $realPath")
            // Close the fd since we have a real path
            ParcelFileDescriptor.adoptFd(fd).close()
            realPath
        } else {
            Log.d(TAG, "findRealPath returned null, using fd://$fd")
            "fd://$fd"
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open content FD for $this", e)
        null
    } catch (e: Error) {
        // Catch NoClassDefFoundError etc. that might come from Utils
        Log.e(TAG, "Error opening content FD for $this", e)
        null
    }
}

fun Uri.resolveUri(context: Context): String? {
    val filepath = when (scheme) {
        "file" -> path
        "content" -> openContentFd(context)
        "data" -> "data://$schemeSpecificPart"
        in Utils.PROTOCOLS -> toString()
        else -> toString()
    }

    if (filepath == null) {
        Log.e(TAG, "Unknown scheme: $scheme")
    }
    return filepath
}

/**
 * Resolve any URL string for MPV playback. If it's a content:// URI,
 * resolves it via [resolveUri]. Otherwise returns as-is.
 */
fun resolveUrlForMpv(url: String, context: Context): String {
    return if (url.startsWith("content://")) {
        Uri.parse(url).resolveUri(context) ?: url
    } else {
        url
    }
}
