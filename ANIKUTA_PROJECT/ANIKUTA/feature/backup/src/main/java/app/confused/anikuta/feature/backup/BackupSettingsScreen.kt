package app.confused.anikuta.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.backup.components.BackupCategoryList
import app.confused.anikuta.feature.backup.components.BackupSectionLabel
import app.confused.anikuta.feature.backup.components.FrequencySelector
import app.confused.anikuta.feature.backup.components.RestoreConfirmSheet
import org.koin.androidx.compose.koinViewModel

/**
 * Backup & Restore settings screen.
 *
 * Four sections (per the implementation prompt):
 * 1. **Backup** — category checkboxes + "Create backup" button.
 * 2. **Restore** — "Restore from file" button (opens file picker).
 * 3. **Auto-backup** — enable switch + frequency selector + category checkboxes.
 * 4. **Storage** — current folder + "Select folder" button + usage display.
 *
 * State overlays:
 * - Creating/Restoring: loading indicator.
 * - Created/Restored: success dialog with summary.
 * - Error: error dialog.
 * - RestorePending: [RestoreConfirmSheet] bottom sheet.
 *
 * Design: #B1F256 primary, RobotoFamily, surfaceVariant cards (alpha 0.4f),
 * CollapsingHeader, no drag handles on bottom sheets.
 */
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val manualCategories by viewModel.manualCategories.collectAsStateWithLifecycle()
    val autoCategories by viewModel.autoCategories.collectAsStateWithLifecycle()
    val autoEnabled by viewModel.autoEnabled.collectAsStateWithLifecycle()
    val autoFrequency by viewModel.autoFrequency.collectAsStateWithLifecycle()
    val folderUri by viewModel.folderUri.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()

    // SAF folder picker (for the Storage section)
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.setFolder(uri)
    }

    // File picker (for restore)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.onSelectBackupFile(uri)
    }

    val scrollState = rememberScrollState()
    val isCollapsed = false // BackupSettingsScreen is not scroll-collapsed for simplicity

    // State for the "Create backup" category-selection bottom sheet
    var showCreateBackupSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Backup & Restore",
                collapsed = isCollapsed,
                actions = {},
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
            ) {
                // ── Section 1: Backup ──
                // Just a button. Tapping it opens a bottom sheet where the user
                // selects which data categories to include, then confirms.
                item {
                    BackupSectionLabel("Backup")
                    SectionCard(
                        icon = Icons.Filled.CloudUpload,
                        title = "Create backup",
                        subtitle = "Export your data to a .anikuta file",
                    ) {
                        Button(
                            onClick = { showCreateBackupSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "Create backup",
                                fontFamily = RobotoFamily,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }

                // ── Section 2: Restore ──
                item {
                    BackupSectionLabel("Restore")
                    SectionCard(
                        icon = Icons.Filled.Restore,
                        title = "Restore from file",
                        subtitle = "Import data from an ANIKUTA or Aniyomi backup",
                    ) {
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "Select backup file",
                                fontFamily = RobotoFamily,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }

                // ── Section 3: Auto-backup ──
                item {
                    BackupSectionLabel("Auto-backup")
                    SectionCard(
                        icon = Icons.Filled.Schedule,
                        title = "Automatic backups",
                        subtitle = "Periodically back up your data in the background",
                    ) {
                        // Enable switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Enable auto-backup",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = autoEnabled,
                                onCheckedChange = { viewModel.toggleAutoEnabled(it) },
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Frequency selector (only shown when enabled)
                        if (autoEnabled) {
                            Text(
                                text = "FREQUENCY",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.06.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FrequencySelector(
                                selected = autoFrequency,
                                onSelect = { viewModel.setAutoFrequency(it) },
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "WHAT TO INCLUDE",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.06.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            BackupCategoryList(
                                categories = viewModel.categories,
                                selected = autoCategories,
                                onToggle = { viewModel.toggleAutoCategory(it) },
                            )
                        }
                    }
                }

                // ── Section 4: Storage ──
                item {
                    BackupSectionLabel("Storage")
                    SectionCard(
                        icon = Icons.Filled.Folder,
                        title = "Backup folder",
                        subtitle = if (folderUri.isNotBlank()) {
                            "Using ${storageUsage / 1024 / 1024} MB"
                        } else {
                            "No folder selected"
                        },
                    ) {
                        if (folderUri.isNotBlank()) {
                            Text(
                                text = Uri.decode(folderUri),
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        Button(
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = if (folderUri.isBlank()) "Select folder" else "Change folder",
                                fontFamily = RobotoFamily,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }

        // ── Create backup category-selection bottom sheet ──
        if (showCreateBackupSheet) {
            CreateBackupSheet(
                categories = viewModel.categories,
                selected = manualCategories,
                onToggle = { viewModel.toggleManualCategory(it) },
                onConfirm = {
                    showCreateBackupSheet = false
                    viewModel.createBackup()
                },
                onDismiss = { showCreateBackupSheet = false },
            )
        }

        // ── State overlays ──
        when (val s = state) {
            is BackupUiState.Creating -> LoadingOverlay(s.message)
            is BackupUiState.ReadingFile -> LoadingOverlay("Reading ${s.fileName}…")
            is BackupUiState.Restoring -> LoadingOverlay(s.message)
            is BackupUiState.Created -> SuccessDialog(
                title = "Backup created",
                message = "Saved ${s.summary.itemCount} items across ${s.summary.categoryCount} categories.\n\nFile: ${s.summary.filePath}",
                onDismiss = { viewModel.dismissState() },
            )
            is BackupUiState.Restored -> SuccessDialog(
                title = "Restore complete",
                message = buildRestoreMessage(s.summary),
                onDismiss = { viewModel.dismissState() },
            )
            is BackupUiState.Error -> ErrorDialog(
                message = s.message,
                onDismiss = { viewModel.dismissState() },
            )
            is BackupUiState.RestorePending -> {
                RestoreConfirmSheet(
                    summary = s.summary,
                    fileUri = s.fileUri,
                    onConfirm = { uri -> viewModel.confirmRestore(uri) },
                    onCancel = { viewModel.dismissState() },
                )
            }
            BackupUiState.Idle -> { /* no overlay */ }
        }
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column {
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
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SuccessDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        },
        title = {
            Text(title, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Text(message, fontFamily = RobotoFamily, fontSize = 14.sp)
        },
    )
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        },
        title = {
            Text("Error", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
        },
        text = {
            Text(message, fontFamily = RobotoFamily, fontSize = 14.sp)
        },
    )
}

private fun buildRestoreMessage(summary: app.confused.anikuta.core.backup.RestoreSummary): String {
    return buildString {
        appendLine("Format: ${summary.formatType.displayName}")
        appendLine("Imported: ${summary.totalImported} items")
        if (summary.totalSkipped > 0) appendLine("Skipped: ${summary.totalSkipped}")
        if (summary.totalErrors > 0) appendLine("Errors: ${summary.totalErrors}")
        appendLine()
        summary.categoryResults.forEach { result ->
            val status = when {
                result.importedCount > 0 -> "${result.importedCount} imported"
                result.note != null -> result.note
                else -> "no data"
            }
            appendLine("• ${result.category.displayName}: $status")
        }
    }
}

/**
 * Bottom sheet for selecting which data categories to include in a manual backup.
 *
 * Shows the full category list with checkboxes + a "Create backup" confirm button.
 * Uses AnikutaBottomSheet (dragHandle = null per design principle #2).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CreateBackupSheet(
    categories: List<BackupCategory>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnikutaBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Select data to back up",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${selected.size} of ${categories.size} categories selected",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Category checkbox list — scrollable if long
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                BackupCategoryList(
                    categories = categories,
                    selected = selected,
                    onToggle = onToggle,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = selected.isNotEmpty(),
            ) {
                Text(
                    text = "Create backup",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
