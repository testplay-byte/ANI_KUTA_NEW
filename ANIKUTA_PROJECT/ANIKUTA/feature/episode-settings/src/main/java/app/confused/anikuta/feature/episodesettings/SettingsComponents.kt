package app.confused.anikuta.feature.episodesettings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  Group container
// ════════════════════════════════════════════════════════════════════════════

/**
 * A labeled group card that holds a vertical stack of settings rows with dividers.
 *
 * Visual: a `surfaceVariant@0.4` rounded card with an optional title label above it.
 * Rows inside are separated by thin `HorizontalDivider` lines.
 */
@Composable
fun SettingsGroupCard(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 12.dp),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/** A thin divider used between rows inside [SettingsGroupCard]. */
@Composable
fun InGroupDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Switch row (the toggle the user asked for — proper Material3 Switch,
//  NOT the ugly custom on/off pill buttons)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A settings row with a leading icon, title, subtitle, and a trailing Material3 [Switch].
 *
 * The entire row is clickable (toggles the switch). This is the standard boolean-toggle
 * widget for episode settings — per user feedback, we use the native Material3 Switch,
 * NOT a custom pill toggle.
 *
 * Mirrors the old ANIKUTA project's `SwitchSettingsRow`.
 *
 * @param icon Leading icon (rendered in a 36dp `secondaryContainer` rounded square).
 * @param title Bold row title.
 * @param subtitle Smaller description below the title.
 * @param checked Current switch state.
 * @param onCheckedChange Called with the new state when the switch (or row) is toggled.
 */
@Composable
fun SwitchSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
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
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The 36dp rounded-square leading icon used by [SwitchSettingsRow] and [ClickableSettingsRow]. */
@Composable
private fun LeadingIcon(icon: ImageVector) {
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
}

// ════════════════════════════════════════════════════════════════════════════
//  Clickable row (navigates to a sub-page)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A settings row that navigates to a sub-page when tapped. Shows a leading icon,
 * title, subtitle, and a trailing chevron (`>`).
 *
 * Used by the Hub screen for the 3 sub-page links (Display / Layout / Metadata).
 */
@Composable
fun ClickableSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
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
                fontWeight = FontWeight.Normal,
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

// ════════════════════════════════════════════════════════════════════════════
//  Segmented row (2-3 option pill selector)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A 2-or-3-option segmented selector rendered as a row of pill buttons.
 *
 * The selected pill gets `primary` background + `onPrimary` ExtraBold text;
 * unselected pills are transparent with `onSurfaceVariant` Medium text.
 *
 * Used for position knobs (thumbnail left/right, title right/below, etc.) and
 * the title-max-lines (1/2) choice.
 *
 * @param label Section label (bold, above the row).
 * @param description Smaller description under the label.
 * @param options List of (option-label, is-selected) pairs. Typically 2 or 3.
 * @param onSelect Called with the index of the tapped option.
 */
@Composable
fun LabeledSegmentedRow(
    label: String,
    description: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        SegmentedRow(options = options, onSelect = onSelect)
    }
}

/**
 * The bare pill row (no label). Selected pill = `primary`; unselected = transparent.
 */
@Composable
fun SegmentedRow(
    options: List<Pair<String, Boolean>>,
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
            options.forEachIndexed { idx, (label, selected) ->
                val bg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = tween(180),
                    label = "segBg",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "segFg",
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
