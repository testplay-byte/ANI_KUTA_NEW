package app.anikuta.ui.settings

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikuta.storage.StorageManager
import app.anikuta.storage.StoragePreferences
import com.hippo.unifile.UniFile
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Storage settings subpage — shows the current storage folder and lets the
 * user change it via the system SAF picker.
 *
 * Also shows the subdirectory structure that the app creates inside the
 * selected folder (downloads/, data/, backups/, cache/).
 */
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val storagePrefs = remember { Injekt.get<StoragePreferences>() }
    val storageManager = remember { Injekt.get<StorageManager>() }
    val scope = rememberCoroutineScope()
    val currentUri by storagePrefs.baseStorageDirectory().stateIn(scope).collectAsState()

    // Resolve the display path for the current folder
    val displayPath = remember(currentUri) {
        try {
            if (currentUri.startsWith("content://")) {
                UniFile.fromUri(context, Uri.parse(currentUri))?.filePath ?: currentUri.takeLast(60)
            } else {
                currentUri
            }
        } catch (e: Exception) {
            currentUri.takeLast(60)
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                Log.w("StorageSettings", "Persistable URI grant failed (non-fatal)", e)
            }
            storagePrefs.baseStorageDirectory().set(uri.toString())
        }
    }

    SettingsSubpageScaffold(title = "Storage", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Current folder
            item {
                SettingsGroupCard(title = "Current folder") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Storage location",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                displayPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    // Change folder button
                    Column(Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                try { pickFolder.launch(null) }
                                catch (e: android.content.ActivityNotFoundException) {
                                    Log.e("StorageSettings", "No SAF picker", e)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Change folder")
                        }
                    }
                }
            }

            // Subdirectory structure
            item {
                SettingsGroupCard(title = "Folder structure") {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "ANI-KUTA creates these subdirectories inside the selected folder:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        DirRow("downloads/", "Downloaded episodes")
                        DirRow("data/", "App data (screenshots, config)")
                        DirRow("backups/", "Backup files")
                        DirRow("cache/", "Temporary data (auto-cleaned)")
                    }
                }
            }
        }
    }
}

@Composable
private fun DirRow(name: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(100.dp),
        )
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
