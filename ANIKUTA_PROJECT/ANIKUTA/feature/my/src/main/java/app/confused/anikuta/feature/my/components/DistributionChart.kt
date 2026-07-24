package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A horizontal bar chart showing a distribution (format, country, etc.).
 *
 * Uses Compose Canvas (no external charting library — per design language rules).
 * Each entry: label on the left, bar (primary-colored) in the middle, count on
 * the right. Bars are proportional to the max count.
 *
 * Note: For genres, use [GenreChipsSection] instead (clickable chips).
 * For status, use [StatusDistributionSection] instead (cards).
 * For scores, use [ScoreDistributionSection] instead (vertical bars).
 */
@Composable
fun DistributionChart(
    title: String,
    entries: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    maxEntries: Int = 10,
) {
    if (entries.isEmpty()) return

    val sorted = entries.sortedByDescending { it.second }.take(maxEntries)
    val maxCount = sorted.maxOf { it.second }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title)

        // Wrap all bars in a single card for a cleaner look
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                sorted.forEach { (label, count) ->
                    BarRow(
                        label = label,
                        count = count,
                        maxCount = maxCount,
                        barColor = barColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarRow(
    label: String,
    count: Int,
    maxCount: Int,
    barColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label (fixed width, left-aligned)
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Bar (Canvas, proportional to maxCount)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
        ) {
            val barWidth = (size.width * (count.toFloat() / maxCount)).coerceAtLeast(4f)
            drawRoundRect(
                color = barColor,
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Count
        Text(
            text = count.toString(),
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
    }
}
