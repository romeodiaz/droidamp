package com.droidamp.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackResponse(
    @SerialName("video_id") val videoId: String,
    @SerialName("stream_url") val streamUrl: String,
    @SerialName("expires_at") val expiresAt: Long,
    val title: String,
    val artist: String?,
    val thumbnail: String,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("skip_segments") val skipSegments: List<SkipSegment>,
    @SerialName("user_agent") val userAgent: String,
)

@Serializable
data class SkipSegment(
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val category: String,
)

@Serializable
data class NextTrackRequest(
    @SerialName("video_id") val videoId: String,
    val exclude: List<String>? = null,
)

@Serializable
data class MixPlaylistResponse(
    @SerialName("video_ids") val videoIds: List<String>,
    @SerialName("mix_id") val mixId: String,
)

// Local track model with additional state
data class Track(
    val videoId: String,
    val streamUrl: String,
    val expiresAt: Long,
    val title: String,
    val artist: String?,
    val thumbnail: String,
    val durationMs: Long,
    val skipSegments: List<SkipSegment>,
    val userAgent: String,
    val originalQuery: String,  // Store for refresh
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() / 1000 > expiresAt - 1800 // 30min buffer

    companion object {
        fun from(response: TrackResponse, query: String) = Track(
            videoId = response.videoId,
            streamUrl = response.streamUrl,
            expiresAt = response.expiresAt,
            title = response.title,
            artist = response.artist,
            thumbnail = response.thumbnail,
            durationMs = response.durationMs,
            skipSegments = response.skipSegments,
            userAgent = response.userAgent,
            originalQuery = query,
        )
    }
}


