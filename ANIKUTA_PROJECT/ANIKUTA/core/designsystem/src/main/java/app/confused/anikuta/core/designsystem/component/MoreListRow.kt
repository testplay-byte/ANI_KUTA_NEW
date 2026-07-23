package app.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A "More" screen list row — leading icon, title + subtitle, trailing chevron.
 *
 * Mirrors the private `MoreRow` in `app/.../MainActivity.kt` (used by the
 * existing Settings/Extensions/Episode-settings entries) so feature modules
 * can contribute their own rows to the More screen without touching
 * `MainActivity.kt` (which avoids merge conflicts across agent branches).
 *
 * Visual rules (match the existing More row exactly):
 *  - Surface: `surfaceVariant` at 40% alpha, `RoundedCornerShape(12.dp)`,
 *    horizontal 16dp / vertical 4dp outer padding.
 *  - Inner padding: 16dp.
 *  - Icon: 24dp, tinted `primary`.
 *  - Title: RobotoFamily ExtraBold 16sp, `onSurface`, 1 line ellipsized.
 *  - Subtitle: RobotoFamily Normal 13sp, `onSurfaceVariant`, 2 lines ellipsized.
 *  - Trailing: `Icons.Filled.ChevronRight`, tinted `onSurfaceVariant`.
 *
 * @param icon Leading icon (tinted accent).
 * @param title Row title.
 * @param subtitle Row subtitle.
 * @param onClick Click handler.
 * @param modifier Standard modifier.
 */
@Composable
fun MoreListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A small accent-colored section label for the More screen (e.g. "GENERAL",
 * "ACTIVITY"). Mirrors the private `SettingsSectionLabel` in `MainActivity.kt`
 * so feature modules can add labeled sections without touching that file.
 *
 * Style: RobotoFamily ExtraBold 14sp, `primary`, 20dp start / 16dp top / 8dp bottom.
 */
@Composable
fun MoreSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}
