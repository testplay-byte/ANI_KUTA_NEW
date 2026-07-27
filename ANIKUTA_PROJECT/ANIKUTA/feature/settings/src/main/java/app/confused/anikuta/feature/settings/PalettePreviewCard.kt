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
 * Designed to closely mimic the actual anime details page layout:
 * - **Banner** (hero area, ~35% of height) — card surface color.
 * - **Cover thumbnail** (accent) — overlaps the banner bottom-left.
 * - **Title + subtitle** — text-colored bars.
 * - **Action buttons** — accent pill + outlined card.
 * - **Episode rows** — thumbnail + title + meta (2 rows, second is lighter).
 *
 * Tapping the card calls [onClick]. When [isSelected], a border + indicator
 * appear. When [isCustom], the indicator is an edit icon (unique); presets
 * use a checkmark.
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
                .size(width = 100.dp, height = 168.dp)
                .border(borderWidth.dp, borderColor, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                ) {
                    // ── Banner / hero area (card surface, taller) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(cardColor),
                    ) {
                        // Cover thumbnail (accent) — overlaps banner bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = 4.dp, y = 10.dp)
                                .size(width = 22.dp, height = 30.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ── Title line ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // ── Subtitle line ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor.copy(alpha = 0.5f)),
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // ── Action buttons ──
                    Row {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(9.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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

                    // ── Episode row 1 (full opacity) ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 22.dp, height = 13.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cardColor),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(textColor.copy(alpha = 0.8f)),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
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

                    // ── Episode row 2 (lighter — watched) ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 22.dp, height = 13.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cardColor.copy(alpha = 0.6f)),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(textColor.copy(alpha = 0.4f)),
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
                            tint = if ((if (isCustom) accentColor else MaterialTheme.colorScheme.primary).luminance() > 0.5f) Color.Black else Color.White,
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
