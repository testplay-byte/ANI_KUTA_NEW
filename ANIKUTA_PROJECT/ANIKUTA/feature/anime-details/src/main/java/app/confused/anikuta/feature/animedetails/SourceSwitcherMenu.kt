package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The three-dot overflow menu for the unified details page.
 *
 * Replaces the no-op stub at `DetailBanner.kt`. The menu items adapt to the
 * current data source + link state (per owner feedback, 2026-07-27):
 *
 * **AniList mode** (viewing AniList data):
 * - "View from Extension" — if a source link exists (`sourceId != null`).
 *   Switches the displayed data source to Extension in-place.
 * - "Switch anime" — opens an AniList search sheet to navigate to a different
 *   anime (for when the current one is the wrong season/adaptation).
 * - "Refresh"
 * - "Current: AniList" (informational)
 *
 * **Extension mode, linked** (`anilistId != null`):
 * - "View from AniList" — switches to AniList data source in-place.
 * - "Switch anime" — opens the AniList linking sheet to re-link to a different
 *   AniList entry (for when the auto-match picked the wrong anime).
 * - "Refresh"
 * - "Current: {extension name}" (informational)
 *
 * **Extension mode, unlinked** (`anilistId == null`):
 * - "Link to AniList" — opens the AniList linking sheet to establish a link.
 *   Once linked, the view refreshes with AniList metadata merged in.
 * - "Refresh"
 * - "Current: {extension name}" (informational)
 *
 * **Removed** (per owner feedback): "Switch extension" — redundant with the
 * extension-switching affordance next to the episodes header (ManualSearchSheet).
 *
 * @param anime the current unified anime (drives which options are shown).
 * @param currentDataSource which source is currently displayed.
 * @param onSwitchDataSource called with the target [DataSource] for the
 *   "View from AniList" / "View from Extension" toggle.
 * @param onLinkToAniList called for "Link to AniList" (unlinked) and
 *   "Switch anime" (extension mode) — opens the AniList linking sheet via
 *   `AppController.startLinking(source, sAnime)`.
 * @param onSwitchAnilistAnime called for "Switch anime" (AniList mode) —
 *   opens the [AniListSearchSheet] to search + navigate to a different anime.
 * @param onRefresh called to pull-to-refresh.
 */
@Composable
fun SourceSwitcherMenu(
    anime: UnifiedAnime,
    currentDataSource: DataSource,
    onSwitchDataSource: (DataSource) -> Unit,
    onLinkToAniList: () -> Unit,
    onSwitchAnilistAnime: () -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // The three-dot trigger button.
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.size(40.dp),
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "More options",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        when (currentDataSource) {
            DataSource.ANILIST -> {
                // "View from Extension" — only if a source link exists.
                if (anime.sourceId != null) {
                    DropdownMenuItem(
                        text = { MenuText("View from Extension", subtitle = "Switch to ${anime.sourceName} data") },
                        leadingIcon = { MenuIcon(Icons.Outlined.SwapHoriz) },
                        onClick = {
                            expanded = false
                            onSwitchDataSource(DataSource.EXTENSION)
                        },
                    )
                }
                // "Switch anime" — search AniList for the correct entry.
                DropdownMenuItem(
                    text = { MenuText("Switch anime", subtitle = "Wrong anime? Find the correct one") },
                    leadingIcon = { MenuIcon(Icons.Outlined.FindInPage) },
                    onClick = {
                        expanded = false
                        onSwitchAnilistAnime()
                    },
                )
            }
            DataSource.EXTENSION -> {
                if (anime.anilistId != null) {
                    // Linked — "View from AniList" (switch view) + "Switch anime" (re-link).
                    DropdownMenuItem(
                        text = { MenuText("View from AniList", subtitle = "Switch to AniList metadata") },
                        leadingIcon = { MenuIcon(Icons.Outlined.SwapHoriz) },
                        onClick = {
                            expanded = false
                            onSwitchDataSource(DataSource.ANILIST)
                        },
                    )
                    DropdownMenuItem(
                        text = { MenuText("Switch anime", subtitle = "Wrong link? Find the correct AniList entry") },
                        leadingIcon = { MenuIcon(Icons.Outlined.FindInPage) },
                        onClick = {
                            expanded = false
                            onLinkToAniList()
                        },
                    )
                } else {
                    // Unlinked — "Link to AniList" (establish a link).
                    DropdownMenuItem(
                        text = { MenuText("Link to AniList", subtitle = "Search AniList + link this anime") },
                        leadingIcon = { MenuIcon(Icons.Outlined.Link) },
                        onClick = {
                            expanded = false
                            onLinkToAniList()
                        },
                    )
                }
            }
        }

        // ── Refresh (always) ──
        DropdownMenuItem(
            text = { MenuText("Refresh") },
            leadingIcon = { MenuIcon(Icons.Outlined.Cached) },
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
            leadingIcon = { MenuIcon(Icons.Outlined.AutoAwesome) },
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
