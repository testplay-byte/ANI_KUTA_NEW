package app.confused.anikuta.feature.updates

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.util.calendarDayKey
import app.confused.anikuta.core.common.util.formatTimeUntil
import app.confused.anikuta.core.designsystem.component.EmptyState
import app.confused.anikuta.core.designsystem.component.ListSectionHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import java.util.Calendar

/**
 * The Schedule tab — view-mode toggle (List / Calendar) + content.
 *
 * List mode: chronological list of upcoming episodes, grouped by day.
 * Calendar mode: monthly grid with dots on days that have episodes; tapping a
 * day opens a bottom sheet listing that day's episodes.
 */
@Composable
fun ScheduleTabContent(
    state: UpdatesState,
    onRefresh: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    onSelectDay: (String?) -> Unit,
    onDismissDaySheet: () -> Unit,
    onSetViewMode: (ScheduleViewMode) -> Unit,
    onJumpToToday: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // View-mode toggle row (List / Calendar) + a context-aware action button.
        // Per user feedback: the right-side button was an ambiguous calendar icon
        // that did nothing useful. Now it's:
        //  - Calendar view → "Jump to today" (CalendarToday icon) — resets the
        //    displayed month to the current month.
        //  - List view → "Refresh schedule" (Refresh icon) — re-fetches airing data.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScheduleViewModeToggle(
                mode = state.scheduleViewMode,
                onSetMode = onSetViewMode,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = if (state.scheduleViewMode == ScheduleViewMode.CALENDAR) onJumpToToday else onRefresh,
            ) {
                Icon(
                    imageVector = if (state.scheduleViewMode == ScheduleViewMode.CALENDAR) {
                        Icons.Filled.CalendarToday
                    } else {
                        Icons.Filled.Refresh
                    },
                    contentDescription = if (state.scheduleViewMode == ScheduleViewMode.CALENDAR) {
                        "Jump to today"
                    } else {
                        "Refresh schedule"
                    },
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        when {
            state.scheduleError != null -> {
                EmptyState(
                    title = "Couldn't load schedule",
                    description = state.scheduleError,
                    icon = Icons.Filled.CalendarMonth,
                    actionLabel = "Retry",
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.schedule.isEmpty() && !state.isLoading -> {
                EmptyState(
                    title = "No upcoming episodes",
                    description = "Library anime with scheduled episodes will appear here.",
                    icon = Icons.Filled.CalendarMonth,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.scheduleViewMode == ScheduleViewMode.LIST -> {
                ScheduleList(state = state, onOpenAnime = onOpenAnime)
            }
            state.scheduleViewMode == ScheduleViewMode.CALENDAR -> {
                ScheduleCalendar(
                    entries = state.schedule,
                    selectedDay = state.selectedCalendarDay,
                    onSelectDay = onSelectDay,
                    onOpenAnime = { anilistId ->
                        onDismissDaySheet()
                        onOpenAnime(anilistId)
                    },
                    onDismissSheet = onDismissDaySheet,
                    jumpToTodaySignal = state.calendarJumpSignal,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * A 2-icon toggle for List / Calendar view modes.
 */
@Composable
private fun ScheduleViewModeToggle(
    mode: ScheduleViewMode,
    onSetMode: (ScheduleViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ScheduleViewMode.entries.forEach { vm ->
                val isSelected = vm == mode
                val bg = if (isSelected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent
                val tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSetMode(vm) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (vm == ScheduleViewMode.LIST) Icons.Filled.List
                            else Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = if (vm == ScheduleViewMode.LIST) "List" else "Calendar",
                            color = tint,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The list view — upcoming episodes grouped by calendar day, each row showing
 * cover + title + "Episode N" + relative air time.
 */
@Composable
private fun ScheduleList(
    state: UpdatesState,
    onOpenAnime: (Int) -> Unit,
) {
    // Group entries by "yyyy-MM-dd", preserving chronological order.
    val grouped = remember(state.schedule) {
        state.schedule.groupBy { calendarDayKey(it.airingAtMillis) }
    }
    val dayLabels = remember(grouped) {
        grouped.keys.map { dayKey -> dayKey to formatDayHeader(dayKey) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
    ) {
        dayLabels.forEach { (dayKey, label) ->
            item(key = "header_$dayKey") {
                ListSectionHeader(text = label)
            }
            val entries = grouped[dayKey].orEmpty()
            items(count = entries.size, key = { idx ->
                "${dayKey}_${entries[idx].anilistId}_${entries[idx].episodeNumber}"
            }) { idx ->
                val entry = entries[idx]
                ScheduleRow(
                    entry = entry,
                    onClick = { onOpenAnime(entry.anilistId) },
                )
            }
        }
    }
}

/**
 * One schedule row — cover, title, "Episode N", air-time countdown.
 */
@Composable
private fun ScheduleRow(
    entry: ScheduleEntry,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val cover = entry.coverUrl
                if (!cover.isNullOrEmpty()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = entry.animeTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.animeTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Episode ${entry.episodeNumber}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimeUntil(entry.airingAtMillis),
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Formats a "yyyy-MM-dd" key as a human-readable day header.
 *
 * "Today" / "Tomorrow" for the next two calendar days, then "EEE, MMM d"
 * (e.g. "Wed, Mar 5") for everything else.
 */
private fun formatDayHeader(dayKey: String): String {
    val parts = dayKey.split("-")
    if (parts.size != 3) return dayKey
    val (y, m, d) = parts
    val cal = Calendar.getInstance().apply {
        clear()
        set(y.toInt(), m.toInt() - 1, d.toInt())
    }
    val now = Calendar.getInstance()
    val dayDiff = daysBetween(now, cal)
    return when (dayDiff) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> {
            val fmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
            fmt.format(cal.time)
        }
    }
}

/** Whole-day difference between two Calendar instances (a - b), ignoring time. */
private fun daysBetween(a: Calendar, b: Calendar): Int {
    val aDay = a.get(Calendar.DAY_OF_YEAR) + a.get(Calendar.YEAR) * 366
    val bDay = b.get(Calendar.DAY_OF_YEAR) + b.get(Calendar.YEAR) * 366
    return bDay - aDay
}
