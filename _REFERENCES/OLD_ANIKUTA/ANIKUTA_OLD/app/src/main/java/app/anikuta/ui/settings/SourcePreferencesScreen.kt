package app.anikuta.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import app.anikuta.R
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 7 — Extension Source Preferences screen.
 *
 * Hosts a [PreferenceFragmentCompat] inside a Compose [AndroidView]. If the
 * source is NOT [ConfigurableAnimeSource] or has no preferences, shows a
 * "No settings available" message instead of a blank page.
 */
@Composable
fun SourcePreferencesScreen(
    sourceId: Long,
    onBack: () -> Unit,
) {
    // Check if the source is configurable + has preferences BEFORE rendering
    // the fragment. This lets us show a friendly message instead of a blank
    // page when the source has no settings.
    var sourceStatus by remember { mutableStateOf<SourcePrefStatus>(SourcePrefStatus.Loading) }

    LaunchedEffect(sourceId) {
        sourceStatus = try {
            val sourceManager = Injekt.get<AnimeSourceManager>()
            val source = sourceManager.getOrStub(sourceId)
            Log.i("SourcePrefsScreen", "Source: ${source.name} (id=$sourceId, class=${source.javaClass.simpleName})")
            if (source is ConfigurableAnimeSource) {
                SourcePrefStatus.Configurable(source.name, source.javaClass.simpleName)
            } else {
                SourcePrefStatus.NotConfigurable(source.name, source.javaClass.simpleName)
            }
        } catch (e: Exception) {
            Log.e("SourcePrefsScreen", "Failed to check source", e)
            SourcePrefStatus.Error(e.message ?: "Unknown error")
        }
    }

    SettingsSubpageScaffold(title = "Source settings", onBack = onBack) {
        when (val status = sourceStatus) {
            SourcePrefStatus.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            is SourcePrefStatus.Configurable -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FragmentContainerView(ctx).apply {
                            id = R.id.source_preferences_container
                        }
                    },
                    update = { container ->
                        val activity = container.context as? FragmentActivity ?: return@AndroidView
                        val fm = activity.supportFragmentManager
                        val tag = "source_prefs_$sourceId"
                        val existing = fm.findFragmentByTag(tag)
                        if (existing == null) {
                            val fragment = SourcePreferencesFragment.newInstance(sourceId)
                            fm.beginTransaction()
                                .replace(R.id.source_preferences_container, fragment, tag)
                                .commitAllowingStateLoss()
                        }
                    },
                )
            }
            is SourcePrefStatus.NotConfigurable -> {
                NoSettingsAvailable(status.sourceName, status.sourceClass)
            }
            is SourcePrefStatus.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Error: ${status.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoSettingsAvailable(sourceName: String, sourceClass: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                "No settings available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$sourceName doesn't expose any configurable settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private sealed class SourcePrefStatus {
    data object Loading : SourcePrefStatus()
    data class Configurable(val sourceName: String, val sourceClass: String) : SourcePrefStatus()
    data class NotConfigurable(val sourceName: String, val sourceClass: String) : SourcePrefStatus()
    data class Error(val message: String) : SourcePrefStatus()
}

/**
 * The PreferenceFragmentCompat that hosts the extension's preference screen.
 */
class SourcePreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val sourceId = arguments?.getLong(ARG_SOURCE_ID) ?: run {
            Log.e(TAG, "No sourceId argument")
            return
        }
        Log.i(TAG, "onCreatePreferences: sourceId=$sourceId")

        val sourceManager = try {
            Injekt.get<AnimeSourceManager>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get AnimeSourceManager from DI", e)
            return
        }

        val source = sourceManager.getOrStub(sourceId)
        Log.i(TAG, "Source: ${source.name} (id=${source.id}, class=${source.javaClass.simpleName})")

        if (source is ConfigurableAnimeSource) {
            Log.i(TAG, "Source IS ConfigurableAnimeSource — building preference screen")
            try {
                preferenceManager.preferenceDataStore =
                    SourcePreferenceDataStore(source.sourcePreferences())
                val screen = preferenceManager.createPreferenceScreen(requireContext())
                source.setupPreferenceScreen(screen)
                preferenceScreen = screen
                Log.i(TAG, "Preference screen built + attached. Preference count: ${screen.preferenceCount}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build preference screen", e)
            }
        } else {
            Log.w(TAG, "Source is NOT ConfigurableAnimeSource — no settings to show")
            val screen = preferenceManager.createPreferenceScreen(requireContext())
            preferenceScreen = screen
        }
    }

    companion object {
        private const val TAG = "SourcePrefsFragment"
        private const val ARG_SOURCE_ID = "source_id"

        fun newInstance(sourceId: Long): SourcePreferencesFragment {
            return SourcePreferencesFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SOURCE_ID, sourceId)
                }
            }
        }
    }
}
