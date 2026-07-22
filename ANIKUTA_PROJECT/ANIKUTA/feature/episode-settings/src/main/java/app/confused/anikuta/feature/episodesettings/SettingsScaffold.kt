package app.confused.anikuta.feature.episodesettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The full-page scaffold for every episode-settings sub-screen.
 *
 * Renders a sticky top bar: `[← back-arrow]  Title` + optional trailing [actions],
 * then the [content] below it. The top bar respects the status-bar inset.
 *
 * This mirrors the old ANIKUTA project's `SettingsSubpageScaffold` — full pages,
 * NOT bottom sheets (per user requirement: "it should have been a completely new
 * page so I want you to handle that properly").
 *
 * @param title The screen title shown next to the back arrow (RobotoFamily ExtraBold).
 * @param onBack Called when the back arrow is tapped (or system back is pressed —
 *               the caller wires `BackHandler`).
 * @param actions Optional trailing composable (e.g. a reset button). Rendered at the
 *                right edge of the top bar.
 * @param content The screen body. Caller is responsible for its scroll behavior.
 */
@Composable
fun SettingsSubpageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ── Top bar: back arrow + title + actions ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        content()
    }
}
