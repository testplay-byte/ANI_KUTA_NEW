package com.testplaybyte.animeapp.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import com.testplaybyte.animeapp.ui.components.BottomNavBar
import com.testplaybyte.animeapp.ui.components.NavItem
import com.testplaybyte.animeapp.ui.components.NavIcons
import com.testplaybyte.animeapp.ui.screens.*

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val SCHEDULE = "schedule"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{id}"
    fun detail(id: Int) = "detail/$id"

    val NAV_ITEMS = listOf(
        NavItem("home", "Home", NavIcons.Home),
        NavItem("library", "Library", NavIcons.Library),
        NavItem("history", "History", NavIcons.History),
        NavItem("schedule", "Schedule", NavIcons.Schedule),
        NavItem("search", "Search", NavIcons.Search),
        NavItem("settings", "Settings", NavIcons.Settings),
    )
}

@Composable
fun AnimeNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBottomNav = currentRoute?.startsWith("detail") != true

    // NO Scaffold — the bottom nav is a floating overlay on top of the content.
    // Content fills the full screen with the M3 background color and scrolls behind the nav.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) { HomeScreen(onOpenAnime = { navController.navigate(Routes.detail(it)) }) }
            composable(Routes.LIBRARY) { LibraryScreen(onOpenAnime = { navController.navigate(Routes.detail(it)) }) }
            composable(Routes.HISTORY) { HistoryScreen(onOpenAnime = { navController.navigate(Routes.detail(it)) }) }
            composable(Routes.SCHEDULE) { ScheduleScreen(onOpenAnime = { navController.navigate(Routes.detail(it)) }) }
            composable(Routes.SEARCH) { SearchScreen(onOpenAnime = { navController.navigate(Routes.detail(it)) }) }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: 0
                DetailScreen(animeId = id, onBack = { navController.popBackStack() })
            }
        }

        // Floating bottom nav — overlays on top of content.
        if (showBottomNav) {
            BottomNavBar(
                items = Routes.NAV_ITEMS,
                currentRoute = currentRoute ?: "home",
                onSelect = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }
    }
}
