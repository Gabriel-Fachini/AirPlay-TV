package com.airplay.tv.util

/**
 * Constantes globais do projeto AirPlay TV
 */
object Constants {
    // Network
    const val MDNS_SERVICE_TYPE = "_airplay._tcp"
    const val RTSP_PORT = 7000
    const val DEFAULT_DEVICE_NAME = "Sony TV - Sala"
    
    // Protocol
    const val AIRPLAY_MODEL = "AppleTV3,2"
    const val AIRPLAY_FEATURES = "0x427FFFF7,0x1E"  // Sem Authentication_8 (bit 27 desligado)
    const val AIRPLAY_SRC_VERSION = "220.68"
    const val AIRPLAY_VERSION = "2"
    
    // Media
    const val VIDEO_CODEC_MIME = "video/avc"  // H.264
    const val AUDIO_CODEC_MIME = "audio/mp4a-latm"  // AAC
    const val MAX_INPUT_BUFFER_SIZE = 1024 * 1024  // 1MB
    
    // Performance
    const val TARGET_FPS = 30
    const val MAX_LATENCY_MS = 1000
    const val SYNC_THRESHOLD_MS = 100
    const val JITTER_BUFFER_FRAMES = 5
    
    // Session
    // Timeout aumentado para 30 segundos para acomodar mirroring
    // Durante mirroring, o Mac pode não enviar requisições RTSP por longos períodos
    // apenas enviando pacotes de vídeo via TCP
    const val SESSION_TIMEOUT_MS = 30000L
    const val RECONNECT_DELAY_MS = 3000L
    
    // Debug
    const val DEBUG_OVERLAY_ENABLED = true
}
