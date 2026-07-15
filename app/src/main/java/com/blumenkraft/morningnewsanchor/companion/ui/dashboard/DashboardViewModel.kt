package com.blumenkraft.morningnewsanchor.companion.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blumenkraft.morningnewsanchor.companion.data.models.Briefing
import com.blumenkraft.morningnewsanchor.companion.data.models.CronRun
import com.blumenkraft.morningnewsanchor.companion.data.models.Preferences
import com.blumenkraft.morningnewsanchor.companion.data.repository.BriefingRepository
import com.blumenkraft.morningnewsanchor.companion.data.repository.BriefingTriggerRepository
import com.blumenkraft.morningnewsanchor.companion.data.repository.PreferencesRepository
import com.blumenkraft.morningnewsanchor.companion.data.repository.TriggerResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
// kotlinx-datetime 0.7 moved Clock to the Kotlin stdlib; kotlinx.datetime.Clock is gone.
import kotlin.time.Clock

data class DashboardUiState(
    val isLoading: Boolean = true,
    val preferences: Preferences? = null,
    val todaysBriefing: Briefing? = null,
    val recentBriefings: List<Briefing> = emptyList(),
    val lastRun: CronRun? = null,
    val isTriggering: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    /** Run Now stays off until the authenticated endpoint exists — see BriefingTriggerApi. */
    val canRunNow: Boolean
        get() = BriefingTriggerRepository.TRIGGER_ENDPOINT_DEPLOYED && !isTriggering
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val briefings: BriefingRepository,
    private val preferences: PreferencesRepository,
    private val trigger: BriefingTriggerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeRealtime()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching {
                val prefs = preferences.getPreferences()
                val recent = briefings.listBriefings(limit = 10)
                val today = todayInUserZone(prefs)
                DashboardUiState(
                    isLoading = false,
                    preferences = prefs,
                    // Prefer the row the list already returned over a second round-trip.
                    todaysBriefing = recent.firstOrNull { it.briefingDate == today },
                    recentBriefings = recent,
                    lastRun = trigger.latestRun(),
                )
            }.fold(
                onSuccess = { loaded -> _uiState.value = loaded },
                onFailure = { t ->
                    _uiState.update { it.copy(isLoading = false, error = t.message ?: "Failed to load") }
                },
            )
        }
    }

    /**
     * Keeps the card live while the screen is open. Realtime is the only push channel the app
     * has today (no FCM project), so this is what makes "briefing appeared" feel immediate.
     */
    private fun observeRealtime() {
        viewModelScope.launch {
            runCatching {
                briefings.observeNewBriefings().collect { refresh() }
            }.onFailure {
                // A dropped socket shouldn't surface as a user-facing error; pull-to-refresh
                // still works. TODO: reconnect with backoff.
            }
        }
    }

    fun runNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTriggering = true, message = null, error = null) }

            when (val result = trigger.runNow()) {
                is TriggerResult.Started -> {
                    _uiState.update { it.copy(isTriggering = false, message = "Briefing started") }
                    refresh()
                }
                is TriggerResult.Failed ->
                    _uiState.update { it.copy(isTriggering = false, error = result.message) }
                TriggerResult.NotAvailable ->
                    _uiState.update {
                        it.copy(
                            isTriggering = false,
                            error = "Run now isn't wired up yet — the backend needs an authenticated trigger endpoint.",
                        )
                    }
            }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null, error = null) }

    /**
     * "Today" means today in the *user's* configured zone, not the device's — someone
     * travelling still wants the briefing their schedule produced.
     */
    private fun todayInUserZone(prefs: Preferences?): String {
        val zone = runCatching { TimeZone.of(prefs?.timezone ?: "UTC") }.getOrDefault(TimeZone.UTC)
        return Clock.System.now().toLocalDateTime(zone).date.toString()
    }
}
