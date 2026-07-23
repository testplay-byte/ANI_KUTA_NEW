package app.confused.anikuta.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.util.calendarDayKey
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import java.util.Calendar

/**
 * The monthly calendar view for the Schedule tab.
 *
 * A 7-column grid (weeks as rows). Days with scheduled episodes get a
 * `primary`-colored dot under the day number and a subtle `primary` 15%-alpha
 * background tint. Tapping a day calls [onSelectDay] with the "yyyy-MM-dd" key,
 * which the parent turns into a [ModalBottomSheet] listing that day's episodes.
 *
 * Month navigation: left/right arrows in the header. No external calendar
 * library — the grid is built with plain Compose (Row of weighted day cells).
 *
 * Per `DESIGN_LANGUAGE/01-principles/core-principles.md` #2, the day-detail
 * bottom sheet uses `dragHandle = null`.
 */
@Composable
fun ScheduleCalendar(
    entries: List<ScheduleEntry>,
    selectedDay: String?,
    onSelectDay: (String?) -> Unit,
    onOpenAnime: (Int) -> Unit,
    onDismissSheet: () -> Unit,
    jumpToTodaySignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    // Index entries by "yyyy-MM-dd" for O(1) day-cell lookup.
    val byDay = remember(entries) {
        entries.groupBy { calendarDayKey(it.airingAtMillis) }
    }

    // Currently-displayed month. Using `remember` (not rememberSaveable) because
    // Calendar isn't auto-saveable; a config change resets to the current month,
    // which is acceptable. The `by` delegate makes reassignment recompose.
    var displayedMonth by remember {
        mutableStateOf(
            Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) },
        )
    }

    // "Jump to today" — when the signal counter changes, reset the displayed
    // month to the current month. LaunchedEffect avoids resetting on initial
    // composition (signal starts at 0).
    androidx.compose.runtime.LaunchedEffect(jumpToTodaySignal) {
        if (jumpToTodaySignal > 0) {
            displayedMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        }
    }

    Column(modifier = modifier) {
        CalendarHeader(
            month = displayedMonth,
            onPrev = {
                displayedMonth = (displayedMonth.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1)
                }
            },
            onNext = {
                displayedMonth = (displayedMonth.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                }
            },
        )

        WeekdayHeader()

        // Build the grid weeks.
        val weeks = remember(displayedMonth, byDay) {
            buildMonthWeeks(displayedMonth)
        }
        weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { dayInfo ->
                    DayCell(
                        dayInfo = dayInfo,
                        hasEpisodes = dayInfo != null && byDay.containsKey(dayInfo.key),
                        isSelected = dayInfo != null && dayInfo.key == selectedDay,
                        onTap = {
                            if (dayInfo != null) {
                                onSelectDay(dayInfo.key)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ── Day-detail bottom sheet ──
    if (selectedDay != null) {
        val dayEntries = byDay[selectedDay].orEmpty()
        CalendarDaySheet(
            dayKey = selectedDay,
            entries = dayEntries,
            onOpenAnime = onOpenAnime,
            onDismiss = onDismissSheet,
        )
    }
}

/** Header: ‹  Month Year  › */
@Composable
private fun CalendarHeader(
    month: Calendar,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val fmt = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = fmt.format(month.time),
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Single-letter weekday header row (S M T W T F S). */
@Composable
private fun WeekdayHeader() {
    val letters = remember {
        listOf("S", "M", "T", "W", "T", "F", "S")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        letters.forEach {
            Text(
                text = it,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One day cell. Null = empty cell (padding day outside the month). */
@Composable
private fun DayCell(
    dayInfo: DayInfo?,
    hasEpisodes: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            hasEpisodes -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = dayInfo != null, onClick = onTap),
    ) {
        if (dayInfo != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dayInfo.dayOfMonth.toString(),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = if (hasEpisodes) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (hasEpisodes) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (hasEpisodes) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/**
 * The bottom sheet shown when a calendar day is tapped — lists that day's
 * episodes. `dragHandle = null` per design principle #2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDaySheet(
    dayKey: String,
    entries: List<ScheduleEntry>,
    onOpenAnime: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null, // Per DESIGN_LANGUAGE principle #2.
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = formatSheetHeader(dayKey),
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (entries.isEmpty()) {
                Text(
                    text = "No episodes airing this day.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEach { entry ->
                    DaySheetRow(entry = entry, onClick = { onOpenAnime(entry.anilistId) })
                }
            }
        }
    }
}

@Composable
private fun DaySheetRow(entry: ScheduleEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(6.dp))
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
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.animeTitle,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = "Episode ${entry.episodeNumber} · ${formatAirTime(entry.airingAtMillis)}",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Formats the sheet header for a "yyyy-MM-dd" key as "EEEE, MMM d". */
private fun formatSheetHeader(dayKey: String): String {
    val parts = dayKey.split("-")
    if (parts.size != 3) return dayKey
    val cal = Calendar.getInstance().apply {
        clear()
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }
    val fmt = java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault())
    return fmt.format(cal.time)
}

/** Formats an epoch-ms airing time as "HH:mm". */
private fun formatAirTime(epochMs: Long): String {
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochMs))
}

/**
 * Builds the weeks for [month]: a list of 7-element rows. Each element is a
 * [DayInfo] (or null for padding cells before the 1st / after the last day).
 * The grid always starts on Sunday and is padded so the 1st lands on the
 * correct weekday.
 */
private fun buildMonthWeeks(month: Calendar): List<List<DayInfo?>> {
    val cal = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday (Calendar.SUNDAY == 1)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = mutableListOf<DayInfo?>()
    // Leading padding.
    repeat(firstDayOfWeek) { cells.add(null) }
    // Days.
    for (day in 1..daysInMonth) {
        val dayCal = (month.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, day)
        }
        cells.add(
            DayInfo(
                dayOfMonth = day,
                key = "%04d-%02d-%02d".format(
                    month.get(Calendar.YEAR),
                    month.get(Calendar.MONTH) + 1,
                    day,
                ),
            ),
        )
    }
    // Trailing padding to fill the last week.
    while (cells.size % 7 != 0) cells.add(null)

    return cells.chunked(7)
}

private data class DayInfo(
    val dayOfMonth: Int,
    val key: String, // "yyyy-MM-dd"
)
