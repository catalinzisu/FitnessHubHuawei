package ro.fitnesshub.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.ExerciseEntity
import ro.fitnesshub.data.db.ExerciseHistorySet
import ro.fitnesshub.gym.LiftSet
import ro.fitnesshub.gym.Progression
import ro.fitnesshub.ui.navigation.ExerciseProgressRoute

data class ExerciseProgressUiState(
    val loading: Boolean = true,
    val exercise: ExerciseEntity? = null,
    /** Finished sessions with at least one working set, oldest first. */
    val points: List<Progression.SessionPoint> = emptyList(),
    val records: Progression.Records? = null,
)

class ExerciseProgressViewModel(
    repository: GymRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle.toRoute<ExerciseProgressRoute>().exerciseId

    val state: StateFlow<ExerciseProgressUiState> = combine(
        flow { emit(repository.getExercise(exerciseId)) },
        repository.observeExerciseHistory(exerciseId),
    ) { exercise, history ->
        val points = toPoints(history)
        ExerciseProgressUiState(
            loading = false,
            exercise = exercise,
            points = points,
            records = points.takeIf { it.isNotEmpty() }?.let(Progression::records),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseProgressUiState())

    private fun toPoints(history: List<ExerciseHistorySet>): List<Progression.SessionPoint> =
        history.filter { !it.isWarmup }
            .groupBy { it.sessionId } // keeps the query's oldest-first order
            .map { (sessionId, sets) ->
                Progression.SessionPoint(sessionId, sets.first().startedAt, sets.map { LiftSet(it.weightKg, it.reps) })
            }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                ExerciseProgressViewModel(app.container.gymRepository, createSavedStateHandle())
            }
        }
    }
}
