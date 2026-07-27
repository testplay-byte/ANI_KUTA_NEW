package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The three-dot overflow menu for the unified details page (doc 05 §1.5).
 *
 * Replaces the no-op stub at `DetailBanner.kt:116`. Shows data-source
 * switching options + refresh + extension switching, conditionally per
 * the current mode + available links:
 *
 * - **"View from AniList"** — shown when in Extension mode AND the anime is
 *   linked to AniList (`anilistId != null`). Switches [DataSource] to ANILIST.
 * - **"View from Extension"** — shown when in AniList mode AND a source link
 *   exists (`sourceId != null`). Switches [DataSource] to EXTENSION.
 * - **"Switch extension..."** — shown when in Extension mode. Opens the
 *   ManualSearchSheet (via [onSwitchExtension]) to pick a different source.
 * - **"Refresh"** — always shown. Triggers [onRefresh].
 *
 * Works on BOTH modes (per owner requirement B). Switching refreshes the page
 * in-place — no navigation, no new DB row.
 *
 * @param anime the current unified anime (drives which options are enabled).
 * @param currentDataSource which source is currently displayed.
 * @param onSwitchDataSource called with the target [DataSource].
 * @param onSwitchExtension called to open the ManualSearchSheet for extension switching.
 * @param onRefresh called to pull-to-refresh.
 */
@Composable
fun SourceSwitcherMenu(
    anime: UnifiedAnime,
    currentDataSource: DataSource,
    onSwitchDataSource: (DataSource) -> Unit,
    onSwitchExtension: () -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // The three-dot trigger button.
    Surface(
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.size(40.dp),
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "More options",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        // ── View from AniList (Extension → AniList switch) ──
        if (currentDataSource == DataSource.EXTENSION && anime.anilistId != null) {
            DropdownMenuItem(
                text = {
                    MenuText("View from AniList", subtitle = "Switch to AniList metadata")
                },
                leadingIcon = { MenuIcon(Icons.Outlined.SwapHoriz) },
                onClick = {
                    expanded = false
                    onSwitchDataSource(DataSource.ANILIST)
                },
            )
        }

        // ── View from Extension (AniList → Extension switch) ──
        if (currentDataSource == DataSource.ANILIST && anime.sourceId != null) {
            DropdownMenuItem(
                text = {
                    MenuText("View from Extension", subtitle = "Switch to ${anime.sourceName} data")
                },
                leadingIcon = { MenuIcon(Icons.Outlined.SwapHoriz) },
                onClick = {
                    expanded = false
                    onSwitchDataSource(DataSource.EXTENSION)
                },
            )
        }

        // ── Switch extension (pick a different source) ──
        if (currentDataSource == DataSource.EXTENSION) {
            DropdownMenuItem(
                text = {
                    MenuText("Switch extension", subtitle = "Search this anime on other sources")
                },
                leadingIcon = { MenuIcon(Icons.Outlined.Search) },
                onClick = {
                    expanded = false
                    onSwitchExtension()
                },
            )
        }

        // ── Refresh ──
        DropdownMenuItem(
            text = { MenuText("Refresh") },
            leadingIcon = { MenuIcon(Icons.Outlined.Refresh) },
            onClick = {
                expanded = false
                onRefresh()
            },
        )

        // ── Data-source indicator (informational) ──
        DropdownMenuItem(
            text = {
                MenuText(
                    "Current: ${anime.sourceName}",
                    subtitle = "Data source: ${currentDataSource.name.lowercase()}",
                )
            },
            leadingIcon = { MenuIcon(Icons.Outlined.VideoLibrary) },
            onClick = { expanded = false },
        )
    }
}

@Composable
private fun MenuText(text: String, subtitle: String? = null) {
    androidx.compose.foundation.layout.Column {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MenuIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
    )
}
