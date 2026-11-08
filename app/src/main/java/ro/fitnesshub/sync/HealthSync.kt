package ro.fitnesshub.sync

import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.HealthRepository
import java.time.LocalDate

/**
 * One sync pass, used both when the app opens and by [SyncWorker] in the background:
 * the last [days] daily summaries, then watch data for recent gym sessions.
 */
class HealthSync(
    private val health: HealthRepository,
    private val gym: GymRepository,
) {
    suspend fun syncRecent(days: Int = 2) {
        val today = LocalDate.now()
        // Yesterday too: Gadgetbridge may have synced the end of the day only this morning.
        for (offset in 0 until days) health.syncDay(today.minusDays(offset.toLong()))
        gym.attachMissingWatchStats()
    }
}
