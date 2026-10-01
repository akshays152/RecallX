package com.recallx.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recallx.core.network.dto.MemoryDto
import com.recallx.core.network.dto.SearchHitDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private enum class AppTab(val label: String, val symbol: Symbol) {
    LIBRARY("Library", Symbol.GRID), SEARCH("Search", Symbol.SEARCH), SETTINGS("Settings", Symbol.SETTINGS)
}

private enum class MemoryFilter(val label: String) {
    ALL("All"), IMAGES("Images"), DOCUMENTS("Documents"), NOTES("Notes"), AUDIO("Audio");

    fun accepts(memory: MemoryDto): Boolean = when (this) {
        ALL -> true
        IMAGES -> memory.mediaType.startsWith("image/")
        DOCUMENTS -> memory.mediaType.startsWith("application/") || memory.sourceUri.endsWith(".docx", true)
        NOTES -> memory.mediaType.startsWith("text/")
        AUDIO -> memory.mediaType.startsWith("audio/")
    }
}

// Small, local vector icons keep the UI independent of another icon dependency.
private enum class Symbol(val path: String) {
    SEARCH("M21 21L16 16 M18 10A8 8 0 1 1 2 10A8 8 0 1 1 18 10"),
    GRID("M3 3H10V10H3Z M14 3H21V10H14Z M3 14H10V21H3Z M14 14H21V21H14Z"),
    SETTINGS("M9 3H15L16 6L19 7L22 10V14L19 17L16 18L15 21H9L8 18L5 17L2 14V10L5 7L8 6Z M16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12"),
    MIC("M9 5A3 3 0 0 1 15 5V12A3 3 0 0 1 9 12Z M5 10V12A7 7 0 0 0 19 12V10 M12 19V22 M8 22H16"),
    IMAGE("M3 3H21V21H3Z M3 16L8 11L13 16L16 13L21 18 M17 7H17.01"),
    DOCUMENT("M5 2H14L19 7V22H5Z M14 2V7H19 M8 12H16 M8 16H16 M8 19H13"),
    NOTE("M4 3H20V21H4Z M8 8H16 M8 12H16 M8 16H13"),
    AUDIO("M4 9V15 M8 5V19 M12 2V22 M16 5V19 M20 9V15"),
    ADD("M12 5V19 M5 12H19"),
    BACK("M20 12H4 M11 5L4 12L11 19"),
    REFRESH("M20 7V2 M20 7H15 M20 7A9 9 0 1 0 21 15"),
    MORE("M12 4H12.01 M12 12H12.01 M12 20H12.01"),
    EXTERNAL("M14 3H21V10 M21 3L10 14 M10 3H3V21H21V14"),
    CAMERA("M3 7H7L9 4H15L17 7H21V21H3Z M16 14A4 4 0 1 1 8 14A4 4 0 1 1 16 14"),
    CLOSE("M6 6L18 18 M18 6L6 18"),
    DELETE("M3 6H21 M9 6V3H15V6 M5 6L6 21H18L19 6 M10 10V17 M14 10V17")
}

