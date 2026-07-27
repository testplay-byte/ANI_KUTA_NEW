package app.confused.anikuta.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
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
 * Per owner spec (Session 1): the actual theme settings live here. Layout:
 * 1. **Theme mode** (Light / Dark / System) — 3-way segmented toggle.
 * 2. **AMOLED** toggle — only shown when dark mode is active (hidden in light).
 * 3. **Palettes** — skeleton-screen preview cards (mini details-page mock).
 *    The first card is "Custom" — tapping it opens the [CustomColorDialog].
 * 4. **Episode settings** link (moved here from the root Settings page).
 *
 * All changes apply live with a smooth cross-fade transition.
 *
 * @param onOpenEpisodeSettings Navigates to the Episode Settings hub.
 * @param onBack Pops this screen.
 */
@Composable
fun AppearanceGeneralScreen(
    onOpenEpisodeSettings: () -> Unit,
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

    var showCustomDialog by remember { mutableStateOf(false) }

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

            // ── AMOLED (only in dark mode — smoothly fades in/out) ──
            item {
                AnimatedVisibility(
                    visible = isDark,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        AmoledCard(
                            checked = amoled,
                            onCheckedChange = { prefs.amoled.set(it) },
                        )
                    }
                }
            }

            // ── Palettes (skeleton-screen preview cards) ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSectionLabel("Palettes")
            }
            item {
                PalettesCard(
                    currentPreset = accentPreset,
                    customColor = Color(customColorArgb.toLong() and 0xFFFFFFFF),
                    isDark = isDark,
                    paletteMode = paletteMode,
                    customBg = Color(customBgArgb.toLong() and 0xFFFFFFFF),
                    customCard = Color(customCardArgb.toLong() and 0xFFFFFFFF),
                    customText = Color(customTextArgb.toLong() and 0xFFFFFFFF),
                    onSelectPreset = { prefs.accentPreset.set(it) },
                    onOpenCustom = { showCustomDialog = true },
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
                    subtitle = "Display, layout, and metadata",
                    onClick = onOpenEpisodeSettings,
                )
            }
        }
    }

    // ── Custom color dialog ──
    if (showCustomDialog) {
        CustomColorDialog(
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
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false },
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
//  AMOLED card (switch — only shown in dark mode)
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
//  Palettes card (skeleton-screen preview cards)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PalettesCard(
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
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // FlowRow of palette preview cards. Custom is FIRST (per owner spec item 5).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Custom (first position) ──
                // The preview shows the current custom colors (or the dark/light
                // base if the user hasn't customized yet).
                val customPreviewBg = if (paletteMode == PaletteMode.FULL) customBg
                    else if (isDark) BgDark else BgLight
                val customPreviewCard = if (paletteMode == PaletteMode.FULL) customCard
                    else if (isDark) Surface1Dark else Surface1Light
                val customPreviewText = if (paletteMode == PaletteMode.FULL) customText
                    else if (isDark) TextDark else TextLight
                PalettePreviewCard(
                    label = "Custom",
                    backgroundColor = customPreviewBg,
                    cardColor = customPreviewCard,
                    accentColor = customColor,
                    textColor = customPreviewText,
                    isSelected = currentPreset == AccentPreset.CUSTOM,
                    onClick = onOpenCustom,
                )

                // ── Presets ──
                AccentPreset.entries.filter { it != AccentPreset.CUSTOM }.forEach { preset ->
                    val presetAccent = Color(preset.seedColorArgb.toLong() and 0xFFFFFFFF)
                    PalettePreviewCard(
                        label = preset.displayName,
                        backgroundColor = if (isDark) BgDark else BgLight,
                        cardColor = if (isDark) Surface1Dark else Surface1Light,
                        accentColor = presetAccent,
                        textColor = if (isDark) TextDark else TextLight,
                        isSelected = currentPreset == preset,
                        onClick = {
                            onSelectPreset(preset)
                            // Switching to a preset exits full-palette mode.
                            if (paletteMode == PaletteMode.FULL) {
                                // Handled by the preference write — SIMPLIFIED is the default for presets.
                            }
                        },
                    )
                }
            }
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
                        .androidx_clip(RoundedCornerShape(8.dp))
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

/** Helper to avoid the `Modifier.clip` import collision. */
private fun Modifier.androidx_clip(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(androidx.compose.ui.draw.clip(shape))
