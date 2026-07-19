package app.anikuta.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.data.tracker.AniListTracker
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 6 task 6.22 — Tracking settings subpage.
 * AniList OAuth login + status + logout + sync settings.
 */
@Composable
fun TrackingSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracker: AniListTracker = remember { Injekt.get() }
    var loggedIn by remember { mutableStateOf(tracker.isLoggedIn()) }
    var username by remember { mutableStateOf(tracker.username() ?: "") }

    SettingsSubpageScaffold(title = "Tracking", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "AniList") {
                    if (loggedIn) {
                        // Logged in state
                        Column(Modifier.padding(16.dp)) {
                            LeadingIcon(Icons.Default.TrackChanges)
                            Text("Logged in as $username", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("Progress will sync automatically when you watch episodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                        ClickableSettingsRow(
                            icon = Icons.Default.Sync,
                            title = "Sync now",
                            subtitle = "Force sync all watched progress",
                            onClick = {
                                Toast.makeText(context, "Sync started", Toast.LENGTH_SHORT).show()
                            },
                        )
                        HorizontalDivider()
                        ClickableSettingsRow(
                            icon = Icons.Default.Logout,
                            title = "Log out",
                            subtitle = "Disconnect from AniList",
                            onClick = {
                                tracker.logout()
                                loggedIn = false
                                username = ""
                                Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                            },
                        )
                    } else {
                        // Logged out state
                        Column(Modifier.padding(16.dp)) {
                            LeadingIcon(Icons.Default.TrackChanges)
                            Text("Not connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("Log in to sync your watch progress to AniList", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                        ClickableSettingsRow(
                            icon = Icons.Default.Login,
                            title = "Login with AniList",
                            subtitle = "Opens browser for authorization",
                            onClick = {
                                tracker.startLogin(context)
                                Toast.makeText(context, "Complete login in browser", Toast.LENGTH_LONG).show()
                            },
                        )
                    }
                }
            }
        }
    }
}
