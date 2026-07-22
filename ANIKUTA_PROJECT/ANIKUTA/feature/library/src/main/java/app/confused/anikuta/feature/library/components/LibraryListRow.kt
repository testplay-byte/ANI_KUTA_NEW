package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single library list row — small cover on the left, title/meta/date on the right.
 *
 * Per the prototype's `LibraryListRow`:
 * - Row container: 16dp horizontal / 4dp vertical outer padding, 10dp rounded,
 *   surfaceVariant 40% background, 8dp inner padding. Fades to 0.6 alpha when selected.
 * - Cover: 52x74dp, 4dp rounded.
 * - Optional selection check (top-end of cover) in selection mode.
 * - Right column: title, meta row (ep count, score), date-added line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListRow(
    item: Anime,
    selected: Boolean,
    selectionMode: Boolean,
    episodeBadgeMode: EpisodeBadgeMode,
    showScoreBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(8.dp)
            .alpha(if (selected) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover with optional selection checkmark.
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 74.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!item.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            if (selectionMode) {
                val checkBg = if (selected) MaterialTheme.colorScheme.primary
                else Color.Black.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(checkBg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = RobotoFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(3.dp))

            val metaParts = buildList {
                val epText = when (episodeBadgeMode) {
                    EpisodeBadgeMode.OFF -> null
                    EpisodeBadgeMode.TOTAL -> item.totalEpisodes?.takeIf { it > 0 }?.let { MetaPart.Episodes(it) }
                    EpisodeBadgeMode.RELEASED -> item.releasedEpisodes?.takeIf { it > 0 }?.let { MetaPart.Episodes(it) }
                }
                if (epText != null) {
                    add(epText)
                }
                val scoreVal = item.score
                if (showScoreBadge && scoreVal != null) {
                    add(MetaPart.Score(scoreVal))
                }
            }
            if (metaParts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metaParts.forEach { part ->
                        when (part) {
                            is MetaPart.Episodes -> Text(
                                text = "${part.count} ep",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = RobotoFamily,
                            )
                            is MetaPart.Score -> Text(
                                text = "\u2605 ${part.value.toInt()}",
                                color = AmberScoreColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = RobotoFamily,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Added ${formatRelativeDate(item.dateAdded)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RobotoFamily,
            )
        }
    }
}

/** Amber used for the score chip, matching the prototype's 0xFFFFCC80. */
private val AmberScoreColor: Color = Color(0xFFFFCC80)

/** Disjoint union so the meta row can mix episode + score entries without conditionals. */
private sealed interface MetaPart {
    data class Episodes(val count: Int) : MetaPart
    data class Score(val value: Double) : MetaPart
}

/**
 * Formats an epoch-ms timestamp as a short date string.
 *
 * Kept deliberately simple — we don't need "2 days ago" relative wording here,
 * just a stable human-readable date so the user can scan a list of entries.
 */
private fun formatRelativeDate(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        .format(Date(epochMs))
}
