package com.recallx.core.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
@Composable fun RecallXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB9A4FF), onPrimary = Color(0xFF211438),
        primaryContainer = Color(0xFF34294B), onPrimaryContainer = Color(0xFFE8DDFF),
        secondary = Color(0xFF8AD8C5), background = Color(0xFF101014),
        onBackground = Color(0xFFF3F1F7), surface = Color(0xFF191B22),
        onSurface = Color(0xFFF3F1F7), surfaceVariant = Color(0xFF22242D),
        onSurfaceVariant = Color(0xFFBFC0CE), outline = Color(0xFF484A59),
        outlineVariant = Color(0xFF30323D)
    ), content = content)
}
