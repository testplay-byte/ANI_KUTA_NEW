package app.anikuta.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Phase 6 — Search settings subpage.
 *
 * Settings for the Search page:
 *   - Default source (AniList vs Extensions)
 *   - Result count per page
 *   - Filter + recent-search info
 *
 * Related files:
 *   - SearchViewModel.kt — search mode + filters + recents
 *   - SearchScreen.kt — the search UI
 */
@Composable
fun SearchSettingsScreen(onBack: () -> Unit) {
    SettingsSubpageScaffold(title = "Search", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Source") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Search,
                        title = "Toggle AniList / Extensions",
                        subtitle = "Switch between AniList and extension sources from the search screen",
                        onClick = { /* documented in-line */ },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Results") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "25 results per page (AniList)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "AniList search loads 25 results per page with infinite scroll. " +
                                "Extension search loads the first page from each installed source " +
                                "concurrently (like aniyomi's global search).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                SettingsGroupCard(title = "Filters") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Tune,
                        title = "Genre / Year / Format",
                        subtitle = "Client-side filters via the filter button (AniList mode)",
                        onClick = { /* documented in-line */ },
                    )
                }
            }
            item {
                SettingsGroupCard(title = "Recent searches") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Recent searches (max 10)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Your last 10 search terms are saved for quick re-search. " +
                                "Clear them from the search screen's recent section.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
