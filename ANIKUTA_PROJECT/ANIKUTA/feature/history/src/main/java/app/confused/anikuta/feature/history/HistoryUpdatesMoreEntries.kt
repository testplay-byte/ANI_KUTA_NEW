package app.confused.anikuta.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import app.confused.anikuta.core.designsystem.component.MoreListRow
import app.confused.anikuta.core.designsystem.component.MoreSectionLabel

/**
 * The "Activity" section entries the History + Updates feature adds to the
 * More screen.
 *
 * **Conflict-avoidance design.** The More screen (`MoreScreen`) lives in
 * `app/.../MainActivity.kt` as a private composable. To avoid merge conflicts
 * with other agent branches that also touch `MoreScreen`, this feature does
 * NOT modify `MoreScreen` directly. Instead, the main agent (who merges this
 * branch) wires this composable into `MoreScreen`'s `LazyColumn` with a
 * single `item { HistoryUpdatesMoreEntries(...) }` call.
 *
 * Usage (the main agent adds this inside `MoreScreen`'s `LazyColumn`):
 * ```kotlin
 * item {
 *     HistoryUpdatesMoreEntries(
 *         onOpenHistory = { showHistory = true },
 *         onOpenUpdates = { showUpdates = true },
 *     )
 * }
 * ```
 *
 * @param onOpenHistory Opens the History screen (sets `showHistory = true`).
 * @param onOpenUpdates Opens the Updates screen (sets `showUpdates = true`).
 */
@Composable
fun HistoryUpdatesMoreEntries(
    onOpenHistory: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    Column {
        MoreSectionLabel(text = "Activity")
        MoreListRow(
            icon = Icons.Filled.History,
            title = "History",
            subtitle = "Recently watched episodes",
            onClick = onOpenHistory,
        )
        MoreListRow(
            icon = Icons.Filled.Update,
            title = "Updates",
            subtitle = "New episodes and schedule",
            onClick = onOpenUpdates,
        )
    }
}
