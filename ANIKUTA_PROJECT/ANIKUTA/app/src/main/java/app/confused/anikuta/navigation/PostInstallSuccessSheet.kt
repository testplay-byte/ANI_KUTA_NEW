@file:OptIn(ExperimentalMaterial3Api::class)

package app.confused.anikuta.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay

/**
 * The post-install success popup — a bottom-up sheet shown after the user
 * installs an update + reopens the app.
 *
 * # Flow (per spec)
 *
 * 1. Title: "Update Installed Successfully" (bold, primary-colored)
 * 2. **Cleaning up phase (~1.5s):** shows a CircularProgressIndicator + the
 *    text "Cleaning up downloaded APK…"
 * 3. **APK deletion:** calls [AppUpdateManager.cleanupOldDownloads] to delete
 *    the just-installed APK file from disk (it's no longer needed).
 * 4. **Confirmation phase (~0.5s):** shows a check-mark icon + the text
 *    "APK deleted".
 * 5. Auto-dismiss after the total ~2s elapsed (calls
 *    [AppController.dismissPostInstallPopup]).
 *
 * # Why a separate popup (not just a toast)
 *
 * The user just went through a multi-step download + install flow. A clear,
 * animated confirmation closes the loop + visibly demonstrates that the APK
 * file is being cleaned up (otherwise it lingers in cache forever).
 *
 * @param appController the app controller (provides the dismiss callback +
 *   the update manager for cleanup).
 */
@Composable
fun PostInstallSuccessSheet(appController: AppController) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Phase: 0 = cleaning up (spinner), 1 = done (check mark)
    var phase by remember { mutableStateOf(0) }

    // ── Animation lifecycle ──
    // 0–1500ms: cleaning up spinner → call cleanupOldDownloads at ~1400ms
    // 1500–2000ms: check mark + "APK deleted"
    // 2000ms: dismiss
    LaunchedEffect(Unit) {
        delay(1500)
        // Delete the just-installed APK file (no longer needed).
        try {
            appController.updateManager.cleanupOldDownloads()
        } catch (e: Exception) {
            android.util.Log.w("PostInstall", "cleanupOldDownloads failed (non-fatal)", e)
        }
        phase = 1
        delay(500)
        appController.dismissPostInstallPopup()
    }

    ModalBottomSheet(
        onDismissRequest = { appController.dismissPostInstallPopup() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Title ──
            Text(
                text = "Update Installed Successfully",
                fontFamily = RobotoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ── Animated phase content ──
            // Cleaning up (phase 0): spinner + "Cleaning up downloaded APK…"
            // Done (phase 1): check-mark icon + "APK deleted"
            AnimatedVisibility(
                visible = phase == 0,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Cleaning up downloaded APK…",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = phase == 1,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APK deleted",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Small icon at top to make the sheet feel more substantial
            Spacer(modifier = Modifier.height(16.dp))
            Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
