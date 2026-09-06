package com.recallx.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recallx.core.model.Memory
import com.recallx.core.model.MemoryContent
import com.recallx.core.model.SearchResult
import com.recallx.data.repository.RecallXRepository
import com.recallx.data.repository.RecallXApiException
import com.recallx.data.repository.RecallXNetworkException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val memories: List<Memory> = emptyList(),
    val error: String? = null
)

class HomeViewModel(private val repository: RecallXRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getMemories(page = 1, pageSize = 6) }
                .onSuccess { memories -> _uiState.value = HomeUiState(isLoading = false, memories = memories) }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = error.userMessage()) } }
        }
    }
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val memories: List<Memory> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val selectedType: String? = null,
    val error: String? = null
)

class LibraryViewModel(private val repository: RecallXRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private val pageSize = 12

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.memories.isEmpty(), isRefreshing = it.memories.isNotEmpty(), error = null, page = 0, hasMore = true) }
            runCatching { repository.getMemories(_uiState.value.selectedType, 1, pageSize) }
                .onSuccess { memories -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, memories = memories, page = 1, hasMore = memories.size == pageSize) } }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = error.userMessage()) } }
        }
    }

    fun selectType(type: String?) {
        _uiState.update { it.copy(selectedType = type) }
        refresh()
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, error = null) }
            val nextPage = current.page + 1
            runCatching { repository.getMemories(current.selectedType, nextPage, pageSize) }
                .onSuccess { memories -> _uiState.update { it.copy(isLoadingMore = false, memories = it.memories + memories, page = nextPage, hasMore = memories.size == pageSize) } }
                .onFailure { error -> _uiState.update { it.copy(isLoadingMore = false, error = error.userMessage()) } }
        }
    }
}

data class DetailUiState(
    val isLoading: Boolean = true,
    val memory: Memory? = null,
    val related: List<SearchResult> = emptyList(),
    val content: MemoryContent? = null,
    val isLoadingContent: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val contentError: String? = null,
    val deleted: Boolean = false
)

class MemoryDetailViewModel(private val repository: RecallXRepository, private val memoryId: String) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getMemory(memoryId) }
                .onSuccess { memory ->
                    _uiState.update { it.copy(isLoading = false, memory = memory) }
                    loadRelated()
                    if (memory.contentAvailable && memory.fileType in setOf(com.recallx.core.model.MemoryFileType.IMAGE, com.recallx.core.model.MemoryFileType.SCREENSHOT)) loadContent()
                }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = error.userMessage()) } }
        }
    }

    private fun loadRelated() {
        viewModelScope.launch {
            runCatching { repository.getRelatedMemories(memoryId) }.onSuccess { related -> _uiState.update { it.copy(related = related) } }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, contentError = null) }
            runCatching { repository.getMemoryContent(memoryId) }
                .onSuccess { content -> _uiState.update { it.copy(isLoadingContent = false, content = content) } }
                .onFailure { error -> _uiState.update { it.copy(isLoadingContent = false, contentError = error.userMessage()) } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, error = null) }
            runCatching { repository.deleteMemory(memoryId) }
                .onSuccess { _uiState.update { it.copy(isDeleting = false, deleted = true) } }
                .onFailure { error -> _uiState.update { it.copy(isDeleting = false, error = error.userMessage()) } }
        }
    }
}

fun Throwable.userMessage(): String = when (this) {
    is RecallXApiException -> safeMessage
    is RecallXNetworkException -> message ?: "We couldn't reach RecallX. Check your connection and try again."
    else -> "Something went wrong. Please try again."
}
