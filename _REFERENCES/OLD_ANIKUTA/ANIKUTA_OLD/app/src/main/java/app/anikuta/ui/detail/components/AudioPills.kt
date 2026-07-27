package app.anikuta.ui.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Audio pills (SUB / DUB / HSUB) — adaptive width management.
 *
 * ## Heuristic
 *
 * - **2+ audio versions**: Labels shorten to their first letter (SUB→S, DUB→D,
 *   HSUB→H) with dot separators, e.g. "S•D". This guarantees they fit on one row.
 * - **1 audio version**: The full label is shown (e.g., "SUB").
 *
 * ## Why not BoxWithConstraints
 *
 * [BoxWithConstraints] is a `SubcomposeLayout` which crashes when placed
 * inside a `Row(height(IntrinsicSize.Min))` because intrinsic measurement
 * of subcompose layouts is not supported. The heuristic above avoids this
 * entirely by using a fixed-width approach that works in all layouts.
 *
 * @param hasSub  Whether a subbed version is available.
 * @param hasDub  Whether a dubbed version is available.
 * @param hasHsub Whether a hardsubbed version is available.
 */
@Composable
internal fun AudioPills(
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
) {
    if (!hasSub && !hasDub && !hasHsub) return

    data class Audio(val full: String, val short: String)
    val parts = buildList {
        if (hasSub) add(Audio("SUB", "S"))
        if (hasDub) add(Audio("DUB", "D"))
        if (hasHsub) add(Audio("HSUB", "H"))
    }

    val useShort = parts.size >= 2

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            parts.forEachIndexed { idx, audio ->
                if (idx > 0) {
                    // Circular dot separator
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                CircleShape,
                            ),
                    )
                }
                Text(
                    text = if (useShort) audio.short else audio.full,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
