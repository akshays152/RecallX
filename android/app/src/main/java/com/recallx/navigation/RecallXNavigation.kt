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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recallx.core.network.dto.MemoryDto
import com.recallx.core.network.dto.SearchHitDto
import com.recallx.data.repository.DefaultRecallXRepository
import com.recallx.data.repository.RecallXClientException
import com.recallx.data.repository.SampleRecallXRepository
import com.recallx.data.repository.RecallXRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecallXNavHost(activity: Activity) {
    val preferences = remember { activity.getSharedPreferences("recallx", Activity.MODE_PRIVATE) }
    var serverUrl by remember { mutableStateOf(preferences.getString("server_url", DefaultRecallXRepository.EMULATOR_BASE_URL) ?: DefaultRecallXRepository.EMULATOR_BASE_URL) }
    var editingServer by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf(serverUrl) }
    val liveRepository = remember(serverUrl) { DefaultRecallXRepository.create(serverUrl) }
    val sampleRepository = remember { SampleRecallXRepository(activity) }
    var sampleMode by rememberSaveable { mutableStateOf(true) }
    val repository: RecallXRepository = if (sampleMode) sampleRepository else liveRepository
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf<List<MemoryDto>>(emptyList()) }
    var hits by remember { mutableStateOf<List<SearchHitDto>?>(null) }
    var selected by remember { mutableStateOf<MemoryDto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf<Boolean?>(null) }
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
        val requestMode = sampleMode
        val requestServer = serverUrl
        scope.launch {
            busy = true
            runCatching { repository.listMemories() }
                .onSuccess { if (requestMode == sampleMode && requestServer == serverUrl) { memories = it; error = null; if (!sampleMode) connected = true } }
                .onFailure { if (requestMode == sampleMode && requestServer == serverUrl) { error = it.userMessage("Cannot load memories right now."); if (!sampleMode) connected = false } }
            if (requestMode == sampleMode && requestServer == serverUrl) busy = false
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
                val memory = liveRepository.ingestFile(staged, name, uri.toString(), type)
                selected = memory
                memories = liveRepository.listMemories()
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
    LaunchedEffect(serverUrl, sampleMode) { memories = emptyList(); connected = null; refresh() }

    fun useLiveLibrary() {
        sampleMode = false
        hits = null
        selected = null
        query = ""
    }

    RecallXApp(
        memories = memories, hits = hits, selected = selected, query = query,
        busy = busy, error = error,
        voiceMessage = voiceError ?: when (voiceStatus) {
            VoiceStatus.LISTENING -> "Listening… Speak your search query."
            VoiceStatus.PROCESSING -> "Processing your voice query…"
            VoiceStatus.IDLE -> null
        },
        voiceBusy = voiceStatus != VoiceStatus.IDLE,
        connected = connected, serverUrl = serverUrl, sampleMode = sampleMode,
        onSampleMode = { sampleMode = it; hits = null; selected = null; query = ""; error = null },
        onRestoreSamples = {
            sampleRepository.restore(); sampleMode = true; hits = null; selected = null; query = ""; error = null
            scope.launch { memories = sampleRepository.listMemories() }
        },
        onQuery = { query = it },
        onSearch = {
            val requestMode = sampleMode
            val requestServer = serverUrl
            scope.launch {
                busy = true
                runCatching { repository.search(query.trim()) }
                    .onSuccess { if (requestMode == sampleMode && requestServer == serverUrl) { hits = it; error = null } }
                    .onFailure { if (requestMode == sampleMode && requestServer == serverUrl) error = it.userMessage("Search failed. Please try again.") }
                if (requestMode == sampleMode && requestServer == serverUrl) busy = false
            }
        },
        onVoice = {
            voiceError = null
            if (speechRecognizer == null) {
                voiceError = "Voice recognition is unavailable on this device."
            } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startVoiceRecognition(activity, speechRecognizer) { status, message ->
                    voiceStatus = status
                    voiceError = message
                }
            }
        },
        onRefresh = { refresh() }, onSelect = { selected = it },
        onClear = { hits = null; query = "" },
        onFile = { useLiveLibrary(); pickFile.launch("*/*") },
        onText = { useLiveLibrary(); addingText = true },
        onCamera = {
            useLiveLibrary()
            val file = File(activity.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            pendingPhoto = uri
            takePicture.launch(uri)
        },
        onServer = { editingServer = true; serverDraft = serverUrl },
        onOpen = { memory ->
            if (memory.sourceUri.startsWith("sample://")) scope.launch {
                try {
                    val uri = sampleRepository.originalUri(memory)
                    activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, memory.mediaType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: android.content.ActivityNotFoundException) {
                    error = "No viewer is installed for this file. You can read its extracted text here."
                } catch (_: IOException) {
                    error = "Could not open this sample file. Please try again."
                }
            } else repository.contentUrl(memory)?.let { url ->
                try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: android.content.ActivityNotFoundException) { error = "No app is available to open this file. Install a browser or document viewer." }
            } ?: run { error = "Original file unavailable" }
        },
        onDelete = { memory ->
            scope.launch {
                busy = true
                try {
                    repository.deleteMemory(memory.id)
                    selected = null
                    memories = repository.listMemories()
                    hits = null
                    error = null
                } catch (exception: Exception) {
                    error = exception.userMessage("Delete failed. Please try again.")
                } finally {
                    busy = false
                }
            }
        }
    )

    if (editingServer) AlertDialog(onDismissRequest = { editingServer = false }, title = { Text("Recall Engine address") },
        text = { Column {
            Text("Emulator: http://10.0.2.2:8000/v1/  •  Phone: use your laptop's Wi-Fi IP with /v1/.")
            OutlinedTextField(value = serverDraft, onValueChange = { serverDraft = it }, singleLine = true)
        } }, confirmButton = { TextButton(onClick = {
            if (serverDraft.startsWith("http://") || serverDraft.startsWith("https://")) {
                val raw = serverDraft.trim().trimEnd('/')
                serverUrl = (if (raw.endsWith("/v1")) raw else "$raw/v1") + "/"
                sampleMode = false
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
                    selected = liveRepository.ingestText(textBody.trim(), textTitle.trim().ifBlank { "Message" })
                    memories = liveRepository.listMemories()
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
