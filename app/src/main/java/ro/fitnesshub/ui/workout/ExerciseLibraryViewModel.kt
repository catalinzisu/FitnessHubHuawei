package ro.fitnesshub.ui.workout

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.R
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.ExerciseEntity
import ro.fitnesshub.gym.ExerciseLibrary

/** The "add custom exercise" dialog. [error] is the message of the last failed save, if any. */
data class AddExerciseState(@param:StringRes val error: Int? = null)

data class ExerciseLibraryUiState(
    val query: String = "",
    /** Exercises matching the search, grouped by main muscle in a fixed order. */
    val groups: List<Pair<String, List<ExerciseEntity>>> = emptyList(),
    /** Null when the add dialog is closed. */
    val addDialog: AddExerciseState? = null,
    /** One-shot event: a custom exercise was just created (in pick mode it is returned to the caller). */
    val createdExerciseId: Long? = null,
)

/** What the user controls on this screen, kept apart from the exercise list that comes from Room. */
private data class LibraryInput(
    val query: String = "",
    val addDialog: AddExerciseState? = null,
    val createdExerciseId: Long? = null,
)

class ExerciseLibraryViewModel(private val repository: GymRepository) : ViewModel() {

    private val input = MutableStateFlow(LibraryInput())

    val state: StateFlow<ExerciseLibraryUiState> =
        combine(repository.exercises, input) { exercises, input ->
            val q = input.query.trim()
            val filtered = if (q.isEmpty()) exercises else exercises.filter { it.name.contains(q, ignoreCase = true) }
            ExerciseLibraryUiState(
                query = input.query,
                groups = filtered.groupBy { it.primaryMuscle }
                    .toList()
                    .sortedBy { (muscle, _) -> ExerciseLibrary.MUSCLES.indexOf(muscle).let { if (it < 0) Int.MAX_VALUE else it } },
                addDialog = input.addDialog,
                createdExerciseId = input.createdExerciseId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState())

    fun onQueryChange(value: String) = input.update { it.copy(query = value) }

    // ---- Add custom exercise ----

    fun openAddDialog() = input.update { it.copy(addDialog = AddExerciseState()) }

    fun closeAddDialog() = input.update { it.copy(addDialog = null) }

    /** Hides the old error as soon as the user changes the name. */
    fun onAddNameChanged() = input.update { it.copy(addDialog = it.addDialog?.copy(error = null)) }

    /** Saves the exercise, or keeps the dialog open with an error (empty or duplicate name). */
    fun addCustom(name: String, muscle: String, equipment: String, type: String) {
        if (name.isBlank()) {
            input.update { it.copy(addDialog = AddExerciseState(R.string.exercises_name_required)) }
            return
        }
        viewModelScope.launch {
            val id = repository.addCustomExercise(name, muscle, equipment, type)
            input.update {
                if (id == null) {
                    it.copy(addDialog = AddExerciseState(R.string.exercises_duplicate))
                } else {
                    it.copy(addDialog = null, createdExerciseId = id)
                }
            }
        }
    }

    /** The screen calls this once it has handled [ExerciseLibraryUiState.createdExerciseId]. */
    fun createdExerciseHandled() = input.update { it.copy(createdExerciseId = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                ExerciseLibraryViewModel(app.container.gymRepository)
            }
        }
    }
}
