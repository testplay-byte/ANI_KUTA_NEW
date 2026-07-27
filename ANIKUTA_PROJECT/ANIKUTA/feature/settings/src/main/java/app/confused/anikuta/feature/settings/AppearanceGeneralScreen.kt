@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.confused.anikuta.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
 * Per owner spec (Session 1 + feedback): the actual theme settings live here.
 * Layout (top to bottom):
 * 1. **Theme mode** (Light / Dark / System) — 3-way segmented toggle.
 * 2. **Palettes** — horizontal carousel (LazyRow) of skeleton-screen preview
 *    cards. Custom is first, with a unique highlight. Tapping Custom opens
 *    the [CustomColorSheet].
 * 3. **AMOLED** toggle — shown BELOW the palettes, only in dark mode.
 *    Smoothly expands/collapses with slide+fade when dark mode toggles.
 *
 * The Episode settings link is NOT here — it's on the Appearance list screen
 * (per owner feedback: "there was still the episode list showing inside the
 * General options too so that needs to be properly fixed").
 *
 * All changes apply live with a smooth cross-fade transition.
 *
 * @param onBack Pops this screen.
 */
@Composable
fun AppearanceGeneralScreen(
    onBack: () -> Unit,
) {
    val prefs = koinInject<ThemePreferences>()

    // Observe preferences reactively — the app theme updates live.
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

    // Resolve the effective dark mode (System follows the device setting).
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

            // ── Palettes (horizontal carousel) ──
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
                        // Selecting a preset switches back to SIMPLIFIED mode
                        // so the custom bg/card/text no longer apply (per owner
                        // feedback: custom palette should only apply if the user
                        // has selected the custom option).
                        if (paletteMode == PaletteMode.FULL) {
                            prefs.paletteMode.set(PaletteMode.SIMPLIFIED)
                        }
                        prefs.accentPreset.set(preset)
                    },
                    onOpenCustom = { showCustomSheet = true },
                )
            }

            // ── AMOLED (below palettes, only in dark mode — smooth expand/collapse) ──
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
    if (showCustomSheet) {
        CustomColorSheet(
            initialAccent = if (accentPreset == AccentPreset.CUSTOM) {
                Color(customColorArgb.toLong() and 0xFFFFFFFF)
            } else {
                Color(accentPreset.seedColorArgb.toLong() and 0xFFFFFFFF)
            },
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
                showCustomSheet = false
            },
            onDismiss = { showCustomSheet = false },
        )
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
    onOpenCustom: () -> Unit,
) {
    val baseBg = if (isDark) BgDark else BgLight
    val baseCard = if (isDark) Surface1Dark else Surface1Light
    val baseText = if (isDark) TextDark else TextLight

    // The custom preview shows the current custom colors (or the base if not FULL).
    val customPreviewBg = if (paletteMode == PaletteMode.FULL) customBg else baseBg
    val customPreviewCard = if (paletteMode == PaletteMode.FULL) customCard else baseCard
    val customPreviewText = if (paletteMode == PaletteMode.FULL) customText else baseText

    SettingsCard {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // ── Custom (first, unique highlight) ──
            item {
                PalettePreviewCard(
                    label = "Custom",
                    backgroundColor = customPreviewBg,
                    cardColor = customPreviewCard,
                    accentColor = customColor,
                    textColor = customPreviewText,
                    isSelected = currentPreset == AccentPreset.CUSTOM,
                    isCustom = true,
                    onClick = onOpenCustom,
                )
            }

            // ── Presets ──
            items(
                count = AccentPreset.entries.size - 1, // exclude CUSTOM
            ) { idx ->
                Spacer(modifier = Modifier.width(12.dp))
                val preset = AccentPreset.entries[idx + 1] // skip CUSTOM (index 5)
                PalettePreviewCard(
                    label = preset.displayName,
                    backgroundColor = baseBg,
                    cardColor = baseCard,
                    accentColor = Color(preset.seedColorArgb.toLong() and 0xFFFFFFFF),
                    textColor = baseText,
                    isSelected = currentPreset == preset,
                    isCustom = false,
                    onClick = { onSelectPreset(preset) },
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  AMOLED card (switch — only shown in dark mode, below palettes)
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
