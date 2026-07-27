package com.testplaybyte.animeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.testplaybyte.animeapp.data.HistoryRepository
import com.testplaybyte.animeapp.model.HistoryItem
import com.testplaybyte.animeapp.ui.components.CollapsingHeader
import com.testplaybyte.animeapp.ui.components.ContinueWatching
import com.testplaybyte.animeapp.ui.components.NavIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * HistoryScreen — recently viewed anime.
 *
 * Mirrors the web prototype (`history-screen.tsx` + `history-screen.module.css`):
 *   - Pinned `CollapsingHeader("History")` outside the scroll Column so the
 *     title stays visible (shrinks 32sp → 22sp on scroll).
 *   - ContinueWatching horizontal rail at the top (only shown if there are
 *     in-progress items).
 *   - "Recently Viewed" section header (16sp bold, 16dp horizontal padding).
 *   - Vertical list of rows: cover thumbnail (48×64dp), title, "EP X · time ago".
 *   - Empty state: clock icon (NavIcons.History) + "No history yet" + desc.
 *   - 110dp bottom padding for the floating nav bar.
 */
@Composable
fun HistoryScreen(
    onOpenAnime: (Int) -> Unit,
) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository(context) }
    val scrollState = rememberScrollState()
    val items by historyRepo.items.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned collapsing header — sits OUTSIDE the scroll Column so it always stays visible.
        CollapsingHeader(title = "History", scrollState = scrollState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp), // reserved for the floating nav bar
        ) {
            // ── Continue Watching rail ──────────────────────────────────────
            // ContinueWatching internally filters to items with progress > 0 and
            // renders nothing if empty. Match the prototype's `padding: var(--sp-2) 0 110px`.
            if (items.any { it.progress > 0 }) {
                ContinueWatching(items = items, onClick = onOpenAnime)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Recently Viewed header ──────────────────────────────────────
            // 16sp bold, onBackground, 16dp horizontal padding (matches prototype's
            // `.sectionHeader { margin: var(--sp-4) var(--sp-3) var(--sp-2) }` ≈ 16dp/12dp/8dp).
            Text(
                text = "Recently Viewed",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )

            // ── Recently Viewed list OR empty state ─────────────────────────
            if (items.isEmpty()) {
                HistoryEmptyState()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    items.forEach { item ->
                        HistoryRow(item = item, onClick = { onOpenAnime(item.id) })
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HistoryRow — one recently-viewed entry: cover (48×64dp), title, "EP X · time ago".
// Matches prototype's `.row` / `.rowCover` / `.rowInfo` / `.rowTitle` / `.rowMeta`.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover thumbnail — 48×64dp, 4dp rounded corners, surfaceVariant fallback.
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Title — 14sp SemiBold, 1 line, ellipsis (matches `.rowTitle`).
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Meta — 11sp onSurfaceVariant. "EP X · time ago" (only show EP label if episode > 1,
            // matching the prototype's `item.episode > 1 ? EP X · : ""`).
            Spacer(Modifier.height(2.dp))
            val epPrefix = if (item.episode > 1) "EP ${item.episode} · " else ""
            Text(
                text = "$epPrefix${timeAgo(item.viewedAt)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HistoryEmptyState — clock icon (NavIcons.History) + "No history yet" + desc.
// Matches prototype's `.emptyState` / `.emptyStateIcon` (72dp circle, surface-2 bg).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // NavIcons.History is a clock-with-arrow vector — matches the prototype's
            // SVG (circle + clock hands) without needing material-icons-extended.
            Icon(
                imageVector = NavIcons.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No history yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Anime you view will appear here.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// timeAgo — relative time formatter.
//   < 1 min  → "Just now"
//   < 1 hour → "5m ago"
//   < 1 day  → "3h ago"
//   < 1 week → "2d ago"
//   older    → locale date ("MMM d, yyyy")
// ─────────────────────────────────────────────────────────────────────────────

private fun timeAgo(timestamp: Long): String {
    val delta = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> {
            val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            fmt.format(Date(timestamp))
        }
    }
}
