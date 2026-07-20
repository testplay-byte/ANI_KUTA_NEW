@file:OptIn(ExperimentalMaterial3Api::class)

package com.testplaybyte.animeapp.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testplaybyte.animeapp.data.HistoryRepository
import com.testplaybyte.animeapp.data.LibraryRepository
import com.testplaybyte.animeapp.data.SettingsRepository
import com.testplaybyte.animeapp.model.*
import com.testplaybyte.animeapp.ui.components.CollapsingHeader
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * SettingsScreen — appearance, display, animations, data management, and an
 * About section. Mirrors the web prototype's Material 3 Expressive design
 * exactly: collapsing header, surface-tinted section cards, custom toggle,
 * segmented theme toggle, and text-only segmented selectors.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val libraryRepo = remember { LibraryRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var confirmDialog by remember { mutableStateOf<ConfirmTarget?>(null) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned collapsing header — shrinks when content scrolls past 20dp.
        CollapsingHeader(title = "Settings", scrollState = scrollState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp), // reserved for floating nav bar
        ) {
            // ── Appearance ───────────────────────────────────────────────
            SettingsGroup(label = "Appearance") {
                SettingRow(
                    title = "Theme",
                    description = "Switch between dark and light mode",
                    trailing = {
                        ThemeSegmentedToggle(
                            isDark = settings.darkTheme,
                            onSelect = { dark ->
                                scope.launch { settingsRepo.update { copy(darkTheme = dark) } }
                            },
                        )
                    },
                )
            }

            // ── Display ──────────────────────────────────────────────────
            SettingsGroup(label = "Display") {
                SettingRow(
                    title = "Single-line titles",
                    description = "Show anime titles on one line (truncate with ellipsis)",
                    showDivider = true,
                    trailing = {
                        CustomToggle(
                            checked = settings.singleLineTitles,
                            onChange = { v ->
                                scope.launch { settingsRepo.update { copy(singleLineTitles = v) } }
                            },
                        )
                    },
                )
                StackedRow(
                    title = "Poster style",
                    description = "Cover image border radius",
                    showDivider = true,
                ) {
                    TextSelector(
                        options = listOf(
                            "Rounded" to PosterStyle.ROUNDED,
                            "Soft" to PosterStyle.SOFT,
                            "Sharp" to PosterStyle.SHARP,
                        ),
                        selected = settings.posterStyle,
                        onSelect = { v ->
                            scope.launch { settingsRepo.update { copy(posterStyle = v) } }
                        },
                    )
                }
                StackedRow(
                    title = "Card density",
                    description = "Spacing between cards",
                ) {
                    TextSelector(
                        options = listOf(
                            "Compact" to CardDensity.COMPACT,
                            "Default" to CardDensity.DEFAULT,
                            "Comfortable" to CardDensity.COMFORTABLE,
                        ),
                        selected = settings.cardDensity,
                        onSelect = { v ->
                            scope.launch { settingsRepo.update { copy(cardDensity = v) } }
                        },
                    )
                }
            }

            // ── Animations ───────────────────────────────────────────────
            SettingsGroup(label = "Animations") {
                StackedRow(
                    title = "Animation speed",
                    description = "Speed of transitions and effects",
                ) {
                    TextSelector(
                        options = listOf(
                            "Fast" to AnimSpeed.FAST,
                            "Normal" to AnimSpeed.NORMAL,
                            "Slow" to AnimSpeed.SLOW,
                        ),
                        selected = settings.animSpeed,
                        onSelect = { v ->
                            scope.launch { settingsRepo.update { copy(animSpeed = v) } }
                        },
                    )
                }
            }

            // ── Data ─────────────────────────────────────────────────────
            SettingsGroup(label = "Data") {
                SettingRow(
                    title = "Clear history",
                    description = "Remove all recently viewed anime",
                    showDivider = true,
                    onClick = { confirmDialog = ConfirmTarget.HISTORY },
                    trailing = { ChevronRight() },
                )
                SettingRow(
                    title = "Clear library",
                    description = "Remove all saved anime",
                    onClick = { confirmDialog = ConfirmTarget.LIBRARY },
                    trailing = { ChevronRight() },
                )
            }

            // ── About ────────────────────────────────────────────────────
            SettingsGroup(label = "About") {
                SettingRow(
                    title = "Anime App",
                    description = "v1.0 · Material 3 Expressive · AniList API",
                )
            }
        }
    }

    confirmDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = { Text(target.label) },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            when (target) {
                                ConfirmTarget.HISTORY -> historyRepo.clear()
                                ConfirmTarget.LIBRARY -> libraryRepo.clear()
                            }
                        }
                        confirmDialog = null
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) { Text("Cancel") }
            },
        )
    }
}

private enum class ConfirmTarget(val label: String) {
    HISTORY("Clear history?"),
    LIBRARY("Clear library?")
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingsGroup — small uppercase label above a rounded surface card.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.06.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingRow — horizontal layout: title + description on left, control on
// right. Optional divider on the bottom edge (inset 16dp). Clickable when
// `onClick` is provided.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.SettingRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StackedRow — vertical layout: title + description on top, full-width
// control below. Used for poster style & card density selectors.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.StackedRow(
    title: String,
    description: String,
    showDivider: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChevronRight — trailing arrow icon for tappable rows.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChevronRight() {
    Text(
        text = "\u2192",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomToggle — 44×26dp pill, 20×20dp knob. Off: surfaceVariant track +
// onSurfaceVariant knob. On: primary track + onPrimary knob. Knob translates
// 18dp right (from 2dp to 20dp) with an emphasized ease. NOT the default
// Material Switch.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val knobStart by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "toggleKnobStart",
    )
    val trackColor = if (checked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val knobColor = if (checked) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(13.dp),
            )
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobStart, top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThemeSegmentedToggle — Dark/Light segmented pill in a surface-2 container
// with 4dp padding. Active segment gets primaryContainer background and
// onPrimaryContainer foreground. Each segment shows a small moon/sun icon
// followed by its label.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeSegmentedToggle(isDark: Boolean, onSelect: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeSegment(
                label = "Dark",
                icon = { Icon(Icons.Filled.DarkMode, contentDescription = null, tint = it, modifier = Modifier.size(16.dp)) },
                active = isDark,
                onClick = { onSelect(true) },
            )
            ThemeSegment(
                label = "Light",
                icon = { Icon(Icons.Filled.LightMode, contentDescription = null, tint = it, modifier = Modifier.size(16.dp)) },
                active = !isDark,
                onClick = { onSelect(false) },
            )
        }
    }
}

@Composable
private fun RowScope.ThemeSegment(
    label: String,
    icon: @Composable (Color) -> Unit,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer
    else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon(fg)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TextSelector — pill buttons in a surface-2 container with 4dp padding.
// Active option: primary background, onPrimary text, subtle shadow.
// Inactive option: transparent background, onSurfaceVariant text.
// Text is 13sp semibold.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun <T> TextSelector(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            options.forEach { (label, value) ->
                val active = selected == value
                val bg = if (active) MaterialTheme.colorScheme.primary
                else Color.Transparent
                val fg = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .then(
                            if (active) Modifier.shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(10.dp),
                                clip = true,
                            ) else Modifier.clip(RoundedCornerShape(10.dp)),
                        )
                        .background(bg)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// Moon & Sun icons — now using Material Icons (Icons.Filled.DarkMode / LightMode)
// from material-icons-extended. No more Canvas-drawn crude icons.
