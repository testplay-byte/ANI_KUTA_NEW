package app.confused.anikuta.navigation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.ads.AdInteractionState
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The ad interstitial dialog — shown when [AppController.pendingAdNavigation] is non-null.
 *
 * # Design
 *
 * A full-screen overlay with a semi-transparent scrim + a centered card.
 * The card shows:
 * - A catchy title: "Your daily dose of pills is here."
 * - The remaining daily quota.
 * - Two buttons: "OK" (accept the ad → open browser) + "Cancel" (skip the ad).
 *
 * # States
 *
 * - [AdInteractionState.DialogShowing] — the main dialog (OK / Cancel).
 * - [AdInteractionState.AdInProgress] — the browser is open (spinner).
 * - [AdInteractionState.ReturnedTooEarly] — "please stay longer" message.
 *
 * @param appController the app controller (provides ad state + callbacks).
 */
@Composable
fun AdDialog(appController: AppController) {
    val adState by appController.adManager.state.collectAsState()

    // Don't render if idle or in a terminal state.
    if (adState is AdInteractionState.Idle ||
        adState is AdInteractionState.Completed ||
        adState is AdInteractionState.Cancelled
    ) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = adState) {
            is AdInteractionState.DialogShowing -> AdDialogCard(
                remainingQuota = state.remainingQuota,
                onAccept = { appController.onAdAccepted() },
                onCancel = { appController.onAdCancelled() },
            )
            is AdInteractionState.AdInProgress -> AdInProgressCard(adUrl = state.adUrl)
            is AdInteractionState.ReturnedTooEarly -> ReturnedTooEarlyCard(
                elapsedSeconds = state.elapsedSeconds,
                requiredSeconds = state.requiredSeconds,
                onRetry = { appController.onAdTooEarlyRetry() },
                onCancel = { appController.onAdTooEarlyCancel() },
            )
            else -> { /* Idle/Completed/Cancelled — handled above */ }
        }
    }
}

@Composable
private fun AdDialogCard(
    remainingQuota: Int,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Pill emoji icon area
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "\uD83D\uDC8A", // pill emoji
                        fontSize = 36.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Your daily dose of pills is here.",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Support the app by visiting our sponsor.\n$remainingQuota ads remaining today.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = "OK",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdInProgressCard(adUrl: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Waiting for you to return…",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please stay on the page for at least a few seconds, then return to the app.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReturnedTooEarlyCard(
    elapsedSeconds: Int,
    requiredSeconds: Int,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "\u26A0\uFE0F", // warning emoji
                fontSize = 36.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Please take some time.",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please at least stay there for $requiredSeconds second${if (requiredSeconds != 1) "s" else ""}.\n" +
                    "You returned after only $elapsedSeconds second${if (elapsedSeconds != 1) "s" else ""}.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "Skip",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = "Try Again",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}
