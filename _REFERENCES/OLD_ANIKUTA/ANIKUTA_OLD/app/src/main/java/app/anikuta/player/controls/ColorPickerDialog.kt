package app.anikuta.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A bottom-sheet color picker for subtitle colors. Shown from the bottom of
 * the screen (NOT a center popup) so the video player stays visible behind it
 * and the user can see subtitle colors changing in real time.
 *
 * Features:
 *  - A row of preset swatches (one-tap quick select)
 *  - RGB + Alpha sliders for custom color selection
 *  - A live preview + hex display
 *  - Live-applies the color on every change (onLiveChange) so the user sees
 *    the effect immediately. onConfirm/onDismiss just close the sheet.
 *
 * COLOR BUG FIX: The previous version called Compose's Color(a, r, g, b)
 * constructor, but that constructor is Color(red, green, blue, alpha). So the
 * preview showed the wrong color (channels swapped). Fixed to Color(r, g, b, a)
 * and the stored int is ARGB (alpha in the high bits), matching
 * AnikutaMPVView.colorToHex which sends #ARGB to MPV.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initialColor: Int,
    onLiveChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Extract ARGB channels from the stored int (ARGB layout: alpha in high byte).
    var a by remember { mutableIntStateOf((initialColor shr 24) and 0xFF) }
    var r by remember { mutableIntStateOf((initialColor shr 16) and 0xFF) }
    var g by remember { mutableIntStateOf((initialColor shr 8) and 0xFF) }
    var b by remember { mutableIntStateOf(initialColor and 0xFF) }

    fun currentInt() = (a shl 24) or (r shl 16) or (g shl 8) or b
    // Compose Color constructor is Color(red, green, blue, alpha) — NOT (a,r,g,b).
    val currentColor = Color(r, g, b, a)
    val hex = String.format("#%02X%02X%02X%02X", a, r, g, b)

    val presets = listOf(
        "White" to 0xFFFFFFFF.toInt(),
        "Black" to 0xFF000000.toInt(),
        "Yellow" to 0xFFFFFF00.toInt(),
        "Cyan" to 0xFF00FFFF.toInt(),
        "Red" to 0xFFFF0000.toInt(),
        "Green" to 0xFF00FF00.toInt(),
        "Blue" to 0xFF0000FF.toInt(),
        "Transparent" to 0x00000000,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            // ---- Title + preview row (compact) ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                )
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(hex, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- Preset swatches (compact) ----
            Text("Presets", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEach { (_, colorValue) ->
                    val c = Color(
                        red = (colorValue shr 16) and 0xFF,
                        green = (colorValue shr 8) and 0xFF,
                        blue = colorValue and 0xFF,
                        alpha = (colorValue shr 24) and 0xFF,
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable {
                                a = (colorValue shr 24) and 0xFF
                                r = (colorValue shr 16) and 0xFF
                                g = (colorValue shr 8) and 0xFF
                                b = colorValue and 0xFF
                                onLiveChange(currentInt())
                            },
                    )
                }
            }

            // ---- Custom RGB+A sliders (compact) ----
            Text("Custom", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
            ColorSliderRow("Red", r) { r = it; onLiveChange(currentInt()) }
            ColorSliderRow("Green", g) { g = it; onLiveChange(currentInt()) }
            ColorSliderRow("Blue", b) { b = it; onLiveChange(currentInt()) }
            ColorSliderRow("Alpha", a) { a = it; onLiveChange(currentInt()) }

            Spacer(modifier = Modifier.height(4.dp))
            // Done button — just closes the sheet (changes were applied live)
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun ColorSliderRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text("$value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.fillMaxWidth().height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}
