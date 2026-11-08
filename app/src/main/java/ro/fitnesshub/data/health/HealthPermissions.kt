package ro.fitnesshub.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import ro.fitnesshub.BuildConfig
import kotlin.reflect.KClass

/**
 * Every Health Connect permission the app asks for.
 * Must match the <uses-permission> entries in the manifests.
 */
object HealthPermissions {

    val READ: Set<String> = setOf<KClass<out Record>>(
        StepsRecord::class,
        DistanceRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        SleepSessionRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        ExerciseSessionRecord::class,
        OxygenSaturationRecord::class,
        WeightRecord::class,
    ).map { HealthPermission.getReadPermission(it) }.toSet()

    /** Finished gym sessions are saved to Health Connect as strength-training sessions. */
    val WRITE_WORKOUTS: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)

    /** Lets WorkManager sync while the app is closed (only on Health Connect versions that support it). */
    val BACKGROUND: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** Only used by the debug "Seed test data" tool (see src/debug/AndroidManifest.xml). */
    val SEED_WRITE: Set<String> = SeedDataWriter.RECORD_TYPES
        .map { HealthPermission.getWritePermission(it) }
        .toSet()

    /** What the permission screen asks for in this build. */
    fun requested(backgroundSupported: Boolean): Set<String> = buildSet {
        addAll(READ)
        add(WRITE_WORKOUTS)
        if (backgroundSupported) add(BACKGROUND)
        if (BuildConfig.DEBUG) addAll(SEED_WRITE)
    }
}
