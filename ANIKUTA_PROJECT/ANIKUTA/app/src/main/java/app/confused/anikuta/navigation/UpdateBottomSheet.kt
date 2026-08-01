@file:OptIn(ExperimentalMaterial3Api::class)

package app.confused.anikuta.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.appupdate.DownloadProgress
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The update bottom sheet — shown when [AppController.showUpdateDialog] is true.
 *
 * # Design (per user spec)
 *
 * A bottom-up sheet (per DESIGN_LANGUAGE §2 — `dragHandle = null`) with:
 * - **Bold, theme-colored heading** at the top: "New Update Available"
 * - **Version + release date** just below the heading
 * - **Dedicated changelog section** (scrollable) with a "What's New" sub-heading
 * - **Download + Cancel on the SAME row** at the bottom:
 *   - Left: Download button (wider) — transforms into an in-button progress bar
 *   - Right: X (cancel) button — closes the sheet
 *
 * # Download button states
 *
 * - **Not downloaded**: "Download" with a download icon
 * - **Downloading**: The button itself becomes a progress bar — the fill color
 *   moves left-to-right, and "Downloading X%" text is shown inside
 * - **Downloaded**: "Install Update" with an install icon
 * - **Error**: "Retry" with error text above
 *
 * The button does NOT disappear or get replaced by a separate progress bar —
 * it transforms in place (per user spec).
 *
 * @param appController the app controller (provides update state + callbacks).
 */
