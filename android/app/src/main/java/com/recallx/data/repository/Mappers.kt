package com.recallx.data.repository

import com.recallx.core.model.*
import com.recallx.core.network.dto.*

internal fun MemoryRecordDto.toDomain() = Memory(id, title, summary, runCatching { MemoryFileType.valueOf(fileType.uppercase()) }.getOrDefault(MemoryFileType.IMAGE), createdAt, source, runCatching { ProcessingStatus.valueOf(processingStatus.uppercase()) }.getOrDefault(ProcessingStatus.PROCESSING), updatedAt, processingError, extractedText, entities, tags, thumbnailTheme, embeddingReference, contentAvailable, contentUrl, thumbnailAvailable, thumbnailUrl)
internal fun MemorySearchResultDto.toDomain() = SearchResult(toMemoryDto().toDomain(), score, matchReason, rankingSource)
internal fun SearchResponseDto.toDomain() = SearchResponse(query, results.map { it.toDomain() }, provider)
internal fun VisualSearchResponseDto.toDomain() = VisualSearchResponse(queryId, question, results.map { it.toDomain() }, provider)
internal fun IngestionResponseDto.toDomain() = IngestionResult(memory.toDomain(), provider, message)
internal fun MemoryStatusResponseDto.toDomain() = MemoryStatus(id, runCatching { ProcessingStatus.valueOf(processingStatus.uppercase()) }.getOrDefault(ProcessingStatus.PROCESSING), updatedAt, processingError, contentAvailable)
