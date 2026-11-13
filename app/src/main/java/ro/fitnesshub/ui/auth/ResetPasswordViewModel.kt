package ro.fitnesshub.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
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
import ro.fitnesshub.ui.navigation.ResetPasswordRoute

data class ResetPasswordUiState(
    val email: String = "",
    val emailError: FieldError? = null,
    val loading: Boolean = false,
    val error: AuthError? = null,
    /** The address the link was sent to; non-null shows the "check your inbox" message. */
    val sentTo: String? = null,
)

class ResetPasswordViewModel(
    private val auth: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Starts with the email typed on the sign-in screen (route argument).
    private val _state = MutableStateFlow(ResetPasswordUiState(email = savedStateHandle.toRoute<ResetPasswordRoute>().email))
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null, sentTo = null) }

    fun send() {
        val current = _state.value
        if (current.loading) return
        val emailError = AuthValidation.email(current.email)
        if (emailError != null) {
            _state.update { it.copy(emailError = emailError) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.sendPasswordReset(current.email)) {
                AuthResult.Success -> _state.update { it.copy(loading = false, sentTo = current.email.trim()) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FitnessHubApp
                ResetPasswordViewModel(app.container.authRepository, createSavedStateHandle())
            }
        }
    }
}
