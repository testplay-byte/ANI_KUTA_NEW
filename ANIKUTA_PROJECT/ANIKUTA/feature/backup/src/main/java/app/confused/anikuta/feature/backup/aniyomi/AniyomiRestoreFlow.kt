package app.confused.anikuta.feature.backup.aniyomi

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.backup.translation.AniListResolution
import app.confused.anikuta.core.backup.translation.TranslationStats
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/** Lime red color for the "unsupported format" joke screen. */
private val LimeRed = Color(0xFFE5484D)
private val LimeRedDark = Color(0xFF8C1D18)
private val LimeRedContainer = Color(0xFF8C1D18).copy(alpha = 0.3f)

/**
 * The full Aniyomi restore flow — a 6-step multi-screen wizard.
 *
 * Steps:
 * 1. Format detection (red joke → green reveal)
 * 2. Processing animation (5 sec min)
 * 3. Summary (stats + Restore/Cancel)
 * 4. AniList live linking (animated)
 * 5. Manual linking (for failures)
 * 6. Success (auto-close 5s → library)
 *
 * Modular: each step is a separate composable. The flow is driven by
 * [AniyomiRestoreViewModel.state].
 */
@Composable
fun AniyomiRestoreFlow(
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    viewModel: AniyomiRestoreViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is AniyomiRestoreState.Idle -> {
            // Show file picker
            val filePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) viewModel.onFileSelected(uri)
            }
            LaunchedEffect(Unit) { filePicker.launch(arrayOf("*/*")) }
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
            onComplete = { /* linking finishes automatically → transitions to next state */ },
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
// Step 1: Format Detection Screen (red → green joke)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FormatDetectionScreen(
    state: AniyomiRestoreState.FormatDetected,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    // Animate the color shift from red → green when the format is supported
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500) // Show red for 1.5s, then reveal
        revealed = true
    }

    // Animate background color
    val bgColor by animateFloatAsState(
        targetValue = if (revealed && state.isSupported) 1f else 0f,
        animationSpec = tween(800),
        label = "bgColorShift",
    )
    // Lerp from LimeRed to the normal background
    val bg = lerpColor(LimeRed, MaterialTheme.colorScheme.background, bgColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (!revealed) {
                // ── Red phase: "unsupported" joke ──
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This backup format is not supported.",
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Now what are you trying to do?",
                    fontFamily = RobotoFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            } else if (state.isSupported) {
                // ── Green phase: "just joking" ──
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Just joking!",
                    fontFamily = RobotoFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can restore from this format too.\n${state.formatName}",
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                // ── Still red: actually unsupported ──
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This format is not supported.",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Button changes based on state
            if (!revealed) {
                // Red phase: "Go Back" button
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                ) {
                    Text("Go Back", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            } else if (state.isSupported) {
                // Green phase: "Continue" button
                Button(onClick = onContinue) {
                    Text("Continue", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                // Unsupported: "Go Back" button
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = LimeRed)) {
                    Text("Go Back", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** Linear interpolation between two colors. */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction,
    )
}

// ════════════════════════════════════════════════════════════════════════════
// Step 2: Processing Screen (complex animation, 5 sec min)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProcessingScreen(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")

    // Outer ring rotation
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "ringRotation",
    )
    // Pulsing scale
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    // Breathing alpha
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breatheAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Complex multi-layer animation
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                // Outer rotating ring
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = breatheAlpha),
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(120.dp)
                        .rotate(ringRotation),
                )
                // Middle pulsing ring
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale),
                )
                // Inner icon
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(36.dp)
                        .scale(scale),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = message,
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Translating Aniyomi backup to ANIKUTA format…",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 3: Summary Screen (full-screen stats)
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
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = "Backup Summary",
                fontFamily = RobotoFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryStatCard(
                    label = "Anime",
                    value = stats.totalAnime,
                    highlight = true,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatCard(
                    label = "Categories",
                    value = stats.totalCategories,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryStatCard(
                    label = "Episodes",
                    value = stats.totalEpisodes,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatCard(
                    label = "Resolved",
                    value = stats.resolvedAnime,
                    highlight = true,
                    modifier = Modifier.weight(1f),
                )
            }

            // Manga warning
            if (stats.totalManga > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = LimeRedContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = LimeRed,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(
                                text = "${stats.totalManga} manga entries detected",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LimeRed,
                            )
                            Text(
                                text = "Manga is not supported (anime-first app). Manga entries will be skipped.",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Restore", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: Int,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (highlight) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                fontFamily = RobotoFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 4: Linking Screen (live AniList linking with stats)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LinkingScreen(
    state: AniyomiRestoreState.Linking,
    onComplete: () -> Unit,
) {
    val progress = state.progress
    val allDone = progress != null && progress.currentIndex >= progress.total

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Top stats bar
            if (progress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LinkStatItem("Remaining", progress.total - progress.currentIndex, MaterialTheme.colorScheme.onSurfaceVariant)
                    LinkStatItem("Completed", progress.currentIndex, MaterialTheme.colorScheme.primary)
                    LinkStatItem("Success", progress.resolved, MaterialTheme.colorScheme.primary)
                    LinkStatItem("Failed", progress.failed, LimeRed)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top section: currently linking anime
            if (progress != null && !allDone) {
                Text(
                    text = "CURRENTLY LINKING",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                progress.resolution?.let { res ->
                    LinkingRow(res, isCurrent = true)
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (allDone) {
                // All done — show success header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Anime successfully linked",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom section: linked anime list (scrollable, newest first)
            Text(
                text = "LINKED ANIME",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val completed = state.resolutions.take(progress?.currentIndex ?: 0).reversed()
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(completed) { res ->
                    LinkingRow(res, isCurrent = false)
                }
            }
        }
    }
}

@Composable
private fun LinkStatItem(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            fontFamily = RobotoFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
        )
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkingRow(res: AniListResolution, isCurrent: Boolean) {
    val isFailed = res is AnilistResolution.Failed
    val bgColor = if (isFailed) {
        LimeRedContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Aniyomi anime (original)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (res) {
                        is AnilistResolution.Resolved -> res.anilistAnime?.title?.romaji ?: "Unknown"
                        is AnilistResolution.Failed -> res.title
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFailed) LimeRed else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when (res) {
                        is AnilistResolution.Resolved -> "via ${res.method}"
                        is AnilistResolution.Failed -> res.reason
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Middle: link icon
            Icon(
                imageVector = if (isFailed) Icons.Filled.Close else Icons.Filled.Link,
                contentDescription = null,
                tint = if (isFailed) LimeRed else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.size(12.dp))

            // Right: AniList match
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = when (res) {
                        is AnilistResolution.Resolved -> "AniList #${res.anilistId}"
                        is AnilistResolution.Failed -> "No match"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFailed) LimeRed else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 5: Manual Linking Screen (for failed matches)
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
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = "Manual Linking",
                fontFamily = RobotoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${state.failedAnime.size} anime could not be automatically matched to AniList. You can link them manually or skip them.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.failedAnime) { failed ->
                    Surface(
                        color = LimeRedContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = LimeRed,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = failed.title,
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LimeRed,
                                )
                                Text(
                                    text = failed.reason,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Skip & Continue", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Step 6: Success Screen (auto-close 5s)
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

    // Success animation
    val infiniteTransition = rememberInfiniteTransition(label = "success")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Restore Successful",
                fontFamily = RobotoFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${state.stats.resolvedAnime} anime successfully backed up",
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.skippedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Except ${state.skippedCount}",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LimeRed,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Redirecting to library…",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
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
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = LimeRed,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LimeRed,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDismiss) {
                Text("Dismiss", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
