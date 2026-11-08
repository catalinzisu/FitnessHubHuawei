package ro.fitnesshub.data.health

import java.time.Instant
import java.time.LocalDate

/**
 * One source of health data. Health Connect is the main one; the Huawei export
 * importer and manual entries will implement the same interface later.
 */
interface HealthDataSource {
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun readDailySummary(date: LocalDate): DailySummary?
    suspend fun readHeartRate(from: Instant, to: Instant): List<HeartRateSample>
    suspend fun readSleep(from: Instant, to: Instant): List<SleepSession>
    suspend fun readWorkouts(from: Instant, to: Instant): List<Workout>
}

/** Totals for one calendar day. Null means "no data or no permission". */
data class DailySummary(
    val date: LocalDate,
    val steps: Long?,
    val distanceMeters: Double?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val avgHr: Long?,
    val minHr: Long?,
    val maxHr: Long?,
    val restingHr: Long?,
    /** Sleep of the night that ended on [date] (18:00 the day before → 18:00). */
    val sleepMinutes: Long?,
    /** Package names of the apps that wrote the data, e.g. Gadgetbridge. */
    val sources: Set<String>,
)

data class HeartRateSample(val time: Instant, val bpm: Long, val source: String)

data class SleepSession(
    val start: Instant,
    val end: Instant,
    val stages: List<SleepStage>,
    val source: String,
)

data class SleepStage(val start: Instant, val end: Instant, val type: SleepStageType)

enum class SleepStageType { AWAKE, LIGHT, DEEP, REM, SLEEPING, UNKNOWN }

data class Workout(
    val start: Instant,
    val end: Instant,
    val title: String?,
    /** Health Connect exercise type constant (ExerciseSessionRecord.EXERCISE_TYPE_*). */
    val exerciseType: Int,
    val source: String,
)
