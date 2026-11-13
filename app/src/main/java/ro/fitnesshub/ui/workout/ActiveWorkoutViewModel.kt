package ro.fitnesshub.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.R
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.SessionExerciseRow
import ro.fitnesshub.data.db.SetEntryEntity
import ro.fitnesshub.data.db.WorkoutSessionEntity
import ro.fitnesshub.gym.LiftSet
import ro.fitnesshub.gym.Progression
import ro.fitnesshub.gym.RpeInput
import ro.fitnesshub.gym.SetSuggestions
import ro.fitnesshub.gym.WorkoutFormat
import ro.fitnesshub.ui.common.UiMessage
import ro.fitnesshub.ui.navigation.SessionRoute
import ro.fitnesshub.util.Haptics

data class ExerciseBlock(
    val exerciseId: Long,
    val name: String,
    val targetSets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int,
    val equipment: String,
    val sets: List<SetEntryEntity>,
    /** Sets from the last finished session with this exercise; null while loading. */
    val previous: List<SetEntryEntity>?,
    /** What to aim for today, from the last session; null without history. */
    val hint: Progression.Hint?,
    /** Ids of today's sets that were a personal record when logged. */
    val prSetIds: Set<Long>,
)

/** What earlier workouts say about one exercise; loaded once per exercise. */
private data class ExerciseHistory(
    val previous: List<SetEntryEntity>,
    /** Every earlier working set, for PR detection. */
    val allWorking: List<LiftSet>,
)

data class RestTimerState(val remainingSeconds: Int, val totalSeconds: Int)

data class ActiveWorkoutUiState(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val title: String? = null,
    val blocks: List<ExerciseBlock> = emptyList(),
    val rest: RestTimerState? = null,
    val message: UiMessage? = null,
    val closed: Boolean = false,
) {
    /** Finished sessions are shown as history and can't be edited. */
    val readOnly: Boolean get() = session?.endedAt != null
}

private data class Extras(
    val rest: RestTimerState?,
    val message: UiMessage?,
    val closed: Boolean,
    val title: String?,
)

