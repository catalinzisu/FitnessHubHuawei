package ro.fitnesshub.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation: every screen is a @Serializable class or object instead of a "route/{arg}"
// string. Arguments are normal properties, so the compiler checks their names and types, and a
// screen's ViewModel reads them with SavedStateHandle.toRoute<...>().

// ---- One nested graph per bottom tab. The selected tab is the graph the current screen is in. ----

@Serializable object TodayGraph

@Serializable object WorkoutGraph

@Serializable object CoachGraph

@Serializable object SettingsGraph

// ---- Today tab ----

@Serializable object TodayRoute

// ---- Workout tab ----

@Serializable object WorkoutHomeRoute

/** Exercise library. With [pick] = true, tapping an exercise returns it to the previous screen. */
@Serializable data class ExerciseLibraryRoute(val pick: Boolean = false)

/** Training day editor. [templateId] = 0 creates a new training day. */
@Serializable data class TemplateEditorRoute(val templateId: Long)

/** A workout: in progress (editable) or finished (read-only history). */
@Serializable data class SessionRoute(val sessionId: Long)

/** Progress charts and records of one exercise. */
@Serializable data class ExerciseProgressRoute(val exerciseId: Long)

// ---- Coach tab ----

@Serializable object CoachRoute

// ---- Sign-in screens: their own graph, shown without the bottom bar ----

@Serializable object AuthGraph

@Serializable object LoginRoute

@Serializable object RegisterRoute

/** [email] pre-fills the form with what was typed on the sign-in screen. */
@Serializable data class ResetPasswordRoute(val email: String = "")

// ---- Settings tab ----

@Serializable object SettingsRoute

@Serializable object LogRoute

@Serializable object PrivacyRoute
