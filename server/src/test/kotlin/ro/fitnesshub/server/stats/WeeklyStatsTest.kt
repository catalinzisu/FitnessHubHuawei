package ro.fitnesshub.server.stats

import ro.fitnesshub.server.WorkoutResponse
import ro.fitnesshub.server.WorkoutSetDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklyStatsTest {

    // Friday 13 Nov 2026; its week starts on Monday 9 Nov.
    private val today = LocalDate.of(2026, 11, 13)
    private val utc = ZoneOffset.UTC
    private var nextId = 1L

    private fun workout(start: String, vararg sets: WorkoutSetDto): WorkoutResponse {
        val startedAt = LocalDateTime.parse(start).toInstant(utc).toEpochMilli()
        val id = nextId++
        return WorkoutResponse(id = id, clientId = id, startedAt = startedAt, endedAt = startedAt + 3_600_000, sets = sets.toList())
    }

    private fun bench(n: Int, kg: Double, reps: Int, warmup: Boolean = false) =
        WorkoutSetDto("Bench Press", "chest", n, kg, reps, isWarmup = warmup)

    private fun squat(n: Int, kg: Double, reps: Int) = WorkoutSetDto("Squat", "quads", n, kg, reps)

    private fun compute(vararg workouts: WorkoutResponse, weeks: Int = 2, goal: Int? = null, zone: ZoneId = utc) =
        WeeklyStats.compute(workouts.toList(), weeks, today, zone, goal)

    @Test
    fun emptyWeeksAreStillListed_oldestFirst() {
        val stats = compute(weeks = 3)

        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09"), stats.weeks.map { it.weekStart })
        assertTrue(stats.weeks.all { it.workouts == 0 && it.totalVolumeKg == 0.0 && it.personalRecords.isEmpty() })
    }

    @Test
    fun volume_countsWorkingSetsOnly_perMuscle() {
        val stats = compute(
            workout("2026-11-10T18:00", bench(1, 40.0, 10, warmup = true), bench(2, 80.0, 8), bench(3, 80.0, 7)),
            workout("2026-11-12T18:00", squat(1, 100.0, 5)),
        )

        val week = stats.weeks.last()
        assertEquals(2, week.workouts)
        assertEquals(80.0 * 8 + 80.0 * 7 + 100.0 * 5, week.totalVolumeKg) // the 40 kg warm-up doesn't count
        assertEquals(listOf("chest", "quads"), week.volumeByMuscle.map { it.muscle }) // biggest first
        assertEquals(2, week.volumeByMuscle.first().workingSets)
    }

    @Test
    fun workoutsOutsideTheWindow_areLeftOut() {
        val stats = compute(workout("2026-10-01T18:00", bench(1, 80.0, 8)), weeks = 2)

        assertTrue(stats.weeks.all { it.workouts == 0 })
    }

    @Test
    fun goal_isMetOrNot_orUnknown() {
        val one = workout("2026-11-10T18:00", bench(1, 80.0, 8))

        assertEquals(listOf(false, false), compute(one, goal = 2).weeks.map { it.goalMet })
        assertEquals(true, compute(one, goal = 1).weeks.last().goalMet)
        assertNull(compute(one).weeks.last().goalMet)
    }

    @Test
    fun firstWorkoutWithAnExercise_hasNoRecords() {
        val stats = compute(workout("2026-11-10T18:00", bench(1, 60.0, 8), bench(2, 80.0, 8)))

        assertTrue(stats.weeks.last().personalRecords.isEmpty())
    }

    @Test
    fun heavierOrStronger_isARecord_inTheWeekItHappened() {
        val stats = compute(
            workout("2026-11-03T18:00", bench(1, 80.0, 8)), // last week: first time
            workout("2026-11-10T18:00", bench(1, 80.0, 9)), // more reps, same weight: higher 1RM
        )

        assertTrue(stats.weeks.first().personalRecords.isEmpty())
        val pr = stats.weeks.last().personalRecords.single()
        assertEquals("Bench Press", pr.exerciseName)
        assertEquals(80.0, pr.weightKg)
        assertEquals(9, pr.reps)
        assertEquals(104.0, pr.estimated1RmKg) // 80 × (1 + 9/30)
        assertEquals("2026-11-10", pr.date)
    }

    @Test
    fun sameOrWorse_isNotARecord_andWarmupsNeverAre() {
        val stats = compute(
            workout("2026-11-03T18:00", bench(1, 80.0, 8)),
            workout("2026-11-10T18:00", bench(1, 100.0, 3, warmup = true), bench(2, 80.0, 8), bench(3, 75.0, 8)),
        )

        assertTrue(stats.weeks.last().personalRecords.isEmpty())
    }

    @Test
    fun severalRecordsInOneWeek_keepTheLastOnePerExercise() {
        val stats = compute(
            workout("2026-11-03T18:00", bench(1, 80.0, 8), squat(1, 100.0, 5)),
            workout("2026-11-10T18:00", bench(1, 82.5, 8), squat(1, 105.0, 5)),
            workout("2026-11-12T18:00", bench(1, 85.0, 8)),
        )

        val prs = stats.weeks.last().personalRecords
        assertEquals(2, prs.size)
        assertEquals(85.0, prs.single { it.exerciseName == "Bench Press" }.weightKg)
        assertEquals(105.0, prs.single { it.exerciseName == "Squat" }.weightKg)
    }

    @Test
    fun highReps_countByWeightOnly() {
        val stats = compute(
            workout("2026-11-03T18:00", bench(1, 60.0, 15)),
            workout("2026-11-10T18:00", bench(1, 62.5, 15)),
        )

        val pr = stats.weeks.last().personalRecords.single()
        assertEquals(62.5, pr.weightKg)
        assertNull(pr.estimated1RmKg) // Epley isn't used above 12 reps
    }

    @Test
    fun timeZone_decidesTheWeek() {
        // Sunday 8 Nov, 23:30 UTC is already Monday 9 Nov in Bucharest (UTC+2).
        val late = workout("2026-11-08T23:30", bench(1, 80.0, 8))

        assertEquals(listOf(1, 0), compute(late, zone = utc).weeks.map { it.workouts })
        assertEquals(listOf(0, 1), compute(late, zone = ZoneId.of("Europe/Bucharest")).weeks.map { it.workouts })
    }

    @Test
    fun epley() {
        assertEquals(100.0, WeeklyStats.estimated1Rm(100.0, 1))
        assertEquals(80.0 * (1 + 8 / 30.0), WeeklyStats.estimated1Rm(80.0, 8))
        assertNull(WeeklyStats.estimated1Rm(80.0, 13))
        assertNull(WeeklyStats.estimated1Rm(0.0, 10))
    }
}
