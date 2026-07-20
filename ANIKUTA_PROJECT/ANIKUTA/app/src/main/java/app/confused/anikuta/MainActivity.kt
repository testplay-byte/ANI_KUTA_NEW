package app.confused.anikuta

import android.os.Bundle
import android.util.Log
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
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.NavIcons
import app.confused.anikuta.core.designsystem.component.NavItem
import app.confused.anikuta.core.designsystem.theme.AnikutaTheme
import app.confused.anikuta.feature.browse.BrowseScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnikutaTheme(darkTheme = true) {
                AnikutaApp()
            }
        }
    }
}

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
    val anilistApi = remember { AniListApi() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (currentRoute) {
            "home" -> BrowseScreen(api = anilistApi)
            else -> PlaceholderScreen(title = currentRoute.replaceFirstChar { it.uppercase() })
        }

        // Floating bottom nav
        AnikutaBottomNavBar(
            items = navItems,
            currentRoute = currentRoute,
            onSelect = { route -> currentRoute = route },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = title, scrollState = scrollState)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$title — coming in a future phase",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
