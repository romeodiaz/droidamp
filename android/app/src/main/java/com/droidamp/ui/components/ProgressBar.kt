package com.droidamp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidamp.ui.theme.AmpGreen

@Composable
fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableFloatStateOf(-1f) }

    val displayPosition = if (isDragging >= 0f) isDragging else {
        if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayPosition,
            onValueChange = { newValue ->
                isDragging = newValue
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                if (durationMs > 0 && isDragging >= 0f) {
                    onSeek((sliderPosition * durationMs).toLong())
                }
                isDragging = -1f
            },
            colors = SliderDefaults.colors(
                thumbColor = AmpGreen,
                activeTrackColor = AmpGreen,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(positionMs),
                color = Color.Gray,
                fontSize = 14.sp,
            )
            Text(
                text = formatTime(durationMs),
                color = Color.Gray,
                fontSize = 14.sp,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

