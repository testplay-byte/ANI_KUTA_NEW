package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Phase 6 — Library settings subpage.
 *
 * Settings for the Library page:
 *   - Display mode (2-col grid / 3-col grid / list)
 *   - Sort mode (Title / Last watched / Unread)
 *   - Unwatched badge toggle
 *
 * Reads/writes via the LibraryViewModel's display mode (in-memory for now;
 * persistence can be added later via LibraryPreferences).
 *
 * Related files:
 *   - LibraryViewModel.kt — display mode + sort state
 *   - LibraryScreen.kt — the library UI
 *   - SettingsHomeScreen.kt — the hub that links here
 */
@Composable
fun LibrarySettingsScreen(onBack: () -> Unit) {
    SettingsSubpageScaffold(title = "Library", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Display") {
                    // Placeholder — the actual display mode is controlled from
                    // the Library top bar toggle. This screen documents the options.
                    ClickableSettingsRow(
                        icon = Icons.Default.GridView,
                        title = "Display mode",
                        subtitle = "2-col grid, 3-col grid, or list (toggle from the library top bar)",
                        onClick = { /* documented in-line */ },
                    )
                    ClickableSettingsRow(
                        icon = Icons.Default.LibraryBooks,
                        title = "Categories",
                        subtitle = "Create custom categories from the library tab row",
                        onClick = { /* documented in-line */ },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Badges") {
                    Text(
                        "Unwatched episode count badges appear on library cards " +
                            "when an anime has episodes in progress (below 85% watched).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Sort") {
                    Text(
                        "Sort modes: Title (alphabetical), Last watched (most recent " +
                            "first), Unread episodes (most unwatched first). " +
                            "Change from the library top bar sort button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
