package app.confused.anikuta.core.episodemetadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CustomToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Metadata Settings Sheet — controls episode metadata fetching.
 *
 * - Master toggle: enable/disable metadata fetching entirely
 * - Per-field toggles: thumbnails, titles, summaries, air dates
 * - When master is off, per-field toggles are hidden
 *
 * Per design language: dragHandle = null (principle #2), max 60% screen height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSettingsSheet(
    prefs: EpisodeMetadataPreferences,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.6f

    var enabled by remember { mutableStateOf(prefs.enabled().get()) }
    var fetchThumbnails by remember { mutableStateOf(prefs.fetchThumbnails().get()) }
    var fetchTitles by remember { mutableStateOf(prefs.fetchTitles().get()) }
    var fetchSummaries by remember { mutableStateOf(prefs.fetchSummaries().get()) }
    var fetchAirDates by remember { mutableStateOf(prefs.fetchAirDates().get()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Episode Metadata",
                    fontFamily = RobotoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(32.dp).clickable { onDismiss() },
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Master toggle ──
            Text(
                text = "Fetch episode metadata from online sources (Jikan, Anikage, AniList). Enriches the episode list with titles, descriptions, thumbnails, and air dates.",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            MetadataToggleRow("Enable metadata fetching", enabled) { v ->
                enabled = v
                prefs.enabled().set(v)
            }

            // ── Per-field toggles (only visible when master is on) ──
            if (enabled) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "FETCH FIELDS",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MetadataToggleRow("Thumbnails", fetchThumbnails) { v -> fetchThumbnails = v; prefs.fetchThumbnails().set(v) }
                MetadataToggleRow("Titles", fetchTitles) { v -> fetchTitles = v; prefs.fetchTitles().set(v) }
                MetadataToggleRow("Descriptions", fetchSummaries) { v -> fetchSummaries = v; prefs.fetchSummaries().set(v) }
                MetadataToggleRow("Air dates", fetchAirDates) { v -> fetchAirDates = v; prefs.fetchAirDates().set(v) }
            }
        }
    }
}

@Composable
private fun MetadataToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        CustomToggle(checked = checked, onChange = onChange)
    }
}
