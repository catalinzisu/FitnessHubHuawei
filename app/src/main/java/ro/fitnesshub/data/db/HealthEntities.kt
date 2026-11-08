package ro.fitnesshub.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Local cache of one day's totals, so the dashboard opens instantly and works offline. */
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    /** ISO date, e.g. "2026-11-08". */
    @PrimaryKey val date: String,
    val steps: Long?,
    val distanceMeters: Double?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val avgHr: Long?,
    val minHr: Long?,
    val maxHr: Long?,
    val restingHr: Long?,
    val sleepMinutes: Long?,
    /** Comma-separated package names of the apps that wrote the data. */
    val sources: String,
    /** Epoch millis of the last successful sync. */
    val syncedAt: Long,
)

@Dao
interface DailySummaryDao {
    @Query("SELECT * FROM daily_summary WHERE date = :date")
    fun observe(date: String): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summary WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<DailySummaryEntity>>

    @Upsert
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("DELETE FROM daily_summary")
    suspend fun deleteAll()
}
