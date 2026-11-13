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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ro.fitnesshub.R
import ro.fitnesshub.ui.auth.LoginScreen
import ro.fitnesshub.ui.auth.RegisterScreen
import ro.fitnesshub.ui.auth.ResetPasswordScreen
import ro.fitnesshub.ui.common.ComingSoonScreen
import ro.fitnesshub.ui.dashboard.DashboardScreen
import ro.fitnesshub.ui.log.LogScreen
import ro.fitnesshub.ui.privacy.PrivacyPolicyScreen
import ro.fitnesshub.ui.settings.SettingsScreen
import ro.fitnesshub.ui.workout.ActiveWorkoutScreen
import ro.fitnesshub.ui.workout.ExerciseLibraryScreen
import ro.fitnesshub.ui.workout.ExerciseProgressScreen
import ro.fitnesshub.ui.workout.TemplateEditorScreen
import ro.fitnesshub.ui.workout.WorkoutHomeScreen

/** The bottom tabs. [graph] is the nested navigation graph that holds all screens of the tab. */
private enum class TopDestination(val graph: Any, @param:StringRes val label: Int, val icon: ImageVector) {
    Today(TodayGraph, R.string.nav_today, Icons.Filled.Home),
    Workout(WorkoutGraph, R.string.nav_workout, Icons.AutoMirrored.Filled.List),
    Coach(CoachGraph, R.string.nav_coach, Icons.Filled.Face),
    Settings(SettingsGraph, R.string.nav_settings, Icons.Filled.Settings),
}

/** Key used to hand a picked exercise back to the screen that opened the picker. */
private const val PICKED_EXERCISE = "picked_exercise_id"

/**
 * The whole app's navigation.
 * @param startAtSignIn true → the app opens on the sign-in screen (not signed in and
 *   "Continue without account" not chosen yet); false → it opens on Today.
 */
@Composable
fun AppNavHost(startAtSignIn: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // The sign-in screens are full screen: no tabs while signing in.
    val inAuth = currentDestination?.hierarchy?.any { it.hasRoute(AuthGraph::class) } == true

    Scaffold(
        bottomBar = {
            if (!inAuth) NavigationBar {
                TopDestination.entries.forEach { tab ->
                    // A tab stays selected on every screen inside its graph (e.g. a workout opened from the Workout tab).
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.graph::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        // Each screen draws its own top bar (which handles the status bar),
        // so only the bottom bar's space is reserved here.
        NavHost(
            navController = navController,
            startDestination = if (startAtSignIn) AuthGraph else TodayGraph,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            navigation<TodayGraph>(startDestination = TodayRoute) {
                composable<TodayRoute> { DashboardScreen() }
            }

            navigation<WorkoutGraph>(startDestination = WorkoutHomeRoute) {
                composable<WorkoutHomeRoute> {
                    WorkoutHomeScreen(
                        onOpenSession = { navController.navigateOrReturn(SessionRoute(it)) },
                        onEditTemplate = { navController.navigate(TemplateEditorRoute(it)) { launchSingleTop = true } },
                        onOpenLibrary = { navController.navigate(ExerciseLibraryRoute(pick = false)) { launchSingleTop = true } },
                    )
                }

                composable<ExerciseLibraryRoute> { entry ->
                    val route = entry.toRoute<ExerciseLibraryRoute>()
                    ExerciseLibraryScreen(
                        pickMode = route.pick,
                        onBack = { navController.popBackStackOnce(entry) },
                        onOpenExercise = { navController.navigateOrReturn(ExerciseProgressRoute(it)) },
                        onPicked = { id ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE, id)
                            navController.popBackStackOnce(entry)
                        },
                    )
                }

                composable<TemplateEditorRoute> { entry ->
                    val picked by pickedExercise(entry)
                    TemplateEditorScreen(
                        pickedExerciseId = picked,
                        onPickedConsumed = { entry.savedStateHandle[PICKED_EXERCISE] = null },
                        onAddExercise = { navController.navigate(ExerciseLibraryRoute(pick = true)) { launchSingleTop = true } },
                        onClose = { navController.popBackStackOnce(entry) },
                    )
                }

                composable<SessionRoute> { entry ->
                    val picked by pickedExercise(entry)
                    ActiveWorkoutScreen(
                        pickedExerciseId = picked,
                        onPickedConsumed = { entry.savedStateHandle[PICKED_EXERCISE] = null },
                        onAddExercise = { navController.navigate(ExerciseLibraryRoute(pick = true)) { launchSingleTop = true } },
                        onOpenExercise = { navController.navigateOrReturn(ExerciseProgressRoute(it)) },
                        onClose = { navController.popBackStackOnce(entry) },
                    )
                }

                composable<ExerciseProgressRoute> { entry ->
                    ExerciseProgressScreen(
                        onBack = { navController.popBackStackOnce(entry) },
                        onOpenSession = { navController.navigateOrReturn(SessionRoute(it)) },
                    )
                }
            }

            navigation<CoachGraph>(startDestination = CoachRoute) {
                composable<CoachRoute> { ComingSoonScreen(R.string.nav_coach, R.string.coming_soon_coach) }
            }

            navigation<SettingsGraph>(startDestination = SettingsRoute) {
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenLog = { navController.navigate(LogRoute) { launchSingleTop = true } },
                        onOpenPrivacy = { navController.navigate(PrivacyRoute) { launchSingleTop = true } },
                        onSignIn = { navController.navigate(LoginRoute(firstScreen = false)) { launchSingleTop = true } },
                        onSignedOut = { navController.showSignInAfterSignOut() },
                    )
                }
                composable<LogRoute> { entry -> LogScreen(onBack = { navController.popBackStackOnce(entry) }) }
                composable<PrivacyRoute> { entry -> PrivacyPolicyScreen(onBack = { navController.popBackStackOnce(entry) }) }
            }

            // When this graph is the start destination, the first screen is sign-in with firstScreen = true.
            navigation<AuthGraph>(startDestination = LoginRoute(firstScreen = true)) {
                composable<LoginRoute> { entry ->
                    val route = entry.toRoute<LoginRoute>()
                    LoginScreen(
                        onDone = { navController.leaveAuth(route.firstScreen) },
                        onRegister = {
                            navController.navigate(RegisterRoute(firstScreen = route.firstScreen)) { launchSingleTop = true }
                        },
                        onForgotPassword = { email -> navController.navigate(ResetPasswordRoute(email)) { launchSingleTop = true } },
                        onBack = if (route.firstScreen) null else ({ navController.popBackStackOnce(entry) }),
                    )
                }
                composable<RegisterRoute> { entry ->
                    val route = entry.toRoute<RegisterRoute>()
                    RegisterScreen(
                        onRegistered = { navController.leaveAuth(route.firstScreen) },
                        onBack = { navController.popBackStackOnce(entry) },
                    )
                }
                composable<ResetPasswordRoute> { entry ->
                    ResetPasswordScreen(onBack = { navController.popBackStackOnce(entry) })
                }
            }
        }
    }
}

