package ro.fitnesshub.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseLibraryTest {

    /** Reads the real asset file so a typo in exercises.json fails the build. */
    private fun assetJson(): String {
        val candidates = listOf("src/main/assets/exercises.json", "app/src/main/assets/exercises.json")
        val file = candidates.map(::File).first { it.exists() }
        return file.readText()
    }

    @Test
    fun builtInLibrary_isValid() {
        val exercises = ExerciseLibrary.parse(assetJson())
        assertTrue("expected at least 40 exercises", exercises.size >= 40)
        assertEquals("names must be unique", exercises.size, exercises.map { it.name }.toSet().size)
        exercises.forEach {
            assertTrue("unknown muscle ${it.primaryMuscle}", it.primaryMuscle in ExerciseLibrary.MUSCLES)
            assertTrue("unknown equipment ${it.equipment}", it.equipment in ExerciseLibrary.EQUIPMENT)
            assertTrue("unknown type ${it.type}", it.type in ExerciseLibrary.TYPES)
        }
    }
}
