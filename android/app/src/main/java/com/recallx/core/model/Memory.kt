package com.recallx.core.model
enum class MemoryFileType { SCREENSHOT, IMAGE, PDF, DOCUMENT }
enum class ProcessingStatus { PROCESSING, READY, FAILED }
data class Memory(val id: String, val title: String, val summary: String, val fileType: MemoryFileType, val createdAt: String, val source: String, val processingStatus: ProcessingStatus, val updatedAt: String?, val processingError: String?, val extractedText: String, val entities: Map<String, String>, val tags: List<String>, val thumbnailTheme: String, val embeddingReference: String?, val contentAvailable: Boolean, val contentUrl: String?, val thumbnailAvailable: Boolean, val thumbnailUrl: String?)
data class SearchResult(val memory: Memory, val score: Double?, val matchReason: String?, val rankingSource: String?)
data class SearchResponse(val query: String, val results: List<SearchResult>, val provider: String)
data class VisualSearchResponse(val queryId: String, val question: String?, val results: List<SearchResult>, val provider: String)
data class IngestionResult(val memory: Memory, val provider: String, val message: String)
data class MemoryStatus(val id: String, val processingStatus: ProcessingStatus, val updatedAt: String?, val processingError: String?, val contentAvailable: Boolean)
data class MemoryContent(val mediaType: String?, val bytes: ByteArray)
