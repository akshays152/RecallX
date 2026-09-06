package com.recallx.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.recallx.core.ui.HomeScreen
import com.recallx.core.ui.AddMemoryScreen
import com.recallx.core.ui.LibraryScreen
import com.recallx.core.ui.MemoryDetailScreen
import com.recallx.data.repository.RecallXRepository

private sealed class Destination(val route: String, val label: String, val icon: String) {
    data object Home : Destination("home", "Home", "⌂")
    data object Library : Destination("library", "Library", "▤")
    data object CameraSearch : Destination("camera-search", "Camera Search", "⌕")
    data object SearchResults : Destination("search-results", "Search Results", "")
    data object AddMemory : Destination("add-memory", "Add Memory", "")
    data object MemoryDetail : Destination("memory-detail/{id}", "Memory Detail", "")
    data object VisualSearchResults : Destination("visual-search-results", "Visual Search Results", "")
}

@Composable
fun RecallXNavHost(repository: RecallXRepository) {
    val navController = rememberNavController()
    val primary = listOf(Destination.Home, Destination.Library, Destination.CameraSearch)
    val currentEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = primary.any { it.route == currentEntry?.destination?.route }
    Scaffold(bottomBar = {
        if (showBottomBar) NavigationBar {
            primary.forEach { destination ->
                NavigationBarItem(selected = currentEntry?.destination?.route == destination.route, onClick = { navController.navigate(destination.route) { popUpTo(Destination.Home.route); launchSingleTop = true } }, icon = { Text(destination.icon) }, label = { Text(destination.label) })
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = Destination.Home.route, modifier = Modifier.padding(padding)) {
            composable(Destination.Home.route) { HomeScreen(repository, onSearch = { navController.navigate(Destination.SearchResults.route) }, onAddMemory = { navController.navigate(Destination.AddMemory.route) }, onCameraSearch = { navController.navigate(Destination.CameraSearch.route) }, onOpenMemory = { navController.navigate("memory-detail/$it") }, onOpenLibrary = { navController.navigate(Destination.Library.route) }) }
            composable(Destination.Library.route) { LibraryScreen(repository, onMemory = { navController.navigate("memory-detail/$it") }, onAddMemory = { navController.navigate(Destination.AddMemory.route) }) }
            composable(Destination.CameraSearch.route) { PlaceholderScreen("Camera Search", "Visual search is ready for the CameraX phase.", "Open visual results") { navController.navigate(Destination.VisualSearchResults.route) } }
            composable(Destination.SearchResults.route) { PlaceholderScreen("Search", "Advanced semantic search will be added in a later phase.", "Back") { navController.popBackStack() } }
            composable(Destination.AddMemory.route) { AddMemoryScreen(repository, onBack = { navController.popBackStack() }, onViewMemory = { navController.navigate("memory-detail/$it") }) }
            composable(Destination.MemoryDetail.route) { entry ->
                val memoryId = entry.arguments?.getString("id")
                if (memoryId == null) PlaceholderScreen("Memory unavailable", "This memory could not be opened.", "Back") { navController.popBackStack() }
                else MemoryDetailScreen(repository, memoryId, onBack = { navController.popBackStack() }, onDeleted = { navController.popBackStack() }, onOpenMemory = { navController.navigate("memory-detail/$it") })
            }
            composable(Destination.VisualSearchResults.route) { PlaceholderScreen("Visual Search Results", "Visual result cards will be added with CameraX.", "Back") { navController.popBackStack() } }
        }
    }
}

@Composable private fun PlaceholderScreen(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp)); Text(body); Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(action) }
    }
}
