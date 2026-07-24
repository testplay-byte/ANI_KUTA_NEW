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
import app.confused.anikuta.core.tracker.BehindAnime

/**
 * Behind Status tab content — summary cards at the top + behind anime list.
 *
 * Summary cards show:
 * - Total anime in library
 * - Total caught up (watched >= released)
 * - Total behind (watched < released)
 *
 * Below the cards: the list of behind anime (reuses BehindStatusSection).
 */
@Composable
fun BehindStatusTab(
    totalAnime: Int,
    behindAnime: List<BehindAnime>,
    onOpenAnime: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val caughtUp = totalAnime - behindAnime.size

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryCard(
                label = "Total",
                value = totalAnime.toString(),
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Caught Up",
                value = caughtUp.toString(),
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Behind",
                value = behindAnime.size.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        // Behind anime list
        BehindStatusSection(
            behindAnime = behindAnime,
            onOpenAnime = onOpenAnime,
        )
    }
}

@Composable
private fun SummaryCard(
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
            Text(
                text = value,
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
