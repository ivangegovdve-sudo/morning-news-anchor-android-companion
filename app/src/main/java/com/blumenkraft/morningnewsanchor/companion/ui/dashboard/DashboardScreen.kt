package com.blumenkraft.morningnewsanchor.companion.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blumenkraft.morningnewsanchor.companion.R
import com.blumenkraft.morningnewsanchor.companion.data.models.Briefing

@Composable
fun DashboardScreen(
    onOpenBriefing: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.dismissMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.isLoading && state.recentBriefings.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = greeting(state.preferences?.displayName),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item { TodayCard(state, onOpenBriefing, viewModel::runNow) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.height(4.dp))
                        Text("Refresh")
                    }
                    Button(
                        onClick = viewModel::runNow,
                        enabled = state.canRunNow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.dashboard_run_now))
                    }
                }
            }

            if (!state.canRunNow && !state.isTriggering) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_run_now_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            item {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(state.recentBriefings, key = { it.id }) { briefing ->
                BriefingRow(briefing, onClick = { onOpenBriefing(briefing.id) })
            }
        }
    }
}

@Composable
private fun TodayCard(
    state: DashboardUiState,
    onOpenBriefing: (String) -> Unit,
    onRunNow: () -> Unit,
) {
    val today = state.todaysBriefing

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (today == null) {
                Text(
                    stringResource(R.string.dashboard_no_briefing_today),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = state.preferences?.let {
                        "Scheduled for ${it.deliveryLocalTime.dropLast(3)} ${it.timezone}" +
                            if (!it.autoSend) " · automatic delivery is off" else ""
                    } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            } else {
                Text(today.briefingDate, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = today.introText ?: today.status,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                )
                if (today.hasFailed) {
                    Text(
                        text = today.error ?: "Generation failed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Button(
                        onClick = { onOpenBriefing(today.id) },
                        enabled = today.isReady,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.dashboard_play))
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefingRow(briefing: Briefing, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(briefing.briefingDate, style = MaterialTheme.typography.titleLarge)
            Text(
                text = briefing.introText ?: briefing.status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
            )
        }
    }
}

private fun greeting(name: String?): String =
    if (name.isNullOrBlank()) "Good morning" else "Good morning, $name"
