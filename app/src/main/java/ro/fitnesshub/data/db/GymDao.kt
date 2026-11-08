package ro.fitnesshub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GymDao {

    // ---- Exercises ----

    @Query("SELECT * FROM exercise ORDER BY primaryMuscle, name")
    abstract fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercise")
    abstract suspend fun exerciseCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise WHERE id = :id")
    abstract suspend fun getExercise(id: Long): ExerciseEntity?

    /** Returns the new id, or -1 if an exercise with the same name exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertExercise(exercise: ExerciseEntity): Long

    // ---- Templates (custom training days) ----

    @Query(
        """
        SELECT t.*, COUNT(te.exerciseId) AS exerciseCount
        FROM workout_template t
        LEFT JOIN template_exercise te ON te.templateId = t.id
        GROUP BY t.id
        ORDER BY t.name
        """,
    )
    abstract fun observeTemplates(): Flow<List<TemplateSummary>>

    @Query("SELECT * FROM workout_template WHERE id = :id")
    abstract suspend fun getTemplate(id: Long): WorkoutTemplateEntity?

    @Query(
        """
        SELECT te.*, e.name AS exerciseName
        FROM template_exercise te
        JOIN exercise e ON e.id = te.exerciseId
        WHERE te.templateId = :templateId
        ORDER BY te.position
        """,
    )
    abstract suspend fun getTemplateExercises(templateId: Long): List<TemplateExerciseRow>

    @Insert
    protected abstract suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Update
    protected abstract suspend fun updateTemplate(template: WorkoutTemplateEntity)

    @Query("DELETE FROM template_exercise WHERE templateId = :templateId")
    protected abstract suspend fun clearTemplateExercises(templateId: Long)

    @Insert
    protected abstract suspend fun insertTemplateExercises(items: List<TemplateExerciseEntity>)

    @Query("DELETE FROM workout_template WHERE id = :id")
    abstract suspend fun deleteTemplate(id: Long)

    /** Creates or updates a template and replaces its exercise list. Returns the template id. */
    @Transaction
    open suspend fun saveTemplate(template: WorkoutTemplateEntity, exercises: List<TemplateExerciseEntity>): Long {
        val id = if (template.id == 0L) insertTemplate(template) else template.id.also { updateTemplate(template) }
        clearTemplateExercises(id)
        insertTemplateExercises(exercises.mapIndexed { index, item -> item.copy(templateId = id, position = index) })
        return id
    }

    // ---- Sessions ----

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun getActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    abstract fun observeSession(id: Long): Flow<WorkoutSessionEntity?>

    @Query(
        """
        SELECT ws.id, ws.startedAt, ws.endedAt, ws.avgHr, ws.kcal, t.name AS templateName,
            (SELECT COUNT(*) FROM set_entry se WHERE se.sessionId = ws.id AND se.isWarmup = 0) AS workingSets,
            (SELECT COUNT(DISTINCT se.exerciseId) FROM set_entry se WHERE se.sessionId = ws.id) AS exerciseCount
        FROM workout_session ws
        LEFT JOIN workout_template t ON t.id = ws.templateId
        WHERE ws.endedAt IS NOT NULL
        ORDER BY ws.startedAt DESC
        LIMIT :limit
        """,
    )
    abstract fun observeRecentSessions(limit: Int): Flow<List<SessionSummary>>

    @Insert
    protected abstract suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSessionExercises(items: List<SessionExerciseEntity>)

    /** Starts a session and copies the template's exercises into it. Returns the session id. */
    @Transaction
    open suspend fun startSession(templateId: Long?, startedAt: Long): Long {
        val sessionId = insertSession(WorkoutSessionEntity(templateId = templateId, startedAt = startedAt))
        if (templateId != null) {
            insertSessionExercises(
                getTemplateExercises(templateId).map { row ->
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = row.item.exerciseId,
                        position = row.item.position,
                        targetSets = row.item.targetSets,
                        targetRepsMin = row.item.targetRepsMin,
                        targetRepsMax = row.item.targetRepsMax,
                        restSeconds = row.item.restSeconds,
                    )
                },
            )
        }
        return sessionId
    }

    @Query("UPDATE workout_session SET endedAt = :endedAt WHERE id = :id")
    protected abstract suspend fun setSessionEnd(id: Long, endedAt: Long)

    @Query("SELECT COUNT(*) FROM set_entry WHERE sessionId = :sessionId")
    protected abstract suspend fun setCount(sessionId: Long): Int

    @Query(
        """
        DELETE FROM session_exercise
        WHERE sessionId = :sessionId
          AND exerciseId NOT IN (SELECT exerciseId FROM set_entry WHERE sessionId = :sessionId)
        """,
    )
    protected abstract suspend fun pruneEmptyExercises(sessionId: Long)

    /**
     * Ends a session and drops exercises with no sets. A session with no sets at all is deleted.
     * Returns true if the session was kept.
     */
    @Transaction
    open suspend fun finishSession(id: Long, endedAt: Long): Boolean {
        if (setCount(id) == 0) {
            deleteSession(id)
            return false
        }
        pruneEmptyExercises(id)
        setSessionEnd(id, endedAt)
        return true
    }

    @Query("DELETE FROM workout_session WHERE id = :id")
    abstract suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM workout_session WHERE id = :id")
    abstract suspend fun getSession(id: Long): WorkoutSessionEntity?

    @Query("UPDATE workout_session SET avgHr = :avgHr, maxHr = :maxHr, kcal = :kcal WHERE id = :id")
    abstract suspend fun updateSessionStats(id: Long, avgHr: Long?, maxHr: Long?, kcal: Double?)

    /** Finished sessions since [since] that have no watch data yet (the watch often syncs later). */
    @Query(
        """
        SELECT * FROM workout_session
        WHERE endedAt IS NOT NULL AND endedAt >= :since AND avgHr IS NULL AND kcal IS NULL
        ORDER BY startedAt
        """,
    )
    abstract suspend fun sessionsMissingStats(since: Long): List<WorkoutSessionEntity>

    // ---- Session exercises ----

    @Query(
        """
        SELECT se.*, e.name AS exerciseName, e.equipment AS equipment
        FROM session_exercise se
        JOIN exercise e ON e.id = se.exerciseId
        WHERE se.sessionId = :sessionId
        ORDER BY se.position
        """,
    )
    abstract fun observeSessionExercises(sessionId: Long): Flow<List<SessionExerciseRow>>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM session_exercise WHERE sessionId = :sessionId")
    protected abstract suspend fun nextSessionPosition(sessionId: Long): Int

    @Transaction
    open suspend fun addExerciseToSession(item: SessionExerciseEntity) {
        insertSessionExercises(listOf(item.copy(position = nextSessionPosition(item.sessionId))))
    }

    @Query("DELETE FROM session_exercise WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    protected abstract suspend fun deleteSessionExercise(sessionId: Long, exerciseId: Long)

    @Query("DELETE FROM set_entry WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    protected abstract suspend fun deleteSetsOfExercise(sessionId: Long, exerciseId: Long)

    @Transaction
    open suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: Long) {
        deleteSetsOfExercise(sessionId, exerciseId)
        deleteSessionExercise(sessionId, exerciseId)
    }

    // ---- Sets ----

    @Query("SELECT * FROM set_entry WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber")
    abstract fun observeSets(sessionId: Long): Flow<List<SetEntryEntity>>

    @Query("SELECT COALESCE(MAX(setNumber), 0) + 1 FROM set_entry WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    protected abstract suspend fun nextSetNumber(sessionId: Long, exerciseId: Long): Int

    @Insert
    protected abstract suspend fun insertSet(set: SetEntryEntity): Long

    @Transaction
    open suspend fun logSet(set: SetEntryEntity): Long =
        insertSet(set.copy(setNumber = nextSetNumber(set.sessionId, set.exerciseId)))

    @Query("DELETE FROM set_entry WHERE id = :id")
    abstract suspend fun deleteSet(id: Long)

    /** Sets of the most recent finished session (other than [excludeSessionId]) that included this exercise. */
    @Query(
        """
        SELECT * FROM set_entry
        WHERE exerciseId = :exerciseId AND sessionId = (
            SELECT s.sessionId FROM set_entry s
            JOIN workout_session ws ON ws.id = s.sessionId
            WHERE s.exerciseId = :exerciseId AND s.sessionId != :excludeSessionId AND ws.endedAt IS NOT NULL
            ORDER BY ws.startedAt DESC
            LIMIT 1
        )
        ORDER BY setNumber
        """,
    )
    abstract suspend fun previousSets(exerciseId: Long, excludeSessionId: Long): List<SetEntryEntity>

    // ---- Progress ----

    /** Every set of this exercise from finished sessions, oldest first. */
    @Query(
        """
        SELECT se.sessionId, ws.startedAt, se.setNumber, se.weightKg, se.reps, se.rpe, se.isWarmup
        FROM set_entry se
        JOIN workout_session ws ON ws.id = se.sessionId
        WHERE se.exerciseId = :exerciseId AND ws.endedAt IS NOT NULL
        ORDER BY ws.startedAt, se.setNumber
        """,
    )
    abstract fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySet>>

    @Query(
        """
        SELECT se.sessionId, ws.startedAt, se.setNumber, se.weightKg, se.reps, se.rpe, se.isWarmup
        FROM set_entry se
        JOIN workout_session ws ON ws.id = se.sessionId
        WHERE se.exerciseId = :exerciseId AND ws.endedAt IS NOT NULL AND se.sessionId != :excludeSessionId
        ORDER BY ws.startedAt, se.setNumber
        """,
    )
    abstract suspend fun exerciseHistory(exerciseId: Long, excludeSessionId: Long): List<ExerciseHistorySet>
}
