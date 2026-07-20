package app.confused.anikuta.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.Motion

/**
 * A 2-way segmented toggle (two options).
 *
 * Per `DESIGN_LANGUAGE/02-components/components.md` §3 (2-way toggle) + the
 * prototype's `ThemeSegmentedToggle` and the old ANIKUTA's `StyledSegmentedRow`.
 *
 * Visual rules (from `DESIGN_LANGUAGE/04-screens/episode-layout-settings.md`):
 * - Container: `surfaceVariant` at 50% alpha, 12dp rounded, 4dp inner padding.
 * - Pills: `weight(1f)`, 8dp rounded, 4dp gap between.
 * - Selected: `primary` bg + `onPrimary` Bold text.
 * - Unselected: transparent + `onSurfaceVariant` Medium text.
 * - Text: `labelMedium`, center-aligned.
 *
 * @param options Exactly 2 options.
 * @param selected The index of the currently-selected option (0 or 1).
 * @param onSelect Callback with the new selected index.
 */
@Composable
fun TwoWayToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size == 2) { "TwoWayToggle requires exactly 2 options, got ${options.size}" }

    SegmentedToggleBase(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * A 3-way segmented toggle (three options). Same visual rules as [TwoWayToggle].
 */
@Composable
fun ThreeWayToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size == 3) { "ThreeWayToggle requires exactly 3 options, got ${options.size}" }

    SegmentedToggleBase(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun SegmentedToggleBase(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selected
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = tween(Motion.DurationStandard),
                    label = "segmentBgColor$index",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(Motion.DurationStandard),
                    label = "segmentTextColor$index",
                )

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) },
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
