package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Section 3: Genre Distribution — clickable chips/pills (NOT bar chart).
 *
 * Each chip shows the genre name + count. Clicking a chip opens a sheet
 * showing anime in that genre (random selection each time).
 *
 * Design: chips use surfaceVariant background (alpha 0.4f) with RoundedCornerShape
 * (16dp for pill shape). Top genres shown first (sorted by count descending).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChipsSection(
    genres: Map<String, Int>,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return

    val sorted = genres.entries.sortedByDescending { it.value }.take(15)

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        SectionHeader("Genres")

        // FlowRow of clickable chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sorted.forEach { (genre, count) ->
                GenreChip(
                    genre = genre,
                    count = count,
                    onClick = { onGenreClick(genre) },
                )
            }
        }
    }
}

@Composable
private fun GenreChip(
    genre: String,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "$genre  $count",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Reusable section header — accent-colored, left-aligned, uppercase. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.06.sp,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
