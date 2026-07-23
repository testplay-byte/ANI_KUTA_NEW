package app.confused.anikuta.feature.trackers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.trackers.components.TrackerCard
import org.koin.compose.koinViewModel

/**
 * Trackers settings screen — list of tracker cards (AniList, MAL).
 *
 * Each card shows the tracker name, connection status, and a Login/Logout
 * button. Login opens the browser OAuth flow; logout clears the stored token
 * (with confirmation).
 */
@Composable
fun TrackersSettingsScreen(
    onBack: () -> Unit,
    onLoginTracker: (Int) -> Unit,
    viewModel: TrackersViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Trackers", scrollState = scrollState)

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                // Section header
                item {
                    Text(
                        text = "CONNECTED SERVICES",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.06.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                    )
                }

                // Tracker cards
                items(state.trackers) { trackerState ->
                    TrackerCard(
                        state = trackerState,
                        onLogin = { onLoginTracker(trackerState.id) },
                        onLogout = { viewModel.logout(trackerState.id) },
                    )
                }

                // Info text
                item {
                    Text(
                        text = "Trackers sync your watch progress to external services. " +
                            "Login to enable automatic syncing.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}
