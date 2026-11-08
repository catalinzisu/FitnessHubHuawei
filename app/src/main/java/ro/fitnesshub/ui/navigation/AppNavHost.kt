package ro.fitnesshub.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ro.fitnesshub.R
import ro.fitnesshub.ui.common.ComingSoonScreen
import ro.fitnesshub.ui.dashboard.DashboardScreen
import ro.fitnesshub.ui.log.LogScreen
import ro.fitnesshub.ui.privacy.PrivacyPolicyScreen
import ro.fitnesshub.ui.settings.SettingsScreen

private enum class TopDestination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    Today("today", R.string.nav_today, Icons.Filled.Home),
    Workout("workout", R.string.nav_workout, Icons.AutoMirrored.Filled.List),
    Coach("coach", R.string.nav_coach, Icons.Filled.Face),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

private object Routes {
    const val LOG = "log"
    const val PRIVACY = "privacy"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        // Each screen draws its own top bar (which handles the status bar),
        // so only the bottom bar's space is reserved here.
        NavHost(
            navController = navController,
            startDestination = TopDestination.Today.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(TopDestination.Today.route) { DashboardScreen() }
            composable(TopDestination.Workout.route) {
                ComingSoonScreen(R.string.nav_workout, R.string.coming_soon_workout)
            }
            composable(TopDestination.Coach.route) {
                ComingSoonScreen(R.string.nav_coach, R.string.coming_soon_coach)
            }
            composable(TopDestination.Settings.route) {
                SettingsScreen(
                    onOpenLog = { navController.navigate(Routes.LOG) },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                )
            }
            composable(Routes.LOG) { LogScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
