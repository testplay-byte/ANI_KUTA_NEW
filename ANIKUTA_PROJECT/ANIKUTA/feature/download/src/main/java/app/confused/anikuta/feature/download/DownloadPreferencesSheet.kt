package app.confused.anikuta.feature.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.download.DownloadPreferences
import org.koin.compose.koinInject

/**
 * A bottom-up sheet for download settings — folder, Wi-Fi-only, concurrency.
 *
 * **Design rules (DESIGN_LANGUAGE):**
 *  - `dragHandle = null` (principle #2 — no drag handle).
 *  - Partial height (principle #3) — natural content, capped by the sheet.
 *  - RobotoFamily, #B1F256 accents, surfaceVariant cards.
 *  - Material3 `Switch` for on/off toggles.
 *
 * The folder picker uses `OpenDocumentTree`; the chosen URI is persisted via
 * [DownloadPreferences.downloadFolderUri] (+ the SAF permission, taken by the
 * manager's `setDownloadFolder`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPreferencesSheet(
    onDismiss: () -> Unit,
    onFolderPicked: (String) -> Unit,
    preferences: DownloadPreferences = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val wifiOnly by preferences.wifiOnly().changes().collectAsState(initial = preferences.wifiOnly().get())
    val concurrent by preferences.concurrentDownloads().changes()
        .collectAsState(initial = preferences.concurrentDownloads().get())
    val showButton by preferences.showDownloadButton().changes()
        .collectAsState(initial = preferences.showDownloadButton().get())
    val folderUri by preferences.downloadFolderUri().changes()
        .collectAsState(initial = preferences.downloadFolderUri().get())

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onFolderPicked(uri.toString())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null, // Hard rule — design principle #2
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Download settings",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Folder row — shows the readable folder name when set (per owner feedback).
            val folderSubtitle = if (folderUri.isNotBlank()) {
                val name = app.confused.anikuta.core.download.DownloadStorageProvider
                    .folderDisplayName(folderUri)
                if (!name.isNullOrBlank()) "Folder: $name" else "Folder selected"
            } else {
                "Not set — tap to choose"
            }
            SettingsRow(
                title = "Download folder",
                subtitle = folderSubtitle,
                onClick = { folderLauncher.launch(null) },
            )

            // Wi-Fi-only toggle
            ToggleRow(
                title = "Wi-Fi only",
                subtitle = "Pause downloads on mobile data",
                checked = wifiOnly,
                onCheckedChange = { preferences.wifiOnly().set(it) },
            )

            // Show download button on episode rows
            ToggleRow(
                title = "Show download button",
                subtitle = "Display the download icon on episode rows",
                checked = showButton,
                onCheckedChange = { preferences.showDownloadButton().set(it) },
            )

            // Concurrent downloads (clamped 1..5)
            SettingsRow(
                title = "Concurrent downloads",
                subtitle = "${concurrent.coerceIn(1, 5)} at a time",
                onClick = {
                    preferences.concurrentDownloads().set(
                        ((concurrent % 5) + 1).coerceIn(1, 5),
                    )
                },
            )

            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
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
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
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
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
