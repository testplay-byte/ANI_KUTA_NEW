package app.confused.anikuta.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The Appearance screen — a list of appearance-related option rows.
 *
 * Per owner spec (Session 1): this screen is JUST a list of buttons — no
 * customization functions. Tapping each row navigates to the relevant sub-page:
 * - **General** → [AppearanceGeneralScreen] (theme mode, AMOLED, palettes).
 * - **Episode settings** → the episode settings hub.
 *
 * @param onOpenGeneral Navigates to the General appearance screen.
 * @param onOpenEpisodeSettings Navigates to the Episode Settings hub.
 * @param onBack Pops this screen.
 */
@Composable
fun AppearanceScreen(
    onOpenGeneral: () -> Unit,
    onOpenEpisodeSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 || lazyListState.firstVisibleItemIndex > 0

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Appearance", collapsed = collapsed)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            item {
                SettingsSectionLabel("General")
                AppearanceNavRow(
                    icon = Icons.Filled.Palette,
                    title = "General",
                    subtitle = "Theme mode, palettes, and colors",
                    onClick = onOpenGeneral,
                )
            }
            item {
                SettingsSectionLabel("Episode List")
                AppearanceNavRow(
                    icon = Icons.Filled.Tune,
                    title = "Episode settings",
                    subtitle = "Display, layout, and metadata",
                    onClick = onOpenEpisodeSettings,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
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
private fun AppearanceNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
