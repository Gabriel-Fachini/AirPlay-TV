package com.airplay.tv.media

import com.airplay.tv.util.Logger
import com.airplay.tv.util.TelemetryCollector

class VideoPerformanceTracker(
    private val telemetryCollector: TelemetryCollector,
    private val clock: MonotonicClock = MonotonicClock.system()
) {
    fun interface MonotonicClock {
        fun nowNanos(): Long

        companion object {
            fun system(): MonotonicClock = MonotonicClock { System.nanoTime() }
        }
    }

    enum class LocalDropReason {
        INVALID_FRAME,
        STALE_QUEUE_EVICTION,
        QUEUE_OVERFLOW,
        PRE_START_NON_KEYFRAME,
        CODEC_INPUT_REJECTED,
    }

    var onAdjustBufferSize: ((increase: Boolean) -> Unit)? = null

    var framesDecoded = 0L
        private set
    var droppedLocalFrames = 0L
        private set
    var currentFps = 0
        private set
    var submittedInputFrames = 0L
        private set
    var lastRenderedPresentationTimeUs = 0L
        private set

    private val droppedFramesByReason = enumValues<LocalDropReason>().associateWith { 0L }.toMutableMap()
    private var lastFpsTimeNs = clock.nowNanos()
    private var fpsCounter = 0
    private var lastLocalLatencyMs = 0
    private var bytesSubmittedSinceLastBitrateSample = 0L
    private var lastBitrateSampleTimeNs = clock.nowNanos()

    private var lowFpsCounter = 0
    private var highDropRateCounter = 0
    private val performanceCheckIntervalNs = 1_000_000_000L
    private var lastPerformanceCheckNs = clock.nowNanos()

    fun onSessionStarted() {
        resetMetrics()
    }

    fun onSessionStopped() {
        lastRenderedPresentationTimeUs = 0L
    }

    fun captureFrameReceivedAtNs(): Long = clock.nowNanos()

    fun onFrameSubmittedToCodec(size: Int, isKeyFrame: Boolean) {
        submittedInputFrames++
        bytesSubmittedSinceLastBitrateSample += size.toLong()
        updateBitrateIfNeeded()
        if (submittedInputFrames <= 3L) {
            Logger.i(Logger.TAG_VIDEO, "Queued codec input #$submittedInputFrames ($size bytes, keyFrame=$isKeyFrame)")
        }
    }

    fun onFramesDropped(reason: LocalDropReason, count: Long = 1) {
        if (count <= 0L) {
            return
        }
        droppedLocalFrames += count
        droppedFramesByReason[reason] = (droppedFramesByReason[reason] ?: 0L) + count
        emitVideoTelemetry()
    }

    fun onFrameDecoded(presentationTimeUs: Long, frameReceivedAtNs: Long?) {
        framesDecoded++
        updateFps()

        if (frameReceivedAtNs != null) {
            val latencyNs = (clock.nowNanos() - frameReceivedAtNs).coerceAtLeast(0L)
            lastLocalLatencyMs = (latencyNs / 1_000_000L).toInt()
        }
        lastRenderedPresentationTimeUs = presentationTimeUs
        emitVideoTelemetry()
    }

    fun getDroppedFrames(reason: LocalDropReason): Long = droppedFramesByReason[reason] ?: 0L

    private fun updateFps() {
        fpsCounter++
        val nowNs = clock.nowNanos()
        val elapsedNs = nowNs - lastFpsTimeNs
        if (elapsedNs >= 1_000_000_000L) {
            currentFps = ((fpsCounter * 1_000_000_000L) / elapsedNs).toInt()
            fpsCounter = 0
            lastFpsTimeNs = nowNs
            checkPerformance(nowNs)
        }
    }

    private fun updateBitrateIfNeeded() {
        val nowNs = clock.nowNanos()
        val elapsedNs = nowNs - lastBitrateSampleTimeNs
        if (elapsedNs < 1_000_000_000L) return

        val bitrateKbps = if (elapsedNs > 0) {
            (bytesSubmittedSinceLastBitrateSample * 8f * 1_000_000_000f) / elapsedNs.toFloat() / 1_000f
        } else {
            0f
        }

        telemetryCollector.updateBitrateKbps(bitrateKbps)
        bytesSubmittedSinceLastBitrateSample = 0L
        lastBitrateSampleTimeNs = nowNs
    }

    private fun checkPerformance(nowNs: Long) {
        if (nowNs - lastPerformanceCheckNs < performanceCheckIntervalNs) return
        lastPerformanceCheckNs = nowNs

        val totalFrames = framesDecoded + droppedLocalFrames
        val dropRate = if (totalFrames > 0) {
            (droppedLocalFrames.toFloat() / totalFrames.toFloat()) * 100f
        } else {
            0f
        }

        if (currentFps < 20 && currentFps > 0) {
            lowFpsCounter++
            if (lowFpsCounter >= 3) {
                Logger.w(Logger.TAG_VIDEO, "Low FPS detected: $currentFps (threshold: 20)")
                lowFpsCounter = 0
            }
        } else {
            lowFpsCounter = 0
        }

        if (dropRate > 5f) {
            highDropRateCounter++
            if (highDropRateCounter >= 3) {
                Logger.w(Logger.TAG_VIDEO, "High drop rate detected: ${dropRate.toInt()}% (threshold: 5%)")
                onAdjustBufferSize?.invoke(true)
                highDropRateCounter = 0
            }
        } else {
            highDropRateCounter = 0
        }

        val localLatency = telemetryCollector.telemetry.value.localLatencyMs
        if (localLatency > 1000 && dropRate < 1f) {
            Logger.i(Logger.TAG_VIDEO, "High local latency detected: ${localLatency}ms, reducing buffer")
            onAdjustBufferSize?.invoke(false)
        }
    }

    private fun emitVideoTelemetry() {
        telemetryCollector.updateVideoMetrics(
            fps = currentFps.toFloat(),
            localLatencyMs = lastLocalLatencyMs,
            droppedLocalFrames = droppedLocalFrames.toInt(),
            totalVideoFrames = (framesDecoded + droppedLocalFrames).toInt()
        )
    }

    fun resetMetrics() {
        framesDecoded = 0
        droppedLocalFrames = 0
        fpsCounter = 0
        currentFps = 0
        submittedInputFrames = 0
        lastLocalLatencyMs = 0
        bytesSubmittedSinceLastBitrateSample = 0L
        droppedFramesByReason.keys.forEach { droppedFramesByReason[it] = 0L }
        lastFpsTimeNs = clock.nowNanos()
        lastBitrateSampleTimeNs = lastFpsTimeNs
        lastPerformanceCheckNs = lastFpsTimeNs
        emitVideoTelemetry()
    }
}
