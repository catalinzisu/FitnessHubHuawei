package ro.fitnesshub.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ro.fitnesshub.R
import ro.fitnesshub.data.auth.AuthError
import ro.fitnesshub.data.auth.AuthValidation
import ro.fitnesshub.data.auth.FieldError

// Pieces shared by the sign-in, register and reset-password screens.

/** Centered, scrollable form column; narrow on tablets, moves up above the keyboard. */
@Composable
fun AuthForm(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    error: FieldError?,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_email)) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(fieldErrorText(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        // Lets password managers fill it in.
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
    )
}

/** Password field with a Show/Hide button. [isNew] tells password managers to offer a new password. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: FieldError?,
    enabled: Boolean,
    isNew: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(fieldErrorText(it)) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) R.string.auth_hide else R.string.auth_show))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = if (isNew) ContentType.NewPassword else ContentType.Password },
    )
}

/** The main button; shows a spinner while [loading] and ignores taps meanwhile. */
@Composable
fun AuthButton(text: String, loading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}

/** A Firebase error under the form, in the error color. */
@Composable
fun AuthErrorText(error: AuthError?) {
    if (error != null) {
        Text(authErrorText(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun fieldErrorText(error: FieldError): String = when (error) {
    FieldError.Required -> stringResource(R.string.auth_field_required)
    FieldError.InvalidEmail -> stringResource(R.string.auth_field_invalid_email)
    FieldError.PasswordTooShort -> stringResource(R.string.auth_field_password_short, AuthValidation.MIN_PASSWORD_LENGTH)
    FieldError.PasswordNeedsLetterAndDigit -> stringResource(R.string.auth_field_password_mix)
    FieldError.PasswordsDontMatch -> stringResource(R.string.auth_field_password_mismatch)
}

@Composable
fun authErrorText(error: AuthError): String = stringResource(
    when (error) {
        AuthError.NotConfigured -> R.string.auth_error_not_configured
        AuthError.InvalidCredentials -> R.string.auth_error_invalid_credentials
        AuthError.EmailInUse -> R.string.auth_error_email_in_use
        AuthError.WeakPassword -> R.string.auth_error_weak_password
        AuthError.InvalidEmail -> R.string.auth_field_invalid_email
        AuthError.Network -> R.string.auth_error_network
        AuthError.TooManyRequests -> R.string.auth_error_too_many
        AuthError.Unknown -> R.string.auth_error_unknown
    },
)
