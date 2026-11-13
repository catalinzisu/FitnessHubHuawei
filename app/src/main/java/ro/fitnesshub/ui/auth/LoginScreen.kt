package ro.fitnesshub.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.fitnesshub.R

/**
 * Sign in with email and password.
 * @param onBack null when there is nothing to go back to (sign-in is the first screen).
 * @param onForgotPassword gets the email typed so far, to pre-fill the reset form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    // One-shot event from the ViewModel: mark it handled first, then navigate.
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) {
            viewModel.signedInHandled()
            onSignedIn()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_sign_in_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        AuthForm(Modifier.padding(padding)) {
            Text(stringResource(R.string.auth_sign_in_intro), style = MaterialTheme.typography.bodyMedium)
            EmailField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                error = state.emailError,
                enabled = !state.loading,
                onImeAction = { focus.moveFocus(FocusDirection.Down) },
            )
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.auth_password),
                error = state.passwordError,
                enabled = !state.loading,
                isNew = false,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focus.clearFocus()
                    viewModel.signIn()
                },
            )
            TextButton(onClick = { onForgotPassword(state.email.trim()) }, enabled = !state.loading) {
                Text(stringResource(R.string.auth_forgot_password))
            }
            AuthErrorText(state.error)
            AuthButton(stringResource(R.string.auth_sign_in), loading = state.loading) {
                focus.clearFocus()
                viewModel.signIn()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.auth_no_account), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRegister, enabled = !state.loading) {
                    Text(stringResource(R.string.auth_create_account))
                }
            }
        }
    }
}
