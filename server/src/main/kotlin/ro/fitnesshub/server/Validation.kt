package ro.fitnesshub.server

import java.time.DayOfWeek

/**
 * Rules for request bodies. The server never trusts the app: anything can send it JSON.
 * Each function returns the list of problems (empty = valid), with the field named in each one,
 * e.g. "exercises[1].repsMax must be between repsMin and 50". The limits match the app's.
 * Pure functions, so they are unit-tested without HTTP (ValidationTest).
 */
object Validation {

    /** Same muscle names as the app's exercise library. */
    val MUSCLES = setOf("chest", "back", "shoulders", "biceps", "triceps", "quads", "hamstrings", "glutes", "calves", "core")

    const val MAX_NAME = 60
    const val MAX_EXERCISE_NAME = 100
    const val MAX_TEMPLATE_EXERCISES = 30
    const val MAX_WORKOUT_SETS = 500
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun profile(p: ProfileDto): List<String> = buildList {
        p.displayName?.let { text("displayName", it, MAX_NAME) }
        p.heightCm?.let { range("heightCm", it, 100..250) }
        p.weightKg?.let { range("weightKg", it, 30.0..300.0) }
        p.weeklyGoal?.let { range("weeklyGoal", it, 1..14) }
    }

    fun template(t: TemplateRequest): List<String> = buildList {
        text("name", t.name, MAX_NAME)
        val unknownDays = t.weekdays.filter { day -> DayOfWeek.entries.none { it.name == day } }
        if (unknownDays.isNotEmpty()) add("weekdays: unknown day(s) $unknownDays, use e.g. \"MONDAY\"")
        if (t.weekdays.size != t.weekdays.toSet().size) add("weekdays must not repeat a day")
        if (t.exercises.size > MAX_TEMPLATE_EXERCISES) add("exercises: at most $MAX_TEMPLATE_EXERCISES")
        t.exercises.forEachIndexed { i, e ->
            val at = "exercises[$i]"
            text("$at.exerciseName", e.exerciseName, MAX_EXERCISE_NAME)
            muscle("$at.primaryMuscle", e.primaryMuscle)
            range("$at.targetSets", e.targetSets, 1..10)
            range("$at.repsMin", e.repsMin, 1..50)
            if (e.repsMax !in e.repsMin..50) add("$at.repsMax must be between repsMin and 50")
            range("$at.restSeconds", e.restSeconds, 0..600)
        }
    }

    /** [now] is passed in so tests can fix the time. */
    fun workout(w: WorkoutRequest, now: Long = System.currentTimeMillis()): List<String> = buildList {
        if (w.clientId <= 0) add("clientId must be positive")
        w.title?.let { text("title", it, MAX_NAME) }
        if (w.startedAt <= 0) add("startedAt must be a time in epoch milliseconds")
        if (w.endedAt < w.startedAt) add("endedAt must not be before startedAt")
        if (w.endedAt - w.startedAt > DAY_MS) add("a workout can't last more than 24 hours")
        if (w.startedAt > now + DAY_MS) add("startedAt is in the future")
        w.avgHr?.let { range("avgHr", it, 30..250) }
        w.maxHr?.let { range("maxHr", it, 30..250) }
        w.kcal?.let { range("kcal", it, 0.0..10_000.0) }
        if (w.sets.size > MAX_WORKOUT_SETS) add("sets: at most $MAX_WORKOUT_SETS")
        w.sets.forEachIndexed { i, s ->
            val at = "sets[$i]"
            text("$at.exerciseName", s.exerciseName, MAX_EXERCISE_NAME)
            muscle("$at.primaryMuscle", s.primaryMuscle)
            range("$at.setNumber", s.setNumber, 1..1000)
            range("$at.weightKg", s.weightKg, 0.0..1000.0)
            range("$at.reps", s.reps, 1..100)
            s.rpe?.let { if (it !in 1.0..10.0 || it * 2 != Math.rint(it * 2)) add("$at.rpe must be 1–10 in steps of 0.5") }
        }
    }

    // ---- Small helpers that add a message when a rule is broken ----

    private fun MutableList<String>.text(field: String, value: String, max: Int) {
        val length = value.trim().length
        if (length == 0) add("$field must not be empty")
        if (length > max) add("$field must be at most $max characters")
    }

    private fun MutableList<String>.range(field: String, value: Int, allowed: IntRange) {
        if (value !in allowed) add("$field must be between ${allowed.first} and ${allowed.last}")
    }

    private fun MutableList<String>.range(field: String, value: Double, allowed: ClosedFloatingPointRange<Double>) {
        if (value.isNaN() || value !in allowed) add("$field must be between ${allowed.start} and ${allowed.endInclusive}")
    }

    private fun MutableList<String>.muscle(field: String, value: String) {
        if (value !in MUSCLES) add("$field must be one of $MUSCLES")
    }
}
