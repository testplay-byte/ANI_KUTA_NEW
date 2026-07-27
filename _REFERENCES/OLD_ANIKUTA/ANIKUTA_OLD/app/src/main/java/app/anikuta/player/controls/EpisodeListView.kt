package app.anikuta.player.controls

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerViewModel
import app.anikuta.player.PlayerEpisodePreferences
import app.anikuta.source.api.model.SEpisode
import app.anikuta.ui.detail.EpisodeTitleParser
import coil3.compose.AsyncImage
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Episode list for the player's minimized mode.
 *
 * Uses PlayerEpisodePreferences (separate from detail page preferences).
 * Matches the detail page's EpisodeRow design: thumbnails, titles, summaries,
 * dates, audio pills — all customizable.
 */
@Composable
fun EpisodeListView(
    viewModel: PlayerViewModel,
    onEpisodeClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodeList by viewModel.episodeList.collectAsState()
    val currentIndex by viewModel.currentEpisodeIndex.collectAsState()
    val isSwitching by viewModel.isSwitchingEpisode.collectAsState()

    // Player-specific episode display preferences
    val prefs: PlayerEpisodePreferences = remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val showTitles by prefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by prefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by prefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by prefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by prefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by prefs.showAudioPills().stateIn(scope).collectAsState()
    val thumbnailSize by prefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePosition by prefs.titlePosition().stateIn(scope).collectAsState()
    val episodeNumberPosition by prefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbnailPosition by prefs.thumbnailPosition().stateIn(scope).collectAsState()
    val synopsisPosition by prefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePosition by prefs.datePosition().stateIn(scope).collectAsState()
    // Download-button prefs + live queue state (Phase: PLAYER-DL-BTN).
    val showDownloadButton by prefs.showDownloadButton().stateIn(scope).collectAsState()
    val downloadButtonPlacement by prefs.downloadButtonPlacement().stateIn(scope).collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedOnDisk by viewModel.downloadedOnDisk.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(episodeList, key = { _, ep -> ep.url }) { index, episode ->
            PlayerEpisodeRow(
                episode = episode,
                index = index,
                isCurrent = index == currentIndex,
                isSwitching = isSwitching && index == currentIndex,
                onClick = { onEpisodeClick(index) },
                showThumbnails = showThumbnails,
                showSummaries = showSummaries,
                showTitles = showTitles,
                showDates = showDates,
                showEpisodeNumber = showEpisodeNumber,
                showAudioPills = showAudioPills,
                thumbnailSize = thumbnailSize,
                titlePosition = titlePosition,
                episodeNumberPosition = episodeNumberPosition,
                thumbnailPosition = thumbnailPosition,
                synopsisPosition = synopsisPosition,
                datePosition = datePosition,
                showDownloadButton = showDownloadButton,
                downloadButtonPlacement = downloadButtonPlacement,
                downloadStatus = downloadStatus,
                downloadProgress = downloadProgress,
                downloadedOnDisk = downloadedOnDisk,
                // TODO: wire to a real enqueue/resume/cancel handler once the
                // player has the anime title + source (currently a no-op stub
                // — see PlayerViewModel.downloadManager note).
                onDownloadClick = {},
                onDownloadLongClick = {},
            )
        }
    }
}

/**
 * Inline version of PlayerEpisodeRow for use inside a LazyColumn's itemsIndexed.
 * Same design but accepts prefs directly (for use when not inside EpisodeListView).
 *
 * Download state is passed in by the caller (the call site collects it from
 * PlayerViewModel). The placement + show/hide prefs are read internally from
 * [prefs] so callers don't have to thread them through.
 */
