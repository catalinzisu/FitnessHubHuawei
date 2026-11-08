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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.R
import ro.fitnesshub.data.GymRepository
import ro.fitnesshub.data.db.ExerciseEntity
import ro.fitnesshub.gym.ExerciseLibrary

class ExerciseLibraryViewModel(private val repository: GymRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Exercises matching the search, grouped by main muscle in a fixed order. */
    val groups: StateFlow<List<Pair<String, List<ExerciseEntity>>>> =
        combine(repository.exercises, _query) { exercises, query ->
            val q = query.trim()
            val filtered = if (q.isEmpty()) exercises else exercises.filter { it.name.contains(q, ignoreCase = true) }
            filtered.groupBy { it.primaryMuscle }
                .toList()
                .sortedBy { (muscle, _) -> ExerciseLibrary.MUSCLES.indexOf(muscle).let { if (it < 0) Int.MAX_VALUE else it } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Calls [onDone] with the new id, or with null if the name is taken. */
    fun addCustom(name: String, muscle: String, equipment: String, type: String, onDone: (Long?) -> Unit) {
        viewModelScope.launch { onDone(repository.addCustomExercise(name, muscle, equipment, type)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                ExerciseLibraryViewModel(app.container.gymRepository)
            }
        }
    }
}

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
    viewModel: ExerciseLibraryViewModel = viewModel(factory = ExerciseLibraryViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

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
            FloatingActionButton(onClick = { showAddDialog = true }) {
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
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.exercises_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.exercises_none_found),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            groups.forEach { (muscle, exercises) ->
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
                        modifier = if (pickMode) Modifier.clickable { onPicked(exercise.id) } else Modifier,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddExerciseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, muscle, equipment, type, onResult ->
                viewModel.addCustom(name, muscle, equipment, type) { id ->
                    onResult(id != null)
                    if (id != null) {
                        showAddDialog = false
                        if (pickMode) onPicked(id)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, muscle: String, equipment: String, type: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf(ExerciseLibrary.MUSCLES.first()) }
    var equipment by rememberSaveable { mutableStateOf(ExerciseLibrary.EQUIPMENT.first()) }
    var type by rememberSaveable { mutableStateOf(ExerciseLibrary.TYPES.first()) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }

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
                    onValueChange = { name = it; error = null },
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
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = R.string.exercises_name_required
                } else {
                    onSave(name, muscle, equipment, type) { ok -> if (!ok) error = R.string.exercises_duplicate }
                }
            }) { Text(stringResource(R.string.exercises_save)) }
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
