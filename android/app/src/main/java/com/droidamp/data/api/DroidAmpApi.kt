package com.droidamp.data.api

import com.droidamp.data.models.MixPlaylistResponse
import com.droidamp.data.models.NextTrackRequest
import com.droidamp.data.models.TrackResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface DroidAmpApi {
    @GET("/search/lucky")
    suspend fun searchLucky(
        @Query("q") query: String,
        @Header("X-API-Key") apiKey: String,
    ): TrackResponse
    
    @GET("/search/mix")
    suspend fun getMixPlaylist(
        @Query("video_id") videoId: String,
        @Header("X-API-Key") apiKey: String,
    ): MixPlaylistResponse
    
    @GET("/search/track")
    suspend fun getTrackById(
        @Query("video_id") videoId: String,
        @Header("X-API-Key") apiKey: String,
    ): TrackResponse
    
    @POST("/search/next")
    suspend fun getNextTrack(
        @Body request: NextTrackRequest,
        @Header("X-API-Key") apiKey: String,
    ): TrackResponse
}


