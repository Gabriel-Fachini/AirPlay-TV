package com.airplay.tv.media

import com.airplay.tv.util.Logger

class AudioPerformanceTracker {
    var samplesDecoded = 0L
        private set
    var samplesDropped = 0L
        private set
    var lastRenderedPresentationTimeUs = 0L
        private set
    var hasSynchronizedClock = false
        private set
    var inputFramesQueued = 0L
        private set
    var firstRenderedBufferLogged = false
        private set

    fun onFrameQueued(size: Int, rtpTimestamp: Long, presentationTimeUs: Long, clockLocked: Boolean) {
        inputFramesQueued++
        if (inputFramesQueued <= 3L) {
            Logger.i(Logger.TAG_AUDIO, "In#$inputFramesQueued size=$size ts=$rtpTimestamp pts=$presentationTimeUs lock=$clockLocked")
        }
    }

    fun onFrameDropped() {
        samplesDropped++
    }

    fun onFrameSubmittedToCodec(presentationTimeUs: Long, clockLocked: Boolean) {
        if (!hasSynchronizedClock && clockLocked) {
            Logger.i(Logger.TAG_AUDIO, "Clock lock pts=$presentationTimeUs")
        }
        hasSynchronizedClock = hasSynchronizedClock || clockLocked
    }

    fun onSamplesDecoded(written: Int, presentationTimeUs: Long) {
        samplesDecoded += (written / 2).toLong() // 16-bit samples
        lastRenderedPresentationTimeUs = presentationTimeUs
        if (!firstRenderedBufferLogged) {
            firstRenderedBufferLogged = true
            Logger.i(Logger.TAG_AUDIO, "First PCM bytes=$written pts=$presentationTimeUs dec=$samplesDecoded")
        }
    }

    fun resetMetrics() {
        samplesDecoded = 0
        samplesDropped = 0
        lastRenderedPresentationTimeUs = 0L
        hasSynchronizedClock = false
        inputFramesQueued = 0L
        firstRenderedBufferLogged = false
    }
}
