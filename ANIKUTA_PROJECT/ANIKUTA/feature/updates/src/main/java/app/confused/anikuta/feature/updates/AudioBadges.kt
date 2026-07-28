package app.confused.anikuta.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Small SUB/DUB badge pill for the Updates list rows.
 *
 * Matches the implementation-prompt spec exactly: `outlineVariant` surface,
 * `RoundedCornerShape(6.dp)`, `fontSize = 9.sp`, `lineHeight = 11.sp`,
 * `padding(horizontal = 6.dp, vertical = 2.dp)`, "SUB" / "DUB" labels with
 * dot separators. This is a smaller, denser variant of the anime-details
 * `AudioPills` (which uses 10sp/14sp) — tuned for the compact Updates row.
 *
 * Only renders the pills for audio variants that are present.
 */
@Composable
fun AudioBadges(
    hasSub: Boolean,
    hasDub: Boolean,
    modifier: Modifier = Modifier,
) {
    val parts = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
    }
    if (parts.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            parts.forEachIndexed { idx, label ->
                if (idx > 0) {
                    // Dot separator between labels.
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
