package com.airplay.tv.ui

import com.airplay.tv.service.VideoOutputSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

internal object VideoOutputSizeFlowBinder {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun bind(
        sizeSources: Flow<StateFlow<VideoOutputSize>?>
    ): Flow<VideoOutputSize> {
        return sizeSources.flatMapLatest { size ->
            size ?: flowOf(VideoOutputSize())
        }
    }
}
