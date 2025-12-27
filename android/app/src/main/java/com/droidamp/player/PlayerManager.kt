package com.droidamp.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.droidamp.data.models.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class PlayerManager(context: Context) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentUserAgent: String? = null

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING,
                    error = if (playbackState == Player.STATE_IDLE) "Playback stopped" else null,
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })
    }

    private var skipHandler: SkipHandler? = null

    // Progress ticker: update UI every 250ms while playing
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (exoPlayer.isPlaying) {
                _state.value = _state.value.copy(positionMs = exoPlayer.currentPosition)
            }
            progressHandler.postDelayed(this, 250) // Update every 250ms
        }
    }

    init {
        progressHandler.post(progressRunnable)
    }

    fun play(track: Track) {
        // Create data source factory with track-specific User-Agent
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(track.userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to "https://youtube.com"))

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(track.streamUrl))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()

        _state.value = _state.value.copy(
            currentTrack = track,
            durationMs = track.durationMs,
            positionMs = 0,
            isLoading = true,
            error = null,
        )

        // Setup skip handler for SponsorBlock segments (Phase 4)
        skipHandler?.stop()
        if (track.skipSegments.isNotEmpty()) {
            skipHandler = SkipHandler(exoPlayer, track.skipSegments).also { it.start() }
        }

        currentUserAgent = track.userAgent
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun release() {
        progressHandler.removeCallbacks(progressRunnable)
        skipHandler?.stop()
        exoPlayer.release()
    }
}

