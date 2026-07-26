package app.confused.anikuta.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * More screen — a list with Settings and other options.
 *
 * Extracted from `MainActivity.kt` during the Voyager navigation migration.
 * Lives in `:app` (the composition root) because it composes entries from
 * multiple feature modules (`:feature:my`, `:feature:history`, `:feature:download`),
 * which would violate "feature modules never import from other feature modules"
 * (Rule §14) if it lived in `:feature:more`.
 *
 * The "Activity" section (History + Updates rows) is contributed by
 * [HistoryUpdatesMoreEntries] from `:feature:history`.
 */
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenTrackers: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "More", scrollState = scrollState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            // Profile + Trackers entries (at top, per design)
            item {
                app.confused.anikuta.feature.my.ProfileTrackersMoreEntries(
                    onOpenProfile = onOpenProfile,
                    onOpenTrackers = onOpenTrackers,
                )
            }
            item {
                SettingsSectionLabel("General")
                MoreRow(
                    icon = Icons.Filled.Settings,
                    title = "Settings",
                    subtitle = "Theme, display, data management",
                    onClick = onOpenSettings,
                )
            }
            // History + Updates entries
            item {
                app.confused.anikuta.feature.history.HistoryUpdatesMoreEntries(
                    onOpenHistory = onOpenHistory,
                    onOpenUpdates = onOpenUpdates,
                )
            }
            // Downloads entries
            item {
                app.confused.anikuta.feature.download.DownloadsMoreEntries(
                    onOpenDownloads = onOpenDownloads,
                )
            }
        }
    }
}

/**
 * Settings screen — a sub-screen from More.
 *
 * Per user requirement: the episode settings are reached via a SINGLE "Episode
 * settings" row that navigates to a full-page hub (NOT a bottom sheet). The hub
 * then links to Display / Layout / Metadata sub-pages. See
 * `:feature:episode-settings` for the screens.
 */
@Composable
fun SettingsScreen(
    onOpenExtensions: () -> Unit,
    onOpenEpisodeSettings: () -> Unit,
    onOpenBackup: () -> Unit = {},
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Settings", scrollState = scrollState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            item {
                SettingsSectionLabel("General")
                MoreRow(
                    icon = Icons.Filled.Extension,
                    title = "Extensions",
                    subtitle = "Manage anime and manga extensions",
                    onClick = onOpenExtensions,
                )
            }
            item {
                SettingsSectionLabel("Episode List")
                MoreRow(
                    icon = Icons.Filled.Tune,
                    title = "Episode settings",
                    subtitle = "Display, layout, and metadata fetching for the episode list",
                    onClick = onOpenEpisodeSettings,
                )
            }
            // Backup & Restore
            item {
                SettingsSectionLabel("Data")
                MoreRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Backup & Restore",
                    subtitle = "Back up your library, history, and preferences",
                    onClick = onOpenBackup,
                )
            }
        }
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun MoreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
