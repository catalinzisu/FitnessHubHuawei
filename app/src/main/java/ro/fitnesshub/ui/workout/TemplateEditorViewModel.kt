package ro.fitnesshub.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.R
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.TemplateExerciseEntity
import ro.fitnesshub.data.db.WorkoutTemplateEntity
import ro.fitnesshub.gym.WorkoutFormat
import java.time.DayOfWeek

data class TemplateItem(
    val exerciseId: Long,
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int,
)

data class TemplateEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val weekdays: Set<DayOfWeek> = emptySet(),
    val items: List<TemplateItem> = emptyList(),
    val nameError: Int? = null,
    val closed: Boolean = false,
)

class TemplateEditorViewModel(
    private val repository: GymRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val templateId: Long = savedStateHandle.get<Long>(ARG_TEMPLATE_ID) ?: 0L

    private val _state = MutableStateFlow(TemplateEditorUiState(isNew = templateId == 0L))
    val state: StateFlow<TemplateEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (templateId != 0L) {
                val template = repository.getTemplate(templateId)
                val rows = repository.getTemplateExercises(templateId)
                if (template != null) {
                    _state.update {
                        it.copy(
                            name = template.name,
                            weekdays = WorkoutFormat.parseWeekdays(template.weekdays),
                            items = rows.map { row ->
                                TemplateItem(
                                    exerciseId = row.item.exerciseId,
                                    name = row.exerciseName,
                                    sets = row.item.targetSets,
                                    repsMin = row.item.targetRepsMin,
                                    repsMax = row.item.targetRepsMax,
                                    restSeconds = row.item.restSeconds,
                                )
                            },
                        )
                    }
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }

    fun toggleWeekday(day: DayOfWeek) = _state.update {
        it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day)
    }

    fun addExercise(exerciseId: Long) {
        if (_state.value.items.any { it.exerciseId == exerciseId }) return
        viewModelScope.launch {
            val exercise = repository.exercises.first().firstOrNull { it.id == exerciseId } ?: return@launch
            _state.update {
                it.copy(
                    items = it.items + TemplateItem(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        sets = GymRepository.DEFAULT_SETS,
                        repsMin = GymRepository.DEFAULT_REPS_MIN,
                        repsMax = GymRepository.DEFAULT_REPS_MAX,
                        restSeconds = GymRepository.DEFAULT_REST_SECONDS,
                    ),
                )
            }
        }
    }

    fun removeItem(index: Int) = _state.update { it.copy(items = it.items.filterIndexed { i, _ -> i != index }) }

    fun moveItem(index: Int, delta: Int) = _state.update {
        val target = index + delta
        if (target !in it.items.indices) return@update it
        val list = it.items.toMutableList()
        list[index] = list[target].also { list[target] = list[index] }
        it.copy(items = list)
    }

    /** Applies [change] to one item and keeps the numbers in sensible ranges. */
    fun updateItem(index: Int, change: (TemplateItem) -> TemplateItem) = _state.update {
        it.copy(
            items = it.items.mapIndexed { i, item ->
                if (i != index) {
                    item
                } else {
                    val c = change(item)
                    val repsMin = c.repsMin.coerceIn(1, 50)
                    c.copy(
                        sets = c.sets.coerceIn(1, 10),
                        repsMin = repsMin,
                        repsMax = c.repsMax.coerceIn(repsMin, 50),
                        restSeconds = c.restSeconds.coerceIn(0, 600),
                    )
                }
            },
        )
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = R.string.template_name_required) }
            return
        }
        viewModelScope.launch {
            repository.saveTemplate(
                WorkoutTemplateEntity(
                    id = templateId,
                    name = s.name.trim(),
                    weekdays = WorkoutFormat.storeWeekdays(s.weekdays),
                ),
                s.items.map {
                    TemplateExerciseEntity(
                        templateId = templateId,
                        exerciseId = it.exerciseId,
                        position = 0,
                        targetSets = it.sets,
                        targetRepsMin = it.repsMin,
                        targetRepsMax = it.repsMax,
                        restSeconds = it.restSeconds,
                    )
                },
            )
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        if (templateId == 0L) {
            _state.update { it.copy(closed = true) }
            return
        }
        viewModelScope.launch {
            repository.deleteTemplate(templateId)
            _state.update { it.copy(closed = true) }
        }
    }

    companion object {
        const val ARG_TEMPLATE_ID = "templateId"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                TemplateEditorViewModel(app.container.gymRepository, createSavedStateHandle())
            }
        }
    }
}
