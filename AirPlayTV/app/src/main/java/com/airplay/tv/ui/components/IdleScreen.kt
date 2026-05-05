package com.airplay.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airplay.tv.network.mDNSModule
import com.airplay.tv.ui.components.idle.IdleHeroColumn
import com.airplay.tv.ui.components.idle.MirrorGuideCard

@Composable
fun IdleScreen(
    deviceName: String,
    mdnsState: mDNSModule.ServiceState = mDNSModule.ServiceState.Unregistered
) {
    val displayName = if (deviceName.isBlank()) "AirPlay TV" else "AirPlay TV"

    AirPlayBackdrop {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 60.dp, end = 60.dp, top = 74.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IdleHeroColumn(
                modifier = Modifier.width(280.dp)
            )

            MirrorGuideCard(
                deviceName = displayName,
                mdnsState = mdnsState,
                isReady = mdnsState is mDNSModule.ServiceState.Registered,
                modifier = Modifier.width(432.dp)
            )
        }
    }
}
