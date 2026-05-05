package com.airplay.tv.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.Surface
import com.airplay.tv.service.AirPlayService
import com.airplay.tv.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ServiceConnectionManager(private val application: Application) {
    private var airPlayService: AirPlayService? = null
    var serviceBound = false
        private set

    private var pendingVideoSurface: Surface? = null

    private val _serviceFlow = MutableStateFlow<AirPlayService?>(null)
    val serviceFlow: StateFlow<AirPlayService?> = _serviceFlow

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AirPlayService.LocalBinder
            val instance = binder.getService()
            airPlayService = instance
            serviceBound = true

            pendingVideoSurface?.let { surface ->
                instance.setVideoSurface(surface)
            }
            
            _serviceFlow.value = instance
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            airPlayService = null
            serviceBound = false
            _serviceFlow.value = null
        }
    }

    fun startAndBindService(rtspPort: Int) {
        val intent = Intent(application, AirPlayService::class.java).apply {
            putExtra(Constants.EXTRA_RTSP_PORT, rtspPort)
        }
        application.startService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindAndStopService() {
        if (serviceBound) {
            application.unbindService(serviceConnection)
            serviceBound = false
        }
        val intent = Intent(application, AirPlayService::class.java)
        application.stopService(intent)
        _serviceFlow.value = null
    }

    fun setVideoSurface(surface: Surface) {
        pendingVideoSurface = surface
        airPlayService?.setVideoSurface(surface)
    }

    fun clearVideoSurface() {
        pendingVideoSurface = null
        airPlayService?.clearVideoSurface()
    }

    fun endSession() {
        airPlayService?.endSession()
    }
}
