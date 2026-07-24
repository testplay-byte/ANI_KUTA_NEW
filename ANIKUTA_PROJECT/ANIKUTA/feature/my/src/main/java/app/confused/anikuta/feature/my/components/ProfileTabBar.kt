package app.confused.anikuta.feature.my.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.designsystem.theme.Motion

/**
 * Profile tab bar — segmented toggle for switching between Profile sections.
 *
 * Per design language §2 (multi-way toggle): single-row pill with equal-width
 * segments. Active segment filled with primary color, inactive are transparent.
 *
 * Tabs: Main, Behind Status. (Recently Watched is shown on the Main tab.)
 */
@Composable
fun ProfileTabBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf("Main", "Behind Status")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedTab
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    animationSpec = tween(Motion.DurationStandard),
                    label = "tabBgColor$index",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(Motion.DurationStandard),
                    label = "tabTextColor$index",
                )

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectTab(index) },
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = RobotoFamily,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
