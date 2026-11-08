package ro.fitnesshub.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.fitnesshub.R
import ro.fitnesshub.gym.WorkoutFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    pickedExerciseId: Long?,
    onPickedConsumed: () -> Unit,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
    viewModel: TemplateEditorViewModel = viewModel(factory = TemplateEditorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            viewModel.addExercise(pickedExerciseId)
            onPickedConsumed()
        }
    }
    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.template_new else R.string.template_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.template_delete))
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = !state.loading) {
                        Text(stringResource(R.string.template_save))
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
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.template_name)) },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.template_weekdays), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in state.weekdays,
                                onClick = { viewModel.toggleWeekday(day) },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.template_exercises), style = MaterialTheme.typography.titleSmall)
            }
            if (state.items.isEmpty() && !state.loading) {
                item { Text(stringResource(R.string.template_no_exercises)) }
            }
            itemsIndexed(state.items, key = { _, item -> item.exerciseId }) { index, item ->
                TemplateItemCard(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.items.lastIndex,
                    onMove = { delta -> viewModel.moveItem(index, delta) },
                    onRemove = { viewModel.removeItem(index) },
                    onChange = { change -> viewModel.updateItem(index, change) },
                )
            }
            item {
                OutlinedButton(onClick = onAddExercise) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.template_add_exercise), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.template_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TemplateItemCard(
    item: TemplateItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onChange: ((TemplateItem) -> TemplateItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper(stringResource(R.string.template_sets), item.sets.toString(), Modifier.weight(1f)) { d ->
                    onChange { it.copy(sets = it.sets + d) }
                }
                Stepper(stringResource(R.string.template_rest), WorkoutFormat.formatDuration(item.restSeconds * 1000L), Modifier.weight(1f)) { d ->
                    onChange { it.copy(restSeconds = it.restSeconds + d * 15) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper(stringResource(R.string.template_reps_min), item.repsMin.toString(), Modifier.weight(1f)) { d ->
                    onChange { it.copy(repsMin = it.repsMin + d, repsMax = maxOf(it.repsMax, it.repsMin + d)) }
                }
                Stepper(stringResource(R.string.template_reps_max), item.repsMax.toString(), Modifier.weight(1f)) { d ->
                    onChange { it.copy(repsMax = it.repsMax + d) }
                }
            }
        }
    }
}

/** Label + [−] value [+]. Calls [onStep] with -1 or +1. */
@Composable
private fun Stepper(label: String, value: String, modifier: Modifier = Modifier, onStep: (Int) -> Unit) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onStep(-1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(value, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onStep(1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
