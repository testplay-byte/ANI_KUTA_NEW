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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Subtitle settings panel — compact, scrollable, sectioned.
 *
 * Improvements (player-experiment):
 *  - Removed the top explanatory note (clutter).
 *  - Each section is visually separated with dividers + spacing.
 *  - Font selector is a full-width styled dropdown.
 *  - Slider values are tappable → opens a custom numeric keypad dialog
 *    (experimental, toggleable via [PlayerPreferences.useCustomKeypad]).
 *  - Color picker opens a full RGB+A dialog (presets + custom sliders).
 *  - Delay uses a stepper (−/value/+) instead of a slider.
 */
@Composable
fun SubtitleSettingsPanel(
    onSettingsChanged: () -> Unit = {},
) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()

    val font by prefs.subtitleFont().stateIn(scope).collectAsState()
    val fontSize by prefs.subtitleFontSize().stateIn(scope).collectAsState()
    val fontScale by prefs.subtitleFontScale().stateIn(scope).collectAsState()
    val borderSize by prefs.subtitleBorderSize().stateIn(scope).collectAsState()
    val bold by prefs.boldSubtitles().stateIn(scope).collectAsState()
    val italic by prefs.italicSubtitles().stateIn(scope).collectAsState()
    val textColor by prefs.textColorSubtitles().stateIn(scope).collectAsState()
    val borderColor by prefs.borderColorSubtitles().stateIn(scope).collectAsState()
    val bgColor by prefs.backgroundColorSubtitles().stateIn(scope).collectAsState()
    val position by prefs.subtitlePosition().stateIn(scope).collectAsState()
    val shadowOffset by prefs.subtitleShadowOffset().stateIn(scope).collectAsState()
    val overrideASS by prefs.overrideSubsASS().stateIn(scope).collectAsState()
    val delay by prefs.subtitlesDelay().stateIn(scope).collectAsState()

    // Dialog state — which setting is being edited via keypad/dialog.
    var editingDialog by remember { mutableStateOf<String?>(null) }
    var colorDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // ═══════ Section: Typography ═══════
        SectionHeader("Typography")
        // Font family — full-width styled dropdown
        FontSelectorRow(
            value = font,
            onChange = { prefs.subtitleFont().set(it); onSettingsChanged() },
        )
        SectionDivider()

        // Font size — slider + tappable value
        TappableSliderRow(
            label = "Font size",
            valueText = fontSize.toString(),
            value = fontSize.toFloat(),
            range = 20f..100f,
            onChange = { prefs.subtitleFontSize().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "fontSize" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Scale",
            valueText = "%.1fx".format(fontScale),
            value = fontScale,
            range = 0.5f..3f,
            onChange = { prefs.subtitleFontScale().set(it); onSettingsChanged() },
            onTapValue = { editingDialog = "fontScale" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Border size",
            valueText = borderSize.toString(),
            value = borderSize.toFloat(),
            range = 0f..10f,
            onChange = { prefs.subtitleBorderSize().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "borderSize" },
        )
        SectionDivider()

        CompactSwitchRow(label = "Bold", checked = bold, onChange = { prefs.boldSubtitles().set(it); onSettingsChanged() })
        CompactSwitchRow(label = "Italic", checked = italic, onChange = { prefs.italicSubtitles().set(it); onSettingsChanged() })

        SectionSpacer()

        // ═══════ Section: Colors ═══════
        SectionHeader("Colors")
        ColorPickerRow(
            label = "Text color",
            color = textColor,
            onTap = { colorDialog = "text" },
        )
        SectionDivider()
        ColorPickerRow(
            label = "Border color",
            color = borderColor,
            onTap = { colorDialog = "border" },
        )
        SectionDivider()
        ColorPickerRow(
            label = "Background color",
            color = bgColor,
            onTap = { colorDialog = "bg" },
        )

        SectionSpacer()

        // ═══════ Section: Position & Misc ═══════
        SectionHeader("Position & Misc")
        TappableSliderRow(
            label = "Position",
            valueText = "$position%",
            value = position.toFloat(),
            range = 0f..100f,
            onChange = { prefs.subtitlePosition().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "position" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Shadow offset",
            valueText = shadowOffset.toString(),
            value = shadowOffset.toFloat(),
            range = 0f..10f,
            onChange = { prefs.subtitleShadowOffset().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "shadow" },
        )
        SectionDivider()

        CompactSwitchRow(
            label = "Override ASS styling",
            checked = overrideASS,
            onChange = { prefs.overrideSubsASS().set(it); onSettingsChanged() },
        )
        SectionDivider()

        // Delay — stepper instead of slider
        DelayStepperRow(
            delay = delay,
            onChange = { prefs.subtitlesDelay().set(it); onSettingsChanged() },
            onTapValue = { editingDialog = "delay" },
        )
    }

    // ---- Keypad sheets (bottom sheet, not popup) ----
    editingDialog?.let { dialogKey ->
        val (title, initial, suffix, min, max) = when (dialogKey) {
            "fontSize" -> Tuple5("Font size", fontSize, "", 20, 100)
            "fontScale" -> Tuple5("Scale (×10)", (fontScale * 10).toInt(), "", 5, 30)
            "borderSize" -> Tuple5("Border size", borderSize, "", 0, 10)
            "position" -> Tuple5("Position", position, "%", 0, 100)
            "shadow" -> Tuple5("Shadow offset", shadowOffset, "", 0, 10)
            "delay" -> Tuple5("Delay", delay, "ms", -5000, 5000)
            else -> return@let
        }
        NumericEntrySheet(
            title = title,
            initial = initial,
            suffix = suffix,
            min = min,
            max = max,
            onLiveChange = { v ->
                // Live-apply so the user sees the change on the video behind the sheet.
                when (dialogKey) {
                    "fontSize" -> { prefs.subtitleFontSize().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "fontScale" -> { prefs.subtitleFontScale().set((v.coerceIn(min, max)) / 10f); onSettingsChanged() }
                    "borderSize" -> { prefs.subtitleBorderSize().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "position" -> { prefs.subtitlePosition().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "shadow" -> { prefs.subtitleShadowOffset().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "delay" -> { prefs.subtitlesDelay().set(v.coerceIn(min, max)); onSettingsChanged() }
                }
            },
            onConfirm = { v ->
                when (dialogKey) {
                    "fontSize" -> prefs.subtitleFontSize().set(v)
                    "fontScale" -> prefs.subtitleFontScale().set(v / 10f)
                    "borderSize" -> prefs.subtitleBorderSize().set(v)
                    "position" -> prefs.subtitlePosition().set(v)
                    "shadow" -> prefs.subtitleShadowOffset().set(v)
                    "delay" -> prefs.subtitlesDelay().set(v)
                }
                onSettingsChanged()
                editingDialog = null
            },
            onDismiss = { editingDialog = null },
        )
    }

    // ---- Color sheets (bottom sheet, live preview) ----
    colorDialog?.let { dialogKey ->
        val (title, initial, setter) = when (dialogKey) {
            "text" -> Triple("Text color", textColor) { v: Int -> prefs.textColorSubtitles().set(v) }
            "border" -> Triple("Border color", borderColor) { v: Int -> prefs.borderColorSubtitles().set(v) }
            "bg" -> Triple("Background color", bgColor) { v: Int -> prefs.backgroundColorSubtitles().set(v) }
            else -> return@let
        }
        ColorPickerSheet(
            title = title,
            initialColor = initial,
            onLiveChange = { v ->
                // Live-apply so the user sees the color change on the video behind the sheet.
                setter(v)
                onSettingsChanged()
            },
            onDismiss = { colorDialog = null },
        )
    }
}

// ---- Helpers ----

private data class Tuple5(val a: String, val b: Int, val c: String, val d: Int, val e: Int)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(modifier = Modifier.height(20.dp))
}

/**
 * Slider row with a tappable value label. Tapping the value opens a numeric
 * entry dialog (custom keypad or device keyboard, per preference).
 */
@Composable
private fun TappableSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onTapValue: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Tappable value chip
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun CompactSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Font selector — full-width dropdown with styled surface.
 */
@Composable
private fun FontSelectorRow(
    value: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Sans Serif", "Serif", "Monospace", "Roboto")
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Font",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Color picker row — swatch + hex, tappable to open the full color dialog.
 */
@Composable
private fun ColorPickerRow(
    label: String,
    color: Int,
    onTap: () -> Unit,
) {
    val colorObj = Color(color)
    val hex = String.format("#%08X", color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorObj)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            )
            Text(
                text = hex,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Delay stepper — −/value/+ buttons instead of a slider. Tapping the value
 * opens the numeric keypad for precise input. Step = 100ms.
 */
@Composable
private fun DelayStepperRow(
    delay: Int,
    onChange: (Int) -> Unit,
    onTapValue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Delay",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // − button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp).clickable { onChange((delay - 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "−100ms", modifier = Modifier.size(18.dp))
                }
            }
            // Value (tappable)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clickable(onClick = onTapValue),
            ) {
                Text(
                    text = "${delay}ms",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            // + button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp).clickable { onChange((delay + 100).coerceIn(-5000, 5000)) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "+100ms", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
