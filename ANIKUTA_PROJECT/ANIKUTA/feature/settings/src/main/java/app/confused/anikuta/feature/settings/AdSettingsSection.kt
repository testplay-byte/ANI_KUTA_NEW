package app.confused.anikuta.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.ads.AdTracker
import app.confused.anikuta.core.ads.AdsPreferences
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.koinInject

/**
 * The advertising settings section — embedded in GeneralSettingsScreen.
 *
 * Lets the user configure:
 * - **Ads enabled** — master on/off toggle.
 * - **Daily ad quota** — how many ads per day (1–1000). Default: 1000 (testing).
 * - **Cooldown** — minutes between ads after watching one. Default: 30.
 * - **Minimum stay** — seconds the user must stay on the ad URL. Default: 2.
 * - **Ad URL** — the URL to redirect to. Default: https://1118000.xyz/
 *
 * Also shows live stats: ads shown today + total lifetime ads.
 */
@Composable
fun AdSettingsSection() {
    val adsPrefs = koinInject<AdsPreferences>()
    val adTracker = koinInject<AdTracker>()

    val adsEnabled by adsPrefs.observeAdsEnabled()
        .collectAsStateWithLifecycle(initialValue = adsPrefs.isAdsEnabled())
    val dailyQuota by adsPrefs.observeDailyQuota()
        .collectAsStateWithLifecycle(initialValue = adsPrefs.getDailyQuota())
    val cooldownMin by adsPrefs.observeCooldownMinutes()
        .collectAsStateWithLifecycle(initialValue = adsPrefs.getCooldownMinutes())
    val minStay by adsPrefs.observeMinStaySeconds()
        .collectAsStateWithLifecycle(initialValue = adsPrefs.getMinStaySeconds())
    val adUrl by adsPrefs.observeAdUrl()
        .collectAsStateWithLifecycle(initialValue = adsPrefs.getAdUrl())
    val adsShownToday by adTracker.observeAdsShownToday()
        .collectAsStateWithLifecycle(initialValue = adTracker.getAdsShownToday())
    val totalAds by adTracker.observeTotalAdsShown()
        .collectAsStateWithLifecycle(initialValue = adTracker.getTotalAdsShown())

    var showQuotaDialog by remember { mutableStateOf(false) }
    var showCooldownDialog by remember { mutableStateOf(false) }
    var showMinStayDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    Column {
        // ── Master toggle ──
        GeneralToggleCard(
            title = "Show ads",
            subtitle = "When on, ads are shown before opening anime details. " +
                "All tracking is on-device only — no data is sent to any server.",
            checked = adsEnabled,
            onCheckedChange = { adsPrefs.setAdsEnabled(it) },
        )

        // ── Stats ──
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$adsShownToday",
                        fontFamily = RobotoFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Today",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalAds",
                        fontFamily = RobotoFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Total",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Daily quota ──
        GeneralSelectorCard(
            title = "Daily ad quota",
            subtitle = "How many ads you see per day (1–1000). The count resets at midnight.",
            currentSelection = "$dailyQuota",
            onClick = { showQuotaDialog = true },
        )

        // ── Cooldown ──
        GeneralSelectorCard(
            title = "Cooldown after ad",
            subtitle = "After watching an ad, no new ad is shown for this many minutes.",
            currentSelection = "${cooldownMin} min",
            onClick = { showCooldownDialog = true },
        )

        // ── Minimum stay ──
        GeneralSelectorCard(
            title = "Minimum stay time",
            subtitle = "How long you must stay on the ad page before returning. " +
                "If you return sooner, the ad is not counted.",
            currentSelection = "$minStay sec",
            onClick = { showMinStayDialog = true },
        )

        // ── Ad URL ──
        GeneralSelectorCard(
            title = "Ad URL",
            subtitle = "The URL you are redirected to when you accept an ad.",
            currentSelection = if (adUrl.length > 30) adUrl.take(27) + "..." else adUrl,
            onClick = { showUrlDialog = true },
        )
    }

    // ── Dialogs ──
    if (showQuotaDialog) {
        NumberInputDialog(
            title = "Daily ad quota",
            currentValue = dailyQuota,
            min = 1,
            max = 1000,
            onConfirm = { adsPrefs.setDailyQuota(it); showQuotaDialog = false },
            onDismiss = { showQuotaDialog = false },
        )
    }
    if (showCooldownDialog) {
        NumberInputDialog(
            title = "Cooldown (minutes)",
            currentValue = cooldownMin,
            min = 0,
            max = 1440,
            onConfirm = { adsPrefs.setCooldownMinutes(it); showCooldownDialog = false },
            onDismiss = { showCooldownDialog = false },
        )
    }
    if (showMinStayDialog) {
        NumberInputDialog(
            title = "Minimum stay (seconds)",
            currentValue = minStay,
            min = 1,
            max = 60,
            onConfirm = { adsPrefs.setMinStaySeconds(it); showMinStayDialog = false },
            onDismiss = { showMinStayDialog = false },
        )
    }
    if (showUrlDialog) {
        UrlInputDialog(
            currentValue = adUrl,
            onConfirm = { adsPrefs.setAdUrl(it); showUrlDialog = false },
            onDismiss = { showUrlDialog = false },
        )
    }
}

@Composable
private fun NumberInputDialog(
    title: String,
    currentValue: Int,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "Enter a value between $min and $max.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toIntOrNull()
                if (value != null && value in min..max) {
                    onConfirm(value)
                }
            }) {
                Text("Save", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun UrlInputDialog(
    currentValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ad URL", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "The URL the user is redirected to when they accept an ad.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onConfirm(text.trim())
            }) {
                Text("Save", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
