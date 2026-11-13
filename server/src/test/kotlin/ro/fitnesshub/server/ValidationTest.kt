package ro.fitnesshub.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rules themselves, without HTTP. */
class ValidationTest {

    private val now = 1_800_000_000_000L
    private val bench = TemplateExerciseDto("Bench Press", "chest", 3, 8, 12, 120)
    private val set = WorkoutSetDto("Bench Press", "chest", 1, 80.0, 8, rpe = 8.5)
    private val workout = WorkoutRequest(clientId = 1, startedAt = now - 3_600_000, endedAt = now, sets = listOf(set))

    @Test
    fun validBodies_haveNoProblems() {
        assertEquals(emptyList(), Validation.profile(ProfileDto("Alex", 180, 80.0, 3)))
        assertEquals(emptyList(), Validation.profile(ProfileDto())) // everything optional
        assertEquals(emptyList(), Validation.template(TemplateRequest("Push A", listOf("MONDAY"), listOf(bench))))
        assertEquals(emptyList(), Validation.workout(workout, now))
    }

    @Test
    fun profile_ranges() {
        val problems = Validation.profile(ProfileDto(displayName = "  ", heightCm = 20, weightKg = 500.0, weeklyGoal = 0))

        assertEquals(4, problems.size)
        assertTrue(problems.any { it.startsWith("displayName") })
        assertTrue(problems.any { it.startsWith("weeklyGoal") })
    }

    @Test
    fun template_namesDaysAndExercises() {
        val problems = Validation.template(
            TemplateRequest(
                name = "x".repeat(61),
                weekdays = listOf("MONDAY", "MONDAY", "Funday"),
                exercises = listOf(bench, bench.copy(primaryMuscle = "wings", repsMin = 10, repsMax = 8)),
            ),
        )

        assertTrue(problems.any { it.startsWith("name must be at most 60") })
        assertTrue(problems.any { it.contains("Funday") })
        assertTrue(problems.any { it.contains("repeat") })
        // The field path says which exercise is wrong.
        assertTrue(problems.contains("exercises[1].repsMax must be between repsMin and 50"))
        assertTrue(problems.any { it.startsWith("exercises[1].primaryMuscle") })
        assertTrue(problems.none { it.startsWith("exercises[0]") })
    }

    @Test
    fun workout_timesAndHeartRate() {
        fun problems(w: WorkoutRequest) = Validation.workout(w, now)

        assertTrue(problems(workout.copy(clientId = 0)).any { it.startsWith("clientId") })
        assertTrue(problems(workout.copy(endedAt = workout.startedAt - 1)).any { it.startsWith("endedAt") })
        assertTrue(problems(workout.copy(endedAt = workout.startedAt + 25 * 3_600_000L)).any { it.contains("24 hours") })
        assertTrue(problems(workout.copy(startedAt = now + 2 * 86_400_000L, endedAt = now + 2 * 86_400_000L)).any { it.contains("future") })
        assertTrue(problems(workout.copy(avgHr = 300)).any { it.startsWith("avgHr") })
    }

    @Test
    fun workout_sets() {
        val bad = listOf(
            set.copy(reps = 0),
            set.copy(weightKg = -5.0),
            set.copy(rpe = 8.3),
            set.copy(exerciseName = ""),
            set.copy(weightKg = Double.NaN),
        )
        val problems = Validation.workout(workout.copy(sets = bad), now)

        assertEquals(
            listOf("sets[0].reps", "sets[1].weightKg", "sets[2].rpe", "sets[3].exerciseName", "sets[4].weightKg"),
            problems.map { it.substringBefore(' ') },
        )
    }

    @Test
    fun tooManyItems_isRejected() {
        val many = List(Validation.MAX_TEMPLATE_EXERCISES + 1) { bench }

        assertTrue(Validation.template(TemplateRequest("Big", exercises = many)).any { it.startsWith("exercises: at most") })
    }
}
