package com.droidamp.data.api

import com.droidamp.data.models.TrackResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DroidAmpApi {
    @GET("/search/lucky")
    suspend fun searchLucky(
        @Query("q") query: String,
        @Header("X-API-Key") apiKey: String,
    ): TrackResponse
}


