package ro.fitnesshub.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Gym logger tables (plan section 6). The screens that use them come in week 3.

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "compound" or "isolation". */
    val type: String,
    val primaryMuscle: String,
    /** barbell / dumbbell / machine / cable / bodyweight. */
    val equipment: String,
    val isCustom: Boolean = false,
)

@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** e.g. "Push A". */
    val name: String,
    /** Comma-separated ISO weekdays, e.g. "MONDAY,THURSDAY". */
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
    /** Position of the exercise inside the day ("order" is an SQL keyword). */
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
    indices = [Index("templateId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    /** Epoch millis. */
    val start: Long,
    val end: Long?,
    val notes: String? = null,
    /** Filled from Health Connect after the session ends. */
    val avgHr: Long? = null,
    val maxHr: Long? = null,
    val kcal: Double? = null,
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

@Dao
interface GymDao {
    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM workout_template ORDER BY name")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>
}
