package app.confused.anikuta.feature.setupwizard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// WizardPalette — a small data class used ONLY by the animated Canvas visuals.
//
// The wizard's SCREEN UI uses `MaterialTheme.colorScheme.*` directly (the real
// ANIKUTA design system, reactively driven by `ThemePreferences`). The visuals
// below, however, need a handful of color "tones" (primary, primaryContainer,
// surface1..5, background) that don't all have exact M3 equivalents. This data
// class carries those tones; build one with [wizardPaletteFromMaterialTheme]
// inside a composable and pass it to a visual.
// ─────────────────────────────────────────────────────────────────────────────

data class WizardPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val surface5: Color,
)

/** Mixes two colors at `ratio` (0 = base, 1 = tint). */
private fun colorMix(base: Color, tint: Color, ratio: Float): Color = Color(
    red = base.red * (1 - ratio) + tint.red * ratio,
    green = base.green * (1 - ratio) + tint.green * ratio,
    blue = base.blue * (1 - ratio) + tint.blue * ratio,
    alpha = 1f,
)

/**
 * Builds a [WizardPalette] from the current [MaterialTheme.colorScheme].
 *
 * The wizard uses the REAL ANIKUTA theme (`AnikutaTheme` from `:core:designsystem`),
 * so the colors here reflect the user's chosen accent + theme mode + palette
 * mode. The intermediate tones (surface3, surface4, surface5) are derived as
 * blends between the M3 surface roles — this preserves the layered look the
 * visuals were designed with, without introducing a separate color system.
 */
@Composable
fun wizardPaletteFromMaterialTheme(): WizardPalette {
    val cs = MaterialTheme.colorScheme
    return WizardPalette(
        primary = cs.primary,
        onPrimary = cs.onPrimary,
        primaryContainer = cs.primaryContainer,
        onPrimaryContainer = cs.onPrimaryContainer,
        background = cs.background,
        surface1 = cs.surface,
        surface2 = cs.surfaceVariant,
        surface3 = colorMix(cs.surface, cs.surfaceVariant, 0.5f),
        surface4 = cs.outlineVariant,
        surface5 = cs.outline,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.radialGlow(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float = 0.25f) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = Offset(cx, cy),
            radius = radius,
        ),
        center = Offset(cx, cy),
        radius = radius,
    )
}

private fun DrawScope.rr(color: Color, x: Float, y: Float, w: Float, h: Float, cr: Float, stroke: Float = 0f) {
    val path = Path().apply { addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(cr, cr))) }
    if (stroke > 0f) drawPath(path, color, style = Stroke(stroke)) else drawPath(path, color)
}

private fun DrawScope.rrGradient(brush: Brush, x: Float, y: Float, w: Float, h: Float, cr: Float) {
    drawRoundRect(brush, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(cr, cr))
}

