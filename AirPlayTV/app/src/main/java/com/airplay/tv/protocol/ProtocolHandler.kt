package com.airplay.tv.protocol

import com.airplay.tv.util.Logger

/**
 * Handler do protocolo AirPlay (RTSP/RTP)
 * (Stub - será implementado na Fase 4)
 */
class ProtocolHandler {
    
    companion object {
        init {
            try {
                System.loadLibrary("airplay-native")
                Logger.i(Logger.TAG_PROTOCOL, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Logger.e(Logger.TAG_PROTOCOL, "Failed to load native library", e)
            }
        }
    }
    
    /**
     * Obtém versão da biblioteca nativa (teste JNI)
     */
    external fun getVersionFromJNI(): String
    
    /**
     * Inicia servidor RTSP
     */
    fun startRTSPServer(port: Int) {
        Logger.i(Logger.TAG_PROTOCOL, "Starting RTSP server on port $port (stub)")
        // TODO: Implementar na Fase 4
        // - Iniciar servidor RTSP via JNI
        // - Configurar callbacks
    }
    
    /**
     * Para servidor RTSP
     */
    fun stopRTSPServer() {
        Logger.i(Logger.TAG_PROTOCOL, "Stopping RTSP server (stub)")
        // TODO: Implementar na Fase 4
        // - Parar servidor RTSP via JNI
    }
}
