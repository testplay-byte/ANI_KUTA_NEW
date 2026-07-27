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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A mini "skeleton screen" preview of the anime details page for a palette.
 *
 * Closely mimics the actual anime details page layout:
 * - **Banner** (hero, ~30% height) — card surface color.
 * - **Cover thumbnail** (accent) — fully visible, overlapping the banner.
 * - **Title + subtitle** — text bars next to the cover.
 * - **Accent pills** — right below the cover/title (NOT below synopsis).
 * - **Action buttons** — gray (muted) pills, like the real buttons.
 * - **Episode list** — 5 rows, each with a bg + thumbnail + title + meta.
 *   Alternating opacity to show watched/unwatched states.
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
                .size(width = 100.dp, height = 190.dp)
                .border(borderWidth.dp, borderColor, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                ) {
                    // ── Banner / hero area ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cardColor),
                    )

                    // ── Cover + title row (cover overlaps the banner) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-12).dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // Cover thumbnail (accent) — fully visible
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 32.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // Title line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(textColor),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Subtitle line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(textColor.copy(alpha = 0.5f)),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // ── Accent pills (right below cover/title) ──
                    Row {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(7.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor.copy(alpha = 0.3f)),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(7.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor.copy(alpha = 0.3f)),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── Action buttons (gray/muted, like real buttons) ──
                    Row {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(textColor.copy(alpha = 0.2f)),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(textColor.copy(alpha = 0.1f))
                                .border(0.5.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── Episode list (5 rows with background) ──
                    repeat(5) { idx ->
                        val alpha = if (idx == 0) 0.8f else if (idx <= 2) 0.6f else 0.4f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cardColor.copy(alpha = 0.3f))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Episode thumbnail
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 10.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(cardColor),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(textColor.copy(alpha = alpha)),
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(1.5.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(textColor.copy(alpha = alpha * 0.5f)),
                                )
                            }
                        }
                    }
                }

                // ── Selected indicator (top-end) ──
                if (isSelected) {
                    val indicatorColor = if (isCustom) accentColor else MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(indicatorColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isCustom) Icons.Filled.Create else Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (indicatorColor.luminance() > 0.5f) Color.Black else Color.White,
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
