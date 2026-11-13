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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.R
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.ExerciseEntity
import ro.fitnesshub.data.db.ExerciseHistorySet
import ro.fitnesshub.gym.LiftSet
import ro.fitnesshub.gym.Progression
import ro.fitnesshub.gym.WorkoutFormat
import ro.fitnesshub.ui.common.ChartKind
import ro.fitnesshub.ui.common.SimpleChart
import ro.fitnesshub.ui.navigation.ExerciseProgressRoute
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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

/** How many recent sessions the charts show. */
private const val CHART_SESSIONS = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: ExerciseProgressViewModel = viewModel(factory = ExerciseProgressViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val shortDate = DateTimeFormatter.ofPattern("d MMM")
    val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    fun date(millis: Long, format: DateTimeFormatter) = Instant.ofEpochMilli(millis).atZone(zone).format(format)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.exercise?.name ?: stringResource(R.string.progress_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val records = state.records
            if (!state.loading && records == null) {
                item { Text(stringResource(R.string.progress_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            if (records != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecordCard(
                            label = stringResource(R.string.progress_best_1rm),
                            value = records.best1Rm?.let { stringResource(R.string.progress_kg, WorkoutFormat.formatWeight(roundHalf(it))) } ?: "–",
                            modifier = Modifier.weight(1f),
                        )
                        RecordCard(
                            label = stringResource(R.string.progress_heaviest),
                            value = records.heaviest?.let { WorkoutFormat.formatSet(it.weightKg, it.reps) } ?: "–",
                            modifier = Modifier.weight(1f),
                        )
                        RecordCard(
                            label = stringResource(R.string.progress_best_volume),
                            value = records.bestVolumeKg?.let { stringResource(R.string.progress_kg, it.roundToInt().toString()) } ?: "–",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Text(
                        pluralStringResource(R.plurals.progress_sessions, records.sessions, records.sessions),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                val recent = state.points.takeLast(CHART_SESSIONS)
                val withOneRm = recent.filter { it.best1Rm != null }
                if (withOneRm.isNotEmpty()) {
                    item {
                        ChartCard(stringResource(R.string.progress_chart_1rm), stringResource(R.string.progress_chart_1rm_note)) {
                            SimpleChart(
                                values = withOneRm.map { it.best1Rm!! },
                                kind = ChartKind.Line,
                                formatValue = { WorkoutFormat.formatWeight(roundHalf(it)) },
                                startLabel = date(withOneRm.first().startedAt, shortDate),
                                endLabel = date(withOneRm.last().startedAt, shortDate),
                                description = stringResource(R.string.progress_chart_1rm),
                            )
                        }
                    }
                }
                item {
                    ChartCard(stringResource(R.string.progress_chart_volume), stringResource(R.string.progress_chart_volume_note)) {
                        SimpleChart(
                            values = recent.map { it.volumeKg },
                            kind = ChartKind.Bars,
                            formatValue = { it.roundToInt().toString() },
                            startLabel = date(recent.first().startedAt, shortDate),
                            endLabel = date(recent.last().startedAt, shortDate),
                            description = stringResource(R.string.progress_chart_volume),
                        )
                    }
                }

                item { Text(stringResource(R.string.progress_history), style = MaterialTheme.typography.titleMedium) }
                items(state.points.asReversed(), key = { it.sessionId }) { point ->
                    ListItem(
                        overlineContent = { Text(date(point.startedAt, longDate)) },
                        headlineContent = { Text(point.sets.joinToString(", ") { WorkoutFormat.formatSet(it.weightKg, it.reps) }) },
                        supportingContent = {
                            point.best1Rm?.let {
                                Text(stringResource(R.string.progress_session_1rm, WorkoutFormat.formatWeight(roundHalf(it))))
                            }
                        },
                        modifier = Modifier.clickable { onOpenSession(point.sessionId) },
                    )
                }
            }
        }
    }
}

/** 1RM estimates are shown to the nearest 0.5 kg; more precision would be fake. */
private fun roundHalf(kg: Double): Double = (kg * 2).roundToInt() / 2.0

@Composable
private fun RecordCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ChartCard(title: String, note: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
