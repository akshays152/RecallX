package com.recallx.core.network

import com.recallx.core.network.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface RecallXApi {
    @GET("memories") suspend fun listMemories(@Query("limit") limit: Int = 100): ListResponseDto
    @GET("memories/{id}") suspend fun getMemory(@Path("id") id: String): MemoryDto
    @Multipart @POST("memories/file") suspend fun ingestFile(
        @Part file: MultipartBody.Part,
        @Part("source_uri") sourceUri: RequestBody,
    ): IngestResponseDto
    @POST("memories/text") suspend fun ingestText(@Body request: TextIngestRequestDto): IngestResponseDto
    @POST("search") suspend fun search(@Body request: SearchRequestDto): SearchResponseDto
    @DELETE("memories/{id}") suspend fun deleteMemory(@Path("id") id: String)
}
