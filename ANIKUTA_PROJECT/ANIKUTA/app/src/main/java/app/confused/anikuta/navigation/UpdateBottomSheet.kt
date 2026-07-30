@file:OptIn(ExperimentalMaterial3Api::class)

package app.confused.anikuta.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.appupdate.DownloadProgress
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The update bottom sheet — shown when [AppController.showUpdateDialog] is true.
 *
 * # Design
 *
 * A bottom-up sheet (per DESIGN_LANGUAGE §2 — `dragHandle = null`) with:
 * - Title: "New update available"
 * - Version + release date
 * - Scrollable changelog/description area
 * - Download button (becomes a progress bar when downloading)
 * - Cancel button
 *
 * # Behavior
 *
 * - **Cancel** → [AppController.dismissUpdateSheet] (records 6-hour cooldown).
 * - **Download** → [AppUpdateManager.startDownload]. The button transforms into
 *   a progress bar. When complete, the APK is auto-opened via [AppUpdateManager.installDownloadedApk].
 * - If the user closes the sheet while downloading, the download continues in
 *   the background (the [AppUpdateManager] coroutine scope is independent).
 *
 * @param appController the app controller (provides update state + callbacks).
 */
@Composable
fun UpdateBottomSheet(appController: AppController) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val updateInfo by appController.updateManager.latestUpdate.collectAsState()
    val downloadProgress by appController.updateManager.downloadProgress.collectAsState()

    val info = updateInfo ?: return

    ModalBottomSheet(
        onDismissRequest = { appController.dismissUpdateSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 600.dp)
                .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            // ── Title ──
            Text(
                text = "New update available",
                fontFamily = RobotoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))

            // ── Version + date ──
            val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "v${info.versionName}",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (info.releaseDate > 0) {
                    Text(
                        text = "· ${dateFormatter.format(Date(info.releaseDate))}",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ── Changelog (scrollable) ──
            Text(
                text = "What's new",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = info.changelog,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── Download button / progress bar ──
            DownloadSection(
                progress = downloadProgress,
                apkSizeBytes = info.apkSizeBytes,
                onDownload = { appController.updateManager.startDownload() },
                onInstall = {
                    appController.updateManager.installDownloadedApk()
                },
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ── Cancel button ──
            OutlinedButton(
                onClick = { appController.dismissUpdateSheet() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DownloadSection(
    progress: DownloadProgress?,
    apkSizeBytes: Long?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    when {
        progress == null -> {
            // Initial state — show download button.
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                val sizeText = apkSizeBytes?.let { " (${formatBytes(it)})" } ?: ""
                Text(
                    text = "Download$sizeText",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        progress.error != null -> {
            // Error state.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Download failed: ${progress.error}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Retry", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        progress.isComplete -> {
            // Download complete — show install button.
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "Install Update",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        else -> {
            // Downloading — show progress bar.
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Downloading…",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${progress.percent ?: 0}%",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (progress.percent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val downloaded = formatBytes(progress.bytesDownloaded)
                val total = progress.totalBytes?.let { formatBytes(it) } ?: "?"
                Text(
                    text = "$downloaded / $total",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Formats a byte count into a human-readable string (e.g., "12.3 MB"). */
private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
