package app.confused.anikuta.core.player.controls

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.confused.anikuta.core.player.PlayerPreferences

/**
 * Height-constrained bottom sheet for subtitle settings.
 *
 * Ported from OLD `SubtitleSettingsPanel.kt` + the `SubtitleSettingsSheet`
 * wrapper in OLD `PlayerSheets.kt` (Task subtitle-settings-port). The two OLD
 * composables have been merged into one file in the NEW project — the panel
 * is now a private composable inside this file, and the public entry point is
 * [SubtitleSettingsSheet].
 *
 * Improvements (player-experiment, preserved):
 *  - Removed the top explanatory note (clutter).
 *  - Each section is visually separated with dividers + spacing.
 *  - Font selector is a full-width styled dropdown.
 *  - Slider values are tappable → opens a custom numeric keypad sheet
 *    ([NumericEntrySheet]) so the user can type precise values.
 *  - Color picker opens a full RGB+A sheet ([ColorPickerSheet]) with
 *    presets + custom sliders.
 *  - Delay uses a stepper (−/value/+) instead of a slider.
 *
 * DI note: The OLD project used `Injekt.get<PlayerPreferences>()` to grab the
 * preferences object inside the panel. The NEW project uses Koin constructor
 * injection — the caller passes [playerPreferences] in. This file has no
 * `Injekt` / service-locator references.
 *
 * @param playerPreferences Source of all subtitle preferences.
 * @param onApplySettings Called after every preference change. The host uses
 *     this to call [app.confused.anikuta.core.player.AnikutaMPVView
 *     .applySubtitlePreferences] which pushes the new values to MPV live.
 * @param onDismiss Close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSheet(
    playerPreferences: PlayerPreferences,
    onApplySettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Principle #2 — no drag handle (custom header instead).
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Increased from 420dp to 450dp — user wanted slightly taller.
                .heightIn(max = 450.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            // Title at the very top-left of the sheet (minimal top padding
            // so it sits right at the top of the sheet).
            Text(
                text = "Subtitle Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            // The settings panel scrolls internally
            SubtitleSettingsPanel(
                playerPreferences = playerPreferences,
                onSettingsChanged = onApplySettings,
            )
        }
    }
}

/**
 * The actual settings panel — 3 sections (Typography / Colors / Position &
 * Misc). Kept private to this file; the public entry point is
 * [SubtitleSettingsSheet].
 *
 * Every preference change calls [onSettingsChanged], which the host uses to
 * push the new value to MPV via `AnikutaMPVView.applySubtitlePreferences()`.
 *
 * Numeric MPV subtitle properties are applied via `setPropertyInt` inside
 * `AnikutaMPVView.applySubtitlePreferences()` (NOT `setPropertyString` — the
 * OLD project's port-over spec calls this out as a hard requirement, since
 * `setPropertyString` does not reliably update numeric MPV properties at
 * runtime, e.g. `sub-font-size`). This panel just writes to preferences; the
 * MPV-side translation lives in `AnikutaMPVView`.
 */
