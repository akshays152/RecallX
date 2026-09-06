package com.recallx.core.network
import com.recallx.core.network.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
interface RecallXApi {
    @GET("memories") suspend fun listMemories(@Query("type") type: String? = null, @Query("page") page: Int = 1, @Query("page_size") pageSize: Int? = null, @Query("sort") sort: String = "created_at_desc"): List<MemoryRecordDto>
    @GET("memories/{id}") suspend fun getMemory(@Path("id") id: String): MemoryRecordDto
    @GET("memories/{id}/content") suspend fun getMemoryContent(@Path("id") id: String): Response<Unit>
    @GET("memories/{id}/status") suspend fun getMemoryStatus(@Path("id") id: String): MemoryStatusResponseDto
    @Multipart @POST("memories") suspend fun createMemory(@Part file: MultipartBody.Part, @Part("source") source: RequestBody, @Part("file_type") fileType: RequestBody? = null): IngestionResponseDto
    @DELETE("memories/{id}") suspend fun deleteMemory(@Path("id") id: String): Response<Unit>
    @GET("memories/{id}/related") suspend fun relatedMemories(@Path("id") id: String): List<MemorySearchResultDto>
    @POST("memories/search") suspend fun searchMemories(@Body request: SearchRequestDto): SearchResponseDto
    @Multipart @POST("memories/visual-search") suspend fun visualSearch(@Part image: MultipartBody.Part, @Part("question") question: RequestBody? = null): VisualSearchResponseDto
}
