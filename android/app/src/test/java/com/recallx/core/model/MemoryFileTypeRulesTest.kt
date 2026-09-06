package com.recallx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryFileTypeRulesTest {
    @Test fun mapsSupportedBackendTypes() {
        assertEquals("image", MemoryFileTypeRules.backendFileType("image/jpeg", "photo.jpg", PickerKind.IMAGE))
        assertEquals("pdf", MemoryFileTypeRules.backendFileType("application/pdf", "document.pdf", PickerKind.DOCUMENT))
        assertEquals("document", MemoryFileTypeRules.backendFileType("text/plain", "notes.txt", PickerKind.DOCUMENT))
        assertEquals("document", MemoryFileTypeRules.backendFileType("application/msword", "letter.doc", PickerKind.DOCUMENT))
    }

    @Test fun rejectsUnsupportedTypesAndEnforcesSizeLimit() {
        assertNull(MemoryFileTypeRules.backendFileType("video/mp4", "movie.mp4", PickerKind.DOCUMENT))
        assertEquals(20L * 1024L * 1024L, MemoryFileSelector.MAX_FILE_SIZE_BYTES)
    }
}
