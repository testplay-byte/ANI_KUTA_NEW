package app.anikuta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.anikuta.navigation.AnikutaNavGraph
import app.anikuta.onboarding.OnboardingScreen
import app.anikuta.ui.theme.AnikutaTheme
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle AniList OAuth callback (Phase 6 task 6.7)
        handleOAuthCallback(intent)

        setContent {
            AnikutaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val prefs = getSharedPreferences("anikuta_prefs", Context.MODE_PRIVATE)
                    var onboardingComplete by remember {
                        mutableStateOf(prefs.getBoolean("onboarding_complete", false))
                    }

                    if (!onboardingComplete) {
                        OnboardingScreen(
                            onComplete = {
                                prefs.edit().putBoolean("onboarding_complete", true).apply()
                                onboardingComplete = true
                            },
                        )
                    } else {
                        AnikutaNavGraph()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle OAuth callback when the activity is already running
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme == "aniyomi" && data.host == "anilist-auth") {
            Log.d("MainActivity", "AniList OAuth callback received: $data")
            try {
                val tracker: app.anikuta.data.tracker.AniListTracker = Injekt.get()
                val success = tracker.handleOAuthCallback(data)
                if (success) {
                    Toast.makeText(this, "AniList login successful!", Toast.LENGTH_SHORT).show()
                    // Fetch username in background
                    kotlinx.coroutines.MainScope().launch {
                        tracker.fetchUsername()
                    }
                } else {
                    Toast.makeText(this, "AniList login failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "OAuth callback handling failed", e)
            }
        }
    }
}
