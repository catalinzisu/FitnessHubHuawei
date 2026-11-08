package ro.fitnesshub.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ro.fitnesshub.data.db.SetEntryEntity

class SetSuggestionsTest {

    private fun set(weight: Double, reps: Int, warmup: Boolean = false, number: Int = 1) = SetEntryEntity(
        sessionId = 1,
        exerciseId = 1,
        setNumber = number,
        reps = reps,
        weightKg = weight,
        isWarmup = warmup,
        completedAt = 0,
    )

    @Test
    fun nothingLogged_noHistory_noSuggestion() {
        assertNull(SetSuggestions.next(emptyList(), emptyList()))
    }

    @Test
    fun firstSet_usesPreviousSessionFirstWorkingSet() {
        val previous = listOf(set(40.0, 10, warmup = true), set(80.0, 8), set(80.0, 7))
        assertEquals(SetSuggestions.Suggestion(80.0, 8), SetSuggestions.next(emptyList(), previous))
    }

    @Test
    fun laterSets_followTheLastWorkingSetOfToday() {
        val current = listOf(set(40.0, 10, warmup = true), set(82.5, 8))
        val previous = listOf(set(80.0, 8))
        assertEquals(SetSuggestions.Suggestion(82.5, 8), SetSuggestions.next(current, previous))
    }

    @Test
    fun onlyWarmupsToday_fallsBackToHistory() {
        val current = listOf(set(40.0, 10, warmup = true))
        val previous = listOf(set(80.0, 8))
        assertEquals(SetSuggestions.Suggestion(80.0, 8), SetSuggestions.next(current, previous))
    }

    @Test
    fun summary_skipsWarmups() {
        val sets = listOf(set(40.0, 10, warmup = true), set(80.0, 8), set(77.5, 7))
        assertEquals("80 × 8, 77.5 × 7", SetSuggestions.summary(sets))
        assertNull(SetSuggestions.summary(listOf(set(40.0, 10, warmup = true))))
    }
}
