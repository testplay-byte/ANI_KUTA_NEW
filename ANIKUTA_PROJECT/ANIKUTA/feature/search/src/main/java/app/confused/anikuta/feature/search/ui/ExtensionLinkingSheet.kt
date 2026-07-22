@file:OptIn(ExperimentalMaterial3Api::class)

package app.confused.anikuta.feature.search.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.feature.search.viewmodel.ExtensionLinkingState
import app.confused.anikuta.feature.search.viewmodel.ExtensionLinkingViewModel
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.launch

/**
 * The extension→AniList linking sheet — shown when the auto-link fails.
 *
 * Design language rules:
 * - **`dragHandle = null`** (principle #2 — no drag handle on bottom-up menus).
 * - Partial height (principle #3) — `heightIn(min=320, max=640)`.
 *
 * Layout:
 * - Header card: extension anime cover + title + source name.
 * - "Not found on AniList" message (when no results).
 * - Manual search field (pre-filled with the extension title) + search button.
 * - AniList results list (tap to link + open detail).
 * - "Go without linking" button (bottom — opens extension-only detail).
 *
 * @param source the extension source the result came from.
 * @param sAnime the tapped extension anime.
 * @param anilistApi the AniList client (for the manual search).
 * @param linkStore persists the link.
 * @param onLinked called with the resolved AniList ID (open the detail page).
 * @param onGoWithoutLinking called with (source, sAnime) when the user taps
 *   "go without linking" (open the extension-only detail page).
 * @param onDismiss called when the sheet is dismissed (scrim tap / back).
 */
@Composable
fun ExtensionLinkingSheet(
    source: AnimeCatalogueSource,
    sAnime: SAnime,
    anilistApi: AniListApi,
    linkStore: ExtensionLinkStore,
    onLinked: (Int) -> Unit,
    onGoWithoutLinking: (AnimeCatalogueSource, SAnime) -> Unit,
    onDismiss: () -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val vm: ExtensionLinkingViewModel = viewModel(
        key = "ext_linking_${source.id}_${sAnime.url}",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ExtensionLinkingViewModel(
                source = source,
                sAnime = sAnime,
                anilistApi = anilistApi,
                linkStore = linkStore,
            ) as T
        },
    )

    val state by vm.state.collectAsState()

    // Auto-route on terminal states — close the sheet + dispatch to the caller.
    LaunchedEffect(state) {
        when (val s = state) {
            is ExtensionLinkingState.Linked -> onLinked(s.anilistId)
            is ExtensionLinkingState.GoWithoutLinking -> onGoWithoutLinking(s.source, s.sAnime)
            else -> { /* Loading or NeedsManualLink — show the sheet */ }
        }
    }

    // Don't render the sheet on terminal states (it's closing).
    if (state is ExtensionLinkingState.Linked || state is ExtensionLinkingState.GoWithoutLinking) {
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var manualQuery by rememberSaveable(sAnime.url) { mutableStateOf(sAnime.title) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null, // principle #2 — NO drag handle
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 640.dp)
                .padding(top = 20.dp),
        ) {
            // ── Header — extension anime cover + title + source ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumb = sAnime.thumbnail_url
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = sAnime.title,
                        modifier = Modifier
                            .width(80.dp)
                            .height(112.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sAnime.title,
                        fontFamily = RobotoFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "From: ${source.name}",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Not found on AniList automatically.\nPick a match below or search manually.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Manual search field ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = manualQuery,
                    onValueChange = { manualQuery = it },
                    label = { Text("Search AniList manually") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val q = manualQuery.trim()
                        if (q.isNotBlank()) {
                            keyboard?.hide()
                            vm.manualSearch(q)
                        }
                    },
                    enabled = state !is ExtensionLinkingState.Loading && manualQuery.isNotBlank(),
                ) {
                    if (state is ExtensionLinkingState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ── Body — loading / results / error / empty ──
            Box(modifier = Modifier.weight(1f)) {
                when (val s = state) {
                    is ExtensionLinkingState.Loading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Searching AniList for \"${sAnime.title}\"…",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is ExtensionLinkingState.NeedsManualLink -> {
                        if (s.error != null && s.results.isEmpty()) {
                            // Search itself failed.
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    text = "Search failed.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = s.error,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (s.results.isEmpty()) {
                            Text(
                                text = "No matches on AniList. Try a different title, or go without linking.",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                item {
                                    Text(
                                        text = "Did you mean one of these?",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                    )
                                }
                                items(s.results, key = { it.id }) { anime ->
                                    AniListResultRow(anime = anime) {
                                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                                            vm.selectManual(anime)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> { /* terminal states handled by LaunchedEffect above */ }
                }
            }

            // ── Bottom — "Go without linking" ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            vm.goWithoutLinking()
                        }
                    },
            ) {
                Text(
                    text = "Go without linking (extension only)",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}

/** One AniList result row in the linking sheet — thumbnail + title + meta. */
@Composable
private fun AniListResultRow(anime: AniListAnime, onTap: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover = anime.coverUrl
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = anime.displayTitle,
                    modifier = Modifier
                        .width(48.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.displayTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val bits = buildList {
                    anime.seasonYear?.let { add(it.toString()) }
                    anime.format?.let { add(it) }
                    anime.episodes?.let { add("$it eps") }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        text = bits.joinToString(" · "),
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
