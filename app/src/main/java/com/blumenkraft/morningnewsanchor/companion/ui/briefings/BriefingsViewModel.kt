package com.blumenkraft.morningnewsanchor.companion.ui.briefings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blumenkraft.morningnewsanchor.companion.data.models.Briefing
import com.blumenkraft.morningnewsanchor.companion.data.models.BriefingDetail
import com.blumenkraft.morningnewsanchor.companion.data.models.PlayableTrack
import com.blumenkraft.morningnewsanchor.companion.data.repository.BriefingRepository
import com.blumenkraft.morningnewsanchor.companion.player.BriefingPlayerController
import com.blumenkraft.morningnewsanchor.companion.player.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BriefingsUiState(
    val isLoading: Boolean = true,
    val briefings: List<Briefing> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class BriefingsViewModel @Inject constructor(
    private val repository: BriefingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BriefingsUiState())
    val uiState: StateFlow<BriefingsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.listBriefings() }
                .fold(
                    onSuccess = { list -> _uiState.value = BriefingsUiState(isLoading = false, briefings = list) },
                    onFailure = { t -> _uiState.update { it.copy(isLoading = false, error = t.message) } },
                )
        }
    }
}

data class BriefingDetailUiState(
    val isLoading: Boolean = true,
    val detail: BriefingDetail? = null,
    val queue: List<PlayableTrack> = emptyList(),
    val error: String? = null,
) {
    /**
     * A briefing can be `ready` yet have no playable audio — most obviously when the user's
     * voice provider is pocket-tts, which the server refuses to synthesize. Distinguishing
     * this from "still generating" is the difference between a useful message and a dead
     * play button.
     */
    val hasAudio: Boolean get() = queue.isNotEmpty()
}

@HiltViewModel
class BriefingDetailViewModel @Inject constructor(
    private val repository: BriefingRepository,
    private val player: BriefingPlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BriefingDetailUiState())
    val uiState: StateFlow<BriefingDetailUiState> = _uiState.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = player.state

    fun load(briefingId: String) {
        if (briefingId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching {
                player.connect()
                val detail = repository.getBriefingDetail(briefingId)
                    ?: error("Briefing not found")
                // Signing every URL up front costs one request per story, but it means the
                // whole queue is playable the moment the user taps play.
                BriefingDetailUiState(
                    isLoading = false,
                    detail = detail,
                    queue = repository.buildQueue(detail),
                )
            }.fold(
                onSuccess = { loaded -> _uiState.value = loaded },
                onFailure = { t -> _uiState.update { it.copy(isLoading = false, error = t.message) } },
            )
        }
    }

    fun playAll() = player.play(_uiState.value.queue)

    fun playFrom(trackId: String) {
        val index = _uiState.value.queue.indexOfFirst { it.id == trackId }
        if (index >= 0) player.play(_uiState.value.queue, startIndex = index)
    }

    fun togglePlayPause() = player.togglePlayPause()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun next() = player.next()
    fun previous() = player.previous()
}
