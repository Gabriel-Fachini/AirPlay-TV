package com.airplay.tv.ui

import com.airplay.tv.service.VideoOutputSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoOutputSizeFlowBinderTest {
    @Test
    fun `bind emits source video output size without reconstruction`() = runTest {
        val sourceSize = MutableStateFlow(VideoOutputSize(width = 1280, height = 720))
        val sources = MutableStateFlow<StateFlow<VideoOutputSize>?>(sourceSize)

        val size = VideoOutputSizeFlowBinder.bind(sources).first()

        assertEquals(sourceSize.value, size)
    }
}
