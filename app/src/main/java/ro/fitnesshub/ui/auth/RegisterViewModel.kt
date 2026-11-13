package ro.fitnesshub.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.fitnesshub.FitnessHubApp
import ro.fitnesshub.data.auth.AuthError
import ro.fitnesshub.data.auth.AuthRepository
import ro.fitnesshub.data.auth.AuthResult
import ro.fitnesshub.data.auth.AuthValidation
import ro.fitnesshub.data.auth.FieldError

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val confirmError: FieldError? = null,
    val loading: Boolean = false,
    val error: AuthError? = null,
    /** One-shot event: the account exists and is signed in. */
    val registered: Boolean = false,
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun onConfirmChange(value: String) = _state.update { it.copy(confirm = value, confirmError = null, error = null) }

    fun register() {
        val current = _state.value
        if (current.loading) return
        val emailError = AuthValidation.email(current.email)
        val passwordError = AuthValidation.newPassword(current.password)
        val confirmError = AuthValidation.confirmation(current.password, current.confirm)
        if (emailError != null || passwordError != null || confirmError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError, confirmError = confirmError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.register(current.email, current.password)) {
                AuthResult.Success -> _state.update { it.copy(loading = false, password = "", confirm = "", registered = true) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun registeredHandled() = _state.update { it.copy(registered = false) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                RegisterViewModel(app.container.authRepository)
            }
        }
    }
}
