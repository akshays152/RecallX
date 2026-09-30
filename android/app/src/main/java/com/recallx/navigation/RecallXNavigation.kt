package com.recallx.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recallx.core.network.dto.MemoryDto
import com.recallx.core.network.dto.SearchHitDto
import com.recallx.data.repository.DefaultRecallXRepository
import com.recallx.data.repository.RecallXClientException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecallXNavHost(activity: Activity) {
    val preferences = remember { activity.getSharedPreferences("recallx", Activity.MODE_PRIVATE) }
    var serverUrl by remember { mutableStateOf(preferences.getString("server_url", DefaultRecallXRepository.EMULATOR_BASE_URL) ?: DefaultRecallXRepository.EMULATOR_BASE_URL) }
    var editingServer by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf(serverUrl) }
    val repository = remember(serverUrl) { DefaultRecallXRepository.create(serverUrl) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf<List<MemoryDto>>(emptyList()) }
    var hits by remember { mutableStateOf<List<SearchHitDto>?>(null) }
    var selected by remember { mutableStateOf<MemoryDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var addingText by remember { mutableStateOf(false) }
    var textTitle by remember { mutableStateOf("Message") }
    var textBody by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf(VoiceStatus.IDLE) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember(activity) {
        if (SpeechRecognizer.isRecognitionAvailable(activity)) SpeechRecognizer.createSpeechRecognizer(activity) else null
    }
    val requestAudioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecognition(activity, speechRecognizer) { status, message ->
            voiceStatus = status
            voiceError = message
        } else {
            voiceStatus = VoiceStatus.IDLE
            voiceError = "Voice recognition unavailable. Please allow microphone permission."
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { voiceStatus = VoiceStatus.LISTENING }
            override fun onBeginningOfSpeech() { voiceStatus = VoiceStatus.LISTENING }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { voiceStatus = VoiceStatus.PROCESSING }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onError(error: Int) {
                Log.w("RecallXVoice", "SpeechRecognizer error=$error")
                voiceStatus = VoiceStatus.IDLE
                voiceError = speechErrorMessage(error)
            }
            override fun onResults(results: Bundle?) {
                val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                voiceStatus = VoiceStatus.IDLE
                if (recognized.isBlank()) {
                    voiceError = "I couldn't hear a search query. Please try again."
                } else {
                    query = recognized
                    voiceError = null
                }
            }
        })
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
    }

    fun refresh() {
        scope.launch {
            busy = true
            runCatching { repository.listMemories() }
                .onSuccess { memories = it; error = null }
                .onFailure { error = it.userMessage("Cannot load memories right now.") }
            busy = false
        }
    }

    fun ingest(uri: Uri) {
        scope.launch {
            busy = true
            error = null
            var temporary: File? = null
            try {
                val name = displayName(activity, uri) ?: "memory-${System.currentTimeMillis()}.jpg"
                val type = activity.contentResolver.getType(uri) ?: "application/octet-stream"
                val staged = withContext(Dispatchers.IO) {
                    File.createTempFile("recallx-", ".${name.substringAfterLast('.', "bin")}", activity.cacheDir).also { file ->
                        activity.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
                            ?: throw IllegalStateException("Cannot read selected file")
                    }
                }
                temporary = staged
                val memory = repository.ingestFile(staged, name, uri.toString(), type)
                selected = memory
                memories = repository.listMemories()
                hits = null
            } catch (exception: Exception) {
                error = exception.userMessage("Upload failed. Please try again.")
            } finally {
                temporary?.delete()
                busy = false
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) ingest(uri)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingPhoto?.let(::ingest)
    }
    LaunchedEffect(serverUrl) { refresh() }

    Scaffold(topBar = {
        TopAppBar(title = { Text("RecallX") }, actions = {
            TextButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
            TextButton(onClick = { editingServer = true; serverDraft = serverUrl }) { Text("Server") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Your phone's personal memory", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickFile.launch("*/*") }, enabled = !busy) { Text("Add file") }
                OutlinedButton(onClick = { addingText = true }, enabled = !busy) { Text("Add text") }
                OutlinedButton(onClick = {
                    val file = File(activity.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                    pendingPhoto = uri
                    takePicture.launch(uri)
                }, enabled = !busy) { Text("Camera") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search your memories") },
                placeholder = { Text("Find that hotel price screenshot") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        busy = true
                        runCatching { repository.search(query.trim()) }
                            .onSuccess { hits = it; error = null }
                            .onFailure { error = it.userMessage("Search failed. Please try again.") }
                        busy = false
                    }
                }, enabled = query.isNotBlank() && !busy) { Text("Search") }
                OutlinedButton(onClick = {
                    voiceError = null
                    if (speechRecognizer == null) {
                        voiceError = "Voice recognition is unavailable on this emulator."
                    } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        startVoiceRecognition(activity, speechRecognizer) { status, message ->
                            voiceStatus = status
                            voiceError = message
                        }
                    }
                }, enabled = voiceStatus != VoiceStatus.PROCESSING) { Text(if (voiceStatus == VoiceStatus.LISTENING) "Listening..." else "Tap to speak") }
                if (hits != null) TextButton(onClick = { hits = null; query = "" }) { Text("Clear") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            voiceError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(if (hits == null) "Library (${memories.size})" else "Results (${hits!!.size})", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            val displayed = hits?.map { it.memory } ?: memories
            if (displayed.isEmpty() && !busy) Text(if (hits == null) "No memories yet. Add a screenshot, photo, PDF, or audio file." else "No matching memories.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayed, key = { it.id }) { memory ->
                    val hit = hits?.firstOrNull { it.memory.id == memory.id }
                    ElevatedCard(onClick = { selected = memory }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(memory.title, style = MaterialTheme.typography.titleMedium)
                            Text(memory.mediaType + "  •  " + memory.createdAt.take(10), style = MaterialTheme.typography.labelSmall)
                            Text((hit?.highlights?.firstOrNull() ?: memory.text).ifBlank { "Tap to open memory" },
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            hit?.let { Text("Match ${(it.score * 100).toInt()}%  •  ${it.reasons.joinToString()}", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }

    selected?.let { memory ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(memory.title) }, text = {
            Column {
                Text("Added ${memory.createdAt.take(10)} • ${memory.mediaType}")
                Spacer(Modifier.height(8.dp))
                Text(memory.text.ifBlank { "No text was extracted. Open the original file." }, maxLines = 12)
                if (memory.labels.isNotEmpty()) Text("Labels: ${memory.labels.joinToString()}")
                memory.metadata["warning"]?.let { Text("Processing warning: $it", color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(onClick = {
                repository.contentUrl(memory)?.let { url ->
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } ?: run { error = "Original file unavailable" }
            }) { Text("Open original") }
        }, dismissButton = {
            Row {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            repository.deleteMemory(memory.id)
                            selected = null
                            memories = repository.listMemories()
                            hits = null
                        } catch (exception: Exception) {
                            error = exception.userMessage("Delete failed. Please try again.")
                        }
                    }
                }) { Text("Delete") }
                TextButton(onClick = { selected = null }) { Text("Close") }
            }
        })
    }

    if (editingServer) AlertDialog(onDismissRequest = { editingServer = false }, title = { Text("Recall Engine address") },
        text = { Column {
            Text("Emulator: http://10.0.2.2:8000/v1/  •  Phone: use your laptop's Wi-Fi IP with /v1/.")
            OutlinedTextField(value = serverDraft, onValueChange = { serverDraft = it }, singleLine = true)
        } }, confirmButton = { TextButton(onClick = {
            if (serverDraft.startsWith("http://") || serverDraft.startsWith("https://")) {
                val raw = serverDraft.trim().trimEnd('/')
                serverUrl = (if (raw.endsWith("/v1")) raw else "$raw/v1") + "/"
                preferences.edit().putString("server_url", serverUrl).apply()
                editingServer = false
                selected = null
                hits = null
            } else error = "Enter an http:// or https:// URL"
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editingServer = false }) { Text("Cancel") } })

    if (addingText) AlertDialog(onDismissRequest = { addingText = false }, title = { Text("Save a message or note") },
        text = { Column {
            OutlinedTextField(value = textTitle, onValueChange = { textTitle = it }, label = { Text("Title") }, singleLine = true)
            OutlinedTextField(value = textBody, onValueChange = { textBody = it }, label = { Text("Text") }, minLines = 4)
        } }, confirmButton = { TextButton(onClick = {
            scope.launch {
                busy = true
                try {
                    selected = repository.ingestText(textBody.trim(), textTitle.trim().ifBlank { "Message" })
                    memories = repository.listMemories()
                    addingText = false
                    textBody = ""
                    hits = null
                    error = null
                } catch (exception: Exception) {
                    error = exception.userMessage("Save failed. Please try again.")
                }
                busy = false
            }
        }, enabled = textBody.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { addingText = false }) { Text("Cancel") } })
}

private fun displayName(activity: Activity, uri: Uri): String? {
    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0)
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

private fun Throwable.userMessage(fallback: String): String = when (this) {
    is RecallXClientException -> message
    else -> fallback
}

private enum class VoiceStatus { IDLE, LISTENING, PROCESSING }

private fun startVoiceRecognition(context: Context, recognizer: SpeechRecognizer?, onState: (VoiceStatus, String?) -> Unit) {
    if (recognizer == null) {
        onState(VoiceStatus.IDLE, "Voice recognition is unavailable on this emulator.")
        return
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    runCatching {
        onState(VoiceStatus.LISTENING, null)
        recognizer.startListening(intent)
    }.onFailure {
        onState(VoiceStatus.IDLE, "Voice recognition unavailable. Please check microphone permission.")
    }
}

private fun speechErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Voice recognition unavailable. Please check microphone permission."
    SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_CLIENT -> "Microphone input failed. Please try again."
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I couldn't hear a search query. Please try again."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognition is busy. Please try again in a moment."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "Voice recognition could not connect. Check the emulator's speech service."
    else -> "Voice recognition failed. Please try again."
}
