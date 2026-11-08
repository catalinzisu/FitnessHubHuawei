package ro.fitnesshub.data

import kotlinx.coroutines.flow.Flow
import ro.fitnesshub.data.db.DailySummaryDao
import ro.fitnesshub.data.db.DailySummaryEntity
import ro.fitnesshub.data.health.DailySummary
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.data.health.HealthPermissions
import ro.fitnesshub.util.AppLog
import java.time.LocalDate

/**
 * Single entry point for health data used by the UI:
 * Health Connect → Room cache → screens.
 */
class HealthRepository(
    private val source: HealthConnectSource,
    private val dao: DailySummaryDao,
) {
    fun sdkStatus(): Int = source.sdkStatus()

    fun backgroundReadSupported(): Boolean = source.backgroundReadSupported()

    fun observeDay(date: LocalDate): Flow<DailySummaryEntity?> = dao.observe(date.toString())

    /** True when every read permission is granted. */
    suspend fun hasAllReadPermissions(): Boolean =
        source.grantedPermissions().containsAll(HealthPermissions.READ)

    /** True when at least one read permission is granted. */
    suspend fun hasAnyReadPermission(): Boolean =
        source.grantedPermissions().any { it in HealthPermissions.READ }

    /** Reads one day from Health Connect and stores it in Room. */
    suspend fun syncDay(date: LocalDate = LocalDate.now()) {
        val summary = source.readDailySummary(date)
        if (summary == null) {
            AppLog.w(TAG, "Nothing to sync for $date (no permissions or future date)")
            return
        }
        dao.upsert(summary.toEntity(syncedAt = System.currentTimeMillis()))
        // Counts only, never values.
        AppLog.i(TAG, "Synced $date: ${summary.filledFields()} fields from ${summary.sources.size} source(s)")
    }

    private fun DailySummary.toEntity(syncedAt: Long) = DailySummaryEntity(
        date = date.toString(),
        steps = steps,
        distanceMeters = distanceMeters,
        activeKcal = activeKcal,
        totalKcal = totalKcal,
        avgHr = avgHr,
        minHr = minHr,
        maxHr = maxHr,
        restingHr = restingHr,
        sleepMinutes = sleepMinutes,
        sources = sources.sorted().joinToString(","),
        syncedAt = syncedAt,
    )

    private fun DailySummary.filledFields(): Int = listOf(
        steps, distanceMeters, activeKcal, totalKcal, avgHr, minHr, maxHr, restingHr, sleepMinutes,
    ).count { it != null }

    private companion object {
        const val TAG = "Sync"
    }
}
