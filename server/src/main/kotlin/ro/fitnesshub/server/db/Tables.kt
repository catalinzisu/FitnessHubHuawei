package ro.fitnesshub.server.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// The server's database schema, written with Exposed: each `object` is one SQL table and each
// `val` is one column. Times are epoch milliseconds (Long), the same as in the app's Room database.
// Every row belongs to a user; deleting a user deletes all their data (ON DELETE CASCADE).

/** One row per account. [uid] is the Firebase user id from the login token (added in phase C). */
object Users : Table("users") {
    val uid = varchar("uid", 128)
    val email = varchar("email", 320).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(uid)
}

/** Optional details the user fills in. At most one per user. */
object Profiles : Table("profiles") {
    val userId = reference("user_id", Users.uid, onDelete = ReferenceOption.CASCADE)
    val displayName = varchar("display_name", 60).nullable()
    val heightCm = integer("height_cm").nullable()
    val weightKg = double("weight_kg").nullable()
    /** How many workouts per week the user aims for; used by the weekly stats. */
    val weeklyGoal = integer("weekly_goal").nullable()
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

/** A training day, e.g. "Push A" on Monday and Thursday. */
object Templates : Table("templates") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.uid, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 60)
    /** "MONDAY,THURSDAY", the same format the app stores. */
    val weekdays = varchar("weekdays", 80)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/** The exercises of a training day, in order. */
object TemplateExercises : Table("template_exercises") {
    val id = long("id").autoIncrement()
    val templateId = reference("template_id", Templates.id, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    // Exercises are stored by name and muscle: the server has no exercise library of its own.
    val exerciseName = varchar("exercise_name", 100)
    val primaryMuscle = varchar("primary_muscle", 30)
    val targetSets = integer("target_sets")
    val repsMin = integer("reps_min")
    val repsMax = integer("reps_max")
    val restSeconds = integer("rest_seconds")

    override val primaryKey = PrimaryKey(id)
}

/** A finished gym workout uploaded by the app. */
object Workouts : Table("workouts") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.uid, onDelete = ReferenceOption.CASCADE)
    /**
     * The workout's id in the app's own database. Unique per user, so uploading the same
     * workout twice (e.g. a retried sync) can be detected instead of creating a copy.
     */
    val clientId = long("client_id")
    val title = varchar("title", 60).nullable()
    val startedAt = long("started_at")
    val endedAt = long("ended_at")
    val avgHr = integer("avg_hr").nullable()
    val maxHr = integer("max_hr").nullable()
    val kcal = double("kcal").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, clientId)
    }
}

/** The sets of a workout. */
object WorkoutSets : Table("workout_sets") {
    val id = long("id").autoIncrement()
    val workoutId = reference("workout_id", Workouts.id, onDelete = ReferenceOption.CASCADE)
    val exerciseName = varchar("exercise_name", 100)
    val primaryMuscle = varchar("primary_muscle", 30)
    val setNumber = integer("set_number")
    val weightKg = double("weight_kg")
    val reps = integer("reps")
    val rpe = double("rpe").nullable()
    val isWarmup = bool("is_warmup")

    override val primaryKey = PrimaryKey(id)
}

/** Every table, parents before children (the order they are created in). */
val allTables = arrayOf(Users, Profiles, Templates, TemplateExercises, Workouts, WorkoutSets)
