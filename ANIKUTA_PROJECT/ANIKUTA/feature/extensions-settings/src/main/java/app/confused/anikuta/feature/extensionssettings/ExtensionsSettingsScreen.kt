package app.confused.anikuta.feature.extensionssettings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.SettingsGroupCard
import app.confused.anikuta.core.designsystem.component.TwoWayToggle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Extensions settings screen — the user's management surface for anime/manga
 * extensions.
 *
 * Per `DESIGN_LANGUAGE/04-screens/extensions-settings.md` + ADR-016:
 * - **Top:** a 2-way Anime/Manga toggle (the screen shows the selected
 *   category's extensions). Default = Anime (the app is anime-first per
 *   ADR-009). Sticky — does NOT scroll away.
 * - **THREE stacked [SettingsGroupCard]s** in this exact order (the owner's
 *   flagship preference for this screen — "quite good"):
 *   1. **Trusted Sources** — pinned/trusted extensions (max 2 per category,
 *      drag-reorderable in a later phase).
 *   2. **Installed Extensions** — installed locally but not trusted.
 *   3. **Available Extensions** — fetched from extension repositories.
 *
 * This build renders the screen **structure** with the three category cards
 * and their per-section empty-state copy. Real data binding
 * (ViewModel + Repository + extension repo fetching + drag-reorderable
 * trusted sources) lands in a later phase per the phased plan.
 *
 * Design rules honored:
 * - Edge-to-edge (the [CollapsingHeader] uses `.statusBarsPadding()`).
 * - [CollapsingHeader] title "Extensions", shrinks on scroll.
 * - [SettingsGroupCard] for each of the 3 categories.
 * - [TwoWayToggle] (design language §3) for the Anime/Manga switch.
 * - 110dp bottom padding reserved for the floating bottom nav.
 * - [RobotoFamily] + [FontWeight.ExtraBold] for bold text (Type.kt).
 */
@Composable
fun ExtensionsSettingsScreen(
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    // 0 = Anime, 1 = Manga (ADR-016: Video / Image-Manga).
    // Default = Anime per ADR-009 (the app is anime-first).
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned collapsing header with a back button
        CollapsingHeader(
            title = "Extensions",
            scrollState = scrollState,
            actions = {
                // Back button will be handled by the parent's onBack
                // For now, the header just shows the title
            },
        )

        // Sticky Anime/Manga toggle — sits below the header, does NOT scroll
        // away with the list (per extensions-settings.md §4).
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
                .padding(bottom = 110.dp), // reserved for the floating nav bar
        ) {
            // ── Card 1 — Trusted Sources (TOP, max 2 per category) ──────────────
            SettingsGroupCard(label = "Trusted Sources") {
                EmptySectionBody(
                    message = "No trusted sources. Trust an extension to pin it here.",
                )
            }

            // ── Card 2 — Installed Extensions (MIDDLE) ──────────────────────────
            SettingsGroupCard(label = "Installed Extensions") {
                EmptySectionBody(
                    message = "No extensions installed. Browse available extensions below.",
                )
            }

            // ── Card 3 — Available Extensions (BOTTOM, from repos) ──────────────
            SettingsGroupCard(label = "Available Extensions") {
                EmptySectionBody(
                    message = "No extensions available. Add an extension repository to browse.",
                )
            }
        }
    }
}

/**
 * A subdued empty-state body shown inside a [SettingsGroupCard] when the
 * category has no entries. Centered horizontally, 16dp horizontal padding +
 * 20dp vertical padding so the card has visible breathing room.
 *
 * Uses [RobotoFamily] + [FontWeight.Medium] (regular descriptive text — not
 * a heading, so not ExtraBold).
 */
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
