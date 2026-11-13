package ro.fitnesshub.server.stats

import ro.fitnesshub.server.MuscleVolume
import ro.fitnesshub.server.PersonalRecord
import ro.fitnesshub.server.WeekStats
import ro.fitnesshub.server.WeeklyStatsResponse
import ro.fitnesshub.server.WorkoutResponse
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The server's business logic: turns a user's workouts into weekly numbers.
 * Pure Kotlin with no database or HTTP, so it is tested directly with made-up workouts.
 * The PR rules are the same as in the app (Progression.prIndices).
 */
object WeeklyStats {

    /** Epley gets unreliable above roughly 12 reps, so those sets don't count towards the 1RM. */
    private const val MAX_REPS_FOR_1RM = 12

    /** Tiny tolerance so 80.0 vs 79.99999 doesn't count as a record. */
    private const val EPSILON = 0.01

    /**
     * @param workouts all of the user's workouts (older ones are needed to know what a record is)
     * @param weeks how many weeks to return, ending with the week that contains [today]
     * @param zone the user's time zone: it decides on which day (and week) a workout happened
     */
    fun compute(
        workouts: List<WorkoutResponse>,
        weeks: Int,
        today: LocalDate,
        zone: ZoneId,
        weeklyGoal: Int?,
    ): WeeklyStatsResponse {
        val thisWeek = weekStart(today)
        val firstWeek = thisWeek.minusWeeks(weeks - 1L)
        val sorted = workouts.sortedBy { it.startedAt }
        val prsByWeek = personalRecords(sorted, zone).groupBy { weekStart(LocalDate.parse(it.date)) }
        val workoutsByWeek = sorted.groupBy { weekStart(dateOf(it, zone)) }

        val result = (0 until weeks).map { i ->
            val week = firstWeek.plusWeeks(i.toLong())
            val inWeek = workoutsByWeek[week].orEmpty()
            val workingSets = inWeek.flatMap { it.sets }.filter { !it.isWarmup }
            WeekStats(
                weekStart = week.toString(),
                workouts = inWeek.size,
                goalMet = weeklyGoal?.let { inWeek.size >= it },
                totalVolumeKg = round1(workingSets.sumOf { it.weightKg * it.reps }),
                volumeByMuscle = workingSets.groupBy { it.primaryMuscle }
                    .map { (muscle, sets) ->
                        MuscleVolume(muscle, round1(sets.sumOf { it.weightKg * it.reps }), sets.size)
                    }
                    .sortedByDescending { it.volumeKg },
                personalRecords = prsByWeek[week].orEmpty(),
            )
        }
        return WeeklyStatsResponse(weeklyGoal = weeklyGoal, weeks = result)
    }

    /**
     * Goes through every working set in time order, remembering the best estimated 1RM and the
     * heaviest weight seen so far for each exercise. A set that beats either is a record, except
     * in the first workout with that exercise (otherwise every first set would be one).
     * Per exercise and week only the last record is kept, since it is the best one reached that week.
     */
    private fun personalRecords(sortedWorkouts: List<WorkoutResponse>, zone: ZoneId): List<PersonalRecord> {
        val best1Rm = mutableMapOf<String, Double>()
        val heaviest = mutableMapOf<String, Double>()
        val doneInEarlierWorkouts = mutableSetOf<String>()
        // Key: exercise + week, so a later record in the same week replaces an earlier one.
        val records = linkedMapOf<Pair<String, LocalDate>, PersonalRecord>()

        for (workout in sortedWorkouts) {
            val date = dateOf(workout, zone)
            for (set in workout.sets.sortedBy { it.setNumber }) {
                if (set.isWarmup) continue
                val exercise = set.exerciseName
                val e1Rm = estimated1Rm(set.weightKg, set.reps)
                if (exercise in doneInEarlierWorkouts) {
                    val isPr = (e1Rm != null && e1Rm > (best1Rm[exercise] ?: 0.0) + EPSILON) ||
                        set.weightKg > heaviest.getValue(exercise) + EPSILON
                    if (isPr) {
                        records[exercise to weekStart(date)] = PersonalRecord(
                            exerciseName = exercise,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            estimated1RmKg = e1Rm?.let(::round1),
                            date = date.toString(),
                        )
                    }
                }
                if (e1Rm != null) best1Rm[exercise] = max(best1Rm[exercise] ?: 0.0, e1Rm)
                heaviest[exercise] = max(heaviest[exercise] ?: 0.0, set.weightKg)
            }
            doneInEarlierWorkouts += workout.sets.filter { !it.isWarmup }.map { it.exerciseName }
        }
        return records.values.toList()
    }

    /** Epley: weight × (1 + reps / 30). Null when it isn't meaningful. */
    fun estimated1Rm(weightKg: Double, reps: Int): Double? = when {
        weightKg <= 0 || reps !in 1..MAX_REPS_FOR_1RM -> null
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    private fun dateOf(workout: WorkoutResponse, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(workout.startedAt).atZone(zone).toLocalDate()

    private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun round1(value: Double): Double = (value * 10).roundToLong() / 10.0
}