class ActiveWorkoutViewModel(
    private val repository: GymRepository,
    private val haptics: Haptics,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.toRoute<SessionRoute>().sessionId

    private val history = MutableStateFlow<Map<Long, ExerciseHistory>>(emptyMap())
    private val rest = MutableStateFlow<RestTimerState?>(null)
    private val message = MutableStateFlow<UiMessage?>(null)
    private val closed = MutableStateFlow(false)
    private val title = MutableStateFlow<String?>(null)
    private var restJob: Job? = null

    private val extras = combine(rest, message, closed, title, ::Extras)

    val state: StateFlow<ActiveWorkoutUiState> = combine(
        repository.observeSession(sessionId),
        repository.observeSessionExercises(sessionId),
        repository.observeSets(sessionId),
        history,
        extras,
    ) { session, exercises, sets, histories, extra ->
        ActiveWorkoutUiState(
            loading = false,
            session = session,
            title = extra.title,
            blocks = buildBlocks(exercises, sets, histories),
            rest = extra.rest,
            message = extra.message,
            // A deleted session also closes the screen.
            closed = extra.closed || session == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    init {
        viewModelScope.launch {
            val session = repository.observeSession(sessionId).filterNotNull().first()
            title.value = repository.getTemplateName(session.templateId)
            // A finished workout opened later may now have the watch data that wasn't synced yet.
            if (session.endedAt != null) repository.attachWatchStats(session.id)
        }
        // Load "last time" numbers and earlier sets for every exercise in the session, once each.
        viewModelScope.launch {
            repository.observeSessionExercises(sessionId).collect { rows ->
                rows.map { it.item.exerciseId }
                    .filter { it !in history.value }
                    .forEach { exerciseId ->
                        val loaded = ExerciseHistory(
                            previous = repository.previousSets(exerciseId, sessionId),
                            allWorking = repository.exerciseHistory(exerciseId, sessionId)
                                .filter { !it.isWarmup }
                                .map { LiftSet(it.weightKg, it.reps) },
                        )
                        history.update { it + (exerciseId to loaded) }
                    }
            }
        }
    }

    private fun buildBlocks(
        exercises: List<SessionExerciseRow>,
        sets: List<SetEntryEntity>,
        histories: Map<Long, ExerciseHistory>,
    ): List<ExerciseBlock> {
        val setsByExercise = sets.groupBy { it.exerciseId }
        return exercises.map { row ->
            val exerciseSets = setsByExercise[row.item.exerciseId].orEmpty().sortedBy { it.setNumber }
            val history = histories[row.item.exerciseId]
            ExerciseBlock(
                exerciseId = row.item.exerciseId,
                name = row.exerciseName,
                targetSets = row.item.targetSets,
                repsMin = row.item.targetRepsMin,
                repsMax = row.item.targetRepsMax,
                restSeconds = row.item.restSeconds,
                equipment = row.equipment,
                sets = exerciseSets,
                previous = history?.previous,
                hint = history?.let {
                    Progression.hint(
                        SetSuggestions.workingSets(it.previous),
                        row.item.targetRepsMin,
                        row.item.targetRepsMax,
                        row.equipment,
                    )
                },
                prSetIds = history?.let { prSetIds(exerciseSets, it.allWorking) }.orEmpty(),
            )
        }
    }

    private fun prSetIds(sets: List<SetEntryEntity>, earlier: List<LiftSet>): Set<Long> {
        val working = sets.filter { !it.isWarmup }
        return Progression.prIndices(working.map { LiftSet(it.weightKg, it.reps) }, earlier)
            .map { working[it].id }
            .toSet()
    }

    /** Validates the inputs and logs a set. Returns true if it was saved. */
    fun logSet(exerciseId: Long, weightText: String, repsText: String, rpeText: String, isWarmup: Boolean): Boolean {
        val weight = WorkoutFormat.parseWeight(weightText)
            ?: return fail(R.string.session_invalid_weight)
        val reps = WorkoutFormat.parseReps(repsText)
            ?: return fail(R.string.session_invalid_reps)
        val rpe = when (val parsed = WorkoutFormat.parseRpe(rpeText)) {
            RpeInput.Empty -> null
            RpeInput.Invalid -> return fail(R.string.session_invalid_rpe)
            is RpeInput.Value -> parsed.rpe
        }
        viewModelScope.launch {
            repository.logSet(sessionId, exerciseId, weight, reps, rpe, isWarmup)
        }
        if (!isWarmup && isNewPr(exerciseId, LiftSet(weight, reps))) {
            message.value = UiMessage(R.string.session_new_pr)
        }
        if (!isWarmup) {
            val restSeconds = state.value.blocks.firstOrNull { it.exerciseId == exerciseId }?.restSeconds
                ?: GymRepository.DEFAULT_REST_SECONDS
            startRest(restSeconds)
        }
        return true
    }

    private fun isNewPr(exerciseId: Long, set: LiftSet): Boolean {
        val earlier = history.value[exerciseId]?.allWorking ?: return false
        val today = state.value.blocks.firstOrNull { it.exerciseId == exerciseId }
            ?.let { SetSuggestions.workingSets(it.sets) }
            .orEmpty()
        val all = today + set
        return all.lastIndex in Progression.prIndices(all, earlier)
    }

    private fun fail(text: Int): Boolean {
        message.value = UiMessage(text)
        return false
    }

    fun deleteSet(id: Long) {
        viewModelScope.launch { repository.deleteSet(id) }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch { repository.addExerciseToSession(sessionId, exerciseId) }
    }

    fun removeExercise(exerciseId: Long) {
        viewModelScope.launch { repository.removeExerciseFromSession(sessionId, exerciseId) }
    }

    fun finish() {
        skipRest()
        viewModelScope.launch {
            repository.finishWorkout(sessionId)
            closed.value = true
        }
    }

    fun discard() {
        skipRest()
        viewModelScope.launch {
            repository.discardWorkout(sessionId)
            closed.value = true
        }
    }

    fun messageShown() {
        message.value = null
    }

    // ---- Rest timer ----

    private fun startRest(seconds: Int) {
        restJob?.cancel()
        if (seconds <= 0) {
            rest.value = null
            return
        }
        rest.value = RestTimerState(seconds, seconds)
        restJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val current = rest.value ?: break
                if (current.remainingSeconds <= 1) {
                    rest.value = null
                    haptics.restFinished()
                    break
                }
                rest.value = current.copy(remainingSeconds = current.remainingSeconds - 1)
            }
        }
    }

    fun addRest(seconds: Int) = rest.update {
        it?.copy(remainingSeconds = it.remainingSeconds + seconds, totalSeconds = it.totalSeconds + seconds)
    }

    fun skipRest() {
        restJob?.cancel()
        rest.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                ActiveWorkoutViewModel(
                    app.container.gymRepository,
                    app.container.haptics,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
