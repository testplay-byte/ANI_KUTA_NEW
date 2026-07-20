package com.testplaybyte.animeapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.style.*

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * BottomNavBar — FLOATING pill navigation (overlays on top of content).
 *
 * Matches the web prototype:
 * - Floating: 16dp padding from all edges, rounded 28dp, surface-3 background.
 * - NOT in Scaffold's bottomBar — overlaid as a Box layer so content scrolls
 *   underneath it (the prototype has content scrolling behind the nav).
 * - Active item: content-sized (no weight) — expands to fit full label.
 * - Inactive items: weight(1f) — icon-only, share remaining space.
 * - 42dp pill height, 58dp bar height.
 * - SVG vector icons (NEVER emojis).
 */
@Composable
fun BottomNavBar(
    items: List<NavItem>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val isActive = item.route == currentRoute
                    NavPill(
                        item = item,
                        isActive = isActive,
                        onClick = { onSelect(item.route) },
                        // Active: no weight = content-sized. Inactive: weight(1f).
                        modifier = if (isActive) Modifier else Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    item: NavItem,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "bgColor",
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "textColor",
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isActive) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = textColor,
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(
                visible = isActive,
                enter = expandHorizontally(animationSpec = tween(300)) + fadeIn(tween(200)),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
