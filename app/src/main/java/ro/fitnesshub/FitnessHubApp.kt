package ro.fitnesshub

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.HealthRepository
import ro.fitnesshub.data.db.AppDatabase
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.data.health.SeedDataWriter
import ro.fitnesshub.sync.HealthSync
import ro.fitnesshub.sync.SyncWorker
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
        AppLog.i(
            "Startup",
            if (container.firebaseConfigured) {
                "Firebase configured (project ${FirebaseApp.getInstance().options.projectId})"
            } else {
                "Firebase not configured: app/google-services.json is missing, sign-in is off"
            },
        )
        SyncWorker.schedule(this)
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
    val gymRepository: GymRepository by lazy { GymRepository(database.gymDao(), appContext, healthConnect) }
    val healthSync: HealthSync by lazy { HealthSync(healthRepository, gymRepository) }
    val seedDataWriter: SeedDataWriter by lazy { SeedDataWriter(appContext) }
    val haptics: Haptics by lazy { Haptics(appContext) }

    /**
     * True when the build included app/google-services.json. Firebase then starts by itself
     * before onCreate; without it, any Firebase call would crash, so callers check this first.
     */
    val firebaseConfigured: Boolean = FirebaseApp.getApps(appContext).isNotEmpty()
}
