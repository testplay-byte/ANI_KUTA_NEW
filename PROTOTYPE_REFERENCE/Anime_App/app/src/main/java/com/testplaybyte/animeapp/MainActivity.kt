package com.testplaybyte.animeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testplaybyte.animeapp.data.SettingsRepository
import com.testplaybyte.animeapp.model.AppSettings
import com.testplaybyte.animeapp.theme.AnimeAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Read the theme setting from DataStore — this makes the theme
            // toggle in Settings actually work (was hardcoded to dark before).
            val context = LocalContext.current
            val settingsRepo = remember { SettingsRepository(context) }
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

            AnimeAppTheme(darkTheme = settings.darkTheme) {
                AnimeApp()
            }
        }
    }
}
