package app.confused.anikuta.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A mini "skeleton screen" preview of the anime details page for a palette.
 *
 * Per owner spec (Session 1 item 9.4 + feedback): the palette selection shows
 * mini screens (mock UI) — a detailed miniature of the anime details page using
 * the palette's colors:
 * - Background = the palette's background color.
 * - Banner / hero area = the card surface color (with a gradient hint).
 * - Cover thumbnail = a small rectangle in the accent color.
 * - Title line = a text-colored bar.
 * - Subtitle line = a muted text-colored bar (thinner).
 * - Action button = a small pill in the accent color.
 * - Secondary button = a small outlined card.
 * - Episode row mock = a thumbnail + two text bars (title + meta), showing how
 *   episode rows look in this palette.
 *
 * Tapping the card calls [onClick]. When [isSelected], a border + checkmark
 * appear. When [isCustom], a unique dashed border + edit icon distinguish it
 * from presets (per owner feedback: "the custom one should have a different
 * color of unique highlighting to it").
 *
 * @param label The palette name (shown below the preview).
 * @param backgroundColor The palette's background color.
 * @param cardColor The palette's card/surface color.
 * @param accentColor The palette's accent (primary) color.
 * @param textColor The palette's text color.
 * @param isSelected Whether this palette is currently selected.
 * @param isCustom Whether this is the Custom palette (unique highlight).
 * @param onClick Called when the card is tapped.
 */
@Composable
fun PalettePreviewCard(
    label: String,
    backgroundColor: Color,
    cardColor: Color,
    accentColor: Color,
    textColor: Color,
    isSelected: Boolean,
    isCustom: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Custom gets a unique highlight color (accent-tinted), presets get the
    // standard primary/outline border.
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected && isCustom -> accentColor
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(180),
        label = "paletteBorder",
    )
    val borderWidth = if (isSelected) 2.5f else 1.5f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .size(width = 96.dp, height = 148.dp)
                .border(borderWidth.dp, borderColor, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            // Wrap content in a Box so the checkmark can align to TopEnd.
            Box(modifier = Modifier.fillMaxSize()) {
                // ── Mini details-page skeleton (detailed) ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                ) {
                    // ── Banner / hero area (card surface) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(cardColor),
                    ) {
                        // Cover thumbnail (accent color) — overlaps the banner.
                        // Uses offset() (NOT padding) for the overlap because
                        // padding() does not accept negative values.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = 4.dp, y = 8.dp)
                                .size(width = 22.dp, height = 28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Title line (text color) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // ── Subtitle line (muted text — 50% opacity) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor.copy(alpha = 0.5f)),
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // ── Action buttons row ──
                    Row {
                        // Primary action (accent pill)
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(9.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Secondary action (outlined card)
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(9.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(cardColor)
                                .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ── Episode row mock (thumbnail + title + meta) ──
                    // Per owner feedback: "There should be a mini view of the
                    // episode inside it too."
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Episode thumbnail (card color with accent corner)
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cardColor),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // Episode title line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(textColor.copy(alpha = 0.8f)),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Episode meta line (muted)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(textColor.copy(alpha = 0.4f)),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // ── Second episode row mock (lighter) ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cardColor.copy(alpha = 0.7f)),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(textColor.copy(alpha = 0.5f)),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(textColor.copy(alpha = 0.3f)),
                            )
                        }
                    }
                }

                // ── Selected indicator (top-end) ──
                // Custom shows an edit icon (unique), presets show a checkmark.
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isCustom) accentColor else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isCustom) Icons.Filled.Create else Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (accentColor.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }

        // Label below the preview
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                if (isCustom) accentColor else MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Extension to compute luminance for contrast decisions. */
private fun Color.luminance(): Float = androidx.compose.ui.graphics.luminance()