@Composable
private fun Glyph(symbol: Symbol, modifier: Modifier = Modifier, description: String? = null) {
    val vector = remember(symbol) {
        ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(PathParser().parsePathString(symbol.path).toNodes(),
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()
    }
    Icon(vector, description, modifier.size(22.dp))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RecallXApp(
    memories: List<MemoryDto>, hits: List<SearchHitDto>?, selected: MemoryDto?,
    query: String, busy: Boolean, error: String?, voiceMessage: String?, voiceBusy: Boolean,
    connected: Boolean?, serverUrl: String, onQuery: (String) -> Unit,
    sampleMode: Boolean, onSampleMode: (Boolean) -> Unit, onRestoreSamples: () -> Unit,
    onSearch: () -> Unit, onVoice: () -> Unit, onRefresh: () -> Unit,
    onSelect: (MemoryDto?) -> Unit, onClear: () -> Unit,
    onFile: () -> Unit, onText: () -> Unit, onCamera: () -> Unit,
    onServer: () -> Unit, onOpen: (MemoryDto) -> Unit, onDelete: (MemoryDto) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.LIBRARY) }
    var filter by rememberSaveable { mutableStateOf(MemoryFilter.ALL) }
    var adding by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MemoryDto?>(null) }
    var detailMenu by remember { mutableStateOf(false) }
    fun search() { if (query.isNotBlank() && !busy) { tab = AppTab.SEARCH; onSearch() } }
    BackHandler(enabled = selected != null || tab != AppTab.LIBRARY) {
        if (selected != null) onSelect(null) else tab = AppTab.LIBRARY
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    if (selected != null) Text("Memory", fontWeight = FontWeight.SemiBold)
                    else if (tab == AppTab.LIBRARY) Column {
                        Row { Text("Recall", fontWeight = FontWeight.Bold); Text("X", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(
                                if (sampleMode) MaterialTheme.colorScheme.primary else if (connected == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant))
                            Text(if (sampleMode) "Sample library · On this phone" else when (connected) { true -> "Engine connected"; false -> "Engine unavailable"; null -> "Connecting to engine…" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else Text(tab.label, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (selected != null) IconButton(onClick = { onSelect(null) }) { Glyph(Symbol.BACK, description = "Back to memories") }
                },
                actions = {
                    if (selected == null && tab == AppTab.LIBRARY) IconButton(onClick = { tab = AppTab.SETTINGS }) { Glyph(Symbol.SETTINGS, description = "Settings") }
                    if (selected != null) Box {
                        IconButton(onClick = { detailMenu = true }) { Glyph(Symbol.MORE, description = "Memory actions") }
                        DropdownMenu(expanded = detailMenu, onDismissRequest = { detailMenu = false }) {
                            DropdownMenuItem(text = { Text(if (selected.sourceUri.startsWith("sample://")) "Hide sample" else "Delete memory") }, leadingIcon = { Glyph(Symbol.DELETE) }, enabled = !busy,
                                onClick = { detailMenu = false; deleteTarget = selected })
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selected == null) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                AppTab.entries.forEach { destination ->
                    NavigationBarItem(selected = tab == destination, onClick = { tab = destination },
                        icon = { Glyph(destination.symbol) }, label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
                }
            }
        },
        floatingActionButton = {
            if (selected == null && tab == AppTab.LIBRARY) ExtendedFloatingActionButton(
                onClick = { if (!busy) adding = true }, icon = { Glyph(Symbol.ADD) }, text = { Text("Add memory") },
                modifier = Modifier.zIndex(1f).semantics { contentDescription = "Add memory" },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(28.dp))
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { StatusMessage(it, isError = true) }
            voiceMessage?.let { StatusMessage(it, isError = !voiceBusy) }
            if (selected != null) {
                MemoryDetail(selected, serverUrl, busy, onOpen)
            } else if (tab == AppTab.SETTINGS) {
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item { Text("Your recall engine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    item {
                        Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Server connection", fontWeight = FontWeight.SemiBold)
                                Text(serverUrl, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Connect your phone and laptop to the same Wi-Fi, and keep the Recall Engine running on your laptop.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = onServer) { Text("Change server") }
                                OutlinedButton(onClick = onRefresh, enabled = !busy) { Glyph(Symbol.REFRESH); Spacer(Modifier.width(8.dp)); Text("Refresh connection") }
                            }
                        }
                    }
                    item {
                        Text("Built-in samples", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Six photos and three documents, ready to browse and search on this phone. Sample text was prepared ahead of time; your own memories use the connected engine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onRestoreSamples, enabled = !busy) { Text("Restore sample library") }
                    }
                    item {
                        Text("About RecallX", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("A searchable library of the things you save. Processing and storage currently run on your connected laptop.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val searchTab = tab == AppTab.SEARCH
                val hitById = remember(hits) { hits.orEmpty().associateBy { it.memory.id } }
                val displayed = (if (searchTab) hits?.map { it.memory }.orEmpty() else memories).filter(filter::accepts)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!searchTab) item { Text("Find what you saved.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = sampleMode, onClick = { onSampleMode(true) }, label = { Text("Samples") }, shape = RoundedCornerShape(20.dp))
                                FilterChip(selected = !sampleMode, onClick = { onSampleMode(false) }, label = { Text("My memories") }, shape = RoundedCornerShape(20.dp))
                            }
                            Text(if (sampleMode) "Built-in demo content. Search a hotel price, ticket, or invoice." else "Your memories from the connected Recall Engine.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = query, onValueChange = onQuery,
                                placeholder = { Text("Search your memories", maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                                leadingIcon = { Glyph(Symbol.SEARCH) },
                                trailingIcon = { IconButton(onClick = onVoice, enabled = !voiceBusy && !busy) { Glyph(Symbol.MIC, description = "Dictate search query") } },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                            FilledIconButton(onClick = { search() }, enabled = query.isNotBlank() && !busy,
                                modifier = Modifier.size(56.dp), shape = RoundedCornerShape(18.dp)) { Glyph(Symbol.SEARCH, description = "Search") }
                        }
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemoryFilter.entries.forEach { choice ->
                                FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(choice.label) },
                                    shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(if (searchTab) if (hits == null) "Search your library" else "${displayed.size} results" else "Recent memories",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (!searchTab) {
                                Text("${displayed.size} memories", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = onRefresh, enabled = !busy) { Glyph(Symbol.REFRESH, description = "Refresh memories") }
                            } else if (hits != null) IconButton(onClick = onClear) { Glyph(Symbol.CLOSE, description = "Clear search results") }
                        }
                    }
                    if (displayed.isEmpty() && !busy) item {
                        EmptyMemories(searchTab, hits != null, filter != MemoryFilter.ALL, onAdd = { adding = true })
                    }
                    if (searchTab) {
                        items(displayed, key = { it.id }) { memory ->
                            SearchMemoryCard(memory, hitById[memory.id], hits?.firstOrNull()?.memory?.id == memory.id, serverUrl) { onSelect(memory) }
                        }
                    } else {
                        items(displayed.chunked(2), key = { it.first().id }) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                pair.forEach { memory -> LibraryMemoryCard(memory, serverUrl, Modifier.weight(1f)) { onSelect(memory) } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add a memory") },
        text = {
            Column {
                AddOption(Symbol.IMAGE, "Add file or photo", "Images, documents, or audio", !busy) { adding = false; onFile() }
                AddOption(Symbol.NOTE, "Save a note", "Paste a message or write some text", !busy) { adding = false; onText() }
                AddOption(Symbol.CAMERA, "Take a photo", "Capture and save a new memory", !busy) { adding = false; onCamera() }
            }
        }, confirmButton = { TextButton(onClick = { adding = false }) { Text("Close") } })
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text(if (target.sourceUri.startsWith("sample://")) "Hide this sample?" else "Delete this memory?") },
            text = { Text(if (target.sourceUri.startsWith("sample://")) "This sample will be hidden from your library. You can restore samples in Settings." else "\"${target.title}\" and its retained original will be removed from the Recall Engine.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { deleteTarget = null; onDelete(target) }) { Text(if (target.sourceUri.startsWith("sample://")) "Hide" else "Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } })
    }
}

@Composable
private fun AddOption(symbol: Symbol, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Glyph(symbol)
            Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean) {
    Text(message, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun EmptyMemories(search: Boolean, searched: Boolean, filtered: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Glyph(if (search) Symbol.SEARCH else Symbol.GRID, modifier = Modifier.size(36.dp))
        Text(if (search) if (searched) "No matching memories" else "What are you looking for?" else if (filtered) "No memories of this type" else "Your memories start here", fontWeight = FontWeight.SemiBold)
        Text(if (search) "Try a detail you remember, like a hotel price or ticket." else "Add a screenshot, document, photo, or note.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (!search) OutlinedButton(onClick = onAdd) { Text("Add memory") }
    }
}

@Composable
private fun LibraryMemoryCard(memory: MemoryDto, serverUrl: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        MemoryPreview(memory, serverUrl, Modifier.fillMaxWidth().aspectRatio(1.12f))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(memory.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Glyph(memorySymbol(memory), modifier = Modifier.size(14.dp))
                Text(memory.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchMemoryCard(memory: MemoryDto, hit: SearchHitDto?, best: Boolean, serverUrl: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MemoryPreview(memory, serverUrl, Modifier.width(104.dp).height(142.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(memory.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (best) Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(12.dp)) {
                    Text("Best match", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
                Text(hit?.highlights?.firstOrNull() ?: memory.text.ifBlank { "Open to view this memory" }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("${memoryKind(memory)} · ${memory.createdAt.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                hit?.reasons?.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun MemoryDetail(memory: MemoryDto, serverUrl: String, busy: Boolean, onOpen: (MemoryDto) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { MemoryPreview(memory, serverUrl, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)), detail = true) }
            item {
                Text(memory.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("${memoryKind(memory)} · ${memory.createdAt.take(10)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (memory.labels.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    memory.labels.forEach { label ->
                        Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.background) {
                            Text(label.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Glyph(Symbol.DOCUMENT); Text("Extracted text", fontWeight = FontWeight.SemiBold) }
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(memory.text.ifBlank { "No text was extracted. Open the original file to view it." }, style = MaterialTheme.typography.bodyLarge) }
                        memory.metadata["warning"]?.let { Text("Processing warning: $it", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Button(onClick = { onOpen(memory) }, enabled = !busy && memory.contentUrl != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Glyph(Symbol.EXTERNAL); Spacer(Modifier.width(12.dp)); Text(if (memory.contentUrl == null) "No original file for this note" else "Open original", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun memorySymbol(memory: MemoryDto): Symbol = when {
    memory.mediaType.startsWith("image/") -> Symbol.IMAGE
    memory.mediaType.startsWith("audio/") -> Symbol.AUDIO
    memory.mediaType.startsWith("text/") -> Symbol.NOTE
    else -> Symbol.DOCUMENT
}

private fun memoryKind(memory: MemoryDto): String = when (memorySymbol(memory)) {
    Symbol.IMAGE -> "Image"
    Symbol.AUDIO -> "Audio"
    Symbol.NOTE -> "Note"
    else -> "Document"
}

private object ThumbnailCache {
    val images = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

@Composable
private fun MemoryPreview(memory: MemoryDto, serverUrl: String, modifier: Modifier, detail: Boolean = false) {
    val context = LocalContext.current
    val localAsset = memory.thumbnailUrl?.takeIf { it.startsWith("asset://") }
    val url = localAsset ?: if (memory.mediaType.startsWith("image/")) memory.thumbnailUrl?.let { path ->
        serverUrl.removeSuffix("v1/").trimEnd('/') + path
    } else null
    val bitmap by produceState<Bitmap?>(initialValue = url?.let(ThumbnailCache.images::get), key1 = url) {
        value = null
        if (url != null) value = ThumbnailCache.images.get(url) ?: withContext(Dispatchers.IO) {
            if (url.startsWith("asset://")) return@withContext try {
                context.assets.open(url.removePrefix("asset://")).use { BitmapFactory.decodeStream(it) }
            } catch (_: java.io.IOException) { null }
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                if (connection.responseCode == 200) connection.inputStream.use { input -> BitmapFactory.decodeStream(input) }
                else null
            } catch (_: java.io.IOException) { null }
            finally { connection?.disconnect() }
        }?.also { ThumbnailCache.images.put(url, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "Preview of ${memory.title}",
            modifier = Modifier.fillMaxSize(), contentScale = if (detail) ContentScale.Fit else ContentScale.Crop)
        else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Glyph(memorySymbol(memory), modifier = Modifier.size(if (detail) 36.dp else 28.dp))
            Text(memoryKind(memory).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(memory.text.ifBlank { memory.title }, maxLines = if (detail) 6 else 3, overflow = TextOverflow.Ellipsis,
                style = if (detail) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
