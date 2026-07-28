package app.confused.anikuta.feature.episodesettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.confused.anikuta.feature.animedetails.EpisodeDisplayPreferences
import org.koin.compose.koinInject

/**
 * The Episode LAYOUT settings screen — position knobs for every element.
 *
 * Full page. Layout:
 * - Sticky [EpisodeRowPreview] at the top.
 * - "POSITIONS" group: thumbnail side, title position, synopsis position, date
 *   position, episode-number position — each a 2-or-3-option [LabeledSegmentedRow].
 * - "SIZES" group: thumbnail size (Small / Medium / Large).
 *
 * Mirrors the OLD ANIKUTA project's `LayoutSettingsScreen`.
 *
 * @param onBack Called when back is pressed.
 */
@Composable
fun EpisodeLayoutSettingsScreen(onBack: () -> Unit) {
    val prefs: EpisodeDisplayPreferences = koinInject()
    val displayPrefs = rememberEpisodeDisplayPrefs()

    val thumbPos by prefs.thumbnailPosition().changes().collectAsState(initial = prefs.thumbnailPosition().get())
    val titlePos by prefs.titlePosition().changes().collectAsState(initial = prefs.titlePosition().get())
    val synopsisPos by prefs.synopsisPosition().changes().collectAsState(initial = prefs.synopsisPosition().get())
    val datePos by prefs.datePosition().changes().collectAsState(initial = prefs.datePosition().get())
    val epNumPos by prefs.episodeNumberPosition().changes().collectAsState(initial = prefs.episodeNumberPosition().get())
    val thumbSize by prefs.thumbnailSize().changes().collectAsState(initial = prefs.thumbnailSize().get())

    SettingsSubpageScaffold(title = "Episode layout", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Sticky live preview (non-scrolling, stays at top) ──
            EpisodeRowPreview(prefs = displayPrefs)
            Spacer(modifier = Modifier.height(16.dp))
            // ── Scrollable options below ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                // ── Positions ──
                // NOTE: Position prefs are currently DORMANT — the episode row uses a
                // single fixed two-section view (top: thumbnail + title + meta;
                // bottom: synopsis). These options are kept for future use when
                // multiple layouts are re-enabled. Only thumbnail SIZE is active.
                item {
                    SettingsGroupCard(title = "Positions") {
                    LabeledSegmentedRow(
                        label = "Thumbnail",
                        description = "Which side the episode thumbnail sits on",
                        options = listOf(
                            "Left" to (thumbPos == "left"),
                            "Right" to (thumbPos == "right"),
                        ),
                        onSelect = { idx -> prefs.thumbnailPosition().set(if (idx == 0) "left" else "right") },
                    )
                    InGroupDivider()
                    LabeledSegmentedRow(
                        label = "Title",
                        description = "Where the episode title appears",
                        options = listOf(
                            "Right" to (titlePos == "right"),
                            "Below" to (titlePos == "below"),
                        ),
                        onSelect = { idx -> prefs.titlePosition().set(if (idx == 0) "right" else "below") },
                    )
                    InGroupDivider()
                    LabeledSegmentedRow(
                        label = "Synopsis",
                        description = "Where the episode description appears",
                        options = listOf(
                            "Right" to (synopsisPos == "right"),
                            "Below" to (synopsisPos == "below"),
                        ),
                        onSelect = { idx -> prefs.synopsisPosition().set(if (idx == 0) "right" else "below") },
                    )
                    InGroupDivider()
                    LabeledSegmentedRow(
                        label = "Date",
                        description = "Where the air date + audio pills appear",
                        options = listOf(
                            "Above" to (datePos == "right_above_synopsis"),
                            "Below" to (datePos == "right_below_synopsis"),
                            "Full" to (datePos == "below"),
                        ),
                        onSelect = { idx ->
                            prefs.datePosition().set(
                                when (idx) {
                                    0 -> "right_above_synopsis"
                                    1 -> "right_below_synopsis"
                                    else -> "below"
                                },
                            )
                        },
                    )
                    InGroupDivider()
                    LabeledSegmentedRow(
                        label = "Episode number",
                        description = "Overlay sits on the thumbnail; Badge sits beside the title",
                        options = listOf(
                            "Overlay" to (epNumPos == "overlay"),
                            "Badge" to (epNumPos == "badge"),
                        ),
                        onSelect = { idx -> prefs.episodeNumberPosition().set(if (idx == 0) "overlay" else "badge") },
                    )
                }
            }
                item {
                    SettingsGroupCard(title = "Sizes") {
                        LabeledSegmentedRow(
                            label = "Thumbnail size",
                            description = "Small (100×56) · Medium (120×68) · Large (160×90)",
                            options = listOf(
                                "Small" to (thumbSize == "small"),
                                "Medium" to (thumbSize == "medium"),
                                "Large" to (thumbSize == "large"),
                            ),
                            onSelect = { idx ->
                                prefs.thumbnailSize().set(
                                    when (idx) {
                                        0 -> "small"
                                        1 -> "medium"
                                        else -> "large"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
