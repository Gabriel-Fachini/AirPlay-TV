package com.airplay.tv.ui.components

import com.airplay.tv.service.VideoOutputSize
import com.airplay.tv.util.TelemetryCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `shouldShowVideoModeHint is false for native 16 by 9`() {
        assertFalse(shouldShowVideoModeHint(Pair(1920, 1080)))
    }

    @Test
    fun `shouldShowVideoModeHint is true for pseudo landscape phone geometry`() {
        assertTrue(shouldShowVideoModeHint(Pair(1920, 886)))
    }

    @Test
    fun `resolveMirroringLayoutStrategy fits wide content by width in normal mode`() {
        val strategy = resolveMirroringLayoutStrategy(
            presentationMode = MirroringPresentationMode.FIT,
            videoAspectRatio = 1920f / 886f,
            containerAspectRatio = 16f / 9f
        )

        assertEquals(MirroringLayoutStrategy.FIT_WIDTH, strategy)
    }

    @Test
    fun `resolveMirroringLayoutStrategy crops wide content by height in video mode`() {
        val strategy = resolveMirroringLayoutStrategy(
            presentationMode = MirroringPresentationMode.VIDEO_CROP_16_9,
            videoAspectRatio = 1920f / 886f,
            containerAspectRatio = 16f / 9f
        )

        assertEquals(MirroringLayoutStrategy.CROP_FILL, strategy)
    }

    @Test
    fun `resolveMirroringLayoutStrategy crops portrait-like geometry by width in video mode`() {
        val strategy = resolveMirroringLayoutStrategy(
            presentationMode = MirroringPresentationMode.VIDEO_CROP_16_9,
            videoAspectRatio = 4f / 3f,
            containerAspectRatio = 16f / 9f
        )

        assertEquals(MirroringLayoutStrategy.CROP_FILL, strategy)
    }

    @Test
    fun `resolveVideoModeScale widens pseudo landscape phone geometry`() {
        val scale = resolveVideoModeScale(
            presentationMode = MirroringPresentationMode.VIDEO_CROP_16_9,
            videoAspectRatio = 1920f / 886f,
            containerAspectRatio = 16f / 9f
        )

        assertEquals(true, scale.first > 1f)
        assertEquals(1f, scale.second)
    }

    @Test
    fun `resolveVideoModeScale stays neutral outside video mode`() {
        val scale = resolveVideoModeScale(
            presentationMode = MirroringPresentationMode.FIT,
            videoAspectRatio = 1920f / 886f,
            containerAspectRatio = 16f / 9f
        )

        assertEquals(1f, scale.first)
        assertEquals(1f, scale.second)
    }
}
