package app.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A list section header — small, uppercase, `onSurfaceVariant` label used to
 * group items inside a scrolling list (e.g. History's "TODAY" / "YESTERDAY",
 * Schedule's day headers, Updates' "RECENTLY CHECKED").
 *
 * Style matches the existing `OptionLabel` in `feature/library/.../CustomizeSheet.kt`:
 * RobotoFamily ExtraBold 11sp, uppercase, `letterSpacing = 0.06.sp`,
 * `onSurfaceVariant`. This is distinct from [SectionHeader] (14sp Bold primary),
 * which is the larger accent-colored header used for settings sections.
 *
 * Per `DESIGN_LANGUAGE/01-principles/core-principles.md` #6 (accent-colored
 * left-aligned section headers) — the list variant uses a subdued
 * `onSurfaceVariant` instead of accent so it reads as a quiet grouping label,
 * not a primary section title.
 *
 * @param text The label (will be uppercased).
 * @param modifier Standard modifier.
 */
@Composable
fun ListSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.06.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}
