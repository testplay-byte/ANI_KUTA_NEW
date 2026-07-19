package app.anikuta.ui.detail.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Visual treatment applied to watched episode cards.
 *
 * Used by [Modifier.watchedEpisodeEffect] to determine which combination of
 * effects to apply. The user configures this in Settings → Episode Display →
 * "Watched episode appearance".
 */
enum class WatchedEpisodeAppearance {
    /** No visual treatment — watched episodes look the same as unwatched. */
    NONE,

    /** Desaturate the entire card (text, icons, thumbnail) to black & white. */
    GRAYSCALE,

    /** Apply a subtle blur to the entire card. */
    BLUR,

    /** Apply both grayscale AND blur (maximum visual distinction). */
    BOTH;

    companion object {
        /**
         * Parse a preference string value into a [WatchedEpisodeAppearance].
         * Falls back to [GRAYSCALE] for unrecognized values (backward compatible
         * with the previous default behavior).
         */
        fun fromPref(value: String): WatchedEpisodeAppearance = when (value) {
            "none" -> NONE
            "grayscale" -> GRAYSCALE
            "blur" -> BLUR
            "both" -> BOTH
            else -> GRAYSCALE
        }
    }

    /** Whether the grayscale (desaturation) effect should be applied. */
    val appliesGrayscale: Boolean get() = this == GRAYSCALE || this == BOTH

    /** Whether the blur effect should be applied. */
    val appliesBlur: Boolean get() = this == BLUR || this == BOTH
}

/**
 * Applies the configured visual treatment to a watched episode card.
 *
 * Combines grayscale (via [RenderEffect] at the GPU level) and/or blur
 * (via [Modifier.blur]) based on the [appearance] parameter.
 *
 * ## Why RenderEffect for grayscale
 *
 * Previous attempts used `drawWithContent` + `ColorFilter.colorMatrix` on a
 * `Paint` object. This only affected rasterised draw operations (images) and
 * did NOT affect Compose's text rendering pipeline — themed text colors
 * remained unchanged, giving a "half-grayscale" appearance.
 *
 * The fix uses [RenderEffect.createColorFilterEffect] via [Modifier.graphicsLayer],
 * which applies the color filter at the **GPU render-effect level**. This
 * intercepts the entire rendered output of the layer (text, icons, shapes,
 * images) and desaturates it uniformly.
 *
 * ## Platform support
 *
 * - **Grayscale**: Android 12+ (API 31+) via [RenderEffect]. Below API 31,
 *   only alpha dimming is applied.
 * - **Blur**: Android 12+ (API 31+) via [Modifier.blur] (which also uses
 *   [RenderEffect] internally). Below API 31, blur is a no-op (Modifier.blur
 *   handles this gracefully).
 *
 * @param appearance The visual treatment to apply (none/grayscale/blur/both).
 * @param alpha      Alpha multiplier for grayscale mode (default 0.55).
 * @param blurRadiusDp Blur radius in dp for blur mode (default 2dp — subtle).
 */
fun Modifier.watchedEpisodeEffect(
    appearance: WatchedEpisodeAppearance,
    alpha: Float = 0.55f,
    blurRadiusDp: Float = 2f,
): Modifier {
    if (appearance == WatchedEpisodeAppearance.NONE) return this

    var result = this

    // --- Grayscale (GPU render effect) ---
    if (appearance.appliesGrayscale) {
        result = result.graphicsLayer {
            this.alpha = alpha
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                this.renderEffect = RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(matrix)
                ).asComposeRenderEffect()
            }
        }
    }

    // --- Blur ---
    // Note: Modifier.blur uses RenderEffect.createBlurEffect internally on
    // Android 12+. On older versions it renders a translucency fallback.
    // We use a reasonable radius (default 2dp) that's subtle but visible.
    if (appearance.appliesBlur && blurRadiusDp > 0f) {
        result = result.blur(blurRadiusDp.dp)
    }

    return result
}

/**
 * Legacy convenience function — applies grayscale only.
 *
 * Kept for backward compatibility. New code should use [watchedEpisodeEffect]
 * with [WatchedEpisodeAppearance.GRAYSCALE] instead.
 *
 * @param enabled When `true`, applies grayscale + alpha dimming.
 * @param alpha   The alpha multiplier applied to the entire content.
 */
fun Modifier.grayscaleIfSeen(
    enabled: Boolean,
    alpha: Float = 0.55f,
): Modifier = if (!enabled) {
    this
} else {
    watchedEpisodeEffect(
        appearance = WatchedEpisodeAppearance.GRAYSCALE,
        alpha = alpha,
    )
}
