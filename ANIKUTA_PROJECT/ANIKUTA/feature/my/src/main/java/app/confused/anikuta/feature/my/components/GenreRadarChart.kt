package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * Genre Radar Chart (Kiviat / spider / star diagram).
 *
 * Shows the top genres as axes radiating from the center. Each axis length is
 * proportional to the genre's count. The shape is filled with the primary color
 * (semi-transparent) and outlined.
 *
 * Tapping a genre label opens the genre anime sheet.
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

    // Take top 8 genres for the radar (more = too cluttered)
    val sorted = genres.entries.sortedByDescending { it.value }.take(8)
    val maxCount = sorted.maxOf { it.value }.coerceAtLeast(1)

    // Colors captured before Canvas (Canvas lambda is not a composable scope)
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Genres")

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(8.dp),
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = minOf(centerX, centerY) * 0.65f
                val n = sorted.size

                // Draw concentric grid rings (3 levels)
                for (level in 1..3) {
                    val r = radius * level / 3f
                    val ringPath = Path()
                    for (i in 0 until n) {
                        val angle = (2.0 * Math.PI * i / n) - Math.PI / 2
                        val x = centerX + (r * Math.cos(angle)).toFloat()
                        val y = centerY + (r * Math.sin(angle)).toFloat()
                        if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                    }
                    ringPath.close()
                    drawPath(
                        path = ringPath,
                        color = outlineColor.copy(alpha = 0.3f),
                        style = Stroke(width = 1f),
                    )
                }

                // Draw axes (lines from center to each vertex)
                for (i in 0 until n) {
                    val angle = (2.0 * Math.PI * i / n) - Math.PI / 2
                    val x = centerX + (radius * Math.cos(angle)).toFloat()
                    val y = centerY + (radius * Math.sin(angle)).toFloat()
                    drawLine(
                        color = axisColor.copy(alpha = 0.3f),
                        start = Offset(centerX, centerY),
                        end = Offset(x, y),
                        strokeWidth = 1f,
                    )
                }

                // Draw the data polygon (filled + outlined)
                val dataPath = Path()
                for (i in 0 until n) {
                    val angle = (2.0 * Math.PI * i / n) - Math.PI / 2
                    val value = sorted[i].value.toFloat() / maxCount
                    val r = radius * value
                    val x = centerX + (r * Math.cos(angle)).toFloat()
                    val y = centerY + (r * Math.sin(angle)).toFloat()
                    if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                }
                dataPath.close()

                // Fill (semi-transparent primary)
                drawPath(
                    path = dataPath,
                    color = primaryColor.copy(alpha = 0.25f),
                )
                // Outline
                drawPath(
                    path = dataPath,
                    color = primaryColor,
                    style = Stroke(width = 2f),
                )

                // Draw data points (small circles at each vertex)
                for (i in 0 until n) {
                    val angle = (2.0 * Math.PI * i / n) - Math.PI / 2
                    val value = sorted[i].value.toFloat() / maxCount
                    val r = radius * value
                    val x = centerX + (r * Math.cos(angle)).toFloat()
                    val y = centerY + (r * Math.sin(angle)).toFloat()
                    drawCircle(
                        color = primaryColor,
                        radius = 4f,
                        center = Offset(x, y),
                    )
                }

                // Draw genre labels (positioned just outside the outer ring)
                for (i in 0 until n) {
                    val angle = (2.0 * Math.PI * i / n) - Math.PI / 2
                    val labelR = radius + 24f
                    val x = centerX + (labelR * Math.cos(angle)).toFloat()
                    val y = centerY + (labelR * Math.sin(angle)).toFloat()
                    val genreName = sorted[i].key
                    val textResult = textMeasurer.measure(
                        text = genreName,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor,
                            fontFamily = RobotoFamily,
                        ),
                    )
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

        // Genre legend below the chart (clickable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sorted.take(4).forEach { (genre, count) ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onGenreClick(genre) },
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
