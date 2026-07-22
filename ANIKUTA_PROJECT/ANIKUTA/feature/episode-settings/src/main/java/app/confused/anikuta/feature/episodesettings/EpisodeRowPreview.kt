package app.confused.anikuta.feature.episodesettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.animedetails.EpisodeDisplayPrefs

// Demo data — fixed so the user can see how a "typical" episode renders.
private const val DEMO_TITLE = "The Dragon's Labyrinth"
private const val DEMO_SYNOPSIS = "A young adventurer discovers a hidden labyrinth beneath the ancient city and must navigate its traps before dawn."
private const val DEMO_DATE = "Mar 15, 2024"
private const val DEMO_EP_NUM = 5f
private const val DEMO_THUMB_URL = "https://placehold.co/240x135/1a1a2e/e94560.png?text=EP+5"

/**
 * The live preview shown at the top of every episode-settings screen.
 *
 * Renders a representative episode row with dummy data, parameterized by the
 * current [prefs] snapshot. The preview is **sticky** (non-scrolling) — placed
 * above the scrollable options list — so the user sees the effect of every
 * toggle immediately.
 *
 * Mirrors the OLD ANIKUTA project's `EpisodeRowPreview` (488-line file). This
 * implementation is intentionally a single focused composable that mirrors the
 * real `EpisodeRow` in `feature:anime-details` (same badge style, same layout
 * knobs, same audio-pill design). If the real row changes, update this preview
 * to match.
 *
 * @param prefs The current display-prefs snapshot.
 * @param hasSub Whether the demo row should show a SUB pill (default true).
 * @param hasDub Whether the demo row should show a DUB pill (default true).
 * @param hasHsub Whether the demo row should show a HSUB pill (default false).
 */
@Composable
fun EpisodeRowPreview(
    prefs: EpisodeDisplayPrefs,
    hasSub: Boolean = true,
    hasDub: Boolean = true,
    hasHsub: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // "LIVE PREVIEW" label
        Text(
            text = "LIVE PREVIEW",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        PreviewEpisodeRow(prefs, hasSub, hasDub, hasHsub)
    }
}

@Composable
private fun PreviewEpisodeRow(
    prefs: EpisodeDisplayPrefs,
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
) {
    val epNumText = "EP ${formatPreviewEpNumber(DEMO_EP_NUM)}"
    val (thumbW, thumbH) = when (prefs.thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            // ── Top row: thumbnail + title ──
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Thumbnail (left)
                if (prefs.showThumbnails && prefs.thumbnailPosition == "left") {
                    PreviewThumbnail(thumbW, thumbH, prefs, epNumText)
                    Spacer(modifier = Modifier.size(10.dp))
                } else if (prefs.showEpisodeNumber && prefs.episodeNumberPosition == "badge") {
                    // Inline badge (no thumbnail or badge-mode)
                    InlineEpBadge(epNumText)
                    Spacer(modifier = Modifier.size(10.dp))
                } else if (!prefs.showThumbnails && prefs.showEpisodeNumber && prefs.episodeNumberPosition != "badge") {
                    // Circle fallback (no thumbnail)
                    CircleEpNumber(formatPreviewEpNumber(DEMO_EP_NUM))
                    Spacer(modifier = Modifier.size(10.dp))
                }

                // Title column (when title is "right" of thumbnail)
                if (prefs.showTitles && prefs.titlePosition == "right") {
                    Column(modifier = Modifier.weight(1f)) {
                        PreviewTitle(prefs)
                        // Date + audio pills inline beside title (when date position is "right_*")
                        if (prefs.showDates && (prefs.datePosition == "right_above_synopsis" || prefs.datePosition == "right_below_synopsis")) {
                            if (prefs.datePosition == "right_above_synopsis") {
                                Spacer(Modifier.size(4.dp))
                                PreviewDateAndAudioRow(prefs, hasSub, hasDub, hasHsub)
                            }
                        }
                        if (prefs.showSummaries && prefs.synopsisPosition == "right") {
                            Spacer(Modifier.size(4.dp))
                            PreviewSynopsis(prefs)
                        }
                        if (prefs.showDates && prefs.datePosition == "right_below_synopsis") {
                            Spacer(Modifier.size(4.dp))
                            PreviewDateAndAudioRow(prefs, hasSub, hasDub, hasHsub)
                        }
                    }
                } else if (prefs.showTitles && prefs.titlePosition == "below" &&
                    (prefs.showThumbnails || prefs.episodeNumberPosition == "badge" ||
                        (!prefs.showThumbnails && prefs.showEpisodeNumber && prefs.episodeNumberPosition != "badge"))) {
                    // Title is below — fill the remaining horizontal space so the
                    // thumbnail/badge/circle keeps its natural size.
                    Spacer(modifier = Modifier.weight(1f))
                } else if (!prefs.showTitles && (prefs.showThumbnails || prefs.episodeNumberPosition == "badge")) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Thumbnail (right)
                if (prefs.showThumbnails && prefs.thumbnailPosition == "right") {
                    Spacer(modifier = Modifier.size(10.dp))
                    PreviewThumbnail(thumbW, thumbH, prefs, epNumText, alignEnd = true)
                }
            }

            // Title "below" thumbnail (full width)
            if (prefs.showTitles && prefs.titlePosition == "below") {
                Spacer(Modifier.size(8.dp))
                PreviewTitle(prefs)
            }

            // Synopsis "below" (full width)
            if (prefs.showSummaries && prefs.synopsisPosition == "below") {
                Spacer(Modifier.size(6.dp))
                PreviewSynopsis(prefs)
            }

            // Date + audio pills "below" (full-width row)
            if (prefs.showDates && prefs.datePosition == "below") {
                Spacer(Modifier.size(6.dp))
                PreviewDateAndAudioRow(prefs, hasSub, hasDub, hasHsub)
            }
        }
    }
}

