package ro.fitnesshub.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import ro.fitnesshub.data.db.ExerciseEntity
import ro.fitnesshub.data.db.ExerciseHistorySet
import ro.fitnesshub.data.db.GymDao
import ro.fitnesshub.data.db.SessionExerciseEntity
import ro.fitnesshub.data.db.SessionExerciseRow
import ro.fitnesshub.data.db.SessionSummary
import ro.fitnesshub.data.db.SetEntryEntity
import ro.fitnesshub.data.db.TemplateExerciseEntity
import ro.fitnesshub.data.db.TemplateExerciseRow
import ro.fitnesshub.data.db.TemplateSummary
import ro.fitnesshub.data.db.WorkoutSessionEntity
import ro.fitnesshub.data.db.WorkoutTemplateEntity
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.gym.ExerciseLibrary
import ro.fitnesshub.util.AppLog
import java.time.Duration
import java.time.Instant

/**
 * Everything the gym screens need. Sessions live in the local Room database; Health Connect is
 * only used to add the watch's heart rate / calories and to save finished sessions there.
 */
class GymRepository(
    private val dao: GymDao,
    private val context: Context,
    private val health: HealthConnectSource,
) {
    val exercises: Flow<List<ExerciseEntity>> = dao.observeExercises()
    val templates: Flow<List<TemplateSummary>> = dao.observeTemplates()
    val activeSession: Flow<WorkoutSessionEntity?> = dao.observeActiveSession()

    fun recentSessions(limit: Int = 10): Flow<List<SessionSummary>> = dao.observeRecentSessions(limit)

    /** Fills the exercise table from assets on first launch. */
    suspend fun ensureExerciseLibrary() {
        if (dao.exerciseCount() > 0) return
        val exercises = ExerciseLibrary.load(context)
        dao.insertExercises(exercises)
        AppLog.i(TAG, "Loaded ${exercises.size} built-in exercises")
    }

    /** Returns the new id, or null if an exercise with that name already exists. */
    suspend fun addCustomExercise(name: String, muscle: String, equipment: String, type: String): Long? {
        val id = dao.insertExercise(
            ExerciseEntity(name = name.trim(), type = type, primaryMuscle = muscle, equipment = equipment, isCustom = true),
        )
        return id.takeIf { it > 0 }
    }

    // ---- Templates ----

    suspend fun getTemplate(id: Long): WorkoutTemplateEntity? = dao.getTemplate(id)

    suspend fun getTemplateExercises(id: Long): List<TemplateExerciseRow> = dao.getTemplateExercises(id)

    suspend fun saveTemplate(template: WorkoutTemplateEntity, exercises: List<TemplateExerciseEntity>): Long {
        val id = dao.saveTemplate(template, exercises)
        AppLog.i(TAG, "Saved training day #$id with ${exercises.size} exercises")
        return id
    }

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    // ---- Sessions ----

    /** Starts a workout, or returns the one already in progress (only one at a time). */
    suspend fun startWorkout(templateId: Long?): Long {
        dao.getActiveSession()?.let { return it.id }
        val id = dao.startSession(templateId, System.currentTimeMillis())
        AppLog.i(TAG, "Started workout #$id")
        return id
    }

    fun observeSession(id: Long): Flow<WorkoutSessionEntity?> = dao.observeSession(id)

    fun observeSessionExercises(id: Long): Flow<List<SessionExerciseRow>> = dao.observeSessionExercises(id)

    fun observeSets(sessionId: Long): Flow<List<SetEntryEntity>> = dao.observeSets(sessionId)

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long) =
        dao.addExerciseToSession(
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = 0,
                targetSets = DEFAULT_SETS,
                targetRepsMin = DEFAULT_REPS_MIN,
                targetRepsMax = DEFAULT_REPS_MAX,
                restSeconds = DEFAULT_REST_SECONDS,
            ),
        )

    suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: Long) =
        dao.removeExerciseFromSession(sessionId, exerciseId)

    suspend fun logSet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double,
        reps: Int,
        rpe: Double?,
        isWarmup: Boolean,
    ) {
        dao.logSet(
            SetEntryEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = 0, // assigned by the DAO
                reps = reps,
                weightKg = weightKg,
                rpe = rpe,
                isWarmup = isWarmup,
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun previousSets(exerciseId: Long, currentSessionId: Long): List<SetEntryEntity> =
        dao.previousSets(exerciseId, currentSessionId)

    /** Ends the workout. An empty workout (no sets) is deleted instead. */
    suspend fun finishWorkout(id: Long) {
        val kept = dao.finishSession(id, System.currentTimeMillis())
        AppLog.i(TAG, if (kept) "Finished workout #$id" else "Workout #$id had no sets, deleted")
        if (!kept) return
        val session = dao.getSession(id) ?: return
        exportToHealthConnect(session)
        attachWatchStats(session)
    }

    // ---- Health Connect ----

    /** Saves the session in Health Connect if the user allowed it. Failures are logged, never fatal. */
    private suspend fun exportToHealthConnect(session: WorkoutSessionEntity) {
        val end = session.endedAt ?: return
        healthCall("export workout #${session.id}") {
            if (!health.isAvailable() || !health.canWriteWorkouts()) return@healthCall
            health.writeGymSession(
                sessionId = session.id,
                title = getTemplateName(session.templateId),
                start = Instant.ofEpochMilli(session.startedAt),
                end = Instant.ofEpochMilli(end),
            )
            AppLog.i(TAG, "Saved workout #${session.id} to Health Connect")
        }
    }

    /**
     * Copies the watch's average/max heart rate and active calories for the session's time range.
     * Gadgetbridge often syncs the watch later, so this is retried by [attachMissingWatchStats].
     */
    suspend fun attachWatchStats(session: WorkoutSessionEntity) {
        val end = session.endedAt ?: return
        healthCall("watch stats for workout #${session.id}") {
            if (!health.isAvailable()) return@healthCall
            val stats = health.readWorkoutStats(Instant.ofEpochMilli(session.startedAt), Instant.ofEpochMilli(end))
                ?: return@healthCall
            if (stats.avgHr == null && stats.activeKcal == null) return@healthCall
            dao.updateSessionStats(session.id, stats.avgHr, stats.maxHr, stats.activeKcal)
            // Which fields were found, never the values.
            AppLog.i(TAG, "Workout #${session.id}: watch HR ${stats.avgHr != null}, kcal ${stats.activeKcal != null}")
        }
    }

    suspend fun attachWatchStats(sessionId: Long) {
        dao.getSession(sessionId)?.takeIf { it.avgHr == null && it.kcal == null }?.let { attachWatchStats(it) }
    }

    /** Retries sessions from the last few days that still have no watch data. */
    suspend fun attachMissingWatchStats() {
        val since = System.currentTimeMillis() - STATS_RETRY_WINDOW.toMillis()
        dao.sessionsMissingStats(since).forEach { attachWatchStats(it) }
    }

    private inline fun healthCall(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "Health Connect failed: $what", e)
        }
    }

    // ---- Progress ----

    suspend fun getExercise(id: Long): ExerciseEntity? = dao.getExercise(id)

    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySet>> = dao.observeExerciseHistory(exerciseId)

    /** All earlier finished sets of this exercise, for PR detection during a workout. */
    suspend fun exerciseHistory(exerciseId: Long, currentSessionId: Long): List<ExerciseHistorySet> =
        dao.exerciseHistory(exerciseId, currentSessionId)

    suspend fun getTemplateName(templateId: Long?): String? =
        templateId?.let { dao.getTemplate(it)?.name }

    /** Deletes a workout (in progress or finished), including its copy in Health Connect. */
    suspend fun discardWorkout(id: Long) {
        val wasFinished = dao.getSession(id)?.endedAt != null
        dao.deleteSession(id)
        AppLog.i(TAG, "Discarded workout #$id")
        if (wasFinished) {
            healthCall("delete workout #$id") {
                if (health.isAvailable() && health.canWriteWorkouts()) health.deleteGymSession(id)
            }
        }
    }

    companion object {
        private const val TAG = "Gym"
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS_MIN = 8
        const val DEFAULT_REPS_MAX = 12
        const val DEFAULT_REST_SECONDS = 120
        private val STATS_RETRY_WINDOW: Duration = Duration.ofDays(3)
    }
}
