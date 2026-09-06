package com.recallx.core.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recallx.core.model.SearchResult
import com.recallx.data.repository.RecallXRepository
import java.io.File

@Composable
fun CameraSearchScreen(repository: RecallXRepository, onBack: () -> Unit, onResults: () -> Unit) {
    val context = LocalContext.current
    val vm: VisualSearchViewModel = viewModel(factory = repositoryFactory { VisualSearchViewModel(repository) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var askedForPermission by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted; askedForPermission = true }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.prepareGalleryImage(context.contentResolver, uri, context.cacheDir) }

    LaunchedEffect(Unit) { if (!hasPermission) { askedForPermission = true; permissionLauncher.launch(Manifest.permission.CAMERA) } else vm.cameraReady() }
    LaunchedEffect(state.phase) { if (state.phase == VisualSearchPhase.SUCCESS || state.phase == VisualSearchPhase.EMPTY) onResults() }

    when {
        state.phase == VisualSearchPhase.IMAGE_CAPTURED -> CapturedImageContent(state, vm, onBack)
        state.phase == VisualSearchPhase.SEARCHING -> SearchingContent(state, onBack)
        state.phase == VisualSearchPhase.ERROR -> VisualSearchErrorContent(state, vm, onBack)
        !hasPermission -> CameraPermissionContent(context, askedForPermission, onBack, onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        else -> CameraCaptureContent(onBack, cameraError, onCameraError = { cameraError = it }, onReady = { cameraError = null; vm.cameraReady() }, onCapture = { file -> vm.prepareCapturedImage(file, context.cacheDir) }, onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
    }
}

@Composable private fun CameraCaptureContent(onBack: () -> Unit, error: String?, onCameraError: (String) -> Unit, onReady: () -> Unit, onCapture: (File) -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val cameraProvider = future.get()
                    val preview = Preview.Builder().setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3).build()
                    val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(view.display?.rotation ?: android.view.Surface.ROTATION_0).build()
                    preview.setSurfaceProvider(view.surfaceProvider)
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    provider = cameraProvider
                    imageCapture = capture
                    onReady()
                } catch (_: Exception) { onCameraError("Camera isn't available right now. Try again or use an image from Photos.") }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) { onCameraError("Camera isn't available right now. Try again or use an image from Photos.") }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll(); imageCapture = null } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ Back") }; Text("Camera Search", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); TextButton(onClick = onGallery) { Text("Photos") } }
        Text("Point your camera at something you've saved before.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
            AndroidView(factory = { PreviewView(it).also { previewView = it } }, modifier = Modifier.fillMaxSize())
            if (error != null) Card(Modifier.align(Alignment.Center).padding(24.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(error); OutlinedButton(onClick = onGallery) { Text("Choose from Photos") } } }
        }
        Button(onClick = {
            val capture = imageCapture ?: return@Button
            val file = File.createTempFile("recallx-capture-", ".jpg", context.cacheDir)
            capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { onCapture(file) }
                override fun onError(exception: ImageCaptureException) { file.delete(); onCameraError("We couldn't capture that image. Please try again.") }
            })
        }, enabled = imageCapture != null, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp).heightIn(min = 54.dp)) { Text("Capture") }
    }
}

@Composable private fun CapturedImageContent(state: VisualSearchUiState, vm: VisualSearchViewModel, onBack: () -> Unit) {
    var question by remember(state.image) { mutableStateOf(state.question) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Review image", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        state.image?.let { image -> FilePreview(image.file, Modifier.fillMaxWidth().heightIn(max = 300.dp)) }
        OutlinedTextField(question, { question = it; vm.setQuestion(it) }, label = { Text("Question (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(onClick = vm::search, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Search Memory") }
        OutlinedButton(onClick = vm::retake, modifier = Modifier.fillMaxWidth()) { Text("Retake") }
    }
}

@Composable private fun SearchingContent(state: VisualSearchUiState, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { TextButton(onClick = onBack) { Text("‹ Back") }; Text("Searching your memories…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); state.image?.let { FilePreview(it.file, Modifier.fillMaxWidth().heightIn(max = 300.dp)) }; CircularProgressIndicator() } }
@Composable private fun VisualSearchErrorContent(state: VisualSearchUiState, vm: VisualSearchViewModel, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text("Visual search couldn't be completed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(state.error ?: "Please try again."); Spacer(Modifier.height(18.dp)); if (state.image != null) Button(onClick = vm::search, modifier = Modifier.fillMaxWidth()) { Text("Try again") }; OutlinedButton(onClick = vm::retake, modifier = Modifier.fillMaxWidth()) { Text("Retake") }; TextButton(onClick = onBack) { Text("Back") } } }

@Composable
fun VisualSearchResultsScreen(vm: VisualSearchViewModel, onBack: () -> Unit, onOpenMemory: (String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton(onClick = onBack) { Text("‹ Back") }; Text("Visual Search Results", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        state.image?.let { image -> item { FilePreview(image.file, Modifier.fillMaxWidth().heightIn(max = 220.dp)) } }
        item { Text("Question: ${state.response?.question ?: state.question}", style = MaterialTheme.typography.bodyLarge) }
        if (state.phase == VisualSearchPhase.EMPTY) item { EmptyVisualResults(vm::retake, onBack) }
        else if (state.results.isNotEmpty()) items(state.results, key = { it.memory.id }) { result -> VisualResultCard(result) { onOpenMemory(result.memory.id) } }
        item { OutlinedButton(onClick = { vm.retake(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Try another photo") } }
    }
}

@Composable private fun EmptyVisualResults(onRetake: () -> Unit, onBack: () -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("No related memories found.", style = MaterialTheme.typography.titleMedium); Text("Try a clearer photo or a different angle."); OutlinedButton(onClick = { onRetake(); onBack() }) { Text("Retake") } } } }
@Composable private fun VisualResultCard(result: SearchResult, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(result.memory.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(result.memory.summary); Text("${result.memory.fileType.name.lowercase().replaceFirstChar { it.uppercase() }} · ${result.memory.createdAt.displayDate()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (result.score != null) Text("Match score: ${result.score}", style = MaterialTheme.typography.bodySmall); if (!result.matchReason.isNullOrBlank()) Text(result.matchReason.orEmpty(), style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun FilePreview(file: File, modifier: Modifier) { val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath) }; if (bitmap != null) Image(bitmap.asImageBitmap(), "Captured search image", modifier) else Text("Image preview unavailable") }
@Composable private fun CameraPermissionContent(context: android.content.Context, asked: Boolean, onBack: () -> Unit, onGallery: () -> Unit, onRetry: () -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("Camera access needed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Allow camera access to search RecallX with a new photo.", style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(20.dp)); Button(onClick = onRetry) { Text("Allow Camera") }; OutlinedButton(onClick = onGallery) { Text("Choose from Photos") }; if (asked) OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open Settings") }; TextButton(onClick = onBack) { Text("Back") } } }
