package app.confused.anikuta.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.Motion

/**
 * ANIKUTA custom toggle — a pill-shaped switch (NOT the default Material3 Switch).
 *
 * Per `DESIGN_LANGUAGE/02-components/components.md` §12 + the prototype's `CustomToggle`.
 * The prototype uses a custom pill toggle for settings (not the default Switch).
 *
 * @param checked Whether the toggle is on.
 * @param onChange Callback when the toggle is toggled.
 */
@Composable
fun CustomToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(Motion.DurationStandard),
        label = "toggleBgColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.DurationStandard),
        label = "toggleThumbColor",
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .clickable { onChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (checked) "On" else "Off",
                color = thumbColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
