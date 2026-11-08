package ro.fitnesshub.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ro.fitnesshub.R
import ro.fitnesshub.data.db.SetEntryEntity
import ro.fitnesshub.data.db.WorkoutSessionEntity
import ro.fitnesshub.gym.Progression
import ro.fitnesshub.gym.SetSuggestions
import ro.fitnesshub.gym.WorkoutFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class ConfirmAction { Finish, Discard }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    pickedExerciseId: Long?,
    onPickedConsumed: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onClose: () -> Unit,
    viewModel: ActiveWorkoutViewModel = viewModel(factory = ActiveWorkoutViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf<ConfirmAction?>(null) }
    val session = state.session
    val readOnly = state.readOnly

    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            viewModel.addExercise(pickedExerciseId)
            onPickedConsumed()
        }
    }
    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(message.text, *message.args.toTypedArray()))
        viewModel.messageShown()
    }

    // Ticks once a second for the elapsed-time subtitle.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(readOnly) {
        while (!readOnly) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.title ?: stringResource(R.string.session_title_free),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (session != null) {
                            val subtitle = if (readOnly) {
                                val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))
                                stringResource(
                                    R.string.session_finished_on,
                                    date,
                                    WorkoutFormat.formatDuration((session.endedAt ?: session.startedAt) - session.startedAt),
                                )
                            } else {
                                WorkoutFormat.formatDuration(now - session.startedAt)
                            }
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirm = ConfirmAction.Discard }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(if (readOnly) R.string.session_delete else R.string.session_discard),
                        )
                    }
                    if (!readOnly) {
                        TextButton(onClick = { confirm = ConfirmAction.Finish }) {
                            Text(stringResource(R.string.session_finish))
                        }
                    }
                },
            )
        },
        bottomBar = {
            state.rest?.let { rest ->
                RestTimerBar(rest, onAdd = { viewModel.addRest(15) }, onSkip = viewModel::skipRest)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.blocks.isEmpty() && !state.loading && !readOnly) {
                item { Text(stringResource(R.string.session_empty)) }
            }
            if (readOnly && session != null) {
                item { WatchStatsCard(session) }
            }
            items(state.blocks, key = { it.exerciseId }) { block ->
                ExerciseCard(
                    block = block,
                    readOnly = readOnly,
                    onOpenProgress = { onOpenExercise(block.exerciseId) },
                    onLog = { weight, reps, rpe, warmup -> viewModel.logSet(block.exerciseId, weight, reps, rpe, warmup) },
                    onDeleteSet = viewModel::deleteSet,
                    onRemove = { viewModel.removeExercise(block.exerciseId) },
                )
            }
            if (!readOnly) {
                item {
                    OutlinedButton(onClick = onAddExercise) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.session_add_exercise), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    confirm?.let { action ->
        val text = when {
            action == ConfirmAction.Finish -> R.string.session_finish_confirm
            readOnly -> R.string.session_delete_confirm
            else -> R.string.session_discard_confirm
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringResource(text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (action == ConfirmAction.Finish) viewModel.finish() else viewModel.discard()
                }) {
                    Text(stringResource(if (action == ConfirmAction.Finish) R.string.session_finish else R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Heart rate and calories from the watch, for a finished workout. */
@Composable
private fun WatchStatsCard(session: WorkoutSessionEntity) {
    val parts = buildList {
        session.avgHr?.let { add(stringResource(R.string.session_watch_avg_hr, it)) }
        session.maxHr?.let { add(stringResource(R.string.session_watch_max_hr, it)) }
        session.kcal?.let { add(stringResource(R.string.session_watch_kcal, it.roundToInt())) }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.session_watch_title), style = MaterialTheme.typography.titleSmall)
            Text(
                if (parts.isEmpty()) stringResource(R.string.session_watch_none) else parts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    block: ExerciseBlock,
    readOnly: Boolean,
    onOpenProgress: () -> Unit,
    onLog: (weight: String, reps: String, rpe: String, warmup: Boolean) -> Boolean,
    onDeleteSet: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).clickable(onClick = onOpenProgress)) {
                    Text(block.name, style = MaterialTheme.typography.titleMedium)
                    if (!readOnly) {
                        Text(
                            stringResource(
                                R.string.session_target,
                                block.targetSets,
                                WorkoutFormat.formatRepRange(block.repsMin, block.repsMax),
                                WorkoutFormat.formatDuration(block.restSeconds * 1000L),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (!readOnly && block.sets.isEmpty()) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                    }
                }
            }

            if (!readOnly && block.previous != null) {
                val last = SetSuggestions.summary(block.previous)
                Text(
                    if (last != null) stringResource(R.string.session_last_time, last) else stringResource(R.string.session_first_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!readOnly && block.hint != null) {
                Text(
                    hintText(block.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            block.sets.forEach { set ->
                SetRow(set, readOnly, isPr = set.id in block.prSetIds, onDelete = { onDeleteSet(set.id) })
            }

            if (!readOnly) {
                SetInput(block, onLog)
            }
        }
    }
}

@Composable
private fun hintText(hint: Progression.Hint): String {
    val weight = WorkoutFormat.formatWeight(hint.weightKg)
    return when (hint) {
        is Progression.Hint.AddWeight -> stringResource(R.string.session_hint_add_weight, weight, hint.reps)
        is Progression.Hint.AddReps -> stringResource(R.string.session_hint_add_reps, weight, hint.reps)
        is Progression.Hint.Repeat -> stringResource(R.string.session_hint_repeat, weight, hint.reps)
    }
}

@Composable
private fun SetRow(set: SetEntryEntity, readOnly: Boolean, isPr: Boolean, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (set.isWarmup) stringResource(R.string.session_set_warmup) else set.setNumber.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (set.isWarmup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        Text(WorkoutFormat.formatSet(set.weightKg, set.reps), style = MaterialTheme.typography.bodyLarge)
        if (isPr) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    stringResource(R.string.session_pr_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        set.rpe?.let {
            Text(stringResource(R.string.session_set_rpe, WorkoutFormat.formatWeight(it)), style = MaterialTheme.typography.bodySmall)
        }
        if (!readOnly) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}

@Composable
private fun SetInput(
    block: ExerciseBlock,
    onLog: (weight: String, reps: String, rpe: String, warmup: Boolean) -> Boolean,
) {
    // Reset to a fresh suggestion after every logged set and once "last time" has loaded.
    val keys = arrayOf<Any>(block.exerciseId, block.sets.size, block.previous == null)
    val suggestion = SetSuggestions.next(block.sets, block.previous.orEmpty(), block.hint)
    var weight by rememberSaveable(*keys) { mutableStateOf(suggestion?.let { WorkoutFormat.formatWeight(it.weightKg) }.orEmpty()) }
    var reps by rememberSaveable(*keys) { mutableStateOf(suggestion?.reps?.toString().orEmpty()) }
    var rpe by rememberSaveable(*keys) { mutableStateOf("") }
    var warmup by rememberSaveable(*keys) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text(stringResource(R.string.session_weight)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1.2f),
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                label = { Text(stringResource(R.string.session_reps)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = rpe,
                onValueChange = { rpe = it },
                label = { Text(stringResource(R.string.session_rpe)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = warmup, onCheckedChange = { warmup = it })
            Text(stringResource(R.string.session_warmup), modifier = Modifier.weight(1f))
            Button(onClick = { onLog(weight, reps, rpe, warmup) }) {
                Text(stringResource(R.string.session_log_set))
            }
        }
    }
}

@Composable
private fun RestTimerBar(rest: RestTimerState, onAdd: () -> Unit, onSkip: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.rest_label, WorkoutFormat.formatDuration(rest.remainingSeconds * 1000L)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAdd) { Text(stringResource(R.string.rest_add)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.rest_skip)) }
            }
            LinearProgressIndicator(
                progress = { rest.remainingSeconds.toFloat() / rest.totalSeconds.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
