package ro.fitnesshub.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.util.Locale

class WorkoutFormatTest {

    @Test
    fun parseWeight_acceptsDotAndComma() {
        assertEquals(77.5, WorkoutFormat.parseWeight("77.5")!!, 0.0)
        assertEquals(77.5, WorkoutFormat.parseWeight(" 77,5 ")!!, 0.0)
        assertEquals(0.0, WorkoutFormat.parseWeight("0")!!, 0.0)
    }

    @Test
    fun parseWeight_rejectsNonsense() {
        assertNull(WorkoutFormat.parseWeight(""))
        assertNull(WorkoutFormat.parseWeight("abc"))
        assertNull(WorkoutFormat.parseWeight("-5"))
        assertNull(WorkoutFormat.parseWeight("1500"))
    }

    @Test
    fun parseReps_range() {
        assertEquals(8, WorkoutFormat.parseReps("8"))
        assertNull(WorkoutFormat.parseReps("0"))
        assertNull(WorkoutFormat.parseReps("101"))
        assertNull(WorkoutFormat.parseReps("8.5"))
    }

    @Test
    fun parseRpe_optionalAndHalfSteps() {
        assertEquals(RpeInput.Empty, WorkoutFormat.parseRpe("  "))
        assertEquals(RpeInput.Value(8.5), WorkoutFormat.parseRpe("8,5"))
        assertEquals(RpeInput.Value(10.0), WorkoutFormat.parseRpe("10"))
        assertEquals(RpeInput.Invalid, WorkoutFormat.parseRpe("8.3"))
        assertEquals(RpeInput.Invalid, WorkoutFormat.parseRpe("11"))
        assertEquals(RpeInput.Invalid, WorkoutFormat.parseRpe("x"))
    }

    @Test
    fun formatWeight_dropsTrailingZeros() {
        assertEquals("80", WorkoutFormat.formatWeight(80.0))
        assertEquals("77.5", WorkoutFormat.formatWeight(77.5))
        assertEquals("33.25", WorkoutFormat.formatWeight(33.25))
        assertEquals("0.3", WorkoutFormat.formatWeight(0.1 + 0.2))
    }

    @Test
    fun formatSetAndRange() {
        assertEquals("80 × 8", WorkoutFormat.formatSet(80.0, 8))
        assertEquals("8–12", WorkoutFormat.formatRepRange(8, 12))
        assertEquals("5", WorkoutFormat.formatRepRange(5, 5))
    }

    @Test
    fun formatDuration_minutesAndHours() {
        assertEquals("0:00", WorkoutFormat.formatDuration(0))
        assertEquals("2:00", WorkoutFormat.formatDuration(120_000))
        assertEquals("45:12", WorkoutFormat.formatDuration((45 * 60 + 12) * 1000L))
        assertEquals("1:05:03", WorkoutFormat.formatDuration((3600 + 5 * 60 + 3) * 1000L))
        assertEquals("0:00", WorkoutFormat.formatDuration(-5000))
    }

    @Test
    fun epley() {
        assertEquals(100.0, WorkoutFormat.epley1Rm(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 8 / 30.0), WorkoutFormat.epley1Rm(100.0, 8), 1e-9)
    }

    @Test
    fun weekdays_roundTrip() {
        val days = setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)
        val stored = WorkoutFormat.storeWeekdays(days)
        assertEquals("MONDAY,THURSDAY", stored)
        assertEquals(days, WorkoutFormat.parseWeekdays(stored))
        assertEquals(emptySet<DayOfWeek>(), WorkoutFormat.parseWeekdays(""))
        assertEquals(setOf(DayOfWeek.MONDAY), WorkoutFormat.parseWeekdays("MONDAY,NOTADAY"))
        assertEquals("Mon, Thu", WorkoutFormat.shortWeekdays(days, Locale.ENGLISH))
    }
}
