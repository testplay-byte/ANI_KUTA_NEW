package app.confused.anikuta.core.designsystem.theme

import android.graphics.Bitmap
import android.util.Log
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

/**
 * Utility for extracting dominant colors from anime cover images.
 *
 * This module provides a suspend function to download a cover image,
 * convert it to a Bitmap, and extract the dominant color using the
 * Palette API (via [extractDominantColor]).
 *
 * Designed for future integration with the extension-only anime details page,
 * where cover color is not available from AniList's color field and must
 * be extracted from the cover bitmap.
 *
 * Usage (future):
 *   val coverColor = PaletteExtraction.extractCoverColor(coverUrl) // Returns ARGB Int or null
 */
object PaletteExtraction {

    private const val TAG = "PaletteExtraction"
    private const val TIMEOUT_MS = 5_000L

    /**
     * Downloads a cover image from [coverUrl], extracts the dominant color,
     * and returns it as an ARGB int.
     *
     * @param coverUrl The URL of the cover image to extract color from.
     * @param imageLoader The Coil ImageLoader to use for downloading.
     * @param context Android context for the image request.
     * @return The dominant color as an ARGB int, or null if extraction fails.
     */
    suspend fun extractCoverColor(
        coverUrl: String,
        imageLoader: ImageLoader,
        context: android.content.Context,
    ): Int? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false) // Palette needs a software bitmap
                    .build()

                val result = imageLoader.execute(request)
                val bitmap = result.image?.toBitmap()

                if (bitmap == null) {
                    Log.w(TAG, "Failed to load bitmap from $coverUrl")
                    return@withTimeout null
                }

                val color = extractDominantColor(bitmap)
                bitmap.recycle()

                if (color == 0) {
                    Log.w(TAG, "Palette extraction returned 0 for $coverUrl")
                    null
                } else {
                    color
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cover color extraction failed for $coverUrl", e)
            null
        }
    }
}
