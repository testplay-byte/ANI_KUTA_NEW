package com.testplaybyte.animeapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.*
import com.testplaybyte.animeapp.model.*
import com.testplaybyte.animeapp.data.SettingsRepository
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * AnimeCard — reusable result tile with cover image, title, score, meta.
 * Cover border radius is driven by the poster style setting.
 */
@Composable
fun AnimeCard(
    anime: Anime,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    context: String = "default",
) {
    val ctx = LocalContext.current
    val settingsRepo = remember { SettingsRepository(ctx) }
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    val cornerRadius = when (settings.posterStyle) {
        PosterStyle.ROUNDED -> 12.dp
        PosterStyle.SOFT -> 6.dp
        PosterStyle.SHARP -> 2.dp
    }

    Column(
        modifier = modifier
            .clickable { onClick(anime.id) }
            .padding(horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            // Score badge
            if (anime.averageScore != null) {
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = "★ ${anime.scoreFormatted}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = anime.displayTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = if (settings.singleLineTitles) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        val metaParts = mutableListOf<String>()
        if (anime.format != null) metaParts.add(anime.format)
        if (anime.episodes != null) metaParts.add("${anime.episodes} ep")
        else if (anime.seasonYear != null) metaParts.add(anime.seasonYear.toString())
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" · "),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
