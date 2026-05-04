package com.airplay.tv.util

import android.util.Log

/**
 * Sistema de logging centralizado com tags por componente
 */
object Logger {
    // Evita ':' nas tags porque `adb logcat -s <tag>` interpreta ':' como separador
    // de prioridade e quebra o filtro justamente quando precisamos isolar o áudio.
    const val TAG_MDNS = "AirPlayMDNS"
    const val TAG_PROTOCOL = "AirPlayProtocol"
    const val TAG_VIDEO = "AirPlayVideo"
    const val TAG_AUDIO = "AirPlayAudio"
    const val TAG_SESSION = "AirPlaySession"
    const val TAG_UI = "AirPlayUI"
    const val TAG_SERVICE = "AirPlayService"
    
    // Debug
    fun d(tag: String, message: String) {
        runSafely { Log.d(tag, message) }
    }
    
    // Info
    fun i(tag: String, message: String) {
        runSafely { Log.i(tag, message) }
    }
    
    // Warning
    fun w(tag: String, message: String) {
        runSafely { Log.w(tag, message) }
    }
    
    fun w(tag: String, message: String, throwable: Throwable) {
        runSafely { Log.w(tag, message, throwable) }
    }
    
    // Error
    fun e(tag: String, message: String) {
        runSafely { Log.e(tag, message) }
    }
    
    fun e(tag: String, message: String, throwable: Throwable) {
        runSafely { Log.e(tag, message, throwable) }
    }

    private inline fun runSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Permite reutilizar a lógica em testes JVM locais, onde android.util.Log
            // pode lançar exceções dos stubs do Android.
        }
    }
}
