package app.anikuta.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikuta.ui.detail.EpisodeTitleParser

/**
 * Phase 7.5 — Demo live preview of an episode row.
 *
 * Renders a sample episode row with dummy data using the SAME layout logic
 * as the real EpisodeRowRich on the detail page. Updates live as the user
 * changes settings.
 *
 * The preview is a bare episode card (NOT wrapped in SettingsGroupCard) so
 * it looks exactly like a real episode row on the detail page — same padding,
 * same card structure, same pill format, same gradient thumbnail.
 *
 * The gradient uses bright non-theme colors (yellow → orange → red) in a
 * 3-way diagonal: top-left (yellow) → middle (orange, between yellow and red
 * on the color wheel for smooth transition) → bottom-right (red).
 */
@Composable
fun EpisodeRowPreview(
    showThumbnails: Boolean,
    showSummaries: Boolean,
    showTitles: Boolean,
    showDates: Boolean,
    showEpisodeNumber: Boolean,
    showAudioPills: Boolean,
    synopsisPosition: String,
    datePosition: String,
    thumbnailSize: String,
    titlePosition: String,
    episodeNumberPosition: String = "overlay",
    thumbnailPosition: String = "left",
    downloadButtonPlacement: String = "episode_row",
    showDownloadButton: Boolean = true,
) {
    val demoTitle = "The Dragon's Labyrinth"
    val demoSynopsis = "A young adventurer discovers a hidden labyrinth beneath the ancient city, where a mysterious dragon guards a long-forgotten secret that could change the fate of the realm forever."
    val demoDate = "Mar 15, 2024"
    val demoEpisodeNumber = 5f

    val hasThumbnail = showThumbnails
    val hasSummary = showSummaries

    val (thumbWidth, thumbHeight) = when (thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }

    // Demo audio versions (simulating what an extension would put in scanlator)
    val hasSub = true
    val hasDub = true
    val hasHsub = false
    val hasDate = showDates
    val hasAnyPills = hasDate || (showAudioPills && (hasSub || hasDub || hasHsub))

    var summaryExpanded by remember { mutableStateOf(false) }

    // Bright 3-way diagonal gradient: yellow (top-left) → orange (middle) → red (bottom-right).
    // Orange sits between yellow and red on the color wheel, giving a smooth transition.
    // These are fixed bright colors — NOT theme colors — so the preview is always vibrant
    // regardless of light/dark mode.
    val gradientColors = listOf(
        Color(0xFFFFEB3B),  // Bright yellow — top-left
        Color(0xFFFF9800),  // Orange — middle (between yellow and red on the color wheel)
        Color(0xFFEF5350),  // Red — bottom-right
    )

    @Composable
    fun DemoThumbnail() {
        Box(
            modifier = Modifier
                .width(thumbWidth)
                .height(thumbHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            // Episode number overlay — only when position is 'overlay'
            if (showEpisodeNumber && episodeNumberPosition == "overlay") {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                ) {
                    Text(
                        text = "EP ${EpisodeTitleParser.formatEpisodeNumber(demoEpisodeNumber)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    @Composable
    fun EpisodeNumberBadge() {
        // Uses primaryContainer so the badge is a clearly distinct colored pill
        // (separate from the title's surfaceContainer background)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(demoEpisodeNumber)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    // Date + audio pills row — MATCHES the real EpisodeRowRich format:
    // date in its own pill, then audio versions combined in ONE Surface with
    // dot separators between them.
    @Composable
    fun DateAudioPillsRow() {
        if (hasAnyPills) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Date pill (separate)
                if (hasDate) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Text(
                            text = demoDate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                // Audio pills — adaptive (shortens to S•D when space is tight)
                if (showAudioPills && (hasSub || hasDub || hasHsub)) {
                    AudioPillsPreview(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
                }
            }
        }
    }

    @Composable
    fun SynopsisContent() {
        if (hasSummary) {
            if (showDownloadButton && downloadButtonPlacement == "synopsis") {
                // Two separated panels side-by-side, each with its own background:
                //  - Left:  synopsis text (reduced width, all corners rounded)
                //  - Right: a dedicated tall button for the download (own background,
                //           all corners rounded), with a small gap between them.
                // Both share the same height (IntrinsicSize.Min + fillMaxHeight).
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    // Synopsis text — own background, all corners rounded (standalone panel)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text(
                            text = demoSynopsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable { summaryExpanded = !summaryExpanded },
                        )
                    }
                    // Small gap between the two panels (separated, not joined)
                    Spacer(modifier = Modifier.width(6.dp))
                    // Download button — dedicated background, all corners rounded (standalone)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        modifier = Modifier.width(48.dp).fillMaxHeight(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = demoSynopsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable { summaryExpanded = !summaryExpanded },
                    )
                }
            }
        }
    }

    @Composable
    fun TitleContent() {
        // Always render — when titles are off, show "Episode N" as fallback
        // (matches the actual details page behavior)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Episode number badge (if position is 'badge')
                if (showEpisodeNumber && episodeNumberPosition == "badge") {
                    EpisodeNumberBadge()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (showTitles) demoTitle else "Episode ${EpisodeTitleParser.formatEpisodeNumber(demoEpisodeNumber)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // The episode row card — wrapped in a Row so a tall download button can
    // sit beside it for "episode_row" placement (matching the real detail page).
    // For "synopsis" placement the card is full-width and the download button is
    // rendered inside the synopsis area (see SynopsisContent above).
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // Top row: thumbnail + right-side content
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // Thumbnail (left, if position is 'left')
                if (hasThumbnail && thumbnailPosition == "left") {
                    DemoThumbnail()
                    Spacer(modifier = Modifier.width(12.dp))
                } else if (!hasThumbnail && showEpisodeNumber && episodeNumberPosition != "badge") {
                    // No thumbnail — show episode number badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = EpisodeTitleParser.formatEpisodeNumber(demoEpisodeNumber),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Right side content column
                Column(modifier = Modifier.weight(1f)) {
                    // Title on the right side (if position is 'right')
                    if (titlePosition == "right" || !hasThumbnail) {
                        TitleContent()
                    }

                    // Date above synopsis (right side — only when there IS a thumbnail)
                    if (hasThumbnail && datePosition == "right_above_synopsis" && hasAnyPills) {
                        Spacer(modifier = Modifier.height(6.dp))
                        DateAudioPillsRow()
                    }

                    // Synopsis on the right side (only when there IS a thumbnail)
                    if (hasThumbnail && synopsisPosition == "right" && hasSummary) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SynopsisContent()
                    }

                    // Date below synopsis (right side — only when there IS a thumbnail)
                    if (hasThumbnail && datePosition == "right_below_synopsis" && hasAnyPills) {
                        Spacer(modifier = Modifier.height(6.dp))
                        DateAudioPillsRow()
                    }
                }

                // Thumbnail (right, if position is 'right')
                if (hasThumbnail && thumbnailPosition == "right") {
                    Spacer(modifier = Modifier.width(12.dp))
                    DemoThumbnail()
                }
            }

            // Below-thumbnail content (full width)
            if (hasThumbnail) {
                // Title below thumbnail (if position is 'below')
                if (titlePosition == "below" && showTitles) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TitleContent()
                }

                // Synopsis below (if position is 'below')
                if (hasSummary && synopsisPosition == "below") {
                    Spacer(modifier = Modifier.height(8.dp))
                    SynopsisContent()
                }

                // Date + audio pills below (if position is 'below')
                if (datePosition == "below" && hasAnyPills) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DateAudioPillsRow()
                }
            } else {
                // No thumbnail — synopsis and date/pills go full-width below the
                // episode number + title row.
                if (datePosition == "right_above_synopsis" && hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPillsRow()
                }
                if (hasSummary) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SynopsisContent()
                }
                if ((datePosition == "right_below_synopsis" || datePosition == "below") && hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPillsRow()
                }
            }
        }
        }
        // Tall download button — only for "episode_row" placement.
        // Matches the real DownloadButtonTall (48dp wide, fills card height,
        // own background, fully rounded) so the preview accounts for the
        // download button in both placement modes.
        if (showDownloadButton && downloadButtonPlacement == "episode_row") {
            Spacer(modifier = Modifier.width(8.dp))
            // Tall button — dedicated background, all corners rounded, fills card height
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                modifier = Modifier.width(48.dp).fillMaxHeight(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Audio pills (SUB / DUB / HSUB) — ADAPTIVE: keeps everything on ONE row.
 *
 * Heuristic: 2+ versions → short labels (S•D), 1 version → full label (SUB).
 * Mirrors the detail page's AudioPills. Does NOT use BoxWithConstraints
 * (crashes inside Row(IntrinsicSize.Min)).
 */
@Composable
private fun AudioPillsPreview(
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
) {
    if (!hasSub && !hasDub && !hasHsub) return

    data class Audio(val full: String, val short: String)
    val parts = buildList {
        if (hasSub) add(Audio("SUB", "S"))
        if (hasDub) add(Audio("DUB", "D"))
        if (hasHsub) add(Audio("HSUB", "H"))
    }

    // Heuristic: 2+ versions → short labels (S•D), 1 version → full label (SUB)
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
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                CircleShape,
                            ),
                    )
                }
                Text(
                    text = if (useShort) audio.short else audio.full,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
