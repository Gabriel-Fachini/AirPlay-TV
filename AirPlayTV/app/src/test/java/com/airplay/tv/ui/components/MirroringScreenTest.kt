package com.airplay.tv.ui.components

import com.airplay.tv.service.VideoOutputSize
import com.airplay.tv.util.TelemetryCollector
import org.junit.Assert.assertEquals
import org.junit.Test

class MirroringScreenTest {
    @Test
    fun `resolveVideoSize prioritizes decoder output size`() {
        val resolved = resolveVideoSize(
            videoOutputSize = VideoOutputSize(width = 720, height = 1280),
            telemetry = TelemetryCollector.Telemetry(
                resolutionWidth = 1920,
                resolutionHeight = 1080
            ),
            resolution = "3840x2160"
        )

        assertEquals(Pair(720, 1280), resolved)
    }

    @Test
    fun `resolveVideoSize falls back to telemetry when decoder size is missing`() {
        val resolved = resolveVideoSize(
            videoOutputSize = VideoOutputSize(),
            telemetry = TelemetryCollector.Telemetry(
                resolutionWidth = 1024,
                resolutionHeight = 576
            ),
            resolution = "1920x1080"
        )

        assertEquals(Pair(1024, 576), resolved)
    }

    @Test
    fun `resolveVideoSize falls back to session resolution before first output format`() {
        val resolved = resolveVideoSize(
            videoOutputSize = VideoOutputSize(),
            telemetry = TelemetryCollector.Telemetry(),
            resolution = "1920x1080"
        )

        assertEquals(Pair(1920, 1080), resolved)
    }
}
