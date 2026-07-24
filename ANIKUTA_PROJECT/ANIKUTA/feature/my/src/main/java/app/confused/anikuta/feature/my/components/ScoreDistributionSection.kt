package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Score Distribution section — vertical bar chart showing score buckets (1-10).
 *
 * Each bar represents a score bucket (1-10). The bar height is proportional
 * to the count. The score number is shown below each bar. Uses Compose Canvas
 * for the bars (no external charting library).
 */
@Composable
fun ScoreDistributionSection(
    scoreDistribution: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    if (scoreDistribution.isEmpty()) return

    // Ensure all buckets 1-10 are present (0 if no anime)
    val buckets = (1..10).map { bucket ->
        bucket to (scoreDistribution[bucket] ?: 0)
    }
    val maxCount = buckets.maxOf { it.second }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Scores")

        // Bar chart row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            buckets.forEach { (bucket, count) ->
                ScoreBar(
                    bucket = bucket,
                    count = count,
                    maxCount = maxCount,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScoreBar(
    bucket: Int,
    count: Int,
    maxCount: Int,
    modifier: Modifier = Modifier,
) {
    // Capture colors before Canvas (Canvas lambda is not a composable scope)
    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Count above bar (if > 0)
        if (count > 0) {
            Text(
                text = count.toString(),
                fontFamily = RobotoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(modifier = Modifier.height(11.dp))
        }

        // Bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
        ) {
            val barHeight = (size.height * (count.toFloat() / maxCount)).coerceAtLeast(2f)
            drawRoundRect(
                color = if (count > 0) primaryColor else inactiveColor,
                size = Size(size.width, barHeight),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - barHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Score label below bar
        Text(
            text = bucket.toString(),
            fontFamily = RobotoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
