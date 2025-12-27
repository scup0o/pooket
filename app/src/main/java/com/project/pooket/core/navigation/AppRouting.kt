package com.project.pooket.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.project.pooket.core.GlobalViewModel
import com.project.pooket.core.nightlight.NightLightOverlay
import com.project.pooket.ui.features.library.LibraryMainScreen
import com.project.pooket.ui.features.reader.ReaderScreen
import com.project.pooket.ui.features.setting.SettingMainScreen
import kotlinx.coroutines.launch

@Composable
fun AppRouting(
    navManager: NavigationManager,
    viewModel: GlobalViewModel,
    isDarkTheme : Boolean,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(navManager, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            navManager.commands.collect { command ->
                when (command) {
                    is NavigationManager.Command.Navigate -> {
                        navController.navigate(command.route) {
                            if (AppRoute.drawerRoutes.any { it.route == command.route }) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    is NavigationManager.Command.GoBack -> navController.popBackStack()
                    is NavigationManager.Command.OpenDrawer -> scope.launch { drawerState.open() }
                    else -> {}
                }
            }
        }
    }

    val nightLightConfig by viewModel.nightLightState.collectAsStateWithLifecycle()
//    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSwipeEnabled = AppRoute.getByRoute(currentRoute)?.allowDrawerSwipe == true

    ModalNavigationDrawer(
        gesturesEnabled = isSwipeEnabled,
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                onNavigate = { navManager.navigate(it); scope.launch { drawerState.close() } },
                closeDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = AppRoute.Library.route) {
                composable(AppRoute.Library.route) {
                    LibraryMainScreen(onOpenDrawer = { navManager.openDrawer() })
                }
                composable(AppRoute.Collection.route) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Collection Screen Placeholder")
                    }
                }
                composable(AppRoute.Search.route) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Search Screen Placeholder")
                    }
                }
                composable(AppRoute.Setting.route) {
                    SettingMainScreen(
                        nightLightConfig = nightLightConfig,
                        isDarkTheme = isDarkTheme,
                        onThemeChange = {viewModel.setTheme(it)},
                        onToggleNightLight = {viewModel.toggleNightLight()},
                        onWarmthChange = {viewModel.updateWarmth(it)},
                        onDimmingChange = {viewModel.updateDimming(it)},
                        onOpenDrawer = {navManager.openDrawer()}
                    )
                }

                composable(AppRoute.Reader.route, arguments = listOf(navArgument("bookUri"){type =
                    NavType.StringType
                })) { backStackEntry ->
                    val bookUriString = backStackEntry.arguments?.getString("bookUri")
                    if (bookUriString!=null){
                        ReaderScreen(
                            bookUri = bookUriString, isNightMode = isDarkTheme,
                            onBack = {navManager.goBack()}
                        )
                    }
                }

            }
        }
    }
    NightLightOverlay(
        isEnabled = nightLightConfig.isEnabled,
        warmth = nightLightConfig.warmth,
        dimming = nightLightConfig.dimming
    )
}