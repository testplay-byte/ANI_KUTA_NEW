package app.confused.anikuta.feature.my

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import app.confused.anikuta.feature.my.components.BehindStatusTab
import app.confused.anikuta.feature.my.components.ChangeAvatarSheet
import app.confused.anikuta.feature.my.components.CustomizationSheet
import app.confused.anikuta.feature.my.components.EditProfileDialog
import app.confused.anikuta.feature.my.components.GenreAnimeSheet
import app.confused.anikuta.feature.my.components.GenreRadarChart
import app.confused.anikuta.feature.my.components.ProfileHeader
import app.confused.anikuta.feature.my.components.ProfileTabBar
import app.confused.anikuta.feature.my.components.QuickStatsRow
import app.confused.anikuta.feature.my.components.RecentlyWatchedSection
import app.confused.anikuta.feature.my.components.ResetStatsDialog
import app.confused.anikuta.feature.my.components.StatusDistributionSection
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * My Profile screen — redesigned with tab bar, radar chart, and proper sections.
 *
 * Two tabs:
 * 1. **Main** — Profile header, Quick Stats, Genre Radar Chart, Status
 *    Distribution, Recently Watched.
 * 2. **Behind Status** — Summary cards (Total / Caught Up / Behind) + behind
 *    anime list.
 *
 * No refresh button (removed per user feedback). Settings button in top-right
 * opens the CustomizationSheet.
 *
 * Design: #B1F256 primary, RobotoFamily font, surfaceVariant cards (alpha 0.4f)
 * with RoundedCornerShape(12dp).
 */
@Composable
fun ProfileScreen(
    onOpenAnime: (Int) -> Unit,
    onOpenTrackers: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences: ProfilePreferences = koinInject()
    val lazyListState = rememberLazyListState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Main, 1 = Behind Status
    var showCustomization by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditName by remember { mutableStateOf(false) }
    var showChangeAvatar by remember { mutableStateOf(false) }
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
            } else if (stats != null) {
                // Tab bar
                ProfileTabBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    when (selectedTab) {
                        0 -> {
                            // ── Main Tab ──
                            // Profile Header
                            item {
                                ProfileHeader(
                                    displayName = state.effectiveDisplayName,
                                    avatarUrl = state.effectiveAvatarUrl,
                                    isAniListLinked = state.isAniListLinked,
                                )
                            }

                            // Quick Stats
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

                            // Genre Radar Chart
                            if (preferences.showGenreChart.get() && stats.genreDistribution.isNotEmpty()) {
                                item {
                                    GenreRadarChart(
                                        genres = stats.genreDistribution,
                                        onGenreClick = { genre -> selectedGenre = genre },
                                    )
                                }
                            }

                            // Status Distribution
                            if (preferences.showStatusChart.get() && stats.statusDistribution.isNotEmpty()) {
                                item {
                                    StatusDistributionSection(
                                        statusDistribution = stats.statusDistribution,
                                    )
                                }
                            }

                            // Recently Watched
                            if (preferences.showRecentlyWatched.get() && stats.recentlyWatched.isNotEmpty()) {
                                item {
                                    RecentlyWatchedSection(
                                        recentlyWatched = stats.recentlyWatched,
                                        onOpenAnime = onOpenAnime,
                                    )
                                }
                            }
                        }
                        1 -> {
                            // ── Behind Status Tab ──
                            item {
                                BehindStatusTab(
                                    totalAnime = stats.totalAnime,
                                    behindAnime = stats.behindAnime,
                                    onOpenAnime = onOpenAnime,
                                )
                            }
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

        // Customization sheet
        if (showCustomization) {
            CustomizationSheet(
                preferences = preferences,
                onChangeName = {
                    showCustomization = false
                    showEditName = true
                },
                onChangeAvatar = {
                    showCustomization = false
                    showChangeAvatar = true
                },
                onResetStats = {
                    showCustomization = false
                    showResetDialog = true
                },
                onDismiss = { showCustomization = false },
            )
        }

        // Edit name dialog
        if (showEditName) {
            EditProfileDialog(
                currentName = state.displayName,
                onConfirm = { name ->
                    viewModel.setDisplayName(name)
                    showEditName = false
                },
                onDismiss = { showEditName = false },
            )
        }

        // Change avatar sheet
        if (showChangeAvatar) {
            ChangeAvatarSheet(
                currentAvatarUrl = state.displayAvatarUrl,
                onConfirm = { url ->
                    viewModel.setDisplayAvatarUrl(url)
                    showChangeAvatar = false
                },
                onDismiss = { showChangeAvatar = false },
            )
        }

        // Genre anime sheet
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
