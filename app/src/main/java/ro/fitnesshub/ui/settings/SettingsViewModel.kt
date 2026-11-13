package ro.fitnesshub.ui.settings

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
import ro.fitnesshub.data.auth.AuthRepository
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.data.health.HealthPermissions
import ro.fitnesshub.data.health.SeedDataWriter
import ro.fitnesshub.ui.common.UiMessage
import ro.fitnesshub.util.AppLog

/** The account row: sign-in missing from the build, signed out, or signed in. */
sealed interface AccountState {
    data object NotConfigured : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val email: String?) : AccountState
}

/** State of an optional Health Connect permission. */
enum class OptionalAccess { Unknown, Unsupported, Off, On }

data class SettingsUiState(
    val working: Boolean = false,
    val message: UiMessage? = null,
    val backgroundSync: OptionalAccess = OptionalAccess.Unknown,
    val workoutExport: OptionalAccess = OptionalAccess.Unknown,
    val account: AccountState = AccountState.SignedOut,
)

class SettingsViewModel(
    private val seedDataWriter: SeedDataWriter,
    private val repository: HealthRepository,
    private val healthConnect: HealthConnectSource,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Follows sign-in and sign-out as they happen, wherever they happen.
        viewModelScope.launch {
            auth.currentUser.collect { user ->
                val account = when {
                    !auth.isConfigured -> AccountState.NotConfigured
                    user == null -> AccountState.SignedOut
                    else -> AccountState.SignedIn(user.email)
                }
                _state.update { it.copy(account = account) }
            }
        }
    }

    fun signOut() = auth.signOut()

    /** Re-reads the optional permissions; called on every resume. */
    fun refresh() {
        viewModelScope.launch {
            val (background, export) = try {
                if (!healthConnect.isAvailable()) {
                    OptionalAccess.Unsupported to OptionalAccess.Unsupported
                } else {
                    val granted = healthConnect.grantedPermissions()
                    val background = when {
                        !healthConnect.backgroundReadSupported() -> OptionalAccess.Unsupported
                        HealthPermissions.BACKGROUND in granted -> OptionalAccess.On
                        else -> OptionalAccess.Off
                    }
                    val export = if (HealthPermissions.WRITE_WORKOUTS in granted) OptionalAccess.On else OptionalAccess.Off
                    background to export
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Could not read permissions", e)
                OptionalAccess.Unknown to OptionalAccess.Unknown
            }
            _state.update { it.copy(backgroundSync = background, workoutExport = export) }
        }
    }

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
                SettingsViewModel(
                    app.container.seedDataWriter,
                    app.container.healthRepository,
                    app.container.healthConnect,
                    app.container.authRepository,
                )
            }
        }
    }
}
