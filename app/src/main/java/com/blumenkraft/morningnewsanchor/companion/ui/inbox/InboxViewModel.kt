package com.blumenkraft.morningnewsanchor.companion.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blumenkraft.morningnewsanchor.companion.data.models.ArchiveLogEntry
import com.blumenkraft.morningnewsanchor.companion.data.models.CatalogItemWithSubscription
import com.blumenkraft.morningnewsanchor.companion.data.repository.InboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val isLoading: Boolean = true,
    val catalog: List<CatalogItemWithSubscription> = emptyList(),
    val archive: List<ArchiveLogEntry> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: InboxRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                InboxUiState(
                    isLoading = false,
                    catalog = repository.listCatalogWithSubscriptions(),
                    archive = repository.listArchiveLog(limit = 50),
                )
            }.fold(
                onSuccess = { loaded -> _uiState.value = loaded },
                onFailure = { t -> _uiState.update { it.copy(isLoading = false, error = t.message) } },
            )
        }
    }

    fun toggleSubscription(item: CatalogItemWithSubscription) {
        viewModelScope.launch {
            runCatching {
                val sub = item.subscription
                if (sub == null) repository.subscribe(item.catalog.id)
                else repository.unsubscribe(sub.id)
            }.fold(
                // Refetch rather than patch: subscribe/unsubscribe change different fields
                // (insert vs status update) and reconciling both locally isn't worth it.
                onSuccess = { refresh() },
                onFailure = { t -> _uiState.update { it.copy(error = t.message) } },
            )
        }
    }

    fun togglePaused(item: CatalogItemWithSubscription) {
        val sub = item.subscription ?: return
        viewModelScope.launch {
            runCatching { repository.setPaused(sub.id, !sub.paused) }
                .fold(
                    onSuccess = { refresh() },
                    onFailure = { t -> _uiState.update { it.copy(error = t.message) } },
                )
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
