package app.confused.anikuta.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.search.viewmodel.ExtensionRow
import app.confused.anikuta.feature.search.viewmodel.ExtensionRowKind
import app.confused.anikuta.feature.search.viewmodel.SearchResult
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * The extension default view — Popular + Latest rows from the selected source.
 *
 * Shown when source=EXTENSION and the query is blank. Each row is a horizontal
 * `LazyRow` of extension result cards in a dedicated `surfaceVariant` card.
 *
 * Per Q3: "if the top extension... gets the popular anime from it and it gets
 * the latest anime from it and shows that, then in a row properly". So we show
 * the Popular row + the Latest row, each in its own card with a section header.
 *
 * Tapping any card → [onResultTap] (the caller starts the linking flow).
 *
 * @param loading shows a spinner while the rows are loading.
 * @param error shows an error message if the source failed entirely.
 * @param rows the loaded rows (Popular + Latest), each with its own error field.
 * @param onResultTap called when an extension card is tapped.
 */
@Composable
fun ExtensionResultsView(
    loading: Boolean,
    error: String?,
    rows: List<ExtensionRow>,
    onResultTap: (SearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (loading && rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp),
                )
            }
            return@Column
        }
        if (error != null && rows.isEmpty()) {
            Text(
                text = error,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        if (rows.isEmpty() && !loading) {
            Text(
                text = "No extension source selected.",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        rows.forEach { row ->
            ExtensionRowCard(row = row, onResultTap = onResultTap)
        }
    }
}

/**
 * One row card — section header + horizontal LazyRow of extension cards.
 *
 * If the row's source call failed, shows the error inline instead of cards.
 */
@Composable
private fun ExtensionRowCard(
    row: ExtensionRow,
    onResultTap: (SearchResult) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            // Section header — kind label (Popular / Latest) + source name + count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.kind.label,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (row.error == null && row.animes.isNotEmpty()) {
                    Text(
                        text = "${row.animes.size} · ${row.sourceName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (row.error != null) {
                Text(
                    text = "${row.sourceName} failed: ${row.error}",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            } else if (row.animes.isEmpty()) {
                Text(
                    text = "No ${row.kind.label.lowercase()} anime from ${row.sourceName}.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(row.animes, key = { it.url }) { sAnime ->
                        ExtensionRowCard(sAnime = sAnime, sourceName = row.sourceName) {
                            onResultTap(
                                SearchResult.Extension(
                                    source = row.source,
                                    sAnime = sAnime,
                                    sourceName = row.sourceName,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One extension result card in a horizontal row — narrower than the grid card
 * (fixed width 112dp) so multiple fit on screen.
 */
@Composable
private fun ExtensionRowCard(
    sAnime: SAnime,
    sourceName: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = sAnime.thumbnail_url,
                contentDescription = sAnime.title,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = sAnime.title,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        Text(
            text = sourceName,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
