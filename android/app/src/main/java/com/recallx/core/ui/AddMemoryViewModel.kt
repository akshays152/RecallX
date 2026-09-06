package com.recallx.core.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recallx.core.model.FileSelectionResult
import com.recallx.core.model.Memory
import com.recallx.core.model.MemoryFileSelector
import com.recallx.core.model.PickerKind
import com.recallx.core.model.ProcessingStatus
import com.recallx.core.model.SelectedMemoryFile
import com.recallx.data.repository.RecallXRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddMemoryPhase { IDLE, SELECTING, UPLOADING, PROCESSING, SUCCESS, ERROR }

data class AddMemoryUiState(
    val phase: AddMemoryPhase = AddMemoryPhase.IDLE,
    val selectedFile: SelectedMemoryFile? = null,
    val memory: Memory? = null,
    val message: String? = null,
    val error: String? = null
)

class AddMemoryViewModel(private val repository: RecallXRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddMemoryUiState())
    val uiState: StateFlow<AddMemoryUiState> = _uiState.asStateFlow()
    private var uploadJob: Job? = null

    fun beginSelecting() { if (_uiState.value.phase != AddMemoryPhase.UPLOADING && _uiState.value.phase != AddMemoryPhase.PROCESSING) _uiState.update { it.copy(phase = AddMemoryPhase.SELECTING, error = null) } }
    fun cancelSelecting() { if (_uiState.value.phase == AddMemoryPhase.SELECTING) _uiState.update { it.copy(phase = AddMemoryPhase.IDLE) } }

    fun selectAndUpload(contentResolver: ContentResolver, uri: Uri, pickerKind: PickerKind) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            val result = runCatching { MemoryFileSelector.resolve(contentResolver, uri, pickerKind) }.getOrElse { FileSelectionResult.Rejected("RecallX couldn't read that file. Please choose it again.") }
            when (result) {
                is FileSelectionResult.Rejected -> _uiState.value = AddMemoryUiState(phase = AddMemoryPhase.ERROR, error = result.message)
                is FileSelectionResult.Accepted -> performUpload(result.file, contentResolver)
            }
        }
    }

    fun uploadSelectedFile(file: SelectedMemoryFile, contentResolver: ContentResolver) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch { performUpload(file, contentResolver) }
    }

    private suspend fun performUpload(file: SelectedMemoryFile, contentResolver: ContentResolver) {
        _uiState.value = AddMemoryUiState(phase = AddMemoryPhase.UPLOADING, selectedFile = file, message = "Uploading…")
        runCatching { repository.uploadMemory(file, "Imported from Android", contentResolver) }
            .onSuccess { result ->
                _uiState.update { it.copy(memory = result.memory, message = "Processing…", phase = AddMemoryPhase.PROCESSING) }
                when (result.memory.processingStatus) {
                    ProcessingStatus.PROCESSING -> pollUntilComplete(result.memory.id)
                    ProcessingStatus.READY -> finish(result.memory)
                    ProcessingStatus.FAILED -> _uiState.update { it.copy(phase = AddMemoryPhase.ERROR, error = result.memory.processingError ?: "RecallX couldn't finish processing this memory.", message = null) }
                }
            }
            .onFailure { error -> _uiState.update { it.copy(phase = AddMemoryPhase.ERROR, error = error.userMessage(), message = null) } }
    }

    private suspend fun pollUntilComplete(memoryId: String) {
        repeat(MAX_STATUS_POLLS) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return
            delay(STATUS_POLL_INTERVAL_MS)
            runCatching { repository.getMemoryStatus(memoryId) }
                .onSuccess { status ->
                    when (status.processingStatus) {
                        ProcessingStatus.READY -> { val memory = runCatching { repository.getMemory(memoryId) }.getOrNull(); if (memory != null) finish(memory) else _uiState.update { it.copy(phase = AddMemoryPhase.ERROR, error = "Memory saved, but its details are not available yet.", message = null) }; return }
                        ProcessingStatus.FAILED -> { _uiState.update { it.copy(phase = AddMemoryPhase.ERROR, error = status.processingError ?: "RecallX couldn't finish processing this memory.", message = null) }; return }
                        ProcessingStatus.PROCESSING -> Unit
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(phase = AddMemoryPhase.ERROR, error = error.userMessage(), message = null) }; return }
        }
        _uiState.update { it.copy(phase = AddMemoryPhase.ERROR, error = "Processing is taking longer than expected. You can check the Library shortly.", message = null) }
    }

    private fun finish(memory: Memory) { _uiState.update { it.copy(phase = AddMemoryPhase.SUCCESS, memory = memory, message = "Memory saved") } }
    fun reset() { uploadJob?.cancel(); _uiState.value = AddMemoryUiState() }

    companion object { private const val STATUS_POLL_INTERVAL_MS = 2_000L; private const val MAX_STATUS_POLLS = 30 }
}
