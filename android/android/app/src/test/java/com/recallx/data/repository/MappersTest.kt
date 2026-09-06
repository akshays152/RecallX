package com.recallx.data.repository

import com.recallx.core.model.MemoryFileType
import com.recallx.core.model.ProcessingStatus
import com.recallx.core.network.dto.MemoryRecordDto
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test fun mapsBackendSnakeCaseFieldsIntoDomainModel() {
        val dto = MemoryRecordDto("id", "Receipt", "Saved receipt", "pdf", "2026-09-07T10:15:30Z", "Scanner", "ready", extractedText = "Total ₹500", tags = listOf("shopping"), contentAvailable = true)
        val memory = dto.toDomain()
        assertEquals(MemoryFileType.PDF, memory.fileType)
        assertEquals(ProcessingStatus.READY, memory.processingStatus)
        assertEquals("Total ₹500", memory.extractedText)
        assertEquals(true, memory.contentAvailable)
    }
}
