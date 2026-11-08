package ro.fitnesshub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailySummaryEntity::class,
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        WorkoutSessionEntity::class,
        SetEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun gymDao(): GymDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitness_hub.db")
                // While developing: a schema change wipes the local cache instead of crashing.
                // Replace with real migrations before anyone depends on stored gym logs.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
