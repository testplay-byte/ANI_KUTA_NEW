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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.preferences.PaletteMode

/**
 * A bottom sheet for creating/editing a custom color palette.
 *
 * Per owner spec + DESIGN_LANGUAGE §3: capped at 70% of the viewport height
 * (content scrolls within). `dragHandle = null` per design language.
 *
 * Layout (scrollable, max 70% height):
 * 1. Header — "Custom palette" title (compact, no excess top spacing).
 * 2. Accent color picker — preview swatch + hex input + styled RGB sliders.
 * 3. Advanced toggle — tap to expand/collapse.
 * 4. Advanced section — background, card, text color pickers.
 * 5. Action buttons — Cancel (outlined) + OK (filled, accent).
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
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.7f).dp

    var accent by remember { mutableStateOf(initialAccent) }
    var background by remember { mutableStateOf(initialBackground) }
    var card by remember { mutableStateOf(initialCard) }
    var text by remember { mutableStateOf(initialText) }
    var showAdvanced by remember { mutableStateOf(initialPaletteMode == PaletteMode.FULL) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ── Header (compact — no excess top spacing) ──
            Text(
                text = "Custom palette",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Accent color picker ──
            ColorPickerSection(
                label = "Accent color",
                color = accent,
                onColorChange = { accent = it },
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── Advanced section (collapsible) ──
            AnimatedVisibility(
                visible = showAdvanced,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    ColorPickerSection("Background", background, { background = it })
                    Spacer(modifier = Modifier.height(10.dp))
                    ColorPickerSection("Card background", card, { card = it })
                    Spacer(modifier = Modifier.height(10.dp))
                    ColorPickerSection("Text color", text, { text = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
        }
    }
}

/**
 * A labeled color picker section: preview swatch + hex input + styled RGB sliders.
 * Compact — no excess spacing.
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
        // Label + swatch + hex in a single row (compact)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Preview swatch
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.width(8.dp))
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
                isError = parseError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.width(100.dp),
                supportingText = if (parseError) {
                    { Text("6 digits", fontFamily = RobotoFamily, fontSize = 9.sp) }
                } else null,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // RGB sliders — styled with channel-colored tracks
        ColorSlider("R", (color.red * 255).toInt(), Color(0xFFFF5252)) { r ->
            onColorChange(Color(red = r / 255f, green = color.green, blue = color.blue, alpha = 1f))
        }
        ColorSlider("G", (color.green * 255).toInt(), Color(0xFF69F0AE)) { g ->
            onColorChange(Color(red = color.red, green = g / 255f, blue = color.blue, alpha = 1f))
        }
        ColorSlider("B", (color.blue * 255).toInt(), Color(0xFF448AFF)) { b ->
            onColorChange(Color(red = color.red, green = color.green, blue = b / 255f, alpha = 1f))
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Int,
    trackColor: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = trackColor,
            modifier = Modifier.width(14.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.2f),
            ),
        )
        Text(
            text = value.toString(),
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
    }
}
