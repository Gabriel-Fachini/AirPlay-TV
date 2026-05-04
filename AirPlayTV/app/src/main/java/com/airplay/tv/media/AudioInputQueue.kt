package com.airplay.tv.media

import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import java.util.concurrent.LinkedBlockingQueue

class AudioInputQueue {
    private val inputQueue = LinkedBlockingQueue<AudioDecoder.AACFrame>(Constants.JITTER_BUFFER_FRAMES * 2)

    fun getRemainingCapacity(): Int = inputQueue.remainingCapacity()
    fun getSize(): Int = inputQueue.size

    fun clear() {
        inputQueue.clear()
    }

    fun queueFrame(
        data: ByteArray, 
        rtpTimestamp: Long, 
        presentationTimeUs: Long, 
        clockLocked: Boolean,
        onFrameDropped: () -> Unit
    ) {
        val frame = AudioDecoder.AACFrame(data, rtpTimestamp, presentationTimeUs, clockLocked)
        if (!inputQueue.offer(frame)) {
            onFrameDropped()
            Logger.w(Logger.TAG_AUDIO, "Input queue full, dropping frame")
        }
    }

    fun peek(): AudioDecoder.AACFrame? = inputQueue.peek()
    fun poll(): AudioDecoder.AACFrame? = inputQueue.poll()
}
