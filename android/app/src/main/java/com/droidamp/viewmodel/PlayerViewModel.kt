package com.droidamp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidamp.data.SearchHistoryManager
import com.droidamp.data.models.Track
import com.droidamp.data.repository.TrackRepository
import com.droidamp.player.PlayerManager
import com.droidamp.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val playerState: PlayerState = PlayerState(),
    val isLoadingNext: Boolean = false,
    val isPrefetching: Boolean = false,
    val hasPrevious: Boolean = false,
)

class PlayerViewModel(
    private val playerManager: PlayerManager,
    private val searchHistoryManager: SearchHistoryManager,
    private val repository: TrackRepository = TrackRepository(),
) : ViewModel() {
    
    val searchHistory: StateFlow<List<String>> = searchHistoryManager.history

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private var prefetchedTrack: Track? = null
    private var prefetchedForVideoId: String? = null
    private val playHistory = mutableListOf<Track>()
    private val playedVideoIds = mutableSetOf<String>()
    
    // Mix queue - fetched once, used for all skips
    private var mixQueue: List<String> = emptyList()
    private var mixQueueIndex = 0
    private var mixQueueSourceId: String? = null
    private var isFetchingQueue = false

    init {
        // Collect player state updates
        viewModelScope.launch {
            playerManager.state.collect { playerState ->
                _uiState.value = _uiState.value.copy(playerState = playerState)

                // Auto-refresh expired streams by re-running original search
                playerState.currentTrack?.let { track ->
                    if (track.isExpired) {
                        refreshCurrentTrack(track)
                    }
                    
                    // Track this video as played
                    playedVideoIds.add(track.videoId)
                    
                    // Fetch mix queue if we don't have one
                    if (mixQueue.isEmpty() && !isFetchingQueue) {
                        fetchMixQueue(track.videoId)
                    }
                    
                    // Refresh queue when running low (3 or fewer songs left)
                    val remainingInQueue = mixQueue.size - mixQueueIndex
                    if (remainingInQueue <= 3 && !isFetchingQueue) {
                        fetchMixQueue(track.videoId)
                    }
                    
                    // Prefetch next track from queue
                    if (prefetchedForVideoId != track.videoId && !_uiState.value.isPrefetching) {
                        prefetchNextFromQueue()
                    }
                }
                
                // Autoplay next track when current track ends
                if (playerState.playbackEnded && playerState.currentTrack != null) {
                    playNextTrackFromCache(playerState.currentTrack.videoId)
                }
            }
        }
    }
    
    private fun fetchMixQueue(videoId: String) {
        if (isFetchingQueue) return
        isFetchingQueue = true
        
        viewModelScope.launch {
            repository.getMixPlaylist(videoId)
                .onSuccess { newQueue ->
                    // Filter out already played and current video
                    val newSongs = newQueue.filter { it != videoId && it !in playedVideoIds }
                    
                    if (mixQueue.isEmpty()) {
                        // First fetch - use as new queue
                        mixQueue = newSongs
                        mixQueueIndex = 0
                    } else {
                        // Append new songs that aren't already in queue
                        val existingIds = mixQueue.toSet()
                        val songsToAdd = newSongs.filter { it !in existingIds }
                        mixQueue = mixQueue + songsToAdd
                    }
                    
                    mixQueueSourceId = videoId
                    isFetchingQueue = false
                    
                    // Start prefetching now that we have the queue
                    if (!_uiState.value.isPrefetching && prefetchedTrack == null) {
                        prefetchNextFromQueue()
                    }
                }
                .onFailure {
                    isFetchingQueue = false
                }
        }
    }
    
    private fun getNextVideoIdFromQueue(): String? {
        while (mixQueueIndex < mixQueue.size) {
            val videoId = mixQueue[mixQueueIndex]
            if (videoId !in playedVideoIds) {
                return videoId
            }
            mixQueueIndex++
        }
        return null
    }
    
    private fun prefetchNextFromQueue() {
        if (_uiState.value.isPrefetching) return
        
        val currentVideoId = _uiState.value.playerState.currentTrack?.videoId ?: return
        val nextVideoId = getNextVideoIdFromQueue() ?: return
        
        prefetchedForVideoId = currentVideoId
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrefetching = true)
            
            repository.getTrackById(nextVideoId)
                .onSuccess { track ->
                    prefetchedTrack = track
                    _uiState.value = _uiState.value.copy(isPrefetching = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isPrefetching = false)
                }
        }
    }
    
    private fun playNextTrackFromCache(currentVideoId: String) {
        // Save current track to history before moving to next
        _uiState.value.playerState.currentTrack?.let { current ->
            if (playHistory.lastOrNull()?.videoId != current.videoId) {
                playHistory.add(current)
                _uiState.value = _uiState.value.copy(hasPrevious = true)
            }
        }
        
        val cached = prefetchedTrack
        if (cached != null && prefetchedForVideoId == currentVideoId) {
            prefetchedTrack = null
            prefetchedForVideoId = null
            mixQueueIndex++ // Advance queue since we used the prefetched track
            playerManager.play(cached)
        } else {
            playNextTrackFromQueue()
        }
    }
    
    fun skipToPrevious() {
        if (playHistory.isEmpty()) return
        
        val previousTrack = playHistory.removeAt(playHistory.lastIndex)
        _uiState.value = _uiState.value.copy(hasPrevious = playHistory.isNotEmpty())
        playerManager.play(previousTrack)
    }
    
    private fun playNextTrackFromQueue() {
        if (_uiState.value.isLoadingNext) return
        
        val nextVideoId = getNextVideoIdFromQueue()
        if (nextVideoId == null) {
            _uiState.value = _uiState.value.copy(
                searchError = "End of queue. Search for a new song!"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingNext = true)
            
            repository.getTrackById(nextVideoId)
                .onSuccess { track ->
                    mixQueueIndex++
                    _uiState.value = _uiState.value.copy(isLoadingNext = false)
                    playerManager.play(track)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingNext = false,
                        searchError = "Could not load next track: ${error.message}"
                    )
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)
            searchHistoryManager.addSearch(query)
            
            // Reset queue for new search
            mixQueue = emptyList()
            mixQueueIndex = 0
            mixQueueSourceId = null
            isFetchingQueue = false
            prefetchedTrack = null
            prefetchedForVideoId = null

            repository.search(query)
                .onSuccess { track ->
                    _uiState.value = _uiState.value.copy(isSearching = false)
                    playerManager.play(track)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchError = error.message ?: "Search failed"
                    )
                }
        }
    }
    
    fun removeFromHistory(query: String) {
        searchHistoryManager.removeSearch(query)
    }
    
    fun clearSearchHistory() {
        searchHistoryManager.clearHistory()
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }
    
    fun skipToNext() {
        val currentTrack = _uiState.value.playerState.currentTrack ?: return
        playNextTrackFromCache(currentTrack.videoId)
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    private fun refreshCurrentTrack(track: Track) {
        viewModelScope.launch {
            repository.refreshTrack(track)
                .onSuccess { refreshedTrack ->
                    playerManager.play(refreshedTrack)
                }
                .onFailure {
                    // Stream expired and refresh failed - notify user
                    _uiState.value = _uiState.value.copy(
                        searchError = "Stream expired. Please search again."
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(searchError = null)
    }
}


