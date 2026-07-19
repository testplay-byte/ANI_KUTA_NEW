package app.anikuta.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.ui.theme.AnikutaSprings

/**
 * Phase 6 task 6.17 — Settings home (category list).
 *
 * Replaces the old single-page MoreScreen with a clean category list. Each
 * category navigates to a dedicated subpage via separate NavGraph routes
 * (Q6 decision: separate routes — simpler, deep-linkable, back button works).
 *
 * Categories:
 *  - General       → clear cache, storage info
 *  - Player        → speed, hwdec, audio lang, subtitle basics
 *  - Extensions    → installed/available/repos + primary/secondary selection
 *  - Downloads     → queue + quality/audio settings
 *  - Tracking      → AniList login + sync settings
 *  - About         → version, build, GitHub, long-press → Debug screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsHomeScreen(
    onNavigate: (String) -> Unit,
) {
    val categories = listOf(
        SettingsCategory("settings/general", "General", "Clear cache, storage", Icons.Default.CleaningServices, "emerald"),
        SettingsCategory("settings/library", "Library", "Display mode, categories, badges", Icons.Default.LibraryBooks, "amber"),
        SettingsCategory("settings/history", "History", "Watch threshold, retention", Icons.Default.History, "rose"),
        SettingsCategory("settings/search", "Search", "Default source, result count", Icons.Default.Search, "sky"),
        SettingsCategory("settings/notifications", "Notifications", "New episodes, auto-download, quiet hours", Icons.Default.Notifications, "rose"),
        SettingsCategory("settings/data", "Data & Storage", "Storage folder, downloads location", Icons.Default.Folder, "blue"),
        SettingsCategory("settings/player", "Player", "Speed, hardware decoding, audio", Icons.Default.PlayCircle, "violet"),
        SettingsCategory("settings/details", "Details", "Episode list display, thumbnails, titles", Icons.Default.Info, "indigo"),
        SettingsCategory("settings/extensions", "Extensions", "Install, manage, primary source", Icons.Default.Extension, "amber"),
        SettingsCategory("settings/downloads", "Downloads", "Queue, quality, offline", Icons.Default.CloudDownload, "sky"),
        SettingsCategory("settings/tracking", "Tracking", "AniList login + sync", Icons.Default.TrackChanges, "rose"),
        SettingsCategory("settings/about", "About", "Version, GitHub, debug", Icons.Default.Info, "teal"),
        SettingsCategory("settings/backup", "Backup & Restore", "Export/import library, history, settings", Icons.Default.Backup, "indigo"),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    CategoryCard(category = cat, onClick = { onNavigate(cat.route) })
                }
            }
        }
    }
}

private data class SettingsCategory(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tone: String,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCard(category: SettingsCategory, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "settingsCardScale",
    )

    // Use surfaceVariant for all icons — the user reported that primaryContainer
    // produced "deep dark blue" on their device (Monet dynamic color extracted
    // blue from their wallpaper). surfaceVariant is a neutral tonal color that
    // looks consistent across devices and doesn't clash with the app's theme.
    val iconBg = MaterialTheme.colorScheme.surfaceVariant
    val iconFg = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon circle
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBg,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        tint = iconFg,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
