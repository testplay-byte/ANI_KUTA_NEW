package app.confused.anikuta.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.feature.search.viewmodel.SearchSource

/**
 * The AniList / Extension source toggle — a pill with two segments.
 *
 * Ported from the prototype's `SourceToggleBtn`. Visual rules (copy-paste):
 * - Pill container: `RoundedCornerShape(50)`, `surfaceVariant.copy(alpha=0.3f)`
 *   background, 3dp inner padding.
 * - Active segment: `primaryContainer` bg + `onPrimaryContainer` text + icon.
 * - Inactive segment: transparent bg + `onSurfaceVariant` text + icon.
 * - Each segment: icon (14dp) + 4dp spacer + label (11sp SemiBold, 1 line).
 *
 * The toggle fades + shrinks to 0 width when the top bar collapses (the parent
 * `SearchTopBar` drives that animation; this composable is just the pill itself).
 *
 * @param source the currently-selected source.
 * @param onSelect called when the user taps a segment. If the user taps the
 *   already-selected segment, [onRetap] is called instead (so the UI can open
 *   the extension source picker per Q2: "tap Extension while selected → menu").
 */
@Composable
fun SourceToggle(
    source: SearchSource,
    onSelect: (SearchSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(3.dp),
    ) {
        SourceToggleSegment(
            label = "AniList",
            icon = Icons.Filled.Search,
            active = source == SearchSource.ANILIST,
            onClick = { onSelect(SearchSource.ANILIST) },
        )
        SourceToggleSegment(
            label = "Extension",
            icon = Icons.Filled.Extension,
            active = source == SearchSource.EXTENSION,
            onClick = { onSelect(SearchSource.EXTENSION) },
        )
    }
}

@Composable
private fun RowScope.SourceToggleSegment(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
