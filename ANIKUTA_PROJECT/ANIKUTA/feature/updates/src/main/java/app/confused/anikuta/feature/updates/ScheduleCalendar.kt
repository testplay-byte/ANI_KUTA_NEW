package app.confused.anikuta.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.util.calendarDayKey
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * A soft "lime blue" — a blue that harmonizes with the lime-green primary
 * (#B1F256) rather than a stark material blue. Used to highlight the CURRENT
 * date in the calendar (per user feedback: "highlight the current date … using
 * the blue color. Use the similar kind of blue color based on the R theme …
 * not the direct outright definition of the lime blue but the one which is
 * similar to the R theme color").
 */
private val LimeBlue = Color(0xFF6EC6E6)
private val LimeBlueFg = Color(0xFF0A2A33)

/**
 * The monthly calendar view for the Schedule tab.
 *
 * Per user feedback (round 2):
 *  - The calendar sits in a dedicated card/section so it reads as a clean unit.
 *  - Date cells have padding between them + subtle borders; leading empty cells
 *    (before the 1st) are NOT rendered as bordered blocks — they're invisible
 *    spacers so the 1st lands in the correct weekday column without showing
 *    empty "blocks".
 *  - The current date is highlighted in a soft lime-blue (see [LimeBlue]).
 *  - The month/year header + prev/next buttons live in a dedicated section that
 *    supports swipe-left/right to switch months (HorizontalPager) with smooth
 *    animated transitions.
 *  - Days with scheduled episodes show a multi-dot indicator (see
 *    [DayDotsIndicator]) colored by each anime's AniList cover color, with a
 *    rainbow gradient bar for 9+ anime.
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

    // SWIPE LIMITS (per user feedback, round 4 — HARD limits):
    //  - Back: only 1 month before the current month. The pager physically
    //    can't scroll further back — no empty red area drags in, no loading of
    //    older months at all.
    //  - Forward: up to 12 months ahead. Same hard limit.
    // This is achieved by setting pageCount = 14 (1 back + current + 12 ahead)
    // with initialPage = 1 (the current month). The pager's own bounds prevent
    // scrolling past page 0 or page 13 — no snap-back needed.
    //
    // The red message shows when the user clicks a chevron at the bound, OR
    // when a swipe reaches the bound (so the user gets feedback that they
    // can't go further).
    val pageCount = 14   // 1 back + current + 12 ahead
    val initialPage = 1  // page 1 = current month; page 0 = 1 month back
    val minPage = 0      // 1 month back (hard floor)
    val maxPage = 13     // 12 months ahead (hard ceiling)
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val scope = rememberCoroutineScope()

    // The red limit-message. Null = hidden. Auto-dismisses after ~4s (LaunchedEffect
    // below) or on any tap (the overlay is clickable to dismiss).
    var limitMessage by remember { mutableStateOf<String?>(null) }

    // Convert a pager page index to a Calendar (page 1 = current month).
    fun pageToMonth(page: Int): Calendar {
        val offset = page - initialPage
        return Calendar.getInstance().apply {
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    fun showBackLimit() { limitMessage = "What are you trying to do?" }
    fun showForwardLimit() { limitMessage = "What are you even trying to do? Stop it." }

    // "Jump to today" — when the signal counter changes, animate the pager back
    // to the current-month page (page 1).
    LaunchedEffect(jumpToTodaySignal) {
        if (jumpToTodaySignal > 0) {
            pagerState.animateScrollToPage(initialPage)
        }
    }

    // Show the red message when the user swipes to OR TRIES TO SWIPE PAST the
    // hard bound. The pager's own bounds prevent actually scrolling past, but
    // we detect the attempt via:
    //  1. currentPage reaching the bound (settled there), OR
    //  2. The user dragging toward the bound while already at it (the pager
    //     resists, but the offset delta reveals the intent). We watch
    //     pagerState.currentPage + pagerState.targetPage + the scroll offset
    //     sign — when at the bound and the drag direction is "past", show it.
    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(
                pagerState.currentPage,
                pagerState.targetPage,
                pagerState.currentPageOffsetFraction,
            )
        }.collect { (current, target, offsetFraction) ->
            // Settled on / dragged to the back bound.
            if (current == minPage) {
                // If the user is dragging further back (offsetFraction < 0 means
                // the next page is being dragged in from the start — but at the
                // hard floor the pager resists; we detect the attempt via target
                // going below minPage OR a negative offset while at minPage).
                if (target < minPage || offsetFraction < -0.05f) {
                    showBackLimit()
                } else if (target == minPage) {
                    showBackLimit()
                }
            }
            // Settled on / dragged to the forward bound.
            if (current == maxPage) {
                if (target > maxPage || offsetFraction > 0.05f) {
                    showForwardLimit()
                } else if (target == maxPage) {
                    showForwardLimit()
                }
            }
        }
    }

    // Auto-dismiss the limit message after ~4s.
    LaunchedEffect(limitMessage) {
        if (limitMessage != null) {
            kotlinx.coroutines.delay(4000L)
            limitMessage = null
        }
    }

    // The month currently in view (driven by the pager's currentPage).
    val displayedMonth = pageToMonth(pagerState.currentPage)

    // The calendar card's horizontal padding — reduced from 16dp to 8dp so the
    // calendar is closer to the screen corners and bigger, per user feedback:
    // "reduce the side padding … make it look much closer to the corner so that
    // the bottom calendar area would be shown bigger."
    val cardHorizontalPadding = 8.dp

    // Put the whole calendar in a dedicated card so it reads as one clean unit.
    // Per user feedback (round 4): use surfaceVariant at 0.4f alpha — the SAME
    // background as the History/Updates row backgrounds — for consistency.
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = cardHorizontalPadding, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            CalendarHeader(
                month = displayedMonth,
                onPrev = {
                    if (pagerState.currentPage <= minPage) {
                        showBackLimit()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                onNext = {
                    if (pagerState.currentPage >= maxPage) {
                        showForwardLimit()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )

            WeekdayHeader()

            // HorizontalPager — swipe left/right to change months, with the
            // default page-slide animation. Each page renders one month's grid.
            //
            // HEIGHT FIX: the pager defaults to fillMaxSize, which made the card
            // background extend far below the last week row (user feedback: "the
            // background is way too large … even though there is no calendar
            // showing anything in that area"). We constrain the pager height to
            // the grid's natural height for the DISPLAYED month — using the
            // actual number of weeks that month spans (5 or 6), not always 6.
            // This way a 5-week month renders a 5-row grid + the background
            // only stretches to those 5 rows. Cell height = (cardWidth - 6×4dp
            // spacing) / 7 since cells are aspectRatio(1f) weighted 1f across 7
            // columns.
            val displayedWeekCount = remember(displayedMonth) { weeksInMonth(displayedMonth) }
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val cardWidthPx = maxWidth.value
                // 6 inter-cell gaps × 4dp spacing across 7 columns.
                val totalSpacingPx = 6f * 4f
                val cellSizePx = (cardWidthPx - totalSpacingPx) / 7f
                // The displayed month's week count drives the height (5 or 6).
                val rowCount = displayedWeekCount.coerceIn(5, 6)
                val gridHeightPx = cellSizePx * rowCount + (rowCount - 1) * 4f
                val gridHeight = gridHeightPx.dp

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                ) { page ->
                    val month = pageToMonth(page)
                    MonthGrid(
                        month = month,
                        byDay = byDay,
                        selectedDay = selectedDay,
                        onSelectDay = onSelectDay,
                    )
                }
            }
        }
    }

    // Red limit-message overlay — shown on top of the calendar (centered). Tapping
    // anywhere dismisses it instantly (with a fade). Auto-dismisses after 4s.
    androidx.compose.animation.AnimatedVisibility(
        visible = limitMessage != null,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val msg = limitMessage
        if (msg != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { limitMessage = null },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = Color(0xCCFF5252),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = msg,
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
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

/** Header: ‹  Month Year  › — inside the calendar card. */
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
            .padding(vertical = 4.dp),
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
    val letters = remember { listOf("S", "M", "T", "W", "T", "F", "S") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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

/** Renders one month's 7-column grid of day cells (with leading spacers). */
@Composable
private fun MonthGrid(
    month: Calendar,
    byDay: Map<String, List<ScheduleEntry>>,
    selectedDay: String?,
    onSelectDay: (String?) -> Unit,
) {
    val weeks = remember(month) { buildMonthWeeks(month) }
    val todayKey = remember {
        val cal = Calendar.getInstance()
        "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { dayInfo ->
                    if (dayInfo == null) {
                        // Leading/trailing spacer — invisible (no bordered block),
                        // per user feedback: "no need to show the empty dates for
                        // the month which does not have the dates."
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dayEntries = byDay[dayInfo.key].orEmpty()
                        DayCell(
                            dayInfo = dayInfo,
                            entries = dayEntries,
                            isToday = dayInfo.key == todayKey,
                            isSelected = dayInfo.key == selectedDay,
                            onTap = { onSelectDay(dayInfo.key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One day cell — the day number + a multi-dot indicator (see
 * [DayDotsIndicator]) colored by each anime's AniList cover color. The current
 * date is highlighted in soft lime-blue; the selected date in primary; days
 * with episodes get a subtle primary tint.
 */
@Composable
private fun DayCell(
    dayInfo: DayInfo,
    entries: List<ScheduleEntry>,
    isToday: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEpisodes = entries.isNotEmpty()
    val cellColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        isToday -> LimeBlue.copy(alpha = 0.22f)
        hasEpisodes -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    }
    val borderColor = when {
        isToday -> LimeBlue.copy(alpha = 0.7f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        hasEpisodes -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    Surface(
        color = cellColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .aspectRatio(1f)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onTap),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = dayInfo.dayOfMonth.toString(),
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = if (hasEpisodes || isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    isToday -> LimeBlueFg
                    hasEpisodes -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (hasEpisodes) {
                Spacer(modifier = Modifier.height(3.dp))
                DayDotsIndicator(entries = entries)
            }
        }
    }
}

/**
 * The multi-dot indicator for a calendar day, per the user's spec:
 *  - 1–4 anime → that many dots below the number.
 *  - 5 anime → 3 below + 1 left + 1 right of the number.
 *  - 6 anime → 3 below + 1 left + 1 right + 1 top.
 *  - 7 anime → 3 below + 1 left + 1 right + 1 top-left + 1 top-right (no top).
 *  - 8 anime → 8 dots in all compass positions around the number.
 *  - 9+ anime → a soft rainbow gradient bar below the number (green/blue/red/
 *    yellow, themed-soft — no dark colors), no dots at the top.
 *
 * Each dot is colored by its anime's AniList `coverColor` (parsed hex). If the
 * cover color is missing, falls back to the theme primary.
 */
@Composable
private fun DayDotsIndicator(entries: List<ScheduleEntry>) {
    val count = entries.size
    // Hoist the primary color OUT of the remember lambda — MaterialTheme access
    // is @Composable and can't happen inside the (non-composable) remember block.
    val primary = MaterialTheme.colorScheme.primary
    val colors = remember(entries, primary) {
        entries.map { it.coverColor?.toColorOrNull() ?: primary }
    }
    when {
        count <= 4 -> DotsRow(colors = colors)
        count == 5 -> DotsAround(colors = colors, below = 3, left = true, right = true)
        count == 6 -> DotsAround(colors = colors, below = 3, left = true, right = true, top = true)
        count == 7 -> DotsAround(
            colors = colors, below = 3, left = true, right = true,
            topLeft = true, topRight = true,
        )
        count == 8 -> DotsCompass(colors = colors)
        else -> RainbowBar()
    }
}

/** A simple centered row of [colors.size] dots (up to 4). */
@Composable
private fun DotsRow(colors: List<Color>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.take(4).forEach { color ->
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * Dots positioned around the day number — [below] dots in a row beneath,
 * plus optional left/right/top/topLeft/topRight dots flanking the number.
 * Used for counts 5–7.
 *
 * NOTE: this composable renders ONLY the surrounding dots (left/right/top/
 * corners). The "below" dots are rendered by this composable too, in a row
 * under the number. The caller ([DayCell]) renders the number itself above
 * this indicator, so "top" / "topLeft" / "topRight" here are actually rendered
 * ABOVE the below-row (i.e. between the number and the below-row) — which reads
 * as flanking the number from the user's perspective.
 */
@Composable
private fun DotsAround(
    colors: List<Color>,
    below: Int,
    left: Boolean = false,
    right: Boolean = false,
    top: Boolean = false,
    topLeft: Boolean = false,
    topRight: Boolean = false,
) {
    // We render a 3-column row: left dot | (top/topLeft-topRight) | right dot,
    // then the below-row underneath. This keeps the layout compact inside the
    // day cell.
    val sideColor = colors.getOrNull(below)
    val topColor = colors.getOrNull(below + 1)
    val tlColor = colors.getOrNull(below + 2)
    val trColor = colors.getOrNull(below + 3)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Top / corner row (only if any top-side dot is needed).
        if (top || topLeft || topRight) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (topLeft && tlColor != null) Dot(tlColor) else Spacer(Modifier.size(4.dp))
                if (top && topColor != null) Dot(topColor) else Spacer(Modifier.size(4.dp))
                if (topRight && trColor != null) Dot(trColor) else Spacer(Modifier.size(4.dp))
            }
        }
        // Side row: left dot | gap | right dot.
        if (left || right) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (left && sideColor != null) Dot(sideColor) else Spacer(Modifier.size(4.dp))
                Spacer(Modifier.width(8.dp))
                if (right && sideColor != null) Dot(sideColor) else Spacer(Modifier.size(4.dp))
            }
        }
        // Below row.
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.take(below).forEach { Dot(it) }
        }
    }
}

/** 8 dots in all compass positions around the number (for exactly 8 anime). */
@Composable
private fun DotsCompass(colors: List<Color>) {
    // 3x3 grid with the center empty (the number sits there in the caller).
    // Positions: TL, T, TR / L, _, R / BL, B, BR — 8 dots.
    val rows = listOf(
        listOf(colors[0], colors[1], colors[2]),
        listOf(colors[3], null, colors[4]),
        listOf(colors[5], colors[6], colors[7]),
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { c ->
                    if (c != null) Dot(c) else Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

/** A soft rainbow gradient bar (green/blue/red/yellow) — for 9+ anime. */
@Composable
private fun RainbowBar() {
    // Soft, theme-harmonized colors (no bright/dark variants).
    val barColors = listOf(
        MaterialTheme.colorScheme.primary, // green (lime)
        Color(0xFF6EC6E6), // soft blue
        Color(0xFFEF9A9A), // soft coral red
        Color(0xFFFFE082), // soft amber yellow
    )
    Box(
        Modifier
            .width(22.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Brush.horizontalGradient(barColors)),
    )
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(color),
    )
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
 * The grid always starts on Sunday; null cells are rendered as invisible
 * spacers (no bordered block) by [MonthGrid].
 */
/**
 * Returns the number of week-rows [month] spans in the calendar grid (5 or 6).
 *
 * A month that starts on Sunday and has 28 days (e.g. Feb in a non-leap year)
 * spans exactly 4 weeks, but our grid pads to full weeks so it renders as 4
 * rows. Most months span 5 or 6. Used to size the pager height dynamically so
 * the card background only stretches to the actual grid height (per user
 * feedback: "the background is way too tall … make sure that the background
 * only gets applied to that specific area").
 */
private fun weeksInMonth(month: Calendar): Int {
    return buildMonthWeeks(month).size
}

private fun buildMonthWeeks(month: Calendar): List<List<DayInfo?>> {
    val cal = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday (Calendar.SUNDAY == 1)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = mutableListOf<DayInfo?>()
    // Leading padding (rendered as invisible spacers, not bordered blocks).
    repeat(firstDayOfWeek) { cells.add(null) }
    // Days.
    for (day in 1..daysInMonth) {
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

/** Parses a "#RRGGBB" / "RRGGBB" hex string to a Color, or null. */
private fun String.toColorOrNull(): Color? {
    return try {
        val hex = removePrefix("#")
        Color(hex.toLong(16).or(0xFF000000))
    } catch (e: Exception) {
        null
    }
}
