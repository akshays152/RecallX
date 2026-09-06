package com.recallx.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.recallx.core.model.*
import com.recallx.core.network.RecallXApi
import com.recallx.core.network.dto.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File

class DefaultRecallXRepository(private val api: RecallXApi) : RecallXRepository {
    override suspend fun listMemories() = api.listMemories().map { it.toModel() }
    override suspend fun getMemory(id: String) = api.getMemory(id).toModel()
    override suspend fun getMemoryStatus(id: String) = api.getMemoryStatus(id).toModel()
    override suspend fun createMemory(file: File, source: String, fileType: String?): IngestionResult {
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
        return api.createMemory(part, source.toRequestBody("text/plain".toMediaType()), fileType?.toRequestBody("text/plain".toMediaType())).toModel()
    }
    override suspend fun deleteMemory(id: String) { check(api.deleteMemory(id).isSuccessful) { "Unable to delete memory" } }
    override suspend fun relatedMemories(id: String) = api.relatedMemories(id).map { it.toModel() }
    override suspend fun searchMemories(query: String) = api.searchMemories(SearchRequestDto(query)).toModel()
    override suspend fun visualSearch(file: File, question: String?) = api.visualSearch(MultipartBody.Part.createFormData("image", file.name, file.asRequestBody("image/*".toMediaType())), question?.toRequestBody("text/plain".toMediaType())).toModel()

    companion object {
        private const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/api/"
        fun create(baseUrl: String = System.getProperty("recallx.baseUrl") ?: EMULATOR_BASE_URL): DefaultRecallXRepository {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            val client = OkHttpClient.Builder().addInterceptor(logging).build()
            val api = Retrofit.Builder().baseUrl(baseUrl.ensureTrailingSlash()).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(RecallXApi::class.java)
            return DefaultRecallXRepository(api)
        }
        private fun String.ensureTrailingSlash() = if (endsWith('/')) this else "$this/"
    }
}

private fun MemoryRecordDto.toModel() = Memory(id, title, summary, runCatching { MemoryFileType.valueOf(fileType.uppercase()) }.getOrDefault(MemoryFileType.IMAGE), createdAt, source, runCatching { ProcessingStatus.valueOf(processingStatus.uppercase()) }.getOrDefault(ProcessingStatus.PROCESSING), updatedAt, processingError, extractedText, entities, tags, thumbnailTheme, embeddingReference, contentAvailable, contentUrl, thumbnailAvailable, thumbnailUrl)
private fun MemorySearchResultDto.toModel() = SearchResult(toMemoryDto().toModel(), score, matchReason, rankingSource)
private fun SearchResponseDto.toModel() = SearchResponse(query, results.map { it.toModel() }, provider)
private fun VisualSearchResponseDto.toModel() = VisualSearchResponse(queryId, question, results.map { it.toModel() }, provider)
private fun IngestionResponseDto.toModel() = IngestionResult(memory.toModel(), provider, message)
private fun MemoryStatusResponseDto.toModel() = MemoryStatus(id, runCatching { ProcessingStatus.valueOf(processingStatus.uppercase()) }.getOrDefault(ProcessingStatus.PROCESSING), updatedAt, processingError, contentAvailable)
