package com.testplaybyte.animeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.testplaybyte.animeapp.data.AniListClient
import com.testplaybyte.animeapp.model.AiringSchedule
import com.testplaybyte.animeapp.theme.WarnDark
import com.testplaybyte.animeapp.ui.components.CollapsingHeader
import com.testplaybyte.animeapp.ui.components.NavIcons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ScheduleScreen — weekly airing schedule.
 *
 * Mirrors the web prototype (`schedule-screen.tsx` + `schedule-screen.module.css`):
 *   - Pinned `CollapsingHeader("Schedule")` outside the scroll Column.
 *   - Horizontal day selector (Today/Tomorrow/Wed/Thu/Fri/Sat/Sun) with airing count.
 *     Active pill has `primaryContainer` background + `onPrimaryContainer` text.
 *   - Airing list — LazyColumn-like Column of rows: 48×64dp cover with EP badge,
 *     title + format/score/ep-total meta, and a time column (HH:MM absolute +
 *     relative "in 3h" / "2h ago"). Past entries are dimmed (alpha 0.5);
 *     the next-up entry is highlighted with a primary border + primaryContainer tint.
 *   - Loading/error/empty states.
 *   - 110dp bottom padding for the floating nav bar.
 *
 * The day selector sits BETWEEN the CollapsingHeader and the scroll Column so
 * it stays visible (matching the prototype's `.daySelector` with `flex: 0 0 auto`).
 * The header collapses based on the scroll Column's ScrollState.
 */
@Composable
fun ScheduleScreen(
    onOpenAnime: (Int) -> Unit,
) {
    val client = remember { AniListClient() }
    val scrollState = rememberScrollState()

    var schedule by remember { mutableStateOf<List<AiringSchedule>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf(0) } // 0..6 (Today=0)

    // Compute week window in unix SECONDS (AniList uses seconds).
    val weekStart = remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis / 1000
    }
    val weekEnd = remember { weekStart + 7 * 24 * 60 * 60 }

    // Build day labels (Today/Tomorrow/short-weekday) + per-day window.
    val weekDays = remember {
        val today = Calendar.getInstance()
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        (0 until 7).map { offset ->
            val cal = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val label = when (offset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> dayFmt.format(cal.time)
            }
            val startCal = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            ScheduleDay(
                index = offset,
                label = label,
                startSec = startCal.timeInMillis / 1000,
                endSec = startCal.timeInMillis / 1000 + 24 * 60 * 60,
            )
        }
    }

    // Fetch the 7-day airing schedule once. runCatching → empty list + error msg on failure.
    LaunchedEffect(Unit) {
        loading = true
        val result = runCatching { client.fetchAiringSchedule(weekStart, weekEnd) }
        schedule = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
        loading = false
    }

    val daySchedule = remember(schedule, selectedDay) {
        val d = weekDays[selectedDay]
        schedule.filter { it.airingAt in d.startSec until d.endSec }
    }
    val dayCounts = remember(schedule) {
        weekDays.map { d -> schedule.count { it.airingAt in d.startSec until d.endSec } }
    }
    val nowSec = System.currentTimeMillis() / 1000
    // Next-up = first airing whose time is still in the future.
    val nextUpId = remember(schedule, nowSec) {
        schedule.firstOrNull { it.airingAt >= nowSec }?.id
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned collapsing header — sits OUTSIDE the scroll Column.
        CollapsingHeader(title = "Schedule", scrollState = scrollState)

        // ── Day selector (horizontal, scrollable, stays visible) ────────────
        // Matches prototype's `.daySelector` — `flex: 0 0 auto`, primaryContainer active.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(weekDays) { day ->
                val isSelected = day.index == selectedDay
                DayPill(
                    label = day.label,
                    count = dayCounts[day.index],
                    isSelected = isSelected,
                    onClick = { selectedDay = day.index },
                )
            }
        }

        // ── Scrollable airing list ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp),
        ) {
            when {
                loading && schedule.isEmpty() -> ScheduleLoadingState()
                error != null -> ScheduleErrorState(message = error!!)
                daySchedule.isEmpty() -> ScheduleEmptyState(dayLabel = weekDays[selectedDay].label)
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        daySchedule.forEach { sched ->
                            AiringRow(
                                sched = sched,
                                isPast = sched.airingAt < nowSec,
                                isNextUp = sched.id == nextUpId,
                                onClick = { onOpenAnime(sched.media.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ScheduleDay — internal model for one day in the 7-day week window.
// ─────────────────────────────────────────────────────────────────────────────

private data class ScheduleDay(
    val index: Int,
    val label: String,
    val startSec: Long,
    val endSec: Long,
)

// ─────────────────────────────────────────────────────────────────────────────
// DayPill — single day button in the day selector.
// 62×60dp column, surfaceVariant default / primaryContainer active.
// Top: short weekday label (11sp bold). Bottom: airing count (16sp bold) or "—".
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .size(width = 62.dp, height = 60.dp)
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (count > 0) count.toString() else "—",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AiringRow — one airing schedule entry.
// Layout: 48×64dp cover (with EP badge overlay) | title+meta column | time column.
// Past entries dimmed (alpha 0.5). Next-up gets primary border + primaryContainer tint.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AiringRow(
    sched: AiringSchedule,
    isPast: Boolean,
    isNextUp: Boolean,
    onClick: () -> Unit,
) {
    val media = sched.media
    val airingMs = sched.airingAt * 1000L
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val alpha = if (isPast) 0.5f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isNextUp) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .then(
                if (isNextUp) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                ) else Modifier,
            )
            .clickable { onClick() }
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Cover thumbnail (48×64dp) with EP badge ───────────────────────
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = media.coverUrl,
                    contentDescription = media.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // EP badge — bottom-of-cover pill, 8sp bold white on translucent black.
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(2.dp),
                ) {
                    Text(
                        text = "EP ${sched.episode}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.02.sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            // ── Title + meta column ───────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (media.format != null) {
                        Text(
                            text = media.format,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (media.averageScore != null) {
                        // \u2605 = ★ BLACK STAR (Unicode symbol, not an emoji).
                        Text(
                            text = "\u2605 ${media.scoreFormatted}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WarnDark,
                        )
                    }
                    if (media.episodes != null) {
                        Text(
                            text = "${media.episodes} ep total",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))

            // ── Time column (HH:MM absolute + relative) ───────────────────────
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(56.dp),
            ) {
                Text(
                    text = timeFmt.format(Date(airingMs)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = relativeTime(sched.airingAt),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isNextUp) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// States — loading spinner, error, empty.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScheduleLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Loading schedule…",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Couldn't load schedule",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScheduleEmptyState(dayLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Calendar icon (NavIcons.Schedule) — matches prototype's empty-state SVG.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NavIcons.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Nothing airing",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No anime scheduled for $dayLabel.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// relativeTime — "in 3h" / "in 12m" / "in 2d" for future, "2h ago" / "45m ago"
// / "2d ago" for past. Matches prototype's relativeFuture / relativePast helpers.
// ─────────────────────────────────────────────────────────────────────────────

private fun relativeTime(airingAtSec: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = airingAtSec - now
    return when {
        diff < 0 -> {
            val m = (-diff) / 60
            when {
                m < 60 -> "${m}m ago"
                m < 60 * 24 -> "${m / 60}h ago"
                else -> "${m / (60 * 24)}d ago"
            }
        }
        diff == 0L -> "now"
        else -> {
            val m = diff / 60
            when {
                m < 60 -> "in ${m}m"
                m < 60 * 24 -> "in ${m / 60}h"
                else -> "in ${m / (60 * 24)}d"
            }
        }
    }
}
