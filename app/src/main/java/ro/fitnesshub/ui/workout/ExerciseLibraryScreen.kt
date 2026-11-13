package ro.fitnesshub.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.fitnesshub.R
import ro.fitnesshub.gym.ExerciseLibrary

/**
 * Exercise list. In pick mode, tapping an exercise returns it to the previous screen
 * (training-day editor or active workout).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    pickMode: Boolean,
    onBack: () -> Unit,
    onPicked: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    viewModel: ExerciseLibraryViewModel = viewModel(factory = ExerciseLibraryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // One-shot event: a new custom exercise was saved. In pick mode it goes straight back to the caller.
    LaunchedEffect(state.createdExerciseId) {
        val id = state.createdExerciseId ?: return@LaunchedEffect
        viewModel.createdExerciseHandled()
        if (pickMode) onPicked(id)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (pickMode) R.string.exercises_pick_title else R.string.exercises_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddDialog) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.exercises_add_custom))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.exercises_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            if (state.groups.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.exercises_none_found),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            state.groups.forEach { (muscle, exercises) ->
                item(key = "header-$muscle") {
                    Text(
                        ExerciseLibrary.label(muscle),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(exercises, key = { it.id }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text("${ExerciseLibrary.label(exercise.equipment)} · ${exercise.type}")
                        },
                        trailingContent = if (exercise.isCustom) {
                            { Text(stringResource(R.string.exercises_custom), style = MaterialTheme.typography.labelSmall) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { if (pickMode) onPicked(exercise.id) else onOpenExercise(exercise.id) },
                    )
                }
            }
        }
    }

    state.addDialog?.let { dialog ->
        AddExerciseDialog(
            error = dialog.error,
            onNameChanged = viewModel::onAddNameChanged,
            onDismiss = viewModel::closeAddDialog,
            onSave = viewModel::addCustom,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseDialog(
    error: Int?,
    onNameChanged: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (name: String, muscle: String, equipment: String, type: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf(ExerciseLibrary.MUSCLES.first()) }
    var equipment by rememberSaveable { mutableStateOf(ExerciseLibrary.EQUIPMENT.first()) }
    var type by rememberSaveable { mutableStateOf(ExerciseLibrary.TYPES.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercises_add_custom)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onNameChanged()
                    },
                    label = { Text(stringResource(R.string.exercises_name)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipGroup(stringResource(R.string.exercises_muscle), ExerciseLibrary.MUSCLES, muscle) { muscle = it }
                ChipGroup(stringResource(R.string.exercises_equipment), ExerciseLibrary.EQUIPMENT, equipment) { equipment = it }
                ChipGroup(stringResource(R.string.exercises_type), ExerciseLibrary.TYPES, type) { type = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, muscle, equipment, type) }) { Text(stringResource(R.string.exercises_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(ExerciseLibrary.label(option)) },
                )
            }
        }
    }
}
