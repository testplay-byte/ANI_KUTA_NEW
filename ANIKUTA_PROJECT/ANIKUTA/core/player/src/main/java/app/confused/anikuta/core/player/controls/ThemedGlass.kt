package app.confused.anikuta.core.player.controls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared "themed dark glass" helpers for the player controls overlays.
 *
 * Introduced 2026-07-28 per owner feedback:
 *   "Make it a little bit less dark and make it a bit of a theme color mixed
 *    with things... I was hoping for it to be a themed color but in a darker
 *    tone. Maybe [blur] is an issue but still I don't want the dark color."
 *
 * Used by [FullscreenControls] (center play/pause + ±10s buttons) and
 * [MinimizedControls] (center play/pause) so the two modes share the exact
 * same glass treatment — keeping the visual language consistent across the
 * MINIMIZED ↔ FULLSCREEN transition.
 */

/**
 * Returns a "themed dark glass" color: the user's accent/primary color shifted
 * ~55% toward black, with ~62% opacity for translucency.
 *
 * This replaces the previous `Color.Black.copy(alpha = 0.5f)` (pure black,
 * 50% opaque) — which was too dark and had no theme presence.
 *
 * The color is recomputed on every recomposition of the calling composable;
 * it's cheap (a single `lerp` + `copy`).
 */
@Composable
internal fun themedDarkGlassColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    // Lerp primary 55% toward black → a deep, rich version of the accent.
    val darkened = lerp(primary, Color.Black, 0.55f)
    // 62% opacity: translucent enough to show video through it, opaque enough
    // to keep white icons/text legible.
    return darkened.copy(alpha = 0.62f)
}

/**
 * Applies a frosted-glass blur to the modifier.
 *
 * `Modifier.blur()` requires API 31+ (Android 12). Below that, it silently
 * no-ops — the surface will just show its solid `themedDarkGlassColor()`,
 * which still looks good (the dark themed tint provides the "glass" feel
 * even without true convolution blur).
 *
 * The default blur radius (8.dp) is intentionally subtle — large enough to
 * soften the video showing through, small enough to keep the button's outline
 * crisp.
 */
internal fun Modifier.frostedGlassBlur(radiusDp: Dp = 8.dp): Modifier {
    return this.blur(radiusDp)
}
