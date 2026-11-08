package ro.fitnesshub.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.fitnesshub.R
import ro.fitnesshub.data.db.DailySummaryEntity
import ro.fitnesshub.data.health.HealthConnectSource
import ro.fitnesshub.ui.common.HealthConnectIntents
import ro.fitnesshub.ui.common.InfoCard
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onPermissionsResult(granted) }

    // Re-check every time the screen comes back, e.g. after changing permissions in Health Connect.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_today)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.hcStatus) {
                    HcStatus.Checking -> item { Text(stringResource(R.string.hc_checking)) }

                    HcStatus.NotInstalled -> item {
                        InfoCard(
                            title = stringResource(R.string.hc_not_installed_title),
                            body = stringResource(R.string.hc_not_installed_body),
                            primaryLabel = stringResource(R.string.hc_install),
                            onPrimary = { HealthConnectIntents.openPlayStore(context) },
                        )
                    }

                    HcStatus.UpdateRequired -> item {
                        InfoCard(
                            title = stringResource(R.string.hc_update_title),
                            body = stringResource(R.string.hc_update_body),
                            primaryLabel = stringResource(R.string.hc_update),
                            onPrimary = { HealthConnectIntents.openPlayStore(context) },
                        )
                    }

                    HcStatus.Available -> {
                        when (state.permissions) {
                            PermissionState.None -> item {
                                InfoCard(
                                    title = stringResource(R.string.permissions_title),
                                    body = stringResource(R.string.permissions_body),
                                    primaryLabel = stringResource(R.string.permissions_grant),
                                    onPrimary = { permissionLauncher.launch(state.permissionsToRequest) },
                                )
                            }
                            PermissionState.Partial -> item {
                                InfoCard(
                                    title = stringResource(R.string.permissions_title),
                                    body = stringResource(R.string.permissions_partial),
                                    primaryLabel = stringResource(R.string.permissions_grant),
                                    onPrimary = { permissionLauncher.launch(state.permissionsToRequest) },
                                    secondaryLabel = stringResource(R.string.permissions_manage),
                                    onSecondary = { HealthConnectIntents.openSettings(context) },
                                )
                            }
                            else -> Unit
                        }
                        if (state.permissions == PermissionState.All || state.permissions == PermissionState.Partial) {
                            metricItems(state.summary)
                            item { SyncFooter(state) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.metricItems(summary: DailySummaryEntity?) {
    item {
        MetricCard(
            label = stringResource(R.string.metric_steps),
            value = summary?.steps?.let { NumberFormat.getIntegerInstance().format(it) },
            modifier = Modifier.fillMaxWidth(),
            large = true,
        )
    }
    item {
        MetricRow(
            left = {
                MetricCard(
                    label = stringResource(R.string.metric_distance),
                    value = summary?.distanceMeters?.let { stringResource(R.string.value_km, it / 1000.0) },
                    modifier = it,
                )
            },
            right = {
                MetricCard(
                    label = stringResource(R.string.metric_sleep),
                    value = summary?.sleepMinutes?.let { stringResource(R.string.value_sleep, it / 60, it % 60) },
                    modifier = it,
                )
            },
        )
    }
    item {
        MetricRow(
            left = {
                MetricCard(
                    label = stringResource(R.string.metric_active_kcal),
                    value = summary?.activeKcal?.let { stringResource(R.string.value_kcal, it.roundToLong()) },
                    modifier = it,
                )
            },
            right = {
                MetricCard(
                    label = stringResource(R.string.metric_total_kcal),
                    value = summary?.totalKcal?.let { stringResource(R.string.value_kcal, it.roundToLong()) },
                    modifier = it,
                )
            },
        )
    }
    item {
        MetricRow(
            left = {
                val minHr = summary?.minHr
                val maxHr = summary?.maxHr
                val range = if (minHr != null && maxHr != null) {
                    stringResource(R.string.value_bpm_range, minHr, maxHr)
                } else {
                    null
                }
                MetricCard(
                    label = stringResource(R.string.metric_heart_rate),
                    value = summary?.avgHr?.let { stringResource(R.string.value_bpm, it) },
                    supporting = range,
                    modifier = it,
                )
            },
            right = {
                MetricCard(
                    label = stringResource(R.string.metric_resting_hr),
                    value = summary?.restingHr?.let { stringResource(R.string.value_bpm, it) },
                    modifier = it,
                )
            },
        )
    }
}

@Composable
private fun MetricRow(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    large: Boolean = false,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value ?: stringResource(R.string.no_data),
                style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            )
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SyncFooter(state: DashboardUiState) {
    val summary = state.summary
    val text = when {
        state.syncFailed -> stringResource(R.string.sync_error)
        summary == null -> stringResource(R.string.never_synced)
        else -> {
            val time = Instant.ofEpochMilli(summary.syncedAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            val sources = summary.sources.split(',')
                .filter { it.isNotBlank() }
                .joinToString(", ") { friendlySourceName(it) }
                .ifEmpty { stringResource(R.string.sources_none) }
            stringResource(R.string.last_synced, time, sources)
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.syncFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun friendlySourceName(packageName: String): String = when (packageName) {
    HealthConnectSource.GADGETBRIDGE_PACKAGE -> "Gadgetbridge"
    HealthConnectSource.HEALTH_CONNECT_PACKAGE -> "Health Connect"
    "ro.fitnesshub" -> "Fitness Hub (test data)"
    else -> packageName
}
