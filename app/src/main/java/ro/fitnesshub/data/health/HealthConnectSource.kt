package ro.fitnesshub.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Reads data from Health Connect. Gadgetbridge writes the watch data there,
 * so this class never talks to the watch or to Bluetooth.
 */
class HealthConnectSource(private val context: Context) : HealthDataSource {

    override val name = "Health Connect"

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /** One of HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    override suspend fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    override suspend fun readDailySummary(date: LocalDate): DailySummary? {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val dayStart = date.atStartOfDay(zone).toInstant()
        if (!dayStart.isBefore(now)) return null
        val dayEnd = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)

        // Aggregating a type without permission throws, so only ask for what is allowed.
        val granted = grantedPermissions()
        fun canRead(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        val dayMetrics = buildSet<AggregateMetric<*>> {
            if (canRead(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (canRead(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (canRead(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (canRead(TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (canRead(HeartRateRecord::class)) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MIN)
                add(HeartRateRecord.BPM_MAX)
            }
            if (canRead(RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
        }
        val canReadSleep = canRead(SleepSessionRecord::class)
        if (dayMetrics.isEmpty() && !canReadSleep) return null

        // Health Connect de-duplicates overlapping sources (phone + watch) in aggregates.
        val day = if (dayMetrics.isNotEmpty()) {
            client.aggregate(
                AggregateRequest(
                    metrics = dayMetrics,
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                ),
            )
        } else {
            null
        }

        // "Last night" = the night that ended on this date.
        val sleep = if (canReadSleep) {
            val nightStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val nightEnd = minOf(date.atTime(18, 0).atZone(zone).toInstant(), now)
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(nightStart, nightEnd),
                ),
            )
        } else {
            null
        }

        val origins = day?.dataOrigins.orEmpty() + sleep?.dataOrigins.orEmpty()

        return DailySummary(
            date = date,
            steps = day?.get(StepsRecord.COUNT_TOTAL),
            distanceMeters = day?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            activeKcal = day?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            totalKcal = day?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            avgHr = day?.get(HeartRateRecord.BPM_AVG),
            minHr = day?.get(HeartRateRecord.BPM_MIN),
            maxHr = day?.get(HeartRateRecord.BPM_MAX),
            restingHr = day?.get(RestingHeartRateRecord.BPM_AVG),
            sleepMinutes = sleep?.get(SleepSessionRecord.SLEEP_DURATION_TOTAL)?.toMinutes(),
            sources = origins.map { it.packageName }.toSet(),
        )
    }

    override suspend fun readHeartRate(from: Instant, to: Instant): List<HeartRateSample> =
        readAll(HeartRateRecord::class, from, to).flatMap { record ->
            val source = record.metadata.dataOrigin.packageName
            record.samples.map { HeartRateSample(it.time, it.beatsPerMinute, source) }
        }

    override suspend fun readSleep(from: Instant, to: Instant): List<SleepSession> =
        readAll(SleepSessionRecord::class, from, to).map { record ->
            SleepSession(
                start = record.startTime,
                end = record.endTime,
                stages = record.stages.map { SleepStage(it.startTime, it.endTime, mapStage(it.stage)) },
                source = record.metadata.dataOrigin.packageName,
            )
        }

    override suspend fun readWorkouts(from: Instant, to: Instant): List<Workout> =
        readAll(ExerciseSessionRecord::class, from, to).map { record ->
            Workout(
                start = record.startTime,
                end = record.endTime,
                title = record.title,
                exerciseType = record.exerciseType,
                source = record.metadata.dataOrigin.packageName,
            )
        }

    /** Reads every page of records of one type in a time range. */
    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = pageToken,
                ),
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    private fun mapStage(stage: Int): SleepStageType = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.SLEEPING
        else -> SleepStageType.UNKNOWN
    }

    companion object {
        const val GADGETBRIDGE_PACKAGE = "nodomain.freeyourgadget.gadgetbridge"
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}
