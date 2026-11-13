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
import ro.fitnesshub.data.auth.SignInChoice

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    /** True while Firebase is answering; the button shows a spinner and can't be pressed again. */
    val loading: Boolean = false,
    val error: AuthError? = null,
    /** One-shot event: signed in, or chose to go on without an account; the screen should leave. */
    val done: Boolean = false,
)

class LoginViewModel(
    private val auth: AuthRepository,
    private val signInChoice: SignInChoice,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // Typing clears that field's error and the last Firebase error.
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun signIn() {
        val current = _state.value
        if (current.loading) return
        val emailError = AuthValidation.email(current.email)
        val passwordError = AuthValidation.existingPassword(current.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.signIn(current.email, current.password)) {
                AuthResult.Success -> _state.update { it.copy(loading = false, password = "", done = true) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    /** Only offered when sign-in is the first screen. Remembered, so the app opens on Today next time. */
    fun continueWithoutAccount() {
        signInChoice.continuedWithoutAccount = true
        _state.update { it.copy(done = true) }
    }

    fun doneHandled() = _state.update { it.copy(done = false) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                LoginViewModel(app.container.authRepository, app.container.signInChoice)
            }
        }
    }
}
