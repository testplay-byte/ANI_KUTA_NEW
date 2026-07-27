@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.confused.anikuta.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.BgDark
import app.confused.anikuta.core.designsystem.theme.BgLight
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.designsystem.theme.Surface1Dark
import app.confused.anikuta.core.designsystem.theme.Surface1Light
import app.confused.anikuta.core.designsystem.theme.TextDark
import app.confused.anikuta.core.designsystem.theme.TextLight
import app.confused.anikuta.core.preferences.AccentPreset
import app.confused.anikuta.core.preferences.PaletteMode
import app.confused.anikuta.core.preferences.ThemeMode
import app.confused.anikuta.core.preferences.ThemePreferences
import org.koin.compose.koinInject

/**
 * The Appearance → General screen.
 *
 * Layout (top to bottom):
 * 1. **Theme mode** (Light / Dark / System) — 3-way segmented toggle.
 * 2. **Palettes** — horizontal carousel (LazyRow):
 *    - Custom (first, unique highlight, click-to-select, click-again-to-edit).
 *    - 10 accent-only presets.
 *    - 5 full-palette presets (different bg/card/text).
 * 3. **AMOLED** toggle — below palettes, only in dark mode (smooth expand/collapse).
 */
@Composable
fun AppearanceGeneralScreen(
    onBack: () -> Unit,
) {
    val prefs = koinInject<ThemePreferences>()

    val themeMode by prefs.themeMode.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.themeMode.get())
    val amoled by prefs.amoled.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.amoled.get())
    val accentPreset by prefs.accentPreset.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.accentPreset.get())
    val customColorArgb by prefs.customAccentColor.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.customAccentColor.get())
    val paletteMode by prefs.paletteMode.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.paletteMode.get())
    val customBgArgb by prefs.customBackgroundColor.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.customBackgroundColor.get())
    val customCardArgb by prefs.customCardColor.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.customCardColor.get())
    val customTextArgb by prefs.customTextColor.changes()
        .collectAsStateWithLifecycle(initialValue = prefs.customTextColor.get())

    var showCustomSheet by remember { mutableStateOf(false) }

    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 || lazyListState.firstVisibleItemIndex > 0

    Column(modifier = Modifier.fillMaxWidth()) {
        CollapsingHeader(title = "General", collapsed = collapsed)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            // ── Theme mode ──
            item {
                SettingsSectionLabel("Theme mode")
                ThemeModeCard(
                    currentMode = themeMode,
                    onSelect = { prefs.themeMode.set(it) },
                )
            }

            // ── Palettes carousel ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSectionLabel("Palettes")
            }
            item {
                PalettesCarousel(
                    currentPreset = accentPreset,
                    customColor = Color(customColorArgb.toLong() and 0xFFFFFFFF),
                    isDark = isDark,
                    paletteMode = paletteMode,
                    customBg = Color(customBgArgb.toLong() and 0xFFFFFFFF),
                    customCard = Color(customCardArgb.toLong() and 0xFFFFFFFF),
                    customText = Color(customTextArgb.toLong() and 0xFFFFFFFF),
                    onSelectPreset = { preset ->
                        if (preset.isFullPalette) {
                            // Full-palette preset: apply its bg/card/text + FULL mode.
                            prefs.applyFullPalettePreset(preset)
                        } else {
                            // Accent-only preset: switch back to SIMPLIFIED mode.
                            if (paletteMode == PaletteMode.FULL) {
                                prefs.paletteMode.set(PaletteMode.SIMPLIFIED)
                            }
                            prefs.accentPreset.set(preset)
                        }
                    },
                    onCustomClick = {
                        // First click: select custom (restore saved custom mode).
                        // Second click (already selected): open the sheet.
                        if (accentPreset != AccentPreset.CUSTOM) {
                            prefs.selectCustom()
                        } else {
                            showCustomSheet = true
                        }
                    },
                )
            }

            // ── AMOLED (below palettes, dark-only, smooth expand/collapse) ──
            item {
                AnimatedVisibility(
                    visible = isDark,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSectionLabel("Display")
                        AmoledCard(
                            checked = amoled,
                            onCheckedChange = { prefs.amoled.set(it) },
                        )
                    }
                }
            }
        }
    }

    // ── Custom color bottom sheet ──
    // Always opens with the SAVED custom colors (not the current theme's).
    if (showCustomSheet) {
        CustomColorSheet(
            initialAccent = Color(customColorArgb.toLong() and 0xFFFFFFFF),
            initialBackground = Color(customBgArgb.toLong() and 0xFFFFFFFF),
            initialCard = Color(customCardArgb.toLong() and 0xFFFFFFFF),
            initialText = Color(customTextArgb.toLong() and 0xFFFFFFFF),
            initialPaletteMode = paletteMode,
            onApply = { accent, bg, card, text, mode ->
                prefs.setCustomAccent(accent.toArgb())
                prefs.customBackgroundColor.set(bg.toArgb())
                prefs.customCardColor.set(card.toArgb())
                prefs.customTextColor.set(text.toArgb())
                prefs.paletteMode.set(mode)
                // Save the mode for custom palette persistence — so switching
                // away from Custom and back restores the advanced settings.
                prefs.customPaletteMode.set(mode)
                showCustomSheet = false
            },
            onDismiss = { showCustomSheet = false },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Theme mode card
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
            SegmentedToggle(
                options = listOf("Light", "Dark", "System"),
                selectedIndex = currentMode.ordinal,
                onSelect = { idx -> onSelect(ThemeMode.entries[idx]) },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Palettes carousel (horizontal LazyRow)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PalettesCarousel(
    currentPreset: AccentPreset,
    customColor: Color,
    isDark: Boolean,
    paletteMode: PaletteMode,
    customBg: Color,
    customCard: Color,
    customText: Color,
    onSelectPreset: (AccentPreset) -> Unit,
    onCustomClick: () -> Unit,
) {
    val baseBg = if (isDark) BgDark else BgLight
    val baseCard = if (isDark) Surface1Dark else Surface1Light
    val baseText = if (isDark) TextDark else TextLight

    // The custom preview shows the saved custom colors (or base if SIMPLIFIED).
    val customPreviewBg = if (paletteMode == PaletteMode.FULL) customBg else baseBg
    val customPreviewCard = if (paletteMode == PaletteMode.FULL) customCard else baseCard
    val customPreviewText = if (paletteMode == PaletteMode.FULL) customText else baseText

    // All presets EXCEPT CUSTOM (properly filtered — no index math).
    val presets = AccentPreset.entries.filter { it != AccentPreset.CUSTOM }

    SettingsCard {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 15 presets FIRST (10 accent-only + 5 full-palette) ──
            items(presets) { preset ->
                val presetBg = if (preset.isFullPalette) Color(preset.backgroundArgb!!.toLong() and 0xFFFFFFFF) else baseBg
                val presetCard = if (preset.isFullPalette) Color(preset.cardArgb!!.toLong() and 0xFFFFFFFF) else baseCard
                val presetText = if (preset.isFullPalette) Color(preset.textArgb!!.toLong() and 0xFFFFFFFF) else baseText
                PalettePreviewCard(
                    label = preset.displayName,
                    backgroundColor = presetBg,
                    cardColor = presetCard,
                    accentColor = Color(preset.seedColorArgb.toLong() and 0xFFFFFFFF),
                    textColor = presetText,
                    isSelected = currentPreset == preset,
                    isCustom = false,
                    onClick = { onSelectPreset(preset) },
                )
            }

            // ── Accent-colored vertical divider (separates presets from Custom) ──
            // 3dp wide, 108dp tall (70% of card height), rounded ends, centered vertically.
            item {
                Box(
                    modifier = Modifier
                        .height(155.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(108.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    )
                }
            }

            // ── Custom LAST (rightmost, unique highlight) ──
            item {
                PalettePreviewCard(
                    label = "Custom",
                    backgroundColor = customPreviewBg,
                    cardColor = customPreviewCard,
                    accentColor = customColor,
                    textColor = customPreviewText,
                    isSelected = currentPreset == AccentPreset.CUSTOM,
                    isCustom = true,
                    onClick = onCustomClick,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  AMOLED card
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AmoledCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AMOLED black surfaces",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Pure-black background for OLED screens.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Shared UI helpers
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
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val bg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "segBg$idx",
                )
                val fg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = androidx.compose.animation.core.tween(180),
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
