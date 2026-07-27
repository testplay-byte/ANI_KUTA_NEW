package com.testplaybyte.animeapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

// Vector icons for the bottom navigation bar.
// Uses Material Icons (NEVER emojis). These are proper SVG-style vector icons.
// If material-icons-extended is needed for more icons, add:
//   implementation("androidx.compose.material:material-icons-extended")
// to app/build.gradle.kts

object NavIcons {
    val Home: ImageVector get() = Icons.Filled.Home
    val Library: ImageVector get() = Icons.Filled.MenuBook
    val History: ImageVector get() = Icons.Filled.History
    val Schedule: ImageVector get() = Icons.Filled.CalendarMonth
    val Search: ImageVector get() = Icons.Filled.Search
    val Settings: ImageVector get() = Icons.Filled.Settings
}
