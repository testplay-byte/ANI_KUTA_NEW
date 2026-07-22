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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Anime
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * A grid cell showing an anime cover + title.
 *
 * Used inside the library's compact/comfortable grid layouts. Supports
 * long-press to enter selection mode and tap-to-toggle selection while in
 * selection mode.
 *
 * Per the prototype's `LibraryGridCard`:
 * - Cover: 2:3 aspect, 12dp rounded.
 * - Optional episode pill (top-end), score pill (bottom-start).
 * - Optional selection checkmark (top-end) shown in selection mode.
 * - Whole card fades to 0.7 alpha when selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridCard(
    item: Anime,
    selected: Boolean,
    selectionMode: Boolean,
    showEpisodeBadge: Boolean,
    showScoreBadge: Boolean,
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

                // Episode pill — top-end.
                val epCount = item.totalEpisodes
                if (showEpisodeBadge && epCount != null && epCount > 0) {
                    Text(
                        text = "$epCount ep",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoFamily,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                // Score pill — bottom-start.
                val score = item.score
                if (showScoreBadge && score != null) {
                    Text(
                        text = "\u2605 ${score.toInt()}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoFamily,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                // Selection checkmark — top-end (only in selection mode).
                if (selectionMode) {
                    val checkBg = if (selected) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.5f)
                    val checkBorderColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else Color.White.copy(alpha = 0.4f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
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
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

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
    }
}
