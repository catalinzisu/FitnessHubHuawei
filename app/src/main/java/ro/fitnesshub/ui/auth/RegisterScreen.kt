package ro.fitnesshub.ui.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.fitnesshub.R
import ro.fitnesshub.data.auth.AuthValidation

/** Create an account. Firebase signs the new account in right away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    LaunchedEffect(state.registered) {
        if (state.registered) {
            viewModel.registeredHandled()
            onRegistered()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_register_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        AuthForm(Modifier.padding(padding)) {
            Text(stringResource(R.string.auth_register_intro), style = MaterialTheme.typography.bodyMedium)
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
                isNew = true,
                imeAction = ImeAction.Next,
                onImeAction = { focus.moveFocus(FocusDirection.Down) },
            )
            if (state.passwordError == null) {
                Text(
                    stringResource(R.string.auth_password_rule, AuthValidation.MIN_PASSWORD_LENGTH),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PasswordField(
                value = state.confirm,
                onValueChange = viewModel::onConfirmChange,
                label = stringResource(R.string.auth_password_confirm),
                error = state.confirmError,
                enabled = !state.loading,
                isNew = true,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focus.clearFocus()
                    viewModel.register()
                },
            )
            AuthErrorText(state.error)
            AuthButton(stringResource(R.string.auth_create_account), loading = state.loading) {
                focus.clearFocus()
                viewModel.register()
            }
        }
    }
}
