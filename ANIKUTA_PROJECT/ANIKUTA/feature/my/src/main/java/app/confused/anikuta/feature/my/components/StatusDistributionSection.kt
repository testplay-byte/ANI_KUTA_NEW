package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Status Distribution section — shows the release status of library anime
 * (Finished, Releasing, Not Yet Released, Cancelled, On Hiatus).
 *
 * Uses compact cards in a row instead of a bar chart. Each card shows the
 * status name and count. Cards use the same surfaceVariant background as
 * More page entries (alpha 0.4f, RoundedCornerShape 12dp).
 */
@Composable
fun StatusDistributionSection(
    statusDistribution: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    if (statusDistribution.isEmpty()) return

    val sorted = statusDistribution.entries.sortedByDescending { it.value }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Status")

        // Cards in a row (wrap if needed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sorted.forEach { (status, count) ->
                StatusCard(
                    label = status,
                    count = count,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
