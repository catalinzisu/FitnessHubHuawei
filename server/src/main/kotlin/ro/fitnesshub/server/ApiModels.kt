package ro.fitnesshub.server

import kotlinx.serialization.Serializable

// The JSON the app and the server exchange under /api. Requests and responses are separate
// classes where the server adds fields (id, updatedAt) the app must not choose itself.
// Times are epoch milliseconds.

// ---- Profile ----

/** GET and PUT /api/profile. Every field is optional; a new user gets an empty profile. */
@Serializable
data class ProfileDto(
    val displayName: String? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    /** Workouts per week the user aims for. */
    val weeklyGoal: Int? = null,
)

// ---- Training days (templates) ----

@Serializable
data class TemplateExerciseDto(
    val exerciseName: String,
    val primaryMuscle: String,
    val targetSets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int,
)

/** Body of POST /api/templates and PUT /api/templates/{id}. */
@Serializable
data class TemplateRequest(
    val name: String,
    /** Day names such as "MONDAY". */
    val weekdays: List<String> = emptyList(),
    val exercises: List<TemplateExerciseDto> = emptyList(),
)

@Serializable
data class TemplateResponse(
    val id: Long,
    val name: String,
    val weekdays: List<String>,
    val exercises: List<TemplateExerciseDto>,
    val updatedAt: Long,
)

// ---- Workouts ----

@Serializable
data class WorkoutSetDto(
    val exerciseName: String,
    val primaryMuscle: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
)

/** Body of POST /api/workouts: one finished workout from the app. */
@Serializable
data class WorkoutRequest(
    /** The workout's id in the app's database. Sending the same one again doesn't create a copy. */
    val clientId: Long,
    val title: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val kcal: Double? = null,
    val sets: List<WorkoutSetDto> = emptyList(),
)

@Serializable
data class WorkoutResponse(
    val id: Long,
    val clientId: Long,
    val title: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val kcal: Double? = null,
    val sets: List<WorkoutSetDto> = emptyList(),
)
