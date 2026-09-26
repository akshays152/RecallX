package com.recallx.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.recallx.core.network.RecallXApi
import com.recallx.core.network.dto.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

class DefaultRecallXRepository private constructor(
    private val api: RecallXApi,
    override val baseUrl: String,
) : RecallXRepository {
    override suspend fun listMemories(): List<MemoryDto> = api.listMemories().memories

    override suspend fun ingestFile(file: File, originalName: String, sourceUri: String, mediaType: String): MemoryDto {
        val part = MultipartBody.Part.createFormData(
            "file", originalName, file.asRequestBody(mediaType.toMediaType())
        )
        return api.ingestFile(part, sourceUri.toRequestBody("text/plain".toMediaType())).memory
    }

    override suspend fun ingestText(text: String, title: String): MemoryDto =
        api.ingestText(TextIngestRequestDto(text, title, "manual://android/${System.currentTimeMillis()}")).memory

    override suspend fun search(query: String): List<SearchHitDto> = api.search(SearchRequestDto(query)).results

    override suspend fun deleteMemory(id: String) = api.deleteMemory(id)

    override fun contentUrl(memory: MemoryDto): String? = memory.contentUrl?.let {
        baseUrl.removeSuffix("v1/").trimEnd('/') + it
    }

    companion object {
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/v1/"

        fun create(baseUrl: String = EMULATOR_BASE_URL): DefaultRecallXRepository {
            val raw = baseUrl.trim().trimEnd('/')
            val normalized = (if (raw.endsWith("/v1")) raw else "$raw/v1") + "/"
            require(normalized.startsWith("http://") || normalized.startsWith("https://"))
            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
            val api = Retrofit.Builder().baseUrl(normalized).client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(RecallXApi::class.java)
            return DefaultRecallXRepository(api, normalized)
        }
    }
}
