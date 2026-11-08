package ro.fitnesshub.gym

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

/** Pure helpers for the gym logger (parsing user input, formatting, simple maths). Unit-tested. */
object WorkoutFormat {

    const val MAX_WEIGHT_KG = 1000.0
    const val MAX_REPS = 100

    /** Accepts "80", "77.5" or "77,5". Returns null for anything that isn't a sensible weight. */
    fun parseWeight(text: String): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return value.takeIf { it in 0.0..MAX_WEIGHT_KG }
    }

    fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_REPS }

    /** RPE is optional: blank → [RpeInput.Empty]; 1–10 in steps of 0.5 → [RpeInput.Value]. */
    fun parseRpe(text: String): RpeInput {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return RpeInput.Empty
        val value = trimmed.replace(',', '.').toDoubleOrNull() ?: return RpeInput.Invalid
        val validStep = (value * 2).let { it == it.roundToLong().toDouble() }
        return if (value in 1.0..10.0 && validStep) RpeInput.Value(value) else RpeInput.Invalid
    }

    /** 80.0 → "80", 77.5 → "77.5", 33.25 → "33.25". */
    fun formatWeight(kg: Double): String {
        val rounded = (kg * 100).roundToLong() / 100.0
        return if (rounded == rounded.roundToLong().toDouble()) {
            rounded.roundToLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }

    fun formatSet(weightKg: Double, reps: Int): String = "${formatWeight(weightKg)} × $reps"

    /** "8–12" or "8" when min == max. */
    fun formatRepRange(min: Int, max: Int): String = if (min == max) "$min" else "$min–$max"

    /** 45 min 12 s → "45:12"; 1 h 5 min 3 s → "1:05:03". */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** Estimated one-rep max (Epley). Most reliable for sets of about 10 reps or fewer. */
    fun epley1Rm(weightKg: Double, reps: Int): Double =
        if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

    // ---- Weekdays stored as "MONDAY,THURSDAY" ----

    fun parseWeekdays(stored: String): Set<DayOfWeek> =
        stored.split(',')
            .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
            .toSet()

    fun storeWeekdays(days: Set<DayOfWeek>): String = days.sorted().joinToString(",") { it.name }

    /** "Mon, Thu" in the given locale. */
    fun shortWeekdays(days: Set<DayOfWeek>, locale: Locale = Locale.getDefault()): String =
        days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

sealed interface RpeInput {
    data object Empty : RpeInput
    data object Invalid : RpeInput
    data class Value(val rpe: Double) : RpeInput
}
