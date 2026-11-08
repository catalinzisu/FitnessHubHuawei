package ro.fitnesshub.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Gym logger tables (plan section 6).

@Entity(tableName = "exercise", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "compound" or "isolation". */
    val type: String,
    /** chest / back / shoulders / biceps / triceps / quads / hamstrings / glutes / calves / core. */
    val primaryMuscle: String,
    /** barbell / dumbbell / machine / cable / bodyweight. */
    val equipment: String,
    val isCustom: Boolean = false,
)

/** A custom training day, e.g. "Push A" on Monday and Thursday. */
@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated java.time.DayOfWeek names, e.g. "MONDAY,THURSDAY". */
    val weekdays: String,
    val notes: String? = null,
)

@Entity(
    tableName = "template_exercise",
    primaryKeys = ["templateId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class TemplateExerciseEntity(
    val templateId: Long,
    val exerciseId: Long,
    /** Position inside the day ("order" is an SQL keyword). */
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int,
    val targetRepsMax: Int,
    val restSeconds: Int,
)

@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateId"), Index("startedAt")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    /** Epoch millis. */
    val startedAt: Long,
    /** Epoch millis; null while the workout is in progress. */
    val endedAt: Long? = null,
    val notes: String? = null,
    /** Watch data from Health Connect for the session's time range; filled after it ends. */
    val avgHr: Long? = null,
    val maxHr: Long? = null,
    val kcal: Double? = null,
)

/** The exercises of one session, copied from the template when it starts, plus any added on the fly. */
@Entity(
    tableName = "session_exercise",
    primaryKeys = ["sessionId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class SessionExerciseEntity(
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int,
    val targetRepsMax: Int,
    val restSeconds: Int,
)

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    /** Epoch millis. */
    val completedAt: Long,
)

// ---- Query results ----

data class TemplateSummary(
    @Embedded val template: WorkoutTemplateEntity,
    val exerciseCount: Int,
)

data class TemplateExerciseRow(
    @Embedded val item: TemplateExerciseEntity,
    val exerciseName: String,
)

data class SessionExerciseRow(
    @Embedded val item: SessionExerciseEntity,
    val exerciseName: String,
    val equipment: String,
)

/** One logged set of an exercise, with the start time of its (finished) session. */
data class ExerciseHistorySet(
    val sessionId: Long,
    val startedAt: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val isWarmup: Boolean,
)

data class SessionSummary(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val templateName: String?,
    val workingSets: Int,
    val exerciseCount: Int,
    val avgHr: Long?,
    val kcal: Double?,
)
