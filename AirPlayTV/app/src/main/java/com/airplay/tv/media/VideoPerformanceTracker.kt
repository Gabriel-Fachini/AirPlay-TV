package com.airplay.tv.media

import com.airplay.tv.service.TelemetryCollector
import com.airplay.tv.util.Logger

class VideoPerformanceTracker(
    private val telemetryCollector: TelemetryCollector
) {
    var onAdjustBufferSize: ((increase: Boolean) -> Unit)? = null

    var framesDecoded = 0L
        private set
    var framesDropped = 0L
        private set
    var currentFps = 0
        private set
    var submittedInputFrames = 0L
        private set
    var lastRenderedPresentationTimeUs = 0L
        private set

    private var lastFpsTime = System.currentTimeMillis()
    private var fpsCounter = 0
    private var sessionStartTimeUs = 0L
    private var bytesSubmittedSinceLastBitrateSample = 0L
    private var lastBitrateSampleTimeMs = System.currentTimeMillis()

    private var lowFpsCounter = 0
    private var highDropRateCounter = 0
    private val performanceCheckInterval = 1000L
    private var lastPerformanceCheck = System.currentTimeMillis()

    fun onSessionStarted() {
        resetMetrics()
        sessionStartTimeUs = System.currentTimeMillis() * 1000L
    }

    fun onSessionStopped() {
        lastRenderedPresentationTimeUs = 0L
    }

    fun onFrameSubmittedToCodec(size: Int, isKeyFrame: Boolean) {
        submittedInputFrames++
        bytesSubmittedSinceLastBitrateSample += size.toLong()
        updateBitrateIfNeeded()
        if (submittedInputFrames <= 3L) {
            Logger.i(Logger.TAG_VIDEO, "Queued codec input #$submittedInputFrames ($size bytes, keyFrame=$isKeyFrame)")
        }
    }

    fun onFramesDropped(count: Long) {
        framesDropped += count
    }

    fun onFrameDecoded(presentationTimeUs: Long) {
        framesDecoded++
        updateFps()
        
        val currentTimeUs = System.currentTimeMillis() * 1000L
        val elapsedSinceSessionStartUs = currentTimeUs - sessionStartTimeUs
        val latencyUs = elapsedSinceSessionStartUs - presentationTimeUs
        val latencyMs = latencyUs / 1000
        lastRenderedPresentationTimeUs = presentationTimeUs
        
        telemetryCollector.updateVideoMetrics(
            fps = currentFps,
            latencyMs = latencyMs.toInt(),
            droppedFrames = framesDropped.toInt(),
            totalFrames = (framesDecoded + framesDropped).toInt()
        )
    }

    private fun updateFps() {
        fpsCounter++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTime
        if (elapsed >= 1000) {
            currentFps = (fpsCounter * 1000 / elapsed).toInt()
            fpsCounter = 0
            lastFpsTime = now
            checkPerformance()
        }
    }

    private fun updateBitrateIfNeeded() {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastBitrateSampleTimeMs
        if (elapsedMs < 1000) return

        val bitrateMbps = if (elapsedMs > 0) {
            (bytesSubmittedSinceLastBitrateSample * 8f) / (elapsedMs / 1000f) / 1_000_000f
        } else {
            0f
        }

        telemetryCollector.updateBitrate(bitrateMbps)
        bytesSubmittedSinceLastBitrateSample = 0L
        lastBitrateSampleTimeMs = now
    }

    private fun checkPerformance() {
        val now = System.currentTimeMillis()
        if (now - lastPerformanceCheck < performanceCheckInterval) return
        lastPerformanceCheck = now
        
        val totalFrames = framesDecoded + framesDropped
        val dropRate = if (totalFrames > 0) {
            (framesDropped.toFloat() / totalFrames.toFloat()) * 100f
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
        
        val latency = telemetryCollector.telemetry.value.latencyMs
        if (latency > 1000 && dropRate < 1f) {
            Logger.i(Logger.TAG_VIDEO, "High latency detected: ${latency}ms, reducing buffer")
            onAdjustBufferSize?.invoke(false)
        }
    }

    fun resetMetrics() {
        framesDecoded = 0
        framesDropped = 0
        fpsCounter = 0
        currentFps = 0
        submittedInputFrames = 0
        bytesSubmittedSinceLastBitrateSample = 0L
        lastFpsTime = System.currentTimeMillis()
        lastBitrateSampleTimeMs = lastFpsTime
    }
}
