package com.droidamp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.droidamp.ui.theme.AmpGreen

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    isLoadingNext: Boolean,
    hasTrack: Boolean,
    hasPrevious: Boolean,
    onSkipPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Skip previous button
        FilledIconButton(
            onClick = onSkipPrevious,
            modifier = Modifier.size(64.dp),
            enabled = hasPrevious,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = AmpGreen,
                contentColor = Color.Black,
                disabledContainerColor = Color.DarkGray,
                disabledContentColor = Color.Gray,
            )
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Skip to previous",
                modifier = Modifier.size(36.dp),
            )
        }
        
        Spacer(modifier = Modifier.width(24.dp))
        
        // Large play/pause button for driving-friendly UI
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(80.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = AmpGreen,
                contentColor = Color.Black,
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color.Black,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        
        Spacer(modifier = Modifier.width(24.dp))
        
        // Skip next button
        FilledIconButton(
            onClick = onSkipNext,
            modifier = Modifier.size(64.dp),
            enabled = hasTrack && !isLoadingNext,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = AmpGreen,
                contentColor = Color.Black,
                disabledContainerColor = Color.DarkGray,
                disabledContentColor = Color.Gray,
            )
        ) {
            if (isLoadingNext) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.Black,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip to next",
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

