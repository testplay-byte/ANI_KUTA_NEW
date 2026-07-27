package app.anikuta.ui.detail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikuta.download.Download
import app.anikuta.source.api.model.SEpisode
import app.anikuta.ui.detail.EpisodeTitleParser
import coil3.compose.AsyncImage

/**
 * Data class holding all display preferences for episode rows.
 *
 * This avoids passing 10+ individual parameters to each episode row
 * content composable. The settings come from [PlayerPreferences] and
 * are user-configurable in Settings → Episode Display.
 */
internal data class EpisodeDisplaySettings(
    val showThumbnails: Boolean,
    val showSummaries: Boolean,
    val showTitles: Boolean,
    val showDates: Boolean,
    val showEpisodeNumber: Boolean,
    val showAudioPills: Boolean,
    val synopsisPosition: String,
    val datePosition: String,
    val thumbnailSize: String,
    val titlePosition: String,
    val episodeNumberPosition: String,
    val thumbnailPosition: String,
    val downloadButtonPlacement: String,
)

/**
 * Default display settings (matches the default values in PlayerPreferences).
 */
internal val DefaultDisplaySettings = EpisodeDisplaySettings(
    showThumbnails = true,
    showSummaries = true,
    showTitles = true,
    showDates = true,
    showEpisodeNumber = true,
    showAudioPills = true,
    synopsisPosition = "right",
    datePosition = "right_below_synopsis",
    thumbnailSize = "medium",
    titlePosition = "right",
    episodeNumberPosition = "overlay",
    thumbnailPosition = "left",
    downloadButtonPlacement = "episode_row",
)

/**
 * The content of an episode row — chooses between rich (with thumbnail/summary)
 * and simple (text-only) layouts based on available data.
 *
 * This composable is designed to be placed inside an [EpisodeRow] which
 * handles swipe gestures, grayscale, and click/long-click. It does NOT
 * handle any gestures itself.
 *
 * Grayscale for watched episodes is applied at the [EpisodeRow] container
 * level via [Modifier.grayscaleIfSeen], so this composable does not need
 * to know whether the episode is watched.
 */
@Composable
internal fun EpisodeRowContent(
    episode: SEpisode,
    settings: EpisodeDisplaySettings,
    index: Int = 0,
    downloadStatus: Map<String, Download.State> = emptyMap(),
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadedOnDisk: Set<String> = emptySet(),
    onDownloadClick: () -> Unit = {},
    onDownloadLongClick: () -> Unit = {},
) {
    val hasThumbnail = settings.showThumbnails && !episode.preview_url.isNullOrBlank()
    val hasSummary = settings.showSummaries && !episode.summary.isNullOrBlank()
    val isRich = hasThumbnail || hasSummary

    if (isRich) {
        EpisodeRowRich(
            episode = episode,
            hasThumbnail = hasThumbnail,
            hasSummary = hasSummary,
            settings = settings,
            index = index,
            downloadStatus = downloadStatus,
            downloadProgress = downloadProgress,
            downloadedOnDisk = downloadedOnDisk,
            onDownloadClick = onDownloadClick,
            onDownloadLongClick = onDownloadLongClick,
        )
    } else {
        EpisodeRowSimple(
            episode = episode,
            settings = settings,
        )
    }
}

/**
 * Rich episode row — with thumbnail and/or summary.
 *
 * Layout structure:
 * ```
 * ┌─────────────────────────────────────────────┐
 * │ [Thumbnail]  Title (surfaceContainer bg)     │
 * │              Date pills  Audio pills         │
 * │              Synopsis (surfaceContainer bg)  │
 * └─────────────────────────────────────────────┘
 * ```
 *
 * The thumbnail and content positions are configurable via [settings].
 */
