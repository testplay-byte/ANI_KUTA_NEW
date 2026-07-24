package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.my.ProfilePreferences

/**
 * Customization bottom sheet — toggles for section visibility, profile editing,
 * stats source selection, and reset stats.
 *
 * Per design language:
 * - dragHandle = null (principle #2)
 * - max height 70% of viewport (principle #3)
 * - Uses Material3 Switch (principle #8 — switches for true on/off settings)
 *
 * Toggles are REACTIVE — they use `collectAsState()` on the Preference changes
 * flow so the UI updates immediately when the preference changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationSheet(
    preferences: ProfilePreferences,
    onChangeName: () -> Unit,
    onChangeAvatar: () -> Unit,
    onResetStats: () -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxHeight = screenHeight * 0.7f // principle #3: max 70% of viewport

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 32.dp),
        ) {
            // Header
            Text(
                text = "Customize Profile",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
            )

            // Profile editing options
            SectionLabel("Profile")
            ActionRow(
                icon = Icons.Filled.Edit,
                label = "Change Name",
                onClick = onChangeName,
            )
            ActionRow(
                icon = Icons.Filled.Person,
                label = "Change Picture",
                onClick = onChangeAvatar,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats source toggle
            SectionLabel("Stats Source")
            val useTrackerStats by preferences.useTrackerStats.changes().collectAsState(initial = preferences.useTrackerStats.get())
            ToggleRow(
                label = "Use AniList Stats",
                checked = useTrackerStats,
                onCheckedChange = { preferences.useTrackerStats.set(it) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Section visibility toggles
            SectionLabel("Sections")
            val showQuickStats by preferences.showQuickStats.changes().collectAsState(initial = preferences.showQuickStats.get())
            val showGenreChart by preferences.showGenreChart.changes().collectAsState(initial = preferences.showGenreChart.get())
            val showStatusChart by preferences.showStatusChart.changes().collectAsState(initial = preferences.showStatusChart.get())
            val showBehindStatus by preferences.showBehindStatus.changes().collectAsState(initial = preferences.showBehindStatus.get())
            val showRecentlyWatched by preferences.showRecentlyWatched.changes().collectAsState(initial = preferences.showRecentlyWatched.get())

            ToggleRow(label = "Quick Stats", checked = showQuickStats, onCheckedChange = { preferences.showQuickStats.set(it) })
            ToggleRow(label = "Genres", checked = showGenreChart, onCheckedChange = { preferences.showGenreChart.set(it) })
            ToggleRow(label = "Status", checked = showStatusChart, onCheckedChange = { preferences.showStatusChart.set(it) })
            ToggleRow(label = "Behind Status", checked = showBehindStatus, onCheckedChange = { preferences.showBehindStatus.set(it) })
            ToggleRow(label = "Recently Watched", checked = showRecentlyWatched, onCheckedChange = { preferences.showRecentlyWatched.set(it) })

            Spacer(modifier = Modifier.height(16.dp))

            // Reset Stats button
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onResetStats() },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Reset Stats",
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.06.sp,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ),
        )
    }
}
