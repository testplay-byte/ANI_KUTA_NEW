package app.confused.anikuta.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.appupdate.AppUpdateManager
import app.confused.anikuta.core.appupdate.AppUpdatePreferences
import app.confused.anikuta.core.appupdate.DownloadedApk
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The About & Updates screen — reached from Settings → About & Updates.
 *
 * # Design (per user spec)
 *
 * - **App version** section: shows installed version name + code.
 * - **Updates** section: auto-check toggle + manual "Check for updates" button.
 *   Does NOT show the update info card inline — the update is shown as a
 *   bottom-up sheet (UpdateBottomSheet) when an update is found via [onUpdateFound].
 * - **Downloaded versions** section: only shows if there are actual downloaded
 *   APK files on disk. Each row has Install + Delete buttons.
 *
 * # Manual check behavior
 *
 * When the user taps "Check for updates":
 * 1. The check runs (suspend).
 * 2. If an update is found → [onUpdateFound] is called → the caller shows the
 *    UpdateBottomSheet.
 * 3. If no update → "You're on the latest version" is shown inline.
 * 4. If error → error card is shown inline.
 *
 * @param onBack Pops this screen.
 * @param onUpdateFound Called when a manual check finds an update. The caller
 *   should show the UpdateBottomSheet in response.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onUpdateFound: () -> Unit = {},
) {
    val context = LocalContext.current
    val updateManager = koinInject<AppUpdateManager>()
    val updatePrefs = koinInject<AppUpdatePreferences>()
    val scope = rememberCoroutineScope()

    val isChecking by updateManager.isChecking.collectAsStateWithLifecycle()
    val lastCheckError by updateManager.lastCheckError.collectAsStateWithLifecycle()
    val downloadedApks by updatePrefs.observeDownloadedApks().collectAsStateWithLifecycle(emptyList())
    val autoCheckEnabled by updatePrefs.observeUpdateCheckEnabled().collectAsStateWithLifecycle(true)

    // Get installed version.
    val installedVersionName = remember {
        try {
            val pm = context.packageManager
            pm.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    val installedVersionCode = remember {
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    val scrollState = rememberScrollState()
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US) }

    // Filter downloaded APKs to only show those that actually exist on disk
    val validDownloadedApks = remember(downloadedApks) {
        downloadedApks.filter { apk -> File(apk.filePath).exists() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "About", scrollState = scrollState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            // ── App version ──
            item {
                SettingsSectionLabel("App version")
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = "ANIKUTA",
                            fontFamily = RobotoFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Version $installedVersionName ($installedVersionCode)",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Updates section ──
            item {
                SettingsSectionLabel("Updates")
            }

            // Auto-update toggle
            item {
                GeneralToggleCard(
                    title = "Auto-check for updates",
                    subtitle = "Check for new versions when the app starts. If an update is " +
                        "available, the update dialog appears automatically.",
                    checked = autoCheckEnabled,
                    onCheckedChange = { updatePrefs.setUpdateCheckEnabled(it) },
                )
            }

            // Manual check button
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isChecking) {
                                scope.launch {
                                    val result = updateManager.checkForUpdate()
                                    if (result != null) {
                                        // Update found — show the bottom sheet via callback
                                        onUpdateFound()
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(24.dp).height(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Check",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isChecking) "Checking…" else "Check for updates",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val lastCheck = updatePrefs.getLastCheckTimestamp()
                            if (lastCheck > 0) {
                                Text(
                                    text = "Last checked: ${dateFormatter.format(Date(lastCheck))}",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Error display (only if a check failed)
            if (lastCheckError != null && !isChecking) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Check failed",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = lastCheckError ?: "",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // ── Downloaded versions (only show if there are valid files) ──
            if (validDownloadedApks.isNotEmpty()) {
                item {
                    SettingsSectionLabel("Downloaded versions")
                }
                items(validDownloadedApks, key = { it.filePath }) { apk ->
                    DownloadedApkRow(
                        apk = apk,
                        dateFormatter = dateFormatter,
                        onInstall = { updateManager.installDownloadedApk(apk.filePath) },
                        onDelete = { updateManager.deleteDownloadedApk(apk.filePath) },
                    )
                }
            }
        }
    }
}

/**
 * One downloaded APK row — shows version + size + date + Install + Delete buttons.
 *
 * The Delete button (trash icon) deletes the file from disk AND removes the record.
 * The Install button opens the system installer.
 */
@Composable
private fun DownloadedApkRow(
    apk: DownloadedApk,
    dateFormatter: SimpleDateFormat,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "v${apk.versionName}",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatBytes(apk.sizeBytes)} · ${dateFormatter.format(Date(apk.downloadedAt))}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Install button
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.InstallMobile,
                    contentDescription = null,
                    modifier = Modifier.width(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Install",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                )
            }
            // Delete button (trash icon)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 0.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
    else "${bytes / 1024} KB"
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}
