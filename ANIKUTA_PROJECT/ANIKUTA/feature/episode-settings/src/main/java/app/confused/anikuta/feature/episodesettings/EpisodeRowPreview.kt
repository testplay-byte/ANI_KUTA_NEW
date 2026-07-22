package app.confused.anikuta.feature.episodesettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/**
 * The live preview shown at the top of every episode-settings screen.
 *
 * Mirrors the real `EpisodeRow` in `feature:anime-details` — the SAME two-section
 * layout, the SAME separate date/audio pills, the SAME themed green EP badge,
 * the SAME pill heights, the SAME tight synopsis line spacing. If the real row
 * changes, update this preview to match.
 */
@Composable
fun EpisodeRowPreview(
    prefs: EpisodeDisplayPrefs,
    hasSub: Boolean = true,
    hasDub: Boolean = true,
    hasHsub: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
    val bareEpNum = formatPreviewEpNumber(DEMO_EP_NUM)
    val (thumbW, thumbH) = when (prefs.thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }
    val hasAudio = prefs.showAudioPills && (hasSub || hasDub || hasHsub)
    val hasMetaRow = prefs.showDates || hasAudio
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            // ══ TOP SECTION ══
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Thumbnail (left) with themed green EP overlay
                if (prefs.showThumbnails) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(width = thumbW, height = thumbH)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF1A1A2E), Color(0xFFE94560), Color(0xFFFFA500)),
                                    ),
                                ),
                        )
                        if (prefs.showEpisodeNumber) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                            ) {
                                Text(
                                    text = epNumText,
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                } else if (prefs.showEpisodeNumber) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = bareEpNum,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                }

                // Right column: title (top) + meta (bottom, SEPARATE pills)
                if (prefs.showTitles || hasMetaRow) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Title (with optional background)
                        if (prefs.showTitles) {
                            if (prefs.showTitleBackground) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = DEMO_TITLE,
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = prefs.titleMaxLines,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                Text(
                                    text = DEMO_TITLE,
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = prefs.titleMaxLines,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Date + Audio as SEPARATE pills with a spacer above
                        if (hasMetaRow) {
                            Spacer(Modifier.size(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (prefs.showDates) {
                                    PreviewDatePill(showBackground = prefs.showDateBackground)
                                }
                                if (hasAudio) {
                                    PreviewAudioPills(
                                        hasSub = hasSub,
                                        hasDub = hasDub,
                                        hasHsub = hasHsub,
                                        showBackground = prefs.showAudioBackground,
                                    )
                                }
                            }
                        }
                    }
                } else if (prefs.showThumbnails || prefs.showEpisodeNumber) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // ══ BOTTOM SECTION: Synopsis ══
            if (prefs.showSummaries) {
                Spacer(Modifier.size(8.dp))
                if (prefs.showSynopsisBackground) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = DEMO_SYNOPSIS,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = prefs.synopsisMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Text(
                        text = DEMO_SYNOPSIS,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = prefs.synopsisMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewDatePill(showBackground: Boolean) {
    if (showBackground) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {
            Text(
                text = DEMO_DATE,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                maxLines = 1,
                softWrap = false,
            )
        }
    } else {
        Text(
            text = DEMO_DATE,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun PreviewAudioPills(
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
    showBackground: Boolean,
) {
    val parts = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
    if (parts.isEmpty()) return
    if (showBackground) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                parts.forEachIndexed { idx, label ->
                    if (idx > 0) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            parts.forEachIndexed { idx, label ->
                if (idx > 0) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
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
