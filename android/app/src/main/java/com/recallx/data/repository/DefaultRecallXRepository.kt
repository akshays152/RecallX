package com.recallx.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.recallx.core.model.*
import com.recallx.core.network.RecallXApi
import com.recallx.core.network.ContentUriRequestBody
import com.recallx.core.network.dto.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultRecallXRepository(private val api: RecallXApi) : RecallXRepository {
    override suspend fun getMemories(type: String?, page: Int, pageSize: Int, sort: String) = safeCall { api.listMemories(type, page, pageSize, sort) }.map { it.toDomain() }
    override suspend fun getMemory(id: String) = safeCall { api.getMemory(id) }.toDomain()
    override suspend fun getMemoryContent(id: String): MemoryContent = withContext(Dispatchers.IO) {
        val response = safeCall { api.getMemoryContent(id) }
        val body = response.body() ?: throw RecallXApiException(response.code(), "This memory's original content is unavailable.")
        MemoryContent(body.contentType()?.toString(), body.bytes())
    }
    override suspend fun getMemoryStatus(id: String) = safeCall { api.getMemoryStatus(id) }.toDomain()
    override suspend fun createMemory(file: File, source: String, fileType: String?): IngestionResult {
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
        return safeCall { api.createMemory(part, source.toRequestBody("text/plain".toMediaType()), fileType?.toRequestBody("text/plain".toMediaType())) }.toDomain()
    }
    override suspend fun deleteMemory(id: String) { safeCall { api.deleteMemory(id) } }
    override suspend fun getRelatedMemories(id: String) = safeCall { api.relatedMemories(id) }.map { it.toDomain() }
    override suspend fun uploadMemory(file: SelectedMemoryFile, source: String, contentResolver: android.content.ContentResolver): IngestionResult {
        val body = ContentUriRequestBody(contentResolver, file.uri, file.mimeType, file.sizeBytes)
        val part = MultipartBody.Part.createFormData("file", file.displayName, body)
        return safeCall { api.createMemory(part, source.toRequestBody("text/plain".toMediaType()), file.backendFileType.toRequestBody("text/plain".toMediaType())) }.toDomain()
    }
    override suspend fun searchMemories(query: String) = safeCall { api.searchMemories(SearchRequestDto(query)) }.toDomain()
    override suspend fun visualSearch(file: File, question: String?) = safeCall { api.visualSearch(MultipartBody.Part.createFormData("image", file.name, file.asRequestBody("image/*".toMediaType())), question?.toRequestBody("text/plain".toMediaType())) }.toDomain()

    companion object {
        private const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/api/"
        fun create(baseUrl: String = System.getProperty("recallx.baseUrl") ?: EMULATOR_BASE_URL): DefaultRecallXRepository {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val client = OkHttpClient.Builder().build()
            val api = Retrofit.Builder().baseUrl(baseUrl.ensureTrailingSlash()).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(RecallXApi::class.java)
            return DefaultRecallXRepository(api)
        }
        private fun String.ensureTrailingSlash() = if (endsWith('/')) this else "$this/"
    }
}

private suspend fun <T> safeCall(block: suspend () -> T): T = try {
    block()
} catch (error: retrofit2.HttpException) {
    throw RecallXApiException(error.code(), when (error.code()) {
        404 -> "That memory is no longer available."
        413 -> "The selected file is too large."
        415 -> "This file type is not supported."
        else -> "RecallX couldn't complete that request. Please try again."
    })
} catch (error: java.io.IOException) {
    throw RecallXNetworkException()
}

private suspend fun <T> safeCall(block: suspend () -> retrofit2.Response<T>): retrofit2.Response<T> = try {
    val response = block()
    if (!response.isSuccessful) throw RecallXApiException(response.code(), when (response.code()) {
        404 -> "That memory is no longer available."
        else -> "RecallX couldn't complete that request. Please try again."
    })
    response
} catch (error: RecallXApiException) { throw error } catch (error: java.io.IOException) { throw RecallXNetworkException() }
