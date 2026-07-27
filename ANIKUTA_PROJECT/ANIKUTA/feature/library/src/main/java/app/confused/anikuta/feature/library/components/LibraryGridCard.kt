package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.common.model.BadgePosition
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.common.model.oppositeOnSameEdge
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * A grid cell showing an anime cover + optional title.
 *
 * Badges (episode count + score) use THEMED backgrounds per user request:
 * "you need to do the same thing here for the episode badge and for a score
 * badge too" (i.e. themed colored backgrounds like the episode-count badge).
 *  - Episode badge: themed `primary` green background, `onPrimary` text.
 *  - Score badge: themed `primary` green background, `onPrimary` text.
 *
 * Badge positions are configurable (top/bottom L/R). In compact grid, only
 * top positions are used (bottom is occupied by the title overlay).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridCard(
    item: Anime,
    selected: Boolean,
    selectionMode: Boolean,
    displayMode: LibraryDisplayMode,
    episodeBadgeMode: EpisodeBadgeMode,
    episodeBadgePosition: BadgePosition,
    showScoreBadge: Boolean,
    scoreBadgePosition: BadgePosition,
    titleLines: Int = 2,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Column(modifier = Modifier.alpha(if (selected) 0.7f else 1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!item.coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                // ── Compact grid: title overlaid on cover with bottom gradient ──
                if (displayMode == LibraryDisplayMode.COMPACT_GRID) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.5f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.75f),
                                ),
                            ),
                    )
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoFamily,
                        maxLines = titleLines,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                // ── Episode badge — themed primary green background ──
                val epText = when (episodeBadgeMode) {
                    EpisodeBadgeMode.OFF -> null
                    EpisodeBadgeMode.TOTAL -> item.totalEpisodes?.takeIf { it > 0 }?.let { "$it ep" }
                    EpisodeBadgeMode.RELEASED -> item.releasedEpisodes?.takeIf { it > 0 }?.let { "$it ep" }
                }
                // Compute the episode badge's effective position (compact grid clamps to top).
                val epEffectivePos = if (epText != null) {
                    if (displayMode == LibraryDisplayMode.COMPACT_GRID) {
                        if (episodeBadgePosition == BadgePosition.BOTTOM_START || episodeBadgePosition == BadgePosition.BOTTOM_END) {
                            BadgePosition.TOP_END
                        } else {
                            episodeBadgePosition
                        }
                    } else {
                        episodeBadgePosition
                    }
                } else null

                if (epText != null && epEffectivePos != null) {
                    Box(
                        modifier = Modifier
                            .align(epEffectivePos.toAlignment())
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = epText,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = RobotoFamily,
                        )
                    }
                }

                // ── Score badge — themed primary green background ──
                val score = item.score
                if (showScoreBadge && score != null) {
                    // In compact grid, force top positions
                    var scoreEffectivePos = if (displayMode == LibraryDisplayMode.COMPACT_GRID) {
                        if (scoreBadgePosition == BadgePosition.BOTTOM_START || scoreBadgePosition == BadgePosition.BOTTOM_END) {
                            BadgePosition.TOP_START
                        } else {
                            scoreBadgePosition
                        }
                    } else {
                        scoreBadgePosition
                    }
                    // Overlap fix: if the score badge would land on the SAME corner as the
                    // episode badge, shift it to the opposite corner on the same edge.
                    // Per user: "When I select right for both of them it overlaps them."
                    if (epEffectivePos != null && scoreEffectivePos == epEffectivePos) {
                        scoreEffectivePos = scoreEffectivePos.oppositeOnSameEdge()
                    }
                    Box(
                        modifier = Modifier
                            .align(scoreEffectivePos.toAlignment())
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${score.toInt()}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = RobotoFamily,
                        )
                    }
                }

                // ── Selection checkmark ──
                if (selectionMode) {
                    val checkBg = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.5f)
                    val checkBorderColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else Color.White.copy(alpha = 0.4f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(checkBg)
                            .border(2.dp, checkBorderColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }

            // ── Comfortable grid: title below cover ──
            if (displayMode == LibraryDisplayMode.COMFORTABLE_GRID) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = RobotoFamily,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // COVER_ONLY: no title below
        }
    }
}

/** Maps a [BadgePosition] to a Compose [Alignment]. Public so other Compose modules can use it. */
fun BadgePosition.toAlignment(): Alignment = when (this) {
    BadgePosition.TOP_START -> Alignment.TopStart
    BadgePosition.TOP_END -> Alignment.TopEnd
    BadgePosition.BOTTOM_START -> Alignment.BottomStart
    BadgePosition.BOTTOM_END -> Alignment.BottomEnd
}
