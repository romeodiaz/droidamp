package com.droidamp.data.repository

import com.droidamp.BuildConfig
import com.droidamp.data.api.ApiClient
import com.droidamp.data.models.NextTrackRequest
import com.droidamp.data.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrackRepository {
    private val api = ApiClient.api

    suspend fun search(query: String): Result<Track> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchLucky(query, BuildConfig.API_KEY)
            Result.success(Track.from(response, query))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh an expired track by re-running the original search query.
     */
    suspend fun refreshTrack(track: Track): Result<Track> = withContext(Dispatchers.IO) {
        search(track.originalQuery)
    }
    
    /**
     * Get the mix playlist for a video (fast, returns ~25 video IDs).
     */
    suspend fun getMixPlaylist(videoId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMixPlaylist(videoId, BuildConfig.API_KEY)
            Result.success(response.videoIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get track by video ID (single extraction).
     */
    suspend fun getTrackById(videoId: String): Result<Track> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTrackById(videoId, BuildConfig.API_KEY)
            Result.success(Track.from(response, "queue"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get the next recommended track based on YouTube's suggestions.
     * @param excludeIds Set of video IDs to exclude (already played)
     */
    suspend fun getNextTrack(videoId: String, excludeIds: Set<String> = emptySet()): Result<Track> = withContext(Dispatchers.IO) {
        try {
            val request = NextTrackRequest(
                videoId = videoId,
                exclude = if (excludeIds.isNotEmpty()) excludeIds.toList() else null
            )
            val response = api.getNextTrack(request, BuildConfig.API_KEY)
            Result.success(Track.from(response, "autoplay"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