@Composable
private fun EpisodeRowRich(
    episode: SEpisode,
    hasThumbnail: Boolean,
    hasSummary: Boolean,
    settings: EpisodeDisplaySettings,
    index: Int,
    downloadStatus: Map<String, Download.State>,
    downloadProgress: Map<String, Int>,
    downloadedOnDisk: Set<String>,
    onDownloadClick: () -> Unit,
    onDownloadLongClick: () -> Unit,
) {
    var summaryExpanded by remember { mutableStateOf(false) }

    val (thumbWidth, thumbHeight) = when (settings.thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp  // medium (default)
    }

    val scanlatorUpper = episode.scanlator?.uppercase() ?: ""
    val hasSub = scanlatorUpper.contains("SUB")
    val hasDub = scanlatorUpper.contains("DUB")
    val hasHsub = scanlatorUpper.contains("HSUB")
    val hasDate = settings.showDates && episode.date_upload > 0
    val hasAnyPills = hasDate || (settings.showAudioPills && (hasSub || hasDub || hasHsub))

    @Composable
    fun DateAudioPillsRow() {
        if (hasAnyPills) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasDate) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Text(
                            text = formatDate(episode.date_upload),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (settings.showAudioPills && (hasSub || hasDub || hasHsub)) {
                    AudioPills(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
                }
            }
        }
    }

    @Composable
    fun SynopsisContent() {
        if (!hasSummary) return

        if (settings.downloadButtonPlacement == "synopsis") {
            // Two separated panels: synopsis + download button
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Text(
                        text = episode.summary!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable { summaryExpanded = !summaryExpanded },
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                DownloadButtonTall(
                    episodeUrl = episode.url,
                    downloadStatus = downloadStatus,
                    downloadProgress = downloadProgress,
                    downloadedOnDisk = downloadedOnDisk,
                    onDownload = onDownloadClick,
                    onLongClick = onDownloadLongClick,
                    index = index,
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = episode.summary!!,
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
            // Thumbnail (left position)
            if (hasThumbnail && settings.thumbnailPosition == "left") {
                EpisodeThumbnail(
                    episode = episode,
                    showEpisodeNumber = settings.showEpisodeNumber,
                    episodeNumberPosition = settings.episodeNumberPosition,
                    thumbWidth = thumbWidth,
                    thumbHeight = thumbHeight,
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else if (!hasThumbnail && settings.showEpisodeNumber && settings.episodeNumberPosition != "badge") {
                // No thumbnail — show episode number circle
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = EpisodeTitleParser.formatEpisodeNumber(episode.episode_number),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Right-side content column
            Column(modifier = Modifier.weight(1f)) {
                if (settings.titlePosition == "right" || !hasThumbnail) {
                    EpisodeTitleRow(episode = episode, settings = settings)
                }

                if (hasThumbnail && settings.datePosition == "right_above_synopsis" && hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPillsRow()
                }

                if (hasThumbnail && settings.synopsisPosition == "right" && hasSummary) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SynopsisContent()
                }

                if (hasThumbnail && settings.datePosition == "right_below_synopsis" && hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPillsRow()
                }
            }

            // Thumbnail (right position)
            if (hasThumbnail && settings.thumbnailPosition == "right") {
                Spacer(modifier = Modifier.width(12.dp))
                EpisodeThumbnail(
                    episode = episode,
                    showEpisodeNumber = settings.showEpisodeNumber,
                    episodeNumberPosition = settings.episodeNumberPosition,
                    thumbWidth = thumbWidth,
                    thumbHeight = thumbHeight,
                )
            }
        }

        // Below-thumbnail content (full width)
        if (hasThumbnail) {
            if (settings.titlePosition == "below" && settings.showTitles) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            if (hasSummary && settings.synopsisPosition == "below") {
                Spacer(modifier = Modifier.height(8.dp))
                SynopsisContent()
            }

            if (settings.datePosition == "below" && hasAnyPills) {
                Spacer(modifier = Modifier.height(8.dp))
                DateAudioPillsRow()
            }
        } else {
            // No thumbnail — synopsis and pills go full-width
            if (settings.datePosition == "right_above_synopsis" && hasAnyPills) {
                Spacer(modifier = Modifier.height(6.dp))
                DateAudioPillsRow()
            }
            if (hasSummary) {
                Spacer(modifier = Modifier.height(6.dp))
                SynopsisContent()
            }
            if ((settings.datePosition == "right_below_synopsis" || settings.datePosition == "below") && hasAnyPills) {
                Spacer(modifier = Modifier.height(6.dp))
                DateAudioPillsRow()
            }
        }
    }
}

/**
 * The thumbnail box with optional episode number overlay.
 */
@Composable
private fun EpisodeThumbnail(
    episode: SEpisode,
    showEpisodeNumber: Boolean,
    episodeNumberPosition: String,
    thumbWidth: androidx.compose.ui.unit.Dp,
    thumbHeight: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .width(thumbWidth)
            .height(thumbHeight),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxSize(),
        ) {
            AsyncImage(
                model = episode.preview_url,
                contentDescription = "Episode thumbnail",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        if (showEpisodeNumber && episodeNumberPosition == "overlay") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * The title row with optional episode number badge.
 */
@Composable
private fun EpisodeTitleRow(
    episode: SEpisode,
    settings: EpisodeDisplaySettings,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.showEpisodeNumber && settings.episodeNumberPosition == "badge") {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (settings.showTitles) {
                    EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                } else {
                    "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Simple episode row — no thumbnail, no summary.
 *
 * Layout:
 * ```
 * ┌──────────────────────────────────────────┐
 * │ (EP) Title                                │
 * │      Date pill  Audio pills               │
 * └──────────────────────────────────────────┘
 */
@Composable
private fun EpisodeRowSimple(
    episode: SEpisode,
    settings: EpisodeDisplaySettings,
) {
    val scanlatorUpper = episode.scanlator?.uppercase() ?: ""
    val hasSub = scanlatorUpper.contains("SUB")
    val hasDub = scanlatorUpper.contains("DUB")
    val hasHsub = scanlatorUpper.contains("HSUB")
    val hasDate = settings.showDates && episode.date_upload > 0
    val hasAnyPills = hasDate || (settings.showAudioPills && (hasSub || hasDub || hasHsub))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.showEpisodeNumber && settings.episodeNumberPosition != "badge") {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = EpisodeTitleParser.formatEpisodeNumber(episode.episode_number),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (settings.showEpisodeNumber && settings.episodeNumberPosition == "badge") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (settings.showTitles) {
                            EpisodeTitleParser.getDisplayTitle(episode.name, episode.episode_number)
                        } else {
                            "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (hasAnyPills) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasDate) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Text(
                            text = formatDate(episode.date_upload),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (settings.showAudioPills && (hasSub || hasDub || hasHsub)) {
                    AudioPills(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
                }
            }
        }
    }
}
