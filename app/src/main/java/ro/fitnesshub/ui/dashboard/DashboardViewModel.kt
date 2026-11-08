package ro.fitnesshub.ui.dashboard

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.data.HealthRepository
import ro.fitnesshub.data.db.DailySummaryEntity
import ro.fitnesshub.data.health.HealthPermissions
import ro.fitnesshub.util.AppLog
import java.time.LocalDate

enum class HcStatus { Checking, Available, NotInstalled, UpdateRequired }

enum class PermissionState { Unknown, None, Partial, All }

data class DashboardUiState(
    val hcStatus: HcStatus = HcStatus.Checking,
    val permissions: PermissionState = PermissionState.Unknown,
    val summary: DailySummaryEntity? = null,
    val isSyncing: Boolean = false,
    val syncFailed: Boolean = false,
)

class DashboardViewModel(private val repository: HealthRepository) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // The cached day shows immediately; refresh() then updates it from Health Connect.
        viewModelScope.launch {
            repository.observeDay(LocalDate.now()).collect { summary ->
                _state.update { it.copy(summary = summary) }
            }
        }
    }

    /** Checks Health Connect, permissions, then syncs today. Called on every resume. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val status = when (repository.sdkStatus()) {
                HealthConnectClient.SDK_AVAILABLE -> HcStatus.Available
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcStatus.UpdateRequired
                else -> HcStatus.NotInstalled
            }
            _state.update { it.copy(hcStatus = status) }
            if (status != HcStatus.Available) {
                AppLog.w(TAG, "Health Connect status: $status")
                return@launch
            }

            val permissions = when {
                repository.hasAllReadPermissions() -> PermissionState.All
                repository.hasAnyReadPermission() -> PermissionState.Partial
                else -> PermissionState.None
            }
            _state.update { it.copy(permissions = permissions) }
            if (permissions == PermissionState.None) return@launch

            _state.update { it.copy(isSyncing = true, syncFailed = false) }
            try {
                repository.syncDay()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Sync failed", e)
                _state.update { it.copy(syncFailed = true) }
            } finally {
                _state.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        AppLog.i(TAG, "Permissions granted: ${granted.size} of ${HealthPermissions.requested.size}")
        refresh()
    }

    companion object {
        private const val TAG = "Dashboard"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                DashboardViewModel(app.container.healthRepository)
            }
        }
    }
}
