package com.school.hub.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.school.hub.feature.cheatsheets.ui.CheatSheetDetailScreen
import com.school.hub.feature.cheatsheets.ui.CheatSheetEditScreen
import com.school.hub.feature.cheatsheets.ui.CheatSheetListScreen
import com.school.hub.feature.home.HomeScreen
import com.school.hub.feature.soon.ComingSoonScreen
import com.school.hub.feature.soon.Upcoming
import com.school.hub.sync.SyncScreen

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val topDestinations = listOf(
    TopDest(Routes.HOME, "Главная", Icons.Filled.Home),
    TopDest(Routes.CHEATS, "Шпоры", Icons.Filled.Lightbulb),
    TopDest(Routes.SCHEDULE, "Уроки", Icons.Filled.Schedule),
    TopDest(Routes.HOMEWORK, "ДЗ", Icons.Filled.TaskAlt),
    TopDest(Routes.AI, "ИИ", Icons.Filled.AutoAwesome),
)

@Composable
fun SchoolHubApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topDestinations.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    topDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { nav.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigate = { route ->
                        if (topDestinations.any { it.route == route }) nav.navigateTopLevel(route) else nav.navigate(route)
                    },
                    onOpenCheat = { nav.navigate(Routes.cheatDetail(it)) },
                )
            }
            composable(Routes.CHEATS) {
                CheatSheetListScreen(
                    onOpen = { nav.navigate(Routes.cheatDetail(it)) },
                    onAdd = { nav.navigate(Routes.cheatEdit()) },
                    onOpenSync = { nav.navigate(Routes.SYNC) },
                )
            }
            composable(
                Routes.CHEAT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                CheatSheetDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.cheatEdit(it)) },
                )
            }
            composable(
                Routes.CHEAT_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) {
                CheatSheetEditScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable(Routes.SYNC) { SyncScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SCHEDULE) { ComingSoonScreen(Upcoming.SCHEDULE) }
            composable(Routes.HOMEWORK) { ComingSoonScreen(Upcoming.HOMEWORK) }
            composable(Routes.AI) { ComingSoonScreen(Upcoming.AI) }
            composable(Routes.TRANSLATOR) { ComingSoonScreen(Upcoming.TRANSLATOR) }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
