package com.eetu.youtubeapp.data.remote

import com.eetu.youtubeapp.data.model.VideoSegmentsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface SponsorBlockApiService {
    @Streaming
    @GET("mchangrh/sb.js/main/docs/sb.user.js")
    suspend fun downloadLatestScript(): Response<ResponseBody>

    @GET("api/skipSegments/{prefix}")
    suspend fun getSkipSegments(
        @Path("prefix") prefix: String,
        @Query("category") categories: List<String>,
        @Query("actionType") actionTypes: List<String>,
        @Query("service") service: String = "YouTube",
        @Query("userID") userID: String
    ): Response<List<VideoSegmentsResponse>>
}
