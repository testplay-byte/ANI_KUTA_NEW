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
 * is proportional to the genre's count. The shape is filled with the primary
 * color (semi-transparent) and outlined.
 *
 * Features:
 * - Smart genre placement: longest names at top/bottom, shortest at left/right,
 *   other positions random (prevents label crowding on sides).
 * - Dynamic grid ring count: more rings for higher counts (max 30).
 * - Clickable labels: tapping a genre label opens the anime sheet.
 * - Text overlap prevention: radius adjusts based on label widths.
 * - Up to 16 genres supported.
 * - Horizontally scrollable legend below.
 *
 * Uses Compose Canvas (no external charting library — per design language rules).
 */
@Composable
fun GenreRadarChart(
    genres: Map<String, Int>,
    onGenreClick: (String) -> Unit,
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

    // Pre-measure all labels to compute the needed radius
    val measuredLabels = remember(placedGenres, labelFontSize) {
        placedGenres.map { entry ->
            textMeasurer.measure(
                text = "${entry.key} (${entry.value})",
                style = TextStyle(
                    fontSize = labelFontSize,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                    fontFamily = RobotoFamily,
                ),
            )
        }
    }

    // Compute the maximum label half-width to prevent overlap
    val maxLabelHalfWidth = measuredLabels.maxOf { it.size.width / 2f }
    val maxLabelHalfHeight = measuredLabels.maxOf { it.size.height / 2f }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Genres")

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(4.dp)
                    .pointerInput(n, placedGenres) {
                        detectTapGestures { tapOffset ->
                            // Check if tap is near any label position
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val radius = min(centerX, centerY) * 0.75f
                            val labelR = radius + maxLabelHalfWidth + 12f

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
                // Bigger radius (0.75 instead of 0.65) to use more space
                val radius = min(centerX, centerY) * 0.75f
                val labelR = radius + maxLabelHalfWidth + 12f

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
                        color = gridColor.copy(alpha = 0.5f), // More visible (was 0.3f)
                        style = Stroke(width = 1.5f), // Thicker (was 1f)
                    )
                }

                // Draw axes (lines from center to each vertex) — more visible
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()
                    drawLine(
                        color = axisColor.copy(alpha = 0.5f), // More visible (was 0.3f)
                        start = Offset(centerX, centerY),
                        end = Offset(x, y),
                        strokeWidth = 1.5f, // Thicker (was 1f)
                    )
                }

                // Draw the data polygon (filled + outlined)
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

                // Fill (semi-transparent primary — more visible)
                drawPath(
                    path = dataPath,
                    color = primaryColor.copy(alpha = 0.3f), // Was 0.25f
                )
                // Outline (thicker)
                drawPath(
                    path = dataPath,
                    color = primaryColor,
                    style = Stroke(width = 2.5f), // Was 2f
                )

                // Draw data points (circles at each vertex)
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val value = placedGenres[i].value.toFloat() / maxCount
                    val r = radius * value
                    val x = centerX + (r * cos(angle)).toFloat()
                    val y = centerY + (r * sin(angle)).toFloat()
                    drawCircle(
                        color = primaryColor,
                        radius = 5f,
                        center = Offset(x, y),
                    )
                }

                // Draw genre labels (positioned just outside the outer ring)
                for (i in 0 until n) {
                    val angle = (2.0 * PI * i / n) - PI / 2
                    val x = centerX + (labelR * cos(angle)).toFloat()
                    val y = centerY + (labelR * sin(angle)).toFloat()
                    val textResult = measuredLabels[i]
                    drawText(
                        textLayoutResult = textResult,
                        topLeft = Offset(
                            x - textResult.size.width / 2f,
                            y - textResult.size.height / 2f,
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
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                        color = MaterialTheme.colorScheme.onSurface,
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
 * - Shortest names go to right (index n/4) and left (index 3n/4) — horizontal
 *   positions where long names would overlap with the chart.
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

    // Place shortest 2 at right (index n/4) and left (index 3n/4)
    if (n >= 3) {
        val rightIdx = n / 4
        // Only place if the position is different from top/bottom
        if (rightIdx != 0 && rightIdx != n / 2) {
            result[rightIdx] = byLength[0] // shortest at right
        } else {
            // Find next available position near right
            for (i in 1 until n) {
                if (result[i] == null) {
                    result[i] = byLength[0]
                    break
                }
            }
        }
    }
    if (n >= 4) {
        val leftIdx = 3 * n / 4
        if (result[leftIdx] == null && leftIdx != 0 && leftIdx != n / 2) {
            result[leftIdx] = byLength[1] // second shortest at left
        } else {
            // Find next available position
            for (i in 1 until n) {
                if (result[i] == null) {
                    result[i] = byLength[1]
                    break
                }
            }
        }
    }

    // Fill remaining positions with the rest, shuffled randomly
    val placed = result.filterNotNull().toSet()
    val remaining = genres.filter { it !in placed }.shuffled()
    var remIdx = 0
    for (i in 0 until n) {
        if (result[i] == null) {
            result[i] = remaining.getOrNull(remIdx++) ?: genres[i]
        }
    }

    return result.filterNotNull()
}
