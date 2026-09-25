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
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.SchoolApp
import com.school.hub.feature.ai.ui.AiChatScreen
import com.school.hub.feature.ai.ui.AiModelsScreen
import com.school.hub.feature.focus.FocusScreen
import com.school.hub.feature.grades.GradesScreen
import com.school.hub.feature.homework.ui.HomeworkScreen
import com.school.hub.feature.schedule.ui.ScheduleScreen
import com.school.hub.feature.settings.SettingsScreen
import com.school.hub.feature.translator.CameraTranslateScreen
import com.school.hub.feature.translator.TranslatorScreen
import com.school.hub.feature.translator.TranslatorViewModel
import androidx.compose.ui.platform.LocalContext
import com.school.hub.sync.SyncScreen
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.school.hub.feature.admin.AdminScreen
import com.school.hub.feature.auth.AuthGate
import com.school.hub.feature.auth.AuthState
import com.school.hub.feature.browser.MiniBrowserScreen
import com.school.hub.feature.calc.CalculatorScreen
import com.school.hub.feature.games.GamesScreen
import com.school.hub.feature.social.GlobalChatScreen

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val adminDest = TopDest(Routes.ADMIN, "Админ", Icons.Filled.AdminPanelSettings)

private val topDestinations = listOf(
    TopDest(Routes.HOME, "Главная", Icons.Filled.Home),
    TopDest(Routes.CHEATS, "Шпоры", Icons.Filled.Lightbulb),
    TopDest(Routes.SCHEDULE, "Уроки", Icons.Filled.Schedule),
    TopDest(Routes.HOMEWORK, "ДЗ", Icons.Filled.TaskAlt),
    TopDest(Routes.AI, "ИИ", Icons.Filled.AutoAwesome),
)

@Composable
fun SchoolHubApp() {
    val container = (LocalContext.current.applicationContext as SchoolApp).container
    val authState by container.auth.state.collectAsStateWithLifecycle()
    val ready = authState as? AuthState.Ready
    if (ready == null) { AuthGate(authState); return }
    MainApp(isAdmin = ready.profile.isAdmin)
}

@Composable
private fun MainApp(isAdmin: Boolean) {
    val container = (LocalContext.current.applicationContext as SchoolApp).container
    val dests = if (isAdmin) topDestinations + adminDest else topDestinations
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = dests.any { it.route == currentRoute }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    dests.forEach { dest ->
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
                        if (dests.any { it.route == route }) nav.navigateTopLevel(route) else nav.navigate(route)
                    },
                    onOpenCheat = { nav.navigate(Routes.cheatDetail(it)) },
                )
            }
            composable(Routes.CHEATS) {
                CheatSheetListScreen(
                    onOpen = { nav.navigate(Routes.cheatDetail(it)) },
                    onAdd = { nav.navigate(Routes.cheatEdit()) },
                    onOpenSync = { nav.navigate(Routes.SYNC) },
                    onOpenChat = { nav.navigate(Routes.CHAT) },
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
            composable(Routes.SCHEDULE) { ScheduleScreen(onOpenSettings = { nav.navigate(Routes.SETTINGS) }) }
            composable(Routes.HOMEWORK) { HomeworkScreen() }
            composable(Routes.AI) {
                AiModelsScreen(
                    onOpenChat = { nav.navigate(Routes.aiChat(it)) },
                    hasPendingPrompt = container.pendingAiPrompt != null,
                )
            }
            composable(Routes.AI_CHAT, arguments = listOf(navArgument("modelId") { type = NavType.StringType })) {
                AiChatScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.TRANSLATOR) {
                TranslatorScreen(onOpenCamera = { nav.navigate(Routes.CAMERA) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.CAMERA) { entry ->
                // Та же ViewModel, что у переводчика: общие языки и модели
                val parent = remember(entry) { nav.getBackStackEntry(Routes.TRANSLATOR) }
                val vm: TranslatorViewModel = viewModel(parent, factory = AppViewModelFactory.Factory)
                CameraTranslateScreen(vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.GRADES) { GradesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.FOCUS) {
                FocusScreen(container.focusTimer, onBack = { nav.popBackStack() })
            }
            composable(Routes.CALC) { CalculatorScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.GAMES) { GamesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.BROWSER) { MiniBrowserScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.CHAT) { GlobalChatScreen(onBack = { nav.popBackStack() }) }
            if (isAdmin) composable(Routes.ADMIN) { AdminScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() }, onOpenSync = { nav.navigate(Routes.SYNC) })
            }
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
