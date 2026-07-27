package app.confused.anikuta.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
 * Per owner spec (Session 1 item 9.4): the palette selection shows mini screens
 * (mock UI) instead of simple circles. This composable renders a tiny version
 * of the anime details page using the palette's colors:
 * - Background = the palette's background color.
 * - Banner area = a darker/elevated surface (card).
 * - Cover thumbnail = a small rectangle in the accent color.
 * - Title line = a text-colored bar.
 * - Subtitle line = a muted text-colored bar (thinner).
 * - Action button = a small pill in the accent color.
 *
 * Tapping the card calls [onClick]. When [isSelected], a primary-colored
 * border + checkmark appear.
 *
 * @param label The palette name (shown below the preview).
 * @param backgroundColor The palette's background color.
 * @param cardColor The palette's card/surface color.
 * @param accentColor The palette's accent (primary) color.
 * @param textColor The palette's text color.
 * @param isSelected Whether this palette is currently selected.
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(180),
        label = "paletteBorder",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .size(width = 90.dp, height = 130.dp)
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            // ── Mini details-page skeleton ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
            ) {
                // Banner / hero area (card surface)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardColor),
                ) {
                    // Cover thumbnail (accent color) — overlaps the banner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 4.dp, bottom = (-8).dp)
                            .size(width = 24.dp, height = 32.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title line (text color)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(textColor),
                )
                Spacer(modifier = Modifier.height(3.dp))
                // Subtitle line (muted text — 60% opacity)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(textColor.copy(alpha = 0.5f)),
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Action button (accent pill)
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(accentColor),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Secondary button (outlined card)
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(cardColor)
                        .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
                )
            }

            // Selected checkmark (top-end)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        // Label below the preview
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
