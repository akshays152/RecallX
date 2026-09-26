package com.recallx.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MemoryDto(
    val id: String,
    @SerialName("source_uri") val sourceUri: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    val text: String,
    @SerialName("created_at") val createdAt: String,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val labels: List<String> = emptyList(),
    @SerialName("content_url") val contentUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

@Serializable data class ListResponseDto(val count: Int, val total: Int, val memories: List<MemoryDto>)
@Serializable data class IngestResponseDto(val created: Boolean, val memory: MemoryDto)
@Serializable data class TextIngestRequestDto(val text: String, val title: String, @SerialName("source_uri") val sourceUri: String)
@Serializable data class SearchRequestDto(val query: String, val limit: Int = 20)
@Serializable data class SearchHitDto(
    val memory: MemoryDto,
    val score: Double,
    val reasons: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
)
@Serializable data class SearchResponseDto(val query: String, val count: Int, val results: List<SearchHitDto>)
