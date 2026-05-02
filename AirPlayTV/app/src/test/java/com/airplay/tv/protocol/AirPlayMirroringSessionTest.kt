package com.airplay.tv.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class AirPlayMirroringSessionTest {

    @Test
    fun `formats stream connection id as unsigned decimal`() {
        assertEquals(
            "15876509839857110628",
            formatAirPlayStreamConnectionId(-2570234233852440988L)
        )
    }
}
