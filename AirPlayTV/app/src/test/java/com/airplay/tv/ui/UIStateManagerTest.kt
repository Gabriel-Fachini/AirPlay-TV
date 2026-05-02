package com.airplay.tv.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class UIStateManagerTest {

    @Test
    fun `allows transition from connecting to media playback`() {
        val manager = UIStateManager()
        manager.transitionTo(UIStateManager.UIState.Idle("Sony TV"))
        manager.transitionTo(UIStateManager.UIState.Connecting("192.168.0.10"))

        manager.transitionTo(
            UIStateManager.UIState.MediaPlayback(
                clientIp = "192.168.0.10",
                kind = "photo",
                sessionId = "session-1",
                imageData = byteArrayOf(1, 2, 3),
                assetKey = "asset-1",
                transition = "Dissolve",
                theme = null,
                slideDurationSeconds = 0
            )
        )

        assertTrue(manager.currentState.value is UIStateManager.UIState.MediaPlayback)
    }

    @Test
    fun `allows transition from media playback back to idle`() {
        val manager = UIStateManager()
        manager.transitionTo(UIStateManager.UIState.Idle("Sony TV"))
        manager.transitionTo(
            UIStateManager.UIState.MediaPlayback(
                clientIp = "192.168.0.10",
                kind = "slideshow",
                sessionId = "session-2",
                imageData = null,
                assetKey = null,
                transition = null,
                theme = "Classic",
                slideDurationSeconds = 3
            )
        )

        manager.returnToIdle("Sony TV")

        assertTrue(manager.currentState.value is UIStateManager.UIState.Idle)
    }
}