/**
 * Switches tab the standard way: pop everything above the start screen (saving it), don't stack the
 * same tab twice, and restore where the user was inside the tab.
 */
private fun NavController.navigateToTab(tab: TopDestination) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Goes back to [route] if exactly that screen (same arguments) is already in the back stack,
 * otherwise opens it. Stops loops like workout → progress → the same workout from piling up.
 */
private fun <T : Any> NavController.navigateOrReturn(route: T) {
    if (!popBackStack(route, inclusive = false)) {
        navigate(route) { launchSingleTop = true }
    }
}

/**
 * Leaves the sign-in screens after signing in, registering or "Continue without account".
 * - Sign-in was the first screen: open Today and drop every sign-in screen from the back stack
 *   (popUpTo the sign-in graph, inclusive), so Back closes the app instead of showing sign-in again.
 * - Opened from Settings: just close the sign-in screens, which lands back in Settings.
 */
private fun NavController.leaveAuth(firstScreen: Boolean) {
    if (firstScreen) {
        navigate(TodayGraph) {
            popUpTo<AuthGraph> { inclusive = true }
        }
    } else {
        popBackStack<AuthGraph>(inclusive = true)
    }
}

/**
 * After signing out: empty the whole back stack (popUpTo the root graph, inclusive) and show
 * sign-in as the only screen. The tabs' saved places are forgotten too, so nothing of the
 * previous session can be reached with Back or by tapping a tab.
 */
private fun NavController.showSignInAfterSignOut() {
    navigate(LoginRoute(firstScreen = true)) {
        popUpTo(graph.id) { inclusive = true }
    }
    TopDestination.entries.forEach { clearBackStack(it.graph) }
}

@Composable
private fun pickedExercise(entry: NavBackStackEntry) =
    entry.savedStateHandle.getStateFlow<Long?>(PICKED_EXERCISE, null).collectAsStateWithLifecycle()

/** Pops [entry] only if it is still on top, so a double "close" can't pop the screen below. */
private fun NavController.popBackStackOnce(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}
