package com.airplay.tv.util

/**
 * Constantes globais do projeto AirPlay TV
 */
object Constants {
    // Network
    const val MDNS_SERVICE_TYPE = "_airplay._tcp"
    const val RTSP_PORT = 7000
    const val RTSP_PORT_SCAN_LIMIT = 32
    const val EXTRA_RTSP_PORT = "com.airplay.tv.extra.RTSP_PORT"
    const val DEFAULT_DEVICE_NAME = "Sony TV - Sala"
    
    // Protocol
    const val AIRPLAY_MODEL = "AppleTV3,2"
    // Align with UxPlay default feature mask. Most important here: bit 27
    // (Supports Legacy Pairing) stays ON, which affects audio key negotiation.
    const val AIRPLAY_FEATURES = "0x5A7FFEE6,0x0"
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
}
