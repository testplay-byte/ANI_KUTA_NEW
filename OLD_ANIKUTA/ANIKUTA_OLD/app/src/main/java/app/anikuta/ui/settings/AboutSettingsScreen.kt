package app.anikuta.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.BuildConfig
import kotlinx.coroutines.delay

private const val GITHUB_URL = "https://github.com/testplay-byte/anikuta"

/**
 * Phase 6 task 6.23 — About settings subpage.
 * Version, build info, GitHub link, long-press version → Debug screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutSettingsScreen(onBack: () -> Unit, onOpenDebug: () -> Unit) {
    val context = LocalContext.current
    var showHint by remember { mutableStateOf(false) }

    SettingsSubpageScaffold(title = "About", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "App") {
                    Column(Modifier.padding(16.dp)) {
                        LeadingIcon(Icons.Default.Info)
                        Text("ANI-KUTA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        // Version text — long-press for 1.5s → Debug screen
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.combinedClickable(
                                onClick = { showHint = true },
                                onLongClick = { onOpenDebug() },
                            ),
                        )
                        if (showHint) {
                            LaunchedEffect(Unit) {
                                delay(1500)
                                showHint = false
                            }
                            Text("Psst — keep holding for dev tools", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                SettingsGroupCard(title = "Links") {
                    ClickableSettingsRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = "GitHub",
                        subtitle = "Source code + issues",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                        },
                    )
                    HorizontalDivider()
                    ClickableSettingsRow(
                        icon = Icons.Default.Code,
                        title = "Open source licenses",
                        subtitle = "Libraries we use",
                        onClick = { /* TODO: licenses screen */ },
                    )
                }
            }
        }
    }
}
