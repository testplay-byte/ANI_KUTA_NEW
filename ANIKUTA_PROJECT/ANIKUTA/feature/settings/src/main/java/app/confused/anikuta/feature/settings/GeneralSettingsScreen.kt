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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.preferences.LinkingPreferences
import org.koin.compose.koinInject

/**
 * The General settings page — reached from Settings → General.
 *
 * Contains:
 * - **Auto-link toggle** — controls whether extension anime are automatically
 *   searched + linked to a metadata provider (AniList by default) when opened.
 *   When OFF, extension anime open as extension-only (no metadata enrichment,
 *   no tracker sync). The user can still manually link later via the "A"
 *   re-link button on the details page.
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

    val autoLinkEnabled by linkingPrefs.observeAutoLinkEnabled()
        .collectAsStateWithLifecycle(initialValue = linkingPrefs.isAutoLinkEnabled())

    val linkingProvider by linkingPrefs.observeLinkingProvider()
        .collectAsStateWithLifecycle(initialValue = linkingPrefs.getLinkingProvider())

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 || lazyListState.firstVisibleItemIndex > 0

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "General", collapsed = collapsed)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            // ── Extension linking section ──
            item {
                GeneralSectionLabel("Extension linking")
            }
            item {
                GeneralToggleCard(
                    title = "Auto-link extension anime",
                    subtitle = "When you open an anime from an extension, automatically search " +
                        "${linkingProvider.displayName} and link it. When off, extension anime " +
                        "open as extension-only without metadata enrichment or tracker sync. " +
                        "You can still manually link later via the re-link button.",
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
        }
    }
}

@Composable
private fun GeneralSectionLabel(text: String) {
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
private fun GeneralToggleCard(
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
private fun GeneralInfoCard(
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
