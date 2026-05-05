package com.airplay.tv.ui

import com.airplay.tv.util.TelemetryCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryFlowBinderTest {
    @Test
    fun `bind emits source telemetry without field reconstruction`() = runTest {
        val sourceTelemetry = MutableStateFlow(
            TelemetryCollector.Telemetry(
                fps = 29.5f,
                localLatencyMs = 87,
                bitrateKbps = 4_200f,
                resolutionWidth = 1920,
                resolutionHeight = 1080,
                droppedLocalFrames = 3,
                totalVideoFrames = 120
            )
        )
        val sources = MutableStateFlow<StateFlow<TelemetryCollector.Telemetry>?>(sourceTelemetry)

        val telemetry = TelemetryFlowBinder.bind(sources).first()

        assertEquals(sourceTelemetry.value, telemetry)
    }
}
