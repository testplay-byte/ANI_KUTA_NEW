package app.confused.anikuta.feature.extensionssettings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.SettingsGroupCard
import app.confused.anikuta.core.designsystem.component.TwoWayToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.installer.InstallStep
import app.confused.anikuta.data.extension.model.AnimeExtension
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "AnikutaExtUI"

/**
 * Extensions settings screen — shows real installed, untrusted, and available
 * extensions from the [AnimeExtensionManager].
 *
 * Per ADR-016 + DESIGN_LANGUAGE/04-screens/extensions-settings.md:
 * - Top: 2-way Anime/Manga toggle (sticky, doesn't scroll away)
 * - Three categories: Trusted Sources → Installed Extensions → Available Extensions
 * - Settings button (top-right) → opens repo management page
 * - Trust/untrust, install, uninstall functionality
 * - Proper logging (tag: AnikutaExtUI)
 */
@Composable
fun ExtensionsSettingsScreen(
    extensionManager: AnimeExtensionManager,
    repoRepository: ExtensionRepoRepository,
    onBack: () -> Unit = {},
    onOpenRepoSettings: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Collect extension flows
    val installedExtensions by extensionManager.installedExtensionsFlow.collectAsState()
    val untrustedExtensions by extensionManager.untrustedExtensionsFlow.collectAsState()
    val availableExtensions by extensionManager.availableExtensionsFlow.collectAsState()

    // Fetch available extensions on first load
    LaunchedEffect(Unit) {
        Log.i(TAG, "Extensions screen opened — fetching available extensions")
        isRefreshing = true
        extensionManager.findAvailableExtensions()
        isRefreshing = false
        Log.i(TAG, "Extensions: ${installedExtensions.size} installed, ${untrustedExtensions.size} untrusted, ${availableExtensions.size} available")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with settings button in actions slot
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

        // Sticky Anime/Manga toggle
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
            // ── Card 1: Trusted Sources (= installed + trusted extensions) ──
            SettingsGroupCard(label = "Trusted Sources") {
                if (installedExtensions.isEmpty()) {
                    EmptySectionBody("No trusted sources. Trust an extension to pin it here.")
                } else {
                    installedExtensions.forEach { ext ->
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

            // ── Card 2: Untrusted Extensions (installed but not trusted) ──
            if (untrustedExtensions.isNotEmpty()) {
                SettingsGroupCard(label = "Untrusted") {
                    untrustedExtensions.forEach { ext ->
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
                                extensionManager.uninstallExtension(ext)
                            },
                        )
                    }
                }
            }

            // ── Card 3: Available Extensions (from repos) ──
            SettingsGroupCard(label = "Available Extensions") {
                when {
                    isRefreshing && availableExtensions.isEmpty() -> {
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
                    availableExtensions.isEmpty() -> {
                        EmptySectionBody("No extensions available. Add a repository in settings to browse.")
                    }
                    else -> {
                        availableExtensions.forEach { ext ->
                            AvailableExtensionRow(
                                extension = ext,
                                isInstalled = installedExtensions.any { it.pkgName == ext.pkgName } ||
                                    untrustedExtensions.any { it.pkgName == ext.pkgName },
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Extension icon placeholder
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
            // Source names
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

        // Uninstall button
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

        // Trust button
        IconButton(onClick = onTrust) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "Trust",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        // Uninstall button
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
        ExtensionIconPlaceholder(name = extension.name)

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

        // Install button (only if not installed)
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
