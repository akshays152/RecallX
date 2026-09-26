package com.recallx.data.repository

import com.recallx.core.network.dto.MemoryDto
import com.recallx.core.network.dto.SearchHitDto
import java.io.File

interface RecallXRepository {
    val baseUrl: String
    suspend fun listMemories(): List<MemoryDto>
    suspend fun ingestFile(file: File, originalName: String, sourceUri: String, mediaType: String): MemoryDto
    suspend fun ingestText(text: String, title: String): MemoryDto
    suspend fun search(query: String): List<SearchHitDto>
    suspend fun deleteMemory(id: String)
    fun contentUrl(memory: MemoryDto): String?
}