@Composable
fun PlayerEpisodeRowInline(
    episode: SEpisode,
    index: Int,
    isCurrent: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
    prefs: PlayerEpisodePreferences? = null,
    downloadStatus: Map<String, app.anikuta.download.Download.State> = emptyMap(),
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadedOnDisk: Set<String> = emptySet(),
    onDownloadClick: () -> Unit = {},
    onDownloadLongClick: () -> Unit = {},
) {
    val epPrefs = prefs ?: remember { Injekt.get() }
    val scope = rememberCoroutineScope()
    val showTitles by epPrefs.showEpisodeTitles().stateIn(scope).collectAsState()
    val showSummaries by epPrefs.showEpisodeSummaries().stateIn(scope).collectAsState()
    val showThumbnails by epPrefs.showEpisodeThumbnails().stateIn(scope).collectAsState()
    val showDates by epPrefs.showEpisodeDates().stateIn(scope).collectAsState()
    val showEpisodeNumber by epPrefs.showEpisodeNumber().stateIn(scope).collectAsState()
    val showAudioPills by epPrefs.showAudioPills().stateIn(scope).collectAsState()
    val thumbnailSize by epPrefs.thumbnailSize().stateIn(scope).collectAsState()
    val titlePosition by epPrefs.titlePosition().stateIn(scope).collectAsState()
    val episodeNumberPosition by epPrefs.episodeNumberPosition().stateIn(scope).collectAsState()
    val thumbnailPosition by epPrefs.thumbnailPosition().stateIn(scope).collectAsState()
    val synopsisPosition by epPrefs.synopsisPosition().stateIn(scope).collectAsState()
    val datePosition by epPrefs.datePosition().stateIn(scope).collectAsState()
    val showDownloadButton by epPrefs.showDownloadButton().stateIn(scope).collectAsState()
    val downloadButtonPlacement by epPrefs.downloadButtonPlacement().stateIn(scope).collectAsState()

    PlayerEpisodeRow(
        episode = episode,
        index = index,
        isCurrent = isCurrent,
        isSwitching = isSwitching,
        onClick = onClick,
        showThumbnails = showThumbnails,
        showSummaries = showSummaries,
        showTitles = showTitles,
        showDates = showDates,
        showEpisodeNumber = showEpisodeNumber,
        showAudioPills = showAudioPills,
        thumbnailSize = thumbnailSize,
        titlePosition = titlePosition,
        episodeNumberPosition = episodeNumberPosition,
        thumbnailPosition = thumbnailPosition,
        synopsisPosition = synopsisPosition,
        datePosition = datePosition,
        showDownloadButton = showDownloadButton,
        downloadButtonPlacement = downloadButtonPlacement,
        downloadStatus = downloadStatus,
        downloadProgress = downloadProgress,
        downloadedOnDisk = downloadedOnDisk,
        onDownloadClick = onDownloadClick,
        onDownloadLongClick = onDownloadLongClick,
    )
}

