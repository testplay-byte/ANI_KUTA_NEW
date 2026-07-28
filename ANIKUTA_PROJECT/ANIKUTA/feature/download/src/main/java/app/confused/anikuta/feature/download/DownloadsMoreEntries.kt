package app.confused.anikuta.feature.download

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import app.confused.anikuta.core.designsystem.component.MoreListRow
import app.confused.anikuta.core.designsystem.component.MoreSectionLabel

/**
 * The "Activity"/"Library" section entry the Downloads feature adds to the
 * More screen.
 *
 * **Conflict-avoidance design** (mirrors `HistoryUpdatesMoreEntries`): this
 * feature does NOT modify `MoreScreen` directly. The main agent (or whoever
 * merges this branch) wires this composable into `MoreScreen`'s `LazyColumn`
 * with a single `item { DownloadsMoreEntries(...) }` call.
 *
 * Usage (added inside `MoreScreen`'s `LazyColumn`):
 * ```kotlin
 * item { DownloadsMoreEntries(onOpenDownloads = { showDownloads = true }) }
 * ```
 */
@Composable
fun DownloadsMoreEntries(
    onOpenDownloads: () -> Unit,
) {
    Column {
        MoreSectionLabel(text = "Library")
        MoreListRow(
            icon = Icons.Filled.Download,
            title = "Downloads",
            subtitle = "Manage downloaded episodes and the download queue",
            onClick = onOpenDownloads,
        )
    }
}
