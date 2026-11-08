package ro.fitnesshub.gym

import android.content.Context
import org.json.JSONArray
import ro.fitnesshub.data.db.ExerciseEntity

/** Built-in exercise list, loaded once from assets/exercises.json into Room. */
object ExerciseLibrary {

    private const val ASSET = "exercises.json"

    val MUSCLES = listOf(
        "chest", "back", "shoulders", "biceps", "triceps",
        "quads", "hamstrings", "glutes", "calves", "core",
    )
    val EQUIPMENT = listOf("barbell", "dumbbell", "machine", "cable", "bodyweight")
    val TYPES = listOf("compound", "isolation")

    fun load(context: Context): List<ExerciseEntity> {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        return parse(json)
    }

    fun parse(json: String): List<ExerciseEntity> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ExerciseEntity(
                name = item.getString("name"),
                type = item.getString("type"),
                primaryMuscle = item.getString("muscle"),
                equipment = item.getString("equipment"),
                isCustom = false,
            )
        }
    }

    /** "hamstrings" → "Hamstrings". */
    fun label(key: String): String = key.replaceFirstChar { it.uppercase() }
}
