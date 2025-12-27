package com.droidamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidamp.ui.theme.AmpGreen
import kotlin.math.roundToInt

@Composable
fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentDurationMs by rememberUpdatedState(durationMs)

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var trackWidth by remember { mutableFloatStateOf(0f) }

    val actualPosition = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val displayPosition = if (isDragging) dragPosition else actualPosition
    val displayTimeMs = if (isDragging) (dragPosition * durationMs).toLong() else positionMs

    val thumbSize = 28.dp
    val thumbSizePx = with(LocalDensity.current) { thumbSize.toPx() }
    val trackHeight = 8.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onSizeChanged { trackWidth = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragPosition = (offset.x / trackWidth).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (currentDurationMs > 0) {
                                currentOnSeek((dragPosition * currentDurationMs).toLong())
                            }
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val delta = dragAmount / trackWidth
                            dragPosition = (dragPosition + delta).coerceIn(0f, 1f)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            )

            // Track progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayPosition)
                    .height(trackHeight)
                    .background(AmpGreen, RoundedCornerShape(4.dp))
            )

            // Thumb
            Box(
                modifier = Modifier
                    .offset {
                        val x = (displayPosition * (trackWidth - thumbSizePx)).roundToInt()
                        IntOffset(x, 0)
                    }
                    .size(thumbSize)
                    .background(AmpGreen, CircleShape)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(displayTimeMs),
                color = if (isDragging) AmpGreen else Color.Gray,
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


