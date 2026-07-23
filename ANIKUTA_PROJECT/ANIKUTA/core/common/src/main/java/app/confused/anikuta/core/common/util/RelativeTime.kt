package app.confused.anikuta.core.common.util

import java.util.concurrent.TimeUnit

/**
 * Formats an epoch-millis timestamp as a short relative-time string looking
 * backwards from now (e.g. "just now", "3m ago", "2h ago", "5d ago").
 *
 * Used by the History + Updates screens to show when an episode was watched
 * or when the last update check ran. There was no existing "X ago" helper in
 * the codebase — `LibraryListRow.formatRelativeDate` only formats an absolute
 * "MMM d, yyyy".
 *
 * Buckets (deliberately coarse — this is a phone notification-style label,
 * not a precise timestamp):
 *  - < 1 min  → "just now"
 *  - < 1 hour → "Nm ago"
 *  - < 1 day  → "Nh ago"
 *  - < 7 days → "Nd ago"
 *  - < 4 weeks→ "Nw ago"
 *  - older    → absolute "MMM d, yyyy" (delegates to the same format the
 *               library uses, for visual consistency)
 */
fun formatTimeAgo(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (epochMs <= 0L) return "—"
    val delta = nowMs - epochMs
    if (delta < 0L) return "just now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)

    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        days < 28L -> "${days / 7}w ago"
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            fmt.format(java.util.Date(epochMs))
        }
    }
}

/**
 * Formats a future epoch-millis timestamp as a short countdown relative to now
 * (e.g. "in 45m", "in 3h", "in 2d", "Tomorrow at 14:00", "Mar 5 at 09:00").
 *
 * Used by the Updates > Schedule tab to show when an upcoming episode airs.
 *
 * Buckets:
 *  - < 1 hour → "in Nm"
 *  - < 1 day  → "in Nh"
 *  - next day → "Tomorrow at HH:MM"
 *  - < 7 days → "in Nd"
 *  - older    → "MMM d at HH:MM"
 */
fun formatTimeUntil(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (epochMs <= 0L) return "—"
    val delta = epochMs - nowMs
    if (delta <= 0L) return "now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)

    val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return when {
        minutes < 60L -> "in ${minutes}m"
        hours < 24L -> "in ${hours}h"
        days == 1L -> "Tomorrow at ${timeFmt.format(java.util.Date(epochMs))}"
        days < 7L -> "in ${days}d"
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d 'at' HH:mm", java.util.Locale.getDefault())
            fmt.format(java.util.Date(epochMs))
        }
    }
}

/**
 * Formats a pair of (positionSeconds, durationSeconds) as a playback
 * timestamp "M:SS / M:SS" (or "H:MM:SS / H:MM:SS" for content over an hour).
 *
 * Used by the History screen to show how far the user watched — e.g.
 * "12:34 / 24:00". Zero duration → "—".
 *
 * Examples:
 *  - (754, 1440)  → "12:34 / 24:00"
 *  - (30, 1440)   → "0:30 / 24:00"
 *  - (3725, 5400) → "1:02:05 / 1:30:00"
 */
fun formatPlaybackTimestamp(positionSeconds: Int, durationSeconds: Int): String {
    if (durationSeconds <= 0) return "—"
    return "${formatDuration(positionSeconds)} / ${formatDuration(durationSeconds)}"
}

/** Formats a single duration in seconds as "M:SS" or "H:MM:SS". */
private fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds < 0) return "0:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Formats a future epoch-millis timestamp as a detailed live countdown string.
 *
 * Used by the Schedule list for Today/Tomorrow entries, where the user wants
 * a precise ticking countdown. Returns one of:
 *  - "Xh Ym Zs" (>= 1 hour remaining) — e.g. "14h 36m 24s"
 *  - "Ym Zs"    (< 1 hour, >= 1 min)  — e.g. "36m 24s"
 *  - "Zs"       (< 1 min)             — e.g. "24s"
 *  - "now"      (<= 0)
 *
 * The caller pairs this with the episode number: "Episode 16 in 14h 36m 24s".
 * Ticking is driven by the UI recomposing every second (LaunchedEffect).
 */
fun formatDetailedCountdown(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val delta = epochMs - nowMs
    if (delta <= 0L) return "now"
    val totalSecs = (delta / 1000L).toInt()
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Returns the calendar day key for an epoch-millis timestamp, as "yyyy-MM-dd"
 * in the device's default timezone. Used by the History screen's day-bucket
 * grouping (Today / Yesterday / This Week / Earlier) and by the Schedule
 * calendar to place episodes on the correct day cell.
 *
 * Calendar-day based (not 24-hour deltas) per the History design spec —
 * "Today" always means the current calendar day regardless of when the user
 * opens the screen.
 */
fun calendarDayKey(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMs
    return "%04d-%02d-%02d".format(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

/**
 * Classifies an epoch-millis timestamp into a coarse relative-day bucket:
 * 0 = Today, 1 = Yesterday, 2 = This Week (2–6 days ago),
 * 3 = Earlier (7+ days ago). Used by the History screen's section grouping.
 *
 * Compares calendar days (not raw deltas) so "Yesterday" is stable across
 * midnight.
 */
fun relativeDayBucket(epochMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
    if (epochMs <= 0L) return 3
    val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) -
        then.get(java.util.Calendar.DAY_OF_YEAR) +
        (now.get(java.util.Calendar.YEAR) - then.get(java.util.Calendar.YEAR)) * 365
    return when {
        dayDiff <= 0 -> 0
        dayDiff == 1 -> 1
        dayDiff in 2..6 -> 2
        else -> 3
    }
}
