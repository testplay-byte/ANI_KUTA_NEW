package app.anikuta.ui.settings

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.source.api.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource

/**
 * Phase 7 — Extension Details screen.
 *
 * Shows the extension's metadata (icon, name, version, language, NSFW),
 * a list of its sources (each with a Settings gear if [ConfigurableAnimeSource]),
 * and Trust / Untrust / Uninstall actions.
 *
 * Accessed by tapping a Source (trusted) extension in the Extensions screen.
 */
@Composable
fun ExtensionDetailsScreen(
    pkgName: String,
    onBack: () -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
) {
    val viewModel: ExtensionsViewModel = viewModel()
    val sources by viewModel.sources.collectAsState()
    val installed by viewModel.installed.collectAsState()

    // Find the extension by pkgName — check trusted (sources) first, then untrusted
    val extension = sources.find { it.pkgName == pkgName }
    val isTrusted = extension != null
    val untrustedExt = installed.find { it.pkgName == pkgName }

    SettingsSubpageScaffold(title = "Extension details", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header card — centered icon, name, pkg, version/lang
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Centered icon
                        ExtensionIconSlot(drawable = extension?.icon, iconUrl = null, contentDescription = extension?.name ?: "")
                        Spacer(Modifier.height(12.dp))
                        // App name
                        Text(
                            text = extension?.name ?: untrustedExt?.name ?: "Unknown",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        // Package identifier
                        Text(
                            text = pkgName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        // Version + language
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "v${extension?.versionName ?: untrustedExt?.versionName ?: "?"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val lang = extension?.lang ?: untrustedExt?.lang
                            if (!lang.isNullOrBlank()) {
                                Text(
                                    text = "· ${langLabel(lang)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Actions — "Remove from Sources" / "Add to Sources" is the primary
            // button (takes most space). Uninstall is a compact icon-only button.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isTrusted && extension != null) {
                        OutlinedButton(
                            onClick = { viewModel.revokeTrust(extension) },
                            modifier = Modifier.weight(1f),
                            content = {
                                Text("Remove from Sources", maxLines = 1, softWrap = false)
                            },
                        )
                    } else if (untrustedExt != null) {
                        Button(
                            onClick = { viewModel.trustExtension(untrustedExt) },
                            modifier = Modifier.weight(1f),
                            content = {
                                Text("Add to Sources", maxLines = 1, softWrap = false)
                            },
                        )
                    }
                    // Compact icon-only Uninstall button
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.errorContainer,
                        onClick = { viewModel.uninstallExtension(pkgName) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Uninstall",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(20.dp),
                        )
                    }
                }
            }

            // Sources list (only for trusted extensions — untrusted don't have loaded sources)
            if (isTrusted && extension != null && extension.sources.isNotEmpty()) {
                item {
                    Text(
                        "Sources (${extension.sources.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(extension.sources.size) { index ->
                    val source = extension.sources[index]
                    SourceRow(
                        source = source,
                        onOpenSettings = { onOpenSourcePreferences(source.id) },
                    )
                    if (index < extension.sources.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: AnimeSource,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "ID: ${source.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (source is ConfigurableAnimeSource) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Source settings")
            }
        }
    }
}

@Composable
private fun ExtensionIconSlot(
    drawable: Drawable?,
    iconUrl: String?,
    contentDescription: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val painter = drawable?.let { remember(it) { it.toPainterOrNull() } }
            when {
                painter != null -> androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(40.dp),
                )
                else -> Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun Drawable.toPainterOrNull(): Painter? = try {
    BitmapPainter(this.toBitmap().asImageBitmap())
} catch (e: Exception) {
    null
}

// Reuse the langLabel from ExtensionsSettingsScreen
private fun langLabel(code: String): String = when (code.lowercase()) {
    "en" -> "EN"
    "ja" -> "JP"
    "zh" -> "ZH"
    "ko" -> "KO"
    "es" -> "ES"
    "fr" -> "FR"
    "de" -> "DE"
    "pt" -> "PT"
    "pt-br" -> "PT-BR"
    "it" -> "IT"
    "ru" -> "RU"
    "ar" -> "AR"
    "id" -> "ID"
    else -> code.uppercase()
}