@Composable
private fun SubtitleSettingsPanel(
    playerPreferences: PlayerPreferences,
    onSettingsChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val font by playerPreferences.subtitleFont().stateIn(scope).collectAsState()
    val fontSize by playerPreferences.subtitleFontSize().stateIn(scope).collectAsState()
    val fontScale by playerPreferences.subtitleFontScale().stateIn(scope).collectAsState()
    val borderSize by playerPreferences.subtitleBorderSize().stateIn(scope).collectAsState()
    val bold by playerPreferences.boldSubtitles().stateIn(scope).collectAsState()
    val italic by playerPreferences.italicSubtitles().stateIn(scope).collectAsState()
    val textColor by playerPreferences.textColorSubtitles().stateIn(scope).collectAsState()
    val borderColor by playerPreferences.borderColorSubtitles().stateIn(scope).collectAsState()
    val bgColor by playerPreferences.backgroundColorSubtitles().stateIn(scope).collectAsState()
    val position by playerPreferences.subtitlePosition().stateIn(scope).collectAsState()
    val shadowOffset by playerPreferences.subtitleShadowOffset().stateIn(scope).collectAsState()
    val overrideASS by playerPreferences.overrideSubsASS().stateIn(scope).collectAsState()
    val delay by playerPreferences.subtitlesDelay().stateIn(scope).collectAsState()

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
            onChange = { playerPreferences.subtitleFont().set(it); onSettingsChanged() },
        )
        SectionDivider()

        // Font size — slider + tappable value
        TappableSliderRow(
            label = "Font size",
            valueText = fontSize.toString(),
            value = fontSize.toFloat(),
            range = 20f..100f,
            onChange = { playerPreferences.subtitleFontSize().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "fontSize" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Scale",
            valueText = "%.1fx".format(fontScale),
            value = fontScale,
            range = 0.5f..3f,
            onChange = { playerPreferences.subtitleFontScale().set(it); onSettingsChanged() },
            onTapValue = { editingDialog = "fontScale" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Border size",
            valueText = borderSize.toString(),
            value = borderSize.toFloat(),
            range = 0f..10f,
            onChange = { playerPreferences.subtitleBorderSize().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "borderSize" },
        )
        SectionDivider()

        CompactSwitchRow(label = "Bold", checked = bold, onChange = { playerPreferences.boldSubtitles().set(it); onSettingsChanged() })
        CompactSwitchRow(label = "Italic", checked = italic, onChange = { playerPreferences.italicSubtitles().set(it); onSettingsChanged() })

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
            onChange = { playerPreferences.subtitlePosition().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "position" },
        )
        SectionDivider()

        TappableSliderRow(
            label = "Shadow offset",
            valueText = shadowOffset.toString(),
            value = shadowOffset.toFloat(),
            range = 0f..10f,
            onChange = { playerPreferences.subtitleShadowOffset().set(it.toInt()); onSettingsChanged() },
            onTapValue = { editingDialog = "shadow" },
        )
        SectionDivider()

        CompactSwitchRow(
            label = "Override ASS styling",
            checked = overrideASS,
            onChange = { playerPreferences.overrideSubsASS().set(it); onSettingsChanged() },
        )
        SectionDivider()

        // Delay — stepper instead of slider
        DelayStepperRow(
            delay = delay,
            onChange = { playerPreferences.subtitlesDelay().set(it); onSettingsChanged() },
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
                    "fontSize" -> { playerPreferences.subtitleFontSize().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "fontScale" -> { playerPreferences.subtitleFontScale().set((v.coerceIn(min, max)) / 10f); onSettingsChanged() }
                    "borderSize" -> { playerPreferences.subtitleBorderSize().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "position" -> { playerPreferences.subtitlePosition().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "shadow" -> { playerPreferences.subtitleShadowOffset().set(v.coerceIn(min, max)); onSettingsChanged() }
                    "delay" -> { playerPreferences.subtitlesDelay().set(v.coerceIn(min, max)); onSettingsChanged() }
                }
            },
            onConfirm = { v ->
                when (dialogKey) {
                    "fontSize" -> playerPreferences.subtitleFontSize().set(v)
                    "fontScale" -> playerPreferences.subtitleFontScale().set(v / 10f)
                    "borderSize" -> playerPreferences.subtitleBorderSize().set(v)
                    "position" -> playerPreferences.subtitlePosition().set(v)
                    "shadow" -> playerPreferences.subtitleShadowOffset().set(v)
                    "delay" -> playerPreferences.subtitlesDelay().set(v)
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
            "text" -> Triple("Text color", textColor) { v: Int -> playerPreferences.textColorSubtitles().set(v) }
            "border" -> Triple("Border color", borderColor) { v: Int -> playerPreferences.borderColorSubtitles().set(v) }
            "bg" -> Triple("Background color", bgColor) { v: Int -> playerPreferences.backgroundColorSubtitles().set(v) }
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
 * entry sheet ([NumericEntrySheet]).
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
 * Color picker row — swatch + hex, tappable to open the full color dialog
 * ([ColorPickerSheet]).
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
