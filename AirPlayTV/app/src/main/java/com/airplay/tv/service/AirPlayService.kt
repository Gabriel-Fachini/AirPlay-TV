package com.airplay.tv.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.airplay.tv.util.Logger

/**
 * Serviço Android para gerenciar conexões AirPlay
 * (Stub - será implementado na Fase 4)
 */
class AirPlayService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        Logger.i(Logger.TAG_SERVICE, "AirPlayService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(Logger.TAG_SERVICE, "AirPlayService started")
        // TODO: Implementar na Fase 4
        // - Iniciar servidor RTSP
        // - Registrar callbacks de protocolo
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.i(Logger.TAG_SERVICE, "AirPlayService destroyed")
        // TODO: Implementar na Fase 4
        // - Parar servidor RTSP
        // - Liberar recursos
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
