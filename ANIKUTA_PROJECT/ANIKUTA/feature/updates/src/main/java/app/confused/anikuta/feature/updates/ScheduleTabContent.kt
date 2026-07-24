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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.util.calendarDayKey
import app.confused.anikuta.core.common.util.formatDetailedCountdown
import app.confused.anikuta.core.designsystem.component.EmptyState
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRefresh: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    onSelectDay: (String?) -> Unit,
    onDismissDaySheet: () -> Unit,
    onSetViewMode: (ScheduleViewMode) -> Unit,
    onJumpToToday: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // View-mode toggle row. Per user feedback (round 2):
        //  - List view → the List/Calendar toggle expands to FULL WIDTH (no
        //    right-side button). The list has its own per-row content; the
        //    refresh is reachable via pull-to-refresh on the list.
        //  - Calendar view → the toggle shrinks to leave room for a highlighted
        //    "today date" button on the right (shows today's day number + a
        //    CalendarToday icon). Tapping it jumps the calendar to the current
        //    month.
        val isCalendar = state.scheduleViewMode == ScheduleViewMode.CALENDAR
        // Animate the toggle's weight: full-width (1f) in list view, shrinks to
        // leave room for the today-button in calendar view. animateFloatAsState
        // gives a smooth shrink/slide transition when switching modes.
        val toggleWeight by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isCalendar) 0.82f else 1f,
            animationSpec = androidx.compose.animation.core.tween(300),
            label = "toggleWeight",
        )
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
                modifier = Modifier.weight(toggleWeight),
            )
            // Today-button: animated in/out (slide + fade) when switching to/from
            // calendar view. Shows today's day number INSIDE the calendar icon
            // (Box overlay) rather than beside it.
            androidx.compose.animation.AnimatedVisibility(
                visible = isCalendar,
                enter = androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(300),
                ) + androidx.compose.animation.slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(300),
                    initialOffsetX = { it / 2 },
                ),
                exit = androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(200),
                ) + androidx.compose.animation.slideOutHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(200),
                    targetOffsetX = { it / 2 },
                ),
            ) {
                val todayDay = remember {
                    Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable(onClick = onJumpToToday),
                ) {
                    // The day number sits INSIDE the calendar icon (Box overlay),
                    // per user feedback: "that number, meaning today's current
                    // date, should be inside the calendar icon itself".
                    Box(
                        modifier = Modifier.size(width = 40.dp, height = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Jump to today",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        // The day number, overlaid in the lower-center of the icon
                        // (where a calendar's date box sits). Tiny + bold.
                        Text(
                            text = todayDay,
                            fontFamily = RobotoFamily,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
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
                ScheduleList(state = state, listState = listState, onOpenAnime = onOpenAnime)
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
 * cover + title + an "Episode N" pill + a countdown.
 *
 * Per user feedback (round 2):
 *  - Day headers (Today / Tomorrow / date) are theme-colored with a small
 *    themed vertical bar on the left (see [ScheduleDayHeader]).
 *  - Anime title is 16sp Bold (up from 14sp).
 *  - "Episode N" sits in a primary-tinted pill.
 *  - The countdown is detailed for Today/Tomorrow ("Episode 16 in 14h 36m 24s",
 *    ticking live every second) and a full-date-with-highlighted-time for
 *    beyond ("Episode 4 · Mar 5 at 09:00").
 */
@Composable
private fun ScheduleList(
    state: UpdatesState,
    listState: androidx.compose.foundation.lazy.LazyListState,
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
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
    ) {
        dayLabels.forEach { (dayKey, label) ->
            item(key = "header_$dayKey") {
                ScheduleDayHeader(label = label)
            }
            val entries = grouped[dayKey].orEmpty()
            items(count = entries.size, key = { idx ->
                "${dayKey}_${entries[idx].anilistId}_${entries[idx].episodeNumber}"
            }) { idx ->
                val entry = entries[idx]
                ScheduleRow(
                    entry = entry,
                    dayKey = dayKey,
                    onClick = { onOpenAnime(entry.anilistId) },
                )
            }
        }
    }
}

/**
 * A schedule day header — a small primary-colored vertical bar on the left +
 * the day label in primary color. Per user feedback: "highlight the release
 * date in the theme color … on the left side show a small themed colored
 * portrait bar."
 */
@Composable
private fun ScheduleDayHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Themed vertical bar (3dp × 18dp, primary, rounded).
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One schedule row — cover, title, an "Episode N" pill, and a countdown.
 *
 * The countdown is detailed + live for Today/Tomorrow, and a full-date-with-
 * highlighted-time for beyond.
 */
@Composable
private fun ScheduleRow(
    entry: ScheduleEntry,
    dayKey: String,
    onClick: () -> Unit,
) {
    val isTodayOrTomorrow = isTodayOrTomorrowKey(dayKey)

    // Live-ticking "now" for Today/Tomorrow so the countdown updates each second.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    if (isTodayOrTomorrow) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                // "Episode N" in a primary-tinted pill.
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "Episode ${entry.episodeNumber}",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Countdown: detailed + live for Today/Tomorrow; full date with
                // highlighted time for beyond.
                if (isTodayOrTomorrow) {
                    val countdown = formatDetailedCountdown(entry.airingAtMillis, now)
                    Text(
                        text = "in $countdown",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    val fmt = java.text.SimpleDateFormat("MMM d 'at'", java.util.Locale.getDefault())
                    val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val datePart = fmt.format(java.util.Date(entry.airingAtMillis))
                    val timePart = timeFmt.format(java.util.Date(entry.airingAtMillis))
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$datePart ",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = timePart,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Returns true if [dayKey] ("yyyy-MM-dd") is today or tomorrow (calendar-day
 * based, not 24h delta). Drives whether the row shows the live detailed
 * countdown vs. the full-date layout.
 */
private fun isTodayOrTomorrowKey(dayKey: String): Boolean {
    val parts = dayKey.split("-")
    if (parts.size != 3) return false
    val cal = Calendar.getInstance().apply {
        clear()
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }
    val now = Calendar.getInstance()
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) -
        cal.get(Calendar.DAY_OF_YEAR) +
        (now.get(Calendar.YEAR) - cal.get(Calendar.YEAR)) * 366
    // dayDiff < 0 means the day is in the future; 0 = today, -1 = tomorrow.
    return dayDiff == 0 || dayDiff == -1
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
