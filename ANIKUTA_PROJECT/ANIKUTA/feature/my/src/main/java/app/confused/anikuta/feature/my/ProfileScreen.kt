package app.confused.anikuta.feature.my

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.tracker.TrackStatus
import app.confused.anikuta.feature.my.components.BehindStatusSection
import app.confused.anikuta.feature.my.components.DistributionChart
import app.confused.anikuta.feature.my.components.ProfileHeader
import app.confused.anikuta.feature.my.components.QuickStatsRow
import app.confused.anikuta.feature.my.components.RecentlyWatchedSection
import app.confused.anikuta.feature.my.components.ResetStatsDialog
import org.koin.compose.koinViewModel

/**
 * My Profile screen — 10 sections (scrollable page).
 *
 * Works in two modes (ADR-013):
 * 1. Local mode (no AniList linked): stats from WatchProgressStore + library.
 * 2. AniList mode (linked): enriched stats from AniList API.
 *
 * Per design language: uses RobotoFamily font, #B1F256 primary color, Compose
 * Canvas charts (no external charting library), accent-colored section headers.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    onLinkAniList: () -> Unit,
    onOpenTrackers: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    var showCustomization by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "My Profile",
            collapsed = false,
            actions = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val stats = state.displayStats
            if (stats != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // Section 1: Profile Header
                    item {
                        ProfileHeader(
                            username = state.anilistUsername,
                            avatarUrl = state.anilistAvatarUrl,
                            isAniListLinked = state.isAniListLinked,
                            onLinkAniList = onLinkAniList,
                            onOpenCustomization = { showCustomization = true },
                        )
                    }

                    // Section 2: Quick Stats
                    item {
                        QuickStatsRow(
                            totalAnime = stats.totalAnime,
                            totalEpisodes = stats.totalEpisodesWatched,
                            totalWatchTimeMinutes = stats.totalWatchTimeMinutes,
                            meanScore = stats.meanScore,
                        )
                    }

                    // Section 3: Genre Distribution
                    if (stats.genreDistribution.isNotEmpty()) {
                        item {
                            DistributionChart(
                                title = "Genres",
                                entries = stats.genreDistribution.toList(),
                            )
                        }
                    }

                    // Section 4: Format Distribution (from AniList if linked)
                    val formatDist = state.anilistStats?.formatDistribution ?: stats.formatDistribution
                    if (formatDist.isNotEmpty()) {
                        item {
                            DistributionChart(
                                title = "Formats",
                                entries = formatDist.toList(),
                            )
                        }
                    }

                    // Section 5: Status Distribution
                    val statusDist = state.anilistStats?.statusDistribution ?: stats.statusDistribution
                    if (statusDist.isNotEmpty()) {
                        item {
                            DistributionChart(
                                title = "Status",
                                entries = statusDist.mapKeys { it.key.displayName }.toList(),
                            )
                        }
                    }

                    // Section 6: Score Distribution
                    val scoreDist = state.anilistStats?.scoreDistribution ?: stats.scoreDistribution
                    if (scoreDist.isNotEmpty()) {
                        item {
                            DistributionChart(
                                title = "Scores",
                                entries = scoreDist.mapKeys { it.key.toString() }.toList(),
                            )
                        }
                    }

                    // Section 7: Country Distribution
                    val countryDist = state.anilistStats?.countryDistribution ?: stats.countryDistribution
                    if (countryDist.isNotEmpty()) {
                        item {
                            DistributionChart(
                                title = "Countries",
                                entries = countryDist.mapKeys { it.key }.toList(),
                            )
                        }
                    }

                    // Section 8: Behind Status
                    if (stats.behindAnime.isNotEmpty()) {
                        item {
                            BehindStatusSection(
                                behindAnime = stats.behindAnime,
                                onOpenAnime = onOpenAnime,
                            )
                        }
                    }

                    // Section 9: Recently Watched
                    if (stats.recentlyWatched.isNotEmpty()) {
                        item {
                            RecentlyWatchedSection(
                                recentlyWatched = stats.recentlyWatched,
                                onOpenAnime = onOpenAnime,
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No stats yet. Start watching anime to see your stats!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Reset Stats Dialog (Section 10 trigger)
    if (showResetDialog) {
        ResetStatsDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = { categories ->
                // For now, only watch history reset is implemented (calls WatchProgressStore.deleteAll).
                // Other categories are placeholders for future implementation.
                if (categories.any { it.name.contains("WATCH") || it.name.contains("ALL") }) {
                    viewModel.resetWatchHistory()
                }
            },
        )
    }

    // Customization sheet (Section 10) — placeholder for future
    if (showCustomization) {
        showCustomization = false // TODO: implement customization sheet
    }
}

/** Display name for [TrackStatus]. */
private val TrackStatus.displayName: String
    get() = when (this) {
        TrackStatus.WATCHING -> "Watching"
        TrackStatus.COMPLETED -> "Completed"
        TrackStatus.ON_HOLD -> "On Hold"
        TrackStatus.DROPPED -> "Dropped"
        TrackStatus.PLAN_TO_WATCH -> "Plan to Watch"
        TrackStatus.REPEATING -> "Repeating"
    }
