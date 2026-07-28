package app.confused.anikuta.feature.backup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A reusable checkbox list of [BackupCategory] items.
 *
 * Used in both the manual backup section and the auto-backup section. Each
 * row is a surfaceVariant card with a checkbox, title, and description.
 *
 * @param categories the full list of categories to display.
 * @param selected the set of selected category ids.
 * @param onToggle called when a category's checkbox is toggled.
 */
@Composable
fun BackupCategoryList(
    categories: List<BackupCategory>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        categories.forEach { category ->
            val isChecked = category.id in selected
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(category.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggle(category.id) },
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.displayName,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = category.description,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A section header label — RobotoFamily ExtraBold, uppercase, onSurfaceVariant.
 * Matches the settings section header style (principle #10, subdued variant).
 */
@Composable
fun BackupSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.06.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}
