package app.confused.anikuta.feature.updates

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.common.util.formatTimeAgo
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.EmptyState
import app.confused.anikuta.core.designsystem.component.ListSectionHeader
import app.confused.anikuta.core.designsystem.theme.Motion
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.updatechecker.UpdateResult
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

/**
 * The Updates screen — two sub-tabs (Updates / Schedule) reached from More.
 *
 * @param onBack Pop the Updates screen.
 * @param onOpenAnime Open the anime detail page by AniList ID.
 * @param viewModel Injected via Koin.
 */
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    viewModel: UpdatesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Updates", collapsed = false)

        // ── Tab strip (Updates / Schedule) — centered-pill style ──
        UpdatesTabStrip(
            selected = state.activeTab,
            onSelect = { viewModel.setTab(it) },
        )

        when (state.activeTab) {
            UpdatesTab.UPDATES -> UpdatesTabContent(
                state = state,
                onCheck = { viewModel.checkForUpdates() },
                onOpenAnime = onOpenAnime,
            )
            UpdatesTab.SCHEDULE -> ScheduleTabContent(
                state = state,
                onRefresh = { viewModel.fetchSchedule() },
                onOpenAnime = onOpenAnime,
                onSelectDay = { viewModel.selectCalendarDay(it) },
                onDismissDaySheet = { viewModel.selectCalendarDay(null) },
                onSetViewMode = { viewModel.setScheduleViewMode(it) },
            )
        }
    }
}

/**
 * The two-option tab strip — "Updates" / "Schedule". Same centered-pill style
 * as the library settings sheet (a 2-way segmented toggle).
 */
@Composable
private fun UpdatesTabStrip(
    selected: UpdatesTab,
    onSelect: (UpdatesTab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UpdatesTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = tween(Motion.DurationStandard),
                    label = "tabBg_${tab.name}",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(Motion.DurationStandard),
                    label = "tabText_${tab.name}",
                )
                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) },
                ) {
                    Text(
                        text = tab.label,
                        color = textColor,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The Updates tab — pull-to-refresh + last-checked timestamp + list of
 * [UpdateResult] rows (or empty state).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesTabContent(
    state: UpdatesState,
    onCheck: () -> Unit,
    onOpenAnime: (Int) -> Unit,
) {
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.isChecking,
        onRefresh = onCheck,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.updates.isEmpty() && !state.isChecking -> {
                EmptyState(
                    title = "No new episodes",
                    description = if (state.lastCheckedAt > 0) {
                        "Last checked ${formatTimeAgo(state.lastCheckedAt)}. Pull down to refresh."
                    } else {
                        "Pull down to check your library for new episodes."
                    },
                    icon = Icons.Filled.Update,
                    actionLabel = "Check now",
                    onAction = onCheck,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // Last-checked timestamp header.
                    item {
                        Text(
                            text = if (state.lastCheckedAt > 0) {
                                "Last checked ${formatTimeAgo(state.lastCheckedAt)}"
                            } else {
                                "Not checked yet — pull down to check"
                            },
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    item { ListSectionHeader(text = "New episodes") }
                    items(
                        count = state.updates.size,
                        key = { idx -> state.updates[idx].anime.id },
                    ) { idx ->
                        val result = state.updates[idx]
                        UpdateRow(
                            result = result,
                            onClick = {
                                result.anime.anilistId?.let { onOpenAnime(it) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One Updates row — cover, title, "N new episodes", SUB/DUB badges, "checked Xh ago".
 */
@Composable
private fun UpdateRow(
    result: UpdateResult,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail (56×80dp portrait, 8dp rounded).
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val cover = result.anime.coverUrl
                if (!cover.isNullOrEmpty()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = result.anime.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.anime.title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${result.newEpisodeCount} new episode${if (result.newEpisodeCount == 1) "" else "s"}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (result.hasSub || result.hasDub) {
                        AudioBadges(hasSub = result.hasSub, hasDub = result.hasDub)
                    }
                    Text(
                        text = "Checked ${formatTimeAgo(result.checkedAt)}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
