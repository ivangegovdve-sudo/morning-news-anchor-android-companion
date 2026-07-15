package com.blumenkraft.morningnewsanchor.companion.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blumenkraft.morningnewsanchor.companion.R
import com.blumenkraft.morningnewsanchor.companion.data.models.VoiceProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.saved) {
        val text = state.error ?: if (state.saved) "Saved" else null
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.dismissMessages()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val prefs = state.preferences
        if (prefs == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No preferences row for this account. It should be created automatically at " +
                        "first sign-in — if you're seeing this, the database trigger didn't fire.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = viewModel::refresh, Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.nav_preferences),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )

            // ── Voice ──────────────────────────────────────────────────────────────────
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.prefs_voice), style = MaterialTheme.typography.titleLarge)

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = state.voiceProvider.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(
                                androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable
                            ).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            VoiceProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.label) },
                                    onClick = {
                                        viewModel.setVoiceProvider(provider)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }

                    if (!state.voiceProvider.serverSide) {
                        Text(
                            "This provider only synthesizes in the browser, so briefings generated " +
                                "on the server will have no audio to play here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (state.missingKeyForVoiceProvider) {
                        Text(
                            "No ElevenLabs API key on file for your account. Add one on the web app — " +
                                "keys are encrypted server-side and can't be set from here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (state.voiceProvider == VoiceProvider.ELEVENLABS) {
                        var voiceId by remember(prefs.elevenlabsVoiceId) {
                            mutableStateOf(prefs.elevenlabsVoiceId.orEmpty())
                        }
                        OutlinedTextField(
                            value = voiceId,
                            onValueChange = { voiceId = it },
                            label = { Text("Voice ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = { viewModel.setElevenLabsVoiceId(voiceId) }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }

            // ── Delivery ───────────────────────────────────────────────────────────────
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.prefs_delivery), style = MaterialTheme.typography.titleLarge)

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.prefs_auto_send), Modifier.weight(1f))
                        Switch(checked = prefs.autoSend, onCheckedChange = viewModel::setAutoSend)
                    }

                    var time by remember(prefs.deliveryLocalTime) {
                        // Column is HH:MM:SS; the picker only cares about HH:MM.
                        mutableStateOf(prefs.deliveryLocalTime.take(5))
                    }
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text(stringResource(R.string.prefs_delivery_time)) },
                        placeholder = { Text("08:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    var zone by remember(prefs.timezone) { mutableStateOf(prefs.timezone) }
                    OutlinedTextField(
                        value = zone,
                        onValueChange = { zone = it },
                        label = { Text(stringResource(R.string.prefs_timezone)) },
                        placeholder = { Text("Europe/Sofia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedButton(onClick = {
                        viewModel.setDeliveryTime(time)
                        viewModel.setTimezone(zone)
                    }) { Text(stringResource(R.string.save)) }

                    Text(
                        "The backend checks every 30 minutes and generates when your local time is " +
                            "within ±5 minutes of this.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) { Text(stringResource(R.string.prefs_sign_out)) }
        }
    }
}
