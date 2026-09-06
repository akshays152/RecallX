package com.recallx.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recallx.core.model.PickerKind
import com.recallx.data.repository.RecallXRepository

@Composable
fun AddMemoryScreen(repository: RecallXRepository, onBack: () -> Unit, onViewMemory: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: AddMemoryViewModel = viewModel(factory = repositoryFactory { AddMemoryViewModel(repository) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.selectAndUpload(context.contentResolver, uri, PickerKind.IMAGE) else vm.cancelSelecting()
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.selectAndUpload(context.contentResolver, uri, PickerKind.DOCUMENT) else vm.cancelSelecting()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        TextButton(onClick = onBack, enabled = state.phase != AddMemoryPhase.UPLOADING && state.phase != AddMemoryPhase.PROCESSING, modifier = Modifier.padding(top = 8.dp)) { Text("‹ Back") }
        when (state.phase) {
            AddMemoryPhase.SUCCESS -> SuccessContent(state, onViewMemory, vm::reset)
            AddMemoryPhase.UPLOADING, AddMemoryPhase.PROCESSING -> ProcessingContent(state)
            AddMemoryPhase.ERROR -> ErrorContent(state.error.orEmpty(), vm::reset)
            else -> PickerContent(
                onGallery = { vm.beginSelecting(); imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onDocument = { vm.beginSelecting(); documentPicker.launch(arrayOf("application/pdf", "text/plain", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                selecting = state.phase == AddMemoryPhase.SELECTING
            )
        }
    }
}

@Composable private fun PickerContent(onGallery: () -> Unit, onDocument: () -> Unit, selecting: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Add a memory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Save an image, document, or text file so RecallX can organize it for you.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onGallery, enabled = !selecting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Add from Gallery / Photos") }
        OutlinedButton(onClick = onDocument, enabled = !selecting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Add Document / File") }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Supported files", style = MaterialTheme.typography.titleMedium); Text("Images: JPG, JPEG, PNG, WebP\nDocuments: PDF, TXT, DOC, DOCX\nMaximum size: 20 MB", style = MaterialTheme.typography.bodyMedium) } }
    }
}

@Composable private fun ProcessingContent(state: AddMemoryUiState) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(if (state.phase == AddMemoryPhase.UPLOADING) "Uploading…" else "Processing…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        state.selectedFile?.let { Text(it.displayName, style = MaterialTheme.typography.bodyLarge) }
        Text("You can keep this screen open while RecallX prepares your memory.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun SuccessContent(state: AddMemoryUiState, onViewMemory: (String) -> Unit, onAddAnother: () -> Unit) {
    val memory = state.memory
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Memory saved", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (memory != null) {
            Text(memory.title, style = MaterialTheme.typography.titleLarge)
            Text(memory.summary, style = MaterialTheme.typography.bodyLarge)
            Text("${memory.fileType.name.lowercase().replaceFirstChar { it.uppercase() }} · ${memory.processingStatus.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onViewMemory(memory.id) }, modifier = Modifier.fillMaxWidth()) { Text("View Memory") }
        }
        OutlinedButton(onClick = onAddAnother, modifier = Modifier.fillMaxWidth()) { Text("Add Another Memory") }
    }
}

@Composable private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Couldn't save memory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
    }
}
