package app.confused.anikuta.feature.extensionssettings

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.SettingsGroupCard
import app.confused.anikuta.core.designsystem.component.TwoWayToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.model.AnimeExtension
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "AnikutaExtUI"

@Composable
fun ExtensionsSettingsScreen(
    extensionManager: AnimeExtensionManager,
    repoRepository: ExtensionRepoRepository,
    onBack: () -> Unit = {},
    onOpenRepoSettings: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val installedExtensions by extensionManager.installedExtensionsFlow.collectAsState()
    val untrustedExtensions by extensionManager.untrustedExtensionsFlow.collectAsState()
    val availableExtensions by extensionManager.availableExtensionsFlow.collectAsState()
    val repos by repoRepository.repos.collectAsState(initial = emptyList())

    // Fetch available extensions only if repos are configured
    LaunchedEffect(repos.size) {
        if (repos.isNotEmpty()) {
            Log.i(TAG, "Extensions screen — fetching from ${repos.size} repos")
            isRefreshing = true
            extensionManager.findAvailableExtensions()
            isRefreshing = false
            Log.i(TAG, "Extensions: ${installedExtensions.size} installed, ${untrustedExtensions.size} untrusted, ${availableExtensions.size} available")
        } else {
            Log.i(TAG, "No repos configured — skipping available extensions fetch")
        }
    }

    // Filter by category: 0=Anime, 1=Manga
    // For now, manga extensions have pkg names containing "mangaextension" vs "animeextension"
    val isAnimeMode = selectedCategoryIndex == 0

    val filteredInstalled = if (isAnimeMode) {
        installedExtensions.filter { it.pkgName.contains("animeextension") }
    } else {
        installedExtensions.filter { it.pkgName.contains("mangaextension") }
    }

    val filteredUntrusted = if (isAnimeMode) {
        untrustedExtensions.filter { it.pkgName.contains("animeextension") }
    } else {
        untrustedExtensions.filter { it.pkgName.contains("mangaextension") }
    }

    val filteredAvailable = if (isAnimeMode) {
        availableExtensions.filter { it.pkgName.contains("animeextension") }
    } else {
        availableExtensions.filter { it.pkgName.contains("mangaextension") }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "Extensions",
            scrollState = scrollState,
            actions = {
                IconButton(onClick = onOpenRepoSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Repository Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )

        TwoWayToggle(
            options = listOf("Anime", "Manga"),
            selected = selectedCategoryIndex,
            onSelect = { selectedCategoryIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 110.dp),
        ) {
            // ── Trusted Sources ──
            SettingsGroupCard(label = "Trusted Sources") {
                if (filteredInstalled.isEmpty()) {
                    EmptySectionBody(if (isAnimeMode) "No trusted anime sources. Trust an extension to pin it here." else "No trusted manga sources. Trust an extension to pin it here.")
                } else {
                    filteredInstalled.forEach { ext ->
                        InstalledExtensionRow(
                            extension = ext,
                            onUninstall = {
                                Log.i(TAG, "Uninstalling: ${ext.pkgName}")
                                extensionManager.uninstallExtension(ext)
                            },
                        )
                    }
                }
            }

            // ── Untrusted ──
            if (filteredUntrusted.isNotEmpty()) {
                SettingsGroupCard(label = "Untrusted") {
                    filteredUntrusted.forEach { ext ->
                        UntrustedExtensionRow(
                            extension = ext,
                            onTrust = {
                                scope.launch {
                                    Log.i(TAG, "Trusting: ${ext.pkgName}")
                                    extensionManager.trust(ext)
                                    Log.i(TAG, "Trusted: ${ext.pkgName}")
                                }
                            },
                            onUninstall = {
                                Log.i(TAG, "Uninstalling untrusted: ${ext.pkgName}")
                                // Launch system uninstall intent
                                val intent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${ext.pkgName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                        )
                    }
                }
            }

            // ── Available ──
            SettingsGroupCard(label = "Available Extensions") {
                when {
                    repos.isEmpty() -> {
                        EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                    }
                    isRefreshing && filteredAvailable.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    filteredAvailable.isEmpty() -> {
                        EmptySectionBody(if (isAnimeMode) "No anime extensions available." else "No manga extensions available.")
                    }
                    else -> {
                        filteredAvailable.forEach { ext ->
                            AvailableExtensionRow(
                                extension = ext,
                                isInstalled = filteredInstalled.any { it.pkgName == ext.pkgName } ||
                                    filteredUntrusted.any { it.pkgName == ext.pkgName },
                                onInstall = {
                                    Log.i(TAG, "Installing: ${ext.pkgName}")
                                    scope.launch {
                                        extensionManager.installExtension(ext).collectLatest { step ->
                                            Log.d(TAG, "Install step for ${ext.pkgName}: $step")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledExtensionRow(
    extension: AnimeExtension.Installed,
    onUninstall: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Extension icon — use actual icon if available, otherwise placeholder
        // Note: Drawable -> Compose Image requires the drawable to be converted.
        // For simplicity, we use the placeholder for now — actual icon rendering
        // for installed extensions will be improved when we add the Image(drawable)
        // overload from compose-foundation in a future iteration.
        ExtensionIconPlaceholder(name = extension.name)

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = extension.name,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append("v${extension.versionName}")
                    extension.lang?.let { append(" · $it") }
                    if (extension.isNsfw) append(" · NSFW")
                    if (extension.hasUpdate) append(" · Update available")
                },
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extension.sources.forEach { source ->
                Text(
                    text = "  → ${source.name} (${source.lang.ifEmpty { "?" }})",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = {
            // Launch system uninstall dialog
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${extension.pkgName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Uninstall",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun UntrustedExtensionRow(
    extension: AnimeExtension.Untrusted,
    onTrust: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIconPlaceholder(name = extension.name)

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = extension.name,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Untrusted · v${extension.versionName}",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        IconButton(onClick = onTrust) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "Trust",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(onClick = onUninstall) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Uninstall",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AvailableExtensionRow(
    extension: AnimeExtension.Available,
    isInstalled: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Extension icon from URL
        AsyncImage(
            model = extension.iconUrl,
            contentDescription = extension.name,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = extension.name,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isInstalled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append("v${extension.versionName}")
                    append(" · ${extension.lang}")
                    if (extension.isNsfw) append(" · NSFW")
                },
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!isInstalled) {
            IconButton(onClick = onInstall) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Install",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ExtensionIconPlaceholder(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptySectionBody(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
