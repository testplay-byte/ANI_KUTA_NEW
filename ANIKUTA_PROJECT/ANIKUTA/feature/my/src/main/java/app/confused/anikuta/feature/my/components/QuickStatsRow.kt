package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Section 2: Quick Stats — a horizontal row of 4 stat cards.
 *
 * Shows: Total Anime, Total Episodes, Watch Time, Mean Score.
 *
 * Design: label on TOP (bold, onSurface/white), value BELOW (primary color,
 * ExtraBold). Matches the More page entry card style (surfaceVariant alpha
 * 0.4f, RoundedCornerShape 12dp). Labels use short text to avoid wrapping.
 */
@Composable
fun QuickStatsRow(
    totalAnime: Int,
    totalEpisodes: Int,
    totalWatchTimeMinutes: Int,
    meanScore: Double?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            label = "Anime",
            value = totalAnime.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Episodes",
            value = totalEpisodes.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Time",
            value = formatWatchTime(totalWatchTimeMinutes),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Score",
            value = meanScore?.let { "%.1f".format(it) } ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Label on top — bold, onSurface (white in dark theme)
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            // Value below — primary color, ExtraBold
            Text(
                text = value,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Format watch time as "Xd Yh" or "Yh Zm" depending on magnitude. */
fun formatWatchTime(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val days = minutes / (60 * 24)
    val hours = (minutes % (60 * 24)) / 60
    val mins = minutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}
