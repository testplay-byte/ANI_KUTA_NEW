package app.anikuta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A section header — the M3 Expressive pattern used across the app.
 *
 * Layout: [3×16dp tonal accent bar] [uppercase labelMedium Bold text]
 *
 * The accent bar is a small vertical pill in [accentColor] (defaults to
 * primary). The text is uppercase, Bold, with 1sp letter spacing, in
 * [accentColor].
 *
 * Related files:
 *   - HomeScreen.kt HomeSection — the original (uses this pattern inline)
 *   - SettingsComponents.kt — similar pattern for settings groups
 *   - LibraryScreen.kt, HistoryScreen.kt, SearchScreen.kt — should use this
 *
 * @param text the section label
 * @param accentColor color of the accent bar + text (defaults to primary)
 * @param modifier applied to the Row
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 3×16dp tonal accent bar
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .background(accentColor, CircleShape),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = accentColor,
        )
    }
}
