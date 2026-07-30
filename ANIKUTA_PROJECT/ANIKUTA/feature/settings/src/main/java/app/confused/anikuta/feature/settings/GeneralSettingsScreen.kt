package app.confused.anikuta.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.details.DataSource
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.preferences.DetailsViewPreferences
import app.confused.anikuta.core.preferences.LinkingPreferences
import org.koin.compose.koinInject

/**
 * The General settings page — reached from Settings → General.
 *
 * Contains:
 * - **Auto-link toggle** — controls whether extension anime are automatically
 *   searched + linked to a metadata provider (AniList by default) when opened.
 *   When OFF, the manual-link sheet is shown so the user can still link
 *   manually or go without linking.
 * - **Default details view** — the global default data source (AniList vs
 *   Extension) for the anime details page. Applies when the user opens an
 *   anime for the first time (no per-anime preference). Per-anime overrides
 *   (set via the three-dot menu on the details page) always take priority.
 *
 * # Future extensions (architecturally ready)
 *
 * - **Linking provider selector** — a dropdown letting the user pick which
 *   metadata provider to search when auto-linking (AniList, MAL, TMDB, etc.).
 *   The `LinkingPreferences.linkingProvider` field already exists; this UI
 *   will be a simple dropdown once multiple providers are registered.
 * - **Per-extension config** — a Map<extensionPkgName, AutoLinkMode> where
 *   AutoLinkMode = { AUTO, MANUAL, NEVER }. Lets the user auto-link for some
 *   extensions but not others.
 *
 * @param onBack Pops this screen.
 */
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
) {
    val linkingPrefs = koinInject<LinkingPreferences>()
    val detailsViewPrefs = koinInject<DetailsViewPreferences>()

    val autoLinkEnabled by linkingPrefs.observeAutoLinkEnabled()
        .collectAsStateWithLifecycle(initialValue = linkingPrefs.isAutoLinkEnabled())

    val linkingProvider by linkingPrefs.observeLinkingProvider()
        .collectAsStateWithLifecycle(initialValue = linkingPrefs.getLinkingProvider())

    val defaultDataSource by detailsViewPrefs.observeDefaultDataSource()
        .collectAsStateWithLifecycle(initialValue = detailsViewPrefs.getDefaultDataSource())

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 || lazyListState.firstVisibleItemIndex > 0

    var showDetailsViewDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "General", collapsed = collapsed)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            // ── Details page section ──
            item {
                GeneralSectionLabel("Details page")
            }
            item {
                GeneralSelectorCard(
                    title = "Default details view",
                    subtitle = "Choose which data source to show when opening an anime for the " +
                        "first time. Per-anime overrides (set via the three-dot menu on the " +
                        "details page) always take priority over this default.\n\n" +
                        "• AniList: metadata-rich (score, format, season, studios, next airing)\n" +
                        "• Extension: episodes, author/artist, source-specific data\n" +
                        "• Entry mode: uses the source the anime was opened from",
                    currentSelection = defaultDataSource.displayLabel(),
                    onClick = { showDetailsViewDialog = true },
                )
            }

            // ── Extension linking section ──
            item {
                GeneralSectionLabel("Extension linking")
            }
            item {
                GeneralToggleCard(
                    title = "Auto-link extension anime",
                    subtitle = "When you open an anime from an extension, automatically search " +
                        "${linkingProvider.displayName} and link it. When off, the linking sheet " +
                        "is shown so you can search manually or go without linking.",
                    checked = autoLinkEnabled,
                    onCheckedChange = { linkingPrefs.setAutoLinkEnabled(it) },
                )
            }
            item {
                GeneralInfoCard(
                    title = "Linking provider",
                    subtitle = "Currently: ${linkingProvider.displayName}. " +
                        "Future: select which service to search when auto-linking " +
                        "(AniList, MAL, TMDB, etc.).",
                )
            }

            // ── Future: per-extension config placeholder ──
            item {
                GeneralSectionLabel("Per-extension (coming soon)")
            }
            item {
                GeneralInfoCard(
                    title = "Per-extension auto-link",
                    subtitle = "Configure auto-link behavior for each extension individually. " +
                        "Some extensions may have better title matching than others — this " +
                        "lets you auto-link for reliable sources and skip it for niche ones.",
                )
            }

            // ── Advertising section ──
            item {
                GeneralSectionLabel("Advertising")
            }
            item {
                AdSettingsSection()
            }
        }
    }

    // ── Default details view selection dialog ──
    if (showDetailsViewDialog) {
        DefaultDetailsViewDialog(
            currentSelection = defaultDataSource,
            onSelect = { source ->
                detailsViewPrefs.setDefaultDataSource(source)
                showDetailsViewDialog = false
            },
            onDismiss = { showDetailsViewDialog = false },
        )
    }
}

/** Converts a nullable [DataSource] to a user-facing display label. */
private fun DataSource?.displayLabel(): String = when (this) {
    DataSource.ANILIST -> "AniList"
    DataSource.EXTENSION -> "Extension"
    null -> "Entry mode"
}

/**
 * Dialog for selecting the default details view. Shows three radio options:
 * AniList, Extension, and Entry mode (no default).
 */
@Composable
private fun DefaultDetailsViewDialog(
    currentSelection: DataSource?,
    onSelect: (DataSource?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default details view", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                DefaultViewOption(
                    label = "AniList",
                    description = "Metadata-rich: score, format, season, studios, next airing",
                    selected = currentSelection == DataSource.ANILIST,
                    onClick = { onSelect(DataSource.ANILIST) },
                )
                DefaultViewOption(
                    label = "Extension",
                    description = "Episodes, author/artist, source-specific data",
                    selected = currentSelection == DataSource.EXTENSION,
                    onClick = { onSelect(DataSource.EXTENSION) },
                )
                DefaultViewOption(
                    label = "Entry mode",
                    description = "Uses the source the anime was opened from (default)",
                    selected = currentSelection == null,
                    onClick = { onSelect(null) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** One radio option row in the default details view dialog. */
@Composable
private fun DefaultViewOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun GeneralSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun GeneralToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
internal fun GeneralInfoCard(
    title: String,
    subtitle: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * A settings card that shows a title + subtitle + the current selection label.
 * Tapping the card calls [onClick] (typically to open a selection dialog).
 */
@Composable
internal fun GeneralSelectorCard(
    title: String,
    subtitle: String,
    currentSelection: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(
                    text = currentSelection,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
