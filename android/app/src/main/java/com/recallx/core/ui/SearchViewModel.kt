package com.recallx.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recallx.core.model.SearchResult
import com.recallx.data.repository.RecallXRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchPhase { IDLE, SEARCHING, SUCCESS, EMPTY, ERROR }

data class SearchUiState(
    val phase: SearchPhase = SearchPhase.IDLE,
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val provider: String? = null,
    val error: String? = null
)

class SearchViewModel(private val repository: RecallXRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun setQuery(query: String) { _uiState.update { it.copy(query = query, error = null) } }

    fun submit(queryOverride: String? = null) {
        val query = (queryOverride ?: _uiState.value.query).trim()
        _uiState.update { it.copy(query = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(phase = SearchPhase.ERROR, error = "Enter a question to search your memories.") }
            return
        }
        if (_uiState.value.phase == SearchPhase.SEARCHING || searchJob?.isActive == true) return
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = SearchPhase.SEARCHING, error = null) }
            runCatching { repository.searchMemories(query) }
                .onSuccess { response -> _uiState.update { it.copy(phase = if (response.results.isEmpty()) SearchPhase.EMPTY else SearchPhase.SUCCESS, results = response.results, provider = response.provider) } }
                .onFailure { error -> _uiState.update { it.copy(phase = SearchPhase.ERROR, error = error.userMessage()) } }
        }
    }

    fun clear() { searchJob?.cancel(); _uiState.value = SearchUiState() }
    override fun onCleared() { searchJob?.cancel(); super.onCleared() }
}
