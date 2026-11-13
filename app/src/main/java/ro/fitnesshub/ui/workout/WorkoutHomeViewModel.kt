package ro.fitnesshub.ui.workout

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
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.SessionSummary
import ro.fitnesshub.data.db.TemplateSummary
import ro.fitnesshub.data.db.WorkoutSessionEntity
import ro.fitnesshub.gym.WorkoutFormat
import java.time.DayOfWeek
import java.time.LocalDate

data class WorkoutHomeUiState(
    val activeSession: WorkoutSessionEntity? = null,
    val today: DayOfWeek = LocalDate.now().dayOfWeek,
    val todaysTemplates: List<TemplateSummary> = emptyList(),
    val templates: List<TemplateSummary> = emptyList(),
    val recent: List<SessionSummary> = emptyList(),
    /** One-shot event: a workout was just started and its screen should open. */
    val startedSessionId: Long? = null,
)

class WorkoutHomeViewModel(private val repository: GymRepository) : ViewModel() {

    private val startedSessionId = MutableStateFlow<Long?>(null)

    val state: StateFlow<WorkoutHomeUiState> = combine(
        repository.activeSession,
        repository.templates,
        repository.recentSessions(limit = 10),
        startedSessionId,
    ) { active, templates, recent, started ->
        val today = LocalDate.now().dayOfWeek
        WorkoutHomeUiState(
            activeSession = active,
            today = today,
            todaysTemplates = templates.filter { today in WorkoutFormat.parseWeekdays(it.template.weekdays) },
            templates = templates,
            recent = recent,
            startedSessionId = started,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHomeUiState())

    /** Starts a workout (or reuses the one in progress); the screen opens it via [WorkoutHomeUiState.startedSessionId]. */
    fun start(templateId: Long?) {
        viewModelScope.launch { startedSessionId.value = repository.startWorkout(templateId) }
    }

    /** The screen calls this once it has opened the started workout, so it isn't opened twice. */
    fun startedSessionOpened() {
        startedSessionId.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                WorkoutHomeViewModel(app.container.gymRepository)
            }
        }
    }
}
