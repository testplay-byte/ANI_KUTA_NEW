package app.confused.anikuta.feature.settings

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.preferences.AccentPreset
import app.confused.anikuta.core.preferences.ThemeMode
import app.confused.anikuta.core.preferences.ThemePreferences
import org.koin.compose.koinInject

/**
 * The Appearance / UI Customization screen.
 *
 * Per owner spec: reached from Settings → "Appearance". Hosts:
 * 1. **Appearance** section — theme mode (Light/Dark/System), AMOLED toggle,
 *    accent color presets + custom color picker.
 * 2. **Episode List** section — the episode settings link (moved here from the
 *    root Settings page).
 *
 * All changes apply live — the theme preferences are reactive, so the app
 * recomposes immediately when the user picks a new mode or accent.
 *
 * @param onOpenEpisodeSettings Navigates to the Episode Settings hub.
 * @param onBack Pops this screen.
 */
@Composable
fun AppearanceScreen(
    onOpenEpisodeSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val prefs = koinInject<ThemePreferences>()

    // Observe preferences reactively — the app theme updates live.
    val themeMode by prefs.themeMode.changes().collectAsState(initial = prefs.themeMode.get())
    val amoled by prefs.amoled.changes().collectAsState(initial = prefs.amoled.get())
    val accentPreset by prefs.accentPreset.changes().collectAsState(initial = prefs.accentPreset.get())
    val customColorArgb by prefs.customAccentColor.changes()
        .collectAsState(initial = prefs.customAccentColor.get())

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        CollapsingHeader(title = "Appearance", scrollState = scrollState)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        ) {
            // ── Appearance section ──
            item { SettingsSectionLabel("Appearance") }
            item {
                ThemeModeCard(
                    currentMode = themeMode,
                    onSelect = { prefs.themeMode.set(it) },
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AmoledCard(
                    enabled = themeMode != ThemeMode.LIGHT,
                    checked = amoled,
                    onCheckedChange = { prefs.amoled.set(it) },
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AccentColorCard(
                    currentPreset = accentPreset,
                    customColorArgb = customColorArgb,
                    onSelectPreset = { prefs.accentPreset.set(it) },
                    onSetCustom = { color -> prefs.setCustomAccent(color.toArgb()) },
                )
            }

            // ── Episode List section (moved from root Settings) ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionLabel("Episode List")
            }
            item {
                ClickableNavRow(
                    icon = Icons.Filled.Tune,
                    title = "Episode settings",
                    subtitle = "Display, layout, and metadata fetching for the episode list",
                    onClick = onOpenEpisodeSettings,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Theme mode card (Light / Dark / System — 3-way segmented toggle)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThemeModeCard(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Theme mode",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Choose the surface tone. System follows your device setting.",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            SegmentedToggle(
                options = listOf("Light", "Dark", "System"),
                selectedIndex = currentMode.ordinal,
                onSelect = { idx -> onSelect(ThemeMode.entries[idx]) },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  AMOLED card (switch — only enabled in dark mode)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AmoledCard(
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AMOLED black surfaces",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (enabled) "Pure-black background for OLED battery savings. Applies in dark mode."
                    else "Only available in dark mode.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Accent color card (presets + custom color picker)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AccentColorCard(
    currentPreset: AccentPreset,
    customColorArgb: Int,
    onSelectPreset: (AccentPreset) -> Unit,
    onSetCustom: (Color) -> Unit,
) {
    var showCustomPicker by remember { mutableStateOf(currentPreset == AccentPreset.CUSTOM) }
    val customColor = Color(customColorArgb.toLong() and 0xFFFFFFFF)

    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Accent color",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "The primary color used throughout the app. Tap a swatch to apply instantly.",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            // Preset swatches + the Custom swatch (FlowRow wraps nicely on any width).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentPreset.entries.filter { it != AccentPreset.CUSTOM }.forEach { preset ->
                    AccentSwatch(
                        color = Color(preset.seedColorArgb.toLong() and 0xFFFFFFFF),
                        isSelected = currentPreset == preset,
                        onClick = {
                            onSelectPreset(preset)
                            showCustomPicker = false
                        },
                        contentDescription = preset.displayName,
                    )
                }

                // The "Custom" swatch — shows the current custom color + opens the picker.
                AccentSwatch(
                    color = customColor,
                    isSelected = currentPreset == AccentPreset.CUSTOM,
                    onClick = {
                        onSelectPreset(AccentPreset.CUSTOM)
                        showCustomPicker = true
                    },
                    contentDescription = "Custom",
                    isCustom = true,
                )
            }

            // Custom color hex input (collapsible — shown when Custom is selected).
            if (showCustomPicker || currentPreset == AccentPreset.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                CustomColorInput(
                    currentColor = customColor,
                    onColorChange = onSetCustom,
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    isCustom: Boolean = false,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(180),
        label = "swatchBorder",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(3.dp, borderColor, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = contentDescription,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            } else if (isCustom) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = contentDescription,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = contentDescription,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CustomColorInput(
    currentColor: Color,
    onColorChange: (Color) -> Unit,
) {
    // The hex text reflects the current color. Updated when the color changes
    // externally (e.g. quick-pick). User edits parse the hex → call onColorChange.
    var hexText by remember(currentColor) {
        mutableStateOf(String.format("%06X", 0xFFFFFF and currentColor.toArgb()))
    }
    var parseError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Custom hex color",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color preview swatch
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(currentColor)
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
                label = { Text("Hex", fontFamily = RobotoFamily) },
                isError = parseError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
                supportingText = if (parseError) {
                    { Text("Invalid hex (use 6 digits, e.g. B1F256)", fontFamily = RobotoFamily, fontSize = 11.sp) }
                } else null,
            )
        }

        // Quick-pick palette of common colors (NO blue/indigo per design rules).
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Quick pick",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickPickColor(0xFFB1F256, "L", onColorChange)
            QuickPickColor(0xFFFFC107, "A", onColorChange)
            QuickPickColor(0xFFEC407A, "R", onColorChange)
            QuickPickColor(0xFFFF7043, "C", onColorChange)
            QuickPickColor(0xFF8BC34A, "S", onColorChange)
            QuickPickColor(0xFFE91E63, "P", onColorChange)
            QuickPickColor(0xFFFF5722, "Re", onColorChange)
            QuickPickColor(0xFFFF9800, "O", onColorChange)
            QuickPickColor(0xFFCDDC39, "Le", onColorChange)
            QuickPickColor(0xFF4CAF50, "G", onColorChange)
            QuickPickColor(0xFF009688, "T", onColorChange)
            QuickPickColor(0xFF8D6E63, "Br", onColorChange)
        }
    }
}

@Composable
private fun QuickPickColor(colorArgb: Long, label: String, onColorChange: (Color) -> Unit) {
    val color = Color(colorArgb)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable { onColorChange(color) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (color.luminance() > 0.5f) Color.Black else Color.White,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Shared UI helpers (match the design language)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ClickableNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A simple segmented toggle (3-way). Matches the episode-settings SegmentedRow design. */
@Composable
private fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val bg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    animationSpec = tween(180),
                    label = "segBg$idx",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "segFg$idx",
                )
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = fg,
                        )
                    }
                }
            }
        }
    }
}
