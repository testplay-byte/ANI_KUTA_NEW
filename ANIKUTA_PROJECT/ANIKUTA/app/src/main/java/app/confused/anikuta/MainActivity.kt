package app.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.NavIcons
import app.confused.anikuta.core.designsystem.component.NavItem
import app.confused.anikuta.core.designsystem.theme.AnikutaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Design language principle #1: edge-to-edge
        setContent {
            AnikutaTheme(darkTheme = true) { // Dark is the default (owner preference)
                AnikutaApp()
            }
        }
    }
}

/** Temporary nav items — Phase 4 will wire these to real screens. */
private val navItems = listOf(
    NavItem("home", "Home", NavIcons.Home),
    NavItem("library", "Library", NavIcons.Library),
    NavItem("search", "Search", NavIcons.Search),
    NavItem("settings", "Settings", NavIcons.Settings),
    NavItem("more", "More", NavIcons.More),
)

@Composable
private fun AnikutaApp() {
    var currentRoute by remember { mutableStateOf("home") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Content area — placeholder for Phase 4+ screens
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = when (currentRoute) {
                    "home" -> "ANIKUTA"
                    "library" -> "Library"
                    "search" -> "Search"
                    "settings" -> "Settings"
                    else -> "More"
                },
                scrollState = scrollState,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (currentRoute) {
                        "home" -> "Home — Phase 4 coming soon"
                        "library" -> "Library — Phase 5+"
                        "search" -> "Search — Phase 4+"
                        "settings" -> "Settings — Phase 10+"
                        else -> "More — Phase 10+"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Floating bottom nav — overlays on top of content
        AnikutaBottomNavBar(
            items = navItems,
            currentRoute = currentRoute,
            onSelect = { route -> currentRoute = route },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
