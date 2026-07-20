package app.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A surface-tinted card for grouping settings.
 *
 * Per the prototype's `SettingsGroup` pattern: a labeled card with a slightly
 * elevated surface color, containing rows of settings.
 *
 * @param label The section label (e.g. "Appearance", "Display").
 * @param showDividers Whether to show dividers between content rows.
 * @param content The settings rows.
 */
@Composable
fun SettingsGroupCard(
    label: String,
    modifier: Modifier = Modifier,
    showDividers: Boolean = true,
    content: @Composable SettingsGroupScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingsGroupScopeImpl(showDividers).content()
            }
        }
    }
}

/** Scope for [SettingsGroupCard] content — provides the [SettingRow] helper. */
interface SettingsGroupScope {
    @Composable
    fun SettingRow(
        title: String,
        description: String? = null,
        showDivider: Boolean = true,
        trailing: @Composable () -> Unit = {},
    )
}

private class SettingsGroupScopeImpl(private val showDividers: Boolean) : SettingsGroupScope {
    @Composable
    override fun SettingRow(
        title: String,
        description: String?,
        showDivider: Boolean,
        trailing: @Composable () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (description != null) {
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing()
        }
        if (showDividers && showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
