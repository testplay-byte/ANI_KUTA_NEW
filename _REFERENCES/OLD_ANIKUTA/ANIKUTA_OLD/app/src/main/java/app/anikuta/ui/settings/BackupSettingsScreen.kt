package app.anikuta.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Backup & Restore settings screen.
 *
 * Two export formats:
 *  - AniKuta format (.anikuta) — our own JSON format, complete data
 *  - Aniyomi format (.json.gz) — protobuf, aniyomi-compatible
 *
 * Restore auto-detects the format.
 */
@Composable
fun BackupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager: BackupManager = remember { Injekt.get() }

    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf<BackupManager.RestoreResult?>(null) }

    // File launchers
    val createAnikutaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBackingUp = true
                val success = withContext(Dispatchers.IO) {
                    backupManager.createAnikutaBackup(uri)
                }
                isBackingUp = false
                Toast.makeText(context,
                    if (success) "Backup created" else "Backup failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val createAniyomiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBackingUp = true
                val success = withContext(Dispatchers.IO) {
                    backupManager.createAniyomiBackup(uri)
                }
                isBackingUp = false
                Toast.makeText(context,
                    if (success) "Aniyomi backup created" else "Backup failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isRestoring = true
                val result = withContext(Dispatchers.IO) {
                    backupManager.restoreBackup(uri)
                }
                isRestoring = false
                restoreResult = result
            }
        }
    }

    SettingsSubpageScaffold(title = "Backup & Restore", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Create backup ----
            item {
                SettingsGroupCard(title = "Create backup") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Backup,
                        title = "Create backup",
                        subtitle = "Export your library, history, searches, and settings",
                        onClick = { showFormatDialog = true },
                    )
                }
            }

            // ---- Restore backup ----
            item {
                SettingsGroupCard(title = "Restore backup") {
                    ClickableSettingsRow(
                        icon = Icons.Default.Restore,
                        title = "Restore from backup",
                        subtitle = "Import a backup file (auto-detects format)",
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }

            // ---- Info ----
            item {
                SettingsGroupCard(title = "About backups") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Two backup formats are supported:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• AniKuta format (.anikuta) — our own format with all data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Aniyomi format (.json.gz) — compatible with aniyomi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Restore auto-detects the format — just select any backup file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Loading overlay
    if (isBackingUp || isRestoring) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (isBackingUp) "Creating backup..." else "Restoring...")
                }
            }
        }
    }

    // Format selection dialog
    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Backup format") },
            text = { Text("Which format do you want to use?") },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    createAnikutaLauncher.launch("anikuta_backup_${System.currentTimeMillis()}.anikuta")
                }) { Text("AniKuta (.anikuta)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    createAniyomiLauncher.launch("anikuta_backup_${System.currentTimeMillis()}.json.gz")
                }) { Text("Aniyomi (.json.gz)") }
            },
        )
    }

    // Restore result dialog
    restoreResult?.let { result ->
        when (result) {
            is BackupManager.RestoreResult.Success -> {
                AlertDialog(
                    onDismissRequest = { restoreResult = null },
                    title = { Text("Restore complete") },
                    text = {
                        buildString {
                            append("✓ Library: ${result.libraryCount} anime\n")
                            append("✓ History: ${result.historyCount} entries\n")
                            append("✓ Searches: ${result.searchCount} terms\n")
                            append("✓ Categories: ${result.categoryCount}")
                            result.note?.let { append("\n\n⚠ $it") }
                        }.let { Text(it) }
                    },
                    confirmButton = { TextButton(onClick = { restoreResult = null }) { Text("OK") } },
                )
            }
            is BackupManager.RestoreResult.Error -> {
                AlertDialog(
                    onDismissRequest = { restoreResult = null },
                    title = { Text("Restore failed") },
                    text = { Text(result.message) },
                    confirmButton = { TextButton(onClick = { restoreResult = null }) { Text("OK") } },
                )
            }
        }
    }
}
