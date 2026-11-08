package ro.fitnesshub.gym

import ro.fitnesshub.data.db.SetEntryEntity

/** Pure logic for pre-filling the "log set" inputs. Unit-tested. */
object SetSuggestions {

    data class Suggestion(val weightKg: Double, val reps: Int)

    /**
     * What to pre-fill for the next set:
     * 1. the last working set logged in this session (keep going at the same weight), else
     * 2. the first working set of the previous session with this exercise, else
     * 3. nothing.
     */
    fun next(current: List<SetEntryEntity>, previous: List<SetEntryEntity>): Suggestion? {
        val lastWorking = current.lastOrNull { !it.isWarmup }
        if (lastWorking != null) return Suggestion(lastWorking.weightKg, lastWorking.reps)
        val firstPrevious = previous.firstOrNull { !it.isWarmup } ?: return null
        return Suggestion(firstPrevious.weightKg, firstPrevious.reps)
    }

    /** "80 × 8, 80 × 8, 77.5 × 7" for the working sets; null if there are none. */
    fun summary(sets: List<SetEntryEntity>): String? =
        sets.filter { !it.isWarmup }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { WorkoutFormat.formatSet(it.weightKg, it.reps) }
}
