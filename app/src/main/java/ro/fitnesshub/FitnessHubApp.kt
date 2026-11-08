package ro.fitnesshub

import android.app.Application
import android.content.Context
import ro.fitnesshub.data.HealthRepository
import ro.fitnesshub.data.db.AppDatabase
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.data.health.SeedDataWriter

class FitnessHubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
    val seedDataWriter: SeedDataWriter by lazy { SeedDataWriter(appContext) }
}
