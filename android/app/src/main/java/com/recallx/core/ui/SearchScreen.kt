package com.recallx.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.recallx.core.model.SearchResult
import com.recallx.data.repository.RecallXRepository

private val searchExamples = listOf(
    "Find the hotel screenshot around ₹1,300",
    "Show my recent travel booking",
    "Find the document about my project",
    "Find the screenshot from McLeodganj"
)

@Composable
fun SearchScreen(repository: RecallXRepository, onBack: () -> Unit, onOpenMemory: (String) -> Unit) {
    val vm: SearchViewModel = viewModel(factory = repositoryFactory { SearchViewModel(repository) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { if (input != state.query) input = state.query }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Search your memories", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Ask RecallX what you remember, in your own words.", style = MaterialTheme.typography.bodyLarge) }
            item {
                OutlinedTextField(value = input, onValueChange = { input = it; vm.setQuery(it) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Try: Find the hotel screenshot around ₹1,300") }, label = { Text("Memory search") }, singleLine = false, minLines = 2, trailingIcon = { if (input.isNotEmpty()) TextButton(onClick = { input = ""; vm.clear() }) { Text("Clear") } }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.submit(input) }) )
            }
            item { Button(onClick = { vm.submit(input) }, enabled = input.isNotBlank() && state.phase != SearchPhase.SEARCHING, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { if (state.phase == SearchPhase.SEARCHING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Search") } }
            if (state.phase == SearchPhase.IDLE) item { ExampleQueries(onSelect = { input = it; vm.submit(it) }) }
            if (state.phase == SearchPhase.ERROR) item { SearchErrorBlock(state.error.orEmpty()) { vm.submit(input) } }
            if (state.phase == SearchPhase.EMPTY) item { SearchEmptyState() }
            if (state.phase == SearchPhase.SUCCESS) {
                item { Text("${state.results.size} matching memories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                items(state.results, key = { it.memory.id }) { result -> SearchResultCard(result) { onOpenMemory(result.memory.id) } }
            }
        }
    }
}

@Composable private fun ExampleQueries(onSelect: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Try an example", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { searchExamples.forEach { FilterChip(selected = false, onClick = { onSelect(it) }, label = { Text(it) }) } } } }
@Composable private fun SearchEmptyState() { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("No matching memories found.", style = MaterialTheme.typography.titleMedium); Text("Try different words, or ask about a date, place, person, or amount.") } } }
@Composable private fun SearchErrorBlock(message: String, retry: () -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(message, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = retry) { Text("Try again") } } } }
@Composable private fun SearchResultCard(result: SearchResult, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(result.memory.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); AssistChip(onClick = {}, enabled = false, label = { Text(result.memory.processingStatus.name.lowercase().replaceFirstChar { it.uppercase() }) }) }; Text(result.memory.summary); Text("${result.memory.fileType.name.lowercase().replaceFirstChar { it.uppercase() }} · ${result.memory.createdAt.displayDate()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (result.score != null) Text("Score: ${result.score}", style = MaterialTheme.typography.bodySmall); if (!result.matchReason.isNullOrBlank()) Text(result.matchReason.orEmpty(), style = MaterialTheme.typography.bodyMedium); if (result.memory.contentAvailable) Text("Preview available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } } }
