package com.airplay.tv.util

import android.util.Log

/**
 * Sistema de logging centralizado com tags por componente
 */
object Logger {
    // Tags por componente
    const val TAG_MDNS = "AirPlay:mDNS"
    const val TAG_PROTOCOL = "AirPlay:Protocol"
    const val TAG_VIDEO = "AirPlay:Video"
    const val TAG_AUDIO = "AirPlay:Audio"
    const val TAG_SESSION = "AirPlay:Session"
    const val TAG_UI = "AirPlay:UI"
    const val TAG_SERVICE = "AirPlay:Service"
    
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
