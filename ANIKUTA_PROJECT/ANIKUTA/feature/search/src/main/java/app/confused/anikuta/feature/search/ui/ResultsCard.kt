package app.confused.anikuta.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.search.viewmodel.SearchResult

/**
 * The results tray — a `surfaceVariant` card holding the section header + the
 * 3-column chunked grid + loading/error/empty states.
 *
 * Ported from the prototype's inline `Surface` results block. Visual rules
 * (copy-paste):
 * - Card: `surfaceVariant.copy(alpha=0.3f)`, `RoundedCornerShape(20.dp)`,
 *   `padding(h=8, v=4)`, inner content `padding(h=4, v=12)`.
 * - Section header: label (16sp ExtraBold `onBackground`, weight 1f) + count
 *   ("N found", 12sp Medium `onSurfaceVariant`) when results exist.
 * - Grid: `results.chunked(3).forEach { Row(spacedBy 8dp) }`, each card
 *   `weight(1f)`, with `Spacer.weight(1f)` padding so 3 columns stay aligned.
 *   Row spacing: 12dp. (Prototype fidelity — NOT LazyVerticalGrid.)
 *
 * Pagination: when [isLoadingMore] is true, a small "Loading more…" footer
 * appears below the grid (the SearchScreen's scroll-listener calls the VM's
 * `onLoadMore` when the user nears the bottom — AniList only).
 *
 * @param sectionLabel e.g. "Popular anime", "Results for \"query\"".
 * @param loading shows "Loading…" instead of the grid (initial load).
 * @param isLoadingMore shows a "Loading more…" footer (pagination).
 * @param error shows the error message in `error` color.
 * @param hasSearched affects the empty-state copy ("No results found for …").
 * @param query used in the empty-state copy.
 * @param results the unified results (AniList OR extension).
 * @param onResultTap called when a result card is tapped — caller routes by type.
 */
@Composable
fun ResultsCard(
    sectionLabel: String,
    loading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    hasSearched: Boolean,
    query: String,
    results: List<SearchResult>,
    onResultTap: (SearchResult) -> Unit,
) {
    val showCount = !loading && error == null && results.isNotEmpty()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            // Section header — label + count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sectionLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (showCount) {
                    Text(
                        text = "${results.size} found",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                loading -> {
                    Text(
                        text = "Loading…",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                error != null -> {
                    Text(
                        text = error,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                results.isEmpty() && hasSearched -> {
                    Text(
                        text = if (query.isNotBlank()) "No results found for \"$query\""
                        else "No results match these filters.",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    // 3-column chunked grid (prototype fidelity).
                    results.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { result ->
                                ResultAnimeCard(
                                    result = result,
                                    onClick = { onResultTap(result) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    // Pagination footer — shown while the next page is loading.
                    if (isLoadingMore) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.padding(8.dp))
                            Text(
                                text = "Loading more…",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
