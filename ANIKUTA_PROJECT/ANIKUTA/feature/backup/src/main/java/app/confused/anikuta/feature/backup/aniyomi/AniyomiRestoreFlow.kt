package app.confused.anikuta.feature.backup.aniyomi

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.confused.anikuta.core.backup.translation.AnilistResolution
import app.confused.anikuta.core.backup.translation.TranslationStats
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Lime red color for text/icons (NOT background — per owner: "red is for the text only"). */
private val LimeRed = Color(0xFFE5484D)
private val LimeRedContainer = Color(0xFFE5484D).copy(alpha = 0.15f)

/**
 * The full Aniyomi restore flow — a 6-step multi-screen wizard.
 *
 * The file URI is passed in directly (no double file picker).
 */
@Composable
fun AniyomiRestoreFlow(
    fileUri: Uri?,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    viewModel: AniyomiRestoreViewModel = androidx.compose.runtime.remember { 
        val koin = org.koin.core.context.GlobalContext.get()
        AniyomiRestoreViewModel(
            anilistApi = koin.get(),
            backupStorage = koin.get(),
            backupManager = koin.get(),
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // When a file URI is passed in, trigger detection immediately
    LaunchedEffect(fileUri) {
        if (fileUri != null && state is AniyomiRestoreState.Idle) {
            viewModel.onFileSelected(fileUri)
        }
    }

    when (val s = state) {
        is AniyomiRestoreState.Idle -> {
            // Show a loading indicator while detecting format
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is AniyomiRestoreState.FormatDetected -> FormatDetectionScreen(
            state = s,
            onContinue = { viewModel.onContinueFromDetection(s.fileUri) },
            onCancel = { viewModel.cancel(); onCancel() },
        )

        is AniyomiRestoreState.Processing -> ProcessingScreen(s.message)

        is AniyomiRestoreState.Summary -> SummaryScreen(
            state = s,
            onRestore = { viewModel.onRestoreFromSummary(s.fileUri) },
            onCancel = { viewModel.cancel(); onCancel() },
        )

        is AniyomiRestoreState.Linking -> LinkingScreen(
            state = s,
            onNext = { viewModel.onNextFromLinking() },
            onCancel = { viewModel.cancel(); onCancel() },
        )

        is AniyomiRestoreState.ManualLinking -> ManualLinkingScreen(
            state = s,
            onNext = { viewModel.onSkipManualLinking() },
            onCancel = { viewModel.cancel(); onCancel() },
        )

        is AniyomiRestoreState.Success -> SuccessScreen(
            state = s,
            onAutoClose = { viewModel.reset(); onComplete() },
        )

        is AniyomiRestoreState.Error -> ErrorScreen(
            message = s.message,
            onDismiss = { viewModel.cancel(); onCancel() },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 1: Format Detection Screen
// Red phase: "Now what are you trying to do?" + "Continue" button
// Green phase (only after click): "Just joking!" + "Continue" button
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FormatDetectionScreen(
    state: AniyomiRestoreState.FormatDetected,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    // revealed = false → red phase. revealed = true → green phase (only after click).
    var revealed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (!revealed) {
                // ── Red phase ──
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = LimeRed,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "This backup format is not supported.",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LimeRed,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Now what are you\ngoing to do now?",
                    fontFamily = RobotoFamily,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LimeRed,
                    textAlign = TextAlign.Center,
                    lineHeight = 42.sp,
                )
            } else if (state.isSupported) {
                // ── Green phase ──
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Just joking!",
                    fontFamily = RobotoFamily,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You can restore from this format too.",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.formatName,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            } else {
                // ── Actually unsupported ──
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = LimeRed,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "This format is not supported.",
                    fontFamily = RobotoFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LimeRed,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ONE button — "Continue" with different text per phase
            if (!revealed && state.isSupported) {
                // Red phase + supported: "Continue" (reveals the joke)
                Button(
                    onClick = { revealed = true },
                    modifier = Modifier.fillMaxWidth(0.7f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeRed,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        "Continue",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }
            } else if (revealed && state.isSupported) {
                // Green phase: "Continue" (proceeds to processing)
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(0.7f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Continue",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }
            } else {
                // Actually unsupported: "Go Back"
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(0.7f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeRed,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        "Go Back",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 2: Processing Screen (complex animation, 2 sec min)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProcessingScreen(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "ringRotation",
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breatheAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = breatheAlpha),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(120.dp).rotate(ringRotation),
                )
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(80.dp).scale(scale),
                )
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp).scale(scale),
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = message,
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Translating Aniyomi backup to ANIKUTA format…",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 3: Summary Screen (full-screen stats + status bar padding)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SummaryScreen(
    state: AniyomiRestoreState.Summary,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    val stats = state.stats
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = "Backup Summary",
                fontFamily = RobotoFamily,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryStatCard("Anime", stats.totalAnime, true, Modifier.weight(1f))
                SummaryStatCard("Categories", stats.totalCategories, false, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryStatCard("Episodes", stats.totalEpisodes, false, Modifier.weight(1f))
                SummaryStatCard("Resolved", stats.resolvedAnime, true, Modifier.weight(1f))
            }

            // Manga warning
            if (stats.totalManga > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = LimeRedContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, null, tint = LimeRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(
                                "${stats.totalManga} manga entries detected",
                                fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = LimeRed,
                            )
                            Text(
                                "Manga is not supported (anime-first app). Manga entries will be skipped.",
                                fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value.toString(),
                fontFamily = RobotoFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(label, fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 4: Linking Screen — STAYS until user clicks "Next"
// Shows covers + anime names. Clean stats bar.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LinkingScreen(
    state: AniyomiRestoreState.Linking,
    onNext: () -> Unit,
    onCancel: () -> Unit,
) {
    val progress = state.progress
    val allDone = state.allDone

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Title
            Text(
                text = if (allDone) "Linking Complete" else "Linking Anime",
                fontFamily = RobotoFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Clean stats bar — in a card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // Currently linking (top section)
            if (progress != null && !allDone) {
                Text(
                    "CURRENTLY LINKING",
                    fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.06.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                progress.resolution?.let { LinkingRow(it) }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (allDone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        "Anime successfully linked",
                        fontFamily = RobotoFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Linked anime list (scrollable, newest first)
            Text(
                "LINKED ANIME",
                fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.06.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val completed = state.resolutions.take(progress?.currentIndex ?: 0).reversed()
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(completed) { res -> LinkingRow(res) }
            }

            // Bottom buttons: Cancel + Next (Next only enabled when allDone)
            Spacer(modifier = Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onNext,
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = allDone,
                ) {
                    Text("Next", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            fontFamily = RobotoFamily, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color,
        )
        Text(label, fontFamily = RobotoFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinkingRow(res: AnilistResolution) {
    val isFailed = res is AnilistResolution.Failed
    val bgColor = if (isFailed) LimeRedContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(color = bgColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Left: cover image (from AniList or Aniyomi)
            val coverUrl = when (res) {
                is AnilistResolution.Resolved -> res.anilistAnime?.coverImage?.large
                is AnilistResolution.Failed -> null
            }
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(48.dp),
                ) {}
            }

            Spacer(modifier = Modifier.size(12.dp))

            // Middle: anime name + method/reason
            Column(Modifier.weight(1f)) {
                val title = when (res) {
                    is AnilistResolution.Resolved ->
                        res.anilistAnime?.title?.romaji ?: res.anilistAnime?.title?.english ?: "Unknown"
                    is AnilistResolution.Failed -> res.title
                }
                Text(
                    title,
                    fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (isFailed) LimeRed else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    when (res) {
                        is AnilistResolution.Resolved -> "via ${res.method}"
                        is AnilistResolution.Failed -> res.reason
                    },
                    fontFamily = RobotoFamily, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            // Right: link icon + AniList ID
            Icon(
                if (isFailed) Icons.Filled.Close else Icons.Filled.Link,
                null,
                tint = if (isFailed) LimeRed else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                when (res) {
                    is AnilistResolution.Resolved -> "#${res.anilistId}"
                    is AnilistResolution.Failed -> "—"
                },
                fontFamily = RobotoFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = if (isFailed) LimeRed else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 5: Manual Linking Screen — clickable rows + covers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ManualLinkingScreen(
    state: AniyomiRestoreState.ManualLinking,
    onNext: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Manual Linking",
                fontFamily = RobotoFamily, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${state.failedAnime.size} anime could not be automatically matched. Tap to search AniList manually, or skip.",
                fontFamily = RobotoFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.failedAnime) { failed ->
                    Surface(
                        color = LimeRedContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* TODO: open AniList search sheet */ },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Placeholder cover
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    failed.title,
                                    fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LimeRed,
                                )
                                Text(
                                    failed.reason,
                                    fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Filled.Search, null, tint = LimeRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
}

// ════════════════════════════════════════════════════════════════════════════
// Step 6: Success Screen — complete UI redo (modern, beautiful, animated)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SuccessScreen(
    state: AniyomiRestoreState.Success,
    onAutoClose: () -> Unit,
) {
    // Auto-close after 5 seconds
    LaunchedEffect(Unit) {
        delay(5000)
        onAutoClose()
    }

    // Success checkmark pop-in animation
    val infiniteTransition = rememberInfiniteTransition(label = "success")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            // Animated checkmark with pulsing ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                // Pulsing ring
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
                    shape = CircleShape,
                    modifier = Modifier.size(120.dp),
                ) {}
                // Inner circle
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp).scale(scale),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Restore Successful",
                fontFamily = RobotoFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stats in a highlighted card
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${state.stats.resolvedAnime}",
                        fontFamily = RobotoFamily, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "anime successfully restored",
                        fontFamily = RobotoFamily, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.skippedCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = LimeRedContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "Except ${state.skippedCount}",
                                fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                color = LimeRed,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Redirecting to library…",
                fontFamily = RobotoFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Error Screen
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorScreen(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.Error, null, tint = LimeRed, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Error", fontFamily = RobotoFamily, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = LimeRed)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, fontFamily = RobotoFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onDismiss) { Text("Dismiss", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) }
        }
    }
}
