package com.recallx.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemoryRecordDto(
    val id: String, val title: String, val summary: String,
    @SerialName("file_type") val fileType: String, @SerialName("created_at") val createdAt: String,
    val source: String, @SerialName("processing_status") val processingStatus: String,
    @SerialName("updated_at") val updatedAt: String? = null, @SerialName("processing_error") val processingError: String? = null,
    @SerialName("extracted_text") val extractedText: String = "", val entities: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(), @SerialName("thumbnail_theme") val thumbnailTheme: String = "violet",
    @SerialName("embedding_reference") val embeddingReference: String? = null, @SerialName("content_available") val contentAvailable: Boolean = false,
    @SerialName("content_url") val contentUrl: String? = null, @SerialName("thumbnail_available") val thumbnailAvailable: Boolean = false,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class MemorySearchResultDto(
    val id: String, val title: String, val summary: String, @SerialName("file_type") val fileType: String,
    @SerialName("created_at") val createdAt: String, val source: String, @SerialName("processing_status") val processingStatus: String,
    @SerialName("updated_at") val updatedAt: String? = null, @SerialName("processing_error") val processingError: String? = null,
    @SerialName("extracted_text") val extractedText: String = "", val entities: Map<String, String> = emptyMap(), val tags: List<String> = emptyList(),
    @SerialName("thumbnail_theme") val thumbnailTheme: String = "violet", @SerialName("embedding_reference") val embeddingReference: String? = null,
    @SerialName("content_available") val contentAvailable: Boolean = false, @SerialName("content_url") val contentUrl: String? = null,
    @SerialName("thumbnail_available") val thumbnailAvailable: Boolean = false, @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val score: Double? = null, @SerialName("match_reason") val matchReason: String? = null, @SerialName("ranking_source") val rankingSource: String? = null
) {
    fun toMemoryDto() = MemoryRecordDto(id, title, summary, fileType, createdAt, source, processingStatus, updatedAt, processingError, extractedText, entities, tags, thumbnailTheme, embeddingReference, contentAvailable, contentUrl, thumbnailAvailable, thumbnailUrl)
}

@Serializable data class SearchRequestDto(val query: String)
@Serializable data class SearchResponseDto(val query: String, val results: List<MemorySearchResultDto>, val provider: String)
@Serializable data class VisualSearchResponseDto(@SerialName("query_id") val queryId: String, val question: String? = null, val results: List<MemorySearchResultDto>, val provider: String)
@Serializable data class IngestionResponseDto(val memory: MemoryRecordDto, val provider: String, val message: String)
@Serializable data class MemoryStatusResponseDto(val id: String, @SerialName("processing_status") val processingStatus: String, @SerialName("updated_at") val updatedAt: String? = null, @SerialName("processing_error") val processingError: String? = null, @SerialName("content_available") val contentAvailable: Boolean)
