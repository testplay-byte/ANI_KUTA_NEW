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
import app.confused.anikuta.core.common.model.EpisodeBadgeMode
import app.confused.anikuta.core.common.model.LibraryDisplayMode
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * A grid cell showing an anime cover + optional title.
 *
 * Display modes:
 *  - [LibraryDisplayMode.COMPACT_GRID] — title overlaid on cover (bottom gradient).
 *  - [LibraryDisplayMode.COMFORTABLE_GRID] — title below cover (2 lines).
 *  - [LibraryDisplayMode.COVER_ONLY] — no title, just the cover.
 *
 * Badges (episode count + score) are compact pills with reduced height
 * per user feedback ("reduce by half").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridCard(
    item: Anime,
    selected: Boolean,
    selectionMode: Boolean,
    displayMode: LibraryDisplayMode,
    episodeBadgeMode: EpisodeBadgeMode,
    showScoreBadge: Boolean,
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

                // ── Episode badge — compact pill (reduced height) ──
                val epText = when (episodeBadgeMode) {
                    EpisodeBadgeMode.OFF -> null
                    EpisodeBadgeMode.TOTAL -> item.totalEpisodes?.takeIf { it > 0 }?.let { "$it ep" }
                    EpisodeBadgeMode.RELEASED -> item.releasedEpisodes?.takeIf { it > 0 }?.let { "$it ep" }
                }
                if (epText != null) {
                    Text(
                        text = epText,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoFamily,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }

                // ── Score badge — compact pill (reduced height) ──
                val score = item.score
                if (showScoreBadge && score != null) {
                    Text(
                        text = "${score.toInt()}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoFamily,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
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
