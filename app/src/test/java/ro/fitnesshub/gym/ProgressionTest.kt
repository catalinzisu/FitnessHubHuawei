package ro.fitnesshub.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ro.fitnesshub.gym.Progression.Hint

class ProgressionTest {

    private fun sets(vararg pairs: Pair<Double, Int>) = pairs.map { LiftSet(it.first, it.second) }

    @Test
    fun estimated1Rm_ignoresHighRepAndEmptySets() {
        assertEquals(100.0, Progression.estimated1Rm(LiftSet(100.0, 1))!!, 0.001)
        assertEquals(80.0 * (1 + 8 / 30.0), Progression.estimated1Rm(LiftSet(80.0, 8))!!, 0.001)
        assertNull(Progression.estimated1Rm(LiftSet(40.0, 20)))
        assertNull(Progression.estimated1Rm(LiftSet(0.0, 10)))
    }

    @Test
    fun records_pickBestAcrossSessions() {
        val points = listOf(
            Progression.SessionPoint(1, 1_000, sets(80.0 to 8, 80.0 to 8)),
            Progression.SessionPoint(2, 2_000, sets(85.0 to 5, 70.0 to 12)),
        )
        val records = Progression.records(points)
        assertEquals(LiftSet(85.0, 5), records.heaviest)
        assertEquals(80.0 * (1 + 8 / 30.0), records.best1Rm!!, 0.001)
        assertEquals(1280.0, records.bestVolumeKg!!, 0.001)
        assertEquals(2, records.sessions)
    }

    @Test
    fun prIndices_marksOnlyNewBests() {
        val history = sets(80.0 to 8)
        // Same as before (no PR), more reps (PR), then the same again (no longer a PR).
        val today = sets(80.0 to 8, 80.0 to 9, 80.0 to 9)
        assertEquals(setOf(1), Progression.prIndices(today, history))
    }

    @Test
    fun prIndices_heavierWeightCountsEvenWithFewReps() {
        val history = sets(80.0 to 8)
        assertEquals(setOf(0), Progression.prIndices(sets(85.0 to 1), history))
    }

    @Test
    fun prIndices_firstTimeIsNeverAPr() {
        assertEquals(emptySet<Int>(), Progression.prIndices(sets(80.0 to 8), emptyList()))
    }

    @Test
    fun hint_allSetsAtTopOfRange_addsWeight() {
        val hint = Progression.hint(sets(80.0 to 12, 80.0 to 12, 80.0 to 12), 8, 12, "barbell")
        assertEquals(Hint.AddWeight(82.5, 8), hint)
    }

    @Test
    fun hint_insideRange_addsOneRepToWeakestSet() {
        val hint = Progression.hint(sets(80.0 to 10, 80.0 to 9, 80.0 to 8), 8, 12, "barbell")
        assertEquals(Hint.AddReps(80.0, 9), hint)
    }

    @Test
    fun hint_belowRange_repeatsWeight() {
        val hint = Progression.hint(sets(80.0 to 8, 80.0 to 6), 8, 12, "barbell")
        assertEquals(Hint.Repeat(80.0, 8), hint)
    }

    @Test
    fun hint_ignoresLighterBackOffSets() {
        // The back-off set at 70 kg is below the range but doesn't hold back the top weight.
        val hint = Progression.hint(sets(80.0 to 12, 80.0 to 12, 70.0 to 6), 8, 12, "barbell")
        assertEquals(Hint.AddWeight(82.5, 8), hint)
    }

    @Test
    fun hint_bodyweightProgressesWithReps() {
        val hint = Progression.hint(sets(0.0 to 12, 0.0 to 12), 8, 12, "bodyweight")
        assertEquals(Hint.AddReps(0.0, 13), hint)
    }

    @Test
    fun hint_noHistory_noHint() {
        assertNull(Progression.hint(emptyList(), 8, 12, "barbell"))
    }

    @Test
    fun suggestion_usesHintBeforeLastSession() {
        val hint = Hint.AddWeight(82.5, 8)
        assertEquals(SetSuggestions.Suggestion(82.5, 8), SetSuggestions.next(emptyList(), emptyList(), hint))
    }
}