@Composable
private fun PreviewThumbnail(
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    prefs: EpisodeDisplayPrefs,
    epNumText: String,
    alignEnd: Boolean = false,
) {
    Box {
        // Use a gradient Box as the thumbnail (no network in preview; falls back gracefully)
        Box(
            modifier = Modifier
                .size(width = w, height = h)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFFE94560), Color(0xFFFFA500)),
                    ),
                ),
        )
        // Episode number overlay — old-project style: black 70% pill, 6dp corners, "EP N" white
        if (prefs.showEpisodeNumber && prefs.episodeNumberPosition == "overlay") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(if (alignEnd) Alignment.TopEnd else Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = epNumText,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun InlineEpBadge(epNumText: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = epNumText,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CircleEpNumber(numText: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = numText,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewTitle(prefs: EpisodeDisplayPrefs) {
    Text(
        text = DEMO_TITLE,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = prefs.titleMaxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PreviewSynopsis(prefs: EpisodeDisplayPrefs) {
    Text(
        text = DEMO_SYNOPSIS,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = prefs.synopsisMaxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PreviewDateAndAudioRow(
    prefs: EpisodeDisplayPrefs,
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
) {
    if (!prefs.showDates && !prefs.showAudioPills) return
    val showAnyAudio = prefs.showAudioPills && (hasSub || hasDub || hasHsub)
    if (!prefs.showDates && !showAnyAudio) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefs.showDates) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {
                Text(
                    text = DEMO_DATE,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (showAnyAudio) {
            PreviewAudioPills(hasSub, hasDub, hasHsub)
        }
    }
}

/**
 * The audio-pills design from the OLD project: a single `outlineVariant` surface
 * holding all detected versions. When 2+ versions are present, uses short letters
 * ("S", "D") separated by 3dp dots; when only one, uses the full label ("SUB").
 */
@Composable
private fun PreviewAudioPills(hasSub: Boolean, hasDub: Boolean, hasHsub: Boolean) {
    data class Audio(val full: String, val short: String)
    val parts = buildList {
        if (hasSub) add(Audio("SUB", "S"))
        if (hasDub) add(Audio("DUB", "D"))
        if (hasHsub) add(Audio("HSUB", "H"))
    }
    val useShort = parts.size >= 2
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            parts.forEachIndexed { idx, audio ->
                if (idx > 0) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Text(
                    text = if (useShort) audio.short else audio.full,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private fun formatPreviewEpNumber(num: Float): String {
    if (num <= 0f) return "?"
    return if (num == num.toLong().toFloat()) num.toLong().toString() else num.toString()
}
