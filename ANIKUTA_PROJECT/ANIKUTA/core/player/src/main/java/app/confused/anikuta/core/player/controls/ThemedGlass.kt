package app.confused.anikuta.core.player.controls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Shared "themed dark glass" color helper for the player controls overlays.
 *
 * Introduced 2026-07-28 per owner feedback:
 *   "Make it a little bit less dark and make it a bit of a theme color mixed
 *    with things... I was hoping for it to be a themed color but in a darker
 *    tone."
 *
 * Used by [FullscreenControls] (center play/pause + ±10s buttons) and
 * [MinimizedControls] (center play/pause) so the two modes share the exact
 * same treatment — keeping the visual language consistent across the
 * MINIMIZED ↔ FULLSCREEN transition.
 *
 * NOTE on blur: a `Modifier.frostedGlassBlur()` helper was previously here
 * and applied to the center controls. It was REMOVED 2026-07-28 per owner
 * feedback: "remove the blur completely... the corners look sharp and bad".
 * `Modifier.blur()` on a Surface with RoundedCornerShape blurs the entire
 * rectangular bounds, which softened the rounded corners into a muddy
 * rectangular halo. The themed-dark color alone looks clean and crisp, so
 * blur is gone (not just disabled).
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
