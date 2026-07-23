package app.confused.anikuta.feature.my

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import app.confused.anikuta.feature.my.components.BehindStatusSection
import app.confused.anikuta.feature.my.components.CustomizationSheet
import app.confused.anikuta.feature.my.components.DistributionChart
import app.confused.anikuta.feature.my.components.GenreAnimeSheet
import app.confused.anikuta.feature.my.components.GenreChipsSection
import app.confused.anikuta.feature.my.components.ProfileHeader
import app.confused.anikuta.feature.my.components.QuickStatsRow
import app.confused.anikuta.feature.my.components.RecentlyWatchedSection
import app.confused.anikuta.feature.my.components.ResetStatsDialog
import app.confused.anikuta.feature.my.components.ScoreDistributionSection
import app.confused.anikuta.feature.my.components.StatusDistributionSection
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * My Profile screen — redesigned with proper sections, spacing, and visual hierarchy.
 *
 * Works in two modes (ADR-013):
 * 1. Local mode (no AniList linked): stats from WatchProgressStore + library.
 * 2. AniList mode (linked): enriched stats from AniList API.
 *
 * Design: #B1F256 primary, RobotoFamily font, surfaceVariant cards (alpha 0.4f)
 * with RoundedCornerShape(12dp) to match the More page entries. Compose Canvas
 * charts (no external charting library). Settings button in top-right (NOT back
 * button — device back gesture handles navigation).
 */
@Composable
fun ProfileScreen(
    onOpenAnime: (Int) -> Unit,
    onLinkAniList: () -> Unit,
    onOpenTrackers: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences: ProfilePreferences = koinInject()
    val lazyListState = rememberLazyListState()

    var showCustomization by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }

    // CollapsingHeader collapses when the user scrolls down.
    val isCollapsed = lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 20

    val stats = state.displayStats

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "My Profile",
                collapsed = isCollapsed,
                actions = {
                    // Refresh button (re-fetches AniList stats if linked)
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    // Settings button — opens customization sheet
                    IconButton(onClick = { showCustomization = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Profile settings",
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
                if (stats != null) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                    ) {
                        // Section 1: Profile Header
                        item {
                            ProfileHeader(
                                username = state.anilistUsername,
                                avatarUrl = state.anilistAvatarUrl,
                                isAniListLinked = state.isAniListLinked,
                                onLinkAniList = onLinkAniList,
                            )
                        }

                        // Section 2: Quick Stats
                        if (preferences.showQuickStats.get()) {
                            item {
                                QuickStatsRow(
                                    totalAnime = stats.totalAnime,
                                    totalEpisodes = stats.totalEpisodesWatched,
                                    totalWatchTimeMinutes = stats.totalWatchTimeMinutes,
                                    meanScore = stats.meanScore,
                                )
                            }
                        }

                        // Section 3: Genres (clickable chips)
                        if (preferences.showGenreChart.get() && stats.genreDistribution.isNotEmpty()) {
                            item {
                                GenreChipsSection(
                                    genres = stats.genreDistribution,
                                    onGenreClick = { genre ->
                                        selectedGenre = genre
                                    },
                                )
                            }
                        }

                        // Section 4: Formats (bar chart — from AniList if linked)
                        if (preferences.showFormatChart.get()) {
                            val formatDist = state.anilistStats?.formatDistribution ?: stats.formatDistribution
                            if (formatDist.isNotEmpty()) {
                                item {
                                    DistributionChart(
                                        title = "Formats",
                                        entries = formatDist.toList(),
                                    )
                                }
                            }
                        }

                        // Section 5: Status Distribution (release status cards)
                        if (preferences.showStatusChart.get() && stats.statusDistribution.isNotEmpty()) {
                            item {
                                StatusDistributionSection(
                                    statusDistribution = stats.statusDistribution,
                                )
                            }
                        }

                        // Section 6: Score Distribution (vertical bars)
                        if (preferences.showScoreChart.get()) {
                            val scoreDist = state.anilistStats?.scoreDistribution ?: stats.scoreDistribution
                            if (scoreDist.isNotEmpty()) {
                                item {
                                    ScoreDistributionSection(
                                        scoreDistribution = scoreDist,
                                    )
                                }
                            }
                        }

                        // Section 7: Countries (bar chart — from AniList if linked)
                        if (preferences.showCountryChart.get()) {
                            val countryDist = state.anilistStats?.countryDistribution ?: stats.countryDistribution
                            if (countryDist.isNotEmpty()) {
                                item {
                                    DistributionChart(
                                        title = "Countries",
                                        entries = countryDist.toList(),
                                    )
                                }
                            }
                        }

                        // Section 8: Behind Status (dedicated section)
                        if (preferences.showBehindStatus.get()) {
                            item {
                                BehindStatusSection(
                                    behindAnime = stats.behindAnime,
                                    onOpenAnime = onOpenAnime,
                                )
                            }
                        }

                        // Section 9: Recently Watched (3 items)
                        if (preferences.showRecentlyWatched.get() && stats.recentlyWatched.isNotEmpty()) {
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

        // Customization sheet (section visibility toggles + reset stats)
        if (showCustomization) {
            CustomizationSheet(
                preferences = preferences,
                onResetStats = {
                    showCustomization = false
                    showResetDialog = true
                },
                onDismiss = { showCustomization = false },
            )
        }

        // Genre anime sheet (shows anime in the selected genre)
        if (selectedGenre != null && stats != null) {
            val genre = selectedGenre!!
            val genreAnime = stats.libraryAnime.filter { it.genre.contains(genre) }
            GenreAnimeSheet(
                genre = genre,
                anime = genreAnime,
                onDismiss = { selectedGenre = null },
                onOpenAnime = { id ->
                    selectedGenre = null
                    onOpenAnime(id)
                },
            )
        }

        // Reset stats dialog
        if (showResetDialog) {
            ResetStatsDialog(
                onDismiss = { showResetDialog = false },
                onConfirm = { categories ->
                    if (categories.any { it.name.contains("WATCH") || it.name.contains("ALL") }) {
                        viewModel.resetWatchHistory()
                    }
                },
            )
        }
    }
}
