package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.min

/**
 * Genre Radar Chart (Kiviat / spider / star diagram).
 *
 * Shows up to 16 top genres as axes radiating from the center. Each axis length
 * is proportional to the genre's count. Each axis has its own color intensity —
 * the most frequent genre gets the most vivid color, less frequent genres get
 * less intense colors.
 *
 * Features:
 * - Smart genre placement: longest names at top/bottom, shortest at left/right
 *   (only the shortest genres belong on the sides; if multiple have the same
 *   length, they can switch randomly). Other positions are random.
 * - Dynamic grid ring count: more rings for higher counts (max 30).
 * - Clickable labels: tapping a genre label opens the anime sheet.
 * - Text overlap prevention: radius adjusts based on label widths + container bounds.
 * - Up to 16 genres supported.
 * - Horizontally scrollable legend below with highlight for selected genre.
 * - Per-genre color intensity (most frequent = most vivid).
 *
 * Uses Compose Canvas (no external charting library — per design language rules).
 */
@Composable
fun GenreRadarChart(
    genres: Map<String, Int>,
    onGenreClick: (String) -> Unit,
    selectedGenre: String? = null,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return

    // Take up to 16 genres (sorted by count descending)
    val topGenres = genres.entries.sortedByDescending { it.value }.take(16)
    val maxCount = topGenres.maxOf { it.value }.coerceAtLeast(1)
    val n = topGenres.size

    // Smart placement: longest names at top/bottom, shortest at left/right, rest random
    val placedGenres = remember(topGenres) { placeGenresByLabelLength(topGenres) }

    // Dynamic grid ring count: based on maxCount, capped at 30, min 3
    val gridRings = min(maxCount.coerceAtLeast(3), 30)

    // Colors captured before Canvas (Canvas lambda is not a composable scope)
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()

    // Font size adapts to genre count (smaller if many genres)
    val labelFontSize = if (n <= 8) 11.sp else if (n <= 12) 10.sp else 9.sp

    // Pre-measure all labels (genre name only — no count)
    val measuredLabels = remember(placedGenres, labelFontSize) {
        placedGenres.map { entry ->
            textMeasurer.measure(
                text = entry.key,
                style = TextStyle(
                    fontSize = labelFontSize,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                    fontFamily = RobotoFamily,
                ),
            )
        }
    }

    // Compute per-genre color intensity (most frequent = most vivid)
    val genreColors = remember(placedGenres, maxCount, primaryColor) {
        placedGenres.map { entry ->
            // Intensity: 0.4 (least frequent) to 1.0 (most frequent)
            val intensity = 0.4f + 0.6f * (entry.value.toFloat() / maxCount)
            primaryColor.copy(alpha = intensity)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Genres")

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(4.dp)
                    .pointerInput(n, placedGenres) {
                        detectTapGestures { tapOffset ->
                            // Check if tap is near any label position
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            // Bigger radius (0.78) to use more space
                            val radius = min(centerX, centerY) * 0.78f
                            // Label radius based on max label width, but clamped to container
                            val maxLabelW = measuredLabels.maxOf { it.size.width / 2f }
                            val labelR = (radius + maxLabelW + 8f).coerceAtMost(centerX - 8f)

                            for (i in 0 until n) {
                                val angle = (2.0 * PI * i / n) - PI / 2
                                val x = centerX + (labelR * cos(angle)).toFloat()
                                val y = centerY + (labelR * sin(angle)).toFloat()
                                val labelWidth = measuredLabels[i].size.width
                                val labelHeight = measuredLabels[i].size.height
                                // Check if tap is within the label bounds
                                if (tapOffset.x >= x - labelWidth / 2f - 8f &&
                                    tapOffset.x <= x + labelWidth / 2f + 8f &&
                                    tapOffset.y >= y - labelHeight / 2f - 8f &&
                                    tapOffset.y <= y + labelHeight / 2f + 8f
                                ) {
                                    onGenreClick(placedGenres[i].key)
                                    break
                                }
                            }
                        }
                    },
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                // Bigger radius (0.78) to use more space
                val radius = min(centerX, centerY) * 0.78f
                // Label radius based on max label width, but clamped to container
                val maxLabelW = measuredLabels.maxOf { it.size.width / 2f }
                val labelR = (radius + maxLabelW + 8f).coerceAtMost(centerX - 4f)

                // Draw concentric grid rings (dynamic count, more visible)
                for (level in 1..gridRings) {
                    val r = radius * level / gridRings.toFloat()
                    val ringPath = Path()
                    for (i in 0 until n) {
                        val angle = (2.0 * PI * i / n) - PI / 2
                        val x = centerX + (r * cos(angle)).toFloat()
                        val y = centerY + (r * sin(angle)).toFloat()
                        if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                    }
                    ringPath.close()
                    drawPath(
                        path = ringPath,
                        color = gridColor.copy(alpha = 0.5f),
                        style = Stroke(width = 1.5f),
                    )
                }

                // Draw axes (lines from center to each vertex) — per-genre color
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()
                    drawLine(
                        color = genreColors[i].copy(alpha = 0.6f),
                        start = Offset(centerX, centerY),
                        end = Offset(x, y),
                        strokeWidth = 1.5f,
                    )
                }

                // Draw the data polygon (filled + outlined) — per-genre colored vertices
                // Use a gradient fill based on primary color
                val dataPath = Path()
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val value = placedGenres[i].value.toFloat() / maxCount
                    val r = radius * value
                    val x = centerX + (r * cos(angle)).toFloat()
                    val y = centerY + (r * sin(angle)).toFloat()
                    if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                }
                dataPath.close()

                // Fill (semi-transparent primary)
                drawPath(
                    path = dataPath,
                    color = primaryColor.copy(alpha = 0.3f),
                )
                // Outline (thicker)
                drawPath(
                    path = dataPath,
                    color = primaryColor,
                    style = Stroke(width = 2.5f),
                )

                // Draw data points (circles at each vertex) — per-genre color
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val value = placedGenres[i].value.toFloat() / maxCount
                    val r = radius * value
                    val x = centerX + (r * cos(angle)).toFloat()
                    val y = centerY + (r * sin(angle)).toFloat()
                    drawCircle(
                        color = genreColors[i],
                        radius = 5f,
                        center = Offset(x, y),
                    )
                }

                // Draw genre labels (positioned just outside the outer ring)
                // Labels are clamped to stay within the container bounds
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val x = centerX + (labelR * cos(angle)).toFloat()
                    val y = centerY + (labelR * sin(angle)).toFloat()
                    val textResult = measuredLabels[i]
                    val textW = textResult.size.width
                    val textH = textResult.size.height
                    // Clamp label position so it stays within the canvas bounds
                    val clampedX = (x - textW / 2f).coerceIn(2f, size.width - textW - 2f) + textW / 2f
                    val clampedY = (y - textH / 2f).coerceIn(2f, size.height - textH - 2f) + textH / 2f
                    drawText(
                        textLayoutResult = textResult,
                        topLeft = Offset(
                            clampedX - textW / 2f,
                            clampedY - textH / 2f,
                        ),
                    )
                }
            }
        }

        // Genre legend below the chart — horizontally scrollable (LazyRow)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(topGenres) { (genre, count) ->
                val isSelected = genre == selectedGenre
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.pointerInput(genre) {
                        detectTapGestures { onGenreClick(genre) }
                    },
                ) {
                    Text(
                        text = "$genre  $count",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            androidx.compose.ui.graphics.Color.Black
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Smart genre placement algorithm.
 *
 * Places genres so that:
 * - Longest names go to top (index 0) and bottom (index n/2) — vertical positions
 *   where there's more vertical space for long text.
 * - Shortest names go to right (index n/4) and left (index 3n/4) — ONLY the shortest
 *   genres belong on the sides. If multiple genres have the same shortest length,
 *   two of them are randomly selected for the sides, and the rest go to other positions.
 * - Remaining positions are filled randomly.
 *
 * This prevents long labels from crowding the sides of the chart where they
 * would overlap with adjacent labels.
 */
private fun placeGenresByLabelLength(
    genres: List<Map.Entry<String, Int>>,
): List<Map.Entry<String, Int>> {
    val n = genres.size
    if (n <= 1) return genres

    val result = arrayOfNulls<Map.Entry<String, Int>>(n)

    // Sort by label length (shortest first)
    val byLength = genres.sortedBy { it.key.length }

    // Place longest 2 at top (index 0) and bottom (index n/2)
    result[0] = byLength.last() // longest at top
    if (n >= 2) {
        val bottomIdx = if (n == 2) 1 else n / 2
        result[bottomIdx] = byLength[byLength.size - 2] // second longest at bottom
    }

    // Find the shortest genres (those with the minimum character count).
    // If multiple have the same shortest length, pick 2 randomly for the sides.
    if (n >= 3) {
        val minLen = byLength[0].key.length
        val shortestGenres = byLength.filter { it.key.length == minLen }
        val shortestShuffled = shortestGenres.shuffled()
        val placed = result.filterNotNull().toMutableSet()

        // Right position (index n/4)
        val rightIdx = n / 4
        if (rightIdx != 0 && rightIdx != n / 2 && result[rightIdx] == null) {
            val rightGenre = shortestShuffled.getOrNull(0)
            if (rightGenre != null && rightGenre !in placed) {
                result[rightIdx] = rightGenre
                placed.add(rightGenre)
            }
        }

        // Left position (index 3n/4)
        if (n >= 4) {
            val leftIdx = 3 * n / 4
            if (leftIdx != 0 && leftIdx != n / 2 && result[leftIdx] == null) {
                val leftGenre = shortestShuffled.getOrNull(1) ?: shortestShuffled.getOrNull(0)
                if (leftGenre != null && leftGenre !in placed) {
                    result[leftIdx] = leftGenre
                    placed.add(leftGenre)
                }
            }
        }

        // If the right/left positions were already taken by top/bottom,
        // place the shortest genres at the next available random positions.
        for (shortGenre in shortestShuffled) {
            if (shortGenre in placed) continue
            // Find a random available position
            val available = (0 until n).filter { result[it] == null }
            if (available.isNotEmpty()) {
                val pos = available.random()
                result[pos] = shortGenre
                placed.add(shortGenre)
            }
        }
    }

    // Fill remaining positions with the rest, shuffled randomly
    val placedSet = result.filterNotNull().toSet()
    val remaining = genres.filter { it !in placedSet }.shuffled()
    var remIdx = 0
    for (i in 0 until n) {
        if (result[i] == null) {
            result[i] = remaining.getOrNull(remIdx++) ?: genres[i]
        }
    }

    return result.filterNotNull()
}
