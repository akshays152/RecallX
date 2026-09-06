package com.recallx.core.model

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

enum class PickerKind { IMAGE, DOCUMENT }

data class SelectedMemoryFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val backendFileType: String
)

sealed interface FileSelectionResult {
    data class Accepted(val file: SelectedMemoryFile) : FileSelectionResult
    data class Rejected(val message: String) : FileSelectionResult
}

object MemoryFileSelector {
    const val MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L

    fun resolve(resolver: ContentResolver, uri: Uri, pickerKind: PickerKind): FileSelectionResult {
        val mimeType = resolver.getType(uri)?.lowercase()?.substringBefore(';')
        val displayName = resolver.queryName(uri) ?: "Selected memory"
        val resolvedMimeType = mimeType ?: MemoryFileTypeRules.mimeTypeFromExtension(displayName)
        val backendType = MemoryFileTypeRules.backendFileType(resolvedMimeType, displayName, pickerKind)
            ?: return FileSelectionResult.Rejected("RecallX doesn't support this file type yet.")
        val size = resolver.querySize(uri)
        if (size != null && size > MAX_FILE_SIZE_BYTES) {
            return FileSelectionResult.Rejected("Choose a file smaller than 20 MB.")
        }
        if (!resolver.openInputStream(uri).use { it != null }) {
            return FileSelectionResult.Rejected("RecallX couldn't read that file. Please choose it again.")
        }
        return FileSelectionResult.Accepted(SelectedMemoryFile(uri, displayName, resolvedMimeType, size, backendType))
    }

}

object MemoryFileTypeRules {
    fun backendFileType(mimeType: String?, name: String, pickerKind: PickerKind): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mimeType?.startsWith("image/") == true && pickerKind == PickerKind.IMAGE -> "image"
            mimeType == "application/pdf" || extension == "pdf" -> "pdf"
            mimeType == "text/plain" || extension == "txt" -> "document"
            mimeType == "application/msword" || extension == "doc" -> "document"
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" -> "document"
            else -> null
        }
    }

    fun mimeTypeFromExtension(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

}

private fun ContentResolver.queryName(uri: Uri): String? = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
}

private fun ContentResolver.querySize(uri: Uri): Long? = query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor: Cursor ->
    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
}
