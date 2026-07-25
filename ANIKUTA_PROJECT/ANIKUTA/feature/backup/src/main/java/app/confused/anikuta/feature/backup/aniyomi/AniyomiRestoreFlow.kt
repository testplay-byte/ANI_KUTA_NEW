package app.confused.anikuta.feature.backup.aniyomi

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.backup.translation.AnilistResolution
import app.confused.anikuta.core.backup.translation.TranslationStats
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lime red color for text/icons. */
private val LimeRed = Color(0xFFE5484D)
private val LimeRedContainer = Color(0xFFE5484D).copy(alpha = 0.15f)

/**
 * The full Aniyomi restore flow — a 6-step multi-screen wizard.
 */
@Composable
fun AniyomiRestoreFlow(
    fileUri: Uri?,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
) {
    val koin = org.koin.core.context.GlobalContext.get()
    val viewModel: AniyomiRestoreViewModel = remember {
        AniyomiRestoreViewModel(
            anilistApi = koin.get(),
            backupStorage = koin.get(),
            backupManager = koin.get(),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(fileUri) {
        if (fileUri != null && state is AniyomiRestoreState.Idle) {
            viewModel.onFileSelected(fileUri)
        }
    }

    when (val s = state) {
        is AniyomiRestoreState.Idle -> {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        }
        is AniyomiRestoreState.FormatDetected -> FormatDetectionScreen(s,
            onContinue = { viewModel.onContinueFromDetection(s.fileUri) },
            onCancel = { viewModel.cancel(); onCancel() },
        )
        is AniyomiRestoreState.Processing -> ProcessingScreen(s.message)
        is AniyomiRestoreState.Summary -> SummaryScreen(s,
            onRestore = { viewModel.onRestoreFromSummary(s.fileUri) },
            onCancel = { viewModel.cancel(); onCancel() },
        )
        is AniyomiRestoreState.Linking -> LinkingScreen(s,
            onNext = { viewModel.onNextFromLinking() },
            onCancel = { viewModel.cancel(); onCancel() },
            onMarkWrong = { resolved -> viewModel.markAsWrong(resolved) },
        )
        is AniyomiRestoreState.ManualLinking -> ManualLinkingScreen(s,
            onNext = { viewModel.onSkipManualLinking() },
            onCancel = { viewModel.cancel(); onCancel() },
            viewModel = viewModel,
        )
        is AniyomiRestoreState.Success -> SuccessScreen(s,
            onAutoClose = { viewModel.reset(); onComplete() },
        )
        is AniyomiRestoreState.Error -> ErrorScreen(s.message,
            onDismiss = { viewModel.cancel(); onCancel() },
        )
    }
}

// ═══ Step 1: Format Detection ═══

@Composable
private fun FormatDetectionScreen(
    state: AniyomiRestoreState.FormatDetected,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    val infT = rememberInfiniteTransition(label = "fd")
    val iconScale by infT.animateFloat(0.85f, 1.15f,
        infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), "is")
    val iconAlpha by infT.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), "ia")

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            if (!revealed) {
                // ── Red phase ──
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Surface(color = LimeRed.copy(alpha = iconAlpha * 0.2f), shape = CircleShape, modifier = Modifier.size(100.dp)) {}
                    Icon(Icons.Filled.Error, null, tint = LimeRed, modifier = Modifier.size(64.dp).scale(iconScale))
                }
                Spacer(Modifier.height(32.dp))
                Text("This backup format\nis not supported.",
                    fontFamily = RobotoFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                    color = LimeRed, textAlign = TextAlign.Center, lineHeight = 38.sp)
            } else if (state.isSupported) {
                // ── Green phase — "Don't worry" (big) + spacing + "You can" (smaller) ──
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(100.dp)) {}
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp).scale(iconScale))
                }
                Spacer(Modifier.height(32.dp))
                Text("Don't worry",
                    fontFamily = RobotoFamily, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("You can restore from this format too.",
                    fontFamily = RobotoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                Icon(Icons.Filled.Close, null, tint = LimeRed, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text("This format is not supported.",
                    fontFamily = RobotoFamily, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    color = LimeRed, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(48.dp))
            if (!revealed && state.isSupported) {
                Button({ revealed = true }, Modifier.fillMaxWidth(0.7f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimeRed, contentColor = Color.White)) {
                    Text("Continue", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            } else if (revealed && state.isSupported) {
                Button(onContinue, Modifier.fillMaxWidth(0.7f), shape = RoundedCornerShape(12.dp)) {
                    Text("Continue", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            } else {
                Button(onCancel, Modifier.fillMaxWidth(0.7f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimeRed, contentColor = Color.White)) {
                    Text("Go Back", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ═══ Step 2: Processing ═══

@Composable
private fun ProcessingScreen(message: String) {
    val infT = rememberInfiniteTransition(label = "p")
    val rot by infT.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), "r")
    val scale by infT.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), "s")
    val alpha by infT.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), "a")

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary.copy(alpha = alpha), strokeWidth = 4.dp,
                    modifier = Modifier.size(120.dp).rotate(rot))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), strokeWidth = 2.dp,
                    modifier = Modifier.size(80.dp).scale(scale))
                Icon(Icons.Filled.CloudUpload, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp).scale(scale))
            }
            Spacer(Modifier.height(32.dp))
            Text(message, fontFamily = RobotoFamily, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("Translating Aniyomi backup to ANIKUTA format…", fontFamily = RobotoFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══ Step 3: Summary ═══

@Composable
private fun SummaryScreen(state: AniyomiRestoreState.Summary, onRestore: () -> Unit, onCancel: () -> Unit) {
    val stats = state.stats
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Backup Summary", fontFamily = RobotoFamily, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard("Anime", stats.totalAnime, true, Modifier.weight(1f))
                SummaryStatCard("Categories", stats.totalCategories, false, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard("Episodes", stats.totalEpisodes, false, Modifier.weight(1f))
                SummaryStatCard("Resolved", stats.resolvedAnime, true, Modifier.weight(1f))
            }
            if (stats.totalManga > 0) {
                Spacer(Modifier.height(16.dp))
                Surface(color = LimeRedContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, null, tint = LimeRed, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text("${stats.totalManga} manga entries detected", fontFamily = RobotoFamily,
                                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = LimeRed)
                            Text("Manga is not supported (anime-first app). Manga entries will be skipped.",
                                fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                Button(onRestore, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Restore", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun SummaryStatCard(label: String, value: Int, highlight: Boolean, modifier: Modifier) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp), modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontFamily = RobotoFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(label, fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══ Step 4: Linking — STAYS until Next. Shows BOTH names. ═══

@Composable
private fun LinkingScreen(
    state: AniyomiRestoreState.Linking,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onMarkWrong: (AnilistResolution.Resolved) -> Unit = {},
) {
    val progress = state.progress
    val allDone = state.allDone

    // State for the "Is this wrong?" confirmation dialog
    var wrongTarget by remember { mutableStateOf<AnilistResolution.Resolved?>(null) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(if (allDone) "Linking Complete" else "Linking Anime",
                fontFamily = RobotoFamily, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            // Stats in a card
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    val total = progress?.total ?: state.resolutions.size
                    val completed = progress?.currentIndex ?: 0
                    val success = progress?.resolved ?: 0
                    val failed = progress?.failed ?: 0
                    StatItem("Total", total, MaterialTheme.colorScheme.onSurface)
                    StatItem("Completed", completed, MaterialTheme.colorScheme.primary)
                    StatItem("Success", success, MaterialTheme.colorScheme.primary)
                    StatItem("Failed", failed, LimeRed)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (progress != null && !allDone) {
                Text("CURRENTLY LINKING", fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.06.sp)
                Spacer(Modifier.height(8.dp))
                progress.resolution?.let { LinkingRow(it) }
                Spacer(Modifier.height(16.dp))
            } else if (allDone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Anime successfully linked", fontFamily = RobotoFamily, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text("Tap a linked anime if the match is wrong.", fontFamily = RobotoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }

            Text("LINKED ANIME", fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.06.sp)
            Spacer(Modifier.height(8.dp))

            val completed = state.resolutions.take(progress?.currentIndex ?: 0).reversed()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(completed) { res ->
                    LinkingRow(res, onMarkWrong = { resolved -> wrongTarget = resolved })
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                Button(onNext, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = allDone) {
                    Text("Next", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    // "Is this wrong?" confirmation dialog
    if (wrongTarget != null) {
        val resolved = wrongTarget!!
        AlertDialog(
            onDismissRequest = { wrongTarget = null },
            title = { Text("Is this wrong?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text("Backup: ${resolved.anilistAnime?.title?.romaji ?: resolved.anilistAnime?.title?.english ?: "Unknown"}",
                        fontFamily = RobotoFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Matched to: ${resolved.anilistAnime?.title?.romaji ?: resolved.anilistAnime?.title?.english ?: "Unknown"}",
                        fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Mark this as 'no match'? You can manually link it later.",
                        fontFamily = RobotoFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button({
                    onMarkWrong(resolved)
                    wrongTarget = null
                }, colors = ButtonDefaults.buttonColors(containerColor = LimeRed, contentColor = Color.White)) {
                    Text("Yes, it's wrong", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton({ wrongTarget = null }) {
                    Text("No, it's correct", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
        )
    }
}

@Composable
private fun StatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontFamily = RobotoFamily, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontFamily = RobotoFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A linking row showing BOTH the backup name (left) and the AniList name (right)
 * with a clear visual separation (link icon in the middle).
 * Tapping a linked row opens an "Is this wrong?" dialog to mark it as no match.
 */
@Composable
private fun LinkingRow(res: AnilistResolution, onMarkWrong: ((AnilistResolution.Resolved) -> Unit)? = null) {
    val isFailed = res is AnilistResolution.Failed
    val bgColor = if (isFailed) LimeRedContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    // Extract both names
    val backupName = when (res) {
        is AnilistResolution.Resolved -> res.anilistAnime?.title?.romaji ?: res.anilistAnime?.title?.english ?: "Unknown"
        is AnilistResolution.Failed -> res.title
    }
    val anilistName = when (res) {
        is AnilistResolution.Resolved -> res.anilistAnime?.title?.romaji ?: res.anilistAnime?.title?.english ?: "Unknown"
        is AnilistResolution.Failed -> null
    }
    val coverUrl = when (res) {
        is AnilistResolution.Resolved -> res.anilistAnime?.coverImage?.large
        is AnilistResolution.Failed -> null
    }

    Surface(
        color = bgColor, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().then(
            if (!isFailed && onMarkWrong != null) Modifier.clickable { onMarkWrong(res as AnilistResolution.Resolved) }
            else Modifier
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Left: cover image
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp)) {}
            }
            Spacer(Modifier.size(12.dp))

            // Left: backup name (the name from the Aniyomi backup)
            Column(Modifier.weight(1f)) {
                Text("Backup", fontFamily = RobotoFamily, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.06.sp)
                Text(backupName, fontFamily = RobotoFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (isFailed) LimeRed else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }

            // Middle: link icon
            Icon(if (isFailed) Icons.Filled.Close else Icons.Filled.Link, null,
                tint = if (isFailed) LimeRed else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))

            // Right: AniList name only (NO AniList ID — the name is enough)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                if (anilistName != null) {
                    Text("AniList", fontFamily = RobotoFamily, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.06.sp, textAlign = TextAlign.End)
                    Text(anilistName, fontFamily = RobotoFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1, textAlign = TextAlign.End)
                } else {
                    Text("No match", fontFamily = RobotoFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = LimeRed, textAlign = TextAlign.End)
                }
            }
        }
    }
}

// ═══ Step 5: Manual Linking — with actual search functionality ═══

@Composable
private fun ManualLinkingScreen(
    state: AniyomiRestoreState.ManualLinking,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AniyomiRestoreViewModel,
) {
    // State for the search sheet
    var searchingForAnime by remember { mutableStateOf<AnilistResolution.Failed?>(null) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Manual Linking", fontFamily = RobotoFamily, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("${state.failedAnime.size} anime could not be automatically matched. Tap to search AniList manually, or skip.",
                fontFamily = RobotoFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.failedAnime) { failed ->
                    Surface(
                        color = LimeRedContainer, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { searchingForAnime = failed },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(failed.title, fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LimeRed)
                                Text(failed.reason, fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.Search, null, tint = LimeRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                Button(onNext, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Skip & Continue", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    // Search sheet overlay
    if (searchingForAnime != null) {
        ManualSearchSheet(
            failed = searchingForAnime!!,
            viewModel = viewModel,
            onDismiss = { searchingForAnime = null },
            onLinked = { searchingForAnime = null },
        )
    }
}

/**
 * Full-screen search sheet for manually searching AniList and linking an anime.
 * Only searches when the user clicks the "Search" button (no auto-search).
 */
@Composable
private fun ManualSearchSheet(
    failed: AnilistResolution.Failed,
    viewModel: AniyomiRestoreViewModel,
    onDismiss: () -> Unit,
    onLinked: () -> Unit,
) {
    var query by remember { mutableStateOf(failed.title) }
    var results by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Link Anime", fontFamily = RobotoFamily, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Original: ${failed.title}", fontFamily = RobotoFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onDismiss) { Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.height(20.dp))

            // Search field + button
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search AniList", fontFamily = RobotoFamily) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Button(
                    onClick = {
                        if (query.isNotBlank()) {
                            scope.launch {
                                searching = true
                                hasSearched = true
                                results = viewModel.searchAniList(query)
                                searching = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Search", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(20.dp))

            // Results
            if (searching) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (hasSearched) {
                Text("${results.size} results", fontFamily = RobotoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { anime ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.manuallyLink(failed, anime)
                                onLinked()
                            },
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = anime.coverImage?.large,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(anime.title.romaji ?: anime.title.english ?: "Unknown",
                                        fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                                    if (anime.averageScore != null) {
                                        Text("Score: ${anime.averageScore}", fontFamily = RobotoFamily, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            } else {
                // Initial state — prompt the user to search
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Enter a title and tap Search", fontFamily = RobotoFamily, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ═══ Step 6: Success — with Continue button ═══

@Composable
private fun SuccessScreen(state: AniyomiRestoreState.Success, onAutoClose: () -> Unit) {
    // Auto-close after 5 seconds
    LaunchedEffect(Unit) {
        delay(5000)
        onAutoClose()
    }

    val infT = rememberInfiniteTransition(label = "s")
    val scale by infT.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), "s")
    val ringAlpha by infT.animateFloat(0.2f, 0.6f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), "r")

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            // Animated checkmark
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha), shape = CircleShape, modifier = Modifier.size(120.dp)) {}
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(80.dp).scale(scale)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Restore Successful", fontFamily = RobotoFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))

            // Stats card
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.stats.resolvedAnime}", fontFamily = RobotoFamily, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("anime successfully restored", fontFamily = RobotoFamily, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.skippedCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Surface(color = LimeRedContainer, shape = RoundedCornerShape(8.dp)) {
                            Text("Except ${state.skippedCount}", fontFamily = RobotoFamily, fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold, color = LimeRed,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))

            // Continue button (in addition to auto-close)
            Button(onAutoClose, Modifier.fillMaxWidth(0.7f), shape = RoundedCornerShape(12.dp)) {
                Text("Continue", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Auto-redirecting in 5 seconds…", fontFamily = RobotoFamily, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══ Error Screen ═══

@Composable
private fun ErrorScreen(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.Error, null, tint = LimeRed, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Error", fontFamily = RobotoFamily, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = LimeRed)
            Spacer(Modifier.height(8.dp))
            Text(message, fontFamily = RobotoFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onDismiss) { Text("Dismiss", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) }
        }
    }
}
