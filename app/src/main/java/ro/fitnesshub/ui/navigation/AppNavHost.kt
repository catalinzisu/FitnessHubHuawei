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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ro.fitnesshub.R
import ro.fitnesshub.ui.common.ComingSoonScreen
import ro.fitnesshub.ui.dashboard.DashboardScreen
import ro.fitnesshub.ui.log.LogScreen
import ro.fitnesshub.ui.privacy.PrivacyPolicyScreen
import ro.fitnesshub.ui.settings.SettingsScreen
import ro.fitnesshub.ui.workout.ActiveWorkoutScreen
import ro.fitnesshub.ui.workout.ActiveWorkoutViewModel
import ro.fitnesshub.ui.workout.ExerciseLibraryScreen
import ro.fitnesshub.ui.workout.TemplateEditorScreen
import ro.fitnesshub.ui.workout.TemplateEditorViewModel
import ro.fitnesshub.ui.workout.WorkoutHomeScreen

private enum class TopDestination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    Today("today", R.string.nav_today, Icons.Filled.Home),
    Workout("workout", R.string.nav_workout, Icons.AutoMirrored.Filled.List),
    Coach("coach", R.string.nav_coach, Icons.Filled.Face),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

private object Routes {
    const val LOG = "log"
    const val PRIVACY = "privacy"
    const val EXERCISES = "exercises?pick={pick}"
    const val TEMPLATE = "template/{${TemplateEditorViewModel.ARG_TEMPLATE_ID}}"
    const val SESSION = "session/{${ActiveWorkoutViewModel.ARG_SESSION_ID}}"

    fun exercises(pick: Boolean) = "exercises?pick=$pick"
    fun template(id: Long) = "template/$id"
    fun session(id: Long) = "session/$id"
}

/** Key used to hand a picked exercise back to the screen that opened the picker. */
private const val PICKED_EXERCISE = "picked_exercise_id"

/** Which bottom tab a route belongs to, so the tab stays highlighted on nested screens. */
private fun tabOf(route: String?): TopDestination? = when {
    route == null -> null
    route.startsWith("exercises") || route.startsWith("template") || route.startsWith("session") -> TopDestination.Workout
    route == Routes.LOG || route == Routes.PRIVACY -> TopDestination.Settings
    else -> TopDestination.entries.firstOrNull { it.route == route }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = tabOf(backStackEntry?.destination?.route)

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentTab == destination,
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
                WorkoutHomeScreen(
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                    onEditTemplate = { navController.navigate(Routes.template(it)) },
                    onOpenLibrary = { navController.navigate(Routes.exercises(pick = false)) },
                )
            }

            composable(
                Routes.EXERCISES,
                arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                ExerciseLibraryScreen(
                    pickMode = entry.arguments?.getBoolean("pick") ?: false,
                    onBack = { navController.popBackStack() },
                    onPicked = { id ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE, id)
                        navController.popBackStack()
                    },
                )
            }

            composable(
                Routes.TEMPLATE,
                arguments = listOf(navArgument(TemplateEditorViewModel.ARG_TEMPLATE_ID) { type = NavType.LongType }),
            ) { entry ->
                val picked by pickedExercise(entry)
                TemplateEditorScreen(
                    pickedExerciseId = picked,
                    onPickedConsumed = { entry.savedStateHandle[PICKED_EXERCISE] = null },
                    onAddExercise = { navController.navigate(Routes.exercises(pick = true)) },
                    onClose = { navController.popBackStackOnce(entry) },
                )
            }

            composable(
                Routes.SESSION,
                arguments = listOf(navArgument(ActiveWorkoutViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
            ) { entry ->
                val picked by pickedExercise(entry)
                ActiveWorkoutScreen(
                    pickedExerciseId = picked,
                    onPickedConsumed = { entry.savedStateHandle[PICKED_EXERCISE] = null },
                    onAddExercise = { navController.navigate(Routes.exercises(pick = true)) },
                    onClose = { navController.popBackStackOnce(entry) },
                )
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

@Composable
private fun pickedExercise(entry: NavBackStackEntry) =
    entry.savedStateHandle.getStateFlow<Long?>(PICKED_EXERCISE, null).collectAsStateWithLifecycle()

/** Pops [entry] only if it is still on top, so a double "close" can't pop the screen below. */
private fun NavController.popBackStackOnce(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}
