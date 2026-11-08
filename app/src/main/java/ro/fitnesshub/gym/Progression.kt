package ro.fitnesshub.gym

import kotlin.math.max

/** One working set, stripped down to what progress maths needs. */
data class LiftSet(val weightKg: Double, val reps: Int) {
    val volumeKg: Double get() = weightKg * reps
}

/** Pure progress maths: estimated 1RM, records, PRs and the next-session hint. Unit-tested. */
object Progression {

    /** Epley gets unreliable above roughly 12 reps, so those sets don't count towards the 1RM. */
    const val MAX_REPS_FOR_1RM = 12

    /** Tiny tolerance so 80.0 vs 79.99999 doesn't count as a record. */
    private const val EPSILON = 0.01

    fun estimated1Rm(set: LiftSet): Double? =
        if (set.weightKg > 0 && set.reps in 1..MAX_REPS_FOR_1RM) WorkoutFormat.epley1Rm(set.weightKg, set.reps) else null

    /** One finished session of one exercise, as a chart point. */
    data class SessionPoint(
        val sessionId: Long,
        val startedAt: Long,
        val sets: List<LiftSet>,
    ) {
        val best1Rm: Double? get() = sets.mapNotNull(::estimated1Rm).maxOrNull()
        val volumeKg: Double get() = sets.sumOf { it.volumeKg }

        /** Heaviest set; more reps wins a tie. */
        val topSet: LiftSet get() = sets.maxWith(compareBy<LiftSet> { it.weightKg }.thenBy { it.reps })
    }

    data class Records(
        val best1Rm: Double?,
        val heaviest: LiftSet?,
        val bestVolumeKg: Double?,
        val sessions: Int,
    )

    fun records(points: List<SessionPoint>): Records = Records(
        best1Rm = points.mapNotNull { it.best1Rm }.maxOrNull(),
        heaviest = points.map { it.topSet }.maxWithOrNull(compareBy<LiftSet> { it.weightKg }.thenBy { it.reps }),
        bestVolumeKg = points.maxOfOrNull { it.volumeKg },
        sessions = points.size,
    )

    /**
     * Which of today's working sets set a record when they were logged: a higher estimated 1RM
     * or a heavier weight than everything before (history plus earlier sets today).
     * The first time an exercise is done nothing is a PR, otherwise every first set would be one.
     */
    fun prIndices(today: List<LiftSet>, history: List<LiftSet>): Set<Int> {
        if (history.isEmpty()) return emptySet()
        var best1Rm = history.mapNotNull(::estimated1Rm).maxOrNull() ?: 0.0
        var heaviest = history.maxOf { it.weightKg }
        val result = mutableSetOf<Int>()
        today.forEachIndexed { index, set ->
            val e1Rm = estimated1Rm(set)
            val isPr = (e1Rm != null && e1Rm > best1Rm + EPSILON) || set.weightKg > heaviest + EPSILON
            if (isPr) result += index
            if (e1Rm != null) best1Rm = max(best1Rm, e1Rm)
            heaviest = max(heaviest, set.weightKg)
        }
        return result
    }

    /** What to aim for next time, using double progression inside the target rep range. */
    sealed interface Hint {
        val weightKg: Double
        val reps: Int

        /** Every set reached the top of the range: go heavier, back at the bottom of the range. */
        data class AddWeight(override val weightKg: Double, override val reps: Int) : Hint

        /** Inside the range: same weight, one more rep on the weakest set. */
        data class AddReps(override val weightKg: Double, override val reps: Int) : Hint

        /** Some set fell below the range: same weight, reach the bottom of the range first. */
        data class Repeat(override val weightKg: Double, override val reps: Int) : Hint
    }

    /** Smallest sensible jump for the equipment; 0 means "progress with reps" (bodyweight). */
    fun increment(equipment: String): Double = when (equipment) {
        "barbell", "cable" -> 2.5
        "dumbbell" -> 2.0
        "machine" -> 5.0
        else -> 0.0
    }

    /**
     * Looks only at the sets done at last session's top weight
     * (lighter back-off sets don't decide whether the top weight was "beaten").
     */
    fun hint(previous: List<LiftSet>, repsMin: Int, repsMax: Int, equipment: String): Hint? {
        if (previous.isEmpty() || repsMin < 1 || repsMax < repsMin) return null
        val top = previous.maxOf { it.weightKg }
        val atTop = previous.filter { it.weightKg >= top - EPSILON }
        val weakest = atTop.minOf { it.reps }
        val step = if (top > 0) increment(equipment) else 0.0
        return when {
            weakest >= repsMax && step > 0 -> Hint.AddWeight(top + step, repsMin)
            weakest >= repsMax -> Hint.AddReps(top, weakest + 1)
            weakest >= repsMin -> Hint.AddReps(top, weakest + 1)
            else -> Hint.Repeat(top, repsMin)
        }
    }
}
