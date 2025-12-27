package com.droidamp.player

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import com.droidamp.data.models.SkipSegment

/**
 * Handles automatic skipping of SponsorBlock segments during playback.
 * Note: This is implemented in Phase 4 when SponsorBlock is enabled on backend.
 */
class SkipHandler(
    private val player: ExoPlayer,
    private val segments: List<SkipSegment>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isActive = false

    // Track which segments we've already skipped to avoid loops
    private val skippedSegments = mutableSetOf<Int>()

    fun start() {
        if (segments.isEmpty()) return
        isActive = true
        scheduleNextCheck()
    }

    fun stop() {
        isActive = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun scheduleNextCheck() {
        if (!isActive) return

        val currentPos = player.currentPosition
        val nextSegment = findNextSegment(currentPos)

        if (nextSegment != null) {
            val (index, segment) = nextSegment
            val delay = (segment.startMs - currentPos).coerceAtLeast(0)

            handler.postDelayed({
                if (isActive && !skippedSegments.contains(index)) {
                    performSkip(index, segment)
                }
                scheduleNextCheck()
            }, delay.coerceAtMost(1000)) // Check at least every second
        } else {
            // No more segments, check again in 2 seconds
            handler.postDelayed({ scheduleNextCheck() }, 2000)
        }
    }

    private fun findNextSegment(currentPos: Long): Pair<Int, SkipSegment>? {
        return segments.withIndex()
            .filter { (index, seg) ->
                !skippedSegments.contains(index) &&
                    currentPos < seg.endMs &&
                    currentPos >= seg.startMs - 500 // Within 500ms of start
            }
            .minByOrNull { (_, seg) -> seg.startMs }
            ?.let { (index, seg) -> index to seg }
            ?: segments.withIndex()
                .filter { (index, seg) ->
                    !skippedSegments.contains(index) &&
                        currentPos < seg.startMs
                }
                .minByOrNull { (_, seg) -> seg.startMs }
                ?.let { (index, seg) -> index to seg }
    }

    private fun performSkip(index: Int, segment: SkipSegment) {
        val currentPos = player.currentPosition

        // Only skip if we're actually in the segment
        if (currentPos >= segment.startMs - 100 && currentPos < segment.endMs) {
            player.seekTo(segment.endMs)
            skippedSegments.add(index)
        }
    }
}


