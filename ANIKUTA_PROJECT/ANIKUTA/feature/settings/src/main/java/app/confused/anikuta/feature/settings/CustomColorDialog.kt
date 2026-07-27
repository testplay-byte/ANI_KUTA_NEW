package app.confused.anikuta.feature.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.preferences.PaletteMode

/**
 * A popup dialog for creating/editing a custom color palette.
 *
 * Per owner spec (Session 1 items 8 + 9.5):
 * - **Simplified mode (default):** the user picks only the accent color via
 *   hex input + RGB sliders. The background/card/text colors are derived
 *   automatically.
 * - **Advanced mode:** revealed by tapping "Advanced" — the user can manually
 *   set the accent, background, card, AND text colors. Each has its own picker.
 *
 * The dialog has an **OK** button that applies the colors and dismisses.
 *
 * @param initialAccent The starting accent color.
 * @param initialBackground The starting background color (for advanced mode).
 * @param initialCard The starting card color (for advanced mode).
 * @param initialText The starting text color (for advanced mode).
 * @param initialPaletteMode The starting palette mode (SIMPLIFIED or FULL).
 * @param onApply Called with the chosen colors + palette mode when OK is pressed.
 * @param onDismiss Called when the user cancels (back gesture or tap outside).
 */
@Composable
fun CustomColorDialog(
    initialAccent: Color,
    initialBackground: Color,
    initialCard: Color,
    initialText: Color,
    initialPaletteMode: PaletteMode,
    onApply: (accent: Color, background: Color, card: Color, text: Color, mode: PaletteMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var accent by remember { mutableStateOf(initialAccent) }
    var background by remember { mutableStateOf(initialBackground) }
    var card by remember { mutableStateOf(initialCard) }
    var text by remember { mutableStateOf(initialText) }
    var paletteMode by remember { mutableStateOf(initialPaletteMode) }
    var showAdvanced by remember { mutableStateOf(initialPaletteMode == PaletteMode.FULL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Custom palette",
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Accent color picker (always shown) ──
                ColorPickerSection(
                    label = "Accent color",
                    color = accent,
                    onColorChange = { accent = it },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Advanced toggle ──
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Advanced palette customization",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // ── Advanced section (collapsible) ──
                AnimatedVisibility(visible = showAdvanced) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Set each palette color individually. The accent stays as above.",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        ColorPickerSection(
                            label = "Background color",
                            color = background,
                            onColorChange = { background = it },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ColorPickerSection(
                            label = "Card background color",
                            color = card,
                            onColorChange = { card = it },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ColorPickerSection(
                            label = "Text color",
                            color = text,
                            onColorChange = { text = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mode = if (showAdvanced) PaletteMode.FULL else PaletteMode.SIMPLIFIED
                onApply(accent, background, card, text, mode)
            }) {
                Text("OK", fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = RobotoFamily)
            }
        },
    )
}

/**
 * A labeled color picker section: a preview swatch + hex input + RGB sliders.
 */
@Composable
private fun ColorPickerSection(
    label: String,
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    var hexText by remember(color) {
        mutableStateOf(String.format("%06X", 0xFFFFFF and color.toArgb()))
    }
    var parseError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Preview swatch
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = "#$hexText",
                onValueChange = { input ->
                    val clean = input.removePrefix("#").trim()
                    hexText = clean
                    try {
                        val parsed = clean.toLong(16).toInt()
                        onColorChange(Color(parsed.toLong() and 0xFFFFFFFF))
                        parseError = false
                    } catch (e: NumberFormatException) {
                        parseError = true
                    }
                },
                label = { Text("Hex", fontFamily = RobotoFamily, fontSize = 12.sp) },
                isError = parseError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
                supportingText = if (parseError) {
                    { Text("Use 6 hex digits", fontFamily = RobotoFamily, fontSize = 10.sp) }
                } else null,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // RGB sliders
        ColorSlider(
            label = "R",
            value = (color.red * 255).toInt(),
            onValueChange = { r ->
                onColorChange(
                    Color(
                        red = r / 255f,
                        green = color.green,
                        blue = color.blue,
                        alpha = 1f,
                    ),
                )
            },
            sliderColor = Color(0xFFFF5252),
        )
        ColorSlider(
            label = "G",
            value = (color.green * 255).toInt(),
            onValueChange = { g ->
                onColorChange(
                    Color(
                        red = color.red,
                        green = g / 255f,
                        blue = color.blue,
                        alpha = 1f,
                    ),
                )
            },
            sliderColor = Color(0xFF69F0AE),
        )
        ColorSlider(
            label = "B",
            value = (color.blue * 255).toInt(),
            onValueChange = { b ->
                onColorChange(
                    Color(
                        red = color.red,
                        green = color.green,
                        blue = b / 255f,
                        alpha = 1f,
                    ),
                )
            },
            sliderColor = Color(0xFF448AFF),
        )
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    sliderColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
    }
}
