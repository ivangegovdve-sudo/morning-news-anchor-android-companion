package com.blumenkraft.morningnewsanchor.companion.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blumenkraft.morningnewsanchor.companion.data.models.Preferences
import com.blumenkraft.morningnewsanchor.companion.data.models.PreferencesUpdate
import com.blumenkraft.morningnewsanchor.companion.data.models.UserProviderKey
import com.blumenkraft.morningnewsanchor.companion.data.models.VoiceProvider
import com.blumenkraft.morningnewsanchor.companion.data.repository.PreferencesRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.StorageUrlResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreferencesUiState(
    val isLoading: Boolean = true,
    val preferences: Preferences? = null,
    val providerKeys: List<UserProviderKey> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val voiceProvider: VoiceProvider
        get() = VoiceProvider.fromId(preferences?.voiceProvider)

    /**
     * ElevenLabs is the only provider that needs a per-user key in practice — OpenAI runs
     * through the Lovable AI gateway server-side. Surfacing this lets the settings screen
     * warn before a briefing silently fails to narrate.
     */
    val missingKeyForVoiceProvider: Boolean
        get() = voiceProvider == VoiceProvider.ELEVENLABS &&
            providerKeys.none { it.provider == VoiceProvider.ELEVENLABS.id }
}

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val repository: PreferencesRepository,
    private val auth: AuthRepository,
    private val urlResolver: StorageUrlResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                PreferencesUiState(
                    isLoading = false,
                    preferences = repository.getPreferences(),
                    providerKeys = repository.listProviderKeys(),
                )
            }.fold(
                onSuccess = { loaded -> _uiState.value = loaded },
                onFailure = { t -> _uiState.update { it.copy(isLoading = false, error = t.message) } },
            )
        }
    }

    fun setAutoSend(enabled: Boolean) = save(PreferencesUpdate(autoSend = enabled))

    fun setVoiceProvider(provider: VoiceProvider) = save(PreferencesUpdate(voiceProvider = provider.id))

    fun setElevenLabsVoiceId(voiceId: String) =
        save(PreferencesUpdate(elevenlabsVoiceId = voiceId.ifBlank { null }))

    /**
     * @param time `HH:MM`. Postgres wants `HH:MM:SS`, so seconds are appended here — sending
     *   `HH:MM` is accepted by Postgrest but round-trips back as `HH:MM:00`, which would make
     *   the field look like it changed after every save.
     */
    fun setDeliveryTime(time: String) =
        save(PreferencesUpdate(deliveryLocalTime = if (time.count { it == ':' } == 1) "$time:00" else time))

    fun setTimezone(zone: String) = save(PreferencesUpdate(timezone = zone))

    private fun save(update: PreferencesUpdate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
            runCatching { repository.updatePreferences(update) }
                .fold(
                    onSuccess = { prefs ->
                        _uiState.update { it.copy(isSaving = false, preferences = prefs, saved = true) }
                    },
                    onFailure = { t ->
                        _uiState.update { it.copy(isSaving = false, error = t.message) }
                    },
                )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            urlResolver.clear()
        }
    }

    fun dismissMessages() = _uiState.update { it.copy(error = null, saved = false) }
}
