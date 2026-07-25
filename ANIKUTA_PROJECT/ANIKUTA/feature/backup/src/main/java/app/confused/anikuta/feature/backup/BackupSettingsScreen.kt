package app.confused.anikuta.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.backup.components.AutoIncludeSheet
import app.confused.anikuta.feature.backup.components.BackupCategoryList
import app.confused.anikuta.feature.backup.components.BackupSectionLabel
import app.confused.anikuta.feature.backup.components.BackupSuccessDialog
import app.confused.anikuta.feature.backup.components.CreateBackupAnimationOverlay
import app.confused.anikuta.feature.backup.components.FrequencySelector
import app.confused.anikuta.feature.backup.components.MaxBackupsSelector
import app.confused.anikuta.feature.backup.components.RestoreAnimationOverlay
import app.confused.anikuta.feature.backup.components.RestoreCompleteDialog
import app.confused.anikuta.feature.backup.components.RestoreSummaryDialog
import org.koin.androidx.compose.koinViewModel

/**
 * Backup & Restore settings screen.
 *
 * Three sections:
 * 1. **Backup & Restore** (combined) — Create backup + Restore from file buttons.
 * 2. **Auto-backup** — toggle in header, frequency 2x2 grid, max-backups, "what to include" button.
 * 3. **Storage** — folder selector + usage display.
 *
 * UI features:
 * - CollapsingHeader that shrinks on scroll (wired to LazyColumn scroll state).
 * - Bottom sheet backdrop dim + blur when Create Backup / Auto-include sheets open.
 * - Rich grid-based success/restore dialogs (not plain text).
 * - Beautiful 5-second-minimum restore animation.
 * - Post-restore: user clicks OK → redirected to Library page via [onRestoreComplete].
 *
 * Design: #B1F256 primary, RobotoFamily, surfaceVariant cards (alpha 0.4f),
 * CollapsingHeader, no drag handles on bottom sheets.
 */
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    onRestoreComplete: () -> Unit = {},
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val manualCategories by viewModel.manualCategories.collectAsStateWithLifecycle()
    val autoCategories by viewModel.autoCategories.collectAsStateWithLifecycle()
    val autoEnabled by viewModel.autoEnabled.collectAsStateWithLifecycle()
    val autoFrequency by viewModel.autoFrequency.collectAsStateWithLifecycle()
    val autoMaxKeep by viewModel.autoMaxKeep.collectAsStateWithLifecycle()
    val folderUri by viewModel.folderUri.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()

    // SAF folder picker
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

    // LazyColumn scroll state → drives the CollapsingHeader
    val listState = rememberLazyListState()
    val isCollapsed = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20

    // Bottom sheet states
    var showCreateBackupSheet by remember { mutableStateOf(false) }
    var showAutoIncludeSheet by remember { mutableStateOf(false) }

    // Whether any sheet is open (for backdrop dim + blur)
    val anySheetOpen = showCreateBackupSheet || showAutoIncludeSheet

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Main content (dimmed + blurred when a sheet is open) ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (anySheetOpen) {
                        Modifier
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                            .blur(8.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            CollapsingHeader(
                title = "Backup & Restore",
                collapsed = isCollapsed,
                actions = {},
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
            ) {
                // ── Section 1: Backup & Restore (combined) ──
                item {
                    BackupSectionLabel("Backup & Restore")
                    SectionCard(
                        icon = Icons.Filled.CloudUpload,
                        title = "Backup & Restore",
                        subtitle = "Create a backup or restore from a file",
                    ) {
                        // Create backup button
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
                        Spacer(modifier = Modifier.height(8.dp))
                        // Restore button
                        androidx.compose.material3.OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "Restore from file",
                                fontFamily = RobotoFamily,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }

                // ── Section 2: Auto-backup ──
                item {
                    BackupSectionLabel("Auto-backup")
                    SectionCard(
                        icon = Icons.Filled.Schedule,
                        title = "Automatic backups",
                        subtitle = "Periodically back up your data in the background",
                        toggle = {
                            Switch(
                                checked = autoEnabled,
                                onCheckedChange = { viewModel.toggleAutoEnabled(it) },
                            )
                        },
                    ) {
                        if (autoEnabled) {
                            // Frequency 2x2 grid
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

                            // Max backups to keep
                            Text(
                                text = "MAX BACKUPS TO KEEP",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.06.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            MaxBackupsSelector(
                                selected = autoMaxKeep,
                                onSelect = { viewModel.setAutoMaxKeep(it) },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // What to include — behind a button
                            Button(
                                onClick = { showAutoIncludeSheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "What to include (${autoCategories.size})",
                                    fontFamily = RobotoFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                }

                // ── Section 3: Storage ──
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

        // ── Auto-include category-selection bottom sheet ──
        if (showAutoIncludeSheet) {
            AutoIncludeSheet(
                categories = viewModel.categories,
                selected = autoCategories,
                onToggle = { viewModel.toggleAutoCategory(it) },
                onDismiss = { showAutoIncludeSheet = false },
            )
        }

        // ── State overlays ──
        when (val s = state) {
            is BackupUiState.Creating -> CreateBackupAnimationOverlay(s.message)
            is BackupUiState.ReadingFile -> RestoreAnimationOverlay("Reading ${s.fileName}…")
            is BackupUiState.Restoring -> RestoreAnimationOverlay(s.message)
            is BackupUiState.Created -> BackupSuccessDialog(
                summary = s.summary,
                onDismiss = { viewModel.dismissState() },
            )
            is BackupUiState.Restored -> RestoreCompleteDialog(
                summary = s.summary,
                onDismiss = {
                    viewModel.dismissState()
                    onRestoreComplete()
                },
            )
            is BackupUiState.Error -> ErrorDialog(
                message = s.message,
                onDismiss = { viewModel.dismissState() },
            )
            is BackupUiState.RestorePending -> RestoreSummaryDialog(
                summary = s.summary,
                fileUri = s.fileUri,
                onConfirm = { uri -> viewModel.confirmRestore(uri) },
                onCancel = { viewModel.dismissState() },
            )
            BackupUiState.Idle -> { /* no overlay */ }
        }
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    toggle: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
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
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (toggle != null) {
                    toggle()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
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

/**
 * Bottom sheet for selecting which data categories to include in a manual backup.
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
