package ro.fitnesshub.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync. Health Connect only allows reads while the app is closed when the
 * user granted "read in background", so without it this worker does nothing and the app
 * syncs when it is opened instead.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FitnessHubApp).container
        val healthConnect = container.healthConnect
        return try {
            if (!healthConnect.isAvailable() || !healthConnect.canReadInBackground()) {
                AppLog.i(TAG, "Background sync skipped: not allowed")
                return Result.success()
            }
            container.healthSync.syncRecent()
            AppLog.i(TAG, "Background sync done")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Background sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "health-sync"

        /** Safe to call on every start: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
