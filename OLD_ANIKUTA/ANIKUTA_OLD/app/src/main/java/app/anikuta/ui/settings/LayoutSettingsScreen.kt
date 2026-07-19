package app.anikuta.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A custom segmented button row with better visual styling.
 * Rounded container with clear selected/unselected states.
 *
 * NOTE: The shared [StyledSegmentedRow] is now in SelectableOptionCard.kt
 * (internal). This local declaration was removed to avoid a conflicting
 * overload. LayoutSettingsScreen uses the shared one.
 */
// (removed — using shared StyledSegmentedRow from SelectableOptionCard.kt)
@Composable
private fun StyledSegmentedRowRemoved(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, (label, selected) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    onClick = { onSelect(index) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A labeled section with title, description, and content below.
 */
@Composable
private fun LabeledSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * Details → Episode layout subpage.
 *
 * Layout: sticky live preview at top (non-scrolling) + scrollable settings below.
 *
 * Sections:
 *  1. Text content — title and synopsis positions
 *  2. Badges & pills — date/audio pills position
 *  3. Episode number — overlay or badge
 *  4. Thumbnail — position and size
 *  5. Page layout — anime info position (above/below episodes)
 */
@Composable
fun LayoutSettingsScreen(onBack: () -> Unit) {
    val prefs: PlayerPreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val synopsisPos by prefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePos by prefs.datePosition().stateIn(scope).collectAsState()
    val thumbSize by prefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePos by prefs.titlePosition().stateIn(scope).collectAsState()
    val epNumPos by prefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbPos by prefs.thumbnailPosition().stateIn(scope).collectAsState()
    val animeInfoPos by prefs.animeInfoPosition().stateIn(scope).collectAsState()
    // Read display prefs too so the preview reflects the full state
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by prefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by prefs.showAudioPills().stateIn(scope).collectAsState()
    // FIX: read downloadButtonPlacement reactively so the segmented control
    // updates instantly when tapped. Previously used .get() which captured the
    // value once and never recomposed — the toggle only "applied" after
    // navigating away and back.
    val dlPlacement by prefs.downloadButtonPlacement().stateIn(scope).collectAsState()

    SettingsSubpageScaffold(title = "Episode layout", onBack = onBack) {
        // Column: sticky preview at top + scrollable settings below
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Sticky live preview (non-scrolling) ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    "LIVE PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    EpisodeRowPreview(
                        showThumbnails = showThumbnails,
                        showSummaries = showSummaries,
                        showTitles = showTitles,
                        showDates = showDates,
                        showEpisodeNumber = showEpisodeNumber,
                        showAudioPills = showAudioPills,
                        synopsisPosition = synopsisPos,
                        datePosition = datePos,
                        thumbnailSize = thumbSize,
                        titlePosition = titlePos,
                        episodeNumberPosition = epNumPos,
                        thumbnailPosition = thumbPos,
                        downloadButtonPlacement = dlPlacement,
                    )
                }
            }

            // ---- Scrollable settings below ----
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Section 1: Text content
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Text content") {
                            LabeledSection("Title", "Where the episode title appears") {
                                StyledSegmentedRow(
                                    options = listOf("Right" to (titlePos == "right"), "Below" to (titlePos == "below")),
                                    onSelect = { prefs.titlePosition().set(if (it == 0) "right" else "below") },
                                )
                            }
                            HorizontalDivider()
                            LabeledSection("Synopsis", "Where the episode description appears") {
                                StyledSegmentedRow(
                                    options = listOf("Right" to (synopsisPos == "right"), "Below" to (synopsisPos == "below")),
                                    onSelect = { prefs.synopsisPosition().set(if (it == 0) "right" else "below") },
                                )
                            }
                        }
                    }
                }

                // Section 2: Badges & pills
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Badges & pills") {
                            LabeledSection("Date & audio pills", "Where the date and SUB/DUB tags appear") {
                                StyledSegmentedRow(
                                    options = listOf(
                                        "Above" to (datePos == "right_above_synopsis"),
                                        "Below" to (datePos == "right_below_synopsis"),
                                        "Full" to (datePos == "below"),
                                    ),
                                    onSelect = {
                                        prefs.datePosition().set(
                                            when (it) {
                                                0 -> "right_above_synopsis"
                                                1 -> "right_below_synopsis"
                                                else -> "below"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Section 3: Episode number
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Episode number") {
                            LabeledSection("Position", "Overlay on thumbnail or badge next to title") {
                                StyledSegmentedRow(
                                    options = listOf("Overlay" to (epNumPos == "overlay"), "Badge" to (epNumPos == "badge")),
                                    onSelect = { prefs.episodeNumberPosition().set(if (it == 0) "overlay" else "badge") },
                                )
                            }
                        }
                    }
                }

                // Section 4: Thumbnail
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Thumbnail") {
                            LabeledSection("Side", "Left or right side of the episode card") {
                                StyledSegmentedRow(
                                    options = listOf("Left" to (thumbPos == "left"), "Right" to (thumbPos == "right")),
                                    onSelect = { prefs.thumbnailPosition().set(if (it == 0) "left" else "right") },
                                )
                            }
                            HorizontalDivider()
                            LabeledSection("Size", "Small, medium, or large") {
                                StyledSegmentedRow(
                                    options = listOf(
                                        "Small" to (thumbSize == "small"),
                                        "Medium" to (thumbSize == "medium"),
                                        "Large" to (thumbSize == "large"),
                                    ),
                                    onSelect = {
                                        prefs.thumbnailSize().set(
                                            when (it) {
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

                // Section 5: Page layout
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Page layout") {
                            LabeledSection("Anime info", "Above = full-page scroll. Below = episodes in a scrollable section.") {
                                StyledSegmentedRow(
                                    options = listOf("Above episodes" to (animeInfoPos == "above"), "Below episodes" to (animeInfoPos == "below")),
                                    onSelect = { prefs.animeInfoPosition().set(if (it == 0) "above" else "below") },
                                )
                            }
                        }
                    }
                }

                // Section 6: Download button placement
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingsGroupCard(title = "Download button") {
                            LabeledSection("Placement", "Where the download button appears on episode rows") {
                                StyledSegmentedRow(
                                    options = listOf(
                                        "Episode row" to (dlPlacement == "episode_row"),
                                        "Synopsis" to (dlPlacement == "synopsis"),
                                    ),
                                    onSelect = {
                                        prefs.downloadButtonPlacement().set(if (it == 0) "episode_row" else "synopsis")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
