package app.anikuta.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikuta.data.cache.LocalCache
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * General settings — cache + battery optimization.
 */
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localCache: LocalCache? = try { Injekt.get() } catch (e: Exception) { null }

    SettingsSubpageScaffold(title = "General", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroupCard(title = "Storage") {
                    ClickableSettingsRow(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear cache",
                        subtitle = "Remove cached AniList data + images",
                        onClick = {
                            scope.launch {
                                try { localCache?.clear() } catch (_: Exception) {}
                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
            // Battery optimization (moved here from Notifications — it's a general concern)
            item {
                SettingsGroupCard(title = "Background reliability") {
                    val powerManager = remember {
                        context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    }
                    var isIgnoringBattery by remember {
                        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
                    }
                    ClickableSettingsRow(
                        icon = Icons.Default.BatteryFull,
                        title = "Disable battery optimization",
                        subtitle = if (isIgnoringBattery)
                            "✓ App is exempt — background tracking will work reliably"
                        else
                            "Required for reliable background episode tracking. Some OEMs kill background apps without this.",
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                ).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                    )
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
