@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.confused.anikuta.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
 * A bottom sheet for creating/editing a custom color palette.
 *
 * Per owner spec (Session 1 items 8 + 9.5 + feedback): replaces the ugly
 * AlertDialog with a proper [ModalBottomSheet] that follows the design
 * language — rounded top corners, scrollable content, proper spacing,
 * design-language buttons.
 *
 * Layout (scrollable):
 * 1. **Header** — "Custom palette" title.
 * 2. **Accent color picker** (always shown) — preview swatch + hex input + RGB sliders.
 * 3. **Advanced toggle** — tap to expand/collapse the full palette section.
 * 4. **Advanced section** (collapsible) — background, card, text color pickers.
 *    Each has its own preview + hex + sliders.
 * 5. **Action buttons** — Cancel (outlined) + OK (filled, accent-colored).
 *
 * The sheet is full-height-scrollable so all controls are reachable even with
 * the Advanced section expanded. `dragHandle = null` per DESIGN_LANGUAGE §2.
 *
 * @param initialAccent The starting accent color.
 * @param initialBackground The starting background color (for advanced mode).
 * @param initialCard The starting card color (for advanced mode).
 * @param initialText The starting text color (for advanced mode).
 * @param initialPaletteMode The starting palette mode (SIMPLIFIED or FULL).
 * @param onApply Called with the chosen colors + palette mode when OK is pressed.
 * @param onDismiss Called when the user cancels (back gesture or scrim tap).
 */
@Composable
fun CustomColorSheet(
    initialAccent: Color,
    initialBackground: Color,
    initialCard: Color,
    initialText: Color,
    initialPaletteMode: PaletteMode,
    onApply: (accent: Color, background: Color, card: Color, text: Color, mode: PaletteMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var accent by remember { mutableStateOf(initialAccent) }
    var background by remember { mutableStateOf(initialBackground) }
    var card by remember { mutableStateOf(initialCard) }
    var text by remember { mutableStateOf(initialText) }
    var showAdvanced by remember { mutableStateOf(initialPaletteMode == PaletteMode.FULL) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null, // Per DESIGN_LANGUAGE §2 (no drag handle).
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // ── Header ──
            Text(
                text = "Custom palette",
                fontFamily = RobotoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Pick an accent color, or expand Advanced to customize every color.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Accent color picker (always shown) ──
            ColorPickerSection(
                label = "Accent color",
                color = accent,
                onColorChange = { accent = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Advanced toggle ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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

            // ── Advanced section (collapsible, smooth expand/collapse) ──
            AnimatedVisibility(
                visible = showAdvanced,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Set each color individually. These override the defaults.",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    ColorPickerSection(
                        label = "Background color",
                        color = background,
                        onColorChange = { background = it },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ColorPickerSection(
                        label = "Card background color",
                        color = card,
                        onColorChange = { card = it },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ColorPickerSection(
                        label = "Text color",
                        color = text,
                        onColorChange = { text = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Action buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        val mode = if (showAdvanced) PaletteMode.FULL else PaletteMode.SIMPLIFIED
                        onApply(accent, background, card, text, mode)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("OK", fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * A labeled color picker section: a preview swatch + hex input + RGB sliders.
 * Self-contained and scrollable-friendly.
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

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Preview swatch (larger, with border)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
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

            Spacer(modifier = Modifier.height(10.dp))

            // RGB sliders
            ColorSlider(
                label = "R",
                value = (color.red * 255).toInt(),
                onValueChange = { r ->
                    onColorChange(Color(red = r / 255f, green = color.green, blue = color.blue, alpha = 1f))
                },
                sliderColor = Color(0xFFFF5252),
            )
            ColorSlider(
                label = "G",
                value = (color.green * 255).toInt(),
                onValueChange = { g ->
                    onColorChange(Color(red = color.red, green = g / 255f, blue = color.blue, alpha = 1f))
                },
                sliderColor = Color(0xFF69F0AE),
            )
            ColorSlider(
                label = "B",
                value = (color.blue * 255).toInt(),
                onValueChange = { b ->
                    onColorChange(Color(red = color.red, green = color.green, blue = b / 255f, alpha = 1f))
                },
                sliderColor = Color(0xFF448AFF),
            )
        }
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
