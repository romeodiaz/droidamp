package com.droidamp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

class PlayerViewModel(
    private val playerManager: PlayerManager,
    private val repository: TrackRepository = TrackRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
                }
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

    fun togglePlayPause() {
        playerManager.togglePlayPause()
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

