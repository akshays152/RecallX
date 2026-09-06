package com.recallx.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recallx.core.model.*
import com.recallx.data.repository.RecallXRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(repository: RecallXRepository, onSearch: () -> Unit, onAddMemory: () -> Unit, onCameraSearch: () -> Unit, onOpenMemory: (String) -> Unit, onOpenLibrary: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = repositoryFactory { HomeViewModel(repository) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("RecallX", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text("Welcome back", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Your phone remembers so you don't have to.", style = MaterialTheme.typography.bodyLarge) }
        item { OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("⌕  Search your memories", modifier = Modifier.fillMaxWidth()) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = onAddMemory, modifier = Modifier.weight(1f)) { Text("Add Memory") }; OutlinedButton(onClick = onCameraSearch, modifier = Modifier.weight(1f)) { Text("Camera Search") } } }
        item { SectionHeader("Recent memories", onOpenLibrary) }
        when {
            state.isLoading -> item { LoadingBlock() }
            state.error != null -> item { ErrorBlock(state.error!!, vm::load) }
            state.memories.isEmpty() -> item { EmptyBlock("No memories yet", "Add your first memory to start building RecallX.") }
            else -> items(state.memories.take(4), key = { it.id }) { memory -> MemoryCard(memory, onClick = { onOpenMemory(memory.id) }) }
        }
        if (!state.isLoading && state.error == null && state.memories.isNotEmpty()) {
            item { SectionHeader("Types in recent memories", null) }
            item { TypeSummary(state.memories) }
        }
    }
}

@Composable
fun LibraryScreen(repository: RecallXRepository, onMemory: (String) -> Unit, onAddMemory: () -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = repositoryFactory { LibraryViewModel(repository) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val types = listOf(null to "All", "screenshot" to "Screenshots", "image" to "Images", "pdf" to "PDFs", "document" to "Documents")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Everything you have saved in RecallX.", style = MaterialTheme.typography.bodyLarge) }; TextButton(onClick = onAddMemory) { Text("Add Memory") } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { types.forEach { (value, label) -> FilterChip(selected = state.selectedType == value, onClick = { vm.selectType(value) }, label = { Text(label) }) } } }
        if (state.isRefreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        when {
            state.isLoading -> item { LoadingBlock() }
            state.error != null && state.memories.isEmpty() -> item { ErrorBlock(state.error!!, vm::refresh) }
            state.memories.isEmpty() -> item { EmptyBlock("Nothing here yet", "Try another filter or add a memory.") }
            else -> {
                itemsIndexed(state.memories, key = { _, memory -> memory.id }) { _, memory ->
                    MemoryCard(memory, onClick = { onMemory(memory.id) })
                }
                if (state.isLoadingMore) item { Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
                if (state.hasMore && !state.isLoadingMore) item { OutlinedButton(onClick = vm::loadNextPage, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
                if (state.error != null) item { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun MemoryDetailScreen(repository: RecallXRepository, memoryId: String, onBack: () -> Unit, onDeleted: () -> Unit, onOpenMemory: (String) -> Unit) {
    val vm: MemoryDetailViewModel = viewModel(key = memoryId, factory = repositoryFactory { MemoryDetailViewModel(repository, memoryId) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(onClick = onBack) { Text("‹ Back") }; if (state.memory != null) TextButton(onClick = vm::delete, enabled = !state.isDeleting) { Text(if (state.isDeleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) } }
        when {
            state.isLoading -> LoadingBlock()
            state.error != null && state.memory == null -> ErrorBlock(state.error!!, vm::load)
            state.memory != null -> DetailContent(state, onOpenMemory)
        }
    }
}

@Composable private fun DetailContent(state: DetailUiState, onOpenMemory: (String) -> Unit) {
    val memory = state.memory ?: return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(memory.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(memory.summary, style = MaterialTheme.typography.bodyLarge); Text("${memory.fileType.displayName()} · ${memory.createdAt.displayDate()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.error != null) item { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        item { StatusCard(memory) }
        if (state.isLoadingContent) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.content != null) item { ContentPreview(state.content) }
        if (state.contentError != null) item { Text("Preview unavailable: ${state.contentError}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (memory.fileType in setOf(MemoryFileType.PDF, MemoryFileType.DOCUMENT) && memory.contentAvailable) item { Text("Original ${memory.fileType.displayName().lowercase()} available", color = MaterialTheme.colorScheme.primary) }
        if (memory.extractedText.isNotBlank()) item { DetailSection("Extracted text") { ExpandableText(memory.extractedText) } }
        if (memory.entities.isNotEmpty()) item { DetailSection("Entities") { memory.entities.forEach { (key, value) -> Text("$key: $value") } } }
        if (memory.tags.isNotEmpty()) item { DetailSection("Tags") { Text(memory.tags.joinToString("  ·  ")) } }
        item { DetailSection("Source") { Text(memory.source) } }
        if (state.related.isNotEmpty()) item { DetailSection("Related memories") { state.related.forEach { result -> TextButton(onClick = { onOpenMemory(result.memory.id) }) { Text(result.memory.title) } } } }
    }
}

@Composable private fun ContentPreview(content: MemoryContent) {
    val bitmap = remember(content.bytes) { decodePreview(content.bytes) }
    Card(Modifier.fillMaxWidth()) { if (bitmap != null) Image(bitmap.asImageBitmap(), "Memory preview", Modifier.fillMaxWidth().heightIn(max = 280.dp)) else Text("Original content loaded (${content.mediaType ?: "file"}).", Modifier.padding(16.dp)) }
}

private fun decodePreview(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable private fun MemoryCard(memory: Memory, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(memory.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); StatusBadge(memory.processingStatus) }; Text(memory.summary, maxLines = 2, style = MaterialTheme.typography.bodyMedium); Text("${memory.fileType.displayName()} · ${memory.createdAt.displayDate()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun StatusCard(memory: Memory) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Processing status", style = MaterialTheme.typography.labelLarge); StatusBadge(memory.processingStatus); if (memory.processingError != null) Text(memory.processingError.orEmpty(), color = MaterialTheme.colorScheme.error) } } }
@Composable private fun StatusBadge(status: ProcessingStatus) { AssistChip(onClick = {}, enabled = false, label = { Text(status.displayName()) }) }
@Composable private fun SectionHeader(title: String, onClick: (() -> Unit)?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); if (onClick != null) TextButton(onClick = onClick) { Text("See all") } } }
@Composable private fun TypeSummary(memories: List<Memory>) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { memories.groupingBy { it.fileType }.eachCount().entries.take(3).forEach { (type, count) -> Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(type.displayName(), style = MaterialTheme.typography.bodySmall) } } } } }
@Composable private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } }
@Composable private fun ExpandableText(text: String) { var expanded by remember(text) { mutableStateOf(false) }; Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(text, maxLines = if (expanded) Int.MAX_VALUE else 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis); TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") } } }
@Composable private fun LoadingBlock() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
@Composable private fun EmptyBlock(title: String, body: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium) } } }
@Composable private fun ErrorBlock(message: String, retry: () -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(message, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = retry) { Text("Try again") } } } }

fun <T : ViewModel> repositoryFactory(create: () -> T) = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM }
private fun MemoryFileType.displayName() = name.lowercase().replaceFirstChar { it.uppercase() }
private fun ProcessingStatus.displayName() = name.lowercase().replaceFirstChar { it.uppercase() }
fun String.displayDate(): String = runCatching { DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.parse(this).atZone(ZoneId.systemDefault())) }.getOrDefault(this)