@Composable
private fun PlayerEpisodeRow(
    episode: SEpisode,
    index: Int,
    isCurrent: Boolean,
    isSwitching: Boolean,
    onClick: () -> Unit,
    showThumbnails: Boolean,
    showSummaries: Boolean,
    showTitles: Boolean,
    showDates: Boolean,
    showEpisodeNumber: Boolean,
    showAudioPills: Boolean,
    thumbnailSize: String,
    titlePosition: String,
    episodeNumberPosition: String,
    thumbnailPosition: String,
    synopsisPosition: String,
    datePosition: String,
    // ---- Download button (Phase: PLAYER-DL-BTN) ----
    showDownloadButton: Boolean = true,
    downloadButtonPlacement: String = "episode_row",
    downloadStatus: Map<String, app.anikuta.download.Download.State> = emptyMap(),
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadedOnDisk: Set<String> = emptySet(),
    onDownloadClick: () -> Unit = {},
    onDownloadLongClick: () -> Unit = {},
) {
    val hasThumbnail = showThumbnails && !episode.preview_url.isNullOrBlank()
    val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
    val isRich = hasThumbnail || hasSummary

    // Audio detection from scanlator
    val scanlatorUpper = episode.scanlator?.uppercase() ?: ""
    val hasSub = scanlatorUpper.contains("SUB")
    val hasDub = scanlatorUpper.contains("DUB")
    val hasHsub = scanlatorUpper.contains("HSUB")
    val hasDate = showDates && episode.date_upload > 0
    val hasAnyPills = hasDate || (showAudioPills && (hasSub || hasDub || hasHsub))

    // Alternating colors
    val cardColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    // Highlight current episode with surfaceContainerHigh + primary border
    // (was primaryContainer — user dislikes the blue-ish color)
    val finalCardColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        cardColor
    }
    val borderColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val borderWidth = if (isCurrent) 2.dp else 0.dp
    // The card Surface is rendered inside a Row (so a tall download button can
    // sit beside it when needed). `weight(1f)` is applied at the call site so
    // we keep cardModifier free of RowScope-only modifiers.
    val cardModifier = if (isCurrent) {
        Modifier.border(borderWidth, borderColor, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    // Whether the tall download button renders BESIDE the card (episode_row
    // placement, or synopsis placement with no summary to host it inside).
    val showDownloadOutside = showDownloadButton && (
        downloadButtonPlacement == "episode_row" ||
            (downloadButtonPlacement == "synopsis" && !hasSummary)
    )

    val (thumbWidth, thumbHeight) = when (thumbnailSize) {
        "small" -> 100.dp to 56.dp
        "large" -> 160.dp to 90.dp
        else -> 120.dp to 68.dp
    }

    // Wrap the card Surface in a Row with IntrinsicSize.Min so the tall
    // download button's fillMaxHeight stretches to match the card's height
    // (mirrors the detail page's EpisodeRow + DownloadButtonTall pattern).
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        Surface(
            modifier = Modifier.weight(1f).then(cardModifier),
            shape = RoundedCornerShape(12.dp),
            color = finalCardColor,
            tonalElevation = if (isCurrent) 3.dp else 0.dp,
            shadowElevation = if (isCurrent) 4.dp else 0.dp,
            onClick = onClick,
        ) {
        if (isRich) {
            // Rich row (with thumbnail and/or summary)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Thumbnail (left)
                    if (hasThumbnail && thumbnailPosition == "left") {
                        Box(
                            modifier = Modifier
                                .width(thumbWidth)
                                .height(thumbHeight),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AsyncImage(
                                    model = episode.preview_url,
                                    contentDescription = "Episode thumbnail",
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            if (showEpisodeNumber && episodeNumberPosition == "overlay") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
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
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Right side content
                    Column(modifier = Modifier.weight(1f)) {
                        // Title
                        if (titlePosition == "right" || !hasThumbnail) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (showEpisodeNumber && episodeNumberPosition == "badge") {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ) {
                                            Text(
                                                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (showTitles) {
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

                        // Date above synopsis
                        if (datePosition == "right_above_synopsis" && hasAnyPills) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                        }

                        // Synopsis
                        if (hasThumbnail && synopsisPosition == "right" && hasSummary) {
                            Spacer(modifier = Modifier.height(6.dp))
                            SynopsisContent(
                                summary = episode.summary!!,
                                episodeUrl = episode.url,
                                showDownloadButton = showDownloadButton,
                                downloadButtonPlacement = downloadButtonPlacement,
                                downloadStatus = downloadStatus,
                                downloadProgress = downloadProgress,
                                downloadedOnDisk = downloadedOnDisk,
                                onDownloadClick = onDownloadClick,
                                onDownloadLongClick = onDownloadLongClick,
                                index = index,
                            )
                        }

                        // Date below synopsis
                        if (datePosition == "right_below_synopsis" && hasThumbnail && hasAnyPills) {
                            Spacer(modifier = Modifier.height(6.dp))
                            DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                        }
                    }

                    // Thumbnail (right)
                    if (hasThumbnail && thumbnailPosition == "right") {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.width(thumbWidth).height(thumbHeight),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AsyncImage(
                                    model = episode.preview_url,
                                    contentDescription = "Episode thumbnail",
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            if (showEpisodeNumber && episodeNumberPosition == "overlay") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
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
                }

                // Below-thumbnail content
                if (hasThumbnail) {
                    if (titlePosition == "below" && showTitles) {
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
                    if (hasSummary && synopsisPosition == "below") {
                        Spacer(modifier = Modifier.height(8.dp))
                        SynopsisContent(
                            summary = episode.summary!!,
                            episodeUrl = episode.url,
                            showDownloadButton = showDownloadButton,
                            downloadButtonPlacement = downloadButtonPlacement,
                            downloadStatus = downloadStatus,
                            downloadProgress = downloadProgress,
                            downloadedOnDisk = downloadedOnDisk,
                            onDownloadClick = onDownloadClick,
                            onDownloadLongClick = onDownloadLongClick,
                            index = index,
                        )
                    }
                    if (datePosition == "below" && hasAnyPills) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                    }
                }
            }
        } else {
            // Simple row (no thumbnail, no summary)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showEpisodeNumber && episodeNumberPosition != "badge") {
                        Surface(
                            shape = CircleShape,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = EpisodeTitleParser.formatEpisodeNumber(episode.episode_number),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            if (showEpisodeNumber && episodeNumberPosition == "badge") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (showTitles) {
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
                // Date/pills below title
                if (hasAnyPills) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DateAudioPills(hasDate, showAudioPills, hasSub, hasDub, hasHsub, episode.date_upload)
                }
            }
        }

        // Current episode is indicated by the highlight color only (no text)
        }
        // Tall download button — beside the card (episode_row placement, or
        // synopsis placement with no summary to host it inside).
        if (showDownloadOutside) {
            Spacer(modifier = Modifier.width(8.dp))
            PlayerDownloadButtonTall(
                episodeUrl = episode.url,
                downloadStatus = downloadStatus,
                downloadProgress = downloadProgress,
                downloadedOnDisk = downloadedOnDisk,
                onDownload = onDownloadClick,
                onLongClick = onDownloadLongClick,
                index = index,
            )
        }
    }
}

@Composable
private fun SynopsisContent(
    summary: String,
    episodeUrl: String,
    showDownloadButton: Boolean,
    downloadButtonPlacement: String,
    downloadStatus: Map<String, app.anikuta.download.Download.State>,
    downloadProgress: Map<String, Int>,
    downloadedOnDisk: Set<String>,
    onDownloadClick: () -> Unit,
    onDownloadLongClick: () -> Unit,
    index: Int,
) {
    if (showDownloadButton && downloadButtonPlacement == "synopsis") {
        // Two separated panels side-by-side, each with its own background:
        //  - Left:  synopsis text (reduced width, all corners rounded)
        //  - Right: a dedicated tall button for the download (own background,
        //           all corners rounded), with a 6dp gap between them.
        // Both share the same height (IntrinsicSize.Min + fillMaxHeight).
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            PlayerDownloadButtonTall(
                episodeUrl = episodeUrl,
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
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Tall download button with a dedicated (state-coloured) background and fully
 * rounded corners. Used for BOTH placement modes in the player's episode list:
 *  - "episode_row": rendered beside the episode card; fills the card's height
 *    via the parent Row's IntrinsicSize.Min.
 *  - "synopsis": rendered inside the synopsis area, beside the synopsis text
 *    panel (with a 6dp gap); fills the synopsis height via IntrinsicSize.Min.
 *
 * Mirrors `DownloadButtonTall` in DetailScreen.kt — same state-coloured
 * background logic, same alternating default background by [index], same
 * DOWNLOADING / QUEUE / RESOLVING / MUXING / ERROR / PAUSED / RECONNECTING /
 * DOWNLOADED / default icon set. Kept as a private copy here (rather than
 * shared) to avoid coupling the player package to the detail page's internal
 * composables.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerDownloadButtonTall(
    episodeUrl: String,
    downloadStatus: Map<String, app.anikuta.download.Download.State>,
    downloadProgress: Map<String, Int>,
    downloadedOnDisk: Set<String>,
    onDownload: () -> Unit,
    onLongClick: () -> Unit = {},
    index: Int = 0,
) {
    val status = downloadStatus[episodeUrl]
    val progress = downloadProgress[episodeUrl] ?: 0
    val isOnDisk = downloadedOnDisk.contains(episodeUrl)

    // Alternating default background: contrasts with the episode card's
    // alternating row color (even=surfaceContainerLow, odd=surfaceContainerHigh).
    // The button uses the OPPOSITE level so it never blends into the card.
    val defaultBg = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val backgroundColor = when {
        status == app.anikuta.download.Download.State.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
        status == app.anikuta.download.Download.State.ERROR -> MaterialTheme.colorScheme.errorContainer
        status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        status == app.anikuta.download.Download.State.PAUSED -> defaultBg
        status == app.anikuta.download.Download.State.RECONNECTING -> MaterialTheme.colorScheme.errorContainer
        else -> defaultBg
    }

    val iconColor = when {
        status == app.anikuta.download.Download.State.ERROR -> MaterialTheme.colorScheme.error
        status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> MaterialTheme.colorScheme.primary
        status == app.anikuta.download.Download.State.RECONNECTING -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onDownload,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                status == app.anikuta.download.Download.State.DOWNLOADING -> {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                status == app.anikuta.download.Download.State.QUEUE ||
                    status == app.anikuta.download.Download.State.RESOLVING ||
                    status == app.anikuta.download.Download.State.MUXING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                status == app.anikuta.download.Download.State.ERROR -> {
                    Icon(Icons.Default.Error, contentDescription = "Failed", tint = iconColor, modifier = Modifier.size(24.dp))
                }
                status == app.anikuta.download.Download.State.PAUSED -> {
                    Icon(Icons.Default.Download, contentDescription = "Paused", tint = iconColor, modifier = Modifier.size(24.dp))
                }
                status == app.anikuta.download.Download.State.RECONNECTING -> {
                    val transition = rememberInfiniteTransition(label = "reconnect_player")
                    val spinnerColor by transition.animateColor(
                        initialValue = MaterialTheme.colorScheme.error,
                        targetValue = Color(0xFFFFA000),
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "reconnect_player_color",
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = spinnerColor,
                    )
                }
                status == app.anikuta.download.Download.State.DOWNLOADED || isOnDisk -> {
                    Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = iconColor, modifier = Modifier.size(24.dp))
                }
                else -> {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DateAudioPills(
    hasDate: Boolean,
    showAudioPills: Boolean,
    hasSub: Boolean,
    hasDub: Boolean,
    hasHsub: Boolean,
    dateUpload: Long,
) {
    if (!hasDate && !(showAudioPills && (hasSub || hasDub || hasHsub))) return
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
                    text = formatDate(dateUpload),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (showAudioPills && (hasSub || hasDub || hasHsub)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val audioParts = mutableListOf<String>()
                    if (hasSub) audioParts.add("SUB")
                    if (hasDub) audioParts.add("DUB")
                    if (hasHsub) audioParts.add("HSUB")
                    audioParts.forEachIndexed { idx, label ->
                        if (idx > 0) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(epochMillis))
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun rememberCoroutineScope() = androidx.compose.runtime.rememberCoroutineScope()
