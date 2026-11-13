package ro.fitnesshub.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.fitnesshub.R

/** Sends a password-reset link. The new password is chosen on Firebase's web page from the email. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    onBack: () -> Unit,
    viewModel: ResetPasswordViewModel = viewModel(factory = ResetPasswordViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_reset_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        AuthForm(Modifier.padding(padding)) {
            Text(stringResource(R.string.auth_reset_intro), style = MaterialTheme.typography.bodyMedium)
            EmailField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                error = state.emailError,
                enabled = !state.loading,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focus.clearFocus()
                    viewModel.send()
                },
            )
            AuthErrorText(state.error)
            val sentTo = state.sentTo
            if (sentTo == null) {
                AuthButton(stringResource(R.string.auth_reset_send), loading = state.loading) {
                    focus.clearFocus()
                    viewModel.send()
                }
            } else {
                // Same text whether or not the address has an account (see AuthRepository.sendPasswordReset).
                Text(
                    stringResource(R.string.auth_reset_sent, sentTo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(stringResource(R.string.auth_back_to_sign_in))
                }
            }
        }
    }
}
