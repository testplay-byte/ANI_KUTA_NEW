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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The About & Updates screen — reached from Settings → About & Updates.
 *
 * Contains:
 * - **App version** — the installed version name + code.
 * - **Auto-update toggle** — whether to check for updates on app open.
 * - **Check for updates** button — manually triggers a check.
 * - **Latest update info** — if an update is available, shows version + changelog
 *   + a download button (with progress bar).
 * - **Downloaded versions** — list of previously downloaded APKs. Tapping one
 *   opens the system installer to re-install it.
 *
 * The download continues in the background even if the user navigates away
 * (the [AppUpdateManager] coroutine scope is independent of this screen).
 *
 * @param onBack Pops this screen.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val updateManager = koinInject<AppUpdateManager>()
    val updatePrefs = koinInject<AppUpdatePreferences>()
    val scope = rememberCoroutineScope()

    val latestUpdate by updateManager.latestUpdate.collectAsStateWithLifecycle()
    val downloadProgress by updateManager.downloadProgress.collectAsStateWithLifecycle()
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
                        "available (and you haven't dismissed it in the last 6 hours), the " +
                        "update dialog appears automatically.",
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
                                scope.launch { updateManager.checkForUpdate() }
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

            // Latest update info (if available)
            if (latestUpdate != null) {
                item {
                    val info = latestUpdate!!
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "Update available: v${info.versionName}",
                                fontFamily = RobotoFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (info.releaseDate > 0) {
                                Text(
                                    text = "Released: ${dateFormatter.format(Date(info.releaseDate))}",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = info.changelog.take(500) + if (info.changelog.length > 500) "…" else "",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp,
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Download button / progress
                            when {
                                downloadProgress == null -> {
                                    Button(
                                        onClick = { updateManager.startDownload() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download Update", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                downloadProgress!!.isComplete -> {
                                    Button(
                                        onClick = { updateManager.installDownloadedApk() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Text("Install Now", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                downloadProgress!!.error != null -> {
                                    Text(
                                        text = "Error: ${downloadProgress!!.error}",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { updateManager.startDownload() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Text("Retry", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                else -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Downloading…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${downloadProgress!!.percent ?: 0}%", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { (downloadProgress!!.percent ?: 0) / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (lastCheckError != null) {
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
            } else if (!isChecking) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "You're on the latest version.",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            // ── Downloaded versions ──
            if (downloadedApks.isNotEmpty()) {
                item {
                    SettingsSectionLabel("Downloaded versions")
                }
                items(downloadedApks, key = { it.filePath }) { apk ->
                    DownloadedApkRow(
                        apk = apk,
                        dateFormatter = dateFormatter,
                        onInstall = { updateManager.installDownloadedApk(apk.filePath) },
                        onRemove = { updatePrefs.removeDownloadedApk(apk.filePath) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedApkRow(
    apk: DownloadedApk,
    dateFormatter: SimpleDateFormat,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
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
            OutlinedButton(
                onClick = onInstall,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Install", fontWeight = FontWeight.SemiBold)
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
