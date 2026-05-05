package com.airplay.tv.media

import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import java.util.concurrent.LinkedBlockingQueue

class VideoInputQueue {
    var bufferSize = Constants.JITTER_BUFFER_FRAMES * 6
        private set

    private var inputQueue = LinkedBlockingQueue<VideoDecoder.H264Frame>(bufferSize)

    fun clear() {
        inputQueue.clear()
    }

    fun peek(): VideoDecoder.H264Frame? = inputQueue.peek()

    fun adjustBufferSize(increase: Boolean) {
        val oldSize = bufferSize
        if (increase) {
            bufferSize = minOf(bufferSize + 2, 14)
        } else {
            bufferSize = maxOf(bufferSize - 2, 6)
        }

        if (bufferSize != oldSize) {
            Logger.i(Logger.TAG_VIDEO, "Buffer size adjusted: $oldSize → $bufferSize frames")
            val oldQueue = inputQueue
            inputQueue = LinkedBlockingQueue(bufferSize)
            oldQueue.drainTo(inputQueue)
        }
    }

    fun queueFrame(
        frame: VideoDecoder.H264Frame,
        submittedInputFrames: Long,
        onFramesDropped: (VideoPerformanceTracker.LocalDropReason, Long) -> Unit
    ) {
        if (inputQueue.offer(frame)) {
            return
        }

        if (frame.isKeyFrame) {
            val evictedFrames = inputQueue.size
            inputQueue.clear()
            onFramesDropped(VideoPerformanceTracker.LocalDropReason.STALE_QUEUE_EVICTION, evictedFrames.toLong())
            if (evictedFrames > 0) {
                Logger.i(
                    Logger.TAG_VIDEO,
                    "Dropped $evictedFrames stale queued frames to preserve latest keyframe"
                )
            }
            if (inputQueue.offer(frame)) {
                return
            }
        } else {
            val evictedFrame = if (submittedInputFrames == 0L && dropOldestQueuedNonKeyFrame()) {
                true
            } else {
                inputQueue.poll()
                true
            }
            if (evictedFrame) {
                onFramesDropped(VideoPerformanceTracker.LocalDropReason.QUEUE_OVERFLOW, 1)
            }
            if (inputQueue.offer(frame)) {
                return
            }
        }

        onFramesDropped(VideoPerformanceTracker.LocalDropReason.QUEUE_OVERFLOW, 1)
        Logger.w(Logger.TAG_VIDEO, "Input queue full, dropping frames")
    }

    fun pollFrameForCodec(
        submittedInputFrames: Long,
        onFramesDropped: (Long) -> Unit
    ): VideoDecoder.H264Frame? {
        var frame = inputQueue.poll() ?: return null

        while (submittedInputFrames == 0L && !frame.isKeyFrame && !containsCodecConfig(frame.data)) {
            onFramesDropped(1)
            Logger.w(Logger.TAG_VIDEO, "Dropping pre-start non-keyframe while waiting for an IDR")
            frame = inputQueue.poll() ?: return null
        }
        return frame
    }

    private fun dropOldestQueuedNonKeyFrame(): Boolean {
        for (queuedFrame in inputQueue) {
            if (!queuedFrame.isKeyFrame) {
                return inputQueue.remove(queuedFrame)
            }
        }
        return false
    }

    private fun containsCodecConfig(data: ByteArray): Boolean {
        return containsNalType(data, 7) || containsNalType(data, 8)
    }
}
