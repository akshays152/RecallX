package com.recallx.core.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recallx.core.camera.ImageInputProcessor
import com.recallx.core.camera.PreparedSearchImage
import com.recallx.core.model.SearchResult
import com.recallx.core.model.VisualSearchResponse
import com.recallx.data.repository.RecallXRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class VisualSearchPhase { IDLE, CAMERA_READY, IMAGE_CAPTURED, SEARCHING, SUCCESS, EMPTY, ERROR }

data class VisualSearchUiState(
    val phase: VisualSearchPhase = VisualSearchPhase.IDLE,
    val image: PreparedSearchImage? = null,
    val question: String = DEFAULT_QUESTION,
    val response: VisualSearchResponse? = null,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null
) {
    companion object { const val DEFAULT_QUESTION = "Have I seen this before?" }
}

class VisualSearchViewModel(private val repository: RecallXRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(VisualSearchUiState())
    val uiState: StateFlow<VisualSearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun cameraReady() { if (_uiState.value.phase == VisualSearchPhase.IDLE) _uiState.update { it.copy(phase = VisualSearchPhase.CAMERA_READY, error = null) } }
    fun setQuestion(question: String) { _uiState.update { it.copy(question = question) } }

    fun prepareGalleryImage(resolver: ContentResolver, uri: Uri, cacheDir: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            ImageInputProcessor.fromUri(resolver, uri, cacheDir).onSuccess { image -> setImage(image) }.onFailure { error -> _uiState.update { it.copy(phase = VisualSearchPhase.ERROR, error = "We couldn't prepare that image. Please try another one.") } }
        }
    }

    fun prepareCapturedImage(source: File, cacheDir: File) {
        viewModelScope.launch {
            ImageInputProcessor.fromCapturedFile(source, cacheDir).onSuccess { image -> setImage(image) }.onFailure { _uiState.update { it.copy(phase = VisualSearchPhase.ERROR, error = "We couldn't prepare the captured image. Please try again.") } }
        }
    }

    internal fun setImage(image: PreparedSearchImage) {
        _uiState.value.image?.delete()
        _uiState.value = VisualSearchUiState(phase = VisualSearchPhase.IMAGE_CAPTURED, image = image, question = _uiState.value.question)
    }

    fun search() {
        val state = _uiState.value
        val image = state.image ?: return
        if (state.phase == VisualSearchPhase.SEARCHING || searchJob?.isActive == true) return
        searchJob?.cancel()
        _uiState.update { it.copy(phase = VisualSearchPhase.SEARCHING, error = null) }
        searchJob = viewModelScope.launch {
            runCatching { repository.visualSearch(image.file, state.question.trim().ifBlank { null }) }
                .onSuccess { response -> _uiState.update { it.copy(phase = if (response.results.isEmpty()) VisualSearchPhase.EMPTY else VisualSearchPhase.SUCCESS, response = response, results = response.results) } }
                .onFailure { error -> _uiState.update { it.copy(phase = VisualSearchPhase.ERROR, error = error.userMessage()) } }
        }
    }

    fun retake() { searchJob?.cancel(); _uiState.value.image?.delete(); _uiState.value = VisualSearchUiState(phase = VisualSearchPhase.CAMERA_READY) }
    fun reset() { searchJob?.cancel(); _uiState.value.image?.delete(); _uiState.value = VisualSearchUiState() }

    override fun onCleared() { _uiState.value.image?.delete(); super.onCleared() }
}
