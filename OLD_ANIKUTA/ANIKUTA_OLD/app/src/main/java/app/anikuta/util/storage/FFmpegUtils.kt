package app.anikuta.util.storage

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.hippo.unifile.UniFile

/**
 * Ported from aniyomi's FFmpegUtils. Converts SAF URIs to FFmpeg-compatible
 * parameter strings so FFmpegKit can read/write files in the user-selected
 * SAF folder.
 *
 * For content:// URIs: uses FFmpegKitConfig.getSafParameter() which returns
 * a special saf:// protocol string that FFmpegKit understands.
 * For file:// URIs: returns the plain file path.
 */
fun Uri.toFFmpegString(context: Context): String {
    return if (this.scheme == "content") {
        FFmpegKitConfig.getSafParameter(context, this, "rw")
    } else {
        this.path ?: this.toString()
    }.replace("\"", "\\\"")
}

fun UniFile.toFFmpegString(context: Context? = null): String {
    return if (context != null && this.uri.scheme == "content") {
        FFmpegKitConfig.getSafParameter(context, this.uri, "rw")
    } else {
        this.filePath ?: this.uri.toString()
    }.replace("\"", "\\\"")
}
