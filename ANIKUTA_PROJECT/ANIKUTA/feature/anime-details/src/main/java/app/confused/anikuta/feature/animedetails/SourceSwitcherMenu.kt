package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
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
 * Menu items adapt to [entryMode] (how the user entered the page) + [currentDataSource]
 * (what's currently displayed) + link state:
 *
 * **AniList entry** (`entryMode == ANILIST` — user opened from browse/search/home):
 * - "View from Extension" (if a source link exists) — switches the view in-place.
 * - "Refresh"
 * - "Current: AniList" (informational, faded)
 *
 *   **No "Switch anime"** — the user is already on the correct AniList entry.
 *
 * **Extension entry, linked** (`entryMode == EXTENSION`, `anilistId != null`):
 * - "View from AniList" (if currently on Extension) / "View from Extension" (if on AniList)
 * - "Switch anime" — opens the AniList linking sheet to re-link to a different entry
 *   (for when the auto-match picked the wrong anime).
 * - "Refresh"
 * - "Current: {source}" (informational, faded)
 *
 * **Extension entry, unlinked** (`entryMode == EXTENSION`, `anilistId == null`):
 * - "Link to AniList" — opens the AniList linking sheet to establish a link.
 * - "Refresh"
 * - "Current: {source}" (informational, faded)
 *
 * @param entryMode how the user ENTERED the page (fixed for the screen's lifetime).
 *   Drives whether "Switch anime" / "Link to AniList" is shown.
 * @param currentDataSource what's currently displayed (changes when the user toggles view).
 *   Drives whether "View from AniList" or "View from Extension" is shown.
 */
@Composable
fun SourceSwitcherMenu(
    anime: UnifiedAnime,
    currentDataSource: DataSource,
    entryMode: DataSource,
    onSwitchDataSource: (DataSource) -> Unit,
    onLinkToAniList: () -> Unit,
    onSwitchAnime: () -> Unit,
    onUnlinkFromAniList: () -> Unit = {},
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
        // ── View-source toggle ──
        when (currentDataSource) {
            DataSource.ANILIST -> {
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
            }
            DataSource.EXTENSION -> {
                if (anime.anilistId != null) {
                    DropdownMenuItem(
                        text = { MenuText("View from AniList", subtitle = "Switch to AniList metadata") },
                        leadingIcon = { MenuIcon(Icons.Outlined.SwapHoriz) },
                        onClick = {
                            expanded = false
                            onSwitchDataSource(DataSource.ANILIST)
                        },
                    )
                }
            }
        }

        // ── Link / Switch anime — shown whenever there's a source link ──
        // The user may have opened from extensions (auto-link landed on AniList page)
        // OR opened from AniList (auto-matched a source). In both cases, the auto-match
        // link might be wrong, so the user should be able to correct it.
        // entryMode is kept for future use but doesn't gate this option.
        if (anime.sourceId != null || entryMode == DataSource.EXTENSION) {
            if (anime.anilistId != null) {
                // Linked — "Switch anime" opens the AniList search sheet (NOT auto-link).
                DropdownMenuItem(
                    text = { MenuText("Switch anime", subtitle = "Wrong link? Find the correct AniList entry") },
                    leadingIcon = { MenuIcon(Icons.Outlined.FindInPage) },
                    onClick = {
                        expanded = false
                        onSwitchAnime()
                    },
                )
            } else {
                // Unlinked — "Link to AniList" opens the ExtensionLinkingSheet.
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

        // ── Unlink from AniList — only when the anime is linked to BOTH AniList AND an extension ──
        // Removes both directional links (SourceLinkStore + ExtensionLinkStore) +
        // the view preference, then navigates to the extension-mode details page.
        // The anime remains in the library with its extension data intact.
        // Gated on `sourceId != null && sourceId > 0` because unlinking makes no
        // sense for a pure-AniList entry (no extension to "shift to").
        if (anime.anilistId != null && anime.sourceId != null && anime.sourceId > 0) {
            DropdownMenuItem(
                text = { MenuText("Unlink from AniList", subtitle = "Remove the AniList association") },
                leadingIcon = { MenuIcon(Icons.Outlined.LinkOff) },
                onClick = {
                    expanded = false
                    onUnlinkFromAniList()
                },
            )
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

        // ── Data-source indicator (informational — visually faded/disabled) ──
        // Per owner feedback: this item should look non-interactive (faded colors)
        // to signal it's not a clickable feature. The two lines are wrapped in a
        // Column with proper spacing to prevent overlap.
        DropdownMenuItem(
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Current: ${anime.sourceName}",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "Data source: ${currentDataSource.name.lowercase()}",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp),
                )
            },
            enabled = false,  // visually disabled — no ripple, no click effect
            onClick = { /* no-op — informational only */ },
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
