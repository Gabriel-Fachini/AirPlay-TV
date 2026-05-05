package com.airplay.tv.ui

import com.airplay.tv.util.TelemetryCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

internal object TelemetryFlowBinder {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun bind(
        telemetrySources: Flow<StateFlow<TelemetryCollector.Telemetry>?>
    ): Flow<TelemetryCollector.Telemetry> {
        return telemetrySources.flatMapLatest { telemetry ->
            telemetry ?: flowOf(TelemetryCollector.Telemetry())
        }
    }
}
