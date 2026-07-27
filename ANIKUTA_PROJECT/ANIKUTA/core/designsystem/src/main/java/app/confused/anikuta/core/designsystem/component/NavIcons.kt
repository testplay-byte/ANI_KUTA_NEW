package app.confused.anikuta.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Vector icons for the bottom navigation bar.
 *
 * Per `DESIGN_LANGUAGE/`: uses Material vector icons, NEVER emojis.
 * Requires `material-icons-extended` (already in the compose convention plugin deps).
 */
object NavIcons {
    val Home: ImageVector get() = Icons.Filled.Home
    val Library: ImageVector get() = Icons.Filled.MenuBook
    val History: ImageVector get() = Icons.Filled.History
    val Schedule: ImageVector get() = Icons.Filled.CalendarMonth
    val Search: ImageVector get() = Icons.Filled.Search
    val Settings: ImageVector get() = Icons.Filled.Settings
    val More: ImageVector get() = Icons.Filled.MoreHoriz
}

/**
 * A navigation tab item.
 *
 * Per ADR-017: the bottom nav has 3–7 tabs, rearrangeable, with one fixed "More" tab.
 */
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)
