package com.recallx.data.repository
import com.recallx.core.model.*
import java.io.File
import com.recallx.core.model.MemoryContent
interface RecallXRepository {
    suspend fun getMemories(type: String? = null, page: Int = 1, pageSize: Int = 20, sort: String = "created_at_desc"): List<Memory>
    suspend fun getMemory(id: String): Memory
    suspend fun getMemoryContent(id: String): MemoryContent
    suspend fun getMemoryStatus(id: String): MemoryStatus
    suspend fun createMemory(file: File, source: String, fileType: String? = null): IngestionResult
    suspend fun deleteMemory(id: String)
    suspend fun getRelatedMemories(id: String): List<SearchResult>
    suspend fun searchMemories(query: String): SearchResponse
    suspend fun visualSearch(file: File, question: String? = null): VisualSearchResponse
}

class RecallXApiException(val statusCode: Int, val safeMessage: String) : Exception(safeMessage)
class RecallXNetworkException : Exception("We couldn't reach RecallX. Check your connection and try again.")
