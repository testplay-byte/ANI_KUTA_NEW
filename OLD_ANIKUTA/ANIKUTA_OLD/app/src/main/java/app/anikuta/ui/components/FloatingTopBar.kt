package app.anikuta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A floating pill-shaped top bar — the M3 Expressive pattern used across
 * Home, Library, History, and Search.
 *
 * Shape: RoundedCornerShape(20.dp)
 * Surface: surfaceContainerHigh, tonalElevation 3dp, shadowElevation 6dp
 *
 * The bar is split in two:
 *   Left: [title] text (or a custom [leading] composable, e.g. a search field)
 *   Right: [actions] (icons, dropdowns, etc.)
 *
 * Related files:
 *   - HomeScreen.kt FloatingTopBar — the original (still used)
 *   - LibraryScreen.kt LibraryTopBar — should use this
 *   - HistoryScreen.kt HistoryTopBar — should use this
 *   - SearchScreen.kt — should use this with a BasicTextField in [leading]
 *
 * @param title optional title text (shown if [leading] is null)
 * @param leading optional custom leading content (e.g. search field). Overrides [title].
 * @param actions trailing actions (icons, dropdowns)
 * @param modifier applied to the outer Surface
 */
@Composable
fun FloatingTopBar(
    title: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: leading (custom) or title
            if (leading != null) {
                leading()
            } else if (title != null) {
                androidx.compose.material3.Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            // Right: actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
