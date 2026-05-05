package com.airplay.tv.media

import com.airplay.tv.util.TelemetryCollector
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPerformanceTrackerTest {
    private class FakeClock(initialNanos: Long = 0L) : VideoPerformanceTracker.MonotonicClock {
        var currentNanos: Long = initialNanos

        override fun nowNanos(): Long = currentNanos
    }

    @Test
    fun `local latency uses monotonic receive time`() {
        val collector = TelemetryCollector()
        val clock = FakeClock()
        val tracker = VideoPerformanceTracker(collector, clock)

        tracker.onSessionStarted()
        val receivedAtNs = tracker.captureFrameReceivedAtNs()
        clock.currentNanos = 42_000_000L

        tracker.onFrameDecoded(
            presentationTimeUs = 123_456_789L,
            frameReceivedAtNs = receivedAtNs
        )

        assertEquals(42, collector.telemetry.value.localLatencyMs)
        assertEquals(1, collector.telemetry.value.totalVideoFrames)
    }

    @Test
    fun `local latency never depends on remote presentation timestamp`() {
        val collector = TelemetryCollector()
        val clock = FakeClock(initialNanos = 5_000_000L)
        val tracker = VideoPerformanceTracker(collector, clock)

        tracker.onSessionStarted()
        val receivedAtNs = tracker.captureFrameReceivedAtNs()
        clock.currentNanos = 3_000_000L

        tracker.onFrameDecoded(
            presentationTimeUs = Long.MAX_VALUE,
            frameReceivedAtNs = receivedAtNs
        )

        assertEquals(0, collector.telemetry.value.localLatencyMs)
    }

    @Test
    fun `drop counters update immediately by reason without decoded frames`() {
        val collector = TelemetryCollector()
        val tracker = VideoPerformanceTracker(collector, FakeClock())

        tracker.onSessionStarted()
        tracker.onFramesDropped(VideoPerformanceTracker.LocalDropReason.INVALID_FRAME)
        tracker.onFramesDropped(VideoPerformanceTracker.LocalDropReason.QUEUE_OVERFLOW, 2)
        tracker.onFramesDropped(VideoPerformanceTracker.LocalDropReason.PRE_START_NON_KEYFRAME)

        assertEquals(4, collector.telemetry.value.droppedLocalFrames)
        assertEquals(4, collector.telemetry.value.totalVideoFrames)
        assertEquals(1, tracker.getDroppedFrames(VideoPerformanceTracker.LocalDropReason.INVALID_FRAME))
        assertEquals(2, tracker.getDroppedFrames(VideoPerformanceTracker.LocalDropReason.QUEUE_OVERFLOW))
        assertEquals(1, tracker.getDroppedFrames(VideoPerformanceTracker.LocalDropReason.PRE_START_NON_KEYFRAME))
    }
}
