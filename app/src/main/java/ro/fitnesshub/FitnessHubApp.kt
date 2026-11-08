package ro.fitnesshub

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.HealthRepository
import ro.fitnesshub.data.db.AppDatabase
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.data.health.SeedDataWriter
import ro.fitnesshub.util.AppLog
import ro.fitnesshub.util.Haptics

class FitnessHubApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Lives as long as the process; for one-off startup work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            try {
                container.gymRepository.ensureExerciseLibrary()
            } catch (e: Exception) {
                AppLog.e("Startup", "Could not load the exercise library", e)
            }
        }
    }
}

/** Manual dependency container. Small enough that Hilt isn't needed yet. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val healthConnect: HealthConnectSource by lazy { HealthConnectSource(appContext) }
    val healthRepository: HealthRepository by lazy {
        HealthRepository(healthConnect, database.dailySummaryDao())
    }
    val gymRepository: GymRepository by lazy { GymRepository(database.gymDao(), appContext) }
    val seedDataWriter: SeedDataWriter by lazy { SeedDataWriter(appContext) }
    val haptics: Haptics by lazy { Haptics(appContext) }
}
