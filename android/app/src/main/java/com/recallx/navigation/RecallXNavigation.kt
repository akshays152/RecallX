package com.recallx.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.recallx.core.model.Memory
import com.recallx.data.repository.RecallXRepository
import kotlinx.coroutines.launch

private sealed class Destination(val route: String, val label: String, val icon: String) {
    data object Home : Destination("home", "Home", "⌂")
    data object Library : Destination("library", "Library", "▤")
    data object CameraSearch : Destination("camera-search", "Camera Search", "⌕")
    data object SearchResults : Destination("search-results", "Search Results", "")
    data object AddMemory : Destination("add-memory", "Add Memory", "")
    data object MemoryDetail : Destination("memory-detail/{id}", "Memory Detail", "")
    data object VisualSearchResults : Destination("visual-search-results", "Visual Search Results", "")
}

class HomeViewModel(private val repository: RecallXRepository) : ViewModel() {
    var memories by mutableStateOf<List<Memory>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    fun load() { viewModelScope.launch { loading = true; runCatching { repository.listMemories() }.onSuccess { memories = it }; loading = false } }
}

@Composable
fun RecallXNavHost(repository: RecallXRepository) {
    val navController = rememberNavController()
    val primary = listOf(Destination.Home, Destination.Library, Destination.CameraSearch)
    val currentRoute by navController.currentBackStackEntryAsState()
    val showBottomBar = primary.any { it.route == currentRoute?.destination?.route }
    Scaffold(bottomBar = {
        if (showBottomBar) {
            NavigationBar {
                primary.forEach { destination ->
                    NavigationBarItem(selected = currentRoute?.destination?.route == destination.route, onClick = { navController.navigate(destination.route) { popUpTo(Destination.Home.route); launchSingleTop = true } }, icon = { Text(destination.icon) }, label = { Text(destination.label) })
                }
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = Destination.Home.route, modifier = Modifier.padding(padding)) {
            composable(Destination.Home.route) { PlaceholderScreen("Welcome back", "Your memories will appear here.", "Add Memory") { navController.navigate(Destination.AddMemory.route) } }
            composable(Destination.Library.route) { LibraryScreen(repository, onMemory = { navController.navigate("memory-detail/$it") }) }
            composable(Destination.CameraSearch.route) { PlaceholderScreen("Camera Search", "Visual search foundation is ready for the next phase.", "Open visual results") { navController.navigate(Destination.VisualSearchResults.route) } }
            composable(Destination.SearchResults.route) { PlaceholderScreen("Search Results", "Text search results will be rendered here.", "Back") { navController.popBackStack() } }
            composable(Destination.AddMemory.route) { PlaceholderScreen("Add Memory", "File picking and upload UI arrive in the next phase.", "Back") { navController.popBackStack() } }
            composable(Destination.MemoryDetail.route) { PlaceholderScreen("Memory Detail", "Memory detail UI is reserved for the next phase.", "Back") { navController.popBackStack() } }
            composable(Destination.VisualSearchResults.route) { PlaceholderScreen("Visual Search Results", "Visual result cards will be added in the next phase.", "Back") { navController.popBackStack() } }
        }
    }
}

@Composable private fun LibraryScreen(repository: RecallXRepository, onMemory: (String) -> Unit) {
    val vm: HomeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T })
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Library", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(16.dp)); if (vm.loading) CircularProgressIndicator() else vm.memories.forEach { memory -> TextButton(onClick = { onMemory(memory.id) }) { Text(memory.title) } } }
}

@Composable private fun PlaceholderScreen(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text(title, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp)); Text(body); Spacer(Modifier.height(24.dp)); Button(onClick = onAction) { Text(action) } }
}
