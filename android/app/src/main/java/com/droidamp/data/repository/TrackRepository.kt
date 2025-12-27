package com.droidamp.data.repository

import com.droidamp.BuildConfig
import com.droidamp.data.api.ApiClient
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
     * This is simpler than a dedicated refresh endpoint and works well
     * since the backend caches by query.
     */
    suspend fun refreshTrack(track: Track): Result<Track> = withContext(Dispatchers.IO) {
        search(track.originalQuery)
    }
}