@Composable
fun UpdateBottomSheet(appController: AppController) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val updateInfo by appController.updateManager.latestUpdate.collectAsState()
    val downloadProgress by appController.updateManager.downloadProgress.collectAsState()

    val info = updateInfo ?: return

    val context = androidx.compose.ui.platform.LocalContext.current

    // Check if this version is already downloaded (file exists on disk)
    val isAlreadyDownloaded = appController.updateManager.isLatestUpdateDownloaded()

    ModalBottomSheet(
        onDismissRequest = { appController.dismissUpdateSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 620.dp)
                .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            // ── Heading (bold + theme-colored) ──
            Text(
                text = "New Update Available",
                fontFamily = RobotoFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = (-0.5).sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Version + release date ──
            val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "v${info.versionName}",
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (info.releaseDate > 0) {
                    Text(
                        text = "· ${dateFormatter.format(Date(info.releaseDate))}",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── Changelog section ──
            Text(
                text = "What's New",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    ClickableChangelogText(
                        text = info.changelog,
                        onLinkClick = { url ->
                            // Open the URL in the system browser
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.w("UpdateSheet", "Failed to open URL: $url", e)
                            }
                        },
                    )
                    // "View on GitHub" link — always present so the user can
                    // see the full release page even if the changelog has no URLs.
                    Spacer(modifier = Modifier.height(10.dp))
                    val releaseUrl = "https://github.com/testplay-byte/ANI_KUTA_NEW/releases/tag/v${info.versionName}"
                    Text(
                        text = "View full release on GitHub →",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(releaseUrl)).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.w("UpdateSheet", "Failed to open release URL", e)
                                }
                            },
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ── Bottom row: Download (left) + Cancel/X (right) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Download button (takes most of the width) — transforms into progress bar
                DownloadButtonWithProgress(
                    progress = downloadProgress,
                    isAlreadyDownloaded = isAlreadyDownloaded,
                    apkSizeBytes = info.apkSizeBytes,
                    onDownload = { appController.updateManager.startDownload() },
                    onInstall = {
                        val path = appController.updateManager.getDownloadedApkPath()
                        appController.updateManager.installDownloadedApk(path)
                    },
                    modifier = Modifier.weight(1f),
                )

                // X (cancel) button — square-ish, icon only
                IconButton(
                    onClick = { appController.dismissUpdateSheet() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The download button that transforms into a progress bar in-place.
 *
 * States:
 * - `progress == null && !isAlreadyDownloaded` → "Download" button
 * - `progress == null && isAlreadyDownloaded` → "Install Update" button
 * - `progress.isComplete` → "Install Update" button
 * - `progress.error != null` → "Retry" button
 * - `progress` (downloading) → button with fill animation + "Downloading X%"
 *
 * The fill animation: the button background fills left-to-right proportionally
 * to the download percentage, giving a visual progress indicator inside the
 * button itself.
 */
@Composable
private fun DownloadButtonWithProgress(
    progress: DownloadProgress?,
    isAlreadyDownloaded: Boolean,
    apkSizeBytes: Long?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDownloading = progress != null && !progress.isComplete && progress.error == null
    val isComplete = progress != null && progress.isComplete && progress.error == null
    val hasError = progress != null && progress.error != null
    val showInstall = isAlreadyDownloaded || isComplete

    when {
        hasError -> {
            // Error state — show retry
            Button(
                onClick = onDownload,
                modifier = modifier.height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = "Retry",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            }
        }
        showInstall -> {
            // Already downloaded or download complete → show Install
            Button(
                onClick = onInstall,
                modifier = modifier.height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.InstallMobile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Install Update",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            }
        }
        isDownloading -> {
            // Downloading — in-button progress bar with fill animation
            val percent = progress?.percent ?: 0
            DownloadProgressButton(
                percent = percent,
                speedText = progress?.let {
                    val downloaded = formatBytes(it.bytesDownloaded)
                    val total = it.totalBytes?.let { formatBytes(it) } ?: "?"
                    "$downloaded / $total"
                } ?: "",
                modifier = modifier,
            )
        }
        else -> {
            // Initial state — show Download button
            Button(
                onClick = onDownload,
                modifier = modifier.height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val sizeText = apkSizeBytes?.let { " (${formatBytes(it)})" } ?: ""
                Text(
                    text = "Download$sizeText",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * A button that shows download progress with a left-to-right fill animation.
 *
 * The button background fills proportionally to [percent]. Inside, it shows
 * "Downloading X%" centered. The text color adapts to the background:
 * - Over the filled (primary) area → white (onPrimary)
 * - Over the unfilled (light) area → dark (onSurface)
 *
 * Since the text is centered and may span both areas, we compute the luminance
 * of the primary color and pick the text color that has the best contrast
 * against the **dominant** background at the text's position. For simplicity,
 * we use `onPrimary` (white) when the fill is > 50%, and `onSurface` (dark)
 * when the fill is < 50%. This gives good readability in both states.
 */
@Composable
private fun DownloadProgressButton(
    percent: Int,
    speedText: String,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Compute the luminance of the primary color to determine if it's dark or light.
    // Uses the standard relative luminance formula: 0.299*R + 0.587*G + 0.114*B
    // (Rec. 601 luma — simple + fast, good enough for contrast decisions).
    val primaryLuminance = 0.299f * primaryColor.red + 0.587f * primaryColor.green + 0.114f * primaryColor.blue

    // Text color: when the fill covers the center of the button (>50%), use the
    // color that contrasts with primary. Otherwise use the color that contrasts
    // with the light background (primary@15%).
    val textColor = if (percent >= 50) {
        // Center of button is over the fill — contrast with primary
        if (primaryLuminance < 0.5f) onPrimaryColor else onSurfaceColor
    } else {
        // Center of button is over the light background — contrast with it
        // The light background (primary@15%) is always light, so use dark text.
        onSurfaceColor
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
        // Fill layer (left-to-right, proportional to percent)
        Box(
            modifier = Modifier
                .fillMaxWidth(percent / 100f)
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(primaryColor),
        )
        // Text overlay (centered, on top of fill)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Downloading $percent%",
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── URL regex for detecting links in the changelog ──
private val URL_REGEX = Regex(
    """https?://[^\s\n\r<>"']+""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)

/**
 * Renders the changelog text with clickable URL links.
 *
 * Detects all `http://` and `https://` URLs in the text and renders them as
 * clickable links styled with the primary color + underline. Clicking a link
 * calls [onLinkClick] with the URL string (the caller opens it in the browser).
 *
 * Uses [ClickableText] with an [AnnotatedString] — the standard Compose
 * pattern for mixed-style text with interactive regions.
 *
 * @param text the raw changelog text (may contain URLs)
 * @param onLinkClick called when the user taps a URL in the text
 */
@Composable
private fun ClickableChangelogText(
    text: String,
    onLinkClick: (String) -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    val annotatedText = remember(text, primaryColor, textColor) {
        buildAnnotatedString {
            var lastIndex = 0
            // Find all URL matches in the text
            for (match in URL_REGEX.findAll(text)) {
                // Append the text before the URL
                if (match.range.first > lastIndex) {
                    append(text.substring(lastIndex, match.range.first))
                }
                // Append the URL as a clickable link
                val url = match.value
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold,
                            ),
                        ),
                    ),
                ) {
                    append(url)
                }
                lastIndex = match.range.last + 1
            }
            // Append any remaining text after the last URL
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
            // Apply the base style to the entire string (non-link text)
            addStyle(
                style = SpanStyle(
                    color = textColor,
                    fontSize = 13.sp,
                    fontFamily = RobotoFamily,
                ),
                start = 0,
                end = length,
            )
        }
    }

    ClickableText(
        text = annotatedText,
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = textColor,
        ),
        onClick = { offset ->
            // Check if the click landed on a link annotation
            val link = annotatedText.getLinkAnnotations(offset, offset).firstOrNull()
            if (link != null) {
                val url = annotatedText.substring(link.start, link.end)
                onLinkClick(url)
            }
        },
    )
}

/** Formats a byte count into a human-readable string (e.g., "12.3 MB"). */
private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