// ─────────────────────────────────────────────────────────────────────────────
// WELCOME — glowing app logo with orbiting accent dots + breathing pulse
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WelcomeVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val pulseT = rememberInfiniteTransition(label = "wv-pulse")
    val scale by pulseT.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "wv-s")
    val orbitT = rememberInfiniteTransition(label = "wv-orbit")
    val angle by orbitT.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), "wv-a")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = size.height / 2f

        radialGlow(cx, cy, 70f * u, palette.primary, 0.18f + 0.10f * (scale - 0.96f) / 0.08f)

        for (i in 0 until 3) {
            val a = (angle + i * 120f) * PI.toFloat() / 180f
            val orbR = 80f * u
            drawCircle(palette.primary.copy(alpha = 0.7f), 3.5f * u, Offset(cx + orbR * cos(a), cy + orbR * sin(a)))
        }

        val sz = 84f * u * scale
        rrGradient(Brush.verticalGradient(listOf(palette.primary, palette.primary.copy(alpha = 0.85f))), cx - sz / 2, cy - sz / 2, sz, sz, 22f * u)
        val p = Path().apply {
            moveTo(cx - 10f * u * scale, cy - 20f * u * scale)
            lineTo(cx - 10f * u * scale, cy + 20f * u * scale)
            lineTo(cx + 22f * u * scale, cy)
            close()
        }
        drawPath(p, palette.onPrimary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOLDER — clean modern file-manager folder icon with floating anime cards +
// scanning beam. Redesigned to look like a proper folder (rounded-rectangle
// body + tab at top-left + accent stripe across the top), NOT a bottle.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FolderVisual(palette: WizardPalette, selected: Boolean = false, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "fv")
    val float by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fv-float")
    val cardDrop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Restart), "fv-drop")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), "fv-scan")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val fy = float * u

        // ── Folder geometry (modern file-manager folder shape) ──
        // Body: a large rounded rectangle centered on (cx, cy).
        // Tab: a smaller rounded rectangle sitting on top-left of the body.
        // Accent stripe: a thin horizontal strip across the top of the body
        //   (the "active folder" highlight you see in file managers).
        val bodyW = 130f * u
        val bodyH = 96f * u
        val bodyLeft = cx - bodyW / 2f
        val bodyTop = cy - bodyH / 2f + 14f * u + fy // shifted down to leave room for the tab
        val bodyCorner = 14f * u

        val tabW = 48f * u
        val tabH = 18f * u
        val tabLeft = bodyLeft + 4f * u
        val tabTop = bodyTop - tabH + 2f * u // overlaps the body slightly
        val tabCorner = 8f * u

        // Soft glow behind the folder
        radialGlow(cx, cy + 10f * u + fy, 90f * u, palette.primary, 0.16f)

        // ── Folder tab (top-left) ──
        // Drawn first so the body sits on top of its bottom edge (covers the
        // seam). Same fill color as the body so it reads as one shape.
        rrGradient(
            Brush.verticalGradient(listOf(palette.surface5, palette.surface4)),
            tabLeft, tabTop, tabW, tabH, tabCorner,
        )

        // ── Folder body (rounded rectangle, filled with surface4) ──
        rrGradient(
            Brush.verticalGradient(listOf(palette.surface4, palette.surface5)),
            bodyLeft, bodyTop, bodyW, bodyH, bodyCorner,
        )
        // Subtle outline for the modern "card" look
        rr(palette.surface5.copy(alpha = 0.6f), bodyLeft, bodyTop, bodyW, bodyH, bodyCorner, 1.2f * u)

        // ── Accent stripe across the top of the body ──
        // This is the primary-colored highlight that makes the folder look
        // "active" / themed (like a colored folder tab in a file manager).
        val stripeH = 10f * u
        val stripeLeft = bodyLeft + 1.2f * u
        val stripeTop = bodyTop + 1.2f * u
        val stripeW = bodyW - 2.4f * u
        // Drawn as a slightly-inset rounded rectangle with the same corner
        // radius as the body — sits flush inside the body's top edge.
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(palette.primary, palette.primary.copy(alpha = 0.75f)),
            ),
            topLeft = Offset(stripeLeft, stripeTop),
            size = Size(stripeW, stripeH),
            cornerRadius = CornerRadius(bodyCorner - 2f * u, bodyCorner - 2f * u),
        )

        // ── Inner content lines (suggest files inside the folder) ──
        // Three thin horizontal lines, fading from top to bottom.
        for (i in 0 until 3) {
            rr(
                palette.primary.copy(alpha = 0.22f - i * 0.04f),
                bodyLeft + 18f * u,
                bodyTop + stripeH + 14f * u + i * 12f * u,
                bodyW - 36f * u,
                3f * u,
                1.5f * u,
            )
        }

        // ── Anime cards falling into the folder ──
        // 3 cards, staggered, animate from above into the folder's top opening.
        // They fade out as they enter the folder body (so it looks like they
        // "land" inside rather than covering the folder).
        for (i in 0 until 3) {
            val phase = (cardDrop + i * 0.33f) % 1f
            val cardX = cx + (i - 1) * 22f * u
            val startY = cy - 75f * u + fy
            val endY = bodyTop + 14f * u
            val cardY = startY + (endY - startY) * phase
            // Fade out as the card enters the folder body (last 25% of the drop).
            val alpha = when {
                phase < 0.1f -> phase * 10f
                phase > 0.75f -> maxOf(0f, 1f - (phase - 0.75f) * 4f)
                else -> 1f
            }
            val cw = 26f * u
            val ch = 36f * u

            // Card shadow / outline
            rr(palette.surface5.copy(alpha = 0.5f * alpha), cardX - cw / 2, cardY, cw, ch, 4f * u)
            // Card body
            rr(palette.surface2.copy(alpha = alpha), cardX - cw / 2, cardY, cw, ch, 4f * u)
            // Card accent strip (top 35%)
            rr(
                palette.primary.copy(alpha = 0.6f * alpha),
                cardX - cw / 2, cardY, cw, ch * 0.38f, 4f * u,
            )
            // Two small content lines on the card
            for (j in 0 until 2) {
                drawRect(
                    palette.primary.copy(alpha = 0.25f * alpha),
                    Offset(cardX - cw / 2 + 4f * u, cardY + ch * 0.5f + j * 5f * u),
                    Size(cw - 8f * u, 2f * u),
                )
            }
        }

        // ── Scanning beam (kept from the original) ──
        // A horizontal line that sweeps top-to-bottom across the folder body.
        val scanY = bodyTop + 4f * u + scan * (bodyH - 8f * u)
        drawLine(
            palette.primary.copy(alpha = 0.55f),
            Offset(bodyLeft + 6f * u, scanY),
            Offset(bodyLeft + bodyW - 6f * u, scanY),
            2.2f * u,
            StrokeCap.Round,
        )
        // Soft glow around the scan line for a "scanning" effect
        radialGlow(cx, scanY, 18f * u, palette.primary, 0.22f)

        // ── Check badge (kept from the original) ──
        // A circular primary-colored badge with a white checkmark at the
        // top-right corner of the folder body. Only drawn when selected.
        if (selected) {
            val bx = bodyLeft + bodyW - 6f * u
            val by = bodyTop - 6f * u
            radialGlow(bx, by, 28f * u, palette.primary, 0.3f)
            drawCircle(palette.primary, 20f * u, Offset(bx, by))
            val cp = Path().apply {
                moveTo(bx - 8f * u, by)
                lineTo(bx - 2f * u, by + 6f * u)
                lineTo(bx + 8f * u, by - 6f * u)
            }
            drawPath(
                cp,
                palette.background,
                style = Stroke(3.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHIELD — biometric scanning badge with pulse rings + drawing checkmark
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShieldVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val scanT = rememberInfiniteTransition(label = "sv-scan")
    val scan by scanT.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), "sv-s")
    val pulseT = rememberInfiniteTransition(label = "sv-pulse")
    val pulse by pulseT.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), "sv-p")
    val floatT = rememberInfiniteTransition(label = "sv-float")
    val float by floatT.animateFloat(0f, -4f, infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "sv-f")
    val checkT = rememberInfiniteTransition(label = "sv-check")
    val check by checkT.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, delayMillis = 1000, easing = FastOutSlowInEasing), RepeatMode.Restart), "sv-c")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = (100f + float) * u
        val r = 56f * u

        // Pulse rings
        for (i in 0 until 3) {
            val phase = (pulse + i * 0.33f) % 1f
            drawCircle(palette.primary.copy(alpha = (1f - phase) * 0.15f), r + 30f * u * phase, Offset(cx, cy), style = Stroke(2f * u))
        }

        radialGlow(cx, cy, r * 1.3f, palette.primary, 0.16f)

        // Badge circles
        drawCircle(palette.surface3, r, Offset(cx, cy))
        drawCircle(palette.primary, r, Offset(cx, cy), style = Stroke(3f * u))
        drawCircle(palette.surface2, r - 6f * u, Offset(cx, cy))

        // Grid lines (appear as scan passes)
        val gridCount = 8
        for (i in 0..gridCount) {
            val gridY = cy - r + 6f * u + i * (r * 2 - 12f * u) / gridCount
            val progress = (scan * (r * 2) - (gridY - cy + r)) / 20f
            if (progress in 0f..1f) {
                drawLine(palette.primary.copy(alpha = 0.15f * progress), Offset(cx - r + 12f * u, gridY), Offset(cx + r - 12f * u, gridY), 1f * u)
            }
        }

        // Scan line
        val scanY = cy - r + 6f * u + scan * (r * 2 - 12f * u)
        drawLine(palette.primary, Offset(cx - r + 10f * u, scanY), Offset(cx + r - 10f * u, scanY), 2.5f * u, StrokeCap.Round)
        radialGlow(cx, scanY, 16f * u, palette.primary, 0.2f)

        // Drawing checkmark
        if (check > 0f) {
            val p1 = Offset(cx - r * 0.2f, cy)
            val p2 = Offset(cx - r * 0.05f, cy + r * 0.2f)
            val p3 = Offset(cx + r * 0.25f, cy - r * 0.2f)
            if (check <= 0.5f) {
                val k = check * 2f
                drawLine(palette.primary, p1, Offset(p1.x + (p2.x - p1.x) * k, p1.y + (p2.y - p1.y) * k), 5f * u, StrokeCap.Round)
            } else {
                drawLine(palette.primary, p1, p2, 5f * u, StrokeCap.Round)
                val k = (check - 0.5f) * 2f
                drawLine(palette.primary, p2, Offset(p2.x + (p3.x - p2.x) * k, p2.y + (p3.y - p2.y) * k), 5f * u, StrokeCap.Round)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESTORE — file card with rotating circular restore arrow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RestoreVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val spinT = rememberInfiniteTransition(label = "rv-spin")
    val spin by spinT.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), "rv-s")
    val floatT = rememberInfiniteTransition(label = "rv-float")
    val float by floatT.animateFloat(0f, -4f, infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "rv-f")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = (100f + float) * u

        radialGlow(cx, cy, 70f * u, palette.primary, 0.16f)

        // File card
        val fw = 76f * u
        val fh = 96f * u
        rrGradient(Brush.verticalGradient(listOf(palette.surface2, palette.surface4)), cx - fw / 2, cy - fh / 2, fw, fh, fw * 0.14f)
        val fold = Path().apply { moveTo(cx + fw / 2 - fw * 0.24f, cy - fh / 2); lineTo(cx + fw / 2, cy - fh / 2 + fw * 0.24f); lineTo(cx + fw / 2 - fw * 0.24f, cy - fh / 2 + fw * 0.24f); close() }
        drawPath(fold, palette.primary.copy(alpha = 0.45f))
        for (i in 0 until 3) {
            rr(palette.primary.copy(alpha = 0.3f + i * 0.05f), cx - fw * 0.30f, cy - fh * 0.08f + i * fh * 0.16f, fw * 0.60f, fh * 0.035f, 2f * u)
        }

        // Rotating arrow
        val ringR = 72f * u
        val startAngle = -90f + spin
        drawArc(palette.primary, startAngle, 85f, false, Offset(cx - ringR, cy - ringR), Size(ringR * 2, ringR * 2), style = Stroke(4f * u, cap = StrokeCap.Round))
        val endAngle = (startAngle + 85f) * PI.toFloat() / 180f
        drawCircle(palette.primary, 5f * u, Offset(cx + ringR * cos(endAngle), cy + ringR * sin(endAngle)))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WARNING — file with pulsing warning triangle + sparkles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WarningVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val bobT = rememberInfiniteTransition(label = "wv-bob")
    val bob by bobT.animateFloat(-4f, 4f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "wv-b")
    val pulseT = rememberInfiniteTransition(label = "wv-pulse")
    val pulse by pulseT.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), "wv-p")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = (100f + bob) * u
        val warn = Color(0xFFFFCC80)

        radialGlow(cx, cy, 70f * u, warn, 0.18f + 0.08f * (pulse - 0.9f) / 0.2f)

        // File
        val fw = 68f * u
        val fh = 86f * u
        rr(palette.surface3, cx - fw / 2, cy - fh / 2, fw, fh, fw * 0.14f)
        rr(warn, cx - fw / 2, cy - fh / 2, fw, fh, fw * 0.14f, 2f * u)
        val fold = Path().apply { moveTo(cx + fw / 2 - fw * 0.24f, cy - fh / 2); lineTo(cx + fw / 2, cy - fh / 2 + fw * 0.24f); lineTo(cx + fw / 2 - fw * 0.24f, cy - fh / 2 + fw * 0.24f); close() }
        drawPath(fold, warn.copy(alpha = 0.5f))
        for (i in 0 until 4) {
            rr(warn.copy(alpha = 0.35f - i * 0.04f), cx - fw * 0.28f, cy - fh * 0.18f + i * fh * 0.12f, fw * (0.50f - i * 0.06f), fh * 0.035f, 2f * u)
        }

        // Warning triangle
        val triCx = cx + fw * 0.42f
        val triCy = cy + fh * 0.32f
        val triSize = 20f * u * pulse
        val triPath = Path().apply { moveTo(triCx, triCy - triSize * 0.5f); lineTo(triCx + triSize * 0.46f, triCy + triSize * 0.35f); lineTo(triCx - triSize * 0.46f, triCy + triSize * 0.35f); close() }
        drawPath(triPath, warn)
        drawRoundRect(palette.background, Offset(triCx - triSize * 0.06f, triCy - triSize * 0.10f), Size(triSize * 0.12f, triSize * 0.20f), CornerRadius(triSize * 0.06f, triSize * 0.06f))
        drawCircle(palette.background, triSize * 0.07f, Offset(triCx, triCy + triSize * 0.16f))

        // Sparkles
        val sparkA = 0.3f + 0.6f * (pulse - 0.9f) / 0.2f
        drawCircle(warn.copy(alpha = sparkA), 2.5f * u, Offset(cx - 55f * u, cy - 40f * u))
        drawCircle(warn.copy(alpha = sparkA * 0.8f), 2f * u, Offset(cx + 58f * u, cy - 20f * u))
        drawCircle(warn.copy(alpha = sparkA * 0.6f), 2.2f * u, Offset(cx - 48f * u, cy + 42f * u))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROCESSING — rotating rings + file with parsed rows + flowing particle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProcessingVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val spinT = rememberInfiniteTransition(label = "pv-spin")
    val spin by spinT.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), "pv-s")
    val rowsT = rememberInfiniteTransition(label = "pv-rows")
    val rows by rowsT.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), "pv-r")
    val glowT = rememberInfiniteTransition(label = "pv-glow")
    val glowA by glowT.animateFloat(0.16f, 0.28f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pv-g")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = size.height / 2f

        radialGlow(cx, cy, 70f * u, palette.primary, glowA)

        // Rotating rings (opposite directions)
        drawArc(palette.primary.copy(alpha = 0.5f), spin, 120f, false, Offset(cx - 76f * u, cy - 76f * u), Size(152f * u, 152f * u), style = Stroke(2f * u, cap = StrokeCap.Round))
        drawArc(palette.primary.copy(alpha = 0.35f), -spin, 90f, false, Offset(cx - 62f * u, cy - 62f * u), Size(124f * u, 124f * u), style = Stroke(1.5f * u, cap = StrokeCap.Round))

        // Central file
        val fw = 56f * u
        val fh = 48f * u
        rr(palette.surface3, cx - fw / 2, cy - fh / 2, fw, fh, fw * 0.16f)
        rr(palette.primary, cx - fw / 2, cy - fh / 2, fw, fh, fw * 0.16f, 1.5f * u)

        // Parsed rows (staggered)
        for (i in 0 until 4) {
            val phase = (rows * 4 - i) % 4
            val reveal = phase.coerceIn(0f, 1f)
            if (reveal <= 0f) continue
            val y = cy - fh * 0.30f + i * fh * 0.20f
            val fullW = fw * (0.72f - i * 0.08f)
            rr(palette.primary.copy(alpha = 0.15f), cx - fw * 0.36f, y, fullW, fh * 0.06f, 2f * u)
            if (reveal > 0f) rr(palette.primary.copy(alpha = 0.85f), cx - fw * 0.36f, y, fullW * reveal, fh * 0.06f, 2f * u)
        }

        // Flowing particle
        val particleT = (spin / 360f * 2f) % 1f
        drawCircle(palette.primary, 3f * u, Offset(cx - fw * 0.40f + fw * 0.80f * particleT, cy))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CLIPBOARD — manifest that fills in with check marks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ClipboardVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "cv")
    val float by t.animateFloat(0f, -4f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), "cv-f")
    val fill by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), "cv-fill")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = (100f + float) * u

        radialGlow(cx, cy, 60f * u, palette.primary, 0.16f)

        val cw = 80f * u
        val ch = 100f * u
        rrGradient(Brush.verticalGradient(listOf(palette.surface3, palette.surface5)), cx - cw / 2, cy - ch / 2, cw, ch, 10f * u)
        rr(palette.primary.copy(alpha = 0.4f), cx - cw / 2, cy - ch / 2, cw, ch, 10f * u, 1.5f * u)
        rr(palette.primary, cx - 16f * u, cy - ch / 2 - 6f * u, 32f * u, 12f * u, 4f * u)

        for (i in 0 until 4) {
            val lineY = cy - ch * 0.22f + i * ch * 0.16f
            val phase = (fill * 4f - i * 0.8f).coerceIn(0f, 1f)
            rr(palette.primary.copy(alpha = 0.2f), cx - cw * 0.28f, lineY, cw * 0.40f, ch * 0.04f, 2f * u)
            if (phase > 0f) rr(palette.primary.copy(alpha = 0.6f), cx - cw * 0.28f, lineY, cw * 0.40f * phase, ch * 0.04f, 2f * u)
            if (phase >= 1f) {
                drawCircle(palette.primary, 5f * u, Offset(cx + cw * 0.24f, lineY + ch * 0.02f))
                val cp = Path().apply { moveTo(cx + cw * 0.24f - 2f * u, lineY + ch * 0.02f); lineTo(cx + cw * 0.24f - 0.5f * u, lineY + ch * 0.02f + 1.5f * u); lineTo(cx + cw * 0.24f + 2.5f * u, lineY + ch * 0.02f - 1.5f * u) }
                drawPath(cp, palette.surface3, style = Stroke(1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESTORE PROCESSING — circular progress ring with flowing particles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RestoreProcessingVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val progT = rememberInfiniteTransition(label = "rpv-prog")
    val progress by progT.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), "rpv-p")
    val partT = rememberInfiniteTransition(label = "rpv-part")
    val particles by partT.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), "rpv-pt")
    val glowT = rememberInfiniteTransition(label = "rpv-glow")
    val glowA by glowT.animateFloat(0.18f, 0.30f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "rpv-g")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = 68f * u

        radialGlow(cx, cy, r * 1.3f, palette.primary, glowA)
        drawCircle(palette.surface3, r, Offset(cx, cy), style = Stroke(10f * u))
        drawArc(palette.primary, -90f, progress * 360f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(10f * u, cap = StrokeCap.Round))

        for (i in 0 until 6) {
            val angle = (particles + i / 6f) * 360f * PI.toFloat() / 180f
            val pr = r * 0.78f
            val alpha = 0.3f + 0.5f * ((cos(angle - PI.toFloat()) + 1f) / 2f)
            drawCircle(palette.primary.copy(alpha = alpha), 3f * u, Offset(cx + pr * cos(angle), cy + pr * sin(angle)))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// POISON BOTTLE — tall bottle with thin neck, liquid, bubbles, skull label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PoisonBottleVisual(palette: WizardPalette, idx: Int = 0, modifier: Modifier = Modifier) {
    val floatT = rememberInfiniteTransition(label = "pbv-float-$idx")
    val float by floatT.animateFloat(-3f, 3f, infiniteRepeatable(tween(3000 + idx * 500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pbv-f-$idx")
    val spinT = rememberInfiniteTransition(label = "pbv-spin-$idx")
    val spin by spinT.animateFloat(-4f, 4f, infiniteRepeatable(tween(4000 + idx * 500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pbv-s-$idx")
    val bubbleT = rememberInfiniteTransition(label = "pbv-bubble-$idx")
    val bubbles = listOf(
        bubbleT.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, delayMillis = 0), RepeatMode.Restart), "pb-b1-$idx"),
        bubbleT.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, delayMillis = 800), RepeatMode.Restart), "pb-b2-$idx"),
        bubbleT.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, delayMillis = 1600), RepeatMode.Restart), "pb-b3-$idx"),
    )

    Canvas(modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
        val u = size.height / 160f
        val cx = size.width / 2f
        val fy = float * u

        radialGlow(cx, 80f * u + fy, 55f * u, palette.primary, 0.20f)

        // Cap (rounded)
        val capW = 30f * u
        val capH = 14f * u
        val capPath = Path().apply { addRoundRect(RoundRect(Rect(cx - capW / 2, 4f * u + fy, cx + capW / 2, 4f * u + fy + capH), CornerRadius(capH * 0.4f, capH * 0.4f))) }
        drawPath(capPath, palette.primary)

        // Neck (thin, longer, rounded)
        val neckW = 18f * u
        val neckH = 30f * u
        rr(palette.surface4, cx - neckW / 2, 18f * u + fy, neckW, neckH, 3f * u)
        rr(palette.primary, cx - neckW / 2, 18f * u + fy, neckW, neckH, 3f * u, 1.5f * u)

        // Body (tall, rounded)
        val bodyW = 50f * u
        val bodyH = 95f * u
        val bodyTop = 48f * u + fy
        val bodyPath = Path().apply {
            moveTo(cx - bodyW / 2, bodyTop + 12f * u)
            quadraticTo(cx - bodyW / 2, bodyTop, cx - neckW / 2 - 2f * u, bodyTop)
            lineTo(cx - neckW / 2, 48f * u + fy)
            lineTo(cx + neckW / 2, 48f * u + fy)
            lineTo(cx + neckW / 2 + 2f * u, bodyTop)
            quadraticTo(cx + bodyW / 2, bodyTop, cx + bodyW / 2, bodyTop + 12f * u)
            lineTo(cx + bodyW / 2, bodyTop + bodyH - 14f * u)
            quadraticTo(cx + bodyW / 2, bodyTop + bodyH, cx + bodyW / 2 - 14f * u, bodyTop + bodyH)
            lineTo(cx - bodyW / 2 + 14f * u, bodyTop + bodyH)
            quadraticTo(cx - bodyW / 2, bodyTop + bodyH, cx - bodyW / 2, bodyTop + bodyH - 14f * u)
            close()
        }
        drawPath(bodyPath, palette.primaryContainer)
        drawPath(bodyPath, palette.primary, style = Stroke(2.5f * u))

        // Liquid
        val liquidTop = bodyTop + bodyH * 0.35f
        val liquidPath = Path().apply {
            moveTo(cx - bodyW / 2 + 4f * u, liquidTop)
            quadraticTo(cx - bodyW / 4, liquidTop - 3f * u, cx, liquidTop)
            quadraticTo(cx + bodyW / 4, liquidTop + 3f * u, cx + bodyW / 2 - 4f * u, liquidTop)
            lineTo(cx + bodyW / 2 - 4f * u, bodyTop + bodyH - 14f * u)
            quadraticTo(cx + bodyW / 2 - 4f * u, bodyTop + bodyH - 4f * u, cx + bodyW / 2 - 14f * u, bodyTop + bodyH - 4f * u)
            lineTo(cx - bodyW / 2 + 14f * u, bodyTop + bodyH - 4f * u)
            quadraticTo(cx - bodyW / 2 + 4f * u, bodyTop + bodyH - 4f * u, cx - bodyW / 2 + 4f * u, bodyTop + bodyH - 14f * u)
            close()
        }
        drawPath(liquidPath, palette.primary.copy(alpha = 0.55f))

        // Bubbles
        bubbles.forEachIndexed { i, b ->
            val p = b.value
            if (p < 0.88f) {
                val y = bodyTop + bodyH - 8f * u - p * (bodyH * 0.5f)
                val alpha = if (p < 0.1f) p * 9f else if (p < 0.72f) 0.9f else maxOf(0f, 0.9f - (p - 0.72f) * 8f)
                val x = cx + when (i) { 0 -> -8f * u; 1 -> 6f * u; 2 -> -2f * u; else -> 0f }
                drawCircle(Color.White.copy(alpha = alpha), 3f * u, Offset(x, y))
            }
        }

        // Label
        val labelW = 36f * u
        val labelH = 34f * u
        val labelY = bodyTop + bodyH * 0.30f
        rr(palette.background.copy(alpha = 0.94f), cx - labelW / 2, labelY, labelW, labelH, 3f * u)

        // Skull and crossbones
        drawLine(palette.primary, Offset(cx - 10f * u, labelY + labelH - 4f * u), Offset(cx + 10f * u, labelY + 4f * u), 2.5f * u, StrokeCap.Round)
        drawLine(palette.primary, Offset(cx + 10f * u, labelY + labelH - 4f * u), Offset(cx - 10f * u, labelY + 4f * u), 2.5f * u, StrokeCap.Round)
        drawCircle(palette.primary, 2.5f * u, Offset(cx - 10f * u, labelY + labelH - 4f * u))
        drawCircle(palette.primary, 2.5f * u, Offset(cx + 10f * u, labelY + 4f * u))
        drawCircle(palette.primary, 2.5f * u, Offset(cx + 10f * u, labelY + labelH - 4f * u))
        drawCircle(palette.primary, 2.5f * u, Offset(cx - 10f * u, labelY + 4f * u))
        drawCircle(palette.primary, 8f * u, Offset(cx, labelY + labelH * 0.45f))
        rr(palette.primary, cx - 6f * u, labelY + labelH * 0.45f + 4f * u, 12f * u, 6f * u, 2f * u)
        drawCircle(palette.background, 2f * u, Offset(cx - 3f * u, labelY + labelH * 0.45f - 1f * u))
        drawCircle(palette.background, 2f * u, Offset(cx + 3f * u, labelY + labelH * 0.45f - 1f * u))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// POISON PILL — animated capsule with customizable colors
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PoisonPillVisual(palette: WizardPalette, idx: Int = 0, pillColor: Color = Color(0xFFE85D5D), pillColor2: Color = Color.White, modifier: Modifier = Modifier) {
    val rotT = rememberInfiniteTransition(label = "ppv-rot-$idx")
    val rot by rotT.animateFloat(-8f, 8f, infiniteRepeatable(tween(4000 + idx * 500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "ppv-r-$idx")
    val floatT = rememberInfiniteTransition(label = "ppv-float-$idx")
    val float by floatT.animateFloat(-3f, 3f, infiniteRepeatable(tween(3000 + idx * 500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "ppv-f-$idx")

    Canvas(modifier.fillMaxSize().graphicsLayer { rotationZ = rot }) {
        val u = size.height / 160f
        val cx = size.width / 2f
        val cy = size.height / 2f + float * u
        val pillW = 90f * u
        val pillH = 38f * u
        val r = pillH / 2f

        radialGlow(cx, cy, 50f * u, pillColor, 0.20f)

        drawCircle(pillColor, r, Offset(cx - pillW / 2 + r, cy))
        drawRect(pillColor, Offset(cx - pillW / 2 + r, cy - r), Size(pillW / 2 - r, pillH))
        drawRect(pillColor2, Offset(cx, cy - r), Size(pillW / 2 - r, pillH))
        drawCircle(pillColor2, r, Offset(cx + pillW / 2 - r, cy))
        drawLine(pillColor.copy(alpha = 0.3f), Offset(cx, cy - r), Offset(cx, cy + r), 1f * u)
        rr(pillColor.copy(alpha = 0.25f), cx - pillW / 2 + r * 0.5f, cy - r + r * 0.15f, pillW - r, r * 0.3f, r * 0.15f)

        val plusCx = cx - pillW / 4f
        drawLine(palette.onPrimary, Offset(plusCx, cy - 7f * u), Offset(plusCx, cy + 7f * u), 3f * u, StrokeCap.Round)
        drawLine(palette.onPrimary, Offset(plusCx - 7f * u, cy), Offset(plusCx + 7f * u, cy), 3f * u, StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FINISH — premium celebration with star burst + orbiting sparkles + slow check
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FinishVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "fv3")
    val draw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Restart), "fv3-d")
    val glow by t.animateFloat(0.15f, 0.30f, infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fv3-g")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), "fv3-o")
    val confetti by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, delayMillis = 2000, easing = LinearEasing), RepeatMode.Restart), "fv3-c")
    val scale by t.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fv3-s")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = 48f * u * scale

        radialGlow(cx, cy, r * 1.6f, palette.primary, glow)

        // Star burst rays
        for (i in 0 until 8) {
            val angle = (orbit + i * 45f) * PI.toFloat() / 180f
            val rayLen = r * (1.3f + 0.15f * sin(orbit * PI.toFloat() / 180f + i))
            drawLine(palette.primary.copy(alpha = 0.15f), Offset(cx + r * 1.1f * cos(angle), cy + r * 1.1f * sin(angle)), Offset(cx + (r * 1.1f + rayLen * 0.3f) * cos(angle), cy + (r * 1.1f + rayLen * 0.3f) * sin(angle)), 2f * u, StrokeCap.Round)
        }

        // Orbiting sparkles
        for (i in 0 until 6) {
            val angle = (orbit + i * 60f) * PI.toFloat() / 180f
            drawCircle(palette.primary.copy(alpha = 0.5f), (3f + 2f * sin(orbit * PI.toFloat() / 180f + i)) * u, Offset(cx + r * 1.35f * cos(angle), cy + r * 1.35f * sin(angle)))
        }

        // Circle (draws in slowly)
        val circleSweep = (draw * 1.2f).coerceIn(0f, 1f)
        drawArc(palette.primary, -90f, circleSweep * 360f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(7f * u, cap = StrokeCap.Round))
        if (circleSweep >= 1f) drawCircle(palette.primary, r, Offset(cx, cy))

        // Check mark (draws slowly)
        val checkP = (draw * 1.5f - 0.5f).coerceIn(0f, 1f)
        if (checkP > 0f) {
            val p1 = Offset(cx - r * 0.30f, cy)
            val p2 = Offset(cx - r * 0.05f, cy + r * 0.28f)
            val p3 = Offset(cx + r * 0.34f, cy - r * 0.26f)
            if (checkP <= 0.5f) {
                val k = checkP * 2f
                drawLine(palette.onPrimary, p1, Offset(p1.x + (p2.x - p1.x) * k, p1.y + (p2.y - p1.y) * k), 6f * u, StrokeCap.Round)
            } else {
                drawLine(palette.onPrimary, p1, p2, 6f * u, StrokeCap.Round)
                val k = (checkP - 0.5f) * 2f
                drawLine(palette.onPrimary, p2, Offset(p2.x + (p3.x - p2.x) * k, p2.y + (p3.y - p2.y) * k), 6f * u, StrokeCap.Round)
            }
        }

        // Gentle confetti
        if (confetti > 0f && circleSweep >= 1f) {
            val rng = java.util.Random(42)
            for (i in 0 until 16) {
                drawCircle(palette.primary.copy(alpha = 0.6f * (1f - confetti * 0.7f)), (2f + 2f * rng.nextFloat()) * u, Offset(rng.nextFloat() * size.width, confetti * size.height * (0.3f + 0.5f * rng.nextFloat())))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESTORE SUCCESS — slow, elegant check with rising sparkles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RestoreSuccessVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "rsv")
    val draw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Restart), "rsv-d")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), "rsv-p")
    val glow by t.animateFloat(0.18f, 0.30f, infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "rsv-g")
    val sparkles by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart), "rsv-s")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = 50f * u

        for (i in 0 until 2) {
            val phase = (pulse + i * 0.5f) % 1f
            drawCircle(palette.primary.copy(alpha = (1f - phase) * 0.12f), r + 40f * u * phase, Offset(cx, cy), style = Stroke(2f * u))
        }

        radialGlow(cx, cy, r * 1.5f, palette.primary, glow)

        val circleSweep = (draw * 1.2f).coerceIn(0f, 1f)
        drawArc(palette.primary, -90f, circleSweep * 360f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(7f * u, cap = StrokeCap.Round))
        if (circleSweep >= 1f) drawCircle(palette.primary, r, Offset(cx, cy))

        val checkP = (draw * 1.4f - 0.4f).coerceIn(0f, 1f)
        if (checkP > 0f) {
            val p1 = Offset(cx - r * 0.28f, cy)
            val p2 = Offset(cx - r * 0.05f, cy + r * 0.26f)
            val p3 = Offset(cx + r * 0.32f, cy - r * 0.24f)
            if (checkP <= 0.5f) {
                val k = checkP * 2f
                drawLine(palette.onPrimary, p1, Offset(p1.x + (p2.x - p1.x) * k, p1.y + (p2.y - p1.y) * k), 6f * u, StrokeCap.Round)
            } else {
                drawLine(palette.onPrimary, p1, p2, 6f * u, StrokeCap.Round)
                val k = (checkP - 0.5f) * 2f
                drawLine(palette.onPrimary, p2, Offset(p2.x + (p3.x - p2.x) * k, p2.y + (p3.y - p2.y) * k), 6f * u, StrokeCap.Round)
            }
        }

        if (sparkles > 0f) {
            val rng = java.util.Random(42)
            for (i in 0 until 12) {
                val sx = cx + (rng.nextFloat() - 0.5f) * r * 2.5f
                val phase = (sparkles + rng.nextFloat() * 0.3f) % 1f
                val sy = cy + r * 0.8f + (cy - r * 1.5f - (cy + r * 0.8f)) * phase
                drawCircle(palette.primary.copy(alpha = 0.5f * (1f - phase)), (2f + 1.5f * rng.nextFloat()) * u, Offset(sx, sy))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEARCH — magnifying glass with pulse ripple
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SearchVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sv2")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), "sv2-r")
    val float by t.animateFloat(0f, -4f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "sv2-f")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width * 0.42f
        val cy = (size.height * 0.42f) + float * u
        val r = size.width * 0.22f

        drawCircle(palette.primary.copy(alpha = (1f - ripple) * 0.3f), r + size.width * 0.10f * ripple, Offset(cx, cy), style = Stroke(2f * u))
        radialGlow(cx, cy, r * 1.4f, palette.primary, 0.18f)
        drawCircle(palette.primary, r, Offset(cx, cy), style = Stroke(5f * u))
        drawCircle(palette.primary.copy(alpha = 0.08f), r - 3f * u, Offset(cx, cy))
        drawLine(palette.primary, Offset(cx + r * 0.70f, cy + r * 0.70f), Offset(cx + r * 1.30f, cy + r * 1.30f), 7f * u, StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ALL LINKED — success animation for when all anime are linked
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AllLinkedVisual(palette: WizardPalette, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "alv")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), "alv-p")
    val draw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart), "alv-d")
    val glow by t.animateFloat(0.18f, 0.30f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "alv-g")
    val orbit by t.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), "alv-o")

    Canvas(modifier.fillMaxSize()) {
        val u = minOf(size.width, size.height) / 200f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = 40f * u

        for (i in 0 until 2) {
            val phase = (pulse + i * 0.5f) % 1f
            drawCircle(palette.primary.copy(alpha = (1f - phase) * 0.12f), r + 35f * u * phase, Offset(cx, cy), style = Stroke(2f * u))
        }

        radialGlow(cx, cy, r * 1.5f, palette.primary, glow)

        // Orbiting check marks
        for (i in 0 until 6) {
            val angle = (orbit + i * 60f) * PI.toFloat() / 180f
            val orbR = r * 1.2f
            val sx = cx + orbR * cos(angle)
            val sy = cy + orbR * sin(angle)
            drawCircle(palette.primary.copy(alpha = 0.6f), 6f * u, Offset(sx, sy))
            val cp = Path().apply { moveTo(sx - 2.5f * u, sy); lineTo(sx - 0.5f * u, sy + 2f * u); lineTo(sx + 3f * u, sy - 2f * u) }
            drawPath(cp, palette.onPrimary, style = Stroke(1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val circleSweep = (draw * 1.2f).coerceIn(0f, 1f)
        drawArc(palette.primary, -90f, circleSweep * 360f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(5f * u, cap = StrokeCap.Round))
        if (circleSweep >= 1f) drawCircle(palette.primary, r, Offset(cx, cy))

        val checkP = (draw * 1.4f - 0.4f).coerceIn(0f, 1f)
        if (checkP > 0f) {
            val p1 = Offset(cx - r * 0.28f, cy)
            val p2 = Offset(cx - r * 0.05f, cy + r * 0.25f)
            val p3 = Offset(cx + r * 0.32f, cy - r * 0.22f)
            if (checkP <= 0.5f) {
                val k = checkP * 2f
                drawLine(palette.onPrimary, p1, Offset(p1.x + (p2.x - p1.x) * k, p1.y + (p2.y - p1.y) * k), 5f * u, StrokeCap.Round)
            } else {
                drawLine(palette.onPrimary, p1, p2, 5f * u, StrokeCap.Round)
                val k = (checkP - 0.5f) * 2f
                drawLine(palette.onPrimary, p2, Offset(p2.x + (p3.x - p2.x) * k, p2.y + (p3.y - p2.y) * k), 5f * u, StrokeCap.Round)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FLOATING SHAPES — animated background for welcome screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FloatingShapes(palette: WizardPalette, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "fs")
    val data = listOf(
        Triple(0.15f, 0.20f, 40f),
        Triple(0.80f, 0.15f, 30f),
        Triple(0.85f, 0.70f, 35f),
        Triple(0.10f, 0.75f, 28f),
        Triple(0.50f, 0.10f, 22f),
        Triple(0.45f, 0.85f, 25f),
    )
    val floats = data.mapIndexed { i, _ ->
        t.animateFloat(-8f, 8f, infiniteRepeatable(tween(3000 + i * 500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fs-f-$i")
    }
    val alphas = data.mapIndexed { i, _ ->
        t.animateFloat(0.06f, 0.14f, infiniteRepeatable(tween(2500 + i * 400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "fs-a-$i")
    }

    Canvas(modifier.fillMaxSize()) {
        data.forEachIndexed { i, (xPct, yPct, baseSize) ->
            val cx = size.width * xPct
            val cy = size.height * yPct + floats[i].value * (size.height / 200f)
            val sz = baseSize * (size.width / 400f)
            if (i % 2 == 0) {
                drawCircle(palette.primary.copy(alpha = alphas[i].value), sz, Offset(cx, cy))
            } else {
                val path = Path().apply { addRoundRect(RoundRect(Rect(cx - sz, cy - sz, cx + sz, cy + sz), CornerRadius(sz * 0.3f, sz * 0.3f))) }
                drawPath(path, palette.primary.copy(alpha = alphas[i].value))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MINI ANIME PREVIEW — animated phone that cycles through screen states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MiniAnimePreview(palette: WizardPalette, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "map")
    val cycle by t.animateFloat(0f, 4f, infiniteRepeatable(tween(10000, easing = LinearEasing)), "map-c")
    val fade by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "map-f")

    val screenIndex = (cycle.toInt()) % 4

    Box(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.Black).padding(4.dp).clip(RoundedCornerShape(16.dp)).background(palette.background)
        ) {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).width(40.dp).height(6.dp).clip(RoundedCornerShape(999.dp)).background(palette.surface4))
            Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (screenIndex) {
                    0 -> {
                        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.5f + fade * 0.2f)))
                        repeat(2) { Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(999.dp)).background(palette.surface3)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(6.dp)).background(palette.surface4)) } }
                    }
                    1 -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(6.dp)).background(palette.primary.copy(alpha = 0.3f + fade * 0.2f))) } }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(6.dp)).background(palette.surface4)) } }
                    }
                    2 -> {
                        Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary.copy(alpha = 0.2f)))
                        repeat(3) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(palette.primary.copy(alpha = 0.4f + fade * 0.2f)))
                                Box(Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(palette.surface3))
                            }
                        }
                    }
                    3 -> {
                        repeat(4) { i ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.6f)))
                                    Spacer(Modifier.width(4.dp))
                                    Box(Modifier.width(50.dp).height(5.dp).clip(RoundedCornerShape(999.dp)).background(palette.surface3))
                                }
                                Box(Modifier.width(18.dp).height(10.dp).clip(RoundedCornerShape(999.dp)).background(if (i < 2) palette.primary.copy(alpha = 0.8f + fade * 0.2f) else palette.surface4))
                            }
                        }
                    }
                }
            }
        }
    }
}
