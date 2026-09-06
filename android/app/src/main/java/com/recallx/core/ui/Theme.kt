package com.recallx.core.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
@Composable fun RecallXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB9A4FF), secondary = Color(0xFF8AD8C5), background = Color(0xFF101014)), content = content)
}
