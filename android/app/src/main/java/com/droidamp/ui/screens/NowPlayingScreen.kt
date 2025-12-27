package com.droidamp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.droidamp.ui.components.PlayerControls
import com.droidamp.ui.components.ProgressBar
import com.droidamp.ui.theme.AmpDark
import com.droidamp.ui.theme.AmpGray
import com.droidamp.ui.theme.AmpGreen
import com.droidamp.viewmodel.PlayerViewModel

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.playerState.currentTrack

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmpDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Search button at top
        OutlinedButton(
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AmpGreen
            )
        ) {
            Text("Search", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Album art
        if (track != null) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmpGray),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No Track",
                    color = Color.Gray,
                    fontSize = 20.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track info
        Text(
            text = track?.title ?: "—",
            color = AmpGreen,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track?.artist ?: "",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Progress bar
        ProgressBar(
            positionMs = uiState.playerState.positionMs,
            durationMs = uiState.playerState.durationMs,
            onSeek = { viewModel.seekTo(it) },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Player controls (large, driver-friendly)
        PlayerControls(
            isPlaying = uiState.playerState.isPlaying,
            isLoading = uiState.playerState.isLoading,
            onPlayPause = { viewModel.togglePlayPause() },
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Error display
        uiState.searchError?.let { error ->
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}


