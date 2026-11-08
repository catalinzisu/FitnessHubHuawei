package ro.fitnesshub.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.R
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.SessionSummary
import ro.fitnesshub.data.db.TemplateSummary
import ro.fitnesshub.data.db.WorkoutSessionEntity
import ro.fitnesshub.gym.WorkoutFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class WorkoutHomeUiState(
    val activeSession: WorkoutSessionEntity? = null,
    val today: DayOfWeek = LocalDate.now().dayOfWeek,
    val todaysTemplates: List<TemplateSummary> = emptyList(),
    val templates: List<TemplateSummary> = emptyList(),
    val recent: List<SessionSummary> = emptyList(),
)

class WorkoutHomeViewModel(private val repository: GymRepository) : ViewModel() {

    val state: StateFlow<WorkoutHomeUiState> = combine(
        repository.activeSession,
        repository.templates,
        repository.recentSessions(limit = 10),
    ) { active, templates, recent ->
        val today = LocalDate.now().dayOfWeek
        WorkoutHomeUiState(
            activeSession = active,
            today = today,
            todaysTemplates = templates.filter { today in WorkoutFormat.parseWeekdays(it.template.weekdays) },
            templates = templates,
            recent = recent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHomeUiState())

    /** Starts a workout (or reuses the one in progress) and passes its id to [onStarted]. */
    fun start(templateId: Long?, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repository.startWorkout(templateId)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHomeScreen(
    onOpenSession: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: WorkoutHomeViewModel = viewModel(factory = WorkoutHomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val time = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_workout)) },
                actions = {
                    TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.workout_exercise_library)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Workout in progress
            state.activeSession?.let { active ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.workout_in_progress), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.workout_started_at,
                                        Instant.ofEpochMilli(active.startedAt).atZone(zone).format(time),
                                        WorkoutFormat.formatDuration(System.currentTimeMillis() - active.startedAt),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = { onOpenSession(active.id) }) { Text(stringResource(R.string.workout_resume)) }
                        }
                    }
                }
            }

            // Today
            item {
                Text(
                    stringResource(R.string.workout_today, state.today.getDisplayName(TextStyle.FULL, Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.todaysTemplates.isEmpty()) {
                item { Text(stringResource(R.string.workout_rest_day), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.todaysTemplates, key = { "today-${it.template.id}" }) { summary ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(summary.template.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(R.plurals.exercise_count, summary.exerciseCount, summary.exerciseCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = { viewModel.start(summary.template.id, onOpenSession) },
                            enabled = state.activeSession == null,
                        ) { Text(stringResource(R.string.workout_start)) }
                    }
                }
            }
            if (state.activeSession == null) {
                item {
                    OutlinedButton(onClick = { viewModel.start(null, onOpenSession) }) {
                        Text(stringResource(R.string.workout_start_empty))
                    }
                }
            }

            // Training days
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.workout_training_days),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onEditTemplate(0L) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.workout_new_day), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (state.templates.isEmpty()) {
                item { Text(stringResource(R.string.workout_no_days), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.templates, key = { "day-${it.template.id}" }) { summary ->
                val days = WorkoutFormat.parseWeekdays(summary.template.weekdays)
                ListItem(
                    headlineContent = { Text(summary.template.name) },
                    supportingContent = {
                        Text(
                            (if (days.isEmpty()) stringResource(R.string.workout_no_weekdays) else WorkoutFormat.shortWeekdays(days)) +
                                " · " + pluralStringResource(R.plurals.exercise_count, summary.exerciseCount, summary.exerciseCount),
                        )
                    },
                    modifier = Modifier.clickable { onEditTemplate(summary.template.id) },
                )
            }

            // History
            item { Text(stringResource(R.string.workout_recent), style = MaterialTheme.typography.titleMedium) }
            if (state.recent.isEmpty()) {
                item { Text(stringResource(R.string.workout_no_recent), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.recent, key = { "recent-${it.id}" }) { session ->
                val date = Instant.ofEpochMilli(session.startedAt).atZone(zone)
                    .format(DateTimeFormatter.ofPattern("EEE d MMM"))
                val duration = WorkoutFormat.formatDuration((session.endedAt ?: session.startedAt) - session.startedAt)
                ListItem(
                    headlineContent = { Text(session.templateName ?: stringResource(R.string.workout_free)) },
                    overlineContent = { Text("$date · $duration") },
                    supportingContent = {
                        Text(
                            pluralStringResource(R.plurals.exercise_count, session.exerciseCount, session.exerciseCount) +
                                " · " + pluralStringResource(R.plurals.working_set_count, session.workingSets, session.workingSets),
                        )
                    },
                    modifier = Modifier.clickable { onOpenSession(session.id) },
                )
            }
        }
    }
}
