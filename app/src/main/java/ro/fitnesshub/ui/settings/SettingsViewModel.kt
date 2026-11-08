package ro.fitnesshub.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.R
import ro.fitnesshub.data.HealthRepository
import ro.fitnesshub.data.health.SeedDataWriter
import ro.fitnesshub.util.AppLog

data class UiMessage(@param:StringRes val text: Int, val args: List<Any> = emptyList())

data class SettingsUiState(
    val working: Boolean = false,
    val message: UiMessage? = null,
)

class SettingsViewModel(
    private val seedDataWriter: SeedDataWriter,
    private val repository: HealthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun seedTestData() = runTool {
        if (!seedDataWriter.hasWritePermissions()) {
            UiMessage(R.string.seed_missing_permission)
        } else {
            val count = seedDataWriter.seed()
            repository.syncDay()
            UiMessage(R.string.seed_done, listOf(count))
        }
    }

    fun clearTestData() = runTool {
        if (!seedDataWriter.hasWritePermissions()) {
            UiMessage(R.string.seed_missing_permission)
        } else {
            seedDataWriter.clear()
            repository.syncDay()
            UiMessage(R.string.seed_cleared)
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun runTool(block: suspend () -> UiMessage) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Test data tool failed", e)
                UiMessage(R.string.seed_failed)
            }
            _state.update { it.copy(working = false, message = message) }
        }
    }

    companion object {
        private const val TAG = "Settings"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                SettingsViewModel(app.container.seedDataWriter, app.container.healthRepository)
            }
        }
    }
}
