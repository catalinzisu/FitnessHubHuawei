package ro.fitnesshub.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import ro.fitnesshub.util.AppLog
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * Debug tool: writes realistic fake data into Health Connect so every screen can be
 * built on the emulator without the watch. The last 10 days have shorter sleep and
 * a slightly higher resting heart rate, which the AI coach can pick up later.
 *
 * Health Connect only lets an app delete records it wrote itself, so [clear] never
 * touches the watch data that Gadgetbridge wrote.
 */
class SeedDataWriter(private val context: Context) {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasWritePermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(HealthPermissions.SEED_WRITE)

    /** Replaces any earlier test data with [days] fresh days. Returns the number of records written. */
    suspend fun seed(days: Int = DEFAULT_DAYS): Int {
        clear()
        val records = generate(days, ZoneId.systemDefault(), Instant.now())
        records.chunked(CHUNK_SIZE).forEach { client.insertRecords(it) }
        AppLog.i(TAG, "Seeded ${records.size} records for $days days")
        return records.size
    }

    suspend fun clear() {
        val now = Instant.now()
        val range = TimeRangeFilter.between(now.minus(Duration.ofDays(CLEAR_WINDOW_DAYS)), now.plus(Duration.ofDays(1)))
        RECORD_TYPES.forEach { client.deleteRecords(it, range) }
        AppLog.i(TAG, "Deleted test records of ${RECORD_TYPES.size} types")
    }

    companion object {
        private const val TAG = "Seed"
        private const val DEFAULT_DAYS = 21
        private const val CHUNK_SIZE = 200
        private const val CLEAR_WINDOW_DAYS = 90L

        val RECORD_TYPES: List<KClass<out Record>> = listOf(
            StepsRecord::class,
            DistanceRecord::class,
            ActiveCaloriesBurnedRecord::class,
            TotalCaloriesBurnedRecord::class,
            HeartRateRecord::class,
            RestingHeartRateRecord::class,
            SleepSessionRecord::class,
        )

        private val GYM_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        private const val GYM_HOUR = 18

        /** Pure function so it can be unit-tested. Only produces records that end before [now]. */
        fun generate(days: Int, zone: ZoneId, now: Instant): List<Record> {
            // LocalDate.ofInstant needs API 34, so go through ZonedDateTime (API 26).
            val today = now.atZone(zone).toLocalDate()
            val records = mutableListOf<Record>()
            fun offset(instant: Instant) = zone.rules.getOffset(instant)

            for (daysAgo in (days - 1) downTo 0) {
                val date = today.minusDays(daysAgo.toLong())
                val random = Random(date.toEpochDay())
                val gymDay = date.dayOfWeek in GYM_DAYS
                val tiredPhase = daysAgo < 10
                var activeKcalSum = 0.0
                var hoursCovered = 0

                // Hourly steps, distance, active calories and heart rate, 07:00–22:00.
                for (hour in 7..21) {
                    val start = date.atTime(hour, 0).atZone(zone).toInstant()
                    val end = start.plusSeconds(3600)
                    if (end.isAfter(now)) break
                    hoursCovered = hour + 1
                    val isGym = gymDay && hour == GYM_HOUR
                    val steps = random.nextLong(150, 1300) + if (isGym) 600 else 0
                    val activeKcal = steps * 0.04 + if (isGym) 250.0 else 0.0
                    activeKcalSum += activeKcal

                    records += StepsRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = end, endZoneOffset = offset(end),
                        count = steps,
                        metadata = Metadata.manualEntry(),
                    )
                    records += DistanceRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = end, endZoneOffset = offset(end),
                        distance = Length.meters(steps * 0.75),
                        metadata = Metadata.manualEntry(),
                    )
                    records += ActiveCaloriesBurnedRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = end, endZoneOffset = offset(end),
                        energy = Energy.kilocalories(activeKcal),
                        metadata = Metadata.manualEntry(),
                    )
                    val baseBpm = if (isGym) 125L else 72L
                    records += HeartRateRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = end, endZoneOffset = offset(end),
                        samples = (0 until 12).map { i ->
                            HeartRateRecord.Sample(
                                time = start.plusSeconds(i * 300L),
                                beatsPerMinute = baseBpm + random.nextLong(-6, 7),
                            )
                        },
                        metadata = Metadata.manualEntry(),
                    )
                }

                // One total-calories record per day: ~70 kcal/h at rest + activity.
                if (hoursCovered > 0) {
                    val start = date.atStartOfDay(zone).toInstant()
                    // hoursCovered is at most 22 (the last hourly slot ends at 22:00).
                    val end = date.atTime(hoursCovered, 0).atZone(zone).toInstant()
                    records += TotalCaloriesBurnedRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = end, endZoneOffset = offset(end),
                        energy = Energy.kilocalories(70.0 * hoursCovered + activeKcalSum),
                        metadata = Metadata.manualEntry(),
                    )
                }

                // Resting heart rate, measured in the morning.
                val restingTime = date.atTime(7, 0).atZone(zone).toInstant()
                if (restingTime.isBefore(now)) {
                    records += RestingHeartRateRecord(
                        time = restingTime, zoneOffset = offset(restingTime),
                        beatsPerMinute = 56L + (if (tiredPhase) 4 else 0) + random.nextLong(0, 3),
                        metadata = Metadata.manualEntry(),
                    )
                }

                // Sleep for the night that ended on this date, in 90-minute cycles.
                val sleepStart = date.minusDays(1).atTime(23, 0).atZone(zone).toInstant()
                    .plusSeconds(random.nextLong(0, 60) * 60)
                val sleepMinutes = if (tiredPhase) random.nextLong(330, 400) else random.nextLong(420, 490)
                val sleepEnd = sleepStart.plusSeconds(sleepMinutes * 60)
                if (sleepEnd.isBefore(now)) {
                    records += SleepSessionRecord(
                        startTime = sleepStart, startZoneOffset = offset(sleepStart),
                        endTime = sleepEnd, endZoneOffset = offset(sleepEnd),
                        stages = sleepStages(sleepStart, sleepEnd),
                        metadata = Metadata.manualEntry(),
                    )
                }
            }
            return records
        }

        private fun sleepStages(start: Instant, end: Instant): List<SleepSessionRecord.Stage> {
            val cycle = listOf(
                SleepSessionRecord.STAGE_TYPE_LIGHT to 45L,
                SleepSessionRecord.STAGE_TYPE_DEEP to 20L,
                SleepSessionRecord.STAGE_TYPE_REM to 25L,
            )
            val stages = mutableListOf<SleepSessionRecord.Stage>()
            var cursor = start
            var index = 0
            while (cursor.isBefore(end)) {
                val (type, minutes) = cycle[index % cycle.size]
                val stageEnd = minOf(cursor.plusSeconds(minutes * 60), end)
                stages += SleepSessionRecord.Stage(startTime = cursor, endTime = stageEnd, stage = type)
                cursor = stageEnd
                index++
            }
            return stages
        }
    }
}
