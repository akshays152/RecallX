package com.recallx.data.repository
import com.recallx.core.model.*
import java.io.File
interface RecallXRepository {
    suspend fun listMemories(): List<Memory>
    suspend fun getMemory(id: String): Memory
    suspend fun getMemoryStatus(id: String): MemoryStatus
    suspend fun createMemory(file: File, source: String, fileType: String? = null): IngestionResult
    suspend fun deleteMemory(id: String)
    suspend fun relatedMemories(id: String): List<SearchResult>
    suspend fun searchMemories(query: String): SearchResponse
    suspend fun visualSearch(file: File, question: String? = null): VisualSearchResponse
}
