package app.confused.anikuta.core.designsystem.theme

import android.graphics.Bitmap
import android.util.Log

/**
 * Utility for extracting dominant colors from anime cover images.
 *
 * This module provides functions to extract the dominant color from a Bitmap
 * using the Palette API (via [extractDominantColor]).
 *
 * Designed for future integration with the extension-only anime details page,
 * where cover color is not available from AniList's color field and must
 * be extracted from the cover bitmap.
 *
 * **Note:** The URL-based [extractCoverColor] function is a skeleton — the
 * actual image download must be performed by the caller (e.g., via Coil's
 * ImageLoader in a feature module that has Coil as a dependency). This module
 * only handles the color extraction from a Bitmap.
 *
 * Usage (future):
 *   val bitmap = imageLoader.execute(request).image?.toBitmap()
 *   val coverColor = PaletteExtraction.extractFromBitmap(bitmap) // Returns ARGB Int or null
 */
object PaletteExtraction {

    private const val TAG = "PaletteExtraction"

    /**
     * Extracts the dominant color from a [Bitmap] and returns it as an ARGB int.
     *
     * This is the core extraction function — it takes a pre-loaded Bitmap and
     * uses the Palette API to find the dominant color. The caller is responsible
     * for downloading/loading the bitmap (e.g., via Coil's ImageLoader).
     *
     * @param bitmap The cover image bitmap to extract color from.
     * @return The dominant color as an ARGB int, or null if extraction fails.
     */
    fun extractFromBitmap(bitmap: Bitmap?): Int? {
        if (bitmap == null) return null
        return try {
            val color = extractDominantColor(bitmap)
            if (color == 0) {
                Log.w(TAG, "Palette extraction returned 0")
                null
            } else {
                color
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cover color extraction failed", e)
            null
        }
    }

    /**
     * Downloads a cover image from [coverUrl], extracts the dominant color,
     * and returns it as an ARGB int.
     *
     * **Skeleton:** This function is a placeholder for future implementation.
     * The actual image download should be done by the caller using Coil's
     * ImageLoader (available in feature modules). This function signature is
     * documented here for future reference.
     *
     * Future implementation:
     * ```
     * suspend fun extractCoverColor(coverUrl: String, imageLoader: ImageLoader, context: Context): Int? {
     *     val request = ImageRequest.Builder(context).data(coverUrl).allowHardware(false).build()
     *     val result = imageLoader.execute(request)
     *     val bitmap = result.image?.toBitmap()
     *     return extractFromBitmap(bitmap)
     * }
     * ```
     *
     * @param coverUrl The URL of the cover image to extract color from.
     * @return The dominant color as an ARGB int, or null if extraction fails.
     */
    suspend fun extractCoverColor(coverUrl: String): Int? {
        // TODO(owner): Implement using Coil ImageLoader when this module is
        // integrated into a feature that has Coil as a dependency. For now,
        // this is a documented skeleton — the caller should use
        // [extractFromBitmap] with a pre-loaded bitmap.
        Log.w(TAG, "extractCoverColor is not yet implemented — use extractFromBitmap instead")
        return null
    }
}
