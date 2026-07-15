package com.blumenkraft.morningnewsanchor.companion.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blumenkraft.morningnewsanchor.companion.data.models.Feed
import com.blumenkraft.morningnewsanchor.companion.data.repository.FeedsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedsUiState(
    val isLoading: Boolean = true,
    val feeds: List<Feed> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val repository: FeedsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedsUiState())
    val uiState: StateFlow<FeedsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.listFeeds() }
                .fold(
                    onSuccess = { list -> _uiState.value = FeedsUiState(isLoading = false, feeds = list) },
                    onFailure = { t -> _uiState.update { it.copy(isLoading = false, error = t.message) } },
                )
        }
    }

    fun addFeed(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) return

        viewModelScope.launch {
            runCatching { repository.addFeed(name, url) }
                .fold(
                    // Append locally rather than refetching — the insert already returned the row.
                    onSuccess = { feed -> _uiState.update { it.copy(feeds = it.feeds + feed) } },
                    onFailure = { t -> _uiState.update { it.copy(error = t.message) } },
                )
        }
    }

    /**
     * Optimistic toggle: the switch flips immediately and rolls back if the write fails.
     * A round-trip before reflecting a switch makes the UI feel broken on mobile data.
     */
    fun toggleFeed(feed: Feed) {
        val target = !feed.enabled
        _uiState.update { state ->
            state.copy(feeds = state.feeds.map { if (it.id == feed.id) it.copy(enabled = target) else it })
        }

        viewModelScope.launch {
            runCatching { repository.setEnabled(feed.id, target) }
                .onFailure { t ->
                    _uiState.update { state ->
                        state.copy(
                            feeds = state.feeds.map { if (it.id == feed.id) it.copy(enabled = feed.enabled) else it },
                            error = t.message,
                        )
                    }
                }
        }
    }

    fun deleteFeed(feed: Feed) {
        val previous = _uiState.value.feeds
        _uiState.update { it.copy(feeds = it.feeds.filterNot { f -> f.id == feed.id }) }

        viewModelScope.launch {
            runCatching { repository.deleteFeed(feed.id) }
                .onFailure { t -> _uiState.update { it.copy(feeds = previous, error = t.message) } }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
