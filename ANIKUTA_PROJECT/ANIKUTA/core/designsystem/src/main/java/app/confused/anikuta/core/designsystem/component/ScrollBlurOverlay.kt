package app.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable scroll-driven gradient scrim overlay.
 *
 * Sits at the **bottom edge of a pinned header** + extends slightly into the
 * scrollable content below. When the user scrolls content underneath, a gradient
 * (solid background color at the top → fully transparent at the bottom) fades in
 * smoothly with rounded bottom corners. When scrolled back to the top, the
 * effect fades out completely.
 *
 * # How it works (the "frosted glass" illusion)
 *
 * This does NOT use `RenderEffect` to blur the scrolling content — that approach
 * is extremely expensive in Compose (requires capturing content as a bitmap each
 * frame) and produces visual artifacts (muddy halos, GPU stalls when toggling).
 *
 * Instead, it uses a **gradient scrim** whose color matches the screen's background.
 * As scrolling content passes beneath the gradient, the solid-to-transparent fade
 * creates an optical illusion of frosted glass — the content appears to "dissolve"
 * into the background. This is the same technique used by iOS navigation bars,
 * Telegram, and Material 3 top app bars. It's GPU-cheap (one `drawRect` per frame)
 * and never causes recomposition.
 *
 * # Visual design
 *
 * - **Top edge:** sharp (no rounding) — blends with the header's background.
 * - **Bottom corners:** rounded (`cornerRadius` radius) — clearly visible curves.
 * - **Gradient:** 6-stop vertical gradient (solid → transparent).
 * - **Overlap:** the overlay's top edge is pulled up 2dp via `graphicsLayer.translationY`
 *   (draw-phase only — no layout jitter) to eliminate any visible seam.
 *
 * # Performance
 *
 * - The scroll-driven alpha is applied via `Modifier.graphicsLayer { alpha = ... }`.
 *   The `graphicsLayer` lambda is a **deferred read** — it executes during the
 *   draw phase, NOT during composition. Reading `scrollOffset()` inside it does
 *   NOT trigger recomposition.
 * - `drawBehind` draws the gradient directly into the composable's draw cache —
 *   no extra layout passes.
 * - No `RenderEffect` — zero GPU pipeline stalls.
 *
 * # Usage
 *
 * ```kotlin
 * Box(modifier = Modifier.fillMaxSize()) {
 *     LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) { ... }
 *     ScrollBlurOverlay(
 *         scrollOffset = {
 *             // CRITICAL: when firstVisibleItemIndex > 0, return MAX_VALUE so the
 *             // overlay stays at full opacity. firstVisibleItemScrollOffset resets
 *             // to 0 when a new item becomes the first visible item — without this
 *             // check, the overlay would flicker (disappear → reappear) on every
 *             // item boundary crossing.
 *             if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
 *             else listState.firstVisibleItemScrollOffset.toFloat()
 *         },
 *         backgroundColor = MaterialTheme.colorScheme.background,
 *         modifier = Modifier.align(Alignment.TopCenter),
 *     )
 * }
 * ```
 *
 * @param scrollOffset a lambda returning the raw pixel scroll offset (0 = top).
 * @param backgroundColor the screen's background color (must match the header).
 * @param modifier the positioning modifier (e.g. `Modifier.align(Alignment.TopCenter)`).
 * @param blurHeight height of the overlay element (default 36dp).
 * @param cornerRadius bottom corner radius (default 24dp).
 * @param enabled when `false`, renders nothing (early return).
 */
@Composable
fun ScrollBlurOverlay(
    scrollOffset: () -> Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    blurHeight: Dp = 36.dp,
    cornerRadius: Dp = 24.dp,
    blurRadius: Float = 25f, // Kept for API compat; unused (no RenderEffect)
    enabled: Boolean = true,
) {
    if (!enabled) return

    val density = LocalDensity.current
    // Fade distance: ~24dp of scroll for the smoothstep transition.
    // Short distance so the blur appears quickly on slight scroll, but the
    // smoothstep curve still ensures a smooth fade (no sudden pop).
    val fadeDistancePx = with(density) { 24.dp.toPx() }
    // 2dp overlap in pixels (draw-phase translationY — no layout jitter).
    val overlapPx = with(density) { (-2).dp.toPx() }

    // The overlay shape: sharp top, rounded bottom corners.
    val shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = cornerRadius,
        bottomEnd = cornerRadius,
    )

    // Gradient stops: solid background → transparent, with smooth intermediate stops.
    // Using fractional positions for a natural dissolve curve.
    val gradientColors = listOf(
        backgroundColor,                        // 0.0 — solid (hidden behind header)
        backgroundColor.copy(alpha = 0.92f),   // 0.15
        backgroundColor.copy(alpha = 0.70f),   // 0.35
        backgroundColor.copy(alpha = 0.42f),   // 0.55
        backgroundColor.copy(alpha = 0.18f),   // 0.75
        backgroundColor.copy(alpha = 0.05f),   // 0.90
        Color.Transparent,                     // 1.0 — fully transparent
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(blurHeight)
            // Clip to the rounded-bottom-corners shape FIRST (layout phase).
            .clip(shape)
            // ALL scroll-driven visuals in a SINGLE graphicsLayer block (draw phase).
            // No conditional RenderEffect toggling — just alpha + translationY.
            .graphicsLayer {
                val raw = scrollOffset()
                val t = (raw / fadeDistancePx).coerceIn(0f, 1f)
                // Smoothstep: t² × (3 - 2t) — imperceptible onset, smooth full opacity.
                val smoothed = t * t * (3 - 2 * t)
                this.alpha = smoothed
                // 2dp overlap — draw-phase only, no layout pass.
                this.translationY = overlapPx
            }
            // Draw the gradient scrim (cached — only re-drawn if size changes).
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            },
    )
}
