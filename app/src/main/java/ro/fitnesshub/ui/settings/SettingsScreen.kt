package ro.fitnesshub.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.fitnesshub.BuildConfig
import ro.fitnesshub.R
import ro.fitnesshub.data.health.HealthPermissions
import ro.fitnesshub.ui.common.HealthConnectIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLog: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmSeed by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) {
            viewModel.signedOutHandled()
            onSignedOut()
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(message.text, *message.args.toTypedArray()))
        viewModel.messageShown()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (val account = state.account) {
                AccountState.NotConfigured -> SettingsRow(
                    title = stringResource(R.string.settings_account),
                    description = stringResource(R.string.settings_account_not_configured),
                    enabled = false,
                    onClick = {},
                )
                AccountState.SignedOut -> SettingsRow(
                    title = stringResource(R.string.settings_account),
                    description = stringResource(R.string.settings_account_signed_out),
                    onClick = onSignIn,
                )
                is AccountState.SignedIn -> SettingsRow(
                    title = stringResource(R.string.settings_account_sign_out),
                    description = stringResource(R.string.settings_account_signed_in, account.email.orEmpty()),
                    onClick = { confirmSignOut = true },
                )
            }
            SettingsRow(
                title = stringResource(R.string.settings_hc),
                description = stringResource(R.string.settings_hc_desc),
                onClick = { HealthConnectIntents.openSettings(context) },
            )
            OptionalAccessRow(
                title = stringResource(R.string.settings_background_sync),
                access = state.backgroundSync,
                onText = R.string.settings_background_sync_on,
                offText = R.string.settings_background_sync_off,
                unsupportedText = R.string.settings_background_sync_unsupported,
                onRequest = { permissionLauncher.launch(setOf(HealthPermissions.BACKGROUND)) },
            )
            OptionalAccessRow(
                title = stringResource(R.string.settings_export),
                access = state.workoutExport,
                onText = R.string.settings_export_on,
                offText = R.string.settings_export_off,
                unsupportedText = R.string.settings_export_unsupported,
                onRequest = { permissionLauncher.launch(setOf(HealthPermissions.WRITE_WORKOUTS)) },
            )
            SettingsRow(
                title = stringResource(R.string.settings_privacy),
                description = stringResource(R.string.settings_privacy_desc),
                onClick = onOpenPrivacy,
            )

            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.settings_debug_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                )
                SettingsRow(
                    title = stringResource(R.string.settings_log),
                    description = stringResource(R.string.settings_log_desc),
                    onClick = onOpenLog,
                )
                SettingsRow(
                    title = stringResource(R.string.seed_title),
                    description = if (state.working) stringResource(R.string.seed_working) else stringResource(R.string.seed_desc),
                    enabled = !state.working,
                    onClick = { confirmSeed = true },
                )
                SettingsRow(
                    title = stringResource(R.string.seed_clear),
                    description = stringResource(R.string.seed_clear_desc),
                    enabled = !state.working,
                    onClick = viewModel::clearTestData,
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.settings_account_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmSeed) {
        AlertDialog(
            onDismissRequest = { confirmSeed = false },
            title = { Text(stringResource(R.string.seed_confirm_title)) },
            text = { Text(stringResource(R.string.seed_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSeed = false
                    viewModel.seedTestData()
                }) { Text(stringResource(R.string.seed_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSeed = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** A row for an optional permission: tapping it asks for the permission while it is off. */
@Composable
private fun OptionalAccessRow(
    title: String,
    access: OptionalAccess,
    @StringRes onText: Int,
    @StringRes offText: Int,
    @StringRes unsupportedText: Int,
    onRequest: () -> Unit,
) {
    val description = when (access) {
        OptionalAccess.On -> stringResource(onText)
        OptionalAccess.Off -> stringResource(offText)
        OptionalAccess.Unsupported -> stringResource(unsupportedText)
        OptionalAccess.Unknown -> ""
    }
    SettingsRow(
        title = title,
        description = description,
        enabled = access == OptionalAccess.Off,
        onClick = onRequest,
    )
}
